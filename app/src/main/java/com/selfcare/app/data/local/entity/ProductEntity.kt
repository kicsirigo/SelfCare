package com.selfcare.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val brand: String = "",
    val barcode: String? = null,
    val category: String = "OTHER",
    val routineSlot: String = "NONE",
    val isDefaultInRoutine: Boolean = false,
    val rawIngredientsText: String = "",
    val matchedInciJson: String = "[]",
    val source: String = "MANUAL",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val photoPath: String? = null
)
