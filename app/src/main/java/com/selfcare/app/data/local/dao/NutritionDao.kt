package com.selfcare.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.selfcare.app.data.local.entity.NutritionLogEntity
import com.selfcare.app.data.local.entity.WaterLogEntity
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
}
