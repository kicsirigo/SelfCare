package com.selfcare.app.unit

import com.selfcare.app.data.remote.FoodLookupResult
import com.selfcare.app.data.remote.GroqVisionProductClient
import com.selfcare.app.data.remote.MealVisionResult
import com.selfcare.app.data.remote.OpenFoodFactsNutritionClient
import com.selfcare.app.domain.health.ActivityLevel
import com.selfcare.app.domain.health.FoodPresetCatalog
import com.selfcare.app.domain.health.MacroCalculator
import com.selfcare.app.domain.health.MacroGoalType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroCalculatorAndNutritionTest {

    @Test
    fun `calculateTargets produces realistic cut maintain and bulk targets`() {
        val cut = MacroCalculator.calculateTargets(
            weightKg = 80f,
            heightCm = 180f,
            ageYears = 25,
            isMale = true,
            activityLevel = ActivityLevel.MODERATE,
            goal = MacroGoalType.CUT
        )
        val bulk = MacroCalculator.calculateTargets(
            weightKg = 80f,
            heightCm = 180f,
            ageYears = 25,
            isMale = true,
            activityLevel = ActivityLevel.MODERATE,
            goal = MacroGoalType.BULK
        )
        assertTrue("Bulk kcal (${bulk.caloriesKcal}) should exceed cut (${cut.caloriesKcal})", bulk.caloriesKcal > cut.caloriesKcal + 400)
        assertTrue("Cut protein should be high (~160g), got ${cut.proteinGrams}", cut.proteinGrams in 140..180)
        assertTrue("Water target should be realistic, got ${cut.waterMl}", cut.waterMl in 2500..4000)
    }

    @Test
    fun `scalePer100g scales calories and macros proportionally`() {
        val scaled = MacroCalculator.scalePer100g(
            grams = 150f,
            per100gKcal = 165f,
            per100gProtein = 31f,
            per100gCarbs = 0f,
            per100gFat = 3.6f
        )
        assertEquals(248, scaled.caloriesKcal)
        assertEquals(46.5f, scaled.proteinGrams, 0.1f)
        assertEquals(0f, scaled.carbsGrams, 0.1f)
        assertEquals(5.4f, scaled.fatGrams, 0.1f)
    }

    @Test
    fun `food preset catalog matches Hungarian and English queries`() {
        assertTrue(FoodPresetCatalog.search("csirkemell").isNotEmpty())
        assertTrue(FoodPresetCatalog.search("whey").isNotEmpty())
        assertTrue(FoodPresetCatalog.search("túró").isNotEmpty())
    }

    @Test
    fun `openFoodFactsNutritionClient parses barcode nutriments and handles 404`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "status": 1,
                      "product": {
                        "code": "5991234567890",
                        "product_name": "High Protein Pudding Vanilla",
                        "brands": "Ehrmann",
                        "serving_quantity": 200,
                        "nutriments": {
                          "energy-kcal_100g": 76,
                          "proteins_100g": 10.0,
                          "carbohydrates_100g": 5.5,
                          "fat_100g": 1.5,
                          "fiber_100g": 0.4,
                          "sugars_100g": 4.2
                        }
                      }
                    }
                    """.trimIndent()
                )
            )
            val client = OpenFoodFactsNutritionClient(baseUrl = server.url("/").toString())
            val found = client.fetchByBarcode("5991234567890")
            assertTrue(found is FoodLookupResult.Found)
            val item = (found as FoodLookupResult.Found).item
            assertEquals("High Protein Pudding Vanilla", item.name)
            assertEquals("Ehrmann", item.brand)
            assertEquals(76f, item.per100gKcal, 0.1f)
            assertEquals(10f, item.per100gProtein, 0.1f)
            assertEquals(200f, item.defaultGrams, 0.1f)

            server.enqueue(MockResponse().setResponseCode(404))
            val notFound = client.fetchByBarcode("0000000000000")
            assertTrue(notFound is FoodLookupResult.NotFound)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `groq meal vision parser strips think tags and parses estimated macros`() {
        val client = GroqVisionProductClient()
        val raw = """
            {
              "choices": [
                {
                  "message": {
                    "content": "<think>I see grilled chicken breast with jasmine rice and steamed broccoli.</think>\n```json\n{\"name\":\"Grilled Chicken Breast with Rice & Broccoli\",\"estimatedGrams\":420,\"caloriesKcal\":540,\"proteinGrams\":48.5,\"carbsGrams\":56.0,\"fatGrams\":9.5,\"fiberGrams\":4.2,\"sugarGrams\":2.1}\n```"
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = client.parseGroqMealResponseJson(raw)
        assertTrue(parsed is MealVisionResult.Success)
        val meal = (parsed as MealVisionResult.Success).meal
        assertEquals("Grilled Chicken Breast with Rice & Broccoli", meal.name)
        assertEquals(540, meal.caloriesKcal)
        assertEquals(48.5f, meal.proteinGrams, 0.1f)
        assertEquals(420f, meal.estimatedGrams, 0.1f)
    }
}
