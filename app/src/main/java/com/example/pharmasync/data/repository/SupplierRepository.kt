package com.example.pharmasync.data.repository

import com.example.pharmasync.data.local.SupplierDao
import com.example.pharmasync.data.local.SupplierEntity
import com.example.pharmasync.data.model.SupplierSummary
import com.example.pharmasync.data.remote.PharmasyncApi
import com.example.pharmasync.data.remote.apiCall
import com.example.pharmasync.data.remote.toSupplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Registered supplier accounts, visible to pharmacists. */
class SupplierRepository(
    private val api: PharmasyncApi,
    private val dao: SupplierDao,
) {
    fun observe(): Flow<List<SupplierSummary>> =
        dao.observeAll().map { rows -> rows.map(SupplierEntity::toModel) }.distinctUntilChanged()

    suspend fun refresh() {
        val suppliers = apiCall { api.suppliers() }.map { it.toSupplier() }
        dao.replaceAll(suppliers.map(SupplierEntity::from))
    }
}
