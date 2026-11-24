package com.selfcare.app.data.repository

import android.graphics.Bitmap
import com.selfcare.app.data.local.dao.ProductDao
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.remote.ProductVisionClient
import com.selfcare.app.data.remote.ProductWebLookupResult
import com.selfcare.app.data.remote.ProductWebSearchResult
import com.selfcare.app.data.remote.RemoteProductItem
import com.selfcare.app.domain.inci.DetectedConflict
import com.selfcare.app.domain.inci.InciEngine
import com.selfcare.app.domain.inci.InciMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Clock
import java.util.UUID

sealed interface BarcodeLookupOutcome {
    data class LocalFound(val product: ProductEntity) : BarcodeLookupOutcome
    data class RemoteFound(
        val product: RemoteProductItem,
        val parsedMatches: List<InciMatch>
    ) : BarcodeLookupOutcome
    data class NotFound(val barcode: String) : BarcodeLookupOutcome
    data class OfflineFallback(val barcode: String, val reason: String) : BarcodeLookupOutcome
}

class ProductRepository(
    private val productDao: ProductDao,
    private val inciEngine: InciEngine,
    private val visionClient: ProductVisionClient? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val productsPhotoDir: File? = null
) {

    fun observeAllProducts(): Flow<List<ProductEntity>> = productDao.observeAll()

    fun searchLocal(query: String): Flow<List<ProductEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            productDao.observeAll()
        } else {
            productDao.search(trimmed)
        }
    }

    suspend fun getAllOnce(): List<ProductEntity> = productDao.getAllOnce()

    suspend fun getById(id: Long): ProductEntity? = productDao.getById(id)

    suspend fun saveProductPhoto(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val dir = productsPhotoDir ?: return@withContext null
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            val nomedia = File(dir, ".nomedia")
            if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
            val target = File(dir, "prod_${UUID.randomUUID()}.jpg")
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            target.absolutePath
        }.getOrNull()
    }

    private fun deleteProductPhotoFile(path: String?) {
        val dir = productsPhotoDir ?: return
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path).canonicalFile
            val root = dir.canonicalFile
            if (file.path.startsWith(root.path + File.separator) && file.exists()) {
                file.delete()
            }
        }
    }

    suspend fun getByBarcode(barcode: String): ProductEntity? =
        productDao.getByBarcode(barcode.trim())

    suspend fun lookupByBarcode(barcode: String, apiKey: String = ""): BarcodeLookupOutcome {
        val clean = barcode.trim()
        if (clean.isEmpty()) return BarcodeLookupOutcome.NotFound(barcode)

        val cached = productDao.getByBarcode(clean)
        if (cached != null) {
            return BarcodeLookupOutcome.LocalFound(cached)
        }

        val client = visionClient
        if (client == null || apiKey.isBlank()) {
            return BarcodeLookupOutcome.OfflineFallback(clean, "Groq API key required for Web Search")
        }

        return when (val remote = client.lookupProductBarcodeOnWeb(clean, apiKey)) {
            is ProductWebLookupResult.Found -> {
                val matches = inciEngine.parse(remote.product.ingredientsText)
                BarcodeLookupOutcome.RemoteFound(remote.product, matches)
            }
            is ProductWebLookupResult.NotFound -> BarcodeLookupOutcome.NotFound(clean)
            is ProductWebLookupResult.Error ->
                BarcodeLookupOutcome.OfflineFallback(clean, remote.reason)
        }
    }

    suspend fun searchRemoteByName(query: String, apiKey: String = ""): ProductWebSearchResult {
        val client = visionClient
        if (client == null || apiKey.isBlank()) {
            return ProductWebSearchResult.Error("Groq API key required for Web Search")
        }
        return client.searchProductOnWeb(query, apiKey)
    }

    suspend fun saveProduct(
        id: Long = 0L,
        name: String,
        brand: String = "",
        barcode: String? = null,
        category: String = "OTHER",
        routineSlot: String = "NONE",
        isDefaultInRoutine: Boolean = false,
        rawIngredientsText: String = "",
        source: String = "MANUAL",
        photoPath: String? = null
    ): ProductEntity {
        val now = clock.millis()
        val existing = if (id > 0L) productDao.getById(id) else null
        val normalizedBarcode = barcode?.trim()?.takeIf { it.isNotEmpty() }
        val matches = inciEngine.parse(rawIngredientsText)
        val matchedJson = inciEngine.encodeMatches(matches)

        val entity = ProductEntity(
            id = existing?.id ?: 0L,
            name = name.trim(),
            brand = brand.trim(),
            barcode = normalizedBarcode,
            category = category.trim().ifEmpty { "OTHER" },
            routineSlot = routineSlot.uppercase(),
            isDefaultInRoutine = isDefaultInRoutine,
            rawIngredientsText = rawIngredientsText.trim(),
            matchedInciJson = matchedJson,
            source = source.ifBlank { existing?.source ?: "MANUAL" },
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            photoPath = photoPath ?: existing?.photoPath
        )

        val insertedId = if (existing == null) {
            productDao.insert(entity)
        } else {
            productDao.update(entity)
            entity.id
        }
        return entity.copy(id = insertedId)
    }

    suspend fun deleteProduct(id: Long) {
        val existing = productDao.getById(id)
        productDao.deleteById(id)
        deleteProductPhotoFile(existing?.photoPath)
    }

    suspend fun setRoutineSlot(
        id: Long,
        routineSlot: String,
        isDefaultInRoutine: Boolean
    ) {
        val existing = productDao.getById(id) ?: return
        productDao.update(
            existing.copy(
                routineSlot = routineSlot.uppercase(),
                isDefaultInRoutine = isDefaultInRoutine,
                updatedAtEpochMillis = clock.millis()
            )
        )
    }

    fun parseIngredients(rawIngredientsText: String): List<InciMatch> =
        inciEngine.parse(rawIngredientsText)

    fun parseProductMatches(product: ProductEntity): List<InciMatch> =
        inciEngine.decodeMatches(product.matchedInciJson).ifEmpty {
            inciEngine.parse(product.rawIngredientsText)
        }

    fun computeShelfConflicts(products: List<ProductEntity>): List<DetectedConflict> =
        inciEngine.detectRoutineConflicts(products)
}
