package com.selfcare.app.unit

import com.selfcare.app.data.remote.OpenBeautyFactsClient
import com.selfcare.app.data.remote.OpenBeautyFactsLookupResult
import com.selfcare.app.data.remote.OpenBeautyFactsSearchResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenBeautyFactsTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenBeautyFactsClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        client = OpenBeautyFactsClient(
            okHttpClient = okHttp,
            baseUrl = server.url("/").toString()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchByBarcode_returnsFound_andSendsZeroTrackingHeadersOrParams() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "status": 1,
                      "product": {
                        "code": "3337875597388",
                        "product_name": "Hydrating Cleanser",
                        "brands": "CeraVe",
                        "ingredients_text_en": "Aqua, Glycerin, Niacinamide"
                      }
                    }
                    """.trimIndent()
                )
        )

        val result = client.fetchByBarcode("3337875597388")
        assertTrue(result is OpenBeautyFactsLookupResult.Found)
        val found = result as OpenBeautyFactsLookupResult.Found
        assertEquals("3337875597388", found.product.barcode)
        assertEquals("Hydrating Cleanser", found.product.name)
        assertEquals("CeraVe", found.product.brand)
        assertEquals("Aqua, Glycerin, Niacinamide", found.product.ingredientsText)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.startsWith("/api/v2/product/3337875597388.json"))
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("Cookie"))
        assertNull(recorded.getHeader("X-Device-Id"))
        assertNull(recorded.getHeader("X-User-Id"))
        val query = recorded.requestUrl?.queryParameterNames.orEmpty()
        assertEquals(setOf("fields"), query)
    }

    @Test
    fun fetchByBarcode_handlesMissingOptionalFields_andFallsBackToIngredientsText() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "status": 1,
                      "product": {
                        "product_name": "Simple Serum",
                        "ingredients_text": "Aqua, Hyaluronic Acid"
                      }
                    }
                    """.trimIndent()
                )
        )

        val result = client.fetchByBarcode("12345678")
        assertTrue(result is OpenBeautyFactsLookupResult.Found)
        val found = result as OpenBeautyFactsLookupResult.Found
        assertEquals("12345678", found.product.barcode)
        assertEquals("Simple Serum", found.product.name)
        assertEquals("", found.product.brand)
        assertEquals("Aqua, Hyaluronic Acid", found.product.ingredientsText)
    }

    @Test
    fun fetchByBarcode_returnsNotFound_onStatusZeroOr404() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":0}"""))
        val statusZero = client.fetchByBarcode("00000000")
        assertTrue(statusZero is OpenBeautyFactsLookupResult.NotFound)

        server.enqueue(MockResponse().setResponseCode(404))
        val notFound404 = client.fetchByBarcode("99999999")
        assertTrue(notFound404 is OpenBeautyFactsLookupResult.NotFound)
    }

    @Test
    fun fetchByBarcode_returnsOfflineOrError_onHttp500MalformedJsonOrNetworkFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val httpError = client.fetchByBarcode("11112222")
        assertTrue(httpError is OpenBeautyFactsLookupResult.OfflineOrError)

        server.enqueue(MockResponse().setResponseCode(200).setBody("{not-valid-json"))
        val badJson = client.fetchByBarcode("11112222")
        assertTrue(badJson is OpenBeautyFactsLookupResult.OfflineOrError)

        server.shutdown()
        val offline = client.fetchByBarcode("11112222")
        assertTrue(offline is OpenBeautyFactsLookupResult.OfflineOrError)
    }

    @Test
    fun searchByName_returnsProducts_andHandlesErrorsGracefully() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "products": [
                        {
                          "code": "101010",
                          "product_name": "Retinol 0.5%",
                          "brands": "TestBrand",
                          "ingredients_text": "Aqua, Retinol"
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val result = client.searchByName("Retinol")
        assertTrue(result is OpenBeautyFactsSearchResult.Success)
        val success = result as OpenBeautyFactsSearchResult.Success
        assertEquals(1, success.products.size)
        assertEquals("Retinol 0.5%", success.products.first().name)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertFalse(recorded.path!!.contains("user_id"))
        assertFalse(recorded.path!!.contains("device_id"))

        server.enqueue(MockResponse().setResponseCode(500))
        val errorResult = client.searchByName("Retinol")
        assertTrue(errorResult is OpenBeautyFactsSearchResult.OfflineOrError)
    }

    @Test
    fun buildSmartReferenceQueries_stripsDosageAndPackNoise() {
        val queries = OpenBeautyFactsClient.buildSmartReferenceQueries(
            brand = "BioCo",
            name = "D3-vitamin 2000 NE 90 db filmtabletta"
        )
        assertTrue(queries.any { it.equals("BioCo D3-vitamin 2000 NE 90 db filmtabletta", ignoreCase = true) })
        assertTrue(queries.any { it.contains("BioCo D3-vitamin", ignoreCase = true) && !it.contains("90 db") })
    }

    @Test
    fun findReferenceImageUrls_collectsAtLeastThreeDistinctHttpsImages() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "products": [
                        {"product_name": "Effaclar Duo 1", "image_front_small_url": "https://images.example.com/1.jpg"},
                        {"product_name": "Effaclar Duo 2", "image_front_small_url": "https://images.example.com/2.jpg"},
                        {"product_name": "Effaclar Duo 3", "image_front_url": "https://images.example.com/3.jpg"},
                        {"product_name": "Effaclar Duo 4", "image_url": "http://insecure.example.com/4.jpg"}
                      ]
                    }
                    """.trimIndent()
                )
        )

        val urls = client.findReferenceImageUrls(
            brand = "La Roche-Posay",
            name = "Effaclar Duo+M 40 ml",
            category = "OTHER",
            desiredCount = 3
        )
        assertEquals(3, urls.size)
        assertTrue(urls.all { it.startsWith("https://") })
    }
}
