package com.selfcare.app.unit

import com.selfcare.app.data.remote.GeminiKeyTestResult
import com.selfcare.app.data.remote.GeminiVisionProductClient
import com.selfcare.app.data.remote.GeminiVisionResult
import com.selfcare.app.domain.inci.InciConflictDetector
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiVisionProductClientTest {

    private val client = GeminiVisionProductClient()

    @Test
    fun parseGeminiResponseJson_extractsRxProductAndNormalizesFields() {
        val raw = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\"name\":\"Davercin 25 mg/g Gel\",\"brand\":\"Polfa Tarchomin\",\"category\":\"RX\",\"routineSlot\":\"PM\",\"ingredients\":\"Erythromycin Cyclocarbonate\"}"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = client.parseGeminiResponseJson(raw)
        assertTrue(result is GeminiVisionResult.Success)
        val info = (result as GeminiVisionResult.Success).product
        assertEquals("Davercin 25 mg/g Gel", info.name)
        assertEquals("Polfa Tarchomin", info.brand)
        assertEquals("RX_TREATMENT", info.category)
        assertEquals(InciConflictDetector.SLOT_EVENING, info.routineSlot)
        assertEquals("Erythromycin Cyclocarbonate", info.ingredientsText)
    }

    @Test
    fun parseGeminiResponseJson_stripsMarkdownCodeFencesForHairProduct() {
        val raw = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\n{\"name\":\"Nizoral 2% Shampoo\",\"brand\":\"Stada\",\"category\":\"HAIR\",\"routineSlot\":\"HAIR\",\"ingredients\":\"Ketoconazole\"}\n```"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = client.parseGeminiResponseJson(raw)
        assertTrue(result is GeminiVisionResult.Success)
        val info = (result as GeminiVisionResult.Success).product
        assertEquals("Nizoral 2% Shampoo", info.name)
        assertEquals("SHAMPOO", info.category)
        assertEquals(InciConflictDetector.SLOT_HAIR, info.routineSlot)
    }

    @Test
    fun parseGeminiResponseJson_returnsErrorWhenNoProductIdentified() {
        val raw = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\"name\":\"\",\"brand\":\"\",\"category\":\"OTHER\",\"routineSlot\":\"NONE\",\"ingredients\":\"\"}"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = client.parseGeminiResponseJson(raw)
        assertTrue(result is GeminiVisionResult.Error)
        assertEquals("NO_PRODUCT_IDENTIFIED", (result as GeminiVisionResult.Error).reason)
    }

    @Test
    fun testApiKey_sendsKeyInHeaderNotQueryParamAndRedactsKeyInErrors() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val secretKey = "AIzaSyTestSecretKey123456"
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"candidates":[]}"""))

            val mockClient = GeminiVisionProductClient(baseUrl = server.url("/").toString())
            val okResult = mockClient.testApiKey(secretKey)
            assertTrue(okResult is GeminiKeyTestResult.Success)

            val recorded = server.takeRequest()
            assertEquals(secretKey, recorded.getHeader("x-goog-api-key"))
            assertFalse("API key must not appear in URL query", recorded.path.orEmpty().contains("key="))
            assertFalse("API key value must not appear in path", recorded.path.orEmpty().contains(secretKey))

            // Verify error responses scrub any leaked API key or URL
            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setBody("""{"error":{"message":"Invalid key $secretKey at https://generativelanguage.googleapis.com/v1beta?key=$secretKey"}}""")
            )
            val errResult = mockClient.testApiKey(secretKey)
            assertTrue(errResult is GeminiKeyTestResult.Failure)
            val reason = (errResult as GeminiKeyTestResult.Failure).reason
            assertFalse("Error message must not leak API key", reason.contains(secretKey))
        } finally {
            server.shutdown()
        }
    }
}
