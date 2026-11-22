package com.selfcare.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "face_check_ins",
    indices = [
        Index(value = ["dateEpochDay"])
    ]
)
data class FaceCheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dateEpochDay: Long,
    val timestampEpochMillis: Long,
    val underEyeDeltaLab: Float,
    val skinRednessIndex: Float,
    val skinTextureVariance: Float,
    val lipDrynessScore: Float,
    val hairToFaceRatio: Float,
    val lightingQualityScore: Float,
    val photoPath: String? = null,
    val notes: String = "",
    val blemishScore: Float = 0f,
    val blemishCount: Int = 0,
    val stubbleScore: Float = 0f
)
