package com.example.pharmasync.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The Pharmasync backend: a Neon Function (see /backend) in front of Neon Postgres and Neon Object
 * Storage. Authenticated routes receive the Firebase ID token from [AuthInterceptor].
 */
interface PharmasyncApi {

    // Public (no account)
    @GET("public/pharmacies")
    suspend fun publicPharmacies(): List<PharmacyDto>

    @GET("public/medicines")
    suspend fun publicMedicines(@Query("q") query: String = ""): List<MedicineDto>

    // Profile
    @GET("me")
    suspend fun me(): UserDto

    @PUT("me")
    suspend fun saveMe(@Body body: SaveProfileRequest): UserDto

    @PUT("me/location")
    suspend fun saveLocation(@Body body: LocationRequest): UserDto

    @PUT("me/photo")
    suspend fun savePhoto(@Body body: PhotoRequest): UserDto

    @POST("uploads")
    suspend fun createUpload(@Body body: UploadRequest): UploadDto

    // Suppliers & medicines
    @GET("suppliers")
    suspend fun suppliers(): List<UserDto>

    @GET("medicines")
    suspend fun medicines(@Query("collection") collection: String, @Query("ownerId") ownerId: String? = null): List<MedicineDto>

    @PUT("medicines/{id}")
    suspend fun saveMedicine(@Path("id") id: String, @Body body: MedicineDto): MedicineDto

    @POST("medicines/import")
    suspend fun importMedicines(@Body body: ImportRequest): List<MedicineDto>

    @PATCH("medicines/{id}/stock")
    suspend fun updateStock(@Path("id") id: String, @Body body: StockRequest): MedicineDto

    @DELETE("medicines/{id}")
    suspend fun deleteMedicine(@Path("id") id: String): retrofit2.Response<Unit>

    // Orders
    @GET("orders")
    suspend fun orders(): List<OrderDto>

    @POST("orders")
    suspend fun placeOrder(@Body body: PlaceOrderRequest): OrderDto

    @POST("orders/{id}/{action}")
    suspend fun orderAction(@Path("id") id: String, @Path("action") action: String): OrderDto

    // Invoices
    @GET("invoices")
    suspend fun invoices(): List<InvoiceDto>

    @POST("invoices")
    suspend fun createInvoice(@Body body: CreateInvoiceRequest): InvoiceDto

    @DELETE("invoices/{id}")
    suspend fun deleteInvoice(@Path("id") id: String): retrofit2.Response<Unit>
}

// region DTOs (field names match the JSON the backend returns)

data class UserDto(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val businessName: String = "",
    val address: String = "",
    val phone: String = "",
    val role: String = "",
    val photoUrl: String = "",
    val photoVersion: Long = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class PharmacyDto(
    val uid: String = "",
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class MedicineDto(
    val id: String = "",
    val ownerId: String = "",
    val collection: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val price: Double = 0.0,
    val stock: Int = 0,
    val lowStockThreshold: Int = 0,
    val imageUrl: String = "",
    val manufacturer: String = "",
    val ndc: String = "",
    val createdAt: Long = 0,
)

data class OrderDto(
    val id: String = "",
    val pharmacistId: String = "",
    val pharmacistName: String? = null,
    val supplierId: String = "",
    val supplierName: String? = null,
    val medicineId: String = "",
    val medicineName: String = "",
    val quantity: Int = 0,
    val unitPrice: Double = 0.0,
    val status: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class InvoiceDto(
    val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val createdAt: Long = 0,
)

data class UploadDto(
    val key: String = "",
    val uploadUrl: String = "",
    val contentType: String = "",
    val publicUrl: String? = null,
)

data class SaveProfileRequest(
    val role: String?,
    val name: String,
    val businessName: String,
    val address: String,
    val phone: String,
    val latitude: Double?,
    val longitude: Double?,
)

data class LocationRequest(val latitude: Double, val longitude: Double, val address: String)
data class PhotoRequest(val photoUrl: String)
data class UploadRequest(val kind: String, val contentType: String = "image/jpeg")
data class ImportRequest(val collection: String, val items: List<MedicineDto>)
data class StockRequest(val stock: Int, val lowStockThreshold: Int)
data class PlaceOrderRequest(val id: String, val supplierId: String, val medicineId: String, val quantity: Int)
data class CreateInvoiceRequest(val id: String, val name: String, val description: String, val imageKey: String)

// endregion
