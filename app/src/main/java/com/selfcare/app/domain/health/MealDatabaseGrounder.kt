package com.selfcare.app.domain.health

import com.selfcare.app.data.remote.EstimatedMealInfo
import com.selfcare.app.data.remote.FoodNutritionItem
import com.selfcare.app.data.remote.FoodSearchResult
import com.selfcare.app.data.repository.NutritionRepository
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Food RAG Grounder: takes an AI-identified meal + guessed ingredients, looks up verified
 * per-100g nutritional facts from the 3-layer database (Offline HU Catalog + OpenFoodFacts + USDA),
 * and formats a compact factual reference block to feed back into Qwen for grounded calculation.
 */
object MealDatabaseGrounder {

    suspend fun collectVerifiedFacts(
        meal: EstimatedMealInfo,
        nutritionRepository: NutritionRepository,
        allowRemoteLookup: Boolean = true
    ): String {
        val terms = extractLookupTerms(meal)
        if (terms.isEmpty()) return ""

        val lines = LinkedHashSet<String>()

        // 1. Instant 0ms offline lookup (250+ Hungarian, bakery, sandwich, fast-food & raw items)
        for (term in terms) {
            val match = FoodPresetCatalog.search(term).firstOrNull() ?: continue
            lines.add(formatPresetLine(match))
            if (lines.size >= 6) break
        }

        // 2. If fewer than 3 offline hits, query OpenFoodFacts (3M+) + USDA (400k+) for top terms
        if (allowRemoteLookup && lines.size < 3) {
            for (term in terms.take(2)) {
                val res = nutritionRepository.searchFoodRemote(term)
                if (res is FoodSearchResult.Success) {
                    res.items.take(2).forEach { item ->
                        lines.add(formatRemoteLine(item))
                    }
                }
                if (lines.size >= 6) break
            }
        }

        return lines.take(6).joinToString("\n")
    }

    internal fun extractLookupTerms(meal: EstimatedMealInfo): List<String> {
        val raw = mutableListOf<String>()
        if (meal.detectedIngredients.isNotBlank()) {
            meal.detectedIngredients
                .split(",", ";", "+")
                .map { cleanIngredientPhrase(it) }
                .filter { it.length >= 3 }
                .forEach { raw.add(it) }
        }
        if (meal.name.isNotBlank()) {
            raw.add(cleanIngredientPhrase(meal.name))
        }
        return raw.distinctBy { FoodPresetCatalog.normalizeForSearch(it) }.take(6)
    }

    private fun cleanIngredientPhrase(phrase: String): String {
        // Strip leading quantities like "2 slices", "1 db", "150g", "kis"
        return phrase
            .trim()
            .replace(Regex("""^\d+([.,]\d+)?\s*(g|gr|gramm|ml|dl|db|szelet|slices?|ek|tk|tbsp|tsp|cup|cups|pcs?)?\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(kis|nagy|egy|pár|kevés|friss|sült|főtt)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun formatPresetLine(p: FoodPreset): String =
        "- ${p.nameHu} (${p.nameEn}) [Offline DB, per 100g]: ${p.per100gKcal.roundToInt()} kcal, " +
            "P:${String.format(Locale.US, "%.1f", p.per100gProtein)}g, " +
            "CH:${String.format(Locale.US, "%.1f", p.per100gCarbs)}g, " +
            "F:${String.format(Locale.US, "%.1f", p.per100gFat)}g, " +
            "Salt:${String.format(Locale.US, "%.1f", p.per100gSalt)}g"

    private fun formatRemoteLine(item: FoodNutritionItem): String {
        val brandSuffix = if (item.brand.isNotBlank()) " (${item.brand})" else ""
        return "- ${item.name}$brandSuffix [DB, per 100g]: ${item.per100gKcal.roundToInt()} kcal, " +
            "P:${String.format(Locale.US, "%.1f", item.per100gProtein)}g, " +
            "CH:${String.format(Locale.US, "%.1f", item.per100gCarbs)}g, " +
            "F:${String.format(Locale.US, "%.1f", item.per100gFat)}g, " +
            "Salt:${String.format(Locale.US, "%.1f", item.per100gSalt)}g"
    }
}
