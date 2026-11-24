package com.selfcare.app.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.selfcare.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class OpenBeautyFactsProduct(
    val barcode: String,
    val name: String,
    val brand: String,
    val ingredientsText: String,
    val imageUrl: String? = null
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
                "?fields=code,product_name,brands,ingredients_text,ingredients_text_en,image_front_small_url,image_front_url,image_url"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
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
        searchByNameOnHost(baseUrl, query)

    private suspend fun searchByNameOnHost(
        hostBaseUrl: String,
        query: String
    ): OpenBeautyFactsSearchResult =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
            if (cleanQuery.isEmpty()) {
                return@withContext OpenBeautyFactsSearchResult.Success(emptyList())
            }

            val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.name())
            val normalizedBase = hostBaseUrl.trimEnd('/')
            val url = "$normalizedBase/cgi/search.pl" +
                "?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=12" +
                "&fields=code,product_name,brands,ingredients_text,ingredients_text_en,image_front_small_url,image_front_url,image_url"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
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

    /**
     * Multi-source anonymous reference image URL collector (OpenBeautyFacts + OpenFoodFacts +
     * Wikimedia Commons) with smart query shortening so vitamins, medications, and skincare
     * products return at least [desiredCount] (default 3) visual confirmation images when available.
     */
    suspend fun findReferenceImageUrls(
        brand: String,
        name: String,
        category: String = "OTHER",
        desiredCount: Int = 3
    ): List<String> = withContext(Dispatchers.IO) {
        val queries = buildSmartReferenceQueries(brand, name)
        if (queries.isEmpty()) return@withContext emptyList()

        val collected = LinkedHashSet<String>()
        val hosts = if (category.equals("VITAMIN", ignoreCase = true)) {
            listOf(OPEN_FOOD_FACTS_BASE_URL, baseUrl)
        } else {
            listOf(baseUrl, OPEN_FOOD_FACTS_BASE_URL)
        }

        for (host in hosts) {
            if (collected.size >= desiredCount) break
            for (q in queries) {
                if (collected.size >= desiredCount) break
                val res = searchByNameOnHost(host, q)
                if (res is OpenBeautyFactsSearchResult.Success) {
                    res.products
                        .mapNotNull { it.imageUrl?.trim() }
                        .filter { it.startsWith("https://", ignoreCase = true) }
                        .forEach { url ->
                            if (collected.size < desiredCount + 2) {
                                collected.add(url)
                            }
                        }
                }
            }
        }

        // Fallback to Wikimedia Commons for pharmacy/Rx/OTC medications or rare products
        if (collected.size < desiredCount) {
            for (q in queries.take(2)) {
                if (collected.size >= desiredCount) break
                val wikiUrls = searchWikimediaImageUrls(q, limit = desiredCount + 2)
                for (u in wikiUrls) {
                    if (collected.size < desiredCount + 2) {
                        collected.add(u)
                    }
                }
            }
        }

        collected.toList()
    }

    suspend fun searchWikimediaImageUrls(query: String, limit: Int = 5): List<String> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isEmpty()) return@withContext emptyList()
            val encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8.name())
            val url = "$WIKIMEDIA_COMMONS_API?action=query&generator=search&gsrnamespace=6" +
                "&gsrsearch=$encoded&gsrlimit=$limit&prop=imageinfo&iiprop=url|mime&iiurlwidth=360&format=json"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext emptyList()
                    val root = json.parseToJsonElement(body).jsonObject
                    val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject
                        ?: return@withContext emptyList()
                    pages.values.mapNotNull { pageEl ->
                        val info = pageEl.jsonObject["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?: return@mapNotNull null
                        val mime = info["mime"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
                        if (!mime.startsWith("image/jpeg") && !mime.startsWith("image/png") && !mime.startsWith("image/webp")) {
                            return@mapNotNull null
                        }
                        val thumb = info["thumburl"]?.jsonPrimitive?.contentOrNull
                            ?: info["url"]?.jsonPrimitive?.contentOrNull
                        thumb?.takeIf { it.startsWith("https://", ignoreCase = true) }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun fetchProductThumbnail(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val clean = imageUrl.trim()
        if (!clean.startsWith("https://", ignoreCase = true)) return@withContext null
        try {
            val request = Request.Builder()
                .url(clean)
                .get()
                .header("User-Agent", USER_AGENT)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()
                if (contentLength > MAX_THUMBNAIL_BYTES) return@withContext null

                val buffer = ByteArray(8 * 1024)
                val out = java.io.ByteArrayOutputStream()
                var totalRead = 0
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        totalRead += read
                        if (totalRead > MAX_THUMBNAIL_BYTES) {
                            return@withContext null
                        }
                        out.write(buffer, 0, read)
                    }
                }
                val bytes = out.toByteArray()
                if (bytes.isEmpty()) return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
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
        val ingredientsTextEn: String? = null,
        @SerialName("image_front_small_url")
        val imageFrontSmallUrl: String? = null,
        @SerialName("image_front_url")
        val imageFrontUrl: String? = null,
        @SerialName("image_url")
        val imageUrl: String? = null
    ) {
        fun toDomain(fallbackBarcode: String): OpenBeautyFactsProduct {
            val resolvedIngredients = ingredientsTextEn?.takeIf { it.isNotBlank() }
                ?: ingredientsText.orEmpty()
            val resolvedImage = imageFrontSmallUrl?.takeIf { it.isNotBlank() }
                ?: imageFrontUrl?.takeIf { it.isNotBlank() }
                ?: imageUrl?.takeIf { it.isNotBlank() }
            return OpenBeautyFactsProduct(
                barcode = code?.trim().takeUnless { it.isNullOrEmpty() } ?: fallbackBarcode,
                name = productName?.trim().orEmpty(),
                brand = brands?.trim().orEmpty(),
                ingredientsText = resolvedIngredients.trim(),
                imageUrl = resolvedImage
            )
        }
    }

    companion object {
        const val MAX_THUMBNAIL_BYTES = 5 * 1024 * 1024
        const val OPEN_FOOD_FACTS_BASE_URL = "https://world.openfoodfacts.org"
        const val WIKIMEDIA_COMMONS_API = "https://commons.wikimedia.org/w/api.php"
        const val USER_AGENT = "SelfCare-Android/2.4.0 (+https://github.com/kicsirigo/SelfCare)"

        private val DOSAGE_OR_PACK_REGEX = Regex(
            """\b\d+([.,/]\d+)?\s*(ml|l|g|mg|mcg|µg|ug|%|iu|ne|db|pcs|tab|caps|tabletta|filmtabletta|kapszula|lágykapszula)\b""",
            RegexOption.IGNORE_CASE
        )
        private val MULTI_SPACE = Regex("""\s+""")

        /**
         * Builds progressively broader search queries by stripping dosages, package counts,
         * and overly verbose suffixes so OpenBeautyFacts / OpenFoodFacts / Wikimedia return hits.
         */
        fun buildSmartReferenceQueries(brand: String, name: String): List<String> {
            val cleanBrand = brand.trim()
            val cleanName = name.trim()
            val strippedName = cleanName
                .replace(DOSAGE_OR_PACK_REGEX, " ")
                .replace(Regex("""[()+]"""), " ")
                .replace(MULTI_SPACE, " ")
                .trim()
            val shortName = strippedName
                .split(" ")
                .filter { it.length >= 2 }
                .take(3)
                .joinToString(" ")
                .trim()

            return listOf(
                "$cleanBrand $cleanName".trim(),
                "$cleanBrand $strippedName".trim(),
                strippedName,
                "$cleanBrand $shortName".trim(),
                shortName
            )
                .map { it.replace(MULTI_SPACE, " ").trim() }
                .filter { it.length >= 3 }
                .distinct()
        }

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}
