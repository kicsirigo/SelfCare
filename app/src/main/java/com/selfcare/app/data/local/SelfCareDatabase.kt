package com.selfcare.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE face_check_ins ADD COLUMN blemishScore REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE face_check_ins ADD COLUMN blemishCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE face_check_ins ADD COLUMN stubbleScore REAL NOT NULL DEFAULT 0.0")
            }
        }

        @Volatile
        private var instance: SelfCareDatabase? = null

        fun getInstance(context: Context): SelfCareDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SelfCareDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { db ->
                    instance = db
                }
            }
        }
    }
}
