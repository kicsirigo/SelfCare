package com.selfcare.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceCheckInDao {
    @Query("SELECT * FROM face_check_ins ORDER BY timestampEpochMillis DESC")
    fun observeAll(): Flow<List<FaceCheckInEntity>>

    @Query("SELECT * FROM face_check_ins ORDER BY timestampEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FaceCheckInEntity>>

    @Query("SELECT * FROM face_check_ins ORDER BY timestampEpochMillis DESC")
    suspend fun getAllOnce(): List<FaceCheckInEntity>

    @Query("SELECT * FROM face_check_ins WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FaceCheckInEntity?

    @Query("SELECT * FROM face_check_ins WHERE dateEpochDay >= :startEpochDay ORDER BY timestampEpochMillis ASC")
    suspend fun getSince(startEpochDay: Long): List<FaceCheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: FaceCheckInEntity): Long

    @Update
    suspend fun update(checkIn: FaceCheckInEntity)

    @Query("DELETE FROM face_check_ins WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM face_check_ins")
    suspend fun deleteAll()
}
