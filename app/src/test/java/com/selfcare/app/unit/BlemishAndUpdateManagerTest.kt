package com.selfcare.app.unit

import com.selfcare.app.data.remote.AppUpdateManager
import com.selfcare.app.domain.cv.FaceMetricCalculator
import com.selfcare.app.domain.cv.MetricInterpreter
import com.selfcare.app.domain.cv.MetricStatusLevel
import com.selfcare.app.domain.cv.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlemishAndUpdateManagerTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    @Test
    fun computeBlemishMetrics_clearSkinVsBlemishSpots() {
        val w = 80
        val h = 80
        val skinColor = argb(205, 168, 148)
        val spotColor = argb(198, 92, 90)
        val pixels = IntArray(w * h) { skinColor }
        val roi = PixelRect(5, 5, 75, 75)

        val clear = FaceMetricCalculator.computeBlemishMetrics(pixels, w, h, listOf(roi))
        assertEquals(0, clear.estimatedSpotCount)
        assertEquals(MetricStatusLevel.OPTIMAL, MetricInterpreter.interpretBlemish(clear.blemishScore, clear.estimatedSpotCount).level)

        // Paint 3 distinct 4x4 inflamed spots separated across the ROI
        val centers = listOf(20 to 20, 45 to 30, 30 to 55)
        for ((cx, cy) in centers) {
            for (y in (cy - 2)..(cy + 1)) {
                for (x in (cx - 2)..(cx + 1)) {
                    pixels[y * w + x] = spotColor
                }
            }
        }

        val withSpots = FaceMetricCalculator.computeBlemishMetrics(pixels, w, h, listOf(roi))
        assertEquals(3, withSpots.estimatedSpotCount)
        assertTrue(withSpots.blemishScore > 0f)
    }

    @Test
    fun appUpdateManager_semVerSha256AndUrlValidation() {
        assertTrue(AppUpdateManager.compareSemVer("1.3.0", "1.2.7") > 0)
        assertEquals(0, AppUpdateManager.compareSemVer("v1.3.0", "1.3.0"))
        assertTrue(AppUpdateManager.compareSemVer("1.2.7", "1.3.0") < 0)

        val hash = "a".repeat(64)
        val sums = "$hash  SelfCare-release.apk\n${"b".repeat(64)}  SelfCare-debug.apk"
        assertEquals(hash, AppUpdateManager.parseSha256ForFile(sums, "SelfCare-release.apk"))

        assertTrue(AppUpdateManager.isAllowedHttpsUrl("https://github.com/kicsirigo/SelfCare/releases/download/v1.3.0/SelfCare-release.apk"))
        assertFalse(AppUpdateManager.isAllowedHttpsUrl("http://github.com/kicsirigo/SelfCare/releases/download/v1.3.0/SelfCare-release.apk"))
        assertFalse(AppUpdateManager.isAllowedHttpsUrl("https://evil.example.com/SelfCare-release.apk"))
    }
}
