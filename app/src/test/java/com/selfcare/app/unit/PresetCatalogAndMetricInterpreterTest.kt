package com.selfcare.app.unit

import com.selfcare.app.data.preset.PresetGroup
import com.selfcare.app.data.preset.PresetProductCatalog
import com.selfcare.app.domain.cv.FaceMetricCalculator
import com.selfcare.app.domain.cv.MetricInterpreter
import com.selfcare.app.domain.cv.MetricStatusLevel
import com.selfcare.app.domain.cv.PixelRect
import com.selfcare.app.domain.inci.InciConflictDetector
import com.selfcare.app.domain.inci.InciDatabaseLoader
import com.selfcare.app.domain.inci.InciEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PresetCatalogAndMetricInterpreterTest {

    private fun loadInciEngine(): InciEngine {
        val candidates = listOf(
            File("src/main/assets/inci_database.json"),
            File("app/src/main/assets/inci_database.json")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate inci_database.json")
        return InciEngine(InciDatabaseLoader.loadFromJson(file.readText()))
    }

    @Test
    fun presetCatalog_findsPrescriptionCreamsAndHairProductsOffline() {
        val davercin = PresetProductCatalog.search("Davercin")
        assertTrue(davercin.any { it.id == "rx_davercin" && it.group == PresetGroup.RX })

        val skinoren = PresetProductCatalog.search("Skinoren")
        assertTrue(skinoren.size >= 2)
        assertTrue(skinoren.all { it.group == PresetGroup.RX })

        val nizoral = PresetProductCatalog.search("Nizoral")
        assertTrue(nizoral.any { it.group == PresetGroup.HAIR && it.defaultSlot == "HAIR" })
    }

    @Test
    fun inciEngine_matchesDavercinAndDetectsRxConflictWithAcid() {
        val engine = loadInciEngine()
        val davercinMatches = engine.parse("Erythromycin, Alcohol Denat.")
        assertTrue(davercinMatches.any { it.ingredientId == "erythromycin" })

        val acidMatches = engine.parse("Aqua, Glycolic Acid")
        val conflicts = InciConflictDetector(engine.database).detectAcrossProducts(
            listOf(
                InciConflictDetector.ProductInciEntry(1L, "Davercin", "EVENING", davercinMatches),
                InciConflictDetector.ProductInciEntry(2L, "Glycolic Toner", "EVENING", acidMatches)
            )
        )
        assertTrue(conflicts.any { it.ruleId == "rx_antibiotic_strong_acid" })
    }

    @Test
    fun metricInterpreter_mapsRawValuesToHumanQualitativeLevels() {
        assertEquals(MetricStatusLevel.OPTIMAL, MetricInterpreter.interpretUnderEye(5.0f).level)
        assertEquals(MetricStatusLevel.GOOD, MetricInterpreter.interpretUnderEye(11.0f).level)
        assertEquals(MetricStatusLevel.MODERATE, MetricInterpreter.interpretUnderEye(19.0f).level)
        assertEquals(MetricStatusLevel.HIGH, MetricInterpreter.interpretUnderEye(28.0f).level)

        assertEquals(MetricStatusLevel.OPTIMAL, MetricInterpreter.interpretRedness(0.16f).level)
        assertEquals(MetricStatusLevel.HIGH, MetricInterpreter.interpretRedness(0.65f).level)

        assertEquals(MetricStatusLevel.OPTIMAL, MetricInterpreter.interpretLipDryness(0.18f).level)
        assertEquals(MetricStatusLevel.HIGH, MetricInterpreter.interpretLipDryness(0.75f).level)

        val hairSummary = MetricInterpreter.interpretHairDetails(
            crownVolumeScore = 0.84f,
            shineScore = 0.81f,
            foreheadCoverage = 0.18f,
            templeSymmetryScore = 0.92f
        )
        assertEquals(MetricStatusLevel.OPTIMAL, hairSummary.crownVolumeLevel)
        assertEquals(84, hairSummary.crownVolumePercent)
        assertEquals(81, hairSummary.shinePercent)
        assertTrue(hairSummary.hairlineOpenForehead)
        assertEquals(92, hairSummary.templeSymmetryPercent)

        val fringeSummary = MetricInterpreter.interpretHairDetails(0.7f, 0.6f, 0.55f, 0.85f)
        assertFalse(fringeSummary.hairlineOpenForehead)
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun detailedHairMetrics_computesCrownVolumeAndShineFromMask() {
        val w = 100
        val h = 100
        val pixels = IntArray(w * h) { argb(210, 170, 150) }
        val mask = FloatArray(w * h) { 0f }

        // Paint dark glossy hair in crown zone above faceBox (top=35..80, left=30..70)
        for (y in 5 until 35) {
            for (x in 22 until 78) {
                mask[y * w + x] = 1.0f
                val shineBoost = if (y in 16..20) 60 else 0
                pixels[y * w + x] = argb(35 + shineBoost, 28 + shineBoost, 24 + shineBoost)
            }
        }
        val details = FaceMetricCalculator.computeDetailedHairMetrics(
            pixels = pixels,
            foregroundMask = mask,
            maskWidth = w,
            maskHeight = h,
            imageWidth = w,
            imageHeight = h,
            faceBox = PixelRect(30, 35, 70, 80)
        )
        assertTrue(details.crownVolumeScore > 0.7f)
        assertTrue(details.shineSmoothnessScore > 0.4f)
        assertTrue(details.templeSymmetryScore > 0.85f)
    }
}
