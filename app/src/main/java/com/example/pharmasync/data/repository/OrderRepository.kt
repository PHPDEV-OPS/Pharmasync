package com.example.pharmasync.data.repository

import android.util.Log
import com.example.pharmasync.data.firestore.Fs
import com.example.pharmasync.data.firestore.mirrorInto
import com.example.pharmasync.data.firestore.number
import com.example.pharmasync.data.firestore.toFirestore
import com.example.pharmasync.data.firestore.toMedicine
import com.example.pharmasync.data.firestore.toOrder
import com.example.pharmasync.data.local.OrderDao
import com.example.pharmasync.data.local.OrderEntity
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.model.Order
import com.example.pharmasync.data.model.OrderStatus
import com.example.pharmasync.data.model.StockCollection
import com.example.pharmasync.data.model.SupplierSummary
import com.example.pharmasync.data.model.UserProfile
import com.example.pharmasync.util.InsufficientStockException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Order lifecycle between a pharmacy and a supplier:
 * Pending → Accepted (supplier stock reserved) → Dispatched → Delivered (pharmacy stock added).
 * Pending orders can also be Declined by the supplier or Cancelled by the pharmacy.
 */
class OrderRepository(
    private val firestore: FirebaseFirestore,
    private val dao: OrderDao,
) {
    private val orders get() = firestore.collection(Fs.ORDERS)

    fun observeForPharmacist(uid: String): Flow<List<Order>> =
        dao.observeForPharmacist(uid).map { rows -> rows.map(OrderEntity::toModel) }.distinctUntilChanged()

    fun observeForSupplier(uid: String): Flow<List<Order>> =
        dao.observeForSupplier(uid).map { rows -> rows.map(OrderEntity::toModel) }.distinctUntilChanged()

    fun syncForPharmacist(uid: String, scope: CoroutineScope, onError: (Exception) -> Unit, onSynced: () -> Unit): ListenerRegistration =
        orders.whereEqualTo("PharmacistId", uid).mirrorInto(scope, onError, onSynced) { snapshot ->
            dao.replaceForPharmacist(uid, snapshot.documents.map { OrderEntity.from(it.toOrder()) })
        }

    fun syncForSupplier(uid: String, scope: CoroutineScope, onError: (Exception) -> Unit, onSynced: () -> Unit): ListenerRegistration =
        orders.whereEqualTo("SupplierId", uid).mirrorInto(scope, onError, onSynced) { snapshot ->
            dao.replaceForSupplier(uid, snapshot.documents.map { OrderEntity.from(it.toOrder()) })
        }

    suspend fun place(pharmacy: UserProfile, supplier: SupplierSummary, item: Medicine, quantity: Int): Order {
        val now = System.currentTimeMillis()
        val ref = orders.document()
        val order = Order(
            id = ref.id,
            pharmacistId = pharmacy.uid,
            pharmacistName = pharmacy.businessName.ifBlank { pharmacy.name },
            supplierId = supplier.uid,
            supplierName = supplier.name,
            medicineId = item.id,
            medicineName = item.name,
            quantity = quantity,
            unitPrice = item.price,
            status = OrderStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(listOf(OrderEntity.from(order)))
        ref.set(order.toFirestore()).addOnFailureListener { Log.w(TAG, "Order not saved", it) }
        return order
    }

    /** Supplier accepts: atomically reserves catalog stock. Requires a connection. */
    suspend fun accept(order: Order) {
        val orderRef = orders.document(order.id)
        val stockRef = order.medicineId.takeIf { it.isNotBlank() }?.let {
            Fs.stockCollection(firestore, order.supplierId, StockCollection.SUPPLIER_CATALOG).document(it)
        }
        firestore.runTransaction { tx ->
            val current = tx.get(orderRef)
            check(OrderStatus.from(current.getString("Status")) == OrderStatus.PENDING) { "Order is no longer pending" }
            val stockSnapshot = stockRef?.let { tx.get(it) }
            if (stockSnapshot != null && stockSnapshot.exists()) {
                val available = stockSnapshot.number("Stock")?.toInt() ?: 0
                if (available < order.quantity) throw InsufficientStockException(available)
                tx.update(stockRef, "Stock", available - order.quantity)
            }
            tx.update(orderRef, statusUpdate(OrderStatus.ACCEPTED))
        }.await()
        dao.upsert(listOf(OrderEntity.from(order.copy(status = OrderStatus.ACCEPTED, updatedAt = System.currentTimeMillis()))))
    }

    /** Simple transitions that don't touch stock: decline, dispatch, cancel. Work offline. */
    suspend fun updateStatus(order: Order, status: OrderStatus) {
        dao.upsert(listOf(OrderEntity.from(order.copy(status = status, updatedAt = System.currentTimeMillis()))))
        orders.document(order.id).update(statusUpdate(status))
            .addOnFailureListener { Log.w(TAG, "Status update failed", it) }
    }

    /**
     * Pharmacy confirms delivery: the quantity is added to the matching inventory item (by name),
     * or a new item is created using the supplier's product details. Requires a connection.
     */
    suspend fun markReceived(order: Order) {
        val orderRef = orders.document(order.id)
        val inventory = Fs.stockCollection(firestore, order.pharmacistId, StockCollection.INVENTORY)
        val existingRef = inventory.whereEqualTo("Medicine", order.medicineName).limit(1).get().await()
            .documents.firstOrNull()?.reference
        val catalogRef = order.medicineId.takeIf { it.isNotBlank() }?.let {
            Fs.stockCollection(firestore, order.supplierId, StockCollection.SUPPLIER_CATALOG).document(it)
        }
        firestore.runTransaction { tx ->
            val status = OrderStatus.from(tx.get(orderRef).getString("Status"))
            check(status == OrderStatus.ACCEPTED || status == OrderStatus.DISPATCHED) { "Order can't be received from $status" }
            if (existingRef != null) {
                val stock = tx.get(existingRef).number("Stock")?.toInt() ?: 0
                tx.update(existingRef, "Stock", stock + order.quantity)
            } else {
                val source = catalogRef?.let { tx.get(it) }?.takeIf { it.exists() }?.toMedicine()
                val newRef = inventory.document()
                val item = Medicine(
                    id = newRef.id,
                    ownerId = order.pharmacistId,
                    name = order.medicineName,
                    description = source?.description.orEmpty(),
                    category = source?.category ?: Medicine.DEFAULT_CATEGORY,
                    price = order.unitPrice,
                    stock = order.quantity,
                    imageRef = source?.imageRef.orEmpty(),
                    manufacturer = source?.manufacturer.orEmpty(),
                    ndc = source?.ndc.orEmpty(),
                    createdAt = System.currentTimeMillis(),
                )
                tx.set(newRef, item.toFirestore())
            }
            tx.update(orderRef, statusUpdate(OrderStatus.DELIVERED))
        }.await()
        dao.upsert(listOf(OrderEntity.from(order.copy(status = OrderStatus.DELIVERED, updatedAt = System.currentTimeMillis()))))
    }

    private fun statusUpdate(status: OrderStatus): Map<String, Any> =
        mapOf("Status" to status.firestoreValue, "UpdatedAt" to Timestamp.now())

    private companion object {
        const val TAG = "OrderRepository"
    }
}
