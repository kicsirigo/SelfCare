package com.selfcare.app.domain.inci

import kotlinx.serialization.Serializable

@Serializable
data class InciDatabase(
    val version: Int = 1,
    val ingredients: List<InciIngredient> = emptyList(),
    val conflicts: List<InciConflictRule> = emptyList()
)

@Serializable
data class InciIngredient(
    val id: String,
    val canonicalName: String,
    val synonyms: List<String> = emptyList(),
    val category: String,
    val effectSummary: String,
    val cautionLevel: String,
    val comedogenicRating: Int = 0,
    val irritancyRating: Int = 0
)

@Serializable
data class InciConflictRule(
    val id: String,
    val categoriesA: List<String> = emptyList(),
    val categoriesB: List<String> = emptyList(),
    val ingredientIdsA: List<String> = emptyList(),
    val ingredientIdsB: List<String> = emptyList(),
    val severity: String,
    val title: String,
    val recommendation: String
)

@Serializable
data class InciMatch(
    val ingredientId: String,
    val canonicalName: String,
    val matchedToken: String,
    val category: String,
    val effectSummary: String,
    val cautionLevel: String,
    val comedogenicRating: Int = 0,
    val irritancyRating: Int = 0
)

data class DetectedConflict(
    val ruleId: String,
    val severity: String,
    val title: String,
    val recommendation: String,
    val productAName: String,
    val productBName: String,
    val ingredientAName: String,
    val ingredientBName: String,
    val slot: String
)
