package com.selfcare.app.domain.health

import kotlin.math.roundToInt

enum class MacroGoalType {
    CUT,
    MAINTAIN,
    BULK
}

enum class ActivityLevel(val multiplier: Float) {
    SEDENTARY(1.2f),
    LIGHT(1.375f),
    MODERATE(1.55f),
    ACTIVE(1.725f)
}

data class DailyMacroTargets(
    val caloriesKcal: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val waterMl: Int
)

data class ScaledNutritionValues(
    val caloriesKcal: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val fiberGrams: Float,
    val sugarGrams: Float
)

/**
 * 100% offline nutrition & hydration calculator using Mifflin-St Jeor BMR/TDEE equations.
 */
object MacroCalculator {

    fun calculateTargets(
        weightKg: Float,
        heightCm: Float,
        ageYears: Int,
        isMale: Boolean,
        activityLevel: ActivityLevel,
        goal: MacroGoalType
    ): DailyMacroTargets {
        val safeWeight = weightKg.coerceIn(35f, 220f)
        val safeHeight = heightCm.coerceIn(130f, 230f)
        val safeAge = ageYears.coerceIn(14, 95)

        val bmr = 10f * safeWeight + 6.25f * safeHeight - 5f * safeAge + if (isMale) 5f else -161f
        val tdee = bmr * activityLevel.multiplier
        val goalFactor = when (goal) {
            MacroGoalType.CUT -> 0.85f
            MacroGoalType.MAINTAIN -> 1.0f
            MacroGoalType.BULK -> 1.12f
        }
        val targetKcal = (tdee * goalFactor).roundToInt().coerceIn(1200, 5500)

        val proteinPerKg = when (goal) {
            MacroGoalType.CUT -> 2.0f
            MacroGoalType.MAINTAIN -> 1.7f
            MacroGoalType.BULK -> 1.9f
        }
        val proteinG = (safeWeight * proteinPerKg).roundToInt().coerceIn(60, 320)
        val fatG = (safeWeight * 0.85f).roundToInt().coerceIn(35, 160)
        val remainingKcal = (targetKcal - proteinG * 4 - fatG * 9).coerceAtLeast(120)
        val carbsG = (remainingKcal / 4f).roundToInt().coerceAtLeast(30)

        val baseWaterMl = (safeWeight * 35f).roundToInt()
        val activityBonusMl = when (activityLevel) {
            ActivityLevel.SEDENTARY -> 0
            ActivityLevel.LIGHT -> 250
            ActivityLevel.MODERATE -> 500
            ActivityLevel.ACTIVE -> 750
        }
        val waterMl = (((baseWaterMl + activityBonusMl) + 125) / 250 * 250).coerceIn(1500, 5000)

        return DailyMacroTargets(
            caloriesKcal = targetKcal,
            proteinGrams = proteinG,
            carbsGrams = carbsG,
            fatGrams = fatG,
            waterMl = waterMl
        )
    }

    fun scalePer100g(
        grams: Float,
        per100gKcal: Float,
        per100gProtein: Float,
        per100gCarbs: Float,
        per100gFat: Float,
        per100gFiber: Float = 0f,
        per100gSugar: Float = 0f
    ): ScaledNutritionValues {
        val factor = (grams.coerceAtLeast(0f)) / 100f
        return ScaledNutritionValues(
            caloriesKcal = (per100gKcal.coerceAtLeast(0f) * factor).roundToInt(),
            proteinGrams = round1(per100gProtein.coerceAtLeast(0f) * factor),
            carbsGrams = round1(per100gCarbs.coerceAtLeast(0f) * factor),
            fatGrams = round1(per100gFat.coerceAtLeast(0f) * factor),
            fiberGrams = round1(per100gFiber.coerceAtLeast(0f) * factor),
            sugarGrams = round1(per100gSugar.coerceAtLeast(0f) * factor)
        )
    }

    private fun round1(value: Float): Float = (value * 10f).roundToInt() / 10f
}
