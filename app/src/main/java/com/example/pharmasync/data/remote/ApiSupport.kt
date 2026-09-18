package com.example.pharmasync.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Attaches the signed-in user's Firebase ID token. On a 401 the token is force-refreshed once, which
 * also picks up a freshly verified email address.
 */
class AuthInterceptor(private val auth: FirebaseAuth) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val user = auth.currentUser ?: return chain.proceed(chain.request())
        val response = chain.proceed(withToken(chain, token(forceRefresh = false) ?: return chain.proceed(chain.request())))
        @Suppress("DEPRECATION_ERROR")
        if (response.code() != 401) return response
        response.close()
        val fresh = token(forceRefresh = true) ?: throw IOException("Could not refresh the session for ${user.uid}")
        return chain.proceed(withToken(chain, fresh))
    }

    private fun withToken(chain: Interceptor.Chain, token: String) =
        chain.request().newBuilder().header("Authorization", "Bearer $token").build()

    private fun token(forceRefresh: Boolean): String? = try {
        // OkHttp calls interceptors on a background thread, so blocking here is safe.
        auth.currentUser?.getIdToken(forceRefresh)?.let { Tasks.await(it, 20, TimeUnit.SECONDS).token }
    } catch (e: Exception) {
        throw IOException("Could not get a sign-in token", e)
    }
}

/** A structured error returned by the backend: `{ "error": { "code", "message", ... } }`. */
class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
    val available: Int? = null,
) : Exception(message) {
    val isProfileMissing: Boolean get() = code == "profile_missing"
    val isEmailNotVerified: Boolean get() = code == "email_not_verified"
    val isUnauthenticated: Boolean get() = status == 401
    val isServerError: Boolean get() = status >= 500
}

/** Runs an API call and converts HTTP failures into [ApiException]. Network failures stay IOExceptions. */
suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    throw e.toApiException()
}

fun HttpException.toApiException(): ApiException {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val error = runCatching { JsonParser.parseString(body).asJsonObject.getAsJsonObject("error") }.getOrNull()
    return ApiException(
        status = code(),
        code = error?.get("code")?.asString ?: "http_${code()}",
        message = error?.get("message")?.asString ?: message(),
        available = error?.get("available")?.takeIf { it.isJsonPrimitive }?.asInt,
    )
}
