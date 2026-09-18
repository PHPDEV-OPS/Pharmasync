package com.example.pharmasync.data.remote

import com.example.pharmasync.data.model.Invoice
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.model.Order
import com.example.pharmasync.data.model.OrderStatus
import com.example.pharmasync.data.model.Pharmacy
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.data.model.StockCollection
import com.example.pharmasync.data.model.SupplierSummary
import com.example.pharmasync.data.model.UserProfile

fun UserDto.toProfile() = UserProfile(
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

fun UserDto.toSupplier() = SupplierSummary(
    uid = uid,
    name = businessName.ifBlank { name.ifBlank { email } },
    email = email,
    phone = phone,
    address = address,
    photoUrl = photoUrl,
)

fun PharmacyDto.toPharmacy() = Pharmacy(
    uid = uid,
    name = name,
    address = address,
    phone = phone,
    photoUrl = photoUrl,
    latitude = latitude,
    longitude = longitude,
)

fun MedicineDto.toMedicine() = Medicine(
    id = id,
    ownerId = ownerId,
    name = name,
    description = description,
    category = category.ifBlank { Medicine.DEFAULT_CATEGORY },
    price = price,
    stock = stock,
    lowStockThreshold = lowStockThreshold,
    imageRef = imageUrl,
    manufacturer = manufacturer,
    ndc = ndc,
    createdAt = createdAt,
)

fun Medicine.toDto(collection: StockCollection) = MedicineDto(
    id = id,
    ownerId = ownerId,
    collection = collection.apiValue,
    name = name,
    description = description,
    category = category,
    price = price,
    stock = stock,
    lowStockThreshold = lowStockThreshold,
    imageUrl = imageRef,
    manufacturer = manufacturer,
    ndc = ndc,
    createdAt = createdAt,
)

fun OrderDto.toOrder() = Order(
    id = id,
    pharmacistId = pharmacistId,
    pharmacistName = pharmacistName.orEmpty(),
    supplierId = supplierId,
    supplierName = supplierName.orEmpty(),
    medicineId = medicineId,
    medicineName = medicineName,
    quantity = quantity,
    unitPrice = unitPrice,
    status = OrderStatus.from(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun InvoiceDto.toInvoice() = Invoice(
    id = id,
    ownerId = ownerId,
    name = name,
    description = description,
    imageRef = imageUrl,
    createdAt = createdAt,
)
