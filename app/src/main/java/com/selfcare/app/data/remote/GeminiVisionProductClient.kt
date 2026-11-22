package com.selfcare.app.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.selfcare.app.domain.inci.InciConflictDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

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

class GeminiVisionProductClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL
) : ProductVisionClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun testApiKey(apiKey: String): GeminiKeyTestResult =
        withContext(Dispatchers.IO) {
            val cleanKey = apiKey.trim()
            if (cleanKey.isEmpty()) {
                return@withContext GeminiKeyTestResult.Failure("API key is empty")
            }

            var lastError = "Unknown error"
            for (model in MODEL_FALLBACK_CHAIN) {
                try {
                    val payload = buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("text", JsonPrimitive("Reply with OK"))
                                    })
                                })
                            })
                        })
                    }.toString()

                    val url = "${baseUrl.trimEnd('/')}/v1beta/models/$model:generateContent?key=$cleanKey"
                    val request = Request.Builder()
                        .url(url)
                        .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            return@withContext GeminiKeyTestResult.Success(model)
                        }
                        val apiMsg = extractApiErrorMessage(bodyStr)
                        lastError = "HTTP ${response.code} ($model): $apiMsg"
                        // Auth/key errors won't be fixed by trying another model
                        if (response.code == 400 || response.code == 401 || response.code == 403) {
                            return@withContext GeminiKeyTestResult.Failure(lastError)
                        }
                    }
                } catch (e: Exception) {
                    lastError = "${e::class.simpleName}: ${e.message}"
                }
            }
            GeminiKeyTestResult.Failure(lastError)
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
                return@withContext GeminiVisionResult.Error("Image encode error: ${e.message}")
            }

            var lastError = "Unknown Gemini error"
            for (model in MODEL_FALLBACK_CHAIN) {
                try {
                    val payload = buildRequestPayload(base64Image, disableThinking = model.startsWith("gemini-2.5"))
                    val url = "${baseUrl.trimEnd('/')}/v1beta/models/$model:generateContent?key=$cleanKey"
                    val request = Request.Builder()
                        .url(url)
                        .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val apiMsg = extractApiErrorMessage(bodyStr)
                            lastError = "HTTP ${response.code} ($model): $apiMsg"
                            // If 404/429/503, try next fallback model; if 400/401/403 key error, stop immediately
                            if (response.code == 401 || response.code == 403) {
                                return@withContext GeminiVisionResult.Error(lastError)
                            }
                            return@use
                        }
                        when (val parsed = parseGeminiResponseJson(bodyStr)) {
                            is GeminiVisionResult.Success -> {
                                return@withContext parsed.copy(modelUsed = model)
                            }
                            is GeminiVisionResult.Error -> {
                                lastError = "$model: ${parsed.reason}"
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastError = "$model ${e::class.simpleName}: ${e.message}"
                }
            }
            GeminiVisionResult.Error(lastError)
        }

    fun parseGeminiResponseJson(responseBody: String): GeminiVisionResult {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val candidates = root["candidates"]?.jsonArray
            val firstCandidate = candidates?.firstOrNull()?.jsonObject
                ?: return GeminiVisionResult.Error("EMPTY_CANDIDATES")
            val parts = firstCandidate["content"]?.jsonObject?.get("parts")?.jsonArray
                ?: return GeminiVisionResult.Error("EMPTY_PARTS")

            // Skip any thinking parts (gemini-2.5) and extract the actual JSON text part
            val rawText = parts
                .mapNotNull { it.jsonObject }
                .firstOrNull { part ->
                    part["thought"]?.jsonPrimitive?.booleanOrNull != true &&
                        !part["text"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
                }
                ?.get("text")?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?: parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.trim()
                ?: return GeminiVisionResult.Error("EMPTY_TEXT")

            val cleanedJson = extractJsonObject(stripCodeFences(rawText))
            val dto = json.decodeFromString<GeminiProductDto>(cleanedJson)
            val name = dto.name.trim()
            if (name.isEmpty()) {
                return GeminiVisionResult.Error("NO_PRODUCT_IDENTIFIED")
            }

            GeminiVisionResult.Success(
                IdentifiedProductInfo(
                    name = name,
                    brand = dto.brand.trim(),
                    category = normalizeCategory(dto.category),
                    routineSlot = normalizeSlot(dto.routineSlot),
                    ingredientsText = dto.ingredients.trim()
                )
            )
        } catch (e: Exception) {
            GeminiVisionResult.Error("PARSE_ERROR: ${e.message}")
        }
    }

    private fun extractApiErrorMessage(body: String): String {
        if (body.isBlank()) return "Empty error response"
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?.take(140)
        }.getOrNull() ?: body.take(140)
    }

    private fun buildRequestPayload(base64Image: String, disableThinking: Boolean): String {
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", JsonPrimitive(SYSTEM_PROMPT))
                        })
                        add(buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", JsonPrimitive("image/jpeg"))
                                put("data", JsonPrimitive(base64Image))
                            })
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", JsonPrimitive("application/json"))
                put("temperature", JsonPrimitive(0.1))
                if (disableThinking) {
                    put("thinkingConfig", buildJsonObject {
                        put("thinkingBudget", JsonPrimitive(0))
                    })
                }
            })
        }
        return body.toString()
    }

    private fun encodeBitmapToBase64Jpeg(bitmap: Bitmap, maxDim: Int = 1024): String {
        val largest = max(bitmap.width, bitmap.height)
        val scaled = if (largest > maxDim && largest > 0) {
            val scale = maxDim.toFloat() / largest.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
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
    private data class GeminiProductDto(
        val name: String = "",
        val brand: String = "",
        val category: String = "OTHER",
        val routineSlot: String = "NONE",
        val ingredients: String = ""
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        val MODEL_FALLBACK_CHAIN = listOf(
            "gemini-3.5-flash-lite",
            "gemini-2.5-flash"
        )

        private const val SYSTEM_PROMPT =
            "Identify the skincare, haircare, or pharmacy/prescription topical product in this photo. " +
                "Return ONLY a single JSON object with keys: " +
                "\"name\" (exact product name without promotional tags like NEW, empty string if no product visible), " +
                "\"brand\" (brand/manufacturer), " +
                "\"category\" (one of: OTHER, RX_TREATMENT, SHAMPOO), " +
                "\"routineSlot\" (one of: MORNING, EVENING, BOTH, HAIR, NONE), " +
                "\"ingredients\" (comma-separated INCI active and key ingredients known for this product or visible on the label)."

        private fun stripCodeFences(raw: String): String {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("```")) return trimmed
            return trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }

        private fun extractJsonObject(raw: String): String {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            return if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
        }

        private fun normalizeCategory(raw: String): String = when (raw.trim().uppercase()) {
            "RX_TREATMENT", "RX", "PRESCRIPTION" -> "RX_TREATMENT"
            "SHAMPOO", "HAIR", "CONDITIONER", "SCALP_TONIC", "HAIR_OIL" -> "SHAMPOO"
            else -> "OTHER"
        }

        private fun normalizeSlot(raw: String): String = when (raw.trim().uppercase()) {
            InciConflictDetector.SLOT_MORNING, "AM" -> InciConflictDetector.SLOT_MORNING
            InciConflictDetector.SLOT_EVENING, "PM" -> InciConflictDetector.SLOT_EVENING
            InciConflictDetector.SLOT_BOTH -> InciConflictDetector.SLOT_BOTH
            InciConflictDetector.SLOT_HAIR -> InciConflictDetector.SLOT_HAIR
            else -> InciConflictDetector.SLOT_NONE
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
