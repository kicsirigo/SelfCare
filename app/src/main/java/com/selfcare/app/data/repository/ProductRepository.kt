package com.selfcare.app.data.repository

import android.graphics.Bitmap
import com.selfcare.app.data.local.dao.ProductDao
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.remote.OpenBeautyFactsClient
import com.selfcare.app.data.remote.OpenBeautyFactsLookupResult
import com.selfcare.app.data.remote.OpenBeautyFactsProduct
import com.selfcare.app.data.remote.OpenBeautyFactsSearchResult
import com.selfcare.app.domain.inci.DetectedConflict
import com.selfcare.app.domain.inci.InciEngine
import com.selfcare.app.domain.inci.InciMatch
import kotlinx.coroutines.flow.Flow
import java.time.Clock

sealed interface BarcodeLookupOutcome {
    data class LocalFound(val product: ProductEntity) : BarcodeLookupOutcome
    data class RemoteFound(
        val product: OpenBeautyFactsProduct,
        val parsedMatches: List<InciMatch>
    ) : BarcodeLookupOutcome
    data class NotFound(val barcode: String) : BarcodeLookupOutcome
    data class OfflineFallback(val barcode: String, val reason: String) : BarcodeLookupOutcome
}

class ProductRepository(
    private val productDao: ProductDao,
    private val openBeautyFactsClient: OpenBeautyFactsClient,
    private val inciEngine: InciEngine,
    private val clock: Clock = Clock.systemDefaultZone()
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

    suspend fun fetchProductThumbnail(imageUrl: String): Bitmap? =
        openBeautyFactsClient.fetchProductThumbnail(imageUrl)

    suspend fun getByBarcode(barcode: String): ProductEntity? =
        productDao.getByBarcode(barcode.trim())

    suspend fun lookupByBarcode(barcode: String): BarcodeLookupOutcome {
        val clean = barcode.trim()
        if (clean.isEmpty()) return BarcodeLookupOutcome.NotFound(barcode)

        val cached = productDao.getByBarcode(clean)
        if (cached != null) {
            return BarcodeLookupOutcome.LocalFound(cached)
        }

        return when (val remote = openBeautyFactsClient.fetchByBarcode(clean)) {
            is OpenBeautyFactsLookupResult.Found -> {
                val matches = inciEngine.parse(remote.product.ingredientsText)
                BarcodeLookupOutcome.RemoteFound(remote.product, matches)
            }
            is OpenBeautyFactsLookupResult.NotFound -> BarcodeLookupOutcome.NotFound(clean)
            is OpenBeautyFactsLookupResult.OfflineOrError ->
                BarcodeLookupOutcome.OfflineFallback(clean, remote.reason)
        }
    }

    suspend fun searchRemoteByName(query: String): OpenBeautyFactsSearchResult =
        openBeautyFactsClient.searchByName(query)

    suspend fun saveProduct(
        id: Long = 0L,
        name: String,
        brand: String = "",
        barcode: String? = null,
        category: String = "OTHER",
        routineSlot: String = "NONE",
        isDefaultInRoutine: Boolean = false,
        rawIngredientsText: String = "",
        source: String = "MANUAL"
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
            updatedAtEpochMillis = now
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
        productDao.deleteById(id)
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
