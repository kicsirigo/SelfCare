package com.selfcare.app.unit

import com.selfcare.app.data.local.CloudBackupCrypto
import com.selfcare.app.data.remote.CloudAuthResult
import com.selfcare.app.data.remote.CloudVaultDownloadResult
import com.selfcare.app.data.remote.CloudVaultUploadResult
import com.selfcare.app.data.remote.EstimatedMealInfo
import com.selfcare.app.data.remote.GroqVisionProductClient
import com.selfcare.app.data.remote.SupabaseCloudSaveClient
import com.selfcare.app.data.remote.WebSearchMealResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchAndCloudSaveTest {

    @Test
    fun `groq searchMealOnWeb parses macros and clickable executed_tools URLs`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "choices": [{
                        "message": {
                          "content": "{\"name\":\"Dubai csokoládé szelet\",\"estimatedGrams\":100,\"caloriesKcal\":545,\"proteinGrams\":8.5,\"carbsGrams\":46.0,\"fatGrams\":36.0,\"fiberGrams\":3.2,\"sugarGrams\":38.0,\"saltGrams\":0.25,\"detectedIngredients\":\"tejcsokoládé, pisztáciakrém, kadayif tészta\",\"clarificationQuestion\":\"\"}",
                          "executed_tools": [{
                            "type": "browser_search",
                            "search_results": {
                              "results": [
                                {"title": "Dubai Chocolate Nutrition Facts", "url": "https://example.com/dubai-chocolate-kcal"},
                                {"title": "Pistachio Kadayif Bar", "url": "https://nutrition.example.org/pistachio-bar"}
                              ]
                            }
                          }]
                        }
                      }]
                    }
                    """.trimIndent()
                )
            )
            val client = GroqVisionProductClient(
                baseUrl = server.url("/").toString(),
                delayFn = {}
            )
            val initial = EstimatedMealInfo(
                name = "Dubai csokoládé szelet",
                estimatedGrams = 100f,
                caloriesKcal = 500,
                proteinGrams = 7f,
                carbsGrams = 50f,
                fatGrams = 30f
            )
            val res = client.searchMealOnWeb(initial, "gsk_test_key")
            assertTrue(res is WebSearchMealResult.Success)
            val ok = res as WebSearchMealResult.Success
            assertEquals(545, ok.meal.caloriesKcal)
            assertEquals(2, ok.sources.size)
            assertEquals("https://example.com/dubai-chocolate-kcal", ok.sources[0].url)
            assertEquals("Web Search", ok.sources[0].sourceLabel)
            assertEquals("https://nutrition.example.org/pistachio-bar", ok.sources[1].url)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `cloud backup crypto round-trips with password and recovery key and fails on wrong password`() {
        val sampleJson = """{"schemaVersion":1,"products":[{"name":"CeraVe Cleanser"}],"nutritionLogs":[{"name":"Csirkemell","caloriesKcal":330}]}"""
        val password = "MySuperSecretPassword123!"

        val authSecret = CloudBackupCrypto.deriveAuthPassword("MateUser", password)
        assertFalse(authSecret.contains(password))
        assertTrue(authSecret.startsWith("sc1_"))

        val bundle = CloudBackupCrypto.createEncryptedVault(sampleJson, password)
        assertTrue(bundle.recoveryKey.startsWith("SC-"))
        assertFalse(bundle.envelopeJson.contains("Csirkemell"))

        val withPwd = CloudBackupCrypto.decryptVaultWithPassword(bundle.envelopeJson, password)
        assertTrue(withPwd.isSuccess)
        assertEquals(sampleJson, withPwd.getOrNull())

        val withRec = CloudBackupCrypto.decryptVaultWithRecoveryKey(bundle.envelopeJson, bundle.recoveryKey.lowercase())
        assertTrue(withRec.isSuccess)
        assertEquals(sampleJson, withRec.getOrNull())

        val wrongPwd = CloudBackupCrypto.decryptVaultWithPassword(bundle.envelopeJson, "WrongPassword999")
        assertTrue(wrongPwd.isFailure)
    }

    @Test
    fun `supabase cloud save client signs up, uploads, and downloads encrypted vault`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"access_token":"jwt_access_123","refresh_token":"jwt_refresh_456","user":{"id":"uid-999"}}"""
                )
            )
            server.enqueue(MockResponse().setResponseCode(201).setBody(""))
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """[{"payload":"{\"schema\":1,\"payloadCiphertext\":\"abc\"}","updated_at":1795500000000}]"""
                )
            )

            val client = SupabaseCloudSaveClient()
            val baseUrl = server.url("/").toString().trimEnd('/')

            val auth = client.signUp(baseUrl, "anon_key_xyz", "Mate_01", "Pass123456")
            assertTrue(auth is CloudAuthResult.Success)
            val sess = (auth as CloudAuthResult.Success).session
            assertEquals("uid-999", sess.userId)
            assertEquals("jwt_access_123", sess.accessToken)

            val up = client.uploadVault(
                serverUrl = baseUrl,
                anonKey = "anon_key_xyz",
                accessToken = sess.accessToken,
                userId = sess.userId,
                encryptedEnvelopeJson = """{"schema":1,"payloadCiphertext":"abc"}""",
                updatedAtEpochMillis = 1795500000000L
            )
            assertTrue(up is CloudVaultUploadResult.Success)

            val dl = client.downloadVault(baseUrl, "anon_key_xyz", sess.accessToken, sess.userId)
            assertTrue(dl is CloudVaultDownloadResult.Found)
            val rec = (dl as CloudVaultDownloadResult.Found).record
            assertEquals(1795500000000L, rec.updatedAtEpochMillis)
            assertTrue(rec.payload.contains("payloadCiphertext"))
        } finally {
            server.shutdown()
        }
    }
}
