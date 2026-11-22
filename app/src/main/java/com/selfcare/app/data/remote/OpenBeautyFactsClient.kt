package com.selfcare.app.data.remote

import com.selfcare.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class OpenBeautyFactsProduct(
    val barcode: String,
    val name: String,
    val brand: String,
    val ingredientsText: String
)

sealed interface OpenBeautyFactsLookupResult {
    data class Found(val product: OpenBeautyFactsProduct) : OpenBeautyFactsLookupResult
    data class NotFound(val barcode: String) : OpenBeautyFactsLookupResult
    data class OfflineOrError(val barcode: String, val reason: String) : OpenBeautyFactsLookupResult
}

sealed interface OpenBeautyFactsSearchResult {
    data class Success(val products: List<OpenBeautyFactsProduct>) : OpenBeautyFactsSearchResult
    data class OfflineOrError(val reason: String) : OpenBeautyFactsSearchResult
}

/**
 * Strictly read-only, anonymous Open Beauty Facts API client.
 * Sends zero user IDs, device IDs, usage telemetry, or images.
 */
class OpenBeautyFactsClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val baseUrl: String = BuildConfig.OPEN_BEAUTY_FACTS_BASE_URL
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchByBarcode(barcode: String): OpenBeautyFactsLookupResult =
        withContext(Dispatchers.IO) {
            val cleanBarcode = barcode.trim()
            if (cleanBarcode.isEmpty()) {
                return@withContext OpenBeautyFactsLookupResult.NotFound(barcode)
            }

            val encoded = URLEncoder.encode(cleanBarcode, StandardCharsets.UTF_8.name())
            val normalizedBase = baseUrl.trimEnd('/')
            val url = "$normalizedBase/api/v2/product/$encoded.json" +
                "?fields=code,product_name,brands,ingredients_text,ingredients_text_en"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        return@withContext OpenBeautyFactsLookupResult.NotFound(cleanBarcode)
                    }
                    if (!response.isSuccessful) {
                        return@withContext OpenBeautyFactsLookupResult.OfflineOrError(
                            barcode = cleanBarcode,
                            reason = "HTTP ${response.code}"
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext OpenBeautyFactsLookupResult.OfflineOrError(
                            barcode = cleanBarcode,
                            reason = "Empty response"
                        )
                    }
                    val dto = json.decodeFromString(ObfProductResponseDto.serializer(), body)
                    val productDto = dto.product
                    if (dto.status != 1 || productDto == null) {
                        return@withContext OpenBeautyFactsLookupResult.NotFound(cleanBarcode)
                    }
                    val mapped = productDto.toDomain(fallbackBarcode = cleanBarcode)
                    if (mapped.name.isBlank() && mapped.ingredientsText.isBlank()) {
                        return@withContext OpenBeautyFactsLookupResult.NotFound(cleanBarcode)
                    }
                    OpenBeautyFactsLookupResult.Found(mapped)
                }
            } catch (e: Exception) {
                OpenBeautyFactsLookupResult.OfflineOrError(
                    barcode = cleanBarcode,
                    reason = e.message ?: e::class.simpleName.orEmpty()
                )
            }
        }

    suspend fun searchByName(query: String): OpenBeautyFactsSearchResult =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
            if (cleanQuery.isEmpty()) {
                return@withContext OpenBeautyFactsSearchResult.Success(emptyList())
            }

            val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.name())
            val normalizedBase = baseUrl.trimEnd('/')
            val url = "$normalizedBase/cgi/search.pl" +
                "?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=10" +
                "&fields=code,product_name,brands,ingredients_text,ingredients_text_en"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext OpenBeautyFactsSearchResult.OfflineOrError(
                            reason = "HTTP ${response.code}"
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext OpenBeautyFactsSearchResult.OfflineOrError(
                            reason = "Empty response"
                        )
                    }
                    val dto = json.decodeFromString(ObfSearchResponseDto.serializer(), body)
                    val products = dto.products
                        .map { it.toDomain(fallbackBarcode = "") }
                        .filter { it.name.isNotBlank() || it.ingredientsText.isNotBlank() }
                    OpenBeautyFactsSearchResult.Success(products)
                }
            } catch (e: Exception) {
                OpenBeautyFactsSearchResult.OfflineOrError(
                    reason = e.message ?: e::class.simpleName.orEmpty()
                )
            }
        }

    @Serializable
    private data class ObfProductResponseDto(
        val status: Int = 0,
        val product: ObfProductDto? = null
    )

    @Serializable
    private data class ObfSearchResponseDto(
        val products: List<ObfProductDto> = emptyList()
    )

    @Serializable
    private data class ObfProductDto(
        val code: String? = null,
        @SerialName("product_name")
        val productName: String? = null,
        val brands: String? = null,
        @SerialName("ingredients_text")
        val ingredientsText: String? = null,
        @SerialName("ingredients_text_en")
        val ingredientsTextEn: String? = null
    ) {
        fun toDomain(fallbackBarcode: String): OpenBeautyFactsProduct {
            val resolvedIngredients = ingredientsTextEn?.takeIf { it.isNotBlank() }
                ?: ingredientsText.orEmpty()
            return OpenBeautyFactsProduct(
                barcode = code?.trim().takeUnless { it.isNullOrEmpty() } ?: fallbackBarcode,
                name = productName?.trim().orEmpty(),
                brand = brands?.trim().orEmpty(),
                ingredientsText = resolvedIngredients.trim()
            )
        }
    }

    companion object {
        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
