package com.selfcare.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.selfcare.app.data.local.entity.ActivityLogEntity
import com.selfcare.app.data.local.entity.CustomRecipeEntity
import com.selfcare.app.data.local.entity.NutritionLogEntity
import com.selfcare.app.data.local.entity.WaterLogEntity
import com.selfcare.app.data.local.entity.WeightLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionDao {
    @Query("SELECT * FROM nutrition_logs WHERE dateEpochDay = :dateEpochDay ORDER BY createdAtEpochMillis DESC")
    fun observeMealsForDay(dateEpochDay: Long): Flow<List<NutritionLogEntity>>

    @Query("SELECT * FROM nutrition_logs ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    fun observeRecentMeals(limit: Int = 50): Flow<List<NutritionLogEntity>>

    @Query("SELECT * FROM nutrition_logs ORDER BY createdAtEpochMillis DESC")
    suspend fun getAllMeals(): List<NutritionLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: NutritionLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMeals(meals: List<NutritionLogEntity>)

    @Query("DELETE FROM nutrition_logs WHERE id = :id")
    suspend fun deleteMealById(id: Long)

    @Query("DELETE FROM nutrition_logs")
    suspend fun clearAllMeals()

    @Query("SELECT * FROM water_logs WHERE dateEpochDay = :dateEpochDay ORDER BY loggedAtEpochMillis DESC")
    fun observeWaterForDay(dateEpochDay: Long): Flow<List<WaterLogEntity>>

    @Query("SELECT * FROM water_logs ORDER BY loggedAtEpochMillis DESC LIMIT :limit")
    fun observeRecentWater(limit: Int = 200): Flow<List<WaterLogEntity>>

    @Query("SELECT * FROM water_logs ORDER BY loggedAtEpochMillis DESC")
    suspend fun getAllWater(): List<WaterLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWater(entry: WaterLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWater(entries: List<WaterLogEntity>)

    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun deleteWaterById(id: Long)

    @Query("DELETE FROM water_logs")
    suspend fun clearAllWater()

    // Custom recipes
    @Query("SELECT * FROM custom_recipes ORDER BY createdAtEpochMillis DESC")
    fun observeAllRecipes(): Flow<List<CustomRecipeEntity>>

    @Query("SELECT * FROM custom_recipes ORDER BY createdAtEpochMillis DESC")
    suspend fun getAllRecipes(): List<CustomRecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: CustomRecipeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRecipes(recipes: List<CustomRecipeEntity>)

    @Query("DELETE FROM custom_recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: Long)

    @Query("DELETE FROM custom_recipes")
    suspend fun clearAllRecipes()

    // Activity / burned calorie logs
    @Query("SELECT * FROM activity_logs WHERE dateEpochDay = :dateEpochDay ORDER BY loggedAtEpochMillis DESC")
    fun observeActivitiesForDay(dateEpochDay: Long): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs ORDER BY loggedAtEpochMillis DESC")
    suspend fun getAllActivities(): List<ActivityLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(entry: ActivityLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllActivities(entries: List<ActivityLogEntity>)

    @Query("DELETE FROM activity_logs WHERE id = :id")
    suspend fun deleteActivityById(id: Long)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAllActivities()

    // Weight logs
    @Query("SELECT * FROM weight_logs ORDER BY dateEpochDay ASC, loggedAtEpochMillis ASC")
    fun observeAllWeights(): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_logs ORDER BY dateEpochDay DESC, loggedAtEpochMillis DESC")
    suspend fun getAllWeights(): List<WeightLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(entry: WeightLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWeights(entries: List<WeightLogEntity>)

    @Query("DELETE FROM weight_logs WHERE id = :id")
    suspend fun deleteWeightById(id: Long)

    @Query("DELETE FROM weight_logs")
    suspend fun clearAllWeights()
}
