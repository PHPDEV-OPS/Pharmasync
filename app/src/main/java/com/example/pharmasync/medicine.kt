package com.example.pharmasync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class medicine(
    @get:PropertyName("Medicine") @set:PropertyName("Medicine") var Medicine: String? = null,
    @get:PropertyName("Description") @set:PropertyName("Description") var Description: String? = null,
    @get:PropertyName("PricePerUnit") @set:PropertyName("PricePerUnit") var PricePerUnit: String? = null,
    @get:PropertyName("MedicineId") @set:PropertyName("MedicineId") var MedicineId: String? = null,
    @get:PropertyName("Stock") @set:PropertyName("Stock") var Stock: String? = null,
    @get:PropertyName("CreatedAt") @set:PropertyName("CreatedAt") var CreatedAt: Timestamp? = null,
    @get:PropertyName("LowStock") @set:PropertyName("LowStock") var LowStock: String? = null,
    @get:PropertyName("UserID") @set:PropertyName("UserID") var UserID: String? = null,
    @get:PropertyName("Category") @set:PropertyName("Category") var Category: String? = null,
    @get:PropertyName("ImageUri") @set:PropertyName("ImageUri") var ImageUri: String? = null
)
