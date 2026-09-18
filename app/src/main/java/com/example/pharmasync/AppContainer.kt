package com.example.pharmasync

import android.content.Context
import com.example.pharmasync.data.local.AppDatabase
import com.example.pharmasync.data.remote.AuthInterceptor
import com.example.pharmasync.data.remote.GeoapifyApi
import com.example.pharmasync.data.remote.OpenFdaApi
import com.example.pharmasync.data.remote.PharmasyncApi
import com.example.pharmasync.data.repository.AuthRepository
import com.example.pharmasync.data.repository.DrugCatalogRepository
import com.example.pharmasync.data.repository.ExploreRepository
import com.example.pharmasync.data.repository.InventoryRepository
import com.example.pharmasync.data.repository.InvoiceRepository
import com.example.pharmasync.data.repository.LocationRepository
import com.example.pharmasync.data.repository.OrderRepository
import com.example.pharmasync.data.repository.StorageRepository
import com.example.pharmasync.data.repository.SupplierRepository
import com.example.pharmasync.data.repository.UserRepository
import com.example.pharmasync.util.SessionPrefs
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Manual dependency container; one instance lives in [PharmasyncApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val session: SessionPrefs by lazy { SessionPrefs(appContext) }

    /** Plain client: used for openFDA, geocoding and presigned storage URLs (no auth header). */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Sends the Firebase ID token to the Pharmasync backend. */
    private val apiClient: OkHttpClient by lazy {
        httpClient.newBuilder().addInterceptor(AuthInterceptor(auth)).build()
    }

    val api: PharmasyncApi by lazy {
        retrofit(BuildConfig.API_BASE_URL, apiClient).create(PharmasyncApi::class.java)
    }
    private val openFdaApi: OpenFdaApi by lazy {
        retrofit(OpenFdaApi.BASE_URL, httpClient).create(OpenFdaApi::class.java)
    }
    private val geoapifyApi: GeoapifyApi by lazy {
        retrofit(GeoapifyApi.BASE_URL, httpClient).create(GeoapifyApi::class.java)
    }

    val storageRepository by lazy { StorageRepository(appContext, api, httpClient) }
    val drugCatalogRepository by lazy { DrugCatalogRepository(openFdaApi) }
    val locationRepository by lazy { LocationRepository(appContext, geoapifyApi) }
    val userRepository by lazy { UserRepository(api, database.profileDao(), storageRepository, locationRepository) }
    val authRepository by lazy { AuthRepository(auth, userRepository, session, database) }
    val inventoryRepository by lazy {
        InventoryRepository(api, database.medicineDao(), storageRepository, drugCatalogRepository)
    }
    val orderRepository by lazy { OrderRepository(api, database.orderDao()) }
    val supplierRepository by lazy { SupplierRepository(api, database.supplierDao()) }
    val invoiceRepository by lazy { InvoiceRepository(appContext, api, database.invoiceDao(), storageRepository) }
    val exploreRepository by lazy { ExploreRepository(api) }

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
