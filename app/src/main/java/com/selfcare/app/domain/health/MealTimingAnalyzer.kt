package com.selfcare.app.domain.health

import com.selfcare.app.data.local.entity.NutritionLogEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DayMealTimingSummary(
    val mealCount: Int = 0,
    val firstMealTimeHm: String? = null,
    val lastMealTimeHm: String? = null,
    val eatingWindowMinutes: Int = 0,
    val lateNightMealCount: Int = 0,
    val kcalBySlot: Map<String, Int> = emptyMap()
) {
    val hasLateNightMeal: Boolean get() = lateNightMealCount > 0
}

/**
 * 100% offline meal-timing helper: auto-selects Breakfast/Lunch/Snack/Dinner by time of day,
 * formats/parses HH:mm eating timestamps, and computes eating-window & late-night meal insights.
 */
object MealTimingAnalyzer {

    private val hmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    val orderedSlots: List<String> = listOf(
        NutritionLogEntity.MEAL_BREAKFAST,
        NutritionLogEntity.MEAL_LUNCH,
        NutritionLogEntity.MEAL_SNACK,
        NutritionLogEntity.MEAL_DINNER
    )

    fun defaultMealTypeForHour(hour24: Int, minute: Int = 0): String {
        val totalMin = (hour24.coerceIn(0, 23) * 60) + minute.coerceIn(0, 59)
        return when {
            totalMin in (5 * 60)..(10 * 60 + 29) -> NutritionLogEntity.MEAL_BREAKFAST
            totalMin in (10 * 60 + 30)..(14 * 60 + 59) -> NutritionLogEntity.MEAL_LUNCH
            totalMin in (15 * 60)..(17 * 60 + 59) -> NutritionLogEntity.MEAL_SNACK
            else -> NutritionLogEntity.MEAL_DINNER
        }
    }

    fun formatTimeHm(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (epochMillis <= 0L) return ""
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalTime()
            .format(hmFormatter)
    }

    fun parseHmOrNull(hmText: String): Pair<Int, Int>? {
        val clean = hmText.trim()
        val parts = clean.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h to m
    }

    fun combineEpochDayAndHm(
        dateEpochDay: Long,
        hour: Int,
        minute: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val date = LocalDate.ofEpochDay(dateEpochDay)
        val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        return date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun analyzeDay(
        meals: List<NutritionLogEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DayMealTimingSummary {
        if (meals.isEmpty()) return DayMealTimingSummary()

        val sortedByTime = meals.sortedBy { it.createdAtEpochMillis }
        val localTimes = sortedByTime.map {
            Instant.ofEpochMilli(it.createdAtEpochMillis).atZone(zoneId).toLocalTime()
        }
        val first = localTimes.first()
        val last = localTimes.last()
        val firstMin = first.hour * 60 + first.minute
        val lastMin = last.hour * 60 + last.minute
        val windowMin = (lastMin - firstMin).coerceAtLeast(0)
        val lateCount = localTimes.count { it.hour >= 21 || it.hour < 4 }

        val bySlot = meals
            .groupBy { it.mealType }
            .mapValues { (_, list) -> list.sumOf { it.caloriesKcal } }

        return DayMealTimingSummary(
            mealCount = meals.size,
            firstMealTimeHm = first.format(hmFormatter),
            lastMealTimeHm = last.format(hmFormatter),
            eatingWindowMinutes = windowMin,
            lateNightMealCount = lateCount,
            kcalBySlot = bySlot
        )
    }
}
