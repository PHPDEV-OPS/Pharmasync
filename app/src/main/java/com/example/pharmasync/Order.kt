package com.example.pharmasync

import com.google.firebase.Timestamp

data class Order(
    val OrderId: String? = null,
    val PharmacistId: String? = null,
    val PharmacistName: String? = null,
    val SupplierId: String? = null,
    val MedicineName: String? = null,
    val Quantity: String? = null,
    val Status: String? = "Pending", // Pending, Accepted, Cancelled
    val CreatedAt: Timestamp? = null
)
