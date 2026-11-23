package com.selfcare.app.domain.cv

/**
 * Qualitative status tier replacing opaque raw numbers with human-readable labels and gauges.
 */
enum class MetricStatusLevel {
    OPTIMAL,
    GOOD,
    MODERATE,
    HIGH
}

data class MetricAssessment(
    val level: MetricStatusLevel,
    val scorePercent: Int,
    val progress: Float
)

data class HairQualitativeSummary(
    val crownVolumeLevel: MetricStatusLevel,
    val crownVolumePercent: Int,
    val shineLevel: MetricStatusLevel,
    val shinePercent: Int,
    val hairlineOpenForehead: Boolean,
    val templeSymmetryPercent: Int
)

enum class ImprovementTipCode {
    BLEMISHES,
    REDNESS,
    TEXTURE,
    UNDER_EYE,
    LIPS,
    SHAVE,
    SPF_WITH_ACTIVES,
    ISOTRETINOIN_VITAMIN_A_WARNING,
    ROUTINE_REMINDER,
    MAINTAIN
}

enum class TipVariant {
    GENERIC,
    HAS_TARGETED_PRODUCT,
    ACTIVE_IRRITATION_CAUTION,
    SYSTEMIC_MEDICATION_CARE,
    HAS_SUPPLEMENT_SUPPORT,
    HYGIENE_UNLOGGED_REMINDER
}

data class ShelfProductSummary(
    val name: String,
    val category: String = "OTHER",
    val ingredientsText: String = ""
)

data class CheckInDailyContext(
    val morningRoutineDone: Boolean = false,
    val eveningRoutineDone: Boolean = false,
    val lipBalmLoggedToday: Boolean = false
)

data class ProductContextTip(
    val code: ImprovementTipCode,
    val matchedProductName: String? = null,
    val variant: TipVariant = TipVariant.GENERIC
)

data class ObjectiveRatingSummary(
    val scoreOutOfTen: Float,
    val level: MetricStatusLevel,
    val tips: List<ImprovementTipCode>,
    val contextualTips: List<ProductContextTip> = tips.map { ProductContextTip(it) },
    val usedShelfProductCount: Int = 0
)

/**
 * Interprets raw CV metrics into realistic qualitative statuses (Optimal / Good / Moderate / High)
 * and 0..100 visual gauge values calibrated for real phone front cameras. Never issues medical diagnoses.
 */
object MetricInterpreter {

