package com.selfcare.app.unit

import androidx.test.core.app.ApplicationProvider
import com.selfcare.app.data.remote.AppUpdateInfo
import com.selfcare.app.data.remote.AppUpdateManager
import com.selfcare.app.data.remote.UpdateCheckResult
import com.selfcare.app.data.remote.UpdateDownloadResult
import com.selfcare.app.domain.cv.FaceMetricCalculator
import com.selfcare.app.domain.cv.MetricInterpreter
import com.selfcare.app.domain.cv.MetricStatusLevel
import com.selfcare.app.domain.cv.PixelRect
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test
    fun downloadAndVerifyUpdate_failsClosedWhenSha256SumsMissingOrUnreachable() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // 1. sha256SumsUrl is null -> must fail closed with Error
        val clientWithDummyApk = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("fake-apk-bytes".toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            })
            .build()

        val managerMissingSums = AppUpdateManager(context = context, httpClient = clientWithDummyApk)
        val infoNoSums = AppUpdateInfo(
            versionName = "9.9.9",
            tagName = "v9.9.9",
            releaseNotes = "",
            apkDownloadUrl = "https://github.com/kicsirigo/SelfCare/releases/download/v9.9.9/SelfCare-release.apk",
            apkSizeBytes = 14L,
            sha256SumsUrl = null
        )
        val resultNullSums = managerMissingSums.downloadAndVerifyUpdate(infoNoSums) { _, _ -> }
        assertTrue(
            "Expected fail-closed Error when sha256SumsUrl is null, got $resultNullSums",
            resultNullSums is UpdateDownloadResult.Error
        )

        // 2. SHA256SUMS.txt returns HTTP 404 -> must fail closed with Error and leave no APK behind
        val clientUnreachableSums = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                if (url.endsWith("SHA256SUMS.txt")) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("fake-apk-bytes".toResponseBody("application/octet-stream".toMediaType()))
                        .build()
                }
            })
            .build()

        val managerUnreachable = AppUpdateManager(context = context, httpClient = clientUnreachableSums)
        val infoWithSums = infoNoSums.copy(
            sha256SumsUrl = "https://github.com/kicsirigo/SelfCare/releases/download/v9.9.9/SHA256SUMS.txt"
        )
        val resultUnreachable = managerUnreachable.downloadAndVerifyUpdate(infoWithSums) { _, _ -> }
        assertTrue(
            "Expected fail-closed Error when SHA256SUMS.txt is unreachable, got $resultUnreachable",
            resultUnreachable is UpdateDownloadResult.Error
        )
        val updatesDir = File(context.cacheDir, AppUpdateManager.UPDATES_DIR)
        val leftoverApks = updatesDir.listFiles()?.filter { it.extension == "apk" }.orEmpty()
        assertTrue("No unverified APK should remain cached", leftoverApks.isEmpty())
    }

    @Test
    fun appUpdateManager_rejectsRedirectToNonAllowlistedHost() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val requestedHosts = mutableListOf<String>()

        val redirectingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                requestedHosts.add(req.url.host)
                val url = req.url.toString()
                when {
                    url.endsWith("SHA256SUMS.txt") -> {
                        val validSums = "${"a".repeat(64)}  SelfCare-release.apk\n"
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(validSums.toResponseBody("text/plain".toMediaType()))
                            .build()
                    }
                    else -> {
                        // Redirect to a non-allowlisted external host
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(302)
                            .message("Found")
                            .header("Location", "https://evil.example.com/SelfCare-release.apk")
                            .body("".toResponseBody("text/plain".toMediaType()))
                            .build()
                    }
                }
            })
            .build()

        val manager = AppUpdateManager(context = context, httpClient = redirectingClient)

        // 1. Redirect during APK download must be rejected
        val info = AppUpdateInfo(
            versionName = "9.9.9",
            tagName = "v9.9.9",
            releaseNotes = "",
            apkDownloadUrl = "https://github.com/kicsirigo/SelfCare/releases/download/v9.9.9/SelfCare-release.apk",
            apkSizeBytes = 100L,
            sha256SumsUrl = "https://github.com/kicsirigo/SelfCare/releases/download/v9.9.9/SHA256SUMS.txt"
        )
        val downloadResult = manager.downloadAndVerifyUpdate(info) { _, _ -> }
        assertTrue(
            "Expected redirect to non-allowlisted host to be rejected, got $downloadResult",
            downloadResult is UpdateDownloadResult.Error
        )
        assertFalse(
            "Client must never contact evil.example.com",
            requestedHosts.contains("evil.example.com")
        )

        // 2. Redirect during release metadata check must also be rejected
        val checkResult = manager.checkForUpdate("1.0.0")
        assertTrue(
            "Expected checkForUpdate redirect to non-allowlisted host to fail, got $checkResult",
            checkResult is UpdateCheckResult.Error
        )
        assertFalse(
            "Client must never contact evil.example.com",
            requestedHosts.contains("evil.example.com")
        )
    }
}
