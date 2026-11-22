package com.selfcare.app.unit

import com.selfcare.app.data.remote.GeminiVisionProductClient
import com.selfcare.app.data.remote.GeminiVisionResult
import com.selfcare.app.domain.cv.ImprovementTipCode
import com.selfcare.app.domain.cv.MetricInterpreter
import com.selfcare.app.domain.cv.ShelfProductSummary
import com.selfcare.app.domain.cv.TipVariant
import com.selfcare.app.domain.health.AgeGroup
import com.selfcare.app.domain.health.NutrientCode
import com.selfcare.app.domain.health.VitaminCalculator
import com.selfcare.app.ui.shelf.SmartScanQueryExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VitaminCalculatorAndProductAdviceTest {

    @Test
    fun `computeAgeYears calculates exact age and rejects invalid or future dates`() {
        val today = LocalDate.of(2026, 11, 22)
        assertEquals(26, VitaminCalculator.computeAgeYears("2000-05-10", today))
        assertEquals(25, VitaminCalculator.computeAgeYears("2000-12-01", today))
        assertNull(VitaminCalculator.computeAgeYears("2030-01-01", today))
        assertNull(VitaminCalculator.computeAgeYears("not-a-date", today))
        assertNull(VitaminCalculator.computeAgeYears(null, today))
    }

    @Test
    fun `calculateForAge scales vitamin D and calcium across age brackets`() {
        val teenPlan = VitaminCalculator.calculateForAge(16)
        assertEquals(AgeGroup.TEEN_14_18, teenPlan.ageGroup)
        assertEquals("1300 mg", teenPlan.requirements.first { it.code == NutrientCode.CALCIUM }.dailyAmount)
        assertEquals("15 µg (600 IU)", teenPlan.requirements.first { it.code == NutrientCode.VITAMIN_D }.dailyAmount)

        val seniorPlan = VitaminCalculator.calculateForAge(74)
        assertEquals(AgeGroup.SENIOR_71_PLUS, seniorPlan.ageGroup)
        assertEquals("20 µg (800 IU)", seniorPlan.requirements.first { it.code == NutrientCode.VITAMIN_D }.dailyAmount)
        assertEquals("1200 mg", seniorPlan.requirements.first { it.code == NutrientCode.CALCIUM }.dailyAmount)
    }

    @Test
    fun `calculateForAge matches shelf vitamins and ignores topical face creams`() {
        val shelf = listOf(
            ShelfProductSummary("Vitamin D3 2000 IU", "VITAMIN", "Cholecalciferol 50 µg"),
            ShelfProductSummary("Magnesium Bisglycinate", "VITAMIN", "Magnesium, Vitamin B6"),
            ShelfProductSummary("CeraVe Moisturizing Cream", "MOISTURIZER", "Ceramide NP, Glycerin")
        )
        val plan = VitaminCalculator.calculateForAge(25, shelf)
        val vitD = plan.requirements.first { it.code == NutrientCode.VITAMIN_D }
        val mag = plan.requirements.first { it.code == NutrientCode.MAGNESIUM }
        val vitC = plan.requirements.first { it.code == NutrientCode.VITAMIN_C }

        assertTrue(vitD.isCoveredOnShelf)
        assertEquals(listOf("Vitamin D3 2000 IU"), vitD.coveredByProducts)
        assertTrue(mag.isCoveredOnShelf)
        assertFalse(vitC.isCoveredOnShelf)
        assertEquals(2, plan.coveredCount)
    }

    @Test
    fun `computeObjectiveRating personalizes tips with shelf creams and warns on missing SPF`() {
        val shelfWithoutSpf = listOf(
            ShelfProductSummary("Epiduo 0.1% / 2.5% Gel", "RX_TREATMENT", "Adapalene, Benzoyl Peroxide"),
            ShelfProductSummary("Cicaplast Baume B5+", "MOISTURIZER", "Panthenol, Madecassoside")
        )

        val result = MetricInterpreter.computeObjectiveRating(
            underEyeDelta = 6f,
            redness = 0.48f, // high redness -> triggers active irritation caution
            texture = 10f,
            lipDryness = 0.18f,
            blemishScore = 0.55f,
            blemishCount = 7,
            stubbleScore = 0.15f,
            shaveDue = false,
            shelfProducts = shelfWithoutSpf
        )

        assertEquals(2, result.usedShelfProductCount)
        val blemishTip = result.contextualTips.first { it.code == ImprovementTipCode.BLEMISHES }
        assertEquals(TipVariant.HAS_TARGETED_PRODUCT, blemishTip.variant)
        assertEquals("Epiduo 0.1% / 2.5% Gel", blemishTip.matchedProductName)

        val rednessTip = result.contextualTips.first { it.code == ImprovementTipCode.REDNESS }
        assertEquals(TipVariant.ACTIVE_IRRITATION_CAUTION, rednessTip.variant)

        assertTrue(result.contextualTips.any { it.code == ImprovementTipCode.SPF_WITH_ACTIVES })

        // Adding sunscreen removes the SPF_WITH_ACTIVES warning
        val shelfWithSpf = shelfWithoutSpf + ShelfProductSummary("Anthelios UV Fluid SPF 50+", "OTHER", "SPF 50")
        val resultWithSpf = MetricInterpreter.computeObjectiveRating(
            underEyeDelta = 6f,
            redness = 0.48f,
            texture = 10f,
            lipDryness = 0.18f,
            blemishScore = 0.55f,
            blemishCount = 7,
            stubbleScore = 0.15f,
            shaveDue = false,
            shelfProducts = shelfWithSpf
        )
        assertFalse(resultWithSpf.contextualTips.any { it.code == ImprovementTipCode.SPF_WITH_ACTIVES })
    }

    @Test
    fun `inferCategoryFromText classifies vitamins medications hair and skincare`() {
        assertEquals("VITAMIN", SmartScanQueryExtractor.inferCategoryFromText("BioCo D3-vitamin 2000 NE étrend-kiegészítő"))
        assertEquals("MEDICATION", SmartScanQueryExtractor.inferCategoryFromText("Algoflex Forte 600 mg filmtabletta Ibuprofen"))
        assertEquals("SHAMPOO", SmartScanQueryExtractor.inferCategoryFromText("Nizoral 2% korpásodás elleni sampon"))
        assertEquals("OTHER", SmartScanQueryExtractor.inferCategoryFromText("CeraVe Foaming Facial Cleanser"))
    }

    @Test
    fun `gemini vision parser accepts VITAMIN and MEDICATION categories`() {
        val client = GeminiVisionProductClient()
        val vitaminResp = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "{\"name\":\"Vitamin D3 2000 IU\",\"brand\":\"GAL\",\"category\":\"VITAMIN\",\"routineSlot\":\"MORNING\",\"ingredients\":\"Vitamin D3 (Cholecalciferol 50 mcg)\"}"
                  }]
                }
              }]
            }
        """.trimIndent()
        val parsedVit = client.parseGeminiResponseJson(vitaminResp)
        assertTrue(parsedVit is GeminiVisionResult.Success)
        assertEquals("VITAMIN", (parsedVit as GeminiVisionResult.Success).product.category)

        val medResp = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "{\"name\":\"Sudocrem\",\"brand\":\"Teva\",\"category\":\"OINTMENT\",\"routineSlot\":\"EVENING\",\"ingredients\":\"Zinc Oxide, Panthenol\"}"
                  }]
                }
              }]
            }
        """.trimIndent()
        val parsedMed = client.parseGeminiResponseJson(medResp)
        assertTrue(parsedMed is GeminiVisionResult.Success)
        assertEquals("MEDICATION", (parsedMed as GeminiVisionResult.Success).product.category)
    }
}
