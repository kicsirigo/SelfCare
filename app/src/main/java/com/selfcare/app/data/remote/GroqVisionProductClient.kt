package com.selfcare.app.data.remote

import android.graphics.Bitmap
import android.util.Base64
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

interface ProductVisionClient {
    suspend fun identifyProduct(bitmap: Bitmap, apiKey: String): GeminiVisionResult
    suspend fun testApiKey(apiKey: String): GeminiKeyTestResult = GeminiKeyTestResult.Failure("Not supported")
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

    fun parseGroqResponseJson(responseBody: String): GeminiVisionResult {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val choices = root["choices"]?.jsonArray
            val firstChoice = choices?.firstOrNull()?.jsonObject
                ?: return GeminiVisionResult.Error("EMPTY_CHOICES")
            val rawText = firstChoice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?: return GeminiVisionResult.Error("EMPTY_TEXT")

            val withoutThink = rawText
                .replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
                .trim()

            val cleaned = withoutThink
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonObjStart = cleaned.indexOf('{')
            val jsonObjEnd = cleaned.lastIndexOf('}')
            val jsonSlice = if (jsonObjStart >= 0 && jsonObjEnd > jsonObjStart) {
                cleaned.substring(jsonObjStart, jsonObjEnd + 1)
            } else {
                cleaned
            }

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

    private fun buildVisionPayload(model: String, base64Jpeg: String): String {
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
                            put("text", SYSTEM_PROMPT)
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

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com"
        const val MAX_TRANSIENT_ATTEMPTS = 3
        val RETRY_BACKOFF_MS = longArrayOf(800L, 1800L)
        val DEFAULT_VISION_MODELS = listOf(
            "qwen/qwen3.8-27b",
            "meta-models/muse-spark-open"
        )

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

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
