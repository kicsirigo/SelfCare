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

    @Test
    fun `hungarian food catalog supports accent-free, spaced, and English queries for csirkeporkolt`() {
        val bySpaced = FoodPresetCatalog.search("Csirke pörkölt")
        val byNoAccent = FoodPresetCatalog.search("csirkeporkolt")
        val byEnglish = FoodPresetCatalog.search("chicken paprikash")
        assertTrue(bySpaced.any { it.id == "csirkeporkolt" })
        assertTrue(byNoAccent.any { it.id == "csirkeporkolt" })
        assertTrue(byEnglish.any { it.id == "csirkeporkolt" })
        assertTrue(FoodPresetCatalog.search("nokedli").any { it.id == "nokedli" })
        assertTrue(FoodPresetCatalog.search("lecso").any { it.id == "lecso" })
    }

    @Test
    fun `salt hydration bonus adds extra recommended water above 5g WHO reference`() {
        assertEquals(0, MacroCalculator.computeSaltHydrationBonusMl(3.5f))
        assertEquals(0, MacroCalculator.computeSaltHydrationBonusMl(5.0f))
        assertEquals(300, MacroCalculator.computeSaltHydrationBonusMl(7.0f))
        assertEquals(1000, MacroCalculator.computeSaltHydrationBonusMl(20.0f))
    }

    @Test
    fun `groq estimateMealFromText returns calories macros and saltGrams`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"data":[{"id":"qwen/qwen3.8-27b"}]}"""
                )
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "choices": [{
                        "message": {
                          "content": "{\"name\":\"Csirkepörkölt nokedlivel\",\"estimatedGrams\":450,\"caloriesKcal\":645,\"proteinGrams\":51.0,\"carbsGrams\":67.0,\"fatGrams\":19.0,\"fiberGrams\":3.4,\"sugarGrams\":4.2,\"saltGrams\":4.9}"
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
            val result = client.estimateMealFromText("csirkepörkölt nokedlivel", "gsk_test_key")
            assertTrue(result is MealVisionResult.Success)
            val meal = (result as MealVisionResult.Success).meal
            assertEquals("Csirkepörkölt nokedlivel", meal.name)
            assertEquals(645, meal.caloriesKcal)
            assertEquals(4.9f, meal.saltGrams, 0.1f)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `activity calorie calculator computes MET-based burned kcal`() {
        // 80 kg running (MET 9.8) for 30 min -> 9.8 * 80 * 0.5 = 392 kcal
        val kcal = com.selfcare.app.domain.health.ActivityCalorieCalculator.estimateBurnedKcal(
            metValue = 9.8f,
            weightKg = 80f,
            durationMinutes = 30
        )
        assertEquals(392, kcal)
        assertTrue(com.selfcare.app.domain.health.ActivityCalorieCalculator.presets.size >= 8)
    }

    @Test
    fun `groq nutrition label parser extracts per-100g values and strips think tags`() {
        val client = GroqVisionProductClient()
        val raw = """
            {
              "choices": [{
                "message": {
                  "content": "<think>Reading Hungarian Tápérték táblázat per 100g.</think>\n```json\n{\"name\":\"Mizo Protein Shake\",\"brand\":\"Mizo\",\"per100gKcal\":64,\"per100gProtein\":6.5,\"per100gCarbs\":5.2,\"per100gFat\":1.5,\"per100gFiber\":0.3,\"per100gSugar\":4.8,\"per100gSalt\":0.14,\"defaultGrams\":330}\n```"
                }
              }]
            }
        """.trimIndent()
        val parsed = client.parseGroqNutritionLabelJson(raw)
        assertTrue(parsed is com.selfcare.app.data.remote.NutritionLabelVisionResult.Success)
        val item = (parsed as com.selfcare.app.data.remote.NutritionLabelVisionResult.Success).item
        assertEquals("Mizo Protein Shake", item.name)
        assertEquals(64f, item.per100gKcal, 0.1f)
        assertEquals(6.5f, item.per100gProtein, 0.1f)
        assertEquals(0.14f, item.per100gSalt, 0.01f)
        assertEquals(330f, item.defaultGrams, 0.1f)
    }

    @Test
    fun `meal timing analyzer maps hours to slots and detects eating window and late meals`() {
        assertEquals(
            com.selfcare.app.data.local.entity.NutritionLogEntity.MEAL_BREAKFAST,
            com.selfcare.app.domain.health.MealTimingAnalyzer.defaultMealTypeForHour(8, 15)
        )
        assertEquals(
            com.selfcare.app.data.local.entity.NutritionLogEntity.MEAL_LUNCH,
            com.selfcare.app.domain.health.MealTimingAnalyzer.defaultMealTypeForHour(12, 30)
        )
        assertEquals(
            com.selfcare.app.data.local.entity.NutritionLogEntity.MEAL_SNACK,
            com.selfcare.app.domain.health.MealTimingAnalyzer.defaultMealTypeForHour(16, 0)
        )
        assertEquals(
            com.selfcare.app.data.local.entity.NutritionLogEntity.MEAL_DINNER,
            com.selfcare.app.domain.health.MealTimingAnalyzer.defaultMealTypeForHour(20, 0)
        )

        val zone = java.time.ZoneOffset.UTC
        val day = 20000L
        val t1 = com.selfcare.app.domain.health.MealTimingAnalyzer.combineEpochDayAndHm(day, 8, 0, zone)
        val t2 = com.selfcare.app.domain.health.MealTimingAnalyzer.combineEpochDayAndHm(day, 21, 30, zone)
        val meals = listOf(
            com.selfcare.app.data.local.entity.NutritionLogEntity(
                dateEpochDay = day,
                mealType = com.selfcare.app.data.local.entity.NutritionLogEntity.MEAL_BREAKFAST,
                name = "Zabkása",
                grams = 250f,
                caloriesKcal = 350,
                proteinGrams = 15f,
                carbsGrams = 50f,
                fatGrams = 8f,
                createdAtEpochMillis = t1
            ),
            com.selfcare.app.data.local.entity.NutritionLogEntity(
                dateEpochDay = day,
                mealType = com.selfcare.app.data.local.entity.NutritionLogEntity.MEAL_DINNER,
                name = "Szendvics",
                grams = 200f,
                caloriesKcal = 420,
                proteinGrams = 20f,
                carbsGrams = 40f,
                fatGrams = 15f,
                createdAtEpochMillis = t2
            )
        )
        val summary = com.selfcare.app.domain.health.MealTimingAnalyzer.analyzeDay(meals, zone)
        assertEquals("08:00", summary.firstMealTimeHm)
        assertEquals("21:30", summary.lastMealTimeHm)
        assertEquals(13 * 60 + 30, summary.eatingWindowMinutes)
        assertTrue(summary.hasLateNightMeal)
        assertEquals(1, summary.lateNightMealCount)
    }

    @Test
    fun `app data context builder respects scope toggles and never leaks exact birthdate`() {
        val settings = com.selfcare.app.data.local.UserSettings(
            birthDateIso = "1998-05-14",
            bodyWeightKg = 78.5f,
            targetWeightKg = 74.0f
        )
        val ctx = com.selfcare.app.domain.ai.AppDataContextBuilder.buildContext(
            scope = com.selfcare.app.domain.ai.AiChatContextScope(
                includeNutrition = true,
                includeFitnessWeight = false,
                includeRoutineShelf = false,
                includeFaceTrends = false
            ),
            settings = settings,
            todayEpochDay = 20000L,
            todayMeals = emptyList(),
            recentMeals7d = emptyList(),
            todayWater = emptyList(),
            todayActivities = emptyList(),
            weights = emptyList(),
            shelfProducts = emptyList(),
            recentCheckIns = emptyList(),
            zoneId = java.time.ZoneOffset.UTC,
            nowTime = java.time.LocalTime.of(9, 24)
        )
        assertTrue(ctx.contains("CURRENT LOCAL TIME: 09:24 (morning)"))
        assertTrue(ctx.contains("TODAY'S NUTRITION"))
        assertTrue(!ctx.contains("EXERCISE & WEIGHT"))
        assertTrue(!ctx.contains("FACE CHECK-IN TRENDS"))
        assertTrue(!ctx.contains("1998-05-14"))
    }

    @Test
    fun `groq coach reply parser strips think tags cleanly`() {
        val client = GroqVisionProductClient()
        val raw = """
            {
              "choices": [{
                "message": {
                  "content": "<think>Analyzing user's 08:00 breakfast and 21:30 dinner.</think>\nA mai fehérjebeviteled szuper, de próbáld a vacsorát 20:30 elé hozni."
                }
              }]
            }
        """.trimIndent()
        val parsed = client.parseGroqCoachReplyJson(raw)
        assertTrue(parsed is com.selfcare.app.data.remote.AiCoachChatResult.Success)
        val reply = (parsed as com.selfcare.app.data.remote.AiCoachChatResult.Success).reply
        assertEquals("A mai fehérjebeviteled szuper, de próbáld a vacsorát 20:30 elé hozni.", reply)
    }

    @Test
    fun `groq meal parser extracts detectedIngredients and clarificationQuestion for sandwiches`() {
        val client = GroqVisionProductClient()
        val raw = """
            {
              "choices": [{
                "message": {
                  "content": "{\"name\":\"Sonkás-sajtos szendvics\",\"estimatedGrams\":180,\"caloriesKcal\":395,\"proteinGrams\":19.0,\"carbsGrams\":42.0,\"fatGrams\":16.0,\"fiberGrams\":2.5,\"sugarGrams\":3.1,\"saltGrams\":2.2,\"detectedIngredients\":\"1 császárzsemle, vaj, 2 szelet sonka, 1 szelet sajt\",\"clarificationQuestion\":\"Van benne majonéz vagy extra sajt, és milyen kenyérből készült?\"}"
                }
              }]
            }
        """.trimIndent()
        val parsed = client.parseGroqMealResponseJson(raw)
        assertTrue(parsed is MealVisionResult.Success)
        val meal = (parsed as MealVisionResult.Success).meal
        assertTrue(meal.detectedIngredients.contains("sonka"))
        assertTrue(meal.clarificationQuestion.contains("majonéz"))
    }

    @Test
    fun `calorie chart bar builder aggregates 14d daily and 365d weekly buckets accurately`() {
        val today = 20050L
        val meals = listOf(
            com.selfcare.app.data.local.entity.NutritionLogEntity(
                dateEpochDay = today,
                mealType = "LUNCH",
                name = "A",
                grams = 300f,
                caloriesKcal = 1800,
                proteinGrams = 110f,
                carbsGrams = 180f,
                fatGrams = 60f,
                createdAtEpochMillis = 1_000L
            ),
            com.selfcare.app.data.local.entity.NutritionLogEntity(
                dateEpochDay = today - 1L,
                mealType = "LUNCH",
                name = "B",
                grams = 300f,
                caloriesKcal = 2200,
                proteinGrams = 130f,
                carbsGrams = 210f,
                fatGrams = 70f,
                createdAtEpochMillis = 1_000L
            )
        )
        val bars14 = com.selfcare.app.ui.trends.TrendsViewModel.buildCalorieChartBars(meals, today, 14)
        assertEquals(14, bars14.size)
        assertEquals(1800, bars14.last().caloriesKcal)
        assertEquals(2200, bars14[bars14.size - 2].caloriesKcal)

        val bars90 = com.selfcare.app.ui.trends.TrendsViewModel.buildCalorieChartBars(meals, today, 90)
        assertEquals(30, bars90.size)

        val bars365 = com.selfcare.app.ui.trends.TrendsViewModel.buildCalorieChartBars(meals, today, 365)
        assertTrue(bars365.size in 52..53)
        // Latest weekly bucket averages the 2 logged days: (1800 + 2200) / 2 = 2000
        assertEquals(2000, bars365.last().caloriesKcal)
    }

    @Test
    fun `streak calculator counts consecutive days ending today or yesterday`() {
        val today = 20100L
        assertEquals(
            3,
            com.selfcare.app.domain.health.StreakCalculator.computeCurrentStreak(
                setOf(today, today - 1L, today - 2L, today - 5L),
                today
            )
        )
        assertEquals(
            2,
            com.selfcare.app.domain.health.StreakCalculator.computeCurrentStreak(
                setOf(today - 1L, today - 2L),
                today
            )
        )
        assertEquals(
            0,
            com.selfcare.app.domain.health.StreakCalculator.computeCurrentStreak(
                setOf(today - 2L, today - 3L),
                today
            )
        )
    }
}
