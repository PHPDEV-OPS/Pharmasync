package com.example.pharmasync.data.firestore

import com.example.pharmasync.data.model.Invoice
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.model.Order
import com.example.pharmasync.data.model.OrderStatus
import com.example.pharmasync.data.model.Pharmacy
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.data.model.StockCollection
import com.example.pharmasync.data.model.SupplierSummary
import com.example.pharmasync.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

/**
 * Collection and field names used in Firestore. They match the names the app has always
 * written, so existing accounts and data keep working.
 */
object Fs {
    const val USERS = "Users"
    const val MEDICAL_STORES = "Medical-Store"
    const val SUPPLIERS_DATA = "Suppliers-Data"
    const val ORDERS = "Orders"
    const val COORDINATES = "Cordinates"
    const val COORDINATES_SUB = "MyCordinates"
    const val COORDINATES_DOC = "data"
    const val INVENTORY_ROOT = "Medicines"
    const val INVENTORY_SUB = "MyMedicines"
    const val CATALOG_ROOT = "Supplier-Stock"
    const val CATALOG_SUB = "MyStock"
    const val INVOICE_ROOT = "Invoice"
    const val INVOICE_SUB = "MyInvoice"

    fun stockCollection(db: FirebaseFirestore, ownerId: String, collection: StockCollection): CollectionReference =
        when (collection) {
            StockCollection.INVENTORY -> db.collection(INVENTORY_ROOT).document(ownerId).collection(INVENTORY_SUB)
            StockCollection.SUPPLIER_CATALOG -> db.collection(CATALOG_ROOT).document(ownerId).collection(CATALOG_SUB)
        }

    fun invoices(db: FirebaseFirestore, ownerId: String): CollectionReference =
        db.collection(INVOICE_ROOT).document(ownerId).collection(INVOICE_SUB)
}

// region Tolerant readers: older documents stored numbers as strings and used varying keys.

internal fun DocumentSnapshot.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    get(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
}

internal fun DocumentSnapshot.number(vararg keys: String): Double? = keys.firstNotNullOfOrNull { key ->
    when (val value = get(key)) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }
}

internal fun DocumentSnapshot.millis(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
    when (val value = get(key)) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        is Number -> value.toLong()
        else -> null
    }
}

// endregion

private val legacyUsernameSuffix = Regex("firestore-\\d+$")

fun DocumentSnapshot.toUserProfile(): UserProfile = UserProfile(
    uid = id,
    email = text("Email").orEmpty(),
    name = text("Uname", "Name")?.replace(legacyUsernameSuffix, "")?.trim().orEmpty(),
    businessName = text("Shop-Name", "ShopName", "Name").orEmpty(),
    address = text("Address").orEmpty(),
    phone = text("Phone", "Contact").orEmpty(),
    role = Role.from(text("Role")),
    photoUrl = text("ProfilePic").orEmpty(),
    photoVersion = millis("ProfilePicUpdatedAt") ?: 0L,
)

fun DocumentSnapshot.toSupplierSummary(): SupplierSummary {
    val profile = toUserProfile()
    return SupplierSummary(
        uid = profile.uid,
        name = profile.businessName.ifBlank { profile.name.ifBlank { profile.email } },
        email = profile.email,
        phone = profile.phone,
        address = profile.address,
        photoUrl = profile.photoUrl,
    )
}

fun DocumentSnapshot.toPharmacy(): Pharmacy = Pharmacy(
    uid = text("Uid") ?: id,
    name = text("ShopName", "Shop-Name", "Name").orEmpty(),
    address = text("Address").orEmpty(),
    phone = text("Phone").orEmpty(),
    photoUrl = text("ProfilePic").orEmpty(),
    latitude = number("Latitude"),
    longitude = number("Longitude"),
)

/**
 * Legacy medicine images were stored as a bare file name under `Medicines/{uid}/`; new ones
 * store the full download URL.
 */
private fun legacyImageRef(folder: String, ownerId: String, value: String?): String = when {
    value.isNullOrBlank() || value.endsWith("null") -> ""
    value.startsWith("http") -> value
    else -> "$folder/$ownerId/$value"
}

fun DocumentSnapshot.toMedicine(): Medicine {
    val ownerId = text("UserID") ?: reference.parent.parent?.id.orEmpty()
    return Medicine(
        id = id,
        ownerId = ownerId,
        name = text("Medicine", "Name").orEmpty(),
        description = text("Description").orEmpty(),
        category = text("Category") ?: Medicine.DEFAULT_CATEGORY,
        price = number("PricePerUnit", "Price") ?: 0.0,
        stock = number("Stock")?.toInt() ?: 0,
        lowStockThreshold = (number("LowStock")?.toInt() ?: 0).coerceAtLeast(0),
        imageRef = legacyImageRef("Medicines", ownerId, text("ImageUri")),
        manufacturer = text("Manufacturer").orEmpty(),
        ndc = text("Ndc").orEmpty(),
        createdAt = millis("CreatedAt") ?: 0L,
    )
}

fun Medicine.toFirestore(): Map<String, Any> = mapOf(
    "MedicineId" to id,
    "UserID" to ownerId,
    "Medicine" to name,
    "Description" to description,
    "Category" to category,
    "PricePerUnit" to price,
    "Stock" to stock,
    "LowStock" to lowStockThreshold,
    "ImageUri" to imageRef,
    "Manufacturer" to manufacturer,
    "Ndc" to ndc,
    "CreatedAt" to Timestamp(Date(if (createdAt > 0) createdAt else System.currentTimeMillis())),
)

fun DocumentSnapshot.toOrder(): Order {
    val created = millis("CreatedAt") ?: 0L
    return Order(
        id = text("OrderId") ?: id,
        pharmacistId = text("PharmacistId").orEmpty(),
        pharmacistName = text("PharmacistName").orEmpty(),
        supplierId = text("SupplierId").orEmpty(),
        supplierName = text("SupplierName").orEmpty(),
        medicineId = text("MedicineId").orEmpty(),
        medicineName = text("MedicineName").orEmpty(),
        quantity = number("Quantity")?.toInt() ?: 0,
        unitPrice = number("UnitPrice") ?: 0.0,
        status = OrderStatus.from(text("Status")),
        createdAt = created,
        updatedAt = millis("UpdatedAt") ?: created,
    )
}

fun Order.toFirestore(): Map<String, Any> = mapOf(
    "OrderId" to id,
    "PharmacistId" to pharmacistId,
    "PharmacistName" to pharmacistName,
    "SupplierId" to supplierId,
    "SupplierName" to supplierName,
    "MedicineId" to medicineId,
    "MedicineName" to medicineName,
    "Quantity" to quantity,
    "UnitPrice" to unitPrice,
    "Status" to status.firestoreValue,
    "CreatedAt" to Timestamp(Date(createdAt)),
    "UpdatedAt" to Timestamp(Date(updatedAt)),
)

fun DocumentSnapshot.toInvoice(): Invoice {
    val ownerId = reference.parent.parent?.id.orEmpty()
    return Invoice(
        id = id,
        ownerId = ownerId,
        name = text("InvoiceName").orEmpty(),
        description = text("Description").orEmpty(),
        imageRef = legacyImageRef("Invoices", ownerId, text("ImageUri")),
        createdAt = millis("time", "CreatedAt") ?: 0L,
    )
}

fun Invoice.toFirestore(): Map<String, Any> = mapOf(
    "InvoiceId" to id,
    "InvoiceName" to name,
    "Description" to description,
    "ImageUri" to imageRef,
    "time" to Timestamp(Date(createdAt)),
)
