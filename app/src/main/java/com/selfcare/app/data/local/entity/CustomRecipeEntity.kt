package com.selfcare.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_recipes")
data class CustomRecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val servingsCount: Int = 1,
    val totalGrams: Float = 100f,
    val perServingKcal: Int = 0,
    val perServingProtein: Float = 0f,
    val perServingCarbs: Float = 0f,
    val perServingFat: Float = 0f,
    val perServingFiber: Float = 0f,
    val perServingSugar: Float = 0f,
    val perServingSalt: Float = 0f,
    val ingredientsSummary: String = "",
    val createdAtEpochMillis: Long
)
