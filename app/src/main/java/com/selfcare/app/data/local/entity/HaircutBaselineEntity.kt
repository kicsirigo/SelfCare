package com.selfcare.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "haircut_baselines")
data class HaircutBaselineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val baselineRatio: Float,
    val growthThresholdPercent: Float = 15f,
    val recordedAtEpochMillis: Long,
    val isActive: Boolean = true
)
