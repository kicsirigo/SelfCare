package com.selfcare.app.unit

import com.selfcare.app.data.remote.GeminiKeyTestResult
import com.selfcare.app.data.remote.GeminiVisionResult
import com.selfcare.app.data.remote.GroqVisionProductClient
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
    fun testApiKey_discoversLiveVisionModelsFromCatalogAndRetries503() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val secretKey = "gsk_TestSecretGroqKey987654321"
            // 1. Live /openai/v1/models discovery response
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "data": [
                            {"id": "whisper-large-v3"},
                            {"id": "llama-3.3-70b-versatile"},
                            {"id": "meta-llama/llama-4-scout-17b-16e-instruct"},
                            {"id": "qwen/qwen2.5-vl-32b-instruct"}
                          ]
                        }
                        """.trimIndent()
                    )
            )
            // 2. 3 attempts on llama-4-scout return 503
            repeat(3) {
                server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"message":"Overloaded"}}"""))
            }
            // 3. Discovered fallback qwen2.5-vl succeeds
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"OK"}}]}"""))

            val delays = mutableListOf<Long>()
            val mockClient = GroqVisionProductClient(
                baseUrl = server.url("/").toString(),
                delayFn = { delays.add(it) }
            )

            val result = mockClient.testApiKey(secretKey)
            assertTrue(result is GeminiKeyTestResult.Success)
            assertEquals("qwen2.5-vl-32b", (result as GeminiKeyTestResult.Success).modelUsed)
            assertEquals(listOf(800L, 1800L), delays)

            val modelsReq = server.takeRequest()
            assertEquals("/openai/v1/models", modelsReq.path)
            assertEquals("Bearer $secretKey", modelsReq.getHeader("Authorization"))

            // Verify error messages redact the Groq API key
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid key $secretKey"}}"""))
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid key $secretKey"}}"""))
            val err = mockClient.testApiKey(secretKey)
            assertTrue(err is GeminiKeyTestResult.Failure)
            assertFalse((err as GeminiKeyTestResult.Failure).reason.contains(secretKey))
        } finally {
            server.shutdown()
        }
    }
}
