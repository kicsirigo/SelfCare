package com.selfcare.app.e2e

import android.Manifest
import android.graphics.Color
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import com.selfcare.app.MainActivity
import com.selfcare.app.data.repository.FaceCheckInRepository
import com.selfcare.app.data.repository.RoutineRepository
import com.selfcare.app.ui.checkin.CheckInViewModel
import com.selfcare.app.ui.routine.RoutineViewModel
import com.selfcare.app.ui.shelf.ShelfViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Tier2BoundaryCornerTest {

    private val e2eRule = SelfCareE2ERule()
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(e2eRule).around(composeRule)

    @Test
    fun emptyStates_displayedOnRoutineShelfAndTrends() {
        composeRule.waitUntilTagExists("routine_empty_state")
        composeRule.onNodeWithTag("routine_empty_state").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_shelf").performClick()
        composeRule.waitUntilTagExists("shelf_empty_state")
        composeRule.onNodeWithTag("shelf_empty_state").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_trends").performClick()
        composeRule.waitUntilTagExists("trends_empty_state")
        composeRule.onNodeWithTag("trends_empty_state").assertIsDisplayed()
    }

    @Test
    fun checkIn_missingCameraPermissionShowsPermissionRequiredCard() {
        Shadows.shadowOf(e2eRule.app).denyPermissions(Manifest.permission.CAMERA)

        composeRule.onNodeWithTag("nav_checkin").performClick()
        composeRule.waitUntilTagExists("checkin_permission_required")
        composeRule.onNodeWithTag("checkin_permission_required").assertIsDisplayed()
    }

    @Test
    fun checkIn_poorLightingShowsLightingWarningBeforeCapture() {
        Shadows.shadowOf(e2eRule.app).grantPermissions(Manifest.permission.CAMERA)

        composeRule.onNodeWithTag("nav_checkin").performClick()
        composeRule.waitUntilTagExists("checkin_capture_btn")

        val checkInViewModel = ViewModelProvider(composeRule.activity)[CheckInViewModel::class.java]
        val darkBitmap = createTestBitmap(width = 64, height = 64, color = Color.BLACK)
        composeRule.runOnUiThread {
            checkInViewModel.assessLightingFromBitmap(darkBitmap)
        }

        composeRule.waitUntilTagExists("checkin_brightness_badge")
        composeRule.onNodeWithTag("checkin_brightness_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("checkin_capture_btn").assertIsDisplayed()
    }

    @Test
    fun shelf_offlineObfErrorShowsFallbackBannerAndNoisyOcrParsesInci() {
        e2eRule.mockWebServer.enqueue(MockResponse().setResponseCode(500))

        composeRule.onNodeWithTag("nav_shelf").performClick()
        composeRule.waitUntilTagExists("shelf_search_input")

        composeRule.onNodeWithTag("shelf_search_input").performTextInput("4005900123456")
        composeRule.onNodeWithTag("shelf_lookup_btn").performClick()

        composeRule.waitUntilTagExists("shelf_offline_fallback_banner")
        composeRule.onNodeWithTag("shelf_offline_fallback_banner").assertIsDisplayed()

        // Noisy OCR text parsing fallback
        e2eRule.fakeOcrScanner.nextText = "INGREDIENTS: Aqua / Water, 10% Niacinamide (B3), Glycerin!! ###"
        val shelfViewModel = ViewModelProvider(composeRule.activity)[ShelfViewModel::class.java]
        composeRule.runOnUiThread {
            shelfViewModel.onOcrBitmapCaptured(createTestBitmap())
        }

        composeRule.waitUntilTagExists("inci_match_list")
        composeRule.onNodeWithTag("inci_match_list").performScrollTo().assertIsDisplayed()
        assertTrue(shelfViewModel.uiState.value.editor?.parsedMatches?.isNotEmpty() == true)
    }

    @Test
    fun routine_midnightDateRolloverAndDuplicateLogPrevention() {
        runBlocking {
        e2eRule.clock.setInstant(Instant.parse("2026-11-22T23:55:00Z"))
        val routineViewModel = ViewModelProvider(composeRule.activity)[RoutineViewModel::class.java]
        composeRule.runOnUiThread {
            routineViewModel.refreshDate()
        }

        composeRule.waitUntilTagExists("routine_complete_morning_btn")
        composeRule.onNodeWithTag("routine_complete_morning_btn").performClick()
        composeRule.waitUntilTagExists("routine_completed_badge")

        // Tap again on the same day -> should not create duplicate rows
        composeRule.onNodeWithTag("routine_complete_morning_btn").performClick()
        val day1 = e2eRule.container.routineRepository.currentEpochDay()
        val day1Logs = e2eRule.database.routineDao().observeByDate(day1).first()
            .filter { it.period == RoutineRepository.PERIOD_MORNING }
        assertEquals(1, day1Logs.size)

        // Cross midnight to 2026-11-23T00:05:00Z
        e2eRule.clock.advanceBy(Duration.ofMinutes(10))
        composeRule.runOnUiThread {
            routineViewModel.refreshDate()
        }

        composeRule.waitUntilTagDoesNotExist("routine_completed_badge")
        composeRule.onNodeWithTag("routine_complete_morning_btn").performClick()
        composeRule.waitUntilTagExists("routine_completed_badge")

        val day2 = e2eRule.container.routineRepository.currentEpochDay()
        assertEquals(day1 + 1L, day2)
        val day2Logs = e2eRule.database.routineDao().observeByDate(day2).first()
            .filter { it.period == RoutineRepository.PERIOD_MORNING }
        assertEquals(1, day2Logs.size)
        }
    }

    @Test
    fun checkIn_autoDeleteRawPhotoDeletesFileAndSetsPhotoPathNull() {
        Shadows.shadowOf(e2eRule.app).grantPermissions(Manifest.permission.CAMERA)

        composeRule.onNodeWithTag("nav_checkin").performClick()
        composeRule.waitUntilTagExists("checkin_auto_delete_toggle")

        val checkInViewModel = ViewModelProvider(composeRule.activity)[CheckInViewModel::class.java]
        composeRule.runOnUiThread {
            checkInViewModel.setCameraUnavailable(true)
        }
        composeRule.onNodeWithTag("checkin_auto_delete_toggle").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            checkInViewModel.uiState.value.autoDeletePhotos
        }

        composeRule.onNodeWithTag("checkin_capture_btn").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            e2eRule.fakeFaceAnalyzer.callCount >= 1 &&
                !checkInViewModel.uiState.value.isCapturing &&
                checkInViewModel.uiState.value.latestCheckIn != null
        }

        runBlocking {
            val checkIns = e2eRule.database.faceCheckInDao().observeRecent(10).first()
            assertEquals(1, checkIns.size)
            assertNull(checkIns.first().photoPath)
        }

        val checkInsDir = File(e2eRule.app.filesDir, FaceCheckInRepository.CHECKINS_DIR_NAME)
        val imageFiles = checkInsDir.listFiles()?.filter { it.name.endsWith(".jpg") || it.name.endsWith(".png") }.orEmpty()
        assertTrue("Expected no raw photo files when auto-delete is enabled", imageFiles.isEmpty())
    }
}
