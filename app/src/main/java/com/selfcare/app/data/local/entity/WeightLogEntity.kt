package com.selfcare.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weight_logs",
    indices = [Index(value = ["dateEpochDay"])]
)
data class WeightLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateEpochDay: Long,
    val weightKg: Float,
    val loggedAtEpochMillis: Long
)
