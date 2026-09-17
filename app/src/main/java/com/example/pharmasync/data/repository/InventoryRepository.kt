package com.example.pharmasync.data.repository

import android.net.Uri
import android.util.Log
import com.example.pharmasync.data.firestore.Fs
import com.example.pharmasync.data.firestore.mirrorInto
import com.example.pharmasync.data.firestore.toFirestore
import com.example.pharmasync.data.firestore.toMedicine
import com.example.pharmasync.data.local.MedicineDao
import com.example.pharmasync.data.local.MedicineEntity
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.model.StockCollection
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Pharmacy inventories and supplier catalogs share one model. Reads come from Room; writes go to
 * Room immediately and to Firestore (which queues them while offline).
 */
class InventoryRepository(
    private val firestore: FirebaseFirestore,
    private val dao: MedicineDao,
    private val storage: StorageRepository,
    private val drugCatalog: DrugCatalogRepository,
) {
    fun observe(ownerId: String, collection: StockCollection): Flow<List<Medicine>> =
        dao.observe(ownerId, collection.name)
            .map { rows -> rows.map(MedicineEntity::toModel) }
            .distinctUntilChanged()

    fun sync(
        ownerId: String,
        collection: StockCollection,
        scope: CoroutineScope,
        onError: (Exception) -> Unit,
        onSynced: () -> Unit,
    ): ListenerRegistration = Fs.stockCollection(firestore, ownerId, collection)
        .mirrorInto(scope, onError, onSynced) { snapshot ->
            dao.replaceAll(ownerId, collection.name, snapshot.documents.map { MedicineEntity.from(it.toMedicine(), collection) })
        }

    fun newId(ownerId: String, collection: StockCollection): String =
        Fs.stockCollection(firestore, ownerId, collection).document().id

    suspend fun save(medicine: Medicine, collection: StockCollection, newImage: Uri? = null): Medicine {
        var item = medicine
        if (newImage != null) {
            val url = storage.uploadJpeg("medicine_images/${item.ownerId}/${item.id}.jpg", newImage)
            item = item.copy(imageRef = url)
        }
        if (item.createdAt == 0L) item = item.copy(createdAt = System.currentTimeMillis())
        dao.upsert(listOf(MedicineEntity.from(item, collection)))
        Fs.stockCollection(firestore, item.ownerId, collection).document(item.id)
            .set(item.toFirestore())
            .addOnFailureListener { Log.w(TAG, "Save failed for ${item.id}", it) }
        return item
    }

    suspend fun updateStock(medicine: Medicine, collection: StockCollection, stock: Int, threshold: Int) {
        val updated = medicine.copy(stock = stock, lowStockThreshold = threshold)
        dao.upsert(listOf(MedicineEntity.from(updated, collection)))
        Fs.stockCollection(firestore, medicine.ownerId, collection).document(medicine.id)
            .update(mapOf("Stock" to stock, "LowStock" to threshold))
            .addOnFailureListener { Log.w(TAG, "Stock update failed for ${medicine.id}", it) }
    }

    suspend fun delete(medicine: Medicine, collection: StockCollection) {
        dao.delete(medicine.ownerId, collection.name, medicine.id)
        Fs.stockCollection(firestore, medicine.ownerId, collection).document(medicine.id).delete()
        storage.deleteQuietly(medicine.imageRef)
    }

    /** Fills the list with real products from openFDA. Returns the number imported. */
    suspend fun importDemo(ownerId: String, collection: StockCollection, count: Int = 20): Int {
        val existing = dao.observe(ownerId, collection.name).first().map { it.name.lowercase() }.toSet()
        val items = drugCatalog.demoCatalog(count)
            .filter { it.displayName.lowercase() !in existing }
            .map { drugCatalog.toDemoMedicine(it, newId(ownerId, collection), ownerId) }
        if (items.isEmpty()) return 0
        dao.upsert(items.map { MedicineEntity.from(it, collection) })
        val batch = firestore.batch()
        val ref = Fs.stockCollection(firestore, ownerId, collection)
        items.forEach { batch.set(ref.document(it.id), it.toFirestore()) }
        batch.commit().addOnFailureListener { Log.w(TAG, "Demo import failed", it) }
        return items.size
    }

    private companion object {
        const val TAG = "InventoryRepository"
    }
}
