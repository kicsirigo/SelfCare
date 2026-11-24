package com.selfcare.app.domain.health

import kotlin.math.roundToInt

data class ActivityPreset(
    val id: String,
    val nameEn: String,
    val nameHu: String,
    val metValue: Float
)

/**
 * 100% offline MET-based exercise calorie burn estimator.
 * Standard formula: kcal = MET * weightKg * (durationMinutes / 60).
 */
object ActivityCalorieCalculator {

    val presets: List<ActivityPreset> = listOf(
        ActivityPreset("walking", "Brisk Walking", "Tempós séta", 3.8f),
        ActivityPreset("running", "Running", "Futás", 9.8f),
        ActivityPreset("cycling", "Cycling", "Kerékpározás", 7.5f),
        ActivityPreset("gym", "Strength Training / Gym", "Konditerem / Súlyzós edzés", 6.0f),
        ActivityPreset("swimming", "Swimming", "Úszás", 8.0f),
        ActivityPreset("hiit", "HIIT / CrossFit", "HIIT / Köredzés", 8.5f),
        ActivityPreset("football", "Football / Basketball", "Foci / Kosárlabda", 7.0f),
        ActivityPreset("yoga", "Yoga / Stretching", "Jóga / Nyújtás", 3.0f)
    )

    fun estimateBurnedKcal(
        metValue: Float,
        weightKg: Float,
        durationMinutes: Int
    ): Int {
        val safeMet = metValue.coerceIn(1f, 20f)
        val safeWeight = weightKg.coerceIn(35f, 220f)
        val safeMinutes = durationMinutes.coerceIn(1, 600)
        return (safeMet * safeWeight * (safeMinutes / 60f)).roundToInt().coerceAtLeast(1)
    }
}
