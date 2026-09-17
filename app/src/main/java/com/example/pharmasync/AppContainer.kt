package com.example.pharmasync

import android.content.Context
import com.example.pharmasync.data.local.AppDatabase
import com.example.pharmasync.data.remote.GeoapifyApi
import com.example.pharmasync.data.remote.OpenFdaApi
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Manual dependency container; one instance lives in [PharmasyncApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val firebaseStorage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val session: SessionPrefs by lazy { SessionPrefs(appContext) }

    private val openFdaApi: OpenFdaApi by lazy { retrofit(OpenFdaApi.BASE_URL).create(OpenFdaApi::class.java) }
    private val geoapifyApi: GeoapifyApi by lazy { retrofit(GeoapifyApi.BASE_URL).create(GeoapifyApi::class.java) }

    val storageRepository by lazy { StorageRepository(appContext, firebaseStorage) }
    val drugCatalogRepository by lazy { DrugCatalogRepository(openFdaApi) }
    val locationRepository by lazy { LocationRepository(appContext, geoapifyApi) }
    val userRepository by lazy { UserRepository(auth, firestore, storageRepository, locationRepository) }
    val authRepository by lazy { AuthRepository(auth, userRepository, session, database) }
    val inventoryRepository by lazy {
        InventoryRepository(firestore, database.medicineDao(), storageRepository, drugCatalogRepository)
    }
    val orderRepository by lazy { OrderRepository(firestore, database.orderDao()) }
    val supplierRepository by lazy { SupplierRepository(firestore, database.supplierDao()) }
    val invoiceRepository by lazy { InvoiceRepository(appContext, firestore, database.invoiceDao(), storageRepository) }
    val exploreRepository by lazy { ExploreRepository(firestore) }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
