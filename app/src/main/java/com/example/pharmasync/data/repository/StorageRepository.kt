package com.example.pharmasync.data.repository

import android.content.Context
import android.net.Uri
import com.example.pharmasync.data.remote.PharmasyncApi
import com.example.pharmasync.data.remote.UploadRequest
import com.example.pharmasync.data.remote.apiCall
import com.example.pharmasync.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Where an image is stored; controls the folder, bucket and compression size. */
enum class ImageKind(val apiValue: String, val maxDimension: Int) {
    AVATAR("avatar", 512),
    PRODUCT("product", 1024),
    INVOICE("invoice", 2000),
}

data class UploadedImage(
    /** Object key in Neon Object Storage (used for private invoice images). */
    val key: String,
    /** Public URL, for objects in the public bucket. */
    val url: String?,
)

/**
 * Uploads images to Neon Object Storage. The app never holds storage credentials: the backend
 * returns a short-lived presigned URL, and the bytes are PUT straight to storage.
 */
class StorageRepository(
    private val context: Context,
    private val api: PharmasyncApi,
    /** A client without the auth interceptor: presigned URLs must not carry an Authorization header. */
    private val uploadClient: OkHttpClient,
) {
    suspend fun upload(kind: ImageKind, source: Uri, onProgress: (Int) -> Unit = {}): UploadedImage {
        onProgress(5)
        val bytes = withContext(Dispatchers.IO) {
            ImageCompressor.compressToJpeg(context, source, kind.maxDimension)
        }
        onProgress(25)
        val ticket = apiCall { api.createUpload(UploadRequest(kind.apiValue)) }
        val request = Request.Builder()
            .url(ticket.uploadUrl)
            .put(bytes.toRequestBody(ticket.contentType.toMediaType()))
            .header("Content-Type", ticket.contentType)
            .build()
        withContext(Dispatchers.IO) {
            uploadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Upload failed (${response.code}). Please try again.")
                }
            }
        }
        onProgress(100)
        return UploadedImage(ticket.key, ticket.publicUrl)
    }

    /** Downloads bytes from a presigned or public URL. */
    suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        uploadClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Could not download the image (${response.code})")
            response.body?.bytes() ?: throw IOException("Empty image response")
        }
    }

    @Suppress("unused")
    private fun cancel(call: Call) = call.cancel()
}
