package com.example.pharmasync.data.repository

import android.util.Log
import com.example.pharmasync.data.model.Pharmacy
import com.example.pharmasync.data.model.PharmacyMedicine
import com.example.pharmasync.data.remote.PharmasyncApi
import com.example.pharmasync.data.remote.apiCall
import com.example.pharmasync.data.remote.toMedicine
import com.example.pharmasync.data.remote.toPharmacy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Read-only public data for people browsing without an account. */
class ExploreRepository(private val api: PharmasyncApi) {

    data class Snapshot(
        val pharmacies: List<Pharmacy>,
        val medicines: List<PharmacyMedicine>,
        /** First failure, if any part couldn't be loaded. Partial data is still returned. */
        val error: Exception?,
    )

    suspend fun load(): Snapshot = coroutineScope {
        val pharmaciesTask = async { attempt { apiCall { api.publicPharmacies() }.map { it.toPharmacy() } } }
        val medicinesTask = async { attempt { apiCall { api.publicMedicines() }.map { it.toMedicine() } } }

        val pharmacyResult = pharmaciesTask.await()
        val medicineResult = medicinesTask.await()
        val pharmacies = pharmacyResult.getOrNull().orEmpty()
        val byUid = pharmacies.associateBy { it.uid }

        Snapshot(
            pharmacies = pharmacies,
            medicines = medicineResult.getOrNull().orEmpty().map { PharmacyMedicine(it, byUid[it.ownerId]) },
            error = listOf(pharmacyResult, medicineResult).firstNotNullOfOrNull { it.exceptionOrNull() as? Exception },
        )
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("ExploreRepository", "Load failed", e)
        Result.failure(e)
    }
}
