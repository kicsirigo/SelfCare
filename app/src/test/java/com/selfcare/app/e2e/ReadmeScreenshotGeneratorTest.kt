package com.selfcare.app.e2e

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.selfcare.app.BuildConfig
import com.selfcare.app.MainActivity
import com.selfcare.app.data.local.AppLanguage
import com.selfcare.app.ui.theme.ThemeMode
import com.selfcare.app.data.local.entity.ActivityLogEntity
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.local.entity.HaircutBaselineEntity
import com.selfcare.app.data.local.entity.HygieneLogEntity
import com.selfcare.app.data.local.entity.NutritionLogEntity
import com.selfcare.app.data.local.entity.RoutineLogEntity
import com.selfcare.app.data.local.entity.WaterLogEntity
import com.selfcare.app.data.local.entity.WeightLogEntity
import com.selfcare.app.data.repository.RoutineRepository
import com.selfcare.app.domain.inci.InciConflictDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * Generates real Jetpack Compose + Android Skia native screenshots of all 5 tabs
 * populated with realistic English demo data.
 *
 * Run manually with:
 *   ./gradlew testDebugUnitTest --tests "com.selfcare.app.e2e.ReadmeScreenshotGeneratorTest" \
 *     -DgenerateReadmeScreenshots=true --no-daemon
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class ReadmeScreenshotGeneratorTest {

    private val e2eRule = SelfCareE2ERule()
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(e2eRule).around(composeRule)

    @Test
    fun captureAllFiveRealAppScreens() {
        Assume.assumeTrue(
            "Set GENERATE_README_SCREENSHOTS=true to regenerate README screenshots",
            System.getenv("GENERATE_README_SCREENSHOTS") == "true" ||
                System.getProperty("generateReadmeScreenshots") == "true"
        )

        val outDir = File("/home/mate/selfcare-android/docs/screenshots").apply { mkdirs() }

        // Infinite 120Hz animations (water wave, streak flame) never become "idle" automatically,
        // so drive the Compose clock deterministically instead of calling waitForIdle().
        composeRule.mainClock.autoAdvance = false

        runBlocking {
            seedEnglishDemoData()
        }

        // Let initial state + spring animations settle
        settleUi()

        // 1. Today Hub & Smart Routine Coach
        captureToFile(File(outDir, "v318_real_01_today_hub.png"))

        // 2. Nutrition, Macros & Animated Water
        composeRule.onNodeWithTag("nav_nutrition").performClick()
        settleUi()
        captureToFile(File(outDir, "v318_real_02_nutrition_water.png"))

        // 3. Face & Hair Check-In
        composeRule.onNodeWithTag("nav_checkin").performClick()
        settleUi()
        captureToFile(File(outDir, "v318_real_03_face_hair_checkin.png"))

        // 4. Product Shelf, INCI & Offline Vitamin Reference
        composeRule.onNodeWithTag("nav_shelf").performClick()
        settleUi()
        captureToFile(File(outDir, "v318_real_04_shelf_inci_vitamins.png"))

        // 5. Personal Trends
        composeRule.onNodeWithTag("nav_trends").performClick()
        settleUi()
        captureToFile(File(outDir, "v318_real_05_trends_ai_coach.png"))
    }

    private fun settleUi() {
        repeat(24) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.mainClock.advanceTimeBy(80L)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun captureToFile(file: File) {
        var captured: Bitmap? = null
        composeRule.activityRule.scenario.onActivity { activity ->
            val root = activity.window.decorView
            val width = root.width.coerceAtLeast(1)
            val height = root.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            root.draw(canvas)
            captured = bitmap
        }
        val bitmap = requireNotNull(captured) { "Failed to capture decorView bitmap" }
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    private suspend fun seedEnglishDemoData() {
        // Keep the fixture's initial clock day (already captured by ViewModels on launch)
        // so Today = selectedEpochDay across Routine, Nutrition, Check-In & Trends!
        val nowMs = e2eRule.clock.millis()
        val today = e2eRule.container.routineRepository.currentEpochDay()

        val settings = e2eRule.settingsRepository
        settings.setThemeMode(ThemeMode.AMOLED)
        settings.setAppLanguage(AppLanguage.EN)
        settings.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
        settings.setBirthDateIso("2001-05-14")
        settings.setShaveIntervalDays(3)
        settings.setLastShaveEpochDay(today - 2L)
        settings.setHaircutThresholdPercent(20)
        settings.updateNutritionTargets(
            caloriesKcal = 2200,
            proteinGrams = 160,
            carbsGrams = 220,
            fatGrams = 70,
            waterMl = 2500
        )

        val productRepo = e2eRule.container.productRepository
        val cleanser = productRepo.saveProduct(
            name = "CeraVe Foaming Cleanser",
            brand = "CeraVe",
            category = "FACE",
            routineSlot = InciConflictDetector.SLOT_BOTH,
            isDefaultInRoutine = true,
            rawIngredientsText = "Aqua, Cocamidopropyl Hydroxysultaine, Glycerin, Niacinamide, Ceramide NP, Ceramide AP"
        )
        val epiduo = productRepo.saveProduct(
            name = "Epiduo 0.1% / 2.5% Gel",
            brand = "Galderma",
            category = "RX",
            routineSlot = InciConflictDetector.SLOT_EVENING,
            isDefaultInRoutine = true,
            rawIngredientsText = "Adapalene, Benzoyl Peroxide, Propylene Glycol, Glycerin"
        )
        val cicaplast = productRepo.saveProduct(
            name = "Cicaplast Baume B5+",
            brand = "La Roche-Posay",
            category = "FACE",
            routineSlot = InciConflictDetector.SLOT_BOTH,
            isDefaultInRoutine = true,
            rawIngredientsText = "Aqua, Hydrogenated Polyisobutene, Glycerin, Panthenol, Madecassoside, Shea Butter"
        )
        val spf = productRepo.saveProduct(
            name = "Anthelios UVMune 400 SPF 50+",
            brand = "La Roche-Posay",
            category = "FACE",
            routineSlot = InciConflictDetector.SLOT_MORNING,
            isDefaultInRoutine = true,
            rawIngredientsText = "Aqua, Alcohol Denat, Glycerin, Silica, Tocopherol"
        )
        productRepo.saveProduct(
            name = "Vitamin D3 2000 IU",
            brand = "Now Foods",
            category = "VITAMIN",
            routineSlot = InciConflictDetector.SLOT_MORNING,
            isDefaultInRoutine = true,
            rawIngredientsText = "Cholecalciferol, Extra Virgin Olive Oil"
        )
        productRepo.saveProduct(
            name = "Magnesium Bisglycinate",
            brand = "Thorne",
            category = "VITAMIN",
            routineSlot = InciConflictDetector.SLOT_EVENING,
            isDefaultInRoutine = true,
            rawIngredientsText = "Magnesium Bisglycinate"
        )
        productRepo.saveProduct(
            name = "Alpecin C1 Caffeine Shampoo",
            brand = "Alpecin",
            category = "HAIR",
            routineSlot = InciConflictDetector.SLOT_HAIR,
            isDefaultInRoutine = true,
            rawIngredientsText = "Aqua, Sodium Laureth Sulfate, Caffeine, Niacinamide, Zinc PCA"
        )

        val db = e2eRule.database

        // 7-day routine streak + today's morning completed
        for (offset in 7L downTo 1L) {
            val d = today - offset
            val ts = nowMs - offset * 86_400_000L
            db.routineDao().upsert(
                RoutineLogEntity(
                    dateEpochDay = d,
                    period = RoutineRepository.PERIOD_MORNING,
                    completedAtEpochMillis = ts - 36_000_000L,
                    productIdsJson = "[${cleanser.id},${cicaplast.id},${spf.id}]",
                    skippedProductIdsJson = "[]"
                )
            )
            db.routineDao().upsert(
                RoutineLogEntity(
                    dateEpochDay = d,
                    period = RoutineRepository.PERIOD_EVENING,
                    completedAtEpochMillis = ts,
                    productIdsJson = "[${cleanser.id},${epiduo.id},${cicaplast.id}]",
                    skippedProductIdsJson = "[]"
                )
            )
        }
        db.routineDao().upsert(
            RoutineLogEntity(
                dateEpochDay = today,
                period = RoutineRepository.PERIOD_MORNING,
                completedAtEpochMillis = nowMs - 39_600_000L, // ~07:30
                productIdsJson = "[${cleanser.id},${cicaplast.id},${spf.id}]",
                skippedProductIdsJson = "[]"
            )
        )

        // Daily habits for today
        db.hygieneDao().upsert(
            HygieneLogEntity(
                dateEpochDay = today,
                habitKey = "teeth_morning",
                label = "Morning Teeth",
                completed = true,
                completedAtEpochMillis = nowMs - 39_000_000L
            )
        )
        db.hygieneDao().upsert(
            HygieneLogEntity(
                dateEpochDay = today,
                habitKey = "shower",
                label = "Shower",
                completed = true,
                completedAtEpochMillis = nowMs - 38_000_000L
            )
        )
        db.hygieneDao().upsert(
            HygieneLogEntity(
                dateEpochDay = today,
                habitKey = "lip_balm",
                label = "Lip Balm",
                completed = true,
                completedAtEpochMillis = nowMs - 20_000_000L
            )
        )

        // Today's 100% English meals, water & workout
        val nutritionDao = db.nutritionDao()
        nutritionDao.insertMeal(
            NutritionLogEntity(
                dateEpochDay = today,
                mealType = NutritionLogEntity.MEAL_BREAKFAST,
                name = "Oatmeal with Whey & Berries",
                grams = 280f,
                caloriesKcal = 460,
                proteinGrams = 36f,
                carbsGrams = 58f,
                fatGrams = 9f,
                fiberGrams = 8f,
                sugarGrams = 12f,
                saltGrams = 0.4f,
                source = "PRESET",
                createdAtEpochMillis = nowMs - 36_000_000L
            )
        )
        nutritionDao.insertMeal(
            NutritionLogEntity(
                dateEpochDay = today,
                mealType = NutritionLogEntity.MEAL_LUNCH,
                name = "Chicken Stew with Dumplings",
                grams = 350f,
                caloriesKcal = 610,
                proteinGrams = 44f,
                carbsGrams = 52f,
                fatGrams = 18f,
                fiberGrams = 4f,
                sugarGrams = 5f,
                saltGrams = 2.1f,
                source = "OFFLINE_DB",
                createdAtEpochMillis = nowMs - 21_600_000L
            )
        )
        nutritionDao.insertMeal(
            NutritionLogEntity(
                dateEpochDay = today,
                mealType = NutritionLogEntity.MEAL_DINNER,
                name = "Grilled Atlantic Salmon & Rice",
                grams = 300f,
                caloriesKcal = 520,
                proteinGrams = 42f,
                carbsGrams = 45f,
                fatGrams = 16f,
                fiberGrams = 3f,
                sugarGrams = 2f,
                saltGrams = 1.4f,
                source = "USDA",
                createdAtEpochMillis = nowMs - 7_200_000L
            )
        )
        nutritionDao.insertMeal(
            NutritionLogEntity(
                dateEpochDay = today,
                mealType = NutritionLogEntity.MEAL_SNACK,
                name = "Natural Skyr Yogurt",
                brand = "Pilos",
                grams = 150f,
                caloriesKcal = 110,
                proteinGrams = 16f,
                carbsGrams = 6f,
                fatGrams = 0.3f,
                fiberGrams = 0f,
                sugarGrams = 5f,
                saltGrams = 0.2f,
                source = "OPEN_FOOD_FACTS",
                createdAtEpochMillis = nowMs - 14_400_000L
            )
        )
        // Previous 13 days nutrition + weight so the full 14-day Trends chart is populated
        for (offset in 13L downTo 1L) {
            val d = today - offset
            val ts = nowMs - offset * 86_400_000L
            val wave = ((offset * 37) % 5).toInt() * 45
            nutritionDao.insertMeal(
                NutritionLogEntity(
                    dateEpochDay = d,
                    mealType = NutritionLogEntity.MEAL_LUNCH,
                    name = "Daily Balanced Meals",
                    grams = 900f,
                    caloriesKcal = 1980 + wave,
                    proteinGrams = 142f + (offset % 4).toFloat() * 4f,
                    carbsGrams = 200f,
                    fatGrams = 62f,
                    fiberGrams = 26f,
                    sugarGrams = 24f,
                    saltGrams = 4.2f,
                    source = "MANUAL",
                    createdAtEpochMillis = ts
                )
            )
            nutritionDao.insertWeight(
                WeightLogEntity(
                    dateEpochDay = d,
                    weightKg = 74.2f + offset.toFloat() * 0.11f,
                    loggedAtEpochMillis = ts
                )
            )
        }
        nutritionDao.insertWeight(
            WeightLogEntity(
                dateEpochDay = today,
                weightKg = 74.2f,
                loggedAtEpochMillis = nowMs - 36_000_000L
            )
        )

        // Water & Workout today
        nutritionDao.insertWater(WaterLogEntity(dateEpochDay = today, amountMl = 750, loggedAtEpochMillis = nowMs - 30_000_000L))
        nutritionDao.insertWater(WaterLogEntity(dateEpochDay = today, amountMl = 500, loggedAtEpochMillis = nowMs - 18_000_000L))
        nutritionDao.insertWater(WaterLogEntity(dateEpochDay = today, amountMl = 500, loggedAtEpochMillis = nowMs - 6_000_000L))
        nutritionDao.insertActivity(
            ActivityLogEntity(
                dateEpochDay = today,
                activityName = "Strength Training",
                durationMinutes = 45,
                caloriesBurned = 290,
                loggedAtEpochMillis = nowMs - 12_000_000L
            )
        )

        // Haircut baseline + 7-day improving Face & Hair Check-In series
        db.haircutBaselineDao().insert(
            HaircutBaselineEntity(
                baselineRatio = 0.41f,
                growthThresholdPercent = 20f,
                recordedAtEpochMillis = nowMs - 18L * 86_400_000L,
                isActive = true
            )
        )
        for (offset in 6L downTo 0L) {
            val progress = (6 - offset) / 6f
            db.faceCheckInDao().insert(
                FaceCheckInEntity(
                    dateEpochDay = today - offset,
                    timestampEpochMillis = nowMs - offset * 86_400_000L,
                    underEyeDeltaLab = 14.5f - progress * 4.5f,
                    skinRednessIndex = 0.31f - progress * 0.11f,
                    skinTextureVariance = 18.0f - progress * 5.5f,
                    lipDrynessScore = 0.34f - progress * 0.12f,
                    hairToFaceRatio = 0.43f + progress * 0.03f,
                    lightingQualityScore = 0.92f,
                    blemishScore = 0.28f - progress * 0.15f,
                    blemishCount = if (offset >= 3L) 3 else 1,
                    stubbleScore = if (offset == 0L) 0.44f else 0.24f
                )
            )
        }
    }
}
