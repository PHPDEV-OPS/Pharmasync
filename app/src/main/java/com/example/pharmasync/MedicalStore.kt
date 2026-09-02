package com.example.pharmasync

data class MedicalStore(
    val Address : String? = null,
    val ShopName : String? = null,
    val Uid : String? = null,
    val Uname : String? = null,
    val MedicineID : List<String> = emptyList()
    )
