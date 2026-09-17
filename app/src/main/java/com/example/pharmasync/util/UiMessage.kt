package com.example.pharmasync.util

import android.content.Context
import androidx.annotation.StringRes
import com.example.pharmasync.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import java.io.IOException

/** A user-facing message that is resolved against resources only when shown. */
sealed interface UiMessage {
    fun resolve(context: Context): String

    data class Text(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiMessage {
        override fun resolve(context: Context): String = context.getString(resId, *args.toTypedArray())
    }

    data class Error(val throwable: Throwable) : UiMessage {
        override fun resolve(context: Context): String = context.getString(ErrorMessages.of(throwable))
    }

    companion object {
        fun of(@StringRes resId: Int, vararg args: Any): UiMessage = Text(resId, args.toList())
    }
}

/** Thrown when a supplier tries to accept an order larger than the remaining stock. */
class InsufficientStockException(val available: Int) : IllegalStateException("Only $available available")

object ErrorMessages {
    @StringRes
    fun of(throwable: Throwable): Int = when (throwable) {
        is FirebaseAuthUserCollisionException -> R.string.error_email_in_use
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> R.string.error_wrong_credentials
        is FirebaseTooManyRequestsException -> R.string.error_too_many_requests
        is FirebaseNetworkException, is IOException -> R.string.error_network
        is StorageException -> when (throwable.errorCode) {
            StorageException.ERROR_NOT_AUTHORIZED, StorageException.ERROR_NOT_AUTHENTICATED -> R.string.error_storage_unauthorized
            StorageException.ERROR_BUCKET_NOT_FOUND, StorageException.ERROR_PROJECT_NOT_FOUND -> R.string.error_storage_bucket
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> R.string.error_network
            else -> R.string.error_generic
        }
        is FirebaseFirestoreException -> when (throwable.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE -> R.string.error_requires_connection
            else -> R.string.error_generic
        }
        else -> R.string.error_generic
    }
}
