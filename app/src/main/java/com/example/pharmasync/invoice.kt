package com.example.pharmasync

import com.google.firebase.Timestamp

data class invoice (
    val InvoiceName :String ? = null,
    val Description :String? = null,
    val time : Timestamp? = null,
    val ImageUri: String? = null,
    val InvoiceId: String? = null
)