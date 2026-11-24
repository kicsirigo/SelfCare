package com.selfcare.app.data.repository

import com.selfcare.app.data.local.dao.NutritionDao
import com.selfcare.app.data.local.entity.ActivityLogEntity
import com.selfcare.app.data.local.entity.CustomRecipeEntity
import com.selfcare.app.data.local.entity.NutritionLogEntity
import com.selfcare.app.data.local.entity.WaterLogEntity
import com.selfcare.app.data.local.entity.WeightLogEntity
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

    fun observeMealsForDay(dateEpochDay: Long): Flow<List<NutritionLogEntity>> =
        nutritionDao.observeMealsForDay(dateEpochDay)

    fun observeMealsForToday(): Flow<List<NutritionLogEntity>> =
        nutritionDao.observeMealsForDay(todayEpochDay())

    fun observeWaterForDay(dateEpochDay: Long): Flow<List<WaterLogEntity>> =
        nutritionDao.observeWaterForDay(dateEpochDay)

    fun observeWaterForToday(): Flow<List<WaterLogEntity>> =
        nutritionDao.observeWaterForDay(todayEpochDay())

    fun observeActivitiesForDay(dateEpochDay: Long): Flow<List<ActivityLogEntity>> =
        nutritionDao.observeActivitiesForDay(dateEpochDay)

    fun observeAllRecipes(): Flow<List<CustomRecipeEntity>> =
        nutritionDao.observeAllRecipes()

    fun observeAllWeights(): Flow<List<WeightLogEntity>> =
        nutritionDao.observeAllWeights()

    fun observeRecentMeals(limit: Int = 40): Flow<List<NutritionLogEntity>> =
        nutritionDao.observeRecentMeals(limit)

    fun observeRecentWater(limit: Int = 200): Flow<List<WaterLogEntity>> =
        nutritionDao.observeRecentWater(limit)

    suspend fun logMeal(
        dateEpochDay: Long = todayEpochDay(),
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
        source: String = "MANUAL",
        eatenAtEpochMillis: Long? = null
    ): Long {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return 0L
        return nutritionDao.insertMeal(
            NutritionLogEntity(
                dateEpochDay = dateEpochDay,
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
                createdAtEpochMillis = eatenAtEpochMillis ?: clock.millis()
            )
        )
    }

    suspend fun deleteMeal(id: Long) {
        nutritionDao.deleteMealById(id)
    }

    suspend fun logWater(amountMl: Int, dateEpochDay: Long = todayEpochDay()): Long {
        val safeMl = amountMl.coerceIn(10, 3000)
        return nutritionDao.insertWater(
            WaterLogEntity(
                dateEpochDay = dateEpochDay,
                amountMl = safeMl,
                loggedAtEpochMillis = clock.millis()
            )
        )
    }

    suspend fun deleteWater(id: Long) {
        nutritionDao.deleteWaterById(id)
    }

    suspend fun saveCustomRecipe(
        name: String,
        servingsCount: Int,
        totalGrams: Float,
        totalKcal: Int,
        totalProtein: Float,
        totalCarbs: Float,
        totalFat: Float,
        totalFiber: Float = 0f,
        totalSugar: Float = 0f,
        totalSalt: Float = 0f,
        ingredientsSummary: String = ""
    ): Long {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return 0L
        val servings = servingsCount.coerceIn(1, 50)
        return nutritionDao.insertRecipe(
            CustomRecipeEntity(
                name = cleanName,
                servingsCount = servings,
                totalGrams = totalGrams.coerceAtLeast(1f),
                perServingKcal = (totalKcal.toFloat() / servings).toInt().coerceAtLeast(0),
                perServingProtein = (totalProtein / servings).coerceAtLeast(0f),
                perServingCarbs = (totalCarbs / servings).coerceAtLeast(0f),
                perServingFat = (totalFat / servings).coerceAtLeast(0f),
                perServingFiber = (totalFiber / servings).coerceAtLeast(0f),
                perServingSugar = (totalSugar / servings).coerceAtLeast(0f),
                perServingSalt = (totalSalt / servings).coerceAtLeast(0f),
                ingredientsSummary = ingredientsSummary.trim(),
                createdAtEpochMillis = clock.millis()
            )
        )
    }

    suspend fun deleteRecipe(id: Long) {
        nutritionDao.deleteRecipeById(id)
    }

    suspend fun logActivity(
        activityName: String,
        durationMinutes: Int,
        caloriesBurned: Int,
        dateEpochDay: Long = todayEpochDay()
    ): Long {
        val clean = activityName.trim()
        if (clean.isBlank()) return 0L
        return nutritionDao.insertActivity(
            ActivityLogEntity(
                dateEpochDay = dateEpochDay,
                activityName = clean,
                durationMinutes = durationMinutes.coerceIn(1, 600),
                caloriesBurned = caloriesBurned.coerceIn(1, 5000),
                loggedAtEpochMillis = clock.millis()
            )
        )
    }

    suspend fun deleteActivity(id: Long) {
        nutritionDao.deleteActivityById(id)
    }

    suspend fun logWeight(weightKg: Float, dateEpochDay: Long = todayEpochDay()): Long {
        val safe = weightKg.coerceIn(30f, 250f)
        return nutritionDao.insertWeight(
            WeightLogEntity(
                dateEpochDay = dateEpochDay,
                weightKg = safe,
                loggedAtEpochMillis = clock.millis()
            )
        )
    }

    suspend fun deleteWeight(id: Long) {
        nutritionDao.deleteWeightById(id)
    }

    suspend fun lookupBarcode(barcode: String): FoodLookupResult =
        openFoodFactsClient.fetchByBarcode(barcode)

    suspend fun searchFoodRemote(query: String): FoodSearchResult =
        openFoodFactsClient.searchByName(query)
}
