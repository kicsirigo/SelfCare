package com.selfcare.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nutrition_logs",
    indices = [Index(value = ["dateEpochDay"])]
)
data class NutritionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateEpochDay: Long,
    val mealType: String = MEAL_LUNCH,
    val name: String,
    val brand: String = "",
    val barcode: String? = null,
    val grams: Float = 100f,
    val caloriesKcal: Int = 0,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f,
    val fiberGrams: Float = 0f,
    val sugarGrams: Float = 0f,
    val source: String = "MANUAL",
    val createdAtEpochMillis: Long
) {
    companion object {
        const val MEAL_BREAKFAST = "BREAKFAST"
        const val MEAL_LUNCH = "LUNCH"
        const val MEAL_DINNER = "DINNER"
        const val MEAL_SNACK = "SNACK"
    }
}
