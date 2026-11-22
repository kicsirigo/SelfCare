package com.selfcare.app.ui.shelf

import com.selfcare.app.data.preset.PresetProduct
import com.selfcare.app.data.preset.PresetProductCatalog

/**
 * Pure, on-device helper that extracts a clean product search query from raw OCR
 * label text and matches against the built-in offline preset catalog (Rx creams,
 * hair care, skincare) before any anonymous text lookup is attempted.
 */
object SmartScanQueryExtractor {

    private val volumeOrDosageOnlyRegex = Regex(
        """^\s*[eE]?\s*\d+([.,]\d+)?\s*(ml|l|g|mg|mg/g|%|fl\.?\s*oz|oz|pcs|db|tab|caps)\b.*$""",
        RegexOption.IGNORE_CASE
    )
    private val mostlyDigitsRegex = Regex("""^[\d\s\-./,:;()]+$""")
    private val boilerplatePrefixRegex = Regex(
        """^(ingredients|inci|összetevők|osszetevok|composition|aqua|water|www\.|http|lot|exp|mfg|made in|for external use|külsőleges|developed with|fragrance[- ]?free|gently|clinically|new|új|uj|original|bestseller|limited)\b.*$""",
        RegexOption.IGNORE_CASE
    )
    private val multiSpaceRegex = Regex("""\s+""")

    fun extractProductSearchQuery(ocrText: String): String {
        val lines = ocrText
            .lineSequence()
            .map { it.trim().replace(multiSpaceRegex, " ") }
            .filter { it.length >= 2 }
            .toList()

        if (lines.isEmpty()) return ""

        val candidates = lines.filter { line ->
            val letterCount = line.count { it.isLetter() }
            letterCount >= 2 &&
                !mostlyDigitsRegex.matches(line) &&
                !volumeOrDosageOnlyRegex.matches(line) &&
                !boilerplatePrefixRegex.matches(line)
        }

        val selected = if (candidates.isNotEmpty()) {
            candidates.take(3)
        } else {
            lines.take(2)
        }

        return selected
            .joinToString(" ")
            .replace(multiSpaceRegex, " ")
            .trim()
            .take(60)
            .trim()
    }

    fun looksLikeIngredientList(ocrText: String, inciMatchCount: Int): Boolean {
        if (inciMatchCount >= 3) return true
        val lower = ocrText.lowercase()
        val hasInciHeader = lower.contains("ingredients") ||
            lower.contains("inci") ||
            lower.contains("összetevők") ||
            lower.contains("osszetevok")
        if (hasInciHeader && inciMatchCount >= 1) return true
        if ((lower.contains("aqua") || lower.contains("water")) && inciMatchCount >= 2) return true
        return false
    }

    fun findPresetMatchesFromOcr(
        ocrText: String,
        extractedQuery: String
    ): List<PresetProduct> {
        if (extractedQuery.isNotBlank()) {
            val direct = PresetProductCatalog.search(extractedQuery)
            if (direct.isNotEmpty()) return direct
        }

        val normalizedOcr = ocrText.lowercase()
        return PresetProductCatalog.allPresets.filter { preset ->
            val nameTokens = preset.name
                .lowercase()
                .split(Regex("""[^a-z0-9]+"""))
                .filter { it.length >= 4 && it !in GENERIC_WORDS }
            nameTokens.any { token -> normalizedOcr.contains(token) }
        }
    }

    private val GENERIC_WORDS = setOf(
        "cream", "lotion", "serum", "shampoo", "cleanser", "balm", "wash",
        "care", "skin", "hair", "scalp", "acid", "daily", "facial", "foaming",
        "moisturizing", "moisturizer", "anti", "dandruff", "solution", "tonic",
        "pure", "organic", "emulsion", "fluid", "tube"
    )
}
