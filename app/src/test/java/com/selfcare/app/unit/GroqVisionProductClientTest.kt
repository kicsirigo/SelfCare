package com.selfcare.app.unit

import com.selfcare.app.data.remote.AutoRoutingVisionClient
import com.selfcare.app.data.remote.GeminiKeyTestResult
import com.selfcare.app.data.remote.GeminiVisionResult
import com.selfcare.app.data.remote.GroqVisionProductClient
import com.selfcare.app.data.remote.ProductVisionClient
import com.selfcare.app.domain.inci.InciConflictDetector
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqVisionProductClientTest {

    private val client = GroqVisionProductClient()

    @Test
    fun parseGroqResponseJson_extractsProductFromMarkdownFencedJson() {
        val raw = """
            {
              "choices": [
                {
                  "message": {
                    "content": "```json\n{\"name\":\"CeraVe Foaming Cleanser\",\"brand\":\"CeraVe\",\"category\":\"OTHER\",\"routineSlot\":\"BOTH\",\"ingredients\":\"Ceramides, Niacinamide, Hyaluronic Acid\"}\n```"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = client.parseGroqResponseJson(raw)
        assertTrue(result is GeminiVisionResult.Success)
        val info = (result as GeminiVisionResult.Success).product
        assertEquals("CeraVe Foaming Cleanser", info.name)
        assertEquals("CeraVe", info.brand)
        assertEquals("OTHER", info.category)
        assertEquals(InciConflictDetector.SLOT_BOTH, info.routineSlot)
        assertEquals("Ceramides, Niacinamide, Hyaluronic Acid", info.ingredientsText)
    }

    @Test
    fun testApiKey_sendsBearerAuthHeaderAndRetries503BeforeFallingBack() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val secretKey = "gsk_TestSecretGroqKey987654321"
            // 3 attempts on llama-4-scout return 503
            repeat(3) {
                server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"message":"Overloaded"}}"""))
            }
            // Fallback llama-4-maverick succeeds
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"OK"}}]}"""))

            val delays = mutableListOf<Long>()
            val mockClient = GroqVisionProductClient(
                baseUrl = server.url("/").toString(),
                delayFn = { delays.add(it) }
            )

            val result = mockClient.testApiKey(secretKey)
            assertTrue(result is GeminiKeyTestResult.Success)
            assertEquals("llama-4-maverick-17b-128e", (result as GeminiKeyTestResult.Success).modelUsed)
            assertEquals(listOf(800L, 1800L), delays)

            val firstReq = server.takeRequest()
            assertEquals("Bearer $secretKey", firstReq.getHeader("Authorization"))

            // Verify error messages redact the Groq API key
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""{"error":{"message":"Invalid key $secretKey"}}""")
            )
            val err = mockClient.testApiKey(secretKey)
            assertTrue(err is GeminiKeyTestResult.Failure)
            assertFalse((err as GeminiKeyTestResult.Failure).reason.contains(secretKey))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun autoRoutingVisionClient_routesGskKeysToGroqAndOtherKeysToGemini() = runTest {
        val groqFake = object : ProductVisionClient {
            override suspend fun identifyProduct(bitmap: android.graphics.Bitmap, apiKey: String) =
                GeminiVisionResult.Error("groq")
            override suspend fun testApiKey(apiKey: String) =
                GeminiKeyTestResult.Success("llama-4-scout-17b-16e")
        }
        val geminiFake = object : ProductVisionClient {
            override suspend fun identifyProduct(bitmap: android.graphics.Bitmap, apiKey: String) =
                GeminiVisionResult.Error("gemini")
            override suspend fun testApiKey(apiKey: String) =
                GeminiKeyTestResult.Success("gemini-3.8-flash")
        }

        val router = AutoRoutingVisionClient(groqClient = groqFake, geminiClient = geminiFake)
        val groqRes = router.testApiKey("gsk_live_abc123")
        val geminiRes = router.testApiKey("AIzaSyTest123")

        assertEquals("llama-4-scout-17b-16e", (groqRes as GeminiKeyTestResult.Success).modelUsed)
        assertEquals("gemini-3.8-flash", (geminiRes as GeminiKeyTestResult.Success).modelUsed)
    }
}
