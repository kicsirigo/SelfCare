package com.selfcare.app.domain.inci

import com.selfcare.app.data.local.entity.ProductEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InciEngine(val database: InciDatabase) {

    private val parser = InciParser(database)
    private val detector = InciConflictDetector(database)
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawIngredients: String): List<InciMatch> = parser.parse(rawIngredients)

    fun detectWithinProduct(
        productName: String,
        matches: List<InciMatch>,
        slot: String = InciConflictDetector.SLOT_NONE
    ): List<DetectedConflict> = detector.detectWithinProduct(productName, matches, slot)

    fun detectRoutineConflicts(products: List<ProductEntity>): List<DetectedConflict> {
        val entries = products.map { product ->
            val matches = decodeMatches(product.matchedInciJson).ifEmpty {
                parse(product.rawIngredientsText)
            }
            InciConflictDetector.ProductInciEntry(
                id = product.id,
                name = product.name,
                routineSlot = product.routineSlot,
                matches = matches
            )
        }
        return detector.detectAcrossProducts(entries)
    }

    fun encodeMatches(matches: List<InciMatch>): String = json.encodeToString(matches)

    fun decodeMatches(rawJson: String): List<InciMatch> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<InciMatch>>(rawJson)
        }.getOrDefault(emptyList())
    }
}
