package com.selfcare.app.domain.inci

import java.util.Locale

class InciParser(database: InciDatabase) {

    private val exactLookup: Map<String, InciIngredient>
    private val ocrFuzzyLookup: Map<String, InciIngredient>

    init {
        val exact = LinkedHashMap<String, InciIngredient>()
        val fuzzy = LinkedHashMap<String, InciIngredient>()
        for (ingredient in database.ingredients) {
            val allNames = buildList {
                add(ingredient.canonicalName)
                addAll(ingredient.synonyms)
            }
            for (name in allNames) {
                val norm = normalizeToken(name)
                if (norm.isNotEmpty()) {
                    exact.putIfAbsent(norm, ingredient)
                    if (norm.length >= MIN_FUZZY_LENGTH) {
                        fuzzy.putIfAbsent(toOcrFuzzyKey(norm), ingredient)
                    }
                }
            }
        }
        exactLookup = exact
        ocrFuzzyLookup = fuzzy
    }

    fun parse(rawIngredients: String): List<InciMatch> {
        if (rawIngredients.isBlank()) return emptyList()

        val cleaned = stripHeadersAndNoise(rawIngredients)
        val rawSegments = cleaned.split(DELIMITER_REGEX)

        val seenIds = LinkedHashSet<String>()
        val matches = mutableListOf<InciMatch>()

        for (segment in rawSegments) {
            val candidates = expandParentheticalAliases(segment)
            for (candidate in candidates) {
                val normalized = normalizeToken(candidate)
                if (normalized.isEmpty()) continue

                val matchedIngredient = exactLookup[normalized]
                    ?: if (normalized.length >= MIN_FUZZY_LENGTH) {
                        ocrFuzzyLookup[toOcrFuzzyKey(normalized)]
                    } else {
                        null
                    }

                if (matchedIngredient != null && seenIds.add(matchedIngredient.id)) {
                    matches.add(
                        InciMatch(
                            ingredientId = matchedIngredient.id,
                            canonicalName = matchedIngredient.canonicalName,
                            matchedToken = candidate.trim(),
                            category = matchedIngredient.category,
                            effectSummary = matchedIngredient.effectSummary,
                            cautionLevel = matchedIngredient.cautionLevel,
                            comedogenicRating = matchedIngredient.comedogenicRating,
                            irritancyRating = matchedIngredient.irritancyRating
                        )
                    )
                }
            }
        }
        return matches
    }

    private fun stripHeadersAndNoise(input: String): String {
        var text = input.replace("\r\n", "\n").replace('\r', '\n')
        text = HEADER_REGEX.replace(text, " ")
        text = BULLET_REGEX.replace(text, ", ")
        text = PERCENTAGE_REGEX.replace(text, " ")
        return text
    }

    private fun expandParentheticalAliases(segment: String): List<String> {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<String>()
        val insideMatches = PAREN_CONTENT_REGEX.findAll(trimmed)
        val outer = PAREN_CONTENT_REGEX.replace(trimmed, " ").trim()
        if (outer.isNotEmpty()) {
            results.add(outer)
        }
        for (match in insideMatches) {
            val inner = match.groupValues[1].trim()
            if (inner.isNotEmpty()) {
                results.add(inner)
            }
        }
        return results
    }

    private fun normalizeToken(token: String): String {
        val lowered = token.lowercase(Locale.ROOT)
        val noPunct = NON_ALNUM_SPACE_REGEX.replace(lowered, " ")
        return WHITESPACE_REGEX.replace(noPunct, " ").trim()
    }

    private fun toOcrFuzzyKey(normalized: String): String {
        val sb = StringBuilder(normalized.length)
        for (ch in normalized) {
            sb.append(
                when (ch) {
                    '0' -> 'o'
                    '1', 'i' -> 'l'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    companion object {
        private const val MIN_FUZZY_LENGTH = 4
        private val HEADER_REGEX = Regex(
            """(?im)^\s*(active\s+ingredients?|ingredients?|inci|összetevők)\s*:\s*"""
        )
        private val BULLET_REGEX = Regex("""[•·▪‣◦*]+|\n+""")
        private val PERCENTAGE_REGEX = Regex("""[\[(]?\s*\d+(?:[.,]\d+)?\s*%\s*[\])]?""")
        private val PAREN_CONTENT_REGEX = Regex("""\(([^)]+)\)""")
        private val DELIMITER_REGEX = Regex("""[,;/|]+""")
        private val NON_ALNUM_SPACE_REGEX = Regex("""[^a-z0-9\s]""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
