package com.example.pharmasync.data.repository

import com.example.pharmasync.data.local.OrderDao
import com.example.pharmasync.data.local.OrderEntity
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.model.Order
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.data.remote.ApiException
import com.example.pharmasync.data.remote.PharmasyncApi
import com.example.pharmasync.data.remote.PlaceOrderRequest
import com.example.pharmasync.data.remote.apiCall
import com.example.pharmasync.data.remote.toOrder
import com.example.pharmasync.util.InsufficientStockException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Order lifecycle between a pharmacy and a supplier:
 * pending → accepted (supplier stock reserved) → dispatched → delivered (pharmacy stock added).
 * Pending orders can also be declined by the supplier or cancelled by the pharmacy.
 * Stock changes happen inside Postgres transactions on the backend.
 */
class OrderRepository(
    private val api: PharmasyncApi,
    private val dao: OrderDao,
) {
    fun observe(uid: String, role: Role): Flow<List<Order>> =
        (if (role == Role.SUPPLIER) dao.observeForSupplier(uid) else dao.observeForPharmacist(uid))
            .map { rows -> rows.map(OrderEntity::toModel) }
            .distinctUntilChanged()

    suspend fun refresh(uid: String, role: Role) {
        val orders = apiCall { api.orders() }.map { it.toOrder() }
        val entities = orders.map(OrderEntity::from)
        if (role == Role.SUPPLIER) dao.replaceForSupplier(uid, entities) else dao.replaceForPharmacist(uid, entities)
    }

    suspend fun place(supplierId: String, item: Medicine, quantity: Int): Order = withStockCheck {
        val order = apiCall {
            api.placeOrder(PlaceOrderRequest(UUID.randomUUID().toString(), supplierId, item.id, quantity))
        }.toOrder()
        dao.upsert(listOf(OrderEntity.from(order)))
        order
    }

    /** Runs a transition (accept, decline, dispatch, cancel, receive) on the backend. */
    suspend fun perform(order: Order, action: String): Order = withStockCheck {
        val updated = apiCall { api.orderAction(order.id, action) }.toOrder()
        dao.upsert(listOf(OrderEntity.from(updated)))
        updated
    }

    private inline fun <T> withStockCheck(block: () -> T): T = try {
        block()
    } catch (e: ApiException) {
        if (e.code == "insufficient_stock") throw InsufficientStockException(e.available ?: 0) else throw e
    }
}
