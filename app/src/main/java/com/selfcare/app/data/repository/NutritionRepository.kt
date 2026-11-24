package com.selfcare.app.data.repository

import com.selfcare.app.data.local.dao.NutritionDao
import com.selfcare.app.data.local.entity.NutritionLogEntity
import com.selfcare.app.data.local.entity.WaterLogEntity
import com.selfcare.app.data.remote.FoodLookupResult
import com.selfcare.app.data.remote.FoodSearchResult
import com.selfcare.app.data.remote.OpenFoodFactsNutritionClient
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate

class NutritionRepository(
    private val nutritionDao: NutritionDao,
    private val openFoodFactsClient: OpenFoodFactsNutritionClient,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    fun todayEpochDay(): Long = LocalDate.now(clock).toEpochDay()

    fun observeMealsForToday(): Flow<List<NutritionLogEntity>> =
        nutritionDao.observeMealsForDay(todayEpochDay())

    fun observeWaterForToday(): Flow<List<WaterLogEntity>> =
        nutritionDao.observeWaterForDay(todayEpochDay())

    fun observeRecentMeals(limit: Int = 40): Flow<List<NutritionLogEntity>> =
        nutritionDao.observeRecentMeals(limit)

    fun observeRecentWater(limit: Int = 200): Flow<List<WaterLogEntity>> =
        nutritionDao.observeRecentWater(limit)

    suspend fun logMeal(
        mealType: String,
        name: String,
        brand: String = "",
        barcode: String? = null,
        grams: Float,
        caloriesKcal: Int,
        proteinGrams: Float,
        carbsGrams: Float,
        fatGrams: Float,
        fiberGrams: Float = 0f,
        sugarGrams: Float = 0f,
        saltGrams: Float = 0f,
        source: String = "MANUAL"
    ): Long {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return 0L
        return nutritionDao.insertMeal(
            NutritionLogEntity(
                dateEpochDay = todayEpochDay(),
                mealType = mealType,
                name = cleanName,
                brand = brand.trim(),
                barcode = barcode?.trim()?.takeIf { it.isNotEmpty() },
                grams = grams.coerceAtLeast(1f),
                caloriesKcal = caloriesKcal.coerceAtLeast(0),
                proteinGrams = proteinGrams.coerceAtLeast(0f),
                carbsGrams = carbsGrams.coerceAtLeast(0f),
                fatGrams = fatGrams.coerceAtLeast(0f),
                fiberGrams = fiberGrams.coerceAtLeast(0f),
                sugarGrams = sugarGrams.coerceAtLeast(0f),
                saltGrams = saltGrams.coerceAtLeast(0f),
                source = source,
                createdAtEpochMillis = clock.millis()
            )
        )
    }

    suspend fun deleteMeal(id: Long) {
        nutritionDao.deleteMealById(id)
    }

    suspend fun logWater(amountMl: Int): Long {
        val safeMl = amountMl.coerceIn(10, 3000)
        return nutritionDao.insertWater(
            WaterLogEntity(
                dateEpochDay = todayEpochDay(),
                amountMl = safeMl,
                loggedAtEpochMillis = clock.millis()
            )
        )
    }

    suspend fun deleteWater(id: Long) {
        nutritionDao.deleteWaterById(id)
    }

    suspend fun lookupBarcode(barcode: String): FoodLookupResult =
        openFoodFactsClient.fetchByBarcode(barcode)

    suspend fun searchFoodRemote(query: String): FoodSearchResult =
        openFoodFactsClient.searchByName(query)
}
