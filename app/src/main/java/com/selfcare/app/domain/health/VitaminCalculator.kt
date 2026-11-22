package com.selfcare.app.domain.health

import com.selfcare.app.domain.cv.ShelfProductSummary
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

enum class NutrientCode {
    VITAMIN_D,
    VITAMIN_C,
    VITAMIN_B12,
    FOLATE,
    VITAMIN_A,
    VITAMIN_E,
    MAGNESIUM,
    ZINC,
    IRON,
    CALCIUM,
    OMEGA_3
}

enum class AgeGroup {
    CHILD_4_8,
    PRETEEN_9_13,
    TEEN_14_18,
    YOUNG_ADULT_19_30,
    ADULT_31_50,
    MATURE_51_70,
    SENIOR_71_PLUS
}

data class VitaminRequirement(
    val code: NutrientCode,
    val dailyAmount: String,
    val coveredByProducts: List<String> = emptyList()
) {
    val isCoveredOnShelf: Boolean get() = coveredByProducts.isNotEmpty()
}

data class VitaminPlan(
    val ageYears: Int,
    val ageGroup: AgeGroup,
    val requirements: List<VitaminRequirement>,
    val coveredCount: Int
)

/**
 * 100% offline, evidence-based daily vitamin & mineral reference intake calculator
 * based on EFSA Dietary Reference Values (DRVs) and NIH RDAs by age.
 * Informational only — never replaces a balanced diet or medical advice.
 */
object VitaminCalculator {

    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseBirthDate(isoDate: String?): LocalDate? {
        if (isoDate.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(isoDate.trim(), isoFormatter) }.getOrNull()
    }

    fun formatBirthDate(year: Int, month: Int, day: Int): String? {
        return runCatching {
            LocalDate.of(year, month, day).format(isoFormatter)
        }.getOrNull()
    }

    fun computeAgeYears(
        birthDateIso: String?,
        today: LocalDate = LocalDate.now()
    ): Int? {
        val dob = parseBirthDate(birthDateIso) ?: return null
        if (dob.isAfter(today)) return null
        val years = Period.between(dob, today).years
        return years.takeIf { it in 1..120 }
    }

    fun ageGroupFor(ageYears: Int): AgeGroup = when {
        ageYears <= 8 -> AgeGroup.CHILD_4_8
        ageYears <= 13 -> AgeGroup.PRETEEN_9_13
        ageYears <= 18 -> AgeGroup.TEEN_14_18
        ageYears <= 30 -> AgeGroup.YOUNG_ADULT_19_30
        ageYears <= 50 -> AgeGroup.ADULT_31_50
        ageYears <= 70 -> AgeGroup.MATURE_51_70
        else -> AgeGroup.SENIOR_71_PLUS
    }

