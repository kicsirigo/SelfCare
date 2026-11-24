package com.selfcare.app.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.selfcare.app.domain.health.GroundedSourceRef
import com.selfcare.app.domain.inci.InciConflictDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class IdentifiedProductInfo(
    val name: String,
    val brand: String,
    val category: String,
    val routineSlot: String,
    val ingredientsText: String
)

sealed interface GeminiVisionResult {
    data class Success(val product: IdentifiedProductInfo, val modelUsed: String = "") : GeminiVisionResult
    data class Error(val reason: String) : GeminiVisionResult
}

sealed interface GeminiKeyTestResult {
    data class Success(val modelUsed: String) : GeminiKeyTestResult
    data class Failure(val reason: String) : GeminiKeyTestResult
}

data class EstimatedMealInfo(
    val name: String,
    val estimatedGrams: Float,
    val caloriesKcal: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val fiberGrams: Float = 0f,
    val sugarGrams: Float = 0f,
    val saltGrams: Float = 0f,
    val detectedIngredients: String = "",
    val clarificationQuestion: String = ""
)

sealed interface MealVisionResult {
    data class Success(val meal: EstimatedMealInfo, val modelUsed: String = "") : MealVisionResult
    data class Error(val reason: String) : MealVisionResult
}

sealed interface WebSearchMealResult {
    data class Success(
        val meal: EstimatedMealInfo,
        val modelUsed: String = "",
        val sources: List<GroundedSourceRef> = emptyList()
    ) : WebSearchMealResult
    data class Error(val reason: String) : WebSearchMealResult
}

sealed interface NutritionLabelVisionResult {
    data class Success(val item: FoodNutritionItem, val modelUsed: String = "") : NutritionLabelVisionResult
    data class Error(val reason: String) : NutritionLabelVisionResult
}

data class AiCoachChatMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

sealed interface AiCoachChatResult {
    data class Success(val reply: String, val modelUsed: String = "") : AiCoachChatResult
    data class Error(val reason: String) : AiCoachChatResult
}

interface ProductVisionClient {
    suspend fun identifyProduct(bitmap: Bitmap, apiKey: String): GeminiVisionResult
    suspend fun testApiKey(apiKey: String): GeminiKeyTestResult = GeminiKeyTestResult.Failure("Not supported")
    suspend fun estimateMealFromPhoto(bitmap: Bitmap, apiKey: String): MealVisionResult =
        MealVisionResult.Error("Not supported")
    suspend fun estimateMealFromText(query: String, apiKey: String): MealVisionResult =
        MealVisionResult.Error("Not supported")
    suspend fun refineMealEstimation(
        previousMeal: EstimatedMealInfo,
        userClarification: String,
        apiKey: String
    ): MealVisionResult = MealVisionResult.Error("Not supported")
    suspend fun groundMealWithDatabaseFacts(
        initialMeal: EstimatedMealInfo,
        databaseFacts: String,
        apiKey: String
    ): MealVisionResult = MealVisionResult.Error("Not supported")
    suspend fun searchMealOnWeb(
        initialMeal: EstimatedMealInfo,
        apiKey: String
    ): WebSearchMealResult = WebSearchMealResult.Error("Not supported")
    suspend fun lookupFoodBarcodeOnWeb(
        barcode: String,
        apiKey: String
    ): FoodLookupResult = FoodLookupResult.Error("Not supported")
    suspend fun searchFoodOnWeb(
        query: String,
        apiKey: String
    ): FoodSearchResult = FoodSearchResult.Error("Not supported")
    suspend fun lookupProductBarcodeOnWeb(
        barcode: String,
        apiKey: String
    ): ProductWebLookupResult = ProductWebLookupResult.Error("Not supported")
    suspend fun searchProductOnWeb(
        query: String,
        apiKey: String
    ): ProductWebSearchResult = ProductWebSearchResult.Error("Not supported")
    suspend fun extractNutritionLabelFromPhoto(bitmap: Bitmap, apiKey: String): NutritionLabelVisionResult =
        NutritionLabelVisionResult.Error("Not supported")
    suspend fun chatWithCoach(
        systemContext: String,
        history: List<AiCoachChatMessage>,
        userMessage: String,
        apiKey: String
    ): AiCoachChatResult = AiCoachChatResult.Error("Not supported")
}

/**
 * Groq Vision client using OpenAI-compatible chat completions with Meta Llama 4 Scout / Maverick.
 * Fast (~1s), generous free tier (1,000 req/day), and resilient to Gemini AI Studio 503 spikes.
 */
class GroqVisionProductClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val delayFn: suspend (Long) -> Unit = { delay(it) }
) : ProductVisionClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var cachedVisionModels: List<String>? = null

    private fun isTransientRetryableStatus(code: Int): Boolean =
        code == 429 || code == 500 || code == 502 || code == 503 || code == 504

    /**
     * Queries Groq's live `/openai/v1/models` endpoint to discover active vision-capable models
     * enabled on the user's account, preventing hardcoded 404s when preview models rotate.
     */
    private fun discoverVisionModels(cleanKey: String, useCache: Boolean): List<String> {
        if (useCache) {
            cachedVisionModels?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val discovered = runCatching {
            val url = "${baseUrl.trimEnd('/')}/openai/v1/models"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $cleanKey")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<String>()
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray.orEmpty()
                val allIds = data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.trim() }
                    .filter { it.isNotBlank() }
                selectVisionModelsFromCatalog(allIds)
            }
        }.getOrDefault(emptyList())

        val resolved = if (discovered.isNotEmpty()) discovered else DEFAULT_VISION_MODELS
        cachedVisionModels = resolved
        return resolved
    }

    override suspend fun testApiKey(apiKey: String): GeminiKeyTestResult =
        withContext(Dispatchers.IO) {
            val cleanKey = apiKey.trim()
            if (cleanKey.isEmpty()) {
                return@withContext GeminiKeyTestResult.Failure("API key is empty")
            }

            val modelsToTry = discoverVisionModels(cleanKey, useCache = false)
            val errors = mutableListOf<String>()
            val url = "${baseUrl.trimEnd('/')}/openai/v1/chat/completions"
            for (model in modelsToTry) {
                val payload = buildJsonObject {
                    put("model", model)
                    put("max_tokens", 16)
                    put("temperature", 0.0)
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "user")
                            put("content", "Reply with OK")
                        })
                    })
                }.toString()

                for (attempt in 0 until MAX_TRANSIENT_ATTEMPTS) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $cleanKey")
                            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()

                        var shouldRetry = false
                        httpClient.newCall(request).execute().use { response ->
                            val bodyStr = response.body?.string().orEmpty()
                            if (response.isSuccessful) {
                                return@withContext GeminiKeyTestResult.Success(shortModelLabel(model))
                            }
                            val apiMsg = extractApiErrorMessage(bodyStr, cleanKey)
                            val err = "HTTP ${response.code} (${shortModelLabel(model)}): $apiMsg"
                            if (response.code == 400 || response.code == 401 || response.code == 403) {
                                return@withContext GeminiKeyTestResult.Failure(err)
                            }
                            shouldRetry = isTransientRetryableStatus(response.code) &&
                                attempt < MAX_TRANSIENT_ATTEMPTS - 1
                            if (!shouldRetry) {
                                errors.add(err)
                            }
                        }
                        if (shouldRetry) {
                            delayFn(RETRY_BACKOFF_MS[attempt])
                            continue
                        }
                        break
                    } catch (e: Exception) {
                        errors.add(sanitizeExceptionReason(shortModelLabel(model), e))
                        break
                    }
                }
            }
            GeminiKeyTestResult.Failure(errors.joinToString(" | ").ifBlank { "Unknown Groq error" })
        }

    override suspend fun identifyProduct(bitmap: Bitmap, apiKey: String): GeminiVisionResult =
        withContext(Dispatchers.IO) {
            val cleanKey = apiKey.trim()
            if (cleanKey.isEmpty()) {
                return@withContext GeminiVisionResult.Error("API key is empty")
            }

            val base64Image = try {
                encodeBitmapToBase64Jpeg(bitmap)
            } catch (e: Exception) {
                return@withContext GeminiVisionResult.Error("Image encode error (${e::class.simpleName})")
            }

            val modelsToTry = discoverVisionModels(cleanKey, useCache = true)
            val errors = mutableListOf<String>()
            val url = "${baseUrl.trimEnd('/')}/openai/v1/chat/completions"
            for (model in modelsToTry) {
                val payload = buildVisionPayload(model, base64Image)
                val label = shortModelLabel(model)
                for (attempt in 0 until MAX_TRANSIENT_ATTEMPTS) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $cleanKey")
                            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()

                        var shouldRetry = false
                        httpClient.newCall(request).execute().use { response ->
                            val bodyStr = response.body?.string().orEmpty()
                            if (!response.isSuccessful) {
                                val apiMsg = extractApiErrorMessage(bodyStr, cleanKey)
                                val err = "HTTP ${response.code} ($label): $apiMsg"
                                if (response.code == 401 || response.code == 403) {
                                    return@withContext GeminiVisionResult.Error(err)
                                }
                                shouldRetry = isTransientRetryableStatus(response.code) &&
                                    attempt < MAX_TRANSIENT_ATTEMPTS - 1
                                if (!shouldRetry) {
                                    errors.add(err)
                                }
                                return@use
                            }
                            when (val parsed = parseGroqResponseJson(bodyStr)) {
                                is GeminiVisionResult.Success -> {
                                    return@withContext parsed.copy(modelUsed = "$label (Groq)")
                                }
                                is GeminiVisionResult.Error -> {
                                    errors.add("$label: ${parsed.reason}")
                                }
                            }
                        }
                        if (shouldRetry) {
                            delayFn(RETRY_BACKOFF_MS[attempt])
                            continue
                        }
                        break
                    } catch (e: Exception) {
                        errors.add(sanitizeExceptionReason(label, e))
                        break
                    }
                }
            }
            GeminiVisionResult.Error(errors.joinToString(" | ").ifBlank { "Unknown Groq error" })
        }

    override suspend fun estimateMealFromPhoto(bitmap: Bitmap, apiKey: String): MealVisionResult =
        withContext(Dispatchers.IO) {
            val cleanKey = apiKey.trim()
            if (cleanKey.isEmpty()) {
                return@withContext MealVisionResult.Error("API key is empty")
            }

            val base64Image = try {
                encodeBitmapToBase64Jpeg(bitmap)
            } catch (e: Exception) {
                return@withContext MealVisionResult.Error("Image encode error (${e::class.simpleName})")
            }

            val modelsToTry = discoverVisionModels(cleanKey, useCache = true)
            val errors = mutableListOf<String>()
            val url = "${baseUrl.trimEnd('/')}/openai/v1/chat/completions"
            for (model in modelsToTry) {
                val payload = buildVisionPayloadWithPrompt(model, base64Image, MEAL_ESTIMATION_PROMPT)
                val label = shortModelLabel(model)
                for (attempt in 0 until MAX_TRANSIENT_ATTEMPTS) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $cleanKey")
                            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()

                        var shouldRetry = false
                        httpClient.newCall(request).execute().use { response ->
                            val bodyStr = response.body?.string().orEmpty()
                            if (!response.isSuccessful) {
                                val apiMsg = extractApiErrorMessage(bodyStr, cleanKey)
                                val err = "HTTP ${response.code} ($label): $apiMsg"
                                if (response.code == 401 || response.code == 403) {
                                    return@withContext MealVisionResult.Error(err)
                                }
                                shouldRetry = isTransientRetryableStatus(response.code) &&
                                    attempt < MAX_TRANSIENT_ATTEMPTS - 1
                                if (!shouldRetry) errors.add(err)
                                return@use
                            }
                            when (val parsed = parseGroqMealResponseJson(bodyStr)) {
                                is MealVisionResult.Success -> {
                                    return@withContext parsed.copy(modelUsed = "$label (Groq)")
                                }
                                is MealVisionResult.Error -> {
                                    errors.add("$label: ${parsed.reason}")
                                }
                            }
                        }
                        if (shouldRetry) {
                            delayFn(RETRY_BACKOFF_MS[attempt])
                            continue
                        }
                        break
                    } catch (e: Exception) {
                        errors.add(sanitizeExceptionReason(label, e))
                        break
                    }
                }
            }
            MealVisionResult.Error(errors.joinToString(" | ").ifBlank { "Unknown Groq error" })
        }

    override suspend fun estimateMealFromText(query: String, apiKey: String): MealVisionResult =
        withContext(Dispatchers.IO) {
            val cleanKey = apiKey.trim()
            val cleanQuery = query.trim()
            if (cleanKey.isEmpty()) {
                return@withContext MealVisionResult.Error("API key is empty")
            }
            if (cleanQuery.isEmpty()) {
                return@withContext MealVisionResult.Error("Query is empty")
            }

            val modelsToTry = discoverVisionModels(cleanKey, useCache = true)
            val errors = mutableListOf<String>()
            val url = "${baseUrl.trimEnd('/')}/openai/v1/chat/completions"
            for (model in modelsToTry) {
                val payload = buildJsonObject {
                    put("model", model)
                    put("temperature", 0.1)
                    put("max_tokens", 512)
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "user")
                            put("content", "$TEXT_MEAL_ESTIMATION_PROMPT\n\nFood/Meal: $cleanQuery")
                        })
                    })
                }.toString()
                val label = shortModelLabel(model)
                for (attempt in 0 until MAX_TRANSIENT_ATTEMPTS) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $cleanKey")
                            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()

                        var shouldRetry = false
                        httpClient.newCall(request).execute().use { response ->
                            val bodyStr = response.body?.string().orEmpty()
                            if (!response.isSuccessful) {
                                val apiMsg = extractApiErrorMessage(bodyStr, cleanKey)
                                val err = "HTTP ${response.code} ($label): $apiMsg"
                                if (response.code == 401 || response.code == 403) {
                                    return@withContext MealVisionResult.Error(err)
                                }
                                shouldRetry = isTransientRetryableStatus(response.code) &&
                                    attempt < MAX_TRANSIENT_ATTEMPTS - 1
                                if (!shouldRetry) errors.add(err)
                                return@use
                            }
                            when (val parsed = parseGroqMealResponseJson(bodyStr)) {
                                is MealVisionResult.Success -> {
                                    return@withContext parsed.copy(modelUsed = "$label (Groq)")
                                }
                                is MealVisionResult.Error -> {
                                    errors.add("$label: ${parsed.reason}")
                                }
                            }
                        }
                        if (shouldRetry) {
                            delayFn(RETRY_BACKOFF_MS[attempt])
                            continue
                        }
                        break
                    } catch (e: Exception) {
                        errors.add(sanitizeExceptionReason(label, e))
                        break
                    }
                }
            }
            MealVisionResult.Error(errors.joinToString(" | ").ifBlank { "Unknown Groq error" })
        }

    override suspend fun searchMealOnWeb(
        initialMeal: EstimatedMealInfo,
        apiKey: String
    ): WebSearchMealResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext WebSearchMealResult.Error("API key is empty")
        }
        val querySubject = buildString {
            append(initialMeal.name)
            if (initialMeal.detectedIngredients.isNotBlank()) {
                append(" (")
                append(initialMeal.detectedIngredients)
                append(")")
            }
        }
        val prompt = """
            Use browser_search to look up verified nutritional values per 100g (calories, protein, carbs, fat, fiber, sugar, salt) for:
            $querySubject
            Then calculate realistic totals for a ${initialMeal.estimatedGrams.toInt()} g portion.
            Return ONLY a single raw JSON object (no markdown, no explanation) with keys:
            "name" (keep the same language as '${initialMeal.name}'), "estimatedGrams" (number), "caloriesKcal" (int), "proteinGrams" (number), "carbsGrams" (number), "fatGrams" (number), "fiberGrams" (number), "sugarGrams" (number), "saltGrams" (number), "detectedIngredients" (short comma-separated list), "clarificationQuestion" (short question or empty string).
        """.trimIndent()

        val url = "${baseUrl.trimEnd('/')}/openai/v1/chat/completions"
        val errors = mutableListOf<String>()
        for (model in DEFAULT_WEB_SEARCH_MODELS) {
            val payload = buildJsonObject {
                put("model", model)
                put("temperature", 0.1)
                put("max_tokens", 700)
                put(
                    "tools",
                    buildJsonArray {
                        add(buildJsonObject { put("type", "browser_search") })
                    }
                )
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", prompt)
                            }
                        )
                    }
                )
            }.toString()
            val label = shortModelLabel(model)
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $cleanKey")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val apiMsg = extractApiErrorMessage(bodyStr, cleanKey)
                        val err = "HTTP ${response.code} ($label): $apiMsg"
                        if (response.code == 401 || response.code == 403) {
                            return@withContext WebSearchMealResult.Error(err)
                        }
                        errors.add(err)
                        return@use
                    }
                    when (val parsed = parseGroqMealResponseJson(bodyStr)) {
                        is MealVisionResult.Success -> {
                            val webSources = parseWebSearchSources(bodyStr)
                            return@withContext WebSearchMealResult.Success(
                                meal = parsed.meal,
                                modelUsed = "$label Web (Groq)",
                                sources = webSources
                            )
                        }
                        is MealVisionResult.Error -> {
                            errors.add("$label: ${parsed.reason}")
                        }
                    }
                }
            } catch (e: Exception) {
                errors.add(sanitizeExceptionReason(label, e))
            }
        }
        WebSearchMealResult.Error(errors.joinToString(" | ").ifBlank { "Web search unavailable" })
    }

    internal fun parseWebSearchSources(responseBody: String): List<GroundedSourceRef> {
        val out = mutableListOf<GroundedSourceRef>()
        val seenUrls = HashSet<String>()

        fun addUrl(rawUrl: String?, rawTitle: String? = null) {
            val clean = rawUrl?.trim()?.trimEnd('.', ',', ';', ')', ']') ?: return
            if (!clean.startsWith("https://") && !clean.startsWith("http://")) return
            if (!seenUrls.add(clean)) return
            val host = clean.removePrefix("https://").removePrefix("http://").substringBefore('/').removePrefix("www.")
            val title = rawTitle?.trim()?.takeIf { it.isNotBlank() } ?: host
            out.add(
                GroundedSourceRef(
                    title = title.take(72),
                    sourceLabel = "Web Search",
                    url = clean
                )
            )
        }

        runCatching {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val msg = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                ?: return@runCatching

            // 1. Standard Groq executed_tools[].search_results.results[] or executed_tools[].output
            msg["executed_tools"]?.jsonArray?.forEach { toolEl ->
                val toolObj = toolEl.jsonObject
                toolObj["search_results"]?.jsonObject?.get("results")?.jsonArray?.forEach { resEl ->
                    val resObj = resEl.jsonObject
                    addUrl(
                        rawUrl = resObj["url"]?.jsonPrimitive?.contentOrNull,
                        rawTitle = resObj["title"]?.jsonPrimitive?.contentOrNull
                    )
                }
                val outputBlob = toolObj["output"]?.jsonPrimitive?.contentOrNull
                    ?: toolObj["result"]?.toString()
                if (!outputBlob.isNullOrBlank()) {
                    URL_REGEX.findAll(outputBlob).forEach { match ->
                        addUrl(match.value)
                    }
                }
            }

            // 2. OpenAI-style annotations[].url_citation
            msg["annotations"]?.jsonArray?.forEach { annEl ->
                val citation = annEl.jsonObject["url_citation"]?.jsonObject ?: annEl.jsonObject
                addUrl(
                    rawUrl = citation["url"]?.jsonPrimitive?.contentOrNull,
                    rawTitle = citation["title"]?.jsonPrimitive?.contentOrNull
                )
            }
        }
        return out.take(5)
    }

    private data class WebSearchRawOutcome(
        val body: String? = null,
        val modelLabel: String = "",
        val sources: List<GroundedSourceRef> = emptyList(),
        val error: String? = null
    )

    private fun executeWebSearchPrompt(prompt: String, apiKey: String): WebSearchRawOutcome {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) return WebSearchRawOutcome(error = "API key is empty")
        val url = "${baseUrl.trimEnd('/')}/openai/v1/chat/completions"
        val errors = mutableListOf<String>()
        for (model in DEFAULT_WEB_SEARCH_MODELS) {
            val payload = buildJsonObject {
                put("model", model)
                put("temperature", 0.1)
                put("max_tokens", 700)
                put(
                    "tools",
                    buildJsonArray {
                        add(buildJsonObject { put("type", "browser_search") })
                    }
                )
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", prompt)
                            }
                        )
                    }
                )
            }.toString()
            val label = shortModelLabel(model)
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $cleanKey")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val apiMsg = extractApiErrorMessage(bodyStr, cleanKey)
                        val err = "HTTP ${response.code} ($label): $apiMsg"
                        if (response.code == 401 || response.code == 403) {
                            return WebSearchRawOutcome(error = err)
                        }
                        errors.add(err)
                        return@use
                    }
                    return WebSearchRawOutcome(
                        body = bodyStr,
                        modelLabel = "$label Web (Groq)",
                        sources = parseWebSearchSources(bodyStr)
                    )
                }
            } catch (e: Exception) {
                errors.add(sanitizeExceptionReason(label, e))
            }
        }
        return WebSearchRawOutcome(error = errors.joinToString(" | ").ifBlank { "Web search unavailable" })
    }

    override suspend fun lookupFoodBarcodeOnWeb(barcode: String, apiKey: String): FoodLookupResult =
        withContext(Dispatchers.IO) {
            val clean = barcode.trim()
            if (clean.isEmpty()) return@withContext FoodLookupResult.NotFound(barcode)
            val prompt = """
                Use browser_search to look up food product barcode "$clean" and its verified nutrition facts per 100g.
                Return ONLY a single raw JSON object (no markdown, no explanation) with keys:
                "name" (product name, or empty string if barcode not found), "brand" (brand or empty string), "per100gKcal" (number), "per100gProtein" (number), "per100gCarbs" (number), "per100gFat" (number), "per100gFiber" (number), "per100gSugar" (number), "per100gSalt" (number), "defaultGrams" (number, package or serving size in g, default 100).
            """.trimIndent()
            val raw = executeWebSearchPrompt(prompt, apiKey)
            val body = raw.body ?: return@withContext FoodLookupResult.Error(raw.error ?: "Web search failed")
            when (val parsed = parseGroqNutritionLabelJson(body)) {
                is NutritionLabelVisionResult.Success -> {
                    if (parsed.item.name.isBlank() || parsed.item.name == "Scanned Package") {
                        FoodLookupResult.NotFound(clean)
                    } else {
                        FoodLookupResult.Found(
                            parsed.item.copy(
                                barcode = clean,
                                sourceLabel = "Web Search",
                                sourceUrl = raw.sources.firstOrNull()?.url
                            )
                        )
                    }
                }
                is NutritionLabelVisionResult.Error -> FoodLookupResult.NotFound(clean)
            }
        }

    override suspend fun searchFoodOnWeb(query: String, apiKey: String): FoodSearchResult =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isEmpty()) return@withContext FoodSearchResult.Success(emptyList())
            val prompt = """
                Use browser_search to look up verified nutrition facts per 100g for: "$clean" (Hungarian or international food/product).
                Return ONLY a single raw JSON object (no markdown, no explanation) with keys:
                "name" (exact food/product name in the same language as "$clean", empty string if not found), "brand" (brand or empty string), "per100gKcal" (number), "per100gProtein" (number), "per100gCarbs" (number), "per100gFat" (number), "per100gFiber" (number), "per100gSugar" (number), "per100gSalt" (number), "defaultGrams" (number, typical portion in g, default 100).
            """.trimIndent()
            val raw = executeWebSearchPrompt(prompt, apiKey)
            val body = raw.body ?: return@withContext FoodSearchResult.Error(raw.error ?: "Web search failed")
            when (val parsed = parseGroqNutritionLabelJson(body)) {
                is NutritionLabelVisionResult.Success -> FoodSearchResult.Success(
                    listOf(
                        parsed.item.copy(
                            sourceLabel = "Web Search",
                            sourceUrl = raw.sources.firstOrNull()?.url
                        )
                    )
                )
                is NutritionLabelVisionResult.Error -> FoodSearchResult.Success(emptyList())
            }
        }

    override suspend fun lookupProductBarcodeOnWeb(barcode: String, apiKey: String): ProductWebLookupResult =
        withContext(Dispatchers.IO) {
            val clean = barcode.trim()
            if (clean.isEmpty()) return@withContext ProductWebLookupResult.NotFound(barcode)
            val prompt = """
                Use browser_search to look up skincare/haircare/vitamin/medication product barcode "$clean" and its INCI ingredients list.
                Return ONLY a single raw JSON object (no markdown, no explanation) with keys:
                "name" (exact product name, empty string if barcode not found), "brand" (brand), "category" (one of: OTHER, RX_TREATMENT, SHAMPOO, VITAMIN, MEDICATION), "routineSlot" (one of: MORNING, EVENING, BOTH, HAIR, NONE), "ingredients" (comma-separated INCI ingredients or active vitamins/dosages).
            """.trimIndent()
            val raw = executeWebSearchPrompt(prompt, apiKey)
            val body = raw.body ?: return@withContext ProductWebLookupResult.Error(raw.error ?: "Web search failed")
            when (val parsed = parseGroqResponseJson(body)) {
                is GeminiVisionResult.Success -> ProductWebLookupResult.Found(
                    RemoteProductItem(
                        barcode = clean,
                        name = parsed.product.name,
                        brand = parsed.product.brand,
                        ingredientsText = parsed.product.ingredientsText,
                        sourceUrl = raw.sources.firstOrNull()?.url,
                        category = parsed.product.category,
                        routineSlot = parsed.product.routineSlot
                    )
                )
                is GeminiVisionResult.Error -> ProductWebLookupResult.NotFound(clean)
            }
        }

    override suspend fun searchProductOnWeb(query: String, apiKey: String): ProductWebSearchResult =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isEmpty()) return@withContext ProductWebSearchResult.Success(emptyList())
            val prompt = """
                Use browser_search to look up skincare/haircare/vitamin/medication product "$clean" and its INCI ingredients list.
                Return ONLY a single raw JSON object (no markdown, no explanation) with keys:
                "name" (exact product name, empty string if not found), "brand" (brand), "category" (one of: OTHER, RX_TREATMENT, SHAMPOO, VITAMIN, MEDICATION), "routineSlot" (one of: MORNING, EVENING, BOTH, HAIR, NONE), "ingredients" (comma-separated INCI ingredients or active vitamins/dosages).
            """.trimIndent()
            val raw = executeWebSearchPrompt(prompt, apiKey)
            val body = raw.body ?: return@withContext ProductWebSearchResult.Error(raw.error ?: "Web search failed")
            when (val parsed = parseGroqResponseJson(body)) {
                is GeminiVisionResult.Success -> ProductWebSearchResult.Success(
                    listOf(
                        RemoteProductItem(
                            barcode = "",
                            name = parsed.product.name,
                            brand = parsed.product.brand,
                            ingredientsText = parsed.product.ingredientsText,
                            sourceUrl = raw.sources.firstOrNull()?.url,
                            category = parsed.product.category,
                            routineSlot = parsed.product.routineSlot
                        )
                    )
                )
                is GeminiVisionResult.Error -> ProductWebSearchResult.Success(emptyList())
            }
        }

    override suspend fun extractNutritionLabelFromPhoto(bitmap: Bitmap, apiKey: String): NutritionLabelVisionResult =
        withContext(Dispatchers.IO) {
            val cleanKey = apiKey.trim()
            if (cleanKey.isEmpty()) {
                return@withContext NutritionLabelVisionResult.Error("API key is empty")
            }

            val base64Image = try {
                encodeBitmapToBase64Jpeg(bitmap)
            } catch (e: Exception) {
                return@withContext NutritionLabelVisionResult.Error("Image encode error (${e::class.simpleName})")
            }

            val modelsToTry = discoverVisionModels(cleanKey, useCache = true)
            val errors = mutableListOf<String>()
            val url = "${baseUrl.trimEnd('/')}/openai/v1/chat/completions"
            for (model in modelsToTry) {
                val payload = buildVisionPayloadWithPrompt(model, base64Image, NUTRITION_LABEL_PROMPT)
                val label = shortModelLabel(model)
                for (attempt in 0 until MAX_TRANSIENT_ATTEMPTS) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $cleanKey")
                            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()

                        var shouldRetry = false
                        httpClient.newCall(request).execute().use { response ->
                            val bodyStr = response.body?.string().orEmpty()
                            if (!response.isSuccessful) {
                                val apiMsg = extractApiErrorMessage(bodyStr, cleanKey)
                                val err = "HTTP ${response.code} ($label): $apiMsg"
                                if (response.code == 401 || response.code == 403) {
                                    return@withContext NutritionLabelVisionResult.Error(err)
                                }
                                shouldRetry = isTransientRetryableStatus(response.code) &&
                                    attempt < MAX_TRANSIENT_ATTEMPTS - 1
                                if (!shouldRetry) errors.add(err)
                                return@use
                            }
                            when (val parsed = parseGroqNutritionLabelJson(bodyStr)) {
                                is NutritionLabelVisionResult.Success -> {
                                    return@withContext parsed.copy(modelUsed = "$label (Groq)")
                                }
                                is NutritionLabelVisionResult.Error -> {
                                    errors.add("$label: ${parsed.reason}")
                                }
                            }
                        }
                        if (shouldRetry) {
                            delayFn(RETRY_BACKOFF_MS[attempt])
                            continue
                        }
                        break
                    } catch (e: Exception) {
                        errors.add(sanitizeExceptionReason(label, e))
                        break
                    }
                }
            }
            NutritionLabelVisionResult.Error(errors.joinToString(" | ").ifBlank { "Unknown Groq error" })
        }

    private fun extractAssistantText(responseBody: String): Pair<String?, String?> {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val firstChoice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return null to "EMPTY_CHOICES"
        val rawText = firstChoice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?: return null to "EMPTY_TEXT"
        val withoutThink = rawText
            .replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
            .trim()
        return withoutThink to null
    }

    private fun extractJsonObjectSlice(responseBody: String): Pair<String?, String?> {
        val (text, err) = extractAssistantText(responseBody)
        if (err != null || text == null) return null to (err ?: "EMPTY_TEXT")
        val cleaned = text
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        val slice = if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
        return slice to null
    }

    fun parseGroqNutritionLabelJson(responseBody: String): NutritionLabelVisionResult {
        return try {
            val (jsonSlice, err) = extractJsonObjectSlice(responseBody)
            if (err != null || jsonSlice == null) return NutritionLabelVisionResult.Error(err ?: "EMPTY_TEXT")
            val dto = json.decodeFromString(GroqNutritionLabelDto.serializer(), jsonSlice)
            if (dto.per100gKcal <= 0f && dto.per100gProtein <= 0f && dto.per100gCarbs <= 0f && dto.per100gFat <= 0f) {
                return NutritionLabelVisionResult.Error("NO_LABEL_FOUND")
            }
            NutritionLabelVisionResult.Success(
                FoodNutritionItem(
                    barcode = null,
                    name = dto.name.trim().ifBlank { "Scanned Package" },
                    brand = dto.brand.trim(),
                    per100gKcal = dto.per100gKcal.coerceIn(0f, 1000f),
                    per100gProtein = dto.per100gProtein.coerceIn(0f, 100f),
                    per100gCarbs = dto.per100gCarbs.coerceIn(0f, 100f),
                    per100gFat = dto.per100gFat.coerceIn(0f, 100f),
                    per100gFiber = dto.per100gFiber.coerceIn(0f, 100f),
                    per100gSugar = dto.per100gSugar.coerceIn(0f, 100f),
                    per100gSalt = dto.per100gSalt.coerceIn(0f, 100f),
                    defaultGrams = dto.defaultGrams.coerceIn(10f, 1000f)
                )
            )
        } catch (e: Exception) {
            NutritionLabelVisionResult.Error("Parse error: ${e.message ?: "invalid json"}")
        }
    }

    fun parseGroqMealResponseJson(responseBody: String): MealVisionResult {
        return try {
            val (jsonSlice, err) = extractJsonObjectSlice(responseBody)
            if (err != null || jsonSlice == null) return MealVisionResult.Error(err ?: "EMPTY_TEXT")
            val dto = json.decodeFromString(GroqMealDto.serializer(), jsonSlice)
            val name = dto.name.trim()
            if (name.isBlank() || dto.caloriesKcal <= 0) {
                return MealVisionResult.Error("NO_MEAL_IDENTIFIED")
            }
            MealVisionResult.Success(
                EstimatedMealInfo(
                    name = name,
                    estimatedGrams = dto.estimatedGrams.coerceIn(10f, 3000f),
                    caloriesKcal = dto.caloriesKcal.coerceIn(1, 5000),
                    proteinGrams = dto.proteinGrams.coerceIn(0f, 350f),
                    carbsGrams = dto.carbsGrams.coerceIn(0f, 600f),
                    fatGrams = dto.fatGrams.coerceIn(0f, 300f),
                    fiberGrams = dto.fiberGrams.coerceIn(0f, 150f),
                    sugarGrams = dto.sugarGrams.coerceIn(0f, 300f),
                    saltGrams = dto.saltGrams.coerceIn(0f, 30f),
                    detectedIngredients = dto.detectedIngredients.trim(),
                    clarificationQuestion = dto.clarificationQuestion.trim()
                )
            )
        } catch (e: Exception) {
            MealVisionResult.Error("Parse error: ${e.message ?: "invalid json"}")
        }
    }

    fun parseGroqResponseJson(responseBody: String): GeminiVisionResult {
        return try {
            val (jsonSlice, err) = extractJsonObjectSlice(responseBody)
            if (err != null || jsonSlice == null) return GeminiVisionResult.Error(err ?: "EMPTY_TEXT")
            val dto = json.decodeFromString(GroqProductDto.serializer(), jsonSlice)
            val name = dto.name.trim()
            if (name.isBlank()) {
                return GeminiVisionResult.Error("NO_PRODUCT_IDENTIFIED")
            }
            val normalizedCategory = when (dto.category.trim().uppercase()) {
                "RX_TREATMENT", "RX", "PRESCRIPTION" -> "RX_TREATMENT"
                "SHAMPOO", "HAIR", "HAIRCARE" -> "SHAMPOO"
                "VITAMIN", "VITAMINS", "SUPPLEMENT", "SUPPLEMENTS" -> "VITAMIN"
                "MEDICATION", "MEDICINE", "PHARMACY", "OINTMENT", "OTC" -> "MEDICATION"
                else -> "OTHER"
            }
            val normalizedSlot = when (dto.routineSlot.trim().uppercase()) {
                "MORNING", "AM" -> InciConflictDetector.SLOT_MORNING
                "EVENING", "PM", "NIGHT" -> InciConflictDetector.SLOT_EVENING
                "BOTH", "AM/PM" -> InciConflictDetector.SLOT_BOTH
                "HAIR", "SHAMPOO" -> InciConflictDetector.SLOT_HAIR
                else -> when (normalizedCategory) {
                    "SHAMPOO" -> InciConflictDetector.SLOT_HAIR
                    "VITAMIN" -> InciConflictDetector.SLOT_MORNING
                    else -> InciConflictDetector.SLOT_NONE
                }
            }
            GeminiVisionResult.Success(
                IdentifiedProductInfo(
                    name = name,
                    brand = dto.brand.trim(),
                    category = normalizedCategory,
                    routineSlot = normalizedSlot,
                    ingredientsText = dto.ingredients.trim()
                )
            )
        } catch (e: Exception) {
            GeminiVisionResult.Error("Parse error: ${e.message ?: "invalid json"}")
        }
    }

    private fun buildVisionPayload(model: String, base64Jpeg: String): String =
        buildVisionPayloadWithPrompt(model, base64Jpeg, SYSTEM_PROMPT)

    private fun buildVisionPayloadWithPrompt(model: String, base64Jpeg: String, prompt: String): String {
        return buildJsonObject {
            put("model", model)
            put("temperature", 0.1)
            put("max_tokens", 512)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", "data:image/jpeg;base64,$base64Jpeg")
                            })
                        })
                    })
                })
            })
        }.toString()
    }

    private fun extractApiErrorMessage(bodyStr: String, apiKey: String): String {
        val extracted = runCatching {
            val root = json.parseToJsonElement(bodyStr).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val raw = (extracted ?: bodyStr.take(140)).ifBlank { "Request failed" }
        return sanitizeSensitiveText(raw, apiKey)
    }

    private fun sanitizeExceptionReason(model: String, e: Exception): String {
        val raw = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "Network error"
        return "$model: ${sanitizeSensitiveText(raw, "")}"
    }

    private fun sanitizeSensitiveText(text: String, apiKey: String): String {
        var cleaned = text
        if (apiKey.isNotBlank()) {
            cleaned = cleaned.replace(apiKey, "[REDACTED]")
        }
        cleaned = cleaned.replace(Regex("""gsk_[A-Za-z0-9_-]{12,}"""), "[REDACTED]")
        cleaned = cleaned.replace(Regex("""https?://\S+"""), "[URL]")
        return cleaned.take(160)
    }

    private fun encodeBitmapToBase64Jpeg(bitmap: Bitmap): String {
        val maxDim = 1024
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > maxDim && largest > 0) {
            val scale = maxDim.toFloat() / largest.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    @Serializable
    private data class GroqProductDto(
        val name: String = "",
        val brand: String = "",
        val category: String = "OTHER",
        val routineSlot: String = "NONE",
        val ingredients: String = ""
    )

    @Serializable
    private data class GroqMealDto(
        val name: String = "",
        val estimatedGrams: Float = 250f,
        val caloriesKcal: Int = 0,
        val proteinGrams: Float = 0f,
        val carbsGrams: Float = 0f,
        val fatGrams: Float = 0f,
        val fiberGrams: Float = 0f,
        val sugarGrams: Float = 0f,
        val saltGrams: Float = 0f,
        val detectedIngredients: String = "",
        val clarificationQuestion: String = ""
    )

    @Serializable
    private data class GroqNutritionLabelDto(
        val name: String = "",
        val brand: String = "",
        val per100gKcal: Float = 0f,
        val per100gProtein: Float = 0f,
        val per100gCarbs: Float = 0f,
        val per100gFat: Float = 0f,
        val per100gFiber: Float = 0f,
        val per100gSugar: Float = 0f,
        val per100gSalt: Float = 0f,
        val defaultGrams: Float = 100f
    )

    override suspend fun refineMealEstimation(
        previousMeal: EstimatedMealInfo,
        userClarification: String,
        apiKey: String
    ): MealVisionResult {
        val cleanClarification = userClarification.trim()
        if (cleanClarification.isEmpty()) return MealVisionResult.Error("Clarification is empty")
        val combinedQuery = buildString {
            append("Previous meal estimate: ").append(previousMeal.name)
            if (previousMeal.detectedIngredients.isNotBlank()) {
                append(" (guessed ingredients: ").append(previousMeal.detectedIngredients).append(")")
            }
            append(", ").append(previousMeal.estimatedGrams.toInt()).append("g, ")
            append(previousMeal.caloriesKcal).append(" kcal. ")
            append("User clarification / exact ingredients: ").append(cleanClarification)
            append(". Recalculate exact total grams, calories, protein, carbs, fat, fiber, sugar, salt, and updated detectedIngredients.")
        }
        return estimateMealFromText(combinedQuery, apiKey)
    }

    override suspend fun groundMealWithDatabaseFacts(
        initialMeal: EstimatedMealInfo,
        databaseFacts: String,
        apiKey: String
    ): MealVisionResult {
        if (databaseFacts.isBlank()) return MealVisionResult.Success(initialMeal)
        val groundedQuery = buildString {
            append("Meal identified: ").append(initialMeal.name)
            if (initialMeal.detectedIngredients.isNotBlank()) {
                append(" | Ingredients: ").append(initialMeal.detectedIngredients)
            }
            append(" | Estimated total weight: ").append(initialMeal.estimatedGrams.toInt()).append("g.\n")
            append("VERIFIED NUTRITION DATABASE FACTS (per 100g):\n")
            append(databaseFacts).append("\n")
            append("Recalculate the exact calories, protein, carbs, fat, fiber, sugar, and salt for this meal using the verified per-100g database facts above. Keep the meal name in the same language and preserve any helpful clarificationQuestion.")
        }
        return estimateMealFromText(groundedQuery, apiKey)
    }

    override suspend fun chatWithCoach(
        systemContext: String,
        history: List<AiCoachChatMessage>,
        userMessage: String,
        apiKey: String
    ): AiCoachChatResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val cleanMsg = userMessage.trim()
        if (cleanKey.isEmpty()) return@withContext AiCoachChatResult.Error("Groq API key is missing")
        if (cleanMsg.isEmpty()) return@withContext AiCoachChatResult.Error("Message is empty")

        val modelsToTry = discoverVisionModels(cleanKey, useCache = true)
        val url = "${baseUrl.trimEnd('/')}/openai/v1/chat/completions"
        var lastError = "Unable to reach Groq AI"
        for (model in modelsToTry) {
            val payload = buildCoachChatPayload(model, systemContext, history, cleanMsg)
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $cleanKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                httpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val parsed = parseGroqCoachReplyJson(bodyStr)
                        if (parsed is AiCoachChatResult.Success) {
                            return@withContext parsed.copy(modelUsed = shortModelLabel(model))
                        }
                        if (parsed is AiCoachChatResult.Error) {
                            lastError = parsed.reason
                        }
                    } else if (resp.code == 401 || resp.code == 403) {
                        return@withContext AiCoachChatResult.Error("Invalid Groq API key (HTTP ${resp.code})")
                    } else {
                        lastError = "Groq HTTP ${resp.code}"
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
            }
        }
        AiCoachChatResult.Error(lastError)
    }

    internal fun parseGroqCoachReplyJson(rawJson: String): AiCoachChatResult {
        return try {
            val (cleaned, err) = extractAssistantText(rawJson)
            if (err != null || cleaned.isNullOrEmpty()) {
                AiCoachChatResult.Error(err ?: "Empty AI response")
            } else {
                AiCoachChatResult.Success(reply = cleaned)
            }
        } catch (e: Exception) {
            AiCoachChatResult.Error("Parse error: ${e.message}")
        }
    }

    private fun buildCoachChatPayload(
        model: String,
        systemContext: String,
        history: List<AiCoachChatMessage>,
        userMessage: String
    ): String {
        val systemFull = COACH_SYSTEM_PROMPT + "\n\n" + systemContext
        val obj = buildJsonObject {
            put("model", model)
            put("temperature", 0.4)
            put("max_tokens", 700)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", systemFull)
                        }
                    )
                    history.takeLast(10).forEach { msg ->
                        add(
                            buildJsonObject {
                                put("role", if (msg.role == "assistant") "assistant" else "user")
                                put("content", msg.content)
                            }
                        )
                    }
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", userMessage)
                        }
                    )
                }
            )
        }
        return obj.toString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com"
        const val MAX_TRANSIENT_ATTEMPTS = 3
        val RETRY_BACKOFF_MS = longArrayOf(800L, 1800L)
        val DEFAULT_VISION_MODELS = listOf(
            "qwen/qwen3.8-27b",
            "meta-models/muse-spark-open"
        )
        val DEFAULT_WEB_SEARCH_MODELS = listOf(
            "openai/gpt-oss-20b",
            "openai/gpt-oss-120b"
        )
        private val URL_REGEX = Regex("""https?://[^\s"'<>\\]+""")

        fun selectVisionModelsFromCatalog(modelIds: List<String>): List<String> {
            val visionCandidates = modelIds.filter { id ->
                val lower = id.lowercase()
                !lower.contains("guard") &&
                    !lower.contains("whisper") &&
                    !lower.contains("tts") &&
                    (
                        lower.contains("qwen3.8") ||
                            lower.contains("qwen3.6") ||
                            lower.contains("muse-spark") ||
                            lower.contains("vision") ||
                            lower.contains("-vl") ||
                            lower.contains("pixtral") ||
                            lower.contains("llava") ||
                            lower.contains("llama-4-scout")
                        )
            }
            return visionCandidates.sortedBy { id ->
                val lower = id.lowercase()
                when {
                    lower.contains("qwen3.8") -> 0
                    lower.contains("muse-spark") -> 1
                    lower.contains("qwen3.6") -> 2
                    lower.contains("vision") || lower.contains("-vl") -> 3
                    else -> 4
                }
            }
        }

        fun shortModelLabel(fullModelId: String): String =
            fullModelId.substringAfter('/').removeSuffix("-instruct")

        private const val SYSTEM_PROMPT =
            "Identify the skincare, haircare, vitamin/supplement, or medication/pharmacy ointment product in this photo. " +
                "Return ONLY a single raw JSON object (no markdown, no explanation) with keys: " +
                "\"name\" (exact product name without promotional tags like NEW, empty string if no product visible), " +
                "\"brand\" (brand/manufacturer), " +
                "\"category\" (one of: OTHER, RX_TREATMENT, SHAMPOO, VITAMIN, MEDICATION), " +
                "\"routineSlot\" (one of: MORNING, EVENING, BOTH, HAIR, NONE), " +
                "\"ingredients\" (comma-separated key active ingredients or vitamins/dosages visible or known for this exact product, e.g. 'Niacinamide, Salicylic Acid' or 'Vitamin D3 2000 IU')."

        private const val MEAL_ESTIMATION_PROMPT =
            "Identify the meal or food on this photo (e.g. sandwich, stew, salad, pastry) and estimate realistic portion nutrition INCLUDING salt content for the ENTIRE visible serving. " +
                "Return ONLY a single raw JSON object (no markdown, no explanation) with keys: " +
                "\"name\" (short descriptive meal name, empty string if no food visible), " +
                "\"estimatedGrams\" (number, estimated total grams of food), " +
                "\"caloriesKcal\" (integer, total kcal), " +
                "\"proteinGrams\" (number), " +
                "\"carbsGrams\" (number), " +
                "\"fatGrams\" (number), " +
                "\"fiberGrams\" (number), " +
                "\"sugarGrams\" (number), " +
                "\"saltGrams\" (number, estimated total salt in grams, e.g. 1.8 for a ham sandwich or 3.2 for chicken paprikash), " +
                "\"detectedIngredients\" (short comma-separated list of the ingredients you see or assume are inside, e.g. '2 slices white bread, butter, ham, cheese'), " +
                "\"clarificationQuestion\" (if fillings/sauce/bread type are hidden or ambiguous — e.g. a closed sandwich, wrap, burger, or stew — ask ONE short friendly question asking what else is inside; otherwise empty string)."

        private const val TEXT_MEAL_ESTIMATION_PROMPT =
            "Estimate realistic nutrition INCLUDING salt content for the described Hungarian or international dish OR natural-language ingredient list (e.g. '1 császárzsemle, kis vaj, 2 szelet sonka, 1 szelet sajt, uborka'). Sum every mentioned ingredient realistically (default to typical single portion sizes if grams aren't given). " +
                "Return ONLY a single raw JSON object (no markdown, no explanation) with keys: " +
                "\"name\" (clean meal name in the same language as the user's input), " +
                "\"estimatedGrams\" (number, total grams of the portion), " +
                "\"caloriesKcal\" (integer, total kcal), " +
                "\"proteinGrams\" (number), " +
                "\"carbsGrams\" (number), " +
                "\"fatGrams\" (number), " +
                "\"fiberGrams\" (number), " +
                "\"sugarGrams\" (number), " +
                "\"saltGrams\" (number, estimated total salt in grams), " +
                "\"detectedIngredients\" (short comma-separated list of ingredients and assumed quantities used in the calculation, in the same language as the user's input), " +
                "\"clarificationQuestion\" (if key calorie drivers like sauce/butter/bread size or filling are unspecified, ask ONE short friendly question in the user's language; otherwise empty string)."

        private const val NUTRITION_LABEL_PROMPT =
            "Read the Nutrition Facts / Tápérték táblázat (values per 100g or 100ml) from this food package photo. " +
                "Return ONLY a single raw JSON object (no markdown, no explanation) with keys: " +
                "\"name\" (product name if visible, else 'Scanned Food'), " +
                "\"brand\" (brand if visible, else empty string), " +
                "\"per100gKcal\" (number, energy in kcal per 100g), " +
                "\"per100gProtein\" (number, protein/fehérje g per 100g), " +
                "\"per100gCarbs\" (number, carbohydrate/szénhidrát g per 100g), " +
                "\"per100gFat\" (number, fat/zsír g per 100g), " +
                "\"per100gFiber\" (number, fiber/rost g per 100g, 0 if not listed), " +
                "\"per100gSugar\" (number, sugars/cukrok g per 100g, 0 if not listed), " +
                "\"per100gSalt\" (number, salt/só g per 100g, 0 if not listed), " +
                "\"defaultGrams\" (number, package or serving weight in g if visible, else 100)."

        private const val COACH_SYSTEM_PROMPT =
            "You are SelfCare AI Coach (powered by Qwen) — a warm, sharp, human nutrition & self-care coach. " +
                "STRICT STYLE RULES:\n" +
                "1. DO NOT repeat or list back the raw numbers, calories, grams, or ml that the user already sees on their dashboard! Mention at most 1-2 numbers ONLY if strictly needed to explain a concrete recommendation. Focus on qualitative insights, patterns, and practical next-meal/hydration ideas.\n" +
                "2. ALWAYS respect CURRENT LOCAL TIME from the context! For example, in the morning (e.g. 09:00), NEVER criticize low daily totals or missing lunch/dinner/water — instead comment on how their morning started and suggest what to focus on for the next meal.\n" +
                "3. Keep replies short and punchy: 3-5 natural sentences (or 2-3 brief bullet points). No giant headings, no emojis, no stat tables.\n" +
                "4. Reply in the exact same language as the user. If Hungarian, ALWAYS use natural singular informal 2nd-person ('tegezés': 'Szia', 'ettél', 'érdemes') — NEVER plural 'Sziasztok' and NEVER confuse 1st person ('ettem') with 2nd person ('ettél').\n" +
                "5. Base your advice ONLY on the anonymized context below. Never give medical diagnoses."

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
