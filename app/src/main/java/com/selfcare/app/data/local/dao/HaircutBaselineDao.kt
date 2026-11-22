package com.selfcare.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.selfcare.app.data.local.entity.HaircutBaselineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HaircutBaselineDao {
    @Query("SELECT * FROM haircut_baselines WHERE isActive = 1 ORDER BY recordedAtEpochMillis DESC LIMIT 1")
    fun observeActiveBaseline(): Flow<HaircutBaselineEntity?>

    @Query("SELECT * FROM haircut_baselines WHERE isActive = 1 ORDER BY recordedAtEpochMillis DESC LIMIT 1")
    suspend fun getActiveBaseline(): HaircutBaselineEntity?

    @Query("SELECT * FROM haircut_baselines ORDER BY recordedAtEpochMillis DESC")
    suspend fun getAllOnce(): List<HaircutBaselineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(baseline: HaircutBaselineEntity): Long

    @Query("UPDATE haircut_baselines SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAll()

    @Query("DELETE FROM haircut_baselines")
    suspend fun deleteAll()
}
