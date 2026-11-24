package com.selfcare.app.data.remote

import com.selfcare.app.data.local.CloudBackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class CloudSession(
    val userId: String,
    val username: String,
    val accessToken: String,
    val refreshToken: String
)

data class CloudVaultRecord(
    val payload: String,
    val updatedAtEpochMillis: Long
)

sealed interface CloudAuthResult {
    data class Success(val session: CloudSession) : CloudAuthResult
    data class Error(val message: String) : CloudAuthResult
}

sealed interface CloudVaultUploadResult {
    data class Success(val updatedAtEpochMillis: Long) : CloudVaultUploadResult
    data class Error(val message: String) : CloudVaultUploadResult
}

sealed interface CloudVaultDownloadResult {
    data class Found(val record: CloudVaultRecord) : CloudVaultDownloadResult
    data object NotFound : CloudVaultDownloadResult
    data class Error(val message: String) : CloudVaultDownloadResult
}

/**
 * Pure OkHttp REST client for Supabase Auth + PostgREST (`public.cloud_saves`).
 * Zero extra SDK dependencies; compatible with both Supabase Cloud (EU Free Tier)
 * and self-hosted on-prem Supabase instances.
 */
class SupabaseCloudSaveClient(
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun signUp(
        serverUrl: String,
        anonKey: String,
        username: String,
        userPassword: String
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanKey = anonKey.trim()
        val cleanUser = normalizeUsername(username)
        if (cleanUrl.isEmpty() || cleanKey.isEmpty()) {
            return@withContext CloudAuthResult.Error("Cloud Save server URL or Anon Key is missing")
        }
        if (cleanUser.length < 3) {
            return@withContext CloudAuthResult.Error("Username must be at least 3 characters (letters/digits/._-)")
        }
        if (userPassword.length < 6) {
            return@withContext CloudAuthResult.Error("Password must be at least 6 characters")
        }

        val email = usernameToSyntheticEmail(cleanUser)
        val authPass = CloudBackupCrypto.deriveAuthPassword(cleanUser, userPassword)
        val bodyJson = buildJsonObject {
            put("email", email)
            put("password", authPass)
        }.toString()

        val request = Request.Builder()
            .url("$cleanUrl/auth/v1/signup")
            .header("apikey", cleanKey)
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext CloudAuthResult.Error(extractError(body, resp.code))
                }
                parseAuthSession(body, cleanUser)
                    ?: CloudAuthResult.Error(
                        "Account created, but email confirmation is enabled on the server. Disable 'Confirm email' in Supabase Auth settings."
                    )
            }
        }.getOrElse { e ->
            CloudAuthResult.Error("Network error: ${e.message ?: e::class.simpleName}")
        }
    }

    suspend fun signIn(
        serverUrl: String,
        anonKey: String,
        username: String,
        userPassword: String
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanKey = anonKey.trim()
        val cleanUser = normalizeUsername(username)
        if (cleanUrl.isEmpty() || cleanKey.isEmpty()) {
            return@withContext CloudAuthResult.Error("Cloud Save server URL or Anon Key is missing")
        }
        if (cleanUser.isEmpty() || userPassword.isEmpty()) {
            return@withContext CloudAuthResult.Error("Username and password are required")
        }

        val email = usernameToSyntheticEmail(cleanUser)
        val authPass = CloudBackupCrypto.deriveAuthPassword(cleanUser, userPassword)
        val bodyJson = buildJsonObject {
            put("email", email)
            put("password", authPass)
        }.toString()

        val request = Request.Builder()
            .url("$cleanUrl/auth/v1/token?grant_type=password")
            .header("apikey", cleanKey)
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext CloudAuthResult.Error(extractError(body, resp.code))
                }
                parseAuthSession(body, cleanUser)
                    ?: CloudAuthResult.Error("Invalid auth response from server")
            }
        }.getOrElse { e ->
            CloudAuthResult.Error("Network error: ${e.message ?: e::class.simpleName}")
        }
    }

    suspend fun refreshSession(
        serverUrl: String,
        anonKey: String,
        username: String,
        refreshToken: String
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanKey = anonKey.trim()
        if (cleanUrl.isEmpty() || cleanKey.isEmpty() || refreshToken.isBlank()) {
            return@withContext CloudAuthResult.Error("Session refresh credentials missing")
        }
        val bodyJson = buildJsonObject {
            put("refresh_token", refreshToken.trim())
        }.toString()

        val request = Request.Builder()
            .url("$cleanUrl/auth/v1/token?grant_type=refresh_token")
            .header("apikey", cleanKey)
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext CloudAuthResult.Error(extractError(body, resp.code))
                }
                parseAuthSession(body, username)
                    ?: CloudAuthResult.Error("Invalid refresh response")
            }
        }.getOrElse { e ->
            CloudAuthResult.Error("Network error: ${e.message ?: e::class.simpleName}")
        }
    }

    suspend fun uploadVault(
        serverUrl: String,
        anonKey: String,
        accessToken: String,
        userId: String,
        encryptedEnvelopeJson: String,
        updatedAtEpochMillis: Long = System.currentTimeMillis()
    ): CloudVaultUploadResult = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanKey = anonKey.trim()
        val bodyJson = buildJsonObject {
            put("user_id", userId)
            put("payload", encryptedEnvelopeJson)
            put("updated_at", updatedAtEpochMillis)
        }.toString()

        val request = Request.Builder()
            .url("$cleanUrl/rest/v1/cloud_saves?on_conflict=user_id")
            .header("apikey", cleanKey)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext CloudVaultUploadResult.Error(extractError(body, resp.code))
                }
                CloudVaultUploadResult.Success(updatedAtEpochMillis)
            }
        }.getOrElse { e ->
            CloudVaultUploadResult.Error("Network error: ${e.message ?: e::class.simpleName}")
        }
    }

    suspend fun downloadVault(
        serverUrl: String,
        anonKey: String,
        accessToken: String,
        userId: String
    ): CloudVaultDownloadResult = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanKey = anonKey.trim()
        val encodedUid = URLEncoder.encode(userId, "UTF-8")

        val request = Request.Builder()
            .url("$cleanUrl/rest/v1/cloud_saves?user_id=eq.$encodedUid&select=payload,updated_at&limit=1")
            .header("apikey", cleanKey)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext CloudVaultDownloadResult.Error(extractError(body, resp.code))
                }
                val arr = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull()
                    ?: return@withContext CloudVaultDownloadResult.Error("Malformed vault response")
                if (arr.isEmpty()) return@withContext CloudVaultDownloadResult.NotFound
                val row = arr.first().jsonObject
                val payload = row["payload"]?.jsonPrimitive?.contentOrNull
                    ?: return@withContext CloudVaultDownloadResult.NotFound
                val updatedAt = row["updated_at"]?.jsonPrimitive?.longOrNull ?: 0L
                CloudVaultDownloadResult.Found(CloudVaultRecord(payload = payload, updatedAtEpochMillis = updatedAt))
            }
        }.getOrElse { e ->
            CloudVaultDownloadResult.Error("Network error: ${e.message ?: e::class.simpleName}")
        }
    }

    private fun parseAuthSession(rawJson: String, username: String): CloudAuthResult.Success? {
        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
        val accessToken = root["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null
        val refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val userId = root["user"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null
        return CloudAuthResult.Success(
            CloudSession(
                userId = userId,
                username = username,
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        )
    }

    private fun extractError(body: String, httpCode: Int): String {
        val parsed = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error_description"]?.jsonPrimitive?.contentOrNull
                ?: obj["msg"]?.jsonPrimitive?.contentOrNull
                ?: obj["message"]?.jsonPrimitive?.contentOrNull
                ?: obj["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return if (!parsed.isNullOrBlank()) "HTTP $httpCode: $parsed" else "HTTP $httpCode"
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun normalizeUsername(raw: String): String =
            raw.trim().lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }

        fun usernameToSyntheticEmail(rawUsername: String): String {
            val clean = normalizeUsername(rawUsername)
            return "$clean@selfcare.app"
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
