package com.selfcare.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.selfcare.app.data.local.dao.FaceCheckInDao
import com.selfcare.app.data.local.dao.HaircutBaselineDao
import com.selfcare.app.data.local.dao.HygieneDao
import com.selfcare.app.data.local.dao.ProductDao
import com.selfcare.app.data.local.dao.RoutineDao
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.local.entity.HaircutBaselineEntity
import com.selfcare.app.data.local.entity.HygieneLogEntity
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.local.entity.RoutineLogEntity

@Database(
    entities = [
        RoutineLogEntity::class,
        ProductEntity::class,
        FaceCheckInEntity::class,
        HygieneLogEntity::class,
        HaircutBaselineEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SelfCareDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun productDao(): ProductDao
    abstract fun faceCheckInDao(): FaceCheckInDao
    abstract fun hygieneDao(): HygieneDao
    abstract fun haircutBaselineDao(): HaircutBaselineDao

    companion object {
        private const val DATABASE_NAME = "selfcare.db"

        @Volatile
        private var instance: SelfCareDatabase? = null

        fun getInstance(context: Context): SelfCareDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SelfCareDatabase::class.java,
                    DATABASE_NAME
                ).build().also { db ->
                    instance = db
                }
            }
        }
    }
}
