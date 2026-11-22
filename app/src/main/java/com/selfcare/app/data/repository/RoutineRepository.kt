package com.selfcare.app.data.repository

import com.selfcare.app.data.local.dao.ProductDao
import com.selfcare.app.data.local.dao.RoutineDao
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.local.entity.RoutineLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate

class RoutineRepository(
    private val routineDao: RoutineDao,
    private val productDao: ProductDao,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun currentEpochDay(): Long = LocalDate.now(clock).toEpochDay()

    fun observeAllLogs(): Flow<List<RoutineLogEntity>> = routineDao.observeAll()

    fun observeLogsForDate(epochDay: Long = currentEpochDay()): Flow<List<RoutineLogEntity>> =
        routineDao.observeByDate(epochDay)

    fun observeAllProducts(): Flow<List<ProductEntity>> = productDao.observeAll()

    suspend fun getLogsForDate(epochDay: Long = currentEpochDay()): List<RoutineLogEntity> =
        routineDao.getByDate(epochDay)

    suspend fun getDefaultProductsForPeriod(period: String): List<ProductEntity> {
        val normalized = normalizePeriod(period)
        return productDao.getDefaultRoutineProducts().filter { product ->
            when (product.routineSlot.uppercase()) {
                PERIOD_MORNING -> normalized == PERIOD_MORNING
                PERIOD_EVENING -> normalized == PERIOD_EVENING
                SLOT_BOTH -> normalized == PERIOD_MORNING || normalized == PERIOD_EVENING
                PERIOD_HAIR -> normalized == PERIOD_HAIR
                else -> normalized == PERIOD_HAIR && isHairCategory(product.category)
            }
        }
    }

    suspend fun completeRoutine(period: String, notes: String = ""): RoutineLogEntity {
        val normalized = normalizePeriod(period)
        val epochDay = currentEpochDay()
        val nowMillis = clock.millis()
        val defaultIds = getDefaultProductsForPeriod(normalized).map { it.id }
        val existing = routineDao.getByDateAndPeriod(epochDay, normalized)

        val skippedIds = if (existing != null) {
            decodeProductIds(existing.skippedProductIdsJson)
        } else {
            emptyList()
        }
        val existingIncluded = if (existing != null) {
            decodeProductIds(existing.productIdsJson)
        } else {
            emptyList()
        }
        val extraIncluded = existingIncluded.filter { it !in defaultIds && it !in skippedIds }
        val includedIds = (defaultIds.filterNot { it in skippedIds } + extraIncluded).distinct()

        val entity = RoutineLogEntity(
            id = existing?.id ?: 0L,
            dateEpochDay = epochDay,
            period = normalized,
            productIdsJson = encodeProductIds(includedIds),
            skippedProductIdsJson = encodeProductIds(skippedIds),
            completedAtEpochMillis = nowMillis,
            notes = notes.ifBlank { existing?.notes.orEmpty() }
        )
        val rowId = routineDao.upsert(entity)
        return routineDao.getByDateAndPeriod(epochDay, normalized) ?: entity.copy(id = rowId)
    }

    suspend fun toggleProductOverride(period: String, productId: Long): RoutineLogEntity {
        val normalized = normalizePeriod(period)
        val epochDay = currentEpochDay()
        val nowMillis = clock.millis()
        val defaultIds = getDefaultProductsForPeriod(normalized).map { it.id }
        val existing = routineDao.getByDateAndPeriod(epochDay, normalized)

        val includedIds: MutableList<Long>
        val skippedIds: MutableList<Long>

        if (existing == null) {
            if (productId in defaultIds) {
                includedIds = defaultIds.filterNot { it == productId }.toMutableList()
                skippedIds = mutableListOf(productId)
            } else {
                includedIds = (defaultIds + productId).distinct().toMutableList()
                skippedIds = mutableListOf()
            }
        } else {
            includedIds = decodeProductIds(existing.productIdsJson).toMutableList()
            skippedIds = decodeProductIds(existing.skippedProductIdsJson).toMutableList()
            when {
                productId in skippedIds -> {
                    skippedIds.removeAll { it == productId }
                    if (productId !in includedIds) {
                        includedIds.add(productId)
                    }
                }
                productId in includedIds -> {
                    includedIds.removeAll { it == productId }
                    if (productId !in skippedIds) {
                        skippedIds.add(productId)
                    }
                }
                productId in defaultIds -> {
                    skippedIds.add(productId)
                }
                else -> {
                    includedIds.add(productId)
                }
            }
        }

        val entity = RoutineLogEntity(
            id = existing?.id ?: 0L,
            dateEpochDay = epochDay,
            period = normalized,
            productIdsJson = encodeProductIds(includedIds.distinct()),
            skippedProductIdsJson = encodeProductIds(skippedIds.distinct()),
            completedAtEpochMillis = existing?.completedAtEpochMillis ?: 0L,
            notes = existing?.notes.orEmpty()
        )
        val rowId = routineDao.upsert(entity)
        return routineDao.getByDateAndPeriod(epochDay, normalized) ?: entity.copy(id = rowId)
    }

    suspend fun setProductRoutineDefault(
        productId: Long,
        isMorning: Boolean,
        isEvening: Boolean
    ): ProductEntity? {
        val product = productDao.getById(productId) ?: return null
        val slot = when {
            isMorning && isEvening -> SLOT_BOTH
            isMorning -> PERIOD_MORNING
            isEvening -> PERIOD_EVENING
            else -> SLOT_NONE
        }
        val updated = product.copy(
            routineSlot = slot,
            isDefaultInRoutine = isMorning || isEvening,
            updatedAtEpochMillis = clock.millis()
        )
        productDao.update(updated)
        return updated
    }

    fun decodeProductIds(rawJson: String): List<Long> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Long>>(rawJson) }.getOrDefault(emptyList())
    }

    fun encodeProductIds(ids: List<Long>): String = json.encodeToString(ids.distinct())

    private fun normalizePeriod(period: String): String = when (period.trim().uppercase()) {
        PERIOD_MORNING -> PERIOD_MORNING
        PERIOD_EVENING -> PERIOD_EVENING
        PERIOD_HAIR -> PERIOD_HAIR
        else -> throw IllegalArgumentException("Unsupported routine period: $period")
    }

    companion object {
        const val PERIOD_MORNING = "MORNING"
        const val PERIOD_EVENING = "EVENING"
        const val PERIOD_HAIR = "HAIR"
        const val SLOT_BOTH = "BOTH"
        const val SLOT_NONE = "NONE"

        private val HAIR_CATEGORIES = setOf(
            "SHAMPOO", "CONDITIONER", "SCALP_TONIC", "HAIR_OIL", "HAIR"
        )

        fun isHairCategory(category: String): Boolean =
            category.trim().uppercase() in HAIR_CATEGORIES
    }
}
