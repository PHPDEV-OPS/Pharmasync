package com.example.pharmasync.data.repository

import com.example.pharmasync.data.firestore.Fs
import com.example.pharmasync.data.firestore.number
import com.example.pharmasync.data.firestore.toMedicine
import com.example.pharmasync.data.firestore.toPharmacy
import com.example.pharmasync.data.model.Pharmacy
import com.example.pharmasync.data.model.PharmacyMedicine
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/** Read-only public data for people browsing without an account. */
class ExploreRepository(private val firestore: FirebaseFirestore) {

    suspend fun pharmacies(): List<Pharmacy> = coroutineScope {
        val stores = async { firestore.collection(Fs.MEDICAL_STORES).get().await() }
        val coordinates = async { runCatching { firestore.collectionGroup(Fs.COORDINATES_SUB).get().await() }.getOrNull() }

        // Coordinates live at Cordinates/{uid}/MyCordinates/data (older data) or on the store itself.
        val pins = coordinates.await()?.documents.orEmpty().mapNotNull { doc ->
            val uid = doc.getString("UserID") ?: doc.reference.parent.parent?.id ?: return@mapNotNull null
            val lat = doc.number("Latitude") ?: return@mapNotNull null
            val lng = doc.number("Longitude") ?: return@mapNotNull null
            uid to (lat to lng)
        }.toMap()

        stores.await().documents
            .map { it.toPharmacy() }
            .filter { it.name.isNotBlank() }
            .map { pharmacy ->
                if (pharmacy.hasLocation) pharmacy
                else pins[pharmacy.uid]?.let { (lat, lng) -> pharmacy.copy(latitude = lat, longitude = lng) } ?: pharmacy
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun medicinesInStock(pharmacies: List<Pharmacy>): List<PharmacyMedicine> {
        val byUid = pharmacies.associateBy { it.uid }
        return firestore.collectionGroup(Fs.INVENTORY_SUB).get().await().documents
            .map { it.toMedicine() }
            .filter { it.stock > 0 && it.name.isNotBlank() && it.ownerId in byUid }
            .map { PharmacyMedicine(it, byUid[it.ownerId]) }
            .sortedBy { it.medicine.name.lowercase() }
    }
}
