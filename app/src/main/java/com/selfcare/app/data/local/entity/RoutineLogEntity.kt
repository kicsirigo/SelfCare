package com.selfcare.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_logs",
    indices = [
        Index(value = ["dateEpochDay", "period"], unique = true)
    ]
)
data class RoutineLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dateEpochDay: Long,
    val period: String,
    val productIdsJson: String = "[]",
    val skippedProductIdsJson: String = "[]",
    val completedAtEpochMillis: Long,
    val notes: String = ""
)
