package com.selfcare.app.domain.health

import com.selfcare.app.data.remote.EstimatedMealInfo
import com.selfcare.app.data.remote.GroundedSourceRef
import java.util.Locale
import kotlin.math.roundToInt

data class GroundedDatabaseResult(
    val factsPromptText: String = "",
    val sources: List<GroundedSourceRef> = emptyList()
)

/**
 * Secondary offline fallback grounder: looks up per-100g facts from the bundled
 * 250+ Hungarian & international FoodPresetCatalog (0ms, 0 network calls, 0 tokens).
 */
object MealDatabaseGrounder {

    fun collectOfflineFacts(meal: EstimatedMealInfo): GroundedDatabaseResult {
        val terms = extractLookupTerms(meal)
        if (terms.isEmpty()) return GroundedDatabaseResult()

        val lines = LinkedHashSet<String>()
        val sources = mutableListOf<GroundedSourceRef>()
        val seenKeys = HashSet<String>()

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
        "- ${p.nameHu} (${p.nameEn}) [Offline HU DB, per 100g]: ${p.per100gKcal.roundToInt()} kcal, " +
            "P:${String.format(Locale.US, "%.1f", p.per100gProtein)}g, " +
            "CH:${String.format(Locale.US, "%.1f", p.per100gCarbs)}g, " +
            "F:${String.format(Locale.US, "%.1f", p.per100gFat)}g, " +
            "Salt:${String.format(Locale.US, "%.1f", p.per100gSalt)}g"
}
