package com.example.pharmasync.data.firestore

import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Listens to [this] query and mirrors every snapshot into local storage via [write].
 *
 * An empty snapshot served from an empty cache (e.g. first launch while offline) is ignored so
 * it doesn't wipe the Room copy. [onSynced] fires once data has been confirmed by the server.
 * Writes are serialised so an older snapshot can never overwrite a newer one.
 */
fun Query.mirrorInto(
    scope: CoroutineScope,
    onError: (Exception) -> Unit,
    onSynced: () -> Unit = {},
    write: suspend (QuerySnapshot) -> Unit,
): ListenerRegistration {
    val writeLock = Mutex()
    return addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) {
            Log.w("FirestoreSync", "Listener failed", error)
            onError(error)
            return@addSnapshotListener
        }
        if (snapshot == null) return@addSnapshotListener
        val fromCache = snapshot.metadata.isFromCache
        if (fromCache && snapshot.isEmpty) return@addSnapshotListener
        scope.launch {
            writeLock.withLock { write(snapshot) }
            if (!fromCache) onSynced()
        }
    }
}
