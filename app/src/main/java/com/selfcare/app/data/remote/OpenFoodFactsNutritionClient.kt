package com.selfcare.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class FoodNutritionItem(
    val barcode: String?,
    val name: String,
    val brand: String,
    val per100gKcal: Float,
    val per100gProtein: Float,
    val per100gCarbs: Float,
    val per100gFat: Float,
    val per100gFiber: Float = 0f,
    val per100gSugar: Float = 0f,
    val per100gSalt: Float = 0f,
    val defaultGrams: Float = 100f,
    val sourceLabel: String = "OpenFoodFacts",
    val sourceUrl: String? = null
)

sealed interface FoodLookupResult {
    data class Found(val item: FoodNutritionItem) : FoodLookupResult
    data class NotFound(val query: String) : FoodLookupResult
    data class Error(val reason: String) : FoodLookupResult
}

sealed interface FoodSearchResult {
    data class Success(val items: List<FoodNutritionItem>) : FoodSearchResult
    data class Error(val reason: String) : FoodSearchResult
}

/**
 * Anonymous, read-only OpenFoodFacts nutrition client returning per-100g calories & macros.
 */
class OpenFoodFactsNutritionClient(
    private val okHttpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetchByBarcode(barcode: String): FoodLookupResult = withContext(Dispatchers.IO) {
        val clean = barcode.trim()
        if (clean.isEmpty()) return@withContext FoodLookupResult.NotFound(barcode)

        val encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8.name())
        val url = "${baseUrl.trimEnd('/')}/api/v2/product/$encoded.json" +
            "?fields=code,product_name,brands,nutriments,serving_quantity"
        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@withContext FoodLookupResult.NotFound(clean)
                if (!resp.isSuccessful) return@withContext FoodLookupResult.Error("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                if (root["status"]?.jsonPrimitive?.intOrNull != 1) {
                    return@withContext FoodLookupResult.NotFound(clean)
                }
                val prod = root["product"]?.jsonObject
                    ?: return@withContext FoodLookupResult.NotFound(clean)
                val parsed = parseProductObject(prod, fallbackBarcode = clean)
                    ?: return@withContext FoodLookupResult.NotFound(clean)
                FoodLookupResult.Found(parsed)
            }
        } catch (e: Exception) {
            FoodLookupResult.Error(e.message ?: e::class.simpleName ?: "Network error")
        }
    }

    suspend fun searchByName(query: String): FoodSearchResult = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext FoodSearchResult.Success(emptyList())

        val huResult = executeSearch(clean, hungarianOnly = true)
        val huItems = (huResult as? FoodSearchResult.Success)?.items.orEmpty()
        if (huItems.size >= 6) {
            return@withContext FoodSearchResult.Success(huItems)
        }

        // Supplement with global OpenFoodFacts catalog for international brands sold in Hungary
        val globalResult = executeSearch(clean, hungarianOnly = false)
        val globalItems = (globalResult as? FoodSearchResult.Success)?.items.orEmpty()
        if (huItems.isEmpty() && globalItems.isEmpty()) {
            return@withContext (huResult as? FoodSearchResult.Error)
                ?: (globalResult as? FoodSearchResult.Error)
                ?: FoodSearchResult.Success(emptyList())
        }
        val merged = (huItems + globalItems)
            .distinctBy { "${it.name.lowercase()}|${it.brand.lowercase()}" }
            .take(20)
        FoodSearchResult.Success(merged)
    }

    private fun executeSearch(query: String, hungarianOnly: Boolean): FoodSearchResult {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val localeFilter = if (hungarianOnly) "&lc=hu&cc=hu" else ""
        val url = "${baseUrl.trimEnd('/')}/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=20$localeFilter" +
            "&fields=code,product_name,product_name_hu,brands,nutriments,serving_quantity"
        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        return try {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return FoodSearchResult.Error("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val products = root["products"]?.jsonArray.orEmpty()
                val items = products.mapNotNull { elem ->
                    parseProductObject(elem.jsonObject, fallbackBarcode = null)
                }
                FoodSearchResult.Success(items)
            }
        } catch (e: Exception) {
            FoodSearchResult.Error(e.message ?: e::class.simpleName ?: "Network error")
        }
    }

    private fun parseProductObject(obj: JsonObject, fallbackBarcode: String?): FoodNutritionItem? {
        val name = obj["product_name_hu"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            ?: obj["product_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (name.isBlank()) return null
        val brand = obj["brands"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val barcode = obj["code"]?.jsonPrimitive?.contentOrNull?.trim() ?: fallbackBarcode
        val servingQty = obj["serving_quantity"]?.jsonPrimitive?.doubleOrNull?.toFloat()
            ?.takeIf { it in 5f..1000f } ?: 100f
        val nutriments = obj["nutriments"]?.jsonObject ?: return null

        val kcal = readNumber(nutriments, "energy-kcal_100g")
            ?: readNumber(nutriments, "energy-kcal")
            ?: readNumber(nutriments, "energy-kj_100g")?.let { it / 4.184f }
            ?: 0f
        val protein = readNumber(nutriments, "proteins_100g") ?: 0f
        val carbs = readNumber(nutriments, "carbohydrates_100g") ?: 0f
        val fat = readNumber(nutriments, "fat_100g") ?: 0f
        val fiber = readNumber(nutriments, "fiber_100g") ?: 0f
        val sugar = readNumber(nutriments, "sugars_100g") ?: 0f
        val salt = readNumber(nutriments, "salt_100g")
            ?: readNumber(nutriments, "sodium_100g")?.let { it * 2.5f }
            ?: 0f

        val productUrl = barcode?.takeIf { it.isNotBlank() }?.let {
            "https://hu.openfoodfacts.org/product/$it"
        }

        return FoodNutritionItem(
            barcode = barcode,
            name = name,
            brand = brand,
            per100gKcal = kcal.coerceAtLeast(0f),
            per100gProtein = protein.coerceAtLeast(0f),
            per100gCarbs = carbs.coerceAtLeast(0f),
            per100gFat = fat.coerceAtLeast(0f),
            per100gFiber = fiber.coerceAtLeast(0f),
            per100gSugar = sugar.coerceAtLeast(0f),
            per100gSalt = salt.coerceAtLeast(0f),
            defaultGrams = servingQty,
            sourceLabel = "OpenFoodFacts",
            sourceUrl = productUrl
        )
    }

    private fun readNumber(obj: JsonObject, key: String): Float? {
        val prim = obj[key]?.jsonPrimitive ?: return null
        return prim.doubleOrNull?.toFloat() ?: prim.contentOrNull?.toFloatOrNull()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://world.openfoodfacts.org"
        private const val USER_AGENT = "SelfCareAndroid/3.5 (https://github.com/kicsirigo/SelfCare)"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
