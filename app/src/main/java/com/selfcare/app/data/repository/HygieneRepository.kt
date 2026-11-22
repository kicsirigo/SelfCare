package com.selfcare.app.data.repository

import com.selfcare.app.data.local.dao.CustomHabitSummary
import com.selfcare.app.data.local.dao.HygieneDao
import com.selfcare.app.data.local.entity.HygieneLogEntity
import kotlinx.coroutines.flow.Flow
import java.text.Normalizer
import java.time.Clock
import java.time.LocalDate

class HygieneRepository(
    private val hygieneDao: HygieneDao,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    fun currentEpochDay(): Long = LocalDate.now(clock).toEpochDay()

    fun observeTodayLogs(): Flow<List<HygieneLogEntity>> =
        hygieneDao.observeByDate(currentEpochDay())

    fun observeLogsForDate(epochDay: Long): Flow<List<HygieneLogEntity>> =
        hygieneDao.observeByDate(epochDay)

    fun observeAllLogs(): Flow<List<HygieneLogEntity>> = hygieneDao.observeAll()

    suspend fun getCustomHabitDefinitions(): List<CustomHabitSummary> =
        hygieneDao.getCustomHabitDefinitions()

    suspend fun toggleHabit(
        habitKey: String,
        label: String,
        isCustom: Boolean = false
    ): HygieneLogEntity {
        val epochDay = currentEpochDay()
        val existing = hygieneDao.getByDateAndHabit(epochDay, habitKey)
        val nowMillis = clock.millis()
        val updated = if (existing != null) {
            existing.copy(
                label = label.ifBlank { existing.label },
                isCustom = existing.isCustom || isCustom,
                completed = !existing.completed,
                completedAtEpochMillis = nowMillis
            )
        } else {
            HygieneLogEntity(
                id = 0L,
                dateEpochDay = epochDay,
                habitKey = habitKey,
                label = label,
                isCustom = isCustom,
                completed = true,
                completedAtEpochMillis = nowMillis
            )
        }
        val rowId = hygieneDao.upsert(updated)
        return hygieneDao.getByDateAndHabit(epochDay, habitKey) ?: updated.copy(id = rowId)
    }

    suspend fun addCustomHabit(
        label: String,
        initialCompleted: Boolean = false
    ): HygieneLogEntity? {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return null
        val epochDay = currentEpochDay()
        val existingDefinitions = hygieneDao.getCustomHabitDefinitions()
        val matched = existingDefinitions.firstOrNull {
            it.label.equals(trimmed, ignoreCase = true)
        }
        val habitKey = matched?.habitKey ?: buildCustomHabitKey(trimmed)
        val existingToday = hygieneDao.getByDateAndHabit(epochDay, habitKey)
        val entity = HygieneLogEntity(
            id = existingToday?.id ?: 0L,
            dateEpochDay = epochDay,
            habitKey = habitKey,
            label = trimmed,
            isCustom = true,
            completed = existingToday?.completed ?: initialCompleted,
            completedAtEpochMillis = clock.millis()
        )
        val rowId = hygieneDao.upsert(entity)
        return hygieneDao.getByDateAndHabit(epochDay, habitKey) ?: entity.copy(id = rowId)
    }

    suspend fun deleteCustomHabit(habitKey: String) {
        val matchingRows = hygieneDao.getAllOnce().filter {
            it.habitKey == habitKey && it.isCustom
        }
        matchingRows.forEach { row ->
            hygieneDao.deleteById(row.id)
        }
    }

    private fun buildCustomHabitKey(label: String): String {
        val normalized = Normalizer.normalize(label, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return if (normalized.isNotEmpty()) {
            "custom_$normalized"
        } else {
            "custom_${clock.millis()}"
        }
    }

    companion object {
        const val HABIT_SHOWER = "SHOWER"
        const val HABIT_TEETH_AM = "TEETH_AM"
        const val HABIT_TEETH_PM = "TEETH_PM"
        const val HABIT_LIP_BALM = "LIP_BALM"

        val BUILT_IN_HABIT_KEYS = listOf(
            HABIT_SHOWER,
            HABIT_TEETH_AM,
            HABIT_TEETH_PM,
            HABIT_LIP_BALM
        )
    }
}