    fun calculateForAge(
        ageYears: Int,
        shelfProducts: List<ShelfProductSummary> = emptyList()
    ): VitaminPlan {
        val clampedAge = ageYears.coerceIn(4, 120)
        val group = ageGroupFor(clampedAge)

        val rawTargets: List<Pair<NutrientCode, String>> = when (group) {
            AgeGroup.CHILD_4_8 -> listOf(
                NutrientCode.VITAMIN_D to "15 µg (600 IU)",
                NutrientCode.VITAMIN_C to "25 mg",
                NutrientCode.VITAMIN_B12 to "1.2 µg",
                NutrientCode.FOLATE to "200 µg",
                NutrientCode.VITAMIN_A to "400 µg",
                NutrientCode.VITAMIN_E to "7 mg",
                NutrientCode.MAGNESIUM to "130 mg",
                NutrientCode.ZINC to "5 mg",
                NutrientCode.IRON to "10 mg",
                NutrientCode.CALCIUM to "1000 mg",
                NutrientCode.OMEGA_3 to "150–250 mg"
            )
            AgeGroup.PRETEEN_9_13 -> listOf(
                NutrientCode.VITAMIN_D to "15 µg (600 IU)",
                NutrientCode.VITAMIN_C to "45 mg",
                NutrientCode.VITAMIN_B12 to "1.8 µg",
                NutrientCode.FOLATE to "300 µg",
                NutrientCode.VITAMIN_A to "600 µg",
                NutrientCode.VITAMIN_E to "11 mg",
                NutrientCode.MAGNESIUM to "240 mg",
                NutrientCode.ZINC to "8 mg",
                NutrientCode.IRON to "8 mg",
                NutrientCode.CALCIUM to "1300 mg",
                NutrientCode.OMEGA_3 to "250 mg"
            )
            AgeGroup.TEEN_14_18 -> listOf(
                NutrientCode.VITAMIN_D to "15 µg (600 IU)",
                NutrientCode.VITAMIN_C to "65–75 mg",
                NutrientCode.VITAMIN_B12 to "2.4 µg",
                NutrientCode.FOLATE to "400 µg",
                NutrientCode.VITAMIN_A to "700–900 µg",
                NutrientCode.VITAMIN_E to "15 mg",
                NutrientCode.MAGNESIUM to "360–410 mg",
                NutrientCode.ZINC to "9–11 mg",
                NutrientCode.IRON to "11–15 mg",
                NutrientCode.CALCIUM to "1300 mg",
                NutrientCode.OMEGA_3 to "250 mg"
            )
            AgeGroup.YOUNG_ADULT_19_30 -> listOf(
                NutrientCode.VITAMIN_D to "15 µg (600 IU)",
                NutrientCode.VITAMIN_C to "75–90 mg",
                NutrientCode.VITAMIN_B12 to "2.4 µg",
                NutrientCode.FOLATE to "400 µg",
                NutrientCode.VITAMIN_A to "700–900 µg",
                NutrientCode.VITAMIN_E to "15 mg",
                NutrientCode.MAGNESIUM to "310–400 mg",
                NutrientCode.ZINC to "8–11 mg",
                NutrientCode.IRON to "8–18 mg",
                NutrientCode.CALCIUM to "1000 mg",
                NutrientCode.OMEGA_3 to "250–500 mg"
            )
            AgeGroup.ADULT_31_50 -> listOf(
                NutrientCode.VITAMIN_D to "15 µg (600 IU)",
                NutrientCode.VITAMIN_C to "75–90 mg",
                NutrientCode.VITAMIN_B12 to "2.4 µg",
                NutrientCode.FOLATE to "400 µg",
                NutrientCode.VITAMIN_A to "700–900 µg",
                NutrientCode.VITAMIN_E to "15 mg",
                NutrientCode.MAGNESIUM to "320–420 mg",
                NutrientCode.ZINC to "8–11 mg",
                NutrientCode.IRON to "8–18 mg",
                NutrientCode.CALCIUM to "1000 mg",
                NutrientCode.OMEGA_3 to "250–500 mg"
            )
            AgeGroup.MATURE_51_70 -> listOf(
                NutrientCode.VITAMIN_D to "15 µg (600 IU)",
                NutrientCode.VITAMIN_C to "75–90 mg",
                NutrientCode.VITAMIN_B12 to "2.4 µg",
                NutrientCode.FOLATE to "400 µg",
                NutrientCode.VITAMIN_A to "700–900 µg",
                NutrientCode.VITAMIN_E to "15 mg",
                NutrientCode.MAGNESIUM to "320–420 mg",
                NutrientCode.ZINC to "8–11 mg",
                NutrientCode.IRON to "8 mg",
                NutrientCode.CALCIUM to "1000–1200 mg",
                NutrientCode.OMEGA_3 to "250–500 mg"
            )
            AgeGroup.SENIOR_71_PLUS -> listOf(
                NutrientCode.VITAMIN_D to "20 µg (800 IU)",
                NutrientCode.VITAMIN_C to "75–90 mg",
                NutrientCode.VITAMIN_B12 to "2.4 µg",
                NutrientCode.FOLATE to "400 µg",
                NutrientCode.VITAMIN_A to "700–900 µg",
                NutrientCode.VITAMIN_E to "15 mg",
                NutrientCode.MAGNESIUM to "320–420 mg",
                NutrientCode.ZINC to "8–11 mg",
                NutrientCode.IRON to "8 mg",
                NutrientCode.CALCIUM to "1200 mg",
                NutrientCode.OMEGA_3 to "250–500 mg"
            )
        }

        // Only match oral supplements / vitamins / medications against daily vitamin intake
        val supplementProducts = shelfProducts.filter { p ->
            p.category.equals("VITAMIN", ignoreCase = true) ||
                p.category.equals("MEDICATION", ignoreCase = true) ||
                isLikelyOralSupplement(p.name, p.ingredientsText)
        }

        val requirements = rawTargets.map { (code, amount) ->
            val matched = supplementProducts
                .filter { productCoversNutrient(it, code) }
                .map { it.name }
                .distinct()
            VitaminRequirement(
                code = code,
                dailyAmount = amount,
                coveredByProducts = matched
            )
        }

        return VitaminPlan(
            ageYears = clampedAge,
            ageGroup = group,
            requirements = requirements,
            coveredCount = requirements.count { it.isCoveredOnShelf }
        )
    }

    private fun isLikelyOralSupplement(name: String, ingredients: String): Boolean {
        val text = "$name $ingredients".lowercase()
        return listOf(
            "multivitamin", "vitamin d", "d3", "omega-3", "omega 3",
            "magnesium", "magnézium", "magnezium", "b-complex", "b12",
            "kapszula", "tabletta", "étrend-kiegészítő", "supplement"
        ).any { text.contains(it) }
    }

    private fun productCoversNutrient(product: ShelfProductSummary, code: NutrientCode): Boolean {
        val text = "${product.name} ${product.ingredientsText}".lowercase()
        if (text.contains("multivitamin") && code != NutrientCode.OMEGA_3) {
            return true
        }
        val keywords = when (code) {
            NutrientCode.VITAMIN_D -> listOf("vitamin d", "d-vitamin", "d3", "d2", "cholecalciferol", "kolekalciferol")
            NutrientCode.VITAMIN_C -> listOf("vitamin c", "c-vitamin", "ascorbic", "aszkorbin")
            NutrientCode.VITAMIN_B12 -> listOf("b12", "b-12", "cobalamin", "kobalamin", "b-complex", "b-komplex")
            NutrientCode.FOLATE -> listOf("folate", "folic", "folsav", "vitamin b9", "b9", "methylfolate", "b-complex", "b-komplex")
            NutrientCode.VITAMIN_A -> listOf("vitamin a", "a-vitamin", "beta-carotene", "béta-karotin", "retinyl")
            NutrientCode.VITAMIN_E -> listOf("vitamin e", "e-vitamin", "tocopherol", "tokoferol")
            NutrientCode.MAGNESIUM -> listOf("magnesium", "magnézium", "magnezium", "bisglycinate", "biszglicinát")
            NutrientCode.ZINC -> listOf("zinc", "cink")
            NutrientCode.IRON -> listOf("iron", "vas ", " vas", "ferrous", "ferric", "bisglycinate iron", "vas-biszglicinát")
            NutrientCode.CALCIUM -> listOf("calcium", "kalcium")
            NutrientCode.OMEGA_3 -> listOf("omega-3", "omega 3", "omega3", "epa", "dha", "fish oil", "halolaj")
        }
        return keywords.any { text.contains(it) }
    }
}
