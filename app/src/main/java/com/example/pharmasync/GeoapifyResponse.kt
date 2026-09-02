package com.example.pharmasync

data class GeoapifyResponse(
    val features: List<Feature>? = null
)

data class Feature(
    val geometry: Geometry? = null
)

data class Geometry(
    val coordinates: List<Double>? = null
)
