package com.selfcare.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hygiene_logs",
    indices = [
        Index(value = ["dateEpochDay", "habitKey"], unique = true)
    ]
)
data class HygieneLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dateEpochDay: Long,
    val habitKey: String,
    val label: String,
    val isCustom: Boolean = false,
    val completed: Boolean = true,
    val completedAtEpochMillis: Long
)
