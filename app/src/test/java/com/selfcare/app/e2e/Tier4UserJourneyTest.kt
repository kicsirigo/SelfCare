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
import com.selfcare.app.data.repository.HygieneRepository
import com.selfcare.app.ui.checkin.CheckInViewModel
import com.selfcare.app.ui.shelf.ShelfViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
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
class Tier4UserJourneyTest {

    private val e2eRule = SelfCareE2ERule()
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(e2eRule).around(composeRule)

    @Test
    fun fullUserJourney_shelfConflictToRoutineHygieneCheckInAndTrends() {
        Shadows.shadowOf(e2eRule.app).grantPermissions(Manifest.permission.CAMERA)

        // 1. Shelf: Scan/lookup Night Cream (Retinol) via Open Beauty Facts
        e2eRule.mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "status": 1,
                      "code": "3600523750009",
                      "product": {
                        "code": "3600523750009",
                        "product_name": "Night Retinol Cream",
                        "brands": "DermJourney",
                        "ingredients_text_en": "Aqua, Glycerin, Retinol"
                      }
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
        composeRule.onNodeWithTag("shelf_product_default_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("shelf_save_product_btn").performScrollTo().performClick()
        composeRule.waitUntilTagDoesNotExist("shelf_save_product_btn")

        // 2. Shelf: Add Exfoliating Toner (AHA/BHA) via OCR
        e2eRule.fakeOcrScanner.nextText = "Aqua, Glycolic Acid, Salicylic Acid"
        val shelfViewModel = ViewModelProvider(composeRule.activity)[ShelfViewModel::class.java]
        composeRule.runOnUiThread {
            shelfViewModel.onOcrBitmapCaptured(createTestBitmap())
        }

        composeRule.waitUntilTagExists("shelf_save_product_btn")
        composeRule.onNodeWithTag("inci_match_list").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("shelf_product_name_input").performScrollTo().performTextReplacement("Exfoliating Toner")
        composeRule.onNodeWithTag("shelf_slot_EVENING").performScrollTo().performClick()
        composeRule.onNodeWithTag("shelf_product_default_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("shelf_save_product_btn").performScrollTo().performClick()

        // 3. Inspect active conflict banner
        composeRule.waitUntilTagExists("inci_conflict_banner")
        composeRule.onNodeWithTag("inci_conflict_banner").assertIsDisplayed()

        // 4. 1-tap Evening Routine
        composeRule.onNodeWithTag("nav_routine").performClick()
        composeRule.waitUntilTagExists("routine_period_evening")
        composeRule.onNodeWithTag("routine_period_evening").performClick()
        composeRule.onNodeWithTag("routine_complete_evening_btn").performClick()
        composeRule.waitUntilTagExists("routine_completed_badge")
        composeRule.onNodeWithTag("routine_completed_badge").assertIsDisplayed()

        // 5. Log Shower & Evening Teeth on Hygiene tab
        composeRule.onNodeWithTag("nav_hygiene").performClick()
        composeRule.waitUntilTagExists("hygiene_item_${HygieneRepository.HABIT_SHOWER}")
        composeRule.onNodeWithTag("hygiene_item_${HygieneRepository.HABIT_SHOWER}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("hygiene_item_${HygieneRepository.HABIT_TEETH_PM}")
            .performScrollTo()
            .performClick()

        val today = e2eRule.container.routineRepository.currentEpochDay()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking {
                val logs = e2eRule.database.hygieneDao().observeByDate(today).first()
                logs.count { it.completed } >= 2
            }
        }
        runBlocking {
            val hygieneLogs = e2eRule.database.hygieneDao().observeByDate(today).first()
            assertTrue(hygieneLogs.any { it.habitKey == HygieneRepository.HABIT_SHOWER && it.completed })
            assertTrue(hygieneLogs.any { it.habitKey == HygieneRepository.HABIT_TEETH_PM && it.completed })
        }

        // 6. Record Face Check-In & verify haircut status
        e2eRule.fakeFaceAnalyzer.nextResult = FakeFaceAnalyzer.defaultResult(
            underEyeDeltaLab = 11.0f,
            skinRednessIndex = 0.24f,
            skinTextureVariance = 13.5f,
            lipDrynessScore = 0.29f,
            hairToFaceRatio = 0.43f
        )
        composeRule.onNodeWithTag("nav_checkin").performClick()
        composeRule.waitUntilTagExists("checkin_capture_btn")
        val checkInViewModel = ViewModelProvider(composeRule.activity)[CheckInViewModel::class.java]
        composeRule.runOnUiThread {
            checkInViewModel.setCameraUnavailable(true)
        }
        composeRule.onNodeWithTag("checkin_capture_btn").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            e2eRule.fakeFaceAnalyzer.callCount >= 1 &&
                !checkInViewModel.uiState.value.isCapturing &&
                checkInViewModel.uiState.value.latestCheckIn != null
        }
        composeRule.waitUntilTagExists("checkin_haircut_reset_btn")
        composeRule.onNodeWithTag("checkin_haircut_reset_btn").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("checkin_haircut_reset_btn").assertIsDisplayed()

        // 7. Verify personal trend cards
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

        runBlocking {
            val checkIns = e2eRule.database.faceCheckInDao().observeRecent(10).first()
            assertEquals(1, checkIns.size)
        }
    }
}
