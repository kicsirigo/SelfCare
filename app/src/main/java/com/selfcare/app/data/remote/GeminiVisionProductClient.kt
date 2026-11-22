package com.selfcare.app.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.selfcare.app.domain.inci.InciConflictDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    data class Success(val product: IdentifiedProductInfo) : GeminiVisionResult
    data class Error(val reason: String) : GeminiVisionResult
}

interface ProductVisionClient {
    suspend fun identifyProduct(bitmap: Bitmap, apiKey: String): GeminiVisionResult
}

class GeminiVisionProductClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL
) : ProductVisionClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun identifyProduct(bitmap: Bitmap, apiKey: String): GeminiVisionResult =
        withContext(Dispatchers.IO) {
            val cleanKey = apiKey.trim()
            if (cleanKey.isEmpty()) {
                return@withContext GeminiVisionResult.Error("API_KEY_EMPTY")
            }

            try {
                val base64Image = encodeBitmapToBase64Jpeg(bitmap)
                val payload = buildRequestPayload(base64Image)
                val url = "${baseUrl.trimEnd('/')}/v1beta/models/$MODEL_ID:generateContent?key=$cleanKey"
                val request = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext GeminiVisionResult.Error("HTTP_${response.code}")
                    }
                    parseGeminiResponseJson(bodyStr)
                }
            } catch (e: Exception) {
                GeminiVisionResult.Error(e.message ?: "NETWORK_ERROR")
            }
        }

    fun parseGeminiResponseJson(responseBody: String): GeminiVisionResult {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val candidates = root["candidates"]?.jsonArray
            val firstCandidate = candidates?.firstOrNull()?.jsonObject
                ?: return GeminiVisionResult.Error("EMPTY_CANDIDATES")
            val parts = firstCandidate["content"]?.jsonObject?.get("parts")?.jsonArray
            val rawText = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?: return GeminiVisionResult.Error("EMPTY_TEXT")

            val cleanedJson = stripCodeFences(rawText)
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
            GeminiVisionResult.Error("PARSE_ERROR:${e.message}")
        }
    }

    private fun buildRequestPayload(base64Image: String): String {
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", JsonPrimitive(SYSTEM_PROMPT))
                        })
                        add(buildJsonObject {
                            put("inline_data", buildJsonObject {
                                put("mime_type", JsonPrimitive("image/jpeg"))
                                put("data", JsonPrimitive(base64Image))
                            })
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", JsonPrimitive("application/json"))
                put("temperature", JsonPrimitive(0.1))
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
        const val MODEL_ID = "gemini-2.5-flash"

        private const val SYSTEM_PROMPT =
            "Identify the skincare, haircare, or pharmacy/prescription topical product in this photo. " +
                "Return ONLY valid JSON with keys: " +
                "\"name\" (exact product name, empty string if no product visible), " +
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
