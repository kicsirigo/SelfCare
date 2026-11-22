package com.selfcare.app.unit

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.selfcare.app.data.local.SelfCareDatabase
import com.selfcare.app.domain.cv.FaceMetricCalculator
import com.selfcare.app.domain.cv.HaircutTracker
import com.selfcare.app.domain.cv.PixelRect
import com.selfcare.app.notifications.NotificationHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class HaircutTrackerTest {

    private lateinit var database: SelfCareDatabase
    private lateinit var clock: Clock

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SelfCareDatabase::class.java
        ).allowMainThreadQueries().build()
        clock = Clock.fixed(Instant.parse("2026-06-01T09:00:00Z"), ZoneId.of("UTC"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun hairToFaceRatioIncreasesWithHairMaskExtentAndArea() {
        val width = 100
        val height = 100
        val faceBox = PixelRect(30, 40, 70, 80) // 40x40 face

        val noHairMask = FloatArray(width * height)
        fillMaskRect(noHairMask, width, faceBox, 1f)
        val zeroHairRatio = FaceMetricCalculator.computeHairToFaceRatio(
            foregroundMask = noHairMask,
            maskWidth = width,
            maskHeight = height,
            imageWidth = width,
            imageHeight = height,
            faceBox = faceBox
        )
        assertEquals(0f, zeroHairRatio, 0.001f)

        val shortHairMask = noHairMask.copyOf()
        fillMaskRect(shortHairMask, width, PixelRect(30, 32, 70, 40), 1f)
        val shortHairRatio = FaceMetricCalculator.computeHairToFaceRatio(
            foregroundMask = shortHairMask,
            maskWidth = width,
            maskHeight = height,
            imageWidth = width,
            imageHeight = height,
            faceBox = faceBox
        )

        val grownHairMask = noHairMask.copyOf()
        fillMaskRect(grownHairMask, width, PixelRect(22, 16, 78, 40), 1f)
        fillMaskRect(grownHairMask, width, PixelRect(22, 40, 30, 75), 1f)
        fillMaskRect(grownHairMask, width, PixelRect(70, 40, 78, 75), 1f)
        val grownHairRatio = FaceMetricCalculator.computeHairToFaceRatio(
            foregroundMask = grownHairMask,
            maskWidth = width,
            maskHeight = height,
            imageWidth = width,
            imageHeight = height,
            faceBox = faceBox
        )

        assertTrue(shortHairRatio > 0.1f)
        assertTrue(grownHairRatio > shortHairRatio * 2f)
    }

    @Test
    fun growthCalculationAndThresholdCrossingTriggerReminderAndResetDeactivatesOldBaselines() = runTest {
        var triggeredWithPercent: Float? = null
        val tracker = HaircutTracker(
            haircutBaselineDao = database.haircutBaselineDao(),
            clock = clock,
            onTriggerReminder = { percent ->
                triggeredWithPercent = percent
                true
            }
        )

        val firstBaseline = tracker.resetBaseline(currentOrInitialRatio = 0.40f, thresholdPercent = 20f)
        assertTrue(firstBaseline.isActive)
        assertEquals(0.40f, firstBaseline.baselineRatio, 0.0001f)

        val belowThreshold = tracker.evaluate(currentHairToFaceRatio = 0.44f)
        assertTrue(belowThreshold.hasBaseline)
        assertEquals(10f, belowThreshold.growthPercent, 0.05f)
        assertFalse(belowThreshold.isThresholdCrossed)
        assertFalse(belowThreshold.notificationTriggered)
        assertEquals(null, triggeredWithPercent)

        val aboveThreshold = tracker.evaluate(currentHairToFaceRatio = 0.50f)
        assertTrue(aboveThreshold.isThresholdCrossed)
        assertEquals(25f, aboveThreshold.growthPercent, 0.05f)
        assertTrue(aboveThreshold.notificationTriggered)
        assertEquals(25f, triggeredWithPercent ?: -1f, 0.05f)

        val secondBaseline = tracker.resetBaseline(currentOrInitialRatio = 0.36f, thresholdPercent = 15f)
        val allBaselines = database.haircutBaselineDao().getAllOnce()
        val activeBaselines = allBaselines.filter { it.isActive }
        assertEquals(2, allBaselines.size)
        assertEquals(1, activeBaselines.size)
        assertEquals(secondBaseline.id, activeBaselines.first().id)

        val activeFromDao = tracker.getActiveBaseline()
        assertNotNull(activeFromDao)
        assertEquals(0.36f, activeFromDao!!.baselineRatio, 0.0001f)
    }

    @Test
    fun reminderDegradesGracefullyWhenPostNotificationsPermissionMissing() = runTest {
        val helper = NotificationHelper(ApplicationProvider.getApplicationContext())
        val tracker = HaircutTracker(
            haircutBaselineDao = database.haircutBaselineDao(),
            notificationHelper = helper,
            clock = clock
        )

        tracker.resetBaseline(currentOrInitialRatio = 0.30f, thresholdPercent = 15f)
        val evaluation = tracker.evaluate(currentHairToFaceRatio = 0.40f)

        assertTrue(evaluation.isThresholdCrossed)
        assertEquals(33.33f, evaluation.growthPercent, 0.1f)
        // Robolectric defaults to denied POST_NOTIFICATIONS on API 33+, or succeeds gracefully if <33
        assertFalse(evaluation.notificationTriggered && !helper.hasNotificationPermission())
    }

    private fun fillMaskRect(mask: FloatArray, width: Int, rect: PixelRect, value: Float) {
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                mask[y * width + x] = value
            }
        }
    }
}
