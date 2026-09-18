package com.example.pharmasync.data.repository

import android.net.Uri
import com.example.pharmasync.data.local.MedicineDao
import com.example.pharmasync.data.local.MedicineEntity
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.model.StockCollection
import com.example.pharmasync.data.remote.ImportRequest
import com.example.pharmasync.data.remote.PharmasyncApi
import com.example.pharmasync.data.remote.StockRequest
import com.example.pharmasync.data.remote.apiCall
import com.example.pharmasync.data.remote.toDto
import com.example.pharmasync.data.remote.toMedicine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Pharmacy inventories and supplier catalogs share one model and one table. Reads come from Room;
 * every write goes to the backend first and the response is mirrored locally.
 */
class InventoryRepository(
    private val api: PharmasyncApi,
    private val dao: MedicineDao,
    private val storage: StorageRepository,
    private val drugCatalog: DrugCatalogRepository,
) {
    fun observe(ownerId: String, collection: StockCollection): Flow<List<Medicine>> =
        dao.observe(ownerId, collection.name)
            .map { rows -> rows.map(MedicineEntity::toModel) }
            .distinctUntilChanged()

    suspend fun refresh(ownerId: String, collection: StockCollection, forOwner: String? = null) {
        val items = apiCall { api.medicines(collection.apiValue, forOwner) }.map { it.toMedicine() }
        dao.replaceAll(ownerId, collection.name, items.map { MedicineEntity.from(it, collection) })
    }

    fun newMedicineId(): String = UUID.randomUUID().toString()

    suspend fun save(medicine: Medicine, collection: StockCollection, newImage: Uri? = null): Medicine {
        var item = medicine
        if (newImage != null) {
            val uploaded = storage.upload(ImageKind.PRODUCT, newImage)
            item = item.copy(imageRef = uploaded.url.orEmpty())
        }
        val saved = apiCall { api.saveMedicine(item.id, item.toDto(collection)) }.toMedicine()
        dao.upsert(listOf(MedicineEntity.from(saved, collection)))
        return saved
    }

    suspend fun updateStock(medicine: Medicine, collection: StockCollection, stock: Int, threshold: Int) {
        val saved = apiCall { api.updateStock(medicine.id, StockRequest(stock, threshold)) }.toMedicine()
        dao.upsert(listOf(MedicineEntity.from(saved, collection)))
    }

    suspend fun delete(medicine: Medicine, collection: StockCollection) {
        apiCall { api.deleteMedicine(medicine.id) }
        dao.delete(medicine.ownerId, collection.name, medicine.id)
    }

    /** Fills the list with real products from openFDA. Returns the number imported. */
    suspend fun importDemo(ownerId: String, collection: StockCollection, count: Int = 20): Int {
        val existing = dao.observe(ownerId, collection.name).first().map { it.name.lowercase() }.toSet()
        val items = drugCatalog.demoCatalog(count)
            .filter { it.displayName.lowercase() !in existing }
            .map { drugCatalog.toDemoMedicine(it, newMedicineId(), ownerId) }
        if (items.isEmpty()) return 0
        val saved = apiCall {
            api.importMedicines(ImportRequest(collection.apiValue, items.map { it.toDto(collection) }))
        }.map { it.toMedicine() }
        dao.upsert(saved.map { MedicineEntity.from(it, collection) })
        return saved.size
    }
}
