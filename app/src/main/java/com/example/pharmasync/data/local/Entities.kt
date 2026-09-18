package com.example.pharmasync.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.pharmasync.data.model.Invoice
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.model.Order
import com.example.pharmasync.data.model.OrderStatus
import com.example.pharmasync.data.model.StockCollection
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.data.model.SupplierSummary
import com.example.pharmasync.data.model.UserProfile

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val name: String,
    @ColumnInfo(name = "business_name") val businessName: String,
    val address: String,
    val phone: String,
    val role: String,
    @ColumnInfo(name = "photo_url") val photoUrl: String,
    @ColumnInfo(name = "photo_version") val photoVersion: Long,
    val latitude: Double?,
    val longitude: Double?,
) {
    fun toModel() = UserProfile(
        uid = uid,
        email = email,
        name = name,
        businessName = businessName,
        address = address,
        phone = phone,
        role = Role.from(role),
        photoUrl = photoUrl,
        photoVersion = photoVersion,
        latitude = latitude,
        longitude = longitude,
    )

    companion object {
        fun from(p: UserProfile) = ProfileEntity(
            uid = p.uid,
            email = p.email,
            name = p.name,
            businessName = p.businessName,
            address = p.address,
            phone = p.phone,
            role = p.role.apiValue,
            photoUrl = p.photoUrl,
            photoVersion = p.photoVersion,
            latitude = p.latitude,
            longitude = p.longitude,
        )
    }
}

@Entity(
    tableName = "medicines",
    primaryKeys = ["collection", "owner_id", "id"],
    indices = [Index("owner_id", "collection")],
)
data class MedicineEntity(
    val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    val collection: String,
    val name: String,
    val description: String,
    val category: String,
    val price: Double,
    val stock: Int,
    @ColumnInfo(name = "low_stock_threshold") val lowStockThreshold: Int,
    @ColumnInfo(name = "image_ref") val imageRef: String,
    val manufacturer: String,
    val ndc: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    fun toModel() = Medicine(
        id = id,
        ownerId = ownerId,
        name = name,
        description = description,
        category = category,
        price = price,
        stock = stock,
        lowStockThreshold = lowStockThreshold,
        imageRef = imageRef,
        manufacturer = manufacturer,
        ndc = ndc,
        createdAt = createdAt,
    )

    companion object {
        fun from(medicine: Medicine, collection: StockCollection) = MedicineEntity(
            id = medicine.id,
            ownerId = medicine.ownerId,
            collection = collection.name,
            name = medicine.name,
            description = medicine.description,
            category = medicine.category,
            price = medicine.price,
            stock = medicine.stock,
            lowStockThreshold = medicine.lowStockThreshold,
            imageRef = medicine.imageRef,
            manufacturer = medicine.manufacturer,
            ndc = medicine.ndc,
            createdAt = medicine.createdAt,
        )
    }
}

@Entity(
    tableName = "orders",
    indices = [Index("pharmacist_id"), Index("supplier_id")],
)
data class OrderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "pharmacist_id") val pharmacistId: String,
    @ColumnInfo(name = "pharmacist_name") val pharmacistName: String,
    @ColumnInfo(name = "supplier_id") val supplierId: String,
    @ColumnInfo(name = "supplier_name") val supplierName: String,
    @ColumnInfo(name = "medicine_id") val medicineId: String,
    @ColumnInfo(name = "medicine_name") val medicineName: String,
    val quantity: Int,
    @ColumnInfo(name = "unit_price") val unitPrice: Double,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    fun toModel() = Order(
        id = id,
        pharmacistId = pharmacistId,
        pharmacistName = pharmacistName,
        supplierId = supplierId,
        supplierName = supplierName,
        medicineId = medicineId,
        medicineName = medicineName,
        quantity = quantity,
        unitPrice = unitPrice,
        status = OrderStatus.from(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun from(order: Order) = OrderEntity(
            id = order.id,
            pharmacistId = order.pharmacistId,
            pharmacistName = order.pharmacistName,
            supplierId = order.supplierId,
            supplierName = order.supplierName,
            medicineId = order.medicineId,
            medicineName = order.medicineName,
            quantity = order.quantity,
            unitPrice = order.unitPrice,
            status = order.status.apiValue,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
        )
    }
}

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val email: String,
    val phone: String,
    val address: String,
    @ColumnInfo(name = "photo_url") val photoUrl: String,
) {
    fun toModel() = SupplierSummary(uid, name, email, phone, address, photoUrl)

    companion object {
        fun from(s: SupplierSummary) = SupplierEntity(s.uid, s.name, s.email, s.phone, s.address, s.photoUrl)
    }
}

@Entity(tableName = "invoices", indices = [Index("owner_id")])
data class InvoiceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    val name: String,
    val description: String,
    @ColumnInfo(name = "image_ref") val imageRef: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    fun toModel() = Invoice(id, ownerId, name, description, imageRef, createdAt)

    companion object {
        fun from(i: Invoice) = InvoiceEntity(i.id, i.ownerId, i.name, i.description, i.imageRef, i.createdAt)
    }
}
