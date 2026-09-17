package com.example.pharmasync.data.repository

import com.example.pharmasync.data.model.CatalogDrug
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.remote.NdcProduct
import com.example.pharmasync.data.remote.OpenFdaApi
import com.example.pharmasync.util.Formatters
import retrofit2.HttpException
import java.net.URLEncoder
import kotlin.random.Random

/** Real-world drug data from the free openFDA NDC directory, used for lookups and demo data. */
class DrugCatalogRepository(private val api: OpenFdaApi) {

    /** Name suggestions while typing, e.g. "amox" -> Amoxicillin products. */
    suspend fun suggest(term: String, limit: Int = 8): List<CatalogDrug> {
        val cleaned = term.trim().replace(Regex("[^A-Za-z0-9 ]"), "")
        if (cleaned.length < 3) return emptyList()
        val words = cleaned.split(' ').filter { it.isNotBlank() }
        val query = words.joinToString("+AND+") { "brand_name:${encode(it)}*" }
        return search(query, limit).distinctBy { it.displayName.lowercase() }
    }

    /**
     * Looks up a scanned barcode. US drug packages carry a UPC-A code that embeds the 10-digit
     * package NDC, which can be split three ways (4-4-2, 5-3-2, 5-4-1).
     */
    suspend fun lookupBarcode(barcode: String): CatalogDrug? {
        val digits = barcode.filter { it.isDigit() }
        val ndc10 = when {
            digits.length == 12 && digits.startsWith("3") -> digits.substring(1, 11)
            digits.length == 13 && digits.startsWith("03") -> digits.substring(2, 12)
            digits.length == 10 -> digits
            digits.length == 11 -> digits // Already an NDC-11; try direct 5-4-2 match below.
            else -> return null
        }
        val candidates = if (ndc10.length == 11) {
            listOf("${ndc10.substring(0, 5)}-${ndc10.substring(5, 9)}-${ndc10.substring(9)}")
        } else {
            listOf(
                "${ndc10.substring(0, 4)}-${ndc10.substring(4, 8)}-${ndc10.substring(8)}",
                "${ndc10.substring(0, 5)}-${ndc10.substring(5, 8)}-${ndc10.substring(8)}",
                "${ndc10.substring(0, 5)}-${ndc10.substring(5, 9)}-${ndc10.substring(9)}",
            )
        }
        val query = candidates.joinToString("+") { "packaging.package_ndc:${encode("\"$it\"")}" }
        return search(query, 1).firstOrNull()
    }

    /** A varied set of real OTC and prescription products for demo inventories. */
    suspend fun demoCatalog(count: Int): List<CatalogDrug> {
        val productType = if (Random.nextBoolean()) "HUMAN OTC DRUG" else "HUMAN PRESCRIPTION DRUG"
        val query = "product_type:${encode("\"$productType\"")}+AND+_exists_:brand_name+AND+_exists_:pharm_class"
        return search(query, limit = count * 3, skip = Random.nextInt(0, 2000))
            .distinctBy { it.name.lowercase() }
            .shuffled()
            .take(count)
    }

    /** Turns a directory entry into an inventory item with plausible demo stock and pricing. */
    fun toDemoMedicine(drug: CatalogDrug, id: String, ownerId: String): Medicine {
        val stock = Random.nextInt(0, 60).let { if (it < 8) it else it * 5 }
        return Medicine(
            id = id,
            ownerId = ownerId,
            name = drug.displayName,
            description = drug.description,
            category = drug.category,
            price = (Random.nextInt(50, 4_500) / 100.0),
            stock = stock,
            lowStockThreshold = 20,
            manufacturer = drug.manufacturer,
            ndc = drug.ndc,
            createdAt = System.currentTimeMillis(),
        )
    }

    private suspend fun search(query: String, limit: Int, skip: Int = 0): List<CatalogDrug> = try {
        api.searchNdc(query, limit.coerceAtMost(100), skip).results.orEmpty().mapNotNull { it.toCatalogDrug() }
    } catch (e: HttpException) {
        // openFDA answers 404 when nothing matches.
        if (e.code() == 404) emptyList() else throw e
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun NdcProduct.toCatalogDrug(): CatalogDrug? {
        val brand = brandName?.takeIf { it.isNotBlank() } ?: genericName?.takeIf { it.isNotBlank() } ?: return null
        val pharmClass = pharmClass.orEmpty()
            .firstOrNull { it.endsWith("[EPC]") }
            ?.removeSuffix("[EPC]")
            ?.trim()
        val category = pharmClass
            ?: dosageForm?.substringBefore(',')
            ?: Medicine.DEFAULT_CATEGORY
        return CatalogDrug(
            name = Formatters.titleCase(brand),
            genericName = genericName?.let(Formatters::titleCase).orEmpty(),
            manufacturer = labelerName?.let(Formatters::titleCase).orEmpty(),
            dosageForm = dosageForm?.let(Formatters::titleCase).orEmpty(),
            category = Formatters.titleCase(category),
            strength = activeIngredients?.singleOrNull()?.strength?.substringBefore('/')?.lowercase().orEmpty(),
            ndc = productNdc.orEmpty(),
        )
    }
}
