package com.selfcare.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.selfcare.app.data.local.entity.HygieneLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HygieneDao {
    @Query("SELECT * FROM hygiene_logs WHERE dateEpochDay = :dateEpochDay ORDER BY completedAtEpochMillis ASC")
    fun observeByDate(dateEpochDay: Long): Flow<List<HygieneLogEntity>>

    @Query("SELECT * FROM hygiene_logs ORDER BY dateEpochDay DESC, completedAtEpochMillis DESC")
    fun observeAll(): Flow<List<HygieneLogEntity>>

    @Query("SELECT * FROM hygiene_logs ORDER BY dateEpochDay DESC, completedAtEpochMillis DESC")
    suspend fun getAllOnce(): List<HygieneLogEntity>

    @Query("SELECT * FROM hygiene_logs WHERE dateEpochDay = :dateEpochDay AND habitKey = :habitKey LIMIT 1")
    suspend fun getByDateAndHabit(dateEpochDay: Long, habitKey: String): HygieneLogEntity?

    @Query("SELECT DISTINCT habitKey, label FROM hygiene_logs WHERE isCustom = 1 ORDER BY label ASC")
    suspend fun getCustomHabitDefinitions(): List<CustomHabitSummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: HygieneLogEntity): Long

    @Query("DELETE FROM hygiene_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM hygiene_logs")
    suspend fun deleteAll()
}

data class CustomHabitSummary(
    val habitKey: String,
    val label: String
)
