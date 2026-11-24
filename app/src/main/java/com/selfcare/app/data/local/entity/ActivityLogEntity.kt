package com.selfcare.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activity_logs",
    indices = [Index(value = ["dateEpochDay"])]
)
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateEpochDay: Long,
    val activityName: String,
    val durationMinutes: Int,
    val caloriesBurned: Int,
    val loggedAtEpochMillis: Long
)
