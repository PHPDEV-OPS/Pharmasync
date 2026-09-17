package com.example.pharmasync.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.pharmasync.util.ImageCompressor
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Uploads images to the Firebase Storage bucket configured in google-services.json.
 * Paths follow `{kind}/{uid}/{file}.jpg` so storage.rules can restrict writes to the owner.
 */
class StorageRepository(
    private val context: Context,
    private val storage: FirebaseStorage,
) {
    suspend fun uploadJpeg(
        path: String,
        source: Uri,
        maxDimension: Int = 1280,
        onProgress: (Int) -> Unit = {},
    ): String {
        val bytes = withContext(Dispatchers.IO) {
            ImageCompressor.compressToJpeg(context, source, maxDimension)
        }
        val ref = storage.reference.child(path)
        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCacheControl("public, max-age=86400")
            .build()
        ref.putBytes(bytes, metadata)
            .addOnProgressListener { snapshot ->
                val total = snapshot.totalByteCount.coerceAtLeast(1)
                onProgress((100 * snapshot.bytesTransferred / total).toInt())
            }
            .await()
        return ref.downloadUrl.await().toString()
    }

    /** Best-effort removal of an image we own; failures are only logged. */
    fun deleteQuietly(ref: String) {
        if (ref.isBlank()) return
        val storageRef = runCatching {
            if (ref.startsWith("http")) storage.getReferenceFromUrl(ref) else storage.reference.child(ref)
        }.getOrNull() ?: return
        storageRef.delete().addOnFailureListener { Log.d(TAG, "Image not deleted: ${it.message}") }
    }

    private companion object {
        const val TAG = "StorageRepository"
    }
}
