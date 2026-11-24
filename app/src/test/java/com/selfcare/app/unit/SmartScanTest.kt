package com.selfcare.app.unit

import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.remote.GroqVisionProductClient
import com.selfcare.app.ui.routine.RoutineViewModel
import com.selfcare.app.ui.shelf.SmartScanQueryExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SmartScanTest {

    @Test
    fun extractProductSearchQuery_filtersDosageVolumeAndBoilerplate() {
        val raw = """
            30 g
            DAVERCIN
            25 mg/g gél
            Ingredients: Erythromycin
            www.example.com
        """.trimIndent()

        val query = SmartScanQueryExtractor.extractProductSearchQuery(raw)
        assertEquals("DAVERCIN", query)
    }

    @Test
    fun extractProductSearchQuery_combinesBrandAndProductName() {
        val raw = """
            CeraVe
            Foaming Cleanser
            236 ml
            For Normal to Oily Skin
        """.trimIndent()

        val query = SmartScanQueryExtractor.extractProductSearchQuery(raw)
        assertEquals("CeraVe Foaming Cleanser", query)
    }

    @Test
    fun findPresetMatchesFromOcr_matchesRxCreamsOffline() {
        val davercinOcr = "DAVERCIN\n25 mg/g gél\nEritromicin-ciklokarbonát\n30 g"
        val query = SmartScanQueryExtractor.extractProductSearchQuery(davercinOcr)
        val hits = SmartScanQueryExtractor.findPresetMatchesFromOcr(davercinOcr, query)
        assertTrue("Expected offline Davercin preset match", hits.any { it.id == "rx_davercin" })

        val skinorenOcr = "LEO Pharma\nSkinoren 200 mg/g krém\nAzelainsav\n30 g"
        val skinorenQuery = SmartScanQueryExtractor.extractProductSearchQuery(skinorenOcr)
        val skinorenHits = SmartScanQueryExtractor.findPresetMatchesFromOcr(skinorenOcr, skinorenQuery)
        assertTrue("Expected offline Skinoren preset match", skinorenHits.any { it.id.startsWith("rx_skinoren") })
    }

    @Test
    fun looksLikeIngredientList_distinguishesInciBackLabelFromFrontBox() {
        val backLabel = "Ingredients: Aqua, Niacinamide, Salicylic Acid, Glycerin, Panthenol"
        assertTrue(SmartScanQueryExtractor.looksLikeIngredientList(backLabel, inciMatchCount = 4))

        val frontBox = "La Roche-Posay\nEffaclar Duo+M\n40 ml"
        assertFalse(SmartScanQueryExtractor.looksLikeIngredientList(frontBox, inciMatchCount = 0))
    }

    @Test
    fun aiCoachLanguageDetector_andRoutineStepOrder_workCorrectly() {
        assertFalse(GroqVisionProductClient.isLikelyHungarian("Hello, what should I eat for dinner?"))
        assertFalse(GroqVisionProductClient.isLikelyHungarian("How is my protein intake today?"))
        assertTrue(GroqVisionProductClient.isLikelyHungarian("Szia, mit egyek vacsorára?"))
        assertTrue(GroqVisionProductClient.isLikelyHungarian("Mennyi vizet igyak ma?"))

        val cleanser = ProductEntity(name = "Foaming Cleanser", brand = "CeraVe", category = "OTHER", createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L)
        val retinol = ProductEntity(name = "Retinol Serum", brand = "Ordinary", category = "RX_TREATMENT", createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L)
        val lipBalm = ProductEntity(name = "CeraVe ajakápoló", brand = "CeraVe", category = "OTHER", createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L)
        val vitamin = ProductEntity(name = "D3-vitamin 2000 NE", brand = "BioCo", category = "VITAMIN", createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L)
        val ordered = listOf(vitamin, lipBalm, retinol, cleanser)
            .sortedBy { RoutineViewModel.applicationStepPriority(it) }
            .map { it.name }
        assertTrue(ordered == listOf("Foaming Cleanser", "Retinol Serum", "CeraVe ajakápoló", "D3-vitamin 2000 NE"))
    }
}
