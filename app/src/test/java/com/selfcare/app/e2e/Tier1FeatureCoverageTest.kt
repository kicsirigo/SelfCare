package com.selfcare.app.e2e

import android.Manifest
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import com.selfcare.app.MainActivity
import com.selfcare.app.data.repository.RoutineRepository
import com.selfcare.app.domain.inci.InciConflictDetector
import com.selfcare.app.ui.checkin.CheckInViewModel
import com.selfcare.app.ui.routine.RoutineViewModel
import com.selfcare.app.ui.shelf.ShelfViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Tier1FeatureCoverageTest {

    private val e2eRule = SelfCareE2ERule()
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(e2eRule).around(composeRule)

    @Test
    fun morningAndEveningRoutine_oneTapLoggingAndProductSkipOverride() {
        val (morningProductId, eveningProductId) = runBlocking {
            val mId = e2eRule.container.productRepository.saveProduct(
                name = "Vitamin C Serum",
                brand = "LabBrand",
                barcode = null,
                routineSlot = InciConflictDetector.SLOT_MORNING,
                isDefaultInRoutine = true,
                rawIngredientsText = "Aqua, Ascorbic Acid"
            ).id
            val eId = e2eRule.container.productRepository.saveProduct(
                name = "Barrier Cream",
                brand = "LabBrand",
                barcode = null,
                routineSlot = InciConflictDetector.SLOT_EVENING,
                isDefaultInRoutine = true,
                rawIngredientsText = "Aqua, Ceramide NP"
            ).id
            mId to eId
        }

        composeRule.waitUntilTagExists("routine_product_toggle_$morningProductId")
        composeRule.onNodeWithTag("routine_complete_morning_btn").performClick()
        composeRule.waitUntilTagExists("routine_completed_badge")

        composeRule.onNodeWithTag("routine_period_evening").performClick()
        composeRule.waitUntilTagExists("routine_product_toggle_$eveningProductId")

        // Skip evening product and wait for override to persist before completing evening routine
        val routineViewModel = ViewModelProvider(composeRule.activity)[RoutineViewModel::class.java]
        composeRule.onNodeWithTag("routine_product_toggle_$eveningProductId")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            eveningProductId in routineViewModel.uiState.value.skippedProductIds
        }

        composeRule.onNodeWithTag("routine_complete_evening_btn").performClick()
        composeRule.waitUntilTagExists("routine_completed_badge")

        runBlocking {
            val today = e2eRule.container.routineRepository.currentEpochDay()
            val logs = e2eRule.database.routineDao().observeByDate(today).first()
            val morningLog = logs.firstOrNull { it.period == RoutineRepository.PERIOD_MORNING }
            val eveningLog = logs.firstOrNull { it.period == RoutineRepository.PERIOD_EVENING }

            assertNotNull(morningLog)
            assertTrue(morningLog!!.completedAtEpochMillis > 0L)
            assertNotNull(eveningLog)
            assertTrue(eveningLog!!.completedAtEpochMillis > 0L)
            val skippedEvening = e2eRule.container.routineRepository.decodeProductIds(eveningLog.skippedProductIdsJson)
            assertEquals(listOf(eveningProductId), skippedEvening)
        }
    }

    @Test
    fun shelf_mockedObfLookupAndOcrFallback_detectsInciMatchesAndConflicts() {
        runBlocking { e2eRule.settingsRepository.setGeminiApiKey("gsk_e2e_test_key") }
        e2eRule.mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "choices": [{
                        "message": {
                          "content": "{\"name\":\"Night Retinol Cream\",\"brand\":\"DermTest\",\"category\":\"OTHER\",\"routineSlot\":\"EVENING\",\"ingredients\":\"Aqua, Glycerin, Retinol, Tocopherol\"}"
                        }
                      }]
                    }
                    """.trimIndent()
                )
        )

        composeRule.onNodeWithTag("nav_shelf").performClick()
        composeRule.waitUntilTagExists("shelf_search_input")

        composeRule.onNodeWithTag("shelf_search_input").performTextInput("3600523750009")
        composeRule.onNodeWithTag("shelf_lookup_btn").performClick()

        composeRule.waitUntilTagExists("shelf_save_product_btn")
        composeRule.onNodeWithTag("inci_match_list").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("shelf_slot_EVENING").performScrollTo().performClick()
        composeRule.onNodeWithTag("shelf_save_product_btn").performScrollTo().performClick()
        composeRule.waitUntilTagDoesNotExist("shelf_save_product_btn")

        // Now simulate offline OCR scan adding an AHA/BHA exfoliant in the same EVENING slot
        e2eRule.fakeOcrScanner.nextText = "Aqua, Glycolic Acid, Salicylic Acid, Panthenol"
        val shelfViewModel = ViewModelProvider(composeRule.activity)[ShelfViewModel::class.java]
        composeRule.runOnUiThread {
            shelfViewModel.onOcrBitmapCaptured(createTestBitmap())
        }

        composeRule.waitUntilTagExists("shelf_save_product_btn")
        composeRule.onNodeWithTag("inci_match_list").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("shelf_product_name_input").performScrollTo().performTextReplacement("Exfoliating Toner")
        composeRule.onNodeWithTag("shelf_slot_EVENING").performScrollTo().performClick()
        composeRule.onNodeWithTag("shelf_save_product_btn").performScrollTo().performClick()

        composeRule.waitUntilTagExists("inci_conflict_banner")
        composeRule.onNodeWithTag("inci_conflict_banner").assertIsDisplayed()
    }

    @Test
    fun checkInAndTrends_computesMetricsAgainst7dAnd30dBaselines() {
        Shadows.shadowOf(e2eRule.app).grantPermissions(Manifest.permission.CAMERA)

        // Day 1 baseline check-in 5 days ago
        runBlocking {
            e2eRule.clock.advanceDays(-5)
            e2eRule.fakeFaceAnalyzer.nextResult = FakeFaceAnalyzer.defaultResult(
                underEyeDeltaLab = 10.0f,
                skinRednessIndex = 0.20f,
                skinTextureVariance = 12.0f,
                lipDrynessScore = 0.25f,
                hairToFaceRatio = 0.40f
            )
            e2eRule.container.faceCheckInRepository.performCheckIn(createTestBitmap())
        }

        // Return to today and record a second check-in via UI
        e2eRule.clock.advanceDays(5)
        e2eRule.fakeFaceAnalyzer.nextResult = FakeFaceAnalyzer.defaultResult(
            underEyeDeltaLab = 14.0f,
            skinRednessIndex = 0.30f,
            skinTextureVariance = 18.0f,
            lipDrynessScore = 0.35f,
            hairToFaceRatio = 0.42f
        )

        composeRule.onNodeWithTag("nav_checkin").performClick()
        composeRule.waitUntilTagExists("checkin_capture_btn")
        val checkInViewModel = ViewModelProvider(composeRule.activity)[CheckInViewModel::class.java]
        composeRule.runOnUiThread {
            checkInViewModel.setCameraUnavailable(true)
        }
        composeRule.onNodeWithTag("checkin_capture_btn").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            e2eRule.fakeFaceAnalyzer.callCount >= 2
        }

        composeRule.onNodeWithTag("nav_trends").performClick()
        composeRule.waitUntilTagExists("trends_7d_baseline")
        composeRule.onNodeWithTag("trends_7d_baseline").assertIsDisplayed()
        composeRule.onNodeWithTag("trends_30d_baseline").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("trends_metric_card_under_eye"))
        composeRule.onNodeWithTag("trends_metric_card_under_eye").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("trends_metric_card_redness"))
        composeRule.onNodeWithTag("trends_metric_card_redness").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("trends_metric_card_texture"))
        composeRule.onNodeWithTag("trends_metric_card_texture").assertIsDisplayed()
    }

    @Test
    fun checkIn_haircutReminderBannerAndBaselineReset() {
        Shadows.shadowOf(e2eRule.app).grantPermissions(Manifest.permission.CAMERA)

        // Seed initial haircut baseline at 0.40f
        runBlocking {
            e2eRule.container.haircutTracker.resetBaseline(currentOrInitialRatio = 0.40f, thresholdPercent = 15f)
        }

        // Next check-in has 0.50f (+25% growth -> crosses 15% threshold)
        e2eRule.fakeFaceAnalyzer.nextResult = FakeFaceAnalyzer.defaultResult(hairToFaceRatio = 0.50f)

        composeRule.onNodeWithTag("nav_checkin").performClick()
        composeRule.waitUntilTagExists("checkin_capture_btn")
        val checkInViewModel = ViewModelProvider(composeRule.activity)[CheckInViewModel::class.java]
        composeRule.runOnUiThread {
            checkInViewModel.setCameraUnavailable(true)
        }
        composeRule.onNodeWithTag("checkin_capture_btn").performScrollTo().performClick()

        composeRule.waitUntilTagExists("haircut_reminder_banner")
        composeRule.onNodeWithTag("haircut_reminder_banner").performScrollTo().assertIsDisplayed()

        // Tap "Just had a haircut" reset button
        composeRule.onNodeWithTag("checkin_haircut_reset_btn").performScrollTo().performClick()
        composeRule.waitUntilTagDoesNotExist("haircut_reminder_banner")

        runBlocking {
            val activeBaseline = e2eRule.database.haircutBaselineDao().getActiveBaseline()
            assertNotNull(activeBaseline)
            assertEquals(0.50f, activeBaseline!!.baselineRatio, 0.001f)
        }
    }
}
