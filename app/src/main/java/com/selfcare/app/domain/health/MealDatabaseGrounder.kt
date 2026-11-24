package com.selfcare.app.domain.health

import com.selfcare.app.data.remote.EstimatedMealInfo
import com.selfcare.app.data.remote.FoodNutritionItem
import com.selfcare.app.data.remote.FoodSearchResult
import com.selfcare.app.data.repository.NutritionRepository
import java.util.Locale
import kotlin.math.roundToInt

data class GroundedSourceRef(
    val title: String,
    val sourceLabel: String,
    val url: String? = null
)

data class GroundedDatabaseResult(
    val factsPromptText: String = "",
    val sources: List<GroundedSourceRef> = emptyList()
)

/**
 * Food RAG Grounder: takes an AI-identified meal + guessed ingredients, looks up verified
 * per-100g nutritional facts from the 3-layer database (Offline HU Catalog + OpenFoodFacts + USDA),
 * and returns both a compact prompt reference block and clickable source citations.
 */
object MealDatabaseGrounder {

    suspend fun collectVerifiedFacts(
        meal: EstimatedMealInfo,
        nutritionRepository: NutritionRepository,
        allowRemoteLookup: Boolean = true
    ): GroundedDatabaseResult {
        val terms = extractLookupTerms(meal)
        if (terms.isEmpty()) return GroundedDatabaseResult()

        val lines = LinkedHashSet<String>()
        val sources = mutableListOf<GroundedSourceRef>()
        val seenKeys = HashSet<String>()

        // 1. Instant 0ms offline lookup (250+ Hungarian, bakery, sandwich, fast-food & raw items)
        for (term in terms) {
            val match = FoodPresetCatalog.search(term).firstOrNull() ?: continue
            if (seenKeys.add("offline:${match.id}")) {
                lines.add(formatPresetLine(match))
                sources.add(
                    GroundedSourceRef(
                        title = match.nameHu,
                        sourceLabel = "Offline HU DB",
                        url = null
                    )
                )
            }
            if (lines.size >= 6) break
        }

        // 2. Also query OpenFoodFacts (3M+) + USDA (400k+) for verifiable web source links
        if (allowRemoteLookup && lines.size < 6) {
            val remoteBudget = if (lines.size < 3) 2 else 1
            for (term in terms.take(remoteBudget)) {
                val res = nutritionRepository.searchFoodRemote(term)
                if (res is FoodSearchResult.Success) {
                    res.items.take(2).forEach { item ->
                        val key = "remote:${item.name.lowercase()}|${item.sourceUrl.orEmpty()}"
                        if (seenKeys.add(key) && lines.size < 6) {
                            lines.add(formatRemoteLine(item))
                            sources.add(
                                GroundedSourceRef(
                                    title = item.name,
                                    sourceLabel = item.sourceLabel.ifBlank { "DB" },
                                    url = item.sourceUrl
                                )
                            )
                        }
                    }
                }
                if (lines.size >= 6) break
            }
        }

        return GroundedDatabaseResult(
            factsPromptText = lines.take(6).joinToString("\n"),
            sources = sources.take(6)
        )
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
        return "- ${item.name}$brandSuffix [${item.sourceLabel}, per 100g]: ${item.per100gKcal.roundToInt()} kcal, " +
            "P:${String.format(Locale.US, "%.1f", item.per100gProtein)}g, " +
            "CH:${String.format(Locale.US, "%.1f", item.per100gCarbs)}g, " +
            "F:${String.format(Locale.US, "%.1f", item.per100gFat)}g, " +
            "Salt:${String.format(Locale.US, "%.1f", item.per100gSalt)}g"
    }
}
