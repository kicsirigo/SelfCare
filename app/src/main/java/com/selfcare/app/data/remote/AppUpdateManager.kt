package com.selfcare.app.data.remote

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
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
 * Enforces HTTPS-only downloads & validated redirects strictly within allowlisted GitHub hosts,
 * bounded response/file sizes, live streaming progress, and fail-closed SHA-256 verification.
 */
class AppUpdateManager(
    private val context: Context,
    httpClient: OkHttpClient = defaultClient(),
    private val latestReleaseUrl: String = DEFAULT_LATEST_RELEASE_URL
) {

    // Always disable automatic OkHttp redirects so every redirect hop is validated against the allowlist.
    private val safeHttpClient: OkHttpClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(currentVersionName: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                if (!isAllowedHttpsUrl(latestReleaseUrl)) {
                    return@withContext UpdateCheckResult.Error("Disallowed release URL")
                }
                val request = Request.Builder()
                    .url(latestReleaseUrl)
                    .get()
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SelfCare-Android/$currentVersionName")
                    .build()

                executeWithValidatedRedirects(request).use { response ->
                    if (!response.isSuccessful) {
                        return@withContext UpdateCheckResult.Error("HTTP ${response.code}")
                    }
                    val responseBody = response.body
                        ?: return@withContext UpdateCheckResult.Error("Empty response")
                    val body = readBoundedUtf8(responseBody, MAX_METADATA_BYTES)
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
                UpdateCheckResult.Error(e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "Network error")
            }
        }

    suspend fun downloadAndVerifyUpdate(
        info: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): UpdateDownloadResult = withContext(Dispatchers.IO) {
        if (!isAllowedHttpsUrl(info.apkDownloadUrl)) {
            return@withContext UpdateDownloadResult.Error("Invalid APK download URL")
        }

        // Fail-closed: refuse to download/install unless SHA256SUMS.txt URL is allowlisted and readable.
        val sumsUrl = info.sha256SumsUrl
        if (sumsUrl.isNullOrBlank() || !isAllowedHttpsUrl(sumsUrl)) {
            return@withContext UpdateDownloadResult.Error("Missing or invalid SHA-256 checksums URL")
        }

        val updatesDir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
        // Clean previous cached update APKs
        updatesDir.listFiles()?.forEach { runCatching { it.delete() } }

        val safeVersion = info.versionName.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val targetFile = File(updatesDir, "SelfCare-$safeVersion.apk")

        val expectedSha256 = fetchExpectedSha256(sumsUrl)
        if (expectedSha256 == null) {
            runCatching { targetFile.delete() }
            return@withContext UpdateDownloadResult.Error("Failed to fetch or parse expected SHA-256 checksum")
        }

        try {
            var downloaded = 0L
            var totalBytes = info.apkSizeBytes
            var attempt = 0
            val maxAttempts = 5
            var lastError: Exception? = null

            while (attempt < maxAttempts) {
                coroutineContext.ensureActive()
                attempt++
                try {
                    val reqBuilder = Request.Builder()
                        .url(info.apkDownloadUrl)
                        .get()
                        .header("User-Agent", "SelfCare-Android")
                        .header("Accept-Encoding", "identity")

                    if (downloaded > 0L) {
                        reqBuilder.header("Range", "bytes=$downloaded-")
                    }

                    executeWithValidatedRedirects(reqBuilder.build()).use { response ->
                        if (response.code == 416 && totalBytes > 0L && downloaded >= totalBytes) {
                            // Already downloaded completely
                            break
                        }
                        if (!response.isSuccessful) {
                            runCatching { targetFile.delete() }
                            return@withContext UpdateDownloadResult.Error("HTTP ${response.code}")
                        }
                        val body = response.body ?: run {
                            runCatching { targetFile.delete() }
                            return@withContext UpdateDownloadResult.Error("Empty body")
                        }
                        val isPartial = response.code == 206
                        if (!isPartial && downloaded > 0L) {
                            // Server ignored Range header; restart cleanly from 0
                            downloaded = 0L
                        }
                        val contentLen = body.contentLength()
                        if (contentLen > 0L) {
                            totalBytes = if (isPartial) downloaded + contentLen else contentLen
                        }
                        if (totalBytes > MAX_APK_BYTES) {
                            runCatching { targetFile.delete() }
                            return@withContext UpdateDownloadResult.Error("APK exceeds maximum allowed size")
                        }

                        val buffer = ByteArray(64 * 1024)
                        var lastReportMs = 0L

                        body.byteStream().use { input ->
                            FileOutputStream(targetFile, isPartial && downloaded > 0L).use { output ->
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    downloaded += read
                                    if (downloaded > MAX_APK_BYTES) {
                                        throw IllegalStateException("Download exceeded max APK size")
                                    }
                                    output.write(buffer, 0, read)

                                    val now = System.currentTimeMillis()
                                    if (now - lastReportMs >= 150L || (totalBytes > 0 && downloaded >= totalBytes)) {
                                        lastReportMs = now
                                        onProgress(downloaded, totalBytes)
                                    }
                                }
                                output.flush()
                            }
                        }
                    }

                    // Finished cleanly if we reached expected size (or server closed at EOF without Content-Length)
                    if (totalBytes <= 0L || downloaded >= totalBytes) {
                        lastError = null
                        break
                    }
                } catch (e: SecurityException) {
                    runCatching { targetFile.delete() }
                    return@withContext UpdateDownloadResult.Error(e.message ?: "Disallowed redirect")
                } catch (e: IOException) {
                    lastError = e
                    if (attempt >= maxAttempts) break
                    delay(1_200L * attempt)
                }
            }

            if (lastError != null && (totalBytes <= 0L || downloaded < totalBytes)) {
                runCatching { targetFile.delete() }
                return@withContext UpdateDownloadResult.Error(lastError::class.simpleName ?: "Network error")
            }

            val actualSha256 = computeFileSha256(targetFile)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                runCatching { targetFile.delete() }
                return@withContext UpdateDownloadResult.Error("SHA-256 verification failed")
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )
            UpdateDownloadResult.ReadyToInstall(
                apkFile = targetFile,
                contentUri = contentUri,
                sha256Verified = true
            )
        } catch (e: Exception) {
            runCatching { targetFile.delete() }
            if (e is kotlinx.coroutines.CancellationException) throw e
            UpdateDownloadResult.Error(e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "Download failed")
        }
    }

    /**
     * Executes an HTTP request while manually following up to [MAX_REDIRECTS] hops and verifying
     * that every redirect target remains on an allowlisted GitHub HTTPS host.
     */
    private fun executeWithValidatedRedirects(initialRequest: Request): Response {
        var currentRequest = initialRequest
        var redirects = 0
        while (true) {
            val currentUrl = currentRequest.url.toString()
            if (!isAllowedHttpsUrl(currentUrl)) {
                throw SecurityException("Disallowed URL blocked: $currentUrl")
            }
            val response = safeHttpClient.newCall(currentRequest).execute()
            if (response.code !in REDIRECT_STATUS_CODES) {
                return response
            }
            val location = response.header("Location")
            response.close()
            if (location.isNullOrBlank()) {
                throw IOException("Redirect response missing Location header")
            }
            redirects++
            if (redirects > MAX_REDIRECTS) {
                throw IOException("Too many redirects ($redirects)")
            }
            val resolved = currentRequest.url.resolve(location)
                ?: throw SecurityException("Invalid redirect Location URL")
            val resolvedStr = resolved.toString()
            if (!isAllowedHttpsUrl(resolvedStr)) {
                throw SecurityException("Redirect to disallowed host blocked")
            }
            currentRequest = currentRequest.newBuilder()
                .url(resolved)
                .build()
        }
    }

    private fun readBoundedUtf8(body: ResponseBody, maxBytes: Long): String {
        val declaredLength = body.contentLength()
        if (declaredLength > maxBytes) {
            throw IOException("Response exceeds maximum allowed size ($maxBytes bytes)")
        }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var totalRead = 0L
        body.byteStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                totalRead += read
                if (totalRead > maxBytes) {
                    throw IOException("Response exceeded maximum allowed size ($maxBytes bytes)")
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toString(StandardCharsets.UTF_8.name())
    }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fetchExpectedSha256(url: String): String? {
        if (!isAllowedHttpsUrl(url)) return null
        return runCatching {
            val req = Request.Builder().url(url).get().build()
            executeWithValidatedRedirects(req).use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body ?: return@use null
                val text = readBoundedUtf8(body, MAX_SHA256_SUMS_BYTES)
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
        const val MAX_METADATA_BYTES = 2L * 1024L * 1024L
        const val MAX_SHA256_SUMS_BYTES = 1L * 1024L * 1024L
        const val MAX_REDIRECTS = 5
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

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
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
