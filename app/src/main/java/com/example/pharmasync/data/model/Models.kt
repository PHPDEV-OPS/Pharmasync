package com.example.pharmasync.data.model

enum class Role(val firestoreValue: String) {
    PHARMACIST("Pharmacist"),
    SUPPLIER("Supplier");

    companion object {
        fun from(value: String?): Role =
            if (value.equals(SUPPLIER.firestoreValue, ignoreCase = true)) SUPPLIER else PHARMACIST
    }
}

/** Which Firestore collection a [Medicine] lives in. */
enum class StockCollection {
    /** A pharmacy's shelf inventory: `Medicines/{uid}/MyMedicines`. */
    INVENTORY,

    /** A supplier's sellable catalog: `Supplier-Stock/{uid}/MyStock`. */
    SUPPLIER_CATALOG
}

data class Medicine(
    val id: String,
    val ownerId: String,
    val name: String,
    val description: String = "",
    val category: String = DEFAULT_CATEGORY,
    val price: Double = 0.0,
    val stock: Int = 0,
    /** Alert when [stock] falls to this level; 0 disables alerts. */
    val lowStockThreshold: Int = 0,
    /** Either an https download URL or a legacy Firebase Storage path. */
    val imageRef: String = "",
    val manufacturer: String = "",
    val ndc: String = "",
    val createdAt: Long = 0L,
) {
    val isOutOfStock: Boolean get() = stock <= 0
    val isLowStock: Boolean get() = lowStockThreshold > 0 && stock <= lowStockThreshold

    companion object {
        const val DEFAULT_CATEGORY = "General"
    }
}

enum class OrderStatus(val firestoreValue: String) {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    DISPATCHED("Dispatched"),
    DELIVERED("Delivered"),
    DECLINED("Declined"),
    CANCELLED("Cancelled");

    val isOpen: Boolean get() = this == PENDING || this == ACCEPTED || this == DISPATCHED
    val isClosed: Boolean get() = !isOpen

    companion object {
        fun from(value: String?): OrderStatus =
            entries.firstOrNull { it.firestoreValue.equals(value, ignoreCase = true) } ?: PENDING
    }
}

data class Order(
    val id: String,
    val pharmacistId: String,
    val pharmacistName: String,
    val supplierId: String,
    val supplierName: String,
    val medicineId: String,
    val medicineName: String,
    val quantity: Int,
    val unitPrice: Double,
    val status: OrderStatus,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val total: Double get() = quantity * unitPrice
}

data class UserProfile(
    val uid: String,
    val email: String,
    val name: String,
    val businessName: String,
    val address: String,
    val phone: String,
    val role: Role,
    val photoUrl: String,
    val photoVersion: Long,
)

data class Invoice(
    val id: String,
    val ownerId: String,
    val name: String,
    val description: String,
    val imageRef: String,
    val createdAt: Long,
)

/** A supplier as seen by pharmacists. */
data class SupplierSummary(
    val uid: String,
    val name: String,
    val email: String,
    val phone: String,
    val address: String,
    val photoUrl: String,
)

/** A pharmacy as seen by the public explore screens. */
data class Pharmacy(
    val uid: String,
    val name: String,
    val address: String,
    val phone: String,
    val photoUrl: String,
    val latitude: Double?,
    val longitude: Double?,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

/** A medicine available at a specific pharmacy (public explore). */
data class PharmacyMedicine(
    val medicine: Medicine,
    val pharmacy: Pharmacy?,
)

/** A drug product looked up from the openFDA directory. */
data class CatalogDrug(
    val name: String,
    val genericName: String,
    val manufacturer: String,
    val dosageForm: String,
    val category: String,
    val strength: String,
    val ndc: String,
) {
    val displayName: String
        get() = if (strength.isNotBlank() && !name.contains(strength, ignoreCase = true)) "$name $strength" else name

    val description: String
        get() = listOf(genericName, dosageForm).filter { it.isNotBlank() }.joinToString(" · ")
}
