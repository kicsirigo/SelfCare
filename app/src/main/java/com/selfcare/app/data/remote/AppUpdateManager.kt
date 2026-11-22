package com.selfcare.app.data.remote

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class AppUpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val sha256SumsUrl: String?
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data class UpToDate(val latestVersion: String) : UpdateCheckResult
    data class Error(val reason: String) : UpdateCheckResult
}

sealed interface UpdateDownloadResult {
    data class ReadyToInstall(val apkFile: File, val contentUri: Uri, val sha256Verified: Boolean) : UpdateDownloadResult
    data class Error(val reason: String) : UpdateDownloadResult
}

/**
 * Privacy-first, read-only in-app GitHub Releases download manager.
 * Enforces HTTPS-only downloads from GitHub hosts, bounded file sizes,
 * live streaming progress, and SHA-256 verification against SHA256SUMS.txt.
 */
class AppUpdateManager(
    private val context: Context,
    private val httpClient: OkHttpClient = defaultClient(),
    private val latestReleaseUrl: String = DEFAULT_LATEST_RELEASE_URL
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(currentVersionName: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(latestReleaseUrl)
                    .get()
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SelfCare-Android/$currentVersionName")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext UpdateCheckResult.Error("HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val latestVersion = tagName.removePrefix("v").removePrefix("V")
                    if (latestVersion.isEmpty()) {
                        return@withContext UpdateCheckResult.Error("Missing tag_name")
                    }

                    val releaseNotes = root["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val assets = root["assets"]?.jsonArray.orEmpty().map { it.jsonObject }

                    val apkAsset = assets.firstOrNull {
                        it["name"]?.jsonPrimitive?.contentOrNull.equals(RELEASE_APK_NAME, ignoreCase = true)
                    }
                    val apkUrl = apkAsset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
                        ?.takeIf { isAllowedHttpsUrl(it) }
                    val apkSize = apkAsset?.get("size")?.jsonPrimitive?.longOrNull ?: 0L

                    val sumsAsset = assets.firstOrNull {
                        it["name"]?.jsonPrimitive?.contentOrNull.equals(SHA256_SUMS_NAME, ignoreCase = true)
                    }
                    val sumsUrl = sumsAsset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
                        ?.takeIf { isAllowedHttpsUrl(it) }

                    if (compareSemVer(latestVersion, currentVersionName) > 0 && apkUrl != null) {
                        UpdateCheckResult.UpdateAvailable(
                            AppUpdateInfo(
                                versionName = latestVersion,
                                tagName = tagName,
                                releaseNotes = releaseNotes,
                                apkDownloadUrl = apkUrl,
                                apkSizeBytes = apkSize,
                                sha256SumsUrl = sumsUrl
                            )
                        )
                    } else {
                        UpdateCheckResult.UpToDate(latestVersion)
                    }
                }
            } catch (e: Exception) {
                UpdateCheckResult.Error(e::class.simpleName ?: "Network error")
            }
        }

    suspend fun downloadAndVerifyUpdate(
        info: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): UpdateDownloadResult = withContext(Dispatchers.IO) {
        if (!isAllowedHttpsUrl(info.apkDownloadUrl)) {
            return@withContext UpdateDownloadResult.Error("Invalid APK download URL")
        }

        val updatesDir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
        // Clean previous cached update APKs
        updatesDir.listFiles()?.forEach { runCatching { it.delete() } }

        val expectedSha256 = info.sha256SumsUrl?.let { fetchExpectedSha256(it) }
        val safeVersion = info.versionName.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val targetFile = File(updatesDir, "SelfCare-$safeVersion.apk")

        try {
            val request = Request.Builder()
                .url(info.apkDownloadUrl)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateDownloadResult.Error("HTTP ${response.code}")
                }
                val body = response.body ?: return@withContext UpdateDownloadResult.Error("Empty body")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: info.apkSizeBytes
                if (totalBytes > MAX_APK_BYTES) {
                    return@withContext UpdateDownloadResult.Error("APK exceeds maximum allowed size")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16 * 1024)
                var downloaded = 0L
                var lastReportMs = 0L

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            downloaded += read
                            if (downloaded > MAX_APK_BYTES) {
                                throw IllegalStateException("Download exceeded max APK size")
                            }
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)

                            val now = System.currentTimeMillis()
                            if (now - lastReportMs >= 120L || (totalBytes > 0 && downloaded >= totalBytes)) {
                                lastReportMs = now
                                onProgress(downloaded, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                val verified = if (expectedSha256 != null) {
                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        runCatching { targetFile.delete() }
                        return@withContext UpdateDownloadResult.Error("SHA-256 verification failed")
                    }
                    true
                } else {
                    false
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetFile
                )
                UpdateDownloadResult.ReadyToInstall(
                    apkFile = targetFile,
                    contentUri = contentUri,
                    sha256Verified = verified
                )
            }
        } catch (e: Exception) {
            runCatching { targetFile.delete() }
            if (e is kotlinx.coroutines.CancellationException) throw e
            UpdateDownloadResult.Error(e::class.simpleName ?: "Download failed")
        }
    }

    private fun fetchExpectedSha256(url: String): String? {
        if (!isAllowedHttpsUrl(url)) return null
        return runCatching {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val text = resp.body?.string().orEmpty()
                parseSha256ForFile(text, RELEASE_APK_NAME)
            }
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_LATEST_RELEASE_URL =
            "https://api.github.com/repos/kicsirigo/SelfCare/releases/latest"
        const val RELEASE_APK_NAME = "SelfCare-release.apk"
        const val SHA256_SUMS_NAME = "SHA256SUMS.txt"
        const val UPDATES_DIR = "updates"
        const val MAX_APK_BYTES = 300L * 1024L * 1024L

        fun isAllowedHttpsUrl(url: String): Boolean {
            val clean = url.trim().lowercase()
            return clean.startsWith("https://github.com/") ||
                clean.startsWith("https://objects.githubusercontent.com/") ||
                clean.startsWith("https://release-assets.githubusercontent.com/") ||
                clean.startsWith("https://api.github.com/")
        }

        fun compareSemVer(a: String, b: String): Int {
            val partsA = a.removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
            val partsB = b.removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
            val len = maxOf(partsA.size, partsB.size)
            for (i in 0 until len) {
                val va = partsA.getOrElse(i) { 0 }
                val vb = partsB.getOrElse(i) { 0 }
                if (va != vb) return va.compareTo(vb)
            }
            return 0
        }

        fun parseSha256ForFile(sumsContent: String, targetFileName: String): String? {
            return sumsContent.lineSequence()
                .map { it.trim() }
                .firstOrNull { line -> line.endsWith(targetFileName, ignoreCase = true) }
                ?.split(Regex("\\s+"))
                ?.firstOrNull()
                ?.takeIf { it.length == 64 && it.all { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' } }
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
