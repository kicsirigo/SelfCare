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
import com.selfcare.app.data.local.dao.NutritionDao
import com.selfcare.app.data.local.dao.ProductDao
import com.selfcare.app.data.local.dao.RoutineDao
import com.selfcare.app.data.local.entity.ActivityLogEntity
import com.selfcare.app.data.local.entity.CustomRecipeEntity
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.local.entity.HaircutBaselineEntity
import com.selfcare.app.data.local.entity.HygieneLogEntity
import com.selfcare.app.data.local.entity.NutritionLogEntity
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.local.entity.RoutineLogEntity
import com.selfcare.app.data.local.entity.WaterLogEntity
import com.selfcare.app.data.local.entity.WeightLogEntity

@Database(
    entities = [
        RoutineLogEntity::class,
        ProductEntity::class,
        FaceCheckInEntity::class,
        HygieneLogEntity::class,
        HaircutBaselineEntity::class,
        NutritionLogEntity::class,
        WaterLogEntity::class,
        CustomRecipeEntity::class,
        ActivityLogEntity::class,
        WeightLogEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class SelfCareDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun productDao(): ProductDao
    abstract fun faceCheckInDao(): FaceCheckInDao
    abstract fun hygieneDao(): HygieneDao
    abstract fun haircutBaselineDao(): HaircutBaselineDao
    abstract fun nutritionDao(): NutritionDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN photoPath TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `nutrition_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dateEpochDay` INTEGER NOT NULL,
                        `mealType` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `brand` TEXT NOT NULL,
                        `barcode` TEXT,
                        `grams` REAL NOT NULL,
                        `caloriesKcal` INTEGER NOT NULL,
                        `proteinGrams` REAL NOT NULL,
                        `carbsGrams` REAL NOT NULL,
                        `fatGrams` REAL NOT NULL,
                        `fiberGrams` REAL NOT NULL,
                        `sugarGrams` REAL NOT NULL,
                        `source` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrition_logs_dateEpochDay` ON `nutrition_logs` (`dateEpochDay`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `water_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dateEpochDay` INTEGER NOT NULL,
                        `amountMl` INTEGER NOT NULL,
                        `loggedAtEpochMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_logs_dateEpochDay` ON `water_logs` (`dateEpochDay`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `nutrition_logs` ADD COLUMN `saltGrams` REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_recipes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `servingsCount` INTEGER NOT NULL,
                        `totalGrams` REAL NOT NULL,
                        `perServingKcal` INTEGER NOT NULL,
                        `perServingProtein` REAL NOT NULL,
                        `perServingCarbs` REAL NOT NULL,
                        `perServingFat` REAL NOT NULL,
                        `perServingFiber` REAL NOT NULL,
                        `perServingSugar` REAL NOT NULL,
                        `perServingSalt` REAL NOT NULL,
                        `ingredientsSummary` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dateEpochDay` INTEGER NOT NULL,
                        `activityName` TEXT NOT NULL,
                        `durationMinutes` INTEGER NOT NULL,
                        `caloriesBurned` INTEGER NOT NULL,
                        `loggedAtEpochMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_logs_dateEpochDay` ON `activity_logs` (`dateEpochDay`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `weight_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dateEpochDay` INTEGER NOT NULL,
                        `weightKg` REAL NOT NULL,
                        `loggedAtEpochMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_weight_logs_dateEpochDay` ON `weight_logs` (`dateEpochDay`)")
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build().also { db ->
                    instance = db
                }
            }
        }
    }
}
