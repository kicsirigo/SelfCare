package com.selfcare.app.unit

import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.domain.inci.InciConflictDetector
import com.selfcare.app.domain.inci.InciDatabaseLoader
import com.selfcare.app.domain.inci.InciEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class InciEngineTest {

    private lateinit var engine: InciEngine

    @Before
    fun setUp() {
        val candidates = listOf(
            File("src/main/assets/inci_database.json"),
            File("app/src/main/assets/inci_database.json")
        )
        val assetFile = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate inci_database.json from ${File(".").absolutePath}")
        val database = InciDatabaseLoader.loadFromJson(assetFile.readText())
        engine = InciEngine(database)
    }

    @Test
    fun parse_cleansNoisyOcrText_andMatchesCanonicalAndSynonyms() {
        val noisyOcr = """
            Ingredients:
            • Aqua (Water) 70%
            • N1acinamide (Vitamin B3) 5%
            • Retin0l (0.5%)
            • Sa1icylic Acid [2%]
            • L-Ascorbic Acid
        """.trimIndent()

        val matches = engine.parse(noisyOcr)
        val ids = matches.map { it.ingredientId }

        assertTrue("Expected aqua in $ids", "aqua" in ids)
        assertTrue("Expected niacinamide in $ids", "niacinamide" in ids)
        assertTrue("Expected retinol in $ids", "retinol" in ids)
        assertTrue("Expected salicylic_acid in $ids", "salicylic_acid" in ids)
        assertTrue("Expected ascorbic_acid in $ids", "ascorbic_acid" in ids)
        assertEquals(5, ids.size)
    }

    @Test
    fun detectWithinProduct_flagsRetinolPlusAhaConflict() {
        val matches = engine.parse("Aqua, Retinol, Glycolic Acid, Glycerin")
        val conflicts = engine.detectWithinProduct("Night Peel", matches, InciConflictDetector.SLOT_EVENING)

        assertTrue(conflicts.any { it.ruleId == "retinoid_aha_bha" })
    }

    @Test
    fun detectRoutineConflicts_flagsSharedSlot_andIgnoresSeparatedSlots() {
        val retinolSerum = product(
            id = 1L,
            name = "Retinol Serum",
            slot = InciConflictDetector.SLOT_EVENING,
            ingredients = "Aqua, Retinol"
        )
        val bhaToner = product(
            id = 2L,
            name = "BHA Toner",
            slot = InciConflictDetector.SLOT_BOTH,
            ingredients = "Aqua, Salicylic Acid"
        )
        val vitaminCSerum = product(
            id = 3L,
            name = "Vitamin C Serum",
            slot = InciConflictDetector.SLOT_MORNING,
            ingredients = "Aqua, Ascorbic Acid"
        )
        val benzoylWash = product(
            id = 4L,
            name = "BP Wash",
            slot = InciConflictDetector.SLOT_EVENING,
            ingredients = "Aqua, Benzoyl Peroxide"
        )

        val sharedConflicts = engine.detectRoutineConflicts(listOf(retinolSerum, bhaToner))
        assertTrue(
            "Expected retinoid_aha_bha across EVENING + BOTH",
            sharedConflicts.any { it.ruleId == "retinoid_aha_bha" && it.slot == InciConflictDetector.SLOT_EVENING }
        )

        // Vitamin C (MORNING) + Benzoyl Peroxide (EVENING) should NOT conflict across separated slots
        val separatedConflicts = engine.detectRoutineConflicts(listOf(vitaminCSerum, benzoylWash))
        assertTrue(
            "Expected no conflict when Vitamin C is MORNING and BP is EVENING",
            separatedConflicts.isEmpty()
        )
    }

    private fun product(
        id: Long,
        name: String,
        slot: String,
        ingredients: String
    ): ProductEntity {
        val matches = engine.parse(ingredients)
        return ProductEntity(
            id = id,
            name = name,
            routineSlot = slot,
            rawIngredientsText = ingredients,
            matchedInciJson = engine.encodeMatches(matches),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L
        )
    }
}
