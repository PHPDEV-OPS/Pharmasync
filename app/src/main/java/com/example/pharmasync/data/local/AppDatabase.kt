package com.example.pharmasync.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local copy of the signed-in user's data. Firestore is the source of truth; snapshot
 * listeners mirror it here so screens render instantly and keep working offline.
 */
@Database(
    entities = [MedicineEntity::class, OrderEntity::class, SupplierEntity::class, InvoiceEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun orderDao(): OrderDao
    abstract fun supplierDao(): SupplierDao
    abstract fun invoiceDao(): InvoiceDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pharmasync.db")
                // The database is a cache of Firestore, so it is safe to rebuild on schema changes.
                .fallbackToDestructiveMigration()
                .build()
    }
}