    fun interpretUnderEye(deltaLab: Float): MetricAssessment {
        val level = when {
            deltaLab < 7.0f -> MetricStatusLevel.OPTIMAL
            deltaLab < 13.5f -> MetricStatusLevel.GOOD
            deltaLab < 21f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val progress = (deltaLab / 30f).coerceIn(0.08f, 1f)
        val healthScore = (100f - (deltaLab / 30f) * 100f).toInt().coerceIn(5, 99)
        return MetricAssessment(level, healthScore, progress)
    }

    fun interpretRedness(rednessIndex: Float): MetricAssessment {
        // Subtract normal healthy human skin CIELAB a* baseline (~0.15) before penalizing erythema
        val excess = (rednessIndex - 0.15f).coerceAtLeast(0f)
        val level = when {
            rednessIndex < 0.21f -> MetricStatusLevel.OPTIMAL
            rednessIndex < 0.31f -> MetricStatusLevel.GOOD
            rednessIndex < 0.44f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val progress = (excess / 0.42f).coerceIn(0.08f, 1f)
        val calmScore = (100f - (excess / 0.42f) * 100f).toInt().coerceIn(5, 99)
        return MetricAssessment(level, calmScore, progress)
    }

    fun interpretTexture(textureVariance: Float): MetricAssessment {
        val level = when {
            textureVariance < 9f -> MetricStatusLevel.OPTIMAL
            textureVariance < 17f -> MetricStatusLevel.GOOD
            textureVariance < 29f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val progress = (textureVariance / 40f).coerceIn(0.08f, 1f)
        val smoothScore = (100f - (textureVariance / 40f) * 100f).toInt().coerceIn(5, 99)
        return MetricAssessment(level, smoothScore, progress)
    }

    fun interpretLipDryness(lipDrynessScore: Float): MetricAssessment {
        val level = when {
            lipDrynessScore < 0.21f -> MetricStatusLevel.OPTIMAL
            lipDrynessScore < 0.37f -> MetricStatusLevel.GOOD
            lipDrynessScore < 0.56f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val progress = (lipDrynessScore / 0.80f).coerceIn(0.08f, 1f)
        val hydrationScore = (100f - (lipDrynessScore / 0.80f) * 100f).toInt().coerceIn(5, 99)
        return MetricAssessment(level, hydrationScore, progress)
    }

    fun interpretBlemish(blemishScore: Float, blemishCount: Int): MetricAssessment {
        val level = when {
            blemishCount <= 1 && blemishScore < 0.11f -> MetricStatusLevel.OPTIMAL
            blemishCount <= 4 && blemishScore < 0.28f -> MetricStatusLevel.GOOD
            blemishCount <= 8 && blemishScore < 0.55f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val progress = blemishScore.coerceIn(0.06f, 1f)
        val clarityScore = (100f - blemishScore.coerceIn(0f, 1f) * 95f).toInt().coerceIn(5, 99)
        return MetricAssessment(level, clarityScore, progress)
    }

    fun interpretStubble(stubbleScore: Float): MetricAssessment {
        val clamped = stubbleScore.coerceIn(0f, 1f)
        val level = when {
            clamped < 0.24f -> MetricStatusLevel.OPTIMAL
            clamped < 0.48f -> MetricStatusLevel.GOOD
            clamped < 0.68f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val groomedScore = (100f - clamped * 90f).toInt().coerceIn(10, 99)
        return MetricAssessment(level, groomedScore, clamped.coerceIn(0.06f, 1f))
    }

    /**
     * Computes an objective 1.0..10.0 grooming & skin-care rating purely from on-device
     * CV metrics (zero subjective beauty bias) with a weakest-link penalty so genuine issues
     * are not hidden by averaging, and generates holistic improvement advice factoring in
     * creams, ointments, systemic/oral medications, vitamins, and daily routine/hygiene logs.
     */
    fun computeObjectiveRating(
        underEyeDelta: Float,
        redness: Float,
        texture: Float,
        lipDryness: Float,
        blemishScore: Float,
        blemishCount: Int,
        stubbleScore: Float,
        shaveDue: Boolean = false,
        shelfProducts: List<ShelfProductSummary> = emptyList(),
        dailyContext: CheckInDailyContext = CheckInDailyContext()
    ): ObjectiveRatingSummary {
        val underEye = interpretUnderEye(underEyeDelta)
        val rednessEval = interpretRedness(redness)
        val textureEval = interpretTexture(texture)
        val lipEval = interpretLipDryness(lipDryness)
        val blemishEval = interpretBlemish(blemishScore, blemishCount)
        val stubbleEval = interpretStubble(stubbleScore)

        val weighted100 = (
            blemishEval.scorePercent * 0.28f +
                rednessEval.scorePercent * 0.20f +
                textureEval.scorePercent * 0.18f +
                underEye.scorePercent * 0.14f +
                lipEval.scorePercent * 0.10f +
                stubbleEval.scorePercent * 0.10f
            ).coerceIn(10f, 99f)

        val weakest = minOf(
            blemishEval.scorePercent,
            rednessEval.scorePercent,
            textureEval.scorePercent,
            underEye.scorePercent,
            lipEval.scorePercent,
            stubbleEval.scorePercent
        ).toFloat()

        // Blend 90% weighted average with 10% weakest-link for balanced realism without shadow over-penalization
        val calibrated100 = (weighted100 * 0.90f + weakest * 0.10f).coerceIn(10f, 99f)
        val rating10 = ((calibrated100 / 10f) * 10f).toInt() / 10f // 1 decimal place
        val level = when {
            rating10 >= 8.3f -> MetricStatusLevel.OPTIMAL
            rating10 >= 6.8f -> MetricStatusLevel.GOOD
            rating10 >= 5.0f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }

        val candidates = mutableListOf<Pair<ImprovementTipCode, Int>>()
        if (blemishEval.scorePercent < 82) candidates.add(ImprovementTipCode.BLEMISHES to blemishEval.scorePercent)
        if (rednessEval.scorePercent < 80) candidates.add(ImprovementTipCode.REDNESS to rednessEval.scorePercent)
        if (textureEval.scorePercent < 80) candidates.add(ImprovementTipCode.TEXTURE to textureEval.scorePercent)
        if (underEye.scorePercent < 78) candidates.add(ImprovementTipCode.UNDER_EYE to underEye.scorePercent)
        if (lipEval.scorePercent < 78) candidates.add(ImprovementTipCode.LIPS to lipEval.scorePercent)
        if (shaveDue || stubbleScore >= 0.56f) candidates.add(ImprovementTipCode.SHAVE to stubbleEval.scorePercent)

        // Holistic Shelf Classification (Medications + Vitamins + Topical Skincare + SPF)
        val systemicIsotretinoin = findProductMatching(shelfProducts, ISOTRETINOIN_KEYWORDS)
        val systemicOralAcneMed = systemicIsotretinoin
            ?: findProductMatching(shelfProducts, ORAL_ACNE_ANTIBIOTIC_KEYWORDS)
        val vitaminASupplement = findOralSupplementMatching(shelfProducts, VITAMIN_A_KEYWORDS)
        val strongActive = findProductMatching(shelfProducts, STRONG_ACTIVE_KEYWORDS)
        val hasSunscreen = findProductMatching(shelfProducts, SUNSCREEN_KEYWORDS) != null
        val topicalBlemishProduct = findTopicalMatching(shelfProducts, BLEMISH_PRODUCT_KEYWORDS)
        val skinSupportVitamin = findOralSupplementMatching(shelfProducts, SKIN_VITAMIN_KEYWORDS)
        val soothingProduct = findProductMatching(shelfProducts, SOOTHING_PRODUCT_KEYWORDS)
        val textureProduct = findTopicalMatching(shelfProducts, TEXTURE_PRODUCT_KEYWORDS)
        val eyeCreamProduct = findTopicalMatching(shelfProducts, EYE_CARE_KEYWORDS)
        val fatigueVitamin = findOralSupplementMatching(shelfProducts, FATIGUE_VITAMIN_KEYWORDS)
        val lipProduct = findTopicalMatching(shelfProducts, LIP_PRODUCT_KEYWORDS)
        val aftershaveProduct = findTopicalMatching(shelfProducts, AFTERSHAVE_KEYWORDS)

        val topCodes = candidates
            .sortedBy { it.second }
            .map { it.first }
            .take(3)
            .toMutableList()

        // Critical safety check: Isotretinoin (e.g. Medinac) + extra Vitamin A / Multivitamin
        if (systemicIsotretinoin != null && vitaminASupplement != null) {
            topCodes.add(0, ImprovementTipCode.ISOTRETINOIN_VITAMIN_A_WARNING)
        }

        // Photosensitizing active (systemic isotretinoin/doxycycline OR topical retinoid/acid) without SPF
        val photosensitizer = systemicOralAcneMed ?: strongActive
        if (photosensitizer != null && !hasSunscreen) {
            topCodes.add(ImprovementTipCode.SPF_WITH_ACTIVES)
        }

        // Remind if neither morning nor evening routine was logged today and skin is not optimal
        if (!dailyContext.morningRoutineDone && !dailyContext.eveningRoutineDone &&
            level != MetricStatusLevel.OPTIMAL && topCodes.size < 4
        ) {
            topCodes.add(ImprovementTipCode.ROUTINE_REMINDER)
        }

        val finalCodes = topCodes.distinct().ifEmpty { listOf(ImprovementTipCode.MAINTAIN) }

        val contextualTips = finalCodes.map { code ->
            when (code) {
                ImprovementTipCode.ISOTRETINOIN_VITAMIN_A_WARNING -> {
                    ProductContextTip(
                        code = code,
                        matchedProductName = "${systemicIsotretinoin?.name.orEmpty()} + ${vitaminASupplement?.name.orEmpty()}",
                        variant = TipVariant.ACTIVE_IRRITATION_CAUTION
                    )
                }
                ImprovementTipCode.BLEMISHES -> {
                    when {
                        systemicOralAcneMed != null -> ProductContextTip(
                            code = code,
                            matchedProductName = systemicOralAcneMed.name,
                            variant = TipVariant.SYSTEMIC_MEDICATION_CARE
                        )
                        topicalBlemishProduct != null -> ProductContextTip(
                            code = code,
                            matchedProductName = topicalBlemishProduct.name,
                            variant = TipVariant.HAS_TARGETED_PRODUCT
                        )
                        skinSupportVitamin != null -> ProductContextTip(
                            code = code,
                            matchedProductName = skinSupportVitamin.name,
                            variant = TipVariant.HAS_SUPPLEMENT_SUPPORT
                        )
                        else -> ProductContextTip(code = code)
                    }
                }
                ImprovementTipCode.REDNESS -> {
                    when {
                        systemicOralAcneMed != null -> ProductContextTip(
                            code = code,
                            matchedProductName = systemicOralAcneMed.name,
                            variant = TipVariant.SYSTEMIC_MEDICATION_CARE
                        )
                        strongActive != null && rednessEval.scorePercent < 72 -> ProductContextTip(
                            code = code,
                            matchedProductName = strongActive.name,
                            variant = TipVariant.ACTIVE_IRRITATION_CAUTION
                        )
                        soothingProduct != null -> ProductContextTip(
                            code = code,
                            matchedProductName = soothingProduct.name,
                            variant = TipVariant.HAS_TARGETED_PRODUCT
                        )
                        else -> ProductContextTip(code = code)
                    }
                }
                ImprovementTipCode.TEXTURE -> {
                    when {
                        systemicOralAcneMed != null -> ProductContextTip(
                            code = code,
                            matchedProductName = systemicOralAcneMed.name,
                            variant = TipVariant.SYSTEMIC_MEDICATION_CARE
                        )
                        textureProduct != null -> ProductContextTip(
                            code = code,
                            matchedProductName = textureProduct.name,
                            variant = TipVariant.HAS_TARGETED_PRODUCT
                        )
                        skinSupportVitamin != null -> ProductContextTip(
                            code = code,
                            matchedProductName = skinSupportVitamin.name,
                            variant = TipVariant.HAS_SUPPLEMENT_SUPPORT
                        )
                        else -> ProductContextTip(code = code)
                    }
                }
                ImprovementTipCode.UNDER_EYE -> {
                    when {
                        eyeCreamProduct != null -> ProductContextTip(
                            code = code,
                            matchedProductName = eyeCreamProduct.name,
                            variant = TipVariant.HAS_TARGETED_PRODUCT
                        )
                        fatigueVitamin != null -> ProductContextTip(
                            code = code,
                            matchedProductName = fatigueVitamin.name,
                            variant = TipVariant.HAS_SUPPLEMENT_SUPPORT
                        )
                        else -> ProductContextTip(code = code)
                    }
                }
                ImprovementTipCode.LIPS -> {
                    when {
                        systemicIsotretinoin != null -> ProductContextTip(
                            code = code,
                            matchedProductName = lipProduct?.name ?: systemicIsotretinoin.name,
                            variant = TipVariant.SYSTEMIC_MEDICATION_CARE
                        )
                        !dailyContext.lipBalmLoggedToday && lipProduct != null -> ProductContextTip(
                            code = code,
                            matchedProductName = lipProduct.name,
                            variant = TipVariant.HYGIENE_UNLOGGED_REMINDER
                        )
                        lipProduct != null -> ProductContextTip(
                            code = code,
                            matchedProductName = lipProduct.name,
                            variant = TipVariant.HAS_TARGETED_PRODUCT
                        )
                        !dailyContext.lipBalmLoggedToday -> ProductContextTip(
                            code = code,
                            variant = TipVariant.HYGIENE_UNLOGGED_REMINDER
                        )
                        else -> ProductContextTip(code = code)
                    }
                }
                ImprovementTipCode.SHAVE -> {
                    when {
                        systemicIsotretinoin != null || strongActive != null -> ProductContextTip(
                            code = code,
                            matchedProductName = (systemicIsotretinoin ?: strongActive)?.name,
                            variant = TipVariant.ACTIVE_IRRITATION_CAUTION
                        )
                        aftershaveProduct != null -> ProductContextTip(
                            code = code,
                            matchedProductName = aftershaveProduct.name,
                            variant = TipVariant.HAS_TARGETED_PRODUCT
                        )
                        else -> ProductContextTip(code = code)
                    }
                }
                ImprovementTipCode.SPF_WITH_ACTIVES -> {
                    ProductContextTip(
                        code = code,
                        matchedProductName = photosensitizer?.name,
                        variant = TipVariant.ACTIVE_IRRITATION_CAUTION
                    )
                }
                else -> ProductContextTip(code = code)
            }
        }

        return ObjectiveRatingSummary(
            scoreOutOfTen = rating10.coerceIn(1.0f, 10.0f),
            level = level,
            tips = finalCodes,
            contextualTips = contextualTips,
            usedShelfProductCount = shelfProducts.size
        )
    }

    /**
     * Finds a hair-care product or hair-support vitamin on the user's shelf for HairHealthAnalysisCard.
     */
    fun findHairSupportProduct(products: List<ShelfProductSummary>): String? {
        return findProductMatching(products, HAIR_SUPPORT_KEYWORDS)?.name
    }

    private fun findProductMatching(
        products: List<ShelfProductSummary>,
        keywords: List<String>
    ): ShelfProductSummary? {
        return products.firstOrNull { product ->
            val combined = "${product.name} ${product.ingredientsText}".lowercase()
            keywords.any { combined.contains(it) }
        }
    }

    private fun findTopicalMatching(
        products: List<ShelfProductSummary>,
        keywords: List<String>
    ): ShelfProductSummary? {
        return products.firstOrNull { product ->
            !product.category.equals("VITAMIN", ignoreCase = true) &&
                run {
                    val combined = "${product.name} ${product.ingredientsText}".lowercase()
                    keywords.any { combined.contains(it) }
                }
        }
    }

    private fun findOralSupplementMatching(
        products: List<ShelfProductSummary>,
        keywords: List<String>
    ): ShelfProductSummary? {
        return products.firstOrNull { product ->
            val isOral = product.category.equals("VITAMIN", ignoreCase = true) ||
                product.category.equals("MEDICATION", ignoreCase = true) ||
                listOf("vitamin", "multivitamin", "kapszula", "tabletta", "omega", "magnézium", "magnesium")
                    .any { product.name.lowercase().contains(it) }
            if (!isOral) return@firstOrNull false
            val combined = "${product.name} ${product.ingredientsText}".lowercase()
            keywords.any { combined.contains(it) }
        }
    }

    private val ISOTRETINOIN_KEYWORDS = listOf(
        "medinac", "isotretinoin", "izotretinoin", "roaccutane", "accutane",
        "inerta", "curacne", "isotrexin"
    )

    private val ORAL_ACNE_ANTIBIOTIC_KEYWORDS = listOf(
        "doxycyclin", "doxycycline", "tetralysal", "lymecyclin", "lymecycline",
        "minocyclin", "minocycline"
    )

    private val VITAMIN_A_KEYWORDS = listOf(
        "vitamin a", "a-vitamin", "retinyl", "multivitamin"
    )

    private val BLEMISH_PRODUCT_KEYWORDS = listOf(
        "salicylic", "szalicil", "bha", "benzoyl", "adapalene", "adapalén",
        "epiduo", "differin", "duac", "akneroxid", "azelaic", "azelain",
        "skinoren", "erythromycin", "eritromicin", "davercin", "zineryt",
        "clindamycin", "klindamicin", "niacinamide", "niacinamid", "effaclar",
        "zinc pca", "cink"
    )

    private val STRONG_ACTIVE_KEYWORDS = listOf(
        "adapalene", "adapalén", "retinol", "retinal", "tretinoin", "epiduo",
        "differin", "benzoyl", "akneroxid", "duac", "glycolic", "glikolsav",
        "lactic", "tejsav", "salicylic", "szalicil", "aha", "bha"
    )

    private val SOOTHING_PRODUCT_KEYWORDS = listOf(
        "panthenol", "pantenol", "ceramide", "ceramid", "cicaplast", "bepanthen",
        "cerave", "madecassoside", "centella", "allantoin", "bisabolol",
        "soolantra", "rozex", "azelaic", "azelain", "skinoren", "sudocrem",
        "hydrocortisone", "elocom", "advantan", "cetirizine", "zyrtec", "lordestin",
        "moisturiz", "hidratál", "nyugtat"
    )

    private val TEXTURE_PRODUCT_KEYWORDS = listOf(
        "retinal", "retinol", "adapalene", "glycolic", "lactic", "salicylic",
        "ceramide", "ceramid", "hyaluron", "hialuron", "niacinamide", "niacinamid",
        "a-game", "cerave", "effaclar"
    )

    private val EYE_CARE_KEYWORDS = listOf(
        "caffeine", "koffein", "eye", "szemkörnyék", "szemkrém", "vitamin c",
        "ascorbic", "niacinamide", "peptide", "peptid"
    )

    private val FATIGUE_VITAMIN_KEYWORDS = listOf(
        "iron", "vas ", " vas", "b12", "b-complex", "b-komplex",
        "magnesium", "magnézium", "magnezium", "vitamin d", "d3", "d-vitamin"
    )

    private val SKIN_VITAMIN_KEYWORDS = listOf(
        "zinc", "cink", "omega-3", "omega 3", "vitamin c", "c-vitamin",
        "vitamin e", "e-vitamin", "biotin", "vitamin d", "d3"
    )

    private val LIP_PRODUCT_KEYWORDS = listOf(
        "lip", "ajak", "balm", "balzsam", "vaseline", "vazelin", "petrolatum",
        "bepanthen", "cicaplast", "labello", "carmex", "blistex"
    )

    private val AFTERSHAVE_KEYWORDS = listOf(
        "aftershave", "after shave", "borotválkozás", "panthenol", "cicaplast",
        "bepanthen", "aloe", "balm", "balzsam"
    )

    private val SUNSCREEN_KEYWORDS = listOf(
        "spf", "sunscreen", "fényvédő", "fenyvedo", "naptej", "uv ",
        "sun fluid", "anthelios", "sunblock"
    )

    private val HAIR_SUPPORT_KEYWORDS = listOf(
        "nizoral", "alpecin", "dercos", "minoxidil", "rosemary", "rozmaring",
        "keratin", "biotin", "ketoconazole", "koffein", "caffeine", "zinc", "cink"
    )

    fun interpretHairDetails(
        crownVolumeScore: Float,
        shineScore: Float,
        foreheadCoverage: Float,
        templeSymmetryScore: Float
    ): HairQualitativeSummary {
        val volPct = (crownVolumeScore * 100f).toInt().coerceIn(0, 100)
        val volLevel = when {
            volPct >= 75 -> MetricStatusLevel.OPTIMAL
            volPct >= 50 -> MetricStatusLevel.GOOD
            volPct >= 30 -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val shinePct = (shineScore * 100f).toInt().coerceIn(0, 100)
        val shineLevel = when {
            shinePct >= 75 -> MetricStatusLevel.OPTIMAL
            shinePct >= 52 -> MetricStatusLevel.GOOD
            shinePct >= 32 -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val symPct = (templeSymmetryScore * 100f).toInt().coerceIn(0, 100)
        return HairQualitativeSummary(
            crownVolumeLevel = volLevel,
            crownVolumePercent = volPct,
            shineLevel = shineLevel,
            shinePercent = shinePct,
            hairlineOpenForehead = foreheadCoverage < 0.35f,
            templeSymmetryPercent = symPct
        )
    }
}
