package com.example.pharmasync.util

import android.content.Context
import androidx.annotation.StringRes
import com.example.pharmasync.R
import com.example.pharmasync.data.remote.ApiException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import java.io.IOException

/** A user-facing message that is resolved against resources only when shown. */
sealed interface UiMessage {
    fun resolve(context: Context): String

    data class Text(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiMessage {
        override fun resolve(context: Context): String = context.getString(resId, *args.toTypedArray())
    }

    /** A literal message, used for the explanations the backend already phrases for people. */
    data class Literal(val text: String) : UiMessage {
        override fun resolve(context: Context): String = text
    }

    data class Error(val throwable: Throwable) : UiMessage {
        override fun resolve(context: Context): String = ErrorMessages.describe(context, throwable)
    }

    companion object {
        fun of(@StringRes resId: Int, vararg args: Any): UiMessage = Text(resId, args.toList())
    }
}

/** Thrown when a supplier tries to accept an order larger than the remaining stock. */
class InsufficientStockException(val available: Int) : IllegalStateException("Only $available available")

object ErrorMessages {

    fun describe(context: Context, throwable: Throwable): String = when {
        throwable is ApiException && throwable.isUnauthenticated -> context.getString(R.string.error_session_expired)
        throwable is ApiException && throwable.isServerError -> context.getString(R.string.error_server)
        // 4xx responses carry a message written for the person using the app.
        throwable is ApiException -> throwable.message
        else -> context.getString(of(throwable))
    }

    @StringRes
    fun of(throwable: Throwable): Int = when (throwable) {
        is FirebaseAuthUserCollisionException -> R.string.error_email_in_use
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> R.string.error_wrong_credentials
        is FirebaseTooManyRequestsException -> R.string.error_too_many_requests
        is FirebaseNetworkException, is IOException -> R.string.error_no_connection
        else -> R.string.error_generic
    }
}
