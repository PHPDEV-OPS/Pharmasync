package com.example.pharmasync.data.repository

import com.example.pharmasync.data.firestore.Fs
import com.example.pharmasync.data.firestore.mirrorInto
import com.example.pharmasync.data.firestore.toSupplierSummary
import com.example.pharmasync.data.local.SupplierDao
import com.example.pharmasync.data.local.SupplierEntity
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.data.model.SupplierSummary
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Registered supplier accounts, visible to pharmacists. */
class SupplierRepository(
    private val firestore: FirebaseFirestore,
    private val dao: SupplierDao,
) {
    fun observe(): Flow<List<SupplierSummary>> =
        dao.observeAll().map { rows -> rows.map(SupplierEntity::toModel) }.distinctUntilChanged()

    fun sync(scope: CoroutineScope, onError: (Exception) -> Unit, onSynced: () -> Unit): ListenerRegistration =
        firestore.collection(Fs.USERS)
            .whereEqualTo("Role", Role.SUPPLIER.firestoreValue)
            .mirrorInto(scope, onError, onSynced) { snapshot ->
                dao.replaceAll(snapshot.documents.map { SupplierEntity.from(it.toSupplierSummary()) })
            }
}
