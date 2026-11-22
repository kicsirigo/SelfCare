package com.selfcare.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.selfcare.app.data.local.entity.RoutineLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine_logs ORDER BY dateEpochDay DESC, completedAtEpochMillis DESC")
    fun observeAll(): Flow<List<RoutineLogEntity>>

    @Query("SELECT * FROM routine_logs WHERE dateEpochDay = :dateEpochDay ORDER BY completedAtEpochMillis ASC")
    fun observeByDate(dateEpochDay: Long): Flow<List<RoutineLogEntity>>

    @Query("SELECT * FROM routine_logs WHERE dateEpochDay = :dateEpochDay ORDER BY completedAtEpochMillis ASC")
    suspend fun getByDate(dateEpochDay: Long): List<RoutineLogEntity>

    @Query("SELECT * FROM routine_logs WHERE dateEpochDay = :dateEpochDay AND period = :period LIMIT 1")
    suspend fun getByDateAndPeriod(dateEpochDay: Long, period: String): RoutineLogEntity?

    @Query("SELECT * FROM routine_logs ORDER BY dateEpochDay DESC, completedAtEpochMillis DESC")
    suspend fun getAllOnce(): List<RoutineLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: RoutineLogEntity): Long

    @Query("DELETE FROM routine_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM routine_logs")
    suspend fun deleteAll()
}
