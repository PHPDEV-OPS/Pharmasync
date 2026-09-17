package com.example.pharmasync.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * openFDA National Drug Code directory — free, no API key required.
 * https://open.fda.gov/apis/drug/ndc/
 */
interface OpenFdaApi {
    /** [search] must already be URL-encoded (openFDA uses `+` as a term separator). */
    @GET("drug/ndc.json")
    suspend fun searchNdc(
        @Query(value = "search", encoded = true) search: String,
        @Query("limit") limit: Int,
        @Query("skip") skip: Int = 0,
    ): NdcResponse

    companion object {
        const val BASE_URL = "https://api.fda.gov/"
    }
}

data class NdcResponse(
    @SerializedName("results") val results: List<NdcProduct>? = null,
)

data class NdcProduct(
    @SerializedName("product_ndc") val productNdc: String? = null,
    @SerializedName("brand_name") val brandName: String? = null,
    @SerializedName("generic_name") val genericName: String? = null,
    @SerializedName("labeler_name") val labelerName: String? = null,
    @SerializedName("dosage_form") val dosageForm: String? = null,
    @SerializedName("product_type") val productType: String? = null,
    @SerializedName("pharm_class") val pharmClass: List<String>? = null,
    @SerializedName("active_ingredients") val activeIngredients: List<ActiveIngredient>? = null,
)

data class ActiveIngredient(
    @SerializedName("name") val name: String? = null,
    @SerializedName("strength") val strength: String? = null,
)
