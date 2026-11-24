package com.selfcare.app.domain.ai

import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.local.entity.ActivityLogEntity
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.local.entity.NutritionLogEntity
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.local.entity.WaterLogEntity
import com.selfcare.app.data.local.entity.WeightLogEntity
import com.selfcare.app.domain.health.MealTimingAnalyzer
import com.selfcare.app.domain.health.VitaminCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class AiChatContextScope(
    val includeNutrition: Boolean = true,
    val includeFitnessWeight: Boolean = true,
    val includeRoutineShelf: Boolean = false,
    val includeFaceTrends: Boolean = false
)

/**
 * Builds a strictly anonymized, opt-in plain-text summary of selected in-app metrics
 * for the AI Coach Chat. Never includes photos, names, emails, or exact birth dates.
 */
object AppDataContextBuilder {

    fun buildContext(
        scope: AiChatContextScope,
        settings: UserSettings,
        todayEpochDay: Long,
        todayMeals: List<NutritionLogEntity>,
        recentMeals7d: List<NutritionLogEntity>,
        todayWater: List<WaterLogEntity>,
        todayActivities: List<ActivityLogEntity>,
        weights: List<WeightLogEntity>,
        shelfProducts: List<ProductEntity>,
        recentCheckIns: List<FaceCheckInEntity>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowTime: LocalTime = LocalTime.now(zoneId)
    ): String {
        val sb = StringBuilder()
        val nowHm = nowTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        val partOfDay = when (nowTime.hour) {
            in 5..10 -> "morning"
            in 11..14 -> "midday"
            in 15..17 -> "afternoon"
            in 18..21 -> "evening"
            else -> "night"
        }
        sb.appendLine("CURRENT LOCAL TIME: $nowHm ($partOfDay) — judge today's progress ONLY relative to this time of day!")
        sb.appendLine()
        sb.appendLine("USER PROFILE (anonymized):")
        val age = VitaminCalculator.computeAgeYears(settings.birthDateIso)
        if (age != null) sb.appendLine("- Age: $age years")
        sb.appendLine("- Gender: ${if (settings.bodyIsMale) "Male" else "Female"}")
        sb.appendLine("- Goal: ${settings.bodyMacroGoal}, Activity: ${settings.bodyActivityLevel}")

        if (scope.includeNutrition) {
            sb.appendLine()
            sb.appendLine("TODAY'S NUTRITION (${LocalDate.ofEpochDay(todayEpochDay)}):")
            val totalKcal = todayMeals.sumOf { it.caloriesKcal }
            val totalP = todayMeals.sumOf { it.proteinGrams.toDouble() }.roundToInt()
            val totalC = todayMeals.sumOf { it.carbsGrams.toDouble() }.roundToInt()
            val totalF = todayMeals.sumOf { it.fatGrams.toDouble() }.roundToInt()
            val totalSalt = todayMeals.sumOf { it.saltGrams.toDouble() }
            val totalWater = todayWater.sumOf { it.amountMl }
            sb.appendLine(
                "- Totals: $totalKcal / ${settings.dailyCalorieTarget} kcal | " +
                    "Protein: ${totalP}/${settings.dailyProteinTargetGrams}g | " +
                    "Carbs: ${totalC}/${settings.dailyCarbsTargetGrams}g | " +
                    "Fat: ${totalF}/${settings.dailyFatTargetGrams}g | " +
                    "Salt: ${String.format(Locale.US, "%.1f", totalSalt)}g | " +
                    "Water: $totalWater / ${settings.dailyWaterTargetMl} ml"
            )
            if (todayMeals.isEmpty()) {
                sb.appendLine("- Meals logged today: none yet.")
            } else {
                val timing = MealTimingAnalyzer.analyzeDay(todayMeals, zoneId)
                sb.appendLine(
                    "- Eating window: ${timing.firstMealTimeHm} - ${timing.lastMealTimeHm} " +
                        "(${timing.eatingWindowMinutes / 60}h ${timing.eatingWindowMinutes % 60}m, " +
                        "late-night meals after 21:00: ${timing.lateNightMealCount})"
                )
                todayMeals.sortedBy { it.createdAtEpochMillis }.forEach { m ->
                    val hm = MealTimingAnalyzer.formatTimeHm(m.createdAtEpochMillis, zoneId)
                    sb.appendLine(
                        "  * [$hm ${m.mealType}] ${m.name} (${m.grams.roundToInt()}g): " +
                            "${m.caloriesKcal} kcal, P:${m.proteinGrams.roundToInt()}g, " +
                            "CH:${m.carbsGrams.roundToInt()}g, F:${m.fatGrams.roundToInt()}g, " +
                            "Salt:${String.format(Locale.US, "%.1f", m.saltGrams)}g"
                    )
                }
            }
            val last7 = recentMeals7d.filter { it.dateEpochDay in (todayEpochDay - 6L)..todayEpochDay }
            val byDay = last7.groupBy { it.dateEpochDay }
            if (byDay.isNotEmpty()) {
                val avgKcal = (byDay.values.sumOf { d -> d.sumOf { it.caloriesKcal } }.toFloat() / byDay.size).roundToInt()
                val avgP = (byDay.values.sumOf { d -> d.sumOf { it.proteinGrams.toDouble() } }.toFloat() / byDay.size).roundToInt()
                sb.appendLine("- 7-day average (${byDay.size} logged days): $avgKcal kcal/day, $avgP g protein/day")
            }
        }

        if (scope.includeFitnessWeight) {
            sb.appendLine()
            sb.appendLine("EXERCISE & WEIGHT:")
            val burned = todayActivities.sumOf { it.caloriesBurned }
            if (todayActivities.isEmpty()) {
                sb.appendLine("- Today's exercise: none logged.")
            } else {
                sb.appendLine("- Today's burned kcal: +$burned kcal")
                todayActivities.forEach { a ->
                    sb.appendLine("  * ${a.activityName} (${a.durationMinutes} min, +${a.caloriesBurned} kcal)")
                }
            }
            val latestW = weights.maxByOrNull { it.loggedAtEpochMillis }?.weightKg ?: settings.bodyWeightKg
            if (latestW != null) {
                val targetStr = settings.targetWeightKg?.let { " -> target ${String.format(Locale.US, "%.1f", it)} kg" } ?: ""
                sb.appendLine("- Current weight: ${String.format(Locale.US, "%.1f", latestW)} kg$targetStr")
            }
            if (weights.size >= 2) {
                val sortedW = weights.sortedBy { it.dateEpochDay }.takeLast(10)
                val delta = sortedW.last().weightKg - sortedW.first().weightKg
                sb.appendLine("- Recent weight trend (${sortedW.size} logs): ${if (delta >= 0f) "+" else ""}${String.format(Locale.US, "%.1f", delta)} kg")
            }
        }

        if (scope.includeRoutineShelf) {
            sb.appendLine()
            sb.appendLine("SKINCARE / SHELF PRODUCTS (${shelfProducts.size}):")
            if (shelfProducts.isEmpty()) {
                sb.appendLine("- No products added yet.")
            } else {
                shelfProducts.take(15).forEach { p ->
                    sb.appendLine("  * [${p.routineSlot} / ${p.category}] ${p.brand} ${p.name}".trim())
                }
            }
        }

        if (scope.includeFaceTrends) {
            sb.appendLine()
            sb.appendLine("FACE CHECK-IN TRENDS (numeric metrics only, no photos):")
            if (recentCheckIns.isEmpty()) {
                sb.appendLine("- No face check-ins recorded yet.")
            } else {
                val latest = recentCheckIns.first()
                sb.appendLine(
                    "- Latest check-in: under-eye delta=${String.format(Locale.US, "%.1f", latest.underEyeDeltaLab)}, " +
                        "redness=${String.format(Locale.US, "%.2f", latest.skinRednessIndex)}, " +
                        "texture=${String.format(Locale.US, "%.1f", latest.skinTextureVariance)}, " +
                        "lipDryness=${String.format(Locale.US, "%.2f", latest.lipDrynessScore)}, " +
                        "blemishes=${latest.blemishCount}"
                )
            }
        }

        return sb.toString().trim()
    }
}
