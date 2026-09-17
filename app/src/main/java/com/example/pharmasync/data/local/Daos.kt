package com.example.pharmasync.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines WHERE owner_id = :ownerId AND collection = :collection ORDER BY name COLLATE NOCASE")
    fun observe(ownerId: String, collection: String): Flow<List<MedicineEntity>>

    @Upsert
    suspend fun upsert(items: List<MedicineEntity>)

    @Query("DELETE FROM medicines WHERE owner_id = :ownerId AND collection = :collection AND id = :id")
    suspend fun delete(ownerId: String, collection: String, id: String)

    @Query("DELETE FROM medicines WHERE owner_id = :ownerId AND collection = :collection")
    suspend fun clear(ownerId: String, collection: String)

    @Transaction
    suspend fun replaceAll(ownerId: String, collection: String, items: List<MedicineEntity>) {
        clear(ownerId, collection)
        upsert(items)
    }
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE pharmacist_id = :uid ORDER BY created_at DESC")
    fun observeForPharmacist(uid: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE supplier_id = :uid ORDER BY created_at DESC")
    fun observeForSupplier(uid: String): Flow<List<OrderEntity>>

    @Upsert
    suspend fun upsert(items: List<OrderEntity>)

    @Query("DELETE FROM orders WHERE pharmacist_id = :uid")
    suspend fun clearForPharmacist(uid: String)

    @Query("DELETE FROM orders WHERE supplier_id = :uid")
    suspend fun clearForSupplier(uid: String)

    @Transaction
    suspend fun replaceForPharmacist(uid: String, items: List<OrderEntity>) {
        clearForPharmacist(uid)
        upsert(items)
    }

    @Transaction
    suspend fun replaceForSupplier(uid: String, items: List<OrderEntity>) {
        clearForSupplier(uid)
        upsert(items)
    }
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Upsert
    suspend fun upsert(items: List<SupplierEntity>)

    @Query("DELETE FROM suppliers")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<SupplierEntity>) {
        clear()
        upsert(items)
    }
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices WHERE owner_id = :ownerId ORDER BY created_at DESC")
    fun observe(ownerId: String): Flow<List<InvoiceEntity>>

    @Upsert
    suspend fun upsert(items: List<InvoiceEntity>)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM invoices WHERE owner_id = :ownerId")
    suspend fun clear(ownerId: String)

    @Transaction
    suspend fun replaceAll(ownerId: String, items: List<InvoiceEntity>) {
        clear(ownerId)
        upsert(items)
    }
}
