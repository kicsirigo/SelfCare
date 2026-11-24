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
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/**
 * Anonymous read-only client for the public USDA FoodData Central API (400,000+ raw ingredients
 * and restaurant/branded foods) using USDA's official DEMO_KEY (no signup required; 30 req/hr, 50/day).
 * Includes a lightweight Hungarian -> English keyword bridge for everyday ingredients.
 */
class UsdaFoodDataClient(
    private val okHttpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val apiKey: String = DEFAULT_DEMO_KEY
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun searchByName(query: String, pageSize: Int = 8): FoodSearchResult = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext FoodSearchResult.Success(emptyList())

        val englishQuery = translateHungarianHint(clean)
        val encoded = URLEncoder.encode(englishQuery, StandardCharsets.UTF_8.name())
        val url = "${baseUrl.trimEnd('/')}/fdc/v1/foods/search?api_key=$apiKey&query=$encoded&pageSize=${pageSize.coerceIn(1, 20)}"
        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        try {
            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.code == 429) {
                    // Gracefully degrade when DEMO_KEY daily/hourly quota is reached
                    return@withContext FoodSearchResult.Success(emptyList())
                }
                if (!resp.isSuccessful) {
                    return@withContext FoodSearchResult.Error("USDA HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                FoodSearchResult.Success(parseSearchResponse(body))
            }
        } catch (e: Exception) {
            FoodSearchResult.Error(e.message ?: "Network error")
        }
    }

    internal fun parseSearchResponse(body: String): List<FoodNutritionItem> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val foods = root["foods"]?.jsonArray.orEmpty()
            foods.mapNotNull { elem -> parseFoodObject(elem.jsonObject) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseFoodObject(obj: JsonObject): FoodNutritionItem? {
        val desc = obj["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (desc.isBlank()) return null
        val brand = obj["brandOwner"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: obj["brandName"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: "USDA"
        val nutrients = obj["foodNutrients"]?.jsonArray.orEmpty()

        var kcal = 0f
        var protein = 0f
        var carbs = 0f
        var fat = 0f
        var fiber = 0f
        var sugar = 0f
        var sodiumMg = 0f

        for (nElem in nutrients) {
            val nObj = nElem.jsonObject
            val id = nObj["nutrientId"]?.jsonPrimitive?.intOrNull ?: -1
            val name = nObj["nutrientName"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            val unit = nObj["unitName"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            val value = nObj["value"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: continue
            when {
                id == 1008 || (name.contains("energy") && unit == "kcal") -> if (kcal == 0f) kcal = value
                id == 1003 || name == "protein" -> protein = value
                id == 1005 || name.startsWith("carbohydrate") -> carbs = value
                id == 1004 || name.startsWith("total lipid") -> fat = value
                id == 1079 || name.startsWith("fiber") -> fiber = value
                id == 2000 || name.startsWith("sugars") || name.startsWith("total sugars") -> sugar = value
                id == 1093 || name.startsWith("sodium") -> sodiumMg = value
            }
        }
        if (kcal <= 0f && protein <= 0f && carbs <= 0f && fat <= 0f) return null
        val saltGrams = (sodiumMg * 2.5f / 1000f).coerceIn(0f, 30f)
        val serving = obj["servingSize"]?.jsonPrimitive?.doubleOrNull?.toFloat()
            ?.takeIf { it in 10f..1000f } ?: 100f

        val fdcId = obj["fdcId"]?.jsonPrimitive?.intOrNull
        val usdaUrl = fdcId?.let { "https://fdc.nal.usda.gov/food-details/$it/nutrients" }

        return FoodNutritionItem(
            barcode = obj["gtinUpc"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
            name = desc,
            brand = brand,
            per100gKcal = kcal.coerceIn(0f, 1000f),
            per100gProtein = protein.coerceIn(0f, 100f),
            per100gCarbs = carbs.coerceIn(0f, 100f),
            per100gFat = fat.coerceIn(0f, 100f),
            per100gFiber = fiber.coerceIn(0f, 100f),
            per100gSugar = sugar.coerceIn(0f, 100f),
            per100gSalt = saltGrams,
            defaultGrams = serving,
            sourceLabel = "USDA FoodData",
            sourceUrl = usdaUrl
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.nal.usda.gov"
        const val DEFAULT_DEMO_KEY = "DEMO_KEY"

        private val huToEnMap = mapOf(
            "csirkemell" to "chicken breast",
            "pulykamell" to "turkey breast",
            "csirkecomb" to "chicken thigh",
            "marhahus" to "beef",
            "sertes" to "pork",
            "lazac" to "salmon",
            "tonhal" to "tuna",
            "tojas" to "egg",
            "turo" to "cottage cheese",
            "joghurt" to "yogurt",
            "sajt" to "cheese",
            "trappista" to "edam cheese",
            "sonka" to "ham",
            "szalami" to "salami",
            "virsli" to "frankfurter sausage",
            "vaj" to "butter",
            "kenyer" to "bread",
            "zsemle" to "bread roll",
            "kifli" to "bread roll",
            "rizs" to "rice",
            "krumpli" to "potato",
            "burgonya" to "potato",
            "edesburgonya" to "sweet potato",
            "teszta" to "pasta",
            "zabpehely" to "oats",
            "paradicsom" to "tomato",
            "uborka" to "cucumber",
            "paprika" to "bell pepper",
            "brokkoli" to "broccoli",
            "karfiol" to "cauliflower",
            "repa" to "carrot",
            "sargarepa" to "carrot",
            "avokado" to "avocado",
            "alma" to "apple",
            "banan" to "banana",
            "narancs" to "orange",
            "eper" to "strawberry",
            "afonya" to "blueberry",
            "mandula" to "almonds",
            "dio" to "walnuts",
            "mogyoro" to "peanuts",
            "szendvics" to "sandwich"
        )

        internal fun translateHungarianHint(raw: String): String {
            val norm = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .trim()
            huToEnMap[norm]?.let { return it }
            val tokens = norm.split(Regex("\\s+"))
            val translated = tokens.map { huToEnMap[it] ?: it }.joinToString(" ")
            return if (translated != norm) translated else raw.trim()
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
