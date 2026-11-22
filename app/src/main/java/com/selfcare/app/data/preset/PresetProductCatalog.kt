package com.selfcare.app.data.preset

/**
 * Offline curated catalog for prescription dermatological creams/gels (which lack retail barcodes),
 * hair & scalp products, and everyday skincare essentials.
 */
enum class PresetGroup {
    RX,
    HAIR,
    FACE,
    VITAMIN,
    MEDICATION
}

data class PresetProduct(
    val id: String,
    val name: String,
    val brand: String,
    val group: PresetGroup,
    val category: String,
    val defaultSlot: String,
    val ingredientsText: String,
    val shortNote: String
)

object PresetProductCatalog {

    val allPresets: List<PresetProduct> = listOf(
        // Prescription Dermatological (Rx - no retail barcode)
        PresetProduct(
            id = "rx_davercin",
            name = "Davercin 25 mg/g Gel",
            brand = "Tarchomin (Rx)",
            group = PresetGroup.RX,
            category = "RX_TREATMENT",
            defaultSlot = "EVENING",
            ingredientsText = "Erythromycin, Alcohol Denat.",
            shortNote = "Erythromycin cyclic carbonate topical antibiotic gel"
        ),
        PresetProduct(
            id = "rx_skinoren_cream",
            name = "Skinoren 20% Cream",
            brand = "LEO Pharma (Rx)",
            group = PresetGroup.RX,
            category = "RX_TREATMENT",
            defaultSlot = "BOTH",
            ingredientsText = "Azelaic Acid, Glycerin, Cetearyl Alcohol, Aqua",
            shortNote = "20% Azelaic Acid prescription cream for acne & post-blemish marks"
        ),
        PresetProduct(
            id = "rx_skinoren_gel",
            name = "Skinoren 15% Gel",
            brand = "LEO Pharma (Rx)",
            group = PresetGroup.RX,
            category = "RX_TREATMENT",
            defaultSlot = "BOTH",
            ingredientsText = "Azelaic Acid, Aqua, Glycerin",
            shortNote = "15% Azelaic Acid gel targeting redness and blemishes"
        ),
        PresetProduct(
            id = "rx_epiduo",
            name = "Epiduo 0.1% / 2.5% Gel",
            brand = "Galderma (Rx)",
            group = PresetGroup.RX,
            category = "RX_TREATMENT",
            defaultSlot = "EVENING",
            ingredientsText = "Adapalene, Benzoyl Peroxide, Glycerin, Aqua",
            shortNote = "Adapalene retinoid + Benzoyl Peroxide acne gel"
        ),
        PresetProduct(
            id = "rx_differin",
            name = "Differin 0.1% Gel",
            brand = "Galderma (Rx)",
            group = PresetGroup.RX,
            category = "RX_TREATMENT",
            defaultSlot = "EVENING",
            ingredientsText = "Adapalene, Aqua",
            shortNote = "0.1% Adapalene retinoid gel for pore turnover"
        ),
        PresetProduct(
            id = "rx_duac",
            name = "Duac Gel",
            brand = "GSK (Rx)",
            group = PresetGroup.RX,
            category = "RX_TREATMENT",
            defaultSlot = "EVENING",
            ingredientsText = "Clindamycin, Benzoyl Peroxide, Glycerin, Dimethicone, Aqua",
            shortNote = "Clindamycin + Benzoyl Peroxide combination gel"
        ),
        PresetProduct(
            id = "rx_zineryt",
            name = "Zineryt Solution",
            brand = "LEO Pharma (Rx)",
            group = PresetGroup.RX,
            category = "RX_TREATMENT",
            defaultSlot = "EVENING",
            ingredientsText = "Erythromycin, Zinc PCA, Alcohol Denat.",
            shortNote = "Erythromycin + Zinc topical solution"
        ),
        PresetProduct(
            id = "rx_akneroxid",
            name = "Akneroxid 5% Gel",
            brand = "Almirall (Rx)",
            group = PresetGroup.RX,
            category = "RX_TREATMENT",
            defaultSlot = "MORNING",
            ingredientsText = "Benzoyl Peroxide, Aqua",
            shortNote = "5% Benzoyl Peroxide antibacterial gel"
        ),
        PresetProduct(
            id = "rx_soolantra",
            name = "Soolantra 10 mg/g Cream",
            brand = "Galderma (Rx)",
            group = PresetGroup.RX,
            category = "RX_TREATMENT",
            defaultSlot = "EVENING",
            ingredientsText = "Ivermectin, Glycerin, Dimethicone, Panthenol, Aqua",
            shortNote = "Ivermectin prescription cream for redness and rosacea"
        ),
        PresetProduct(
            id = "rx_rozex",
            name = "Rozex 0.75% Gel",
            brand = "Galderma (Rx)",
            group = PresetGroup.RX,
            category = "RX_TREATMENT",
            defaultSlot = "BOTH",
            ingredientsText = "Metronidazole, Aqua",
            shortNote = "Metronidazole topical gel for redness-prone skin"
        ),

        // Hair & Scalp Care
        PresetProduct(
            id = "hair_nizoral",
            name = "Nizoral 2% Anti-Dandruff Shampoo",
            brand = "Stada",
            group = PresetGroup.HAIR,
            category = "SHAMPOO",
            defaultSlot = "HAIR",
            ingredientsText = "Ketoconazole, Sodium Lauryl Sulfate, Aqua",
            shortNote = "Ketoconazole 2% antifungal scalp shampoo"
        ),
        PresetProduct(
            id = "hair_alpecin_c1",
            name = "Alpecin C1 Caffeine Shampoo",
            brand = "Dr. Wolff",
            group = PresetGroup.HAIR,
            category = "SHAMPOO",
            defaultSlot = "HAIR",
            ingredientsText = "Caffeine, Zinc PCA, Niacinamide, Menthol, Aqua",
            shortNote = "Daily caffeine shampoo supporting hair roots"
        ),
        PresetProduct(
            id = "hair_dercos",
            name = "Vichy Dercos Anti-Dandruff Shampoo",
            brand = "Vichy",
            group = PresetGroup.HAIR,
            category = "SHAMPOO",
            defaultSlot = "HAIR",
            ingredientsText = "Selenium Sulfide, Salicylic Acid, Tocopherol, Aqua",
            shortNote = "Selenium Sulfide + Salicylic Acid scalp cleanser"
        ),
        PresetProduct(
            id = "hair_rosemary_serum",
            name = "Rosemary & Caffeine Scalp Serum",
            brand = "Botanical Scalp",
            group = PresetGroup.HAIR,
            category = "SCALP_TONIC",
            defaultSlot = "HAIR",
            ingredientsText = "Rosmarinus Officinalis Leaf Oil, Caffeine, Biotin, Panthenol, Aqua",
            shortNote = "Leave-in botanical scalp serum for hair density"
        ),
        PresetProduct(
            id = "hair_minoxidil_5",
            name = "Minoxidil 5% Scalp Solution",
            brand = "Pharmacy Formula",
            group = PresetGroup.HAIR,
            category = "SCALP_TONIC",
            defaultSlot = "HAIR",
            ingredientsText = "Minoxidil, Alcohol Denat., Aqua",
            shortNote = "5% topical scalp density treatment"
        ),
        PresetProduct(
            id = "hair_keratin_mask",
            name = "Keratin & Ceramide Hair Mask",
            brand = "RepairLab",
            group = PresetGroup.HAIR,
            category = "CONDITIONER",
            defaultSlot = "HAIR",
            ingredientsText = "Hydrolyzed Keratin, Ceramide NP, Panthenol, Cetearyl Alcohol, Aqua",
            shortNote = "Smoothing hair mask for cuticle strength and shine"
        ),
        PresetProduct(
            id = "hair_argan_oil",
            name = "Argan & Squalane Hair Oil",
            brand = "SilkStrand",
            group = PresetGroup.HAIR,
            category = "HAIR_OIL",
            defaultSlot = "HAIR",
            ingredientsText = "Argania Spinosa Kernel Oil, Squalane, Tocopherol, Dimethicone",
            shortNote = "Lightweight shine & frizz-control oil for hair lengths"
        ),

        // Daily Skincare Essentials
        PresetProduct(
            id = "face_cicaplast",
            name = "Cicaplast Baume B5+",
            brand = "La Roche-Posay",
            group = PresetGroup.FACE,
            category = "MOISTURIZER",
            defaultSlot = "BOTH",
            ingredientsText = "Panthenol, Madecassoside, Butyrospermum Parkii Butter, Glycerin, Aqua",
            shortNote = "Barrier-repair balm ideal alongside Rx acne treatments"
        ),
        PresetProduct(
            id = "face_cerave_cream",
            name = "CeraVe Moisturizing Cream",
            brand = "CeraVe",
            group = PresetGroup.FACE,
            category = "MOISTURIZER",
            defaultSlot = "BOTH",
            ingredientsText = "Ceramide NP, Ceramide AP, Ceramide EOP, Cholesterol, Sodium Hyaluronate, Glycerin, Aqua",
            shortNote = "Ceramide barrier moisturizer"
        ),
        PresetProduct(
            id = "face_effaclar_duo",
            name = "Effaclar Duo+M",
            brand = "La Roche-Posay",
            group = PresetGroup.FACE,
            category = "SERUM",
            defaultSlot = "MORNING",
            ingredientsText = "Niacinamide, Salicylic Acid, Zinc PCA, Glycerin, Aqua",
            shortNote = "Targeted blemish & pore-care gel-cream"
        ),
        PresetProduct(
            id = "face_agame_5",
            name = "A-Game 5 Retinal Emulsion",
            brand = "Geek & Gorgeous",
            group = PresetGroup.FACE,
            category = "SERUM",
            defaultSlot = "EVENING",
            ingredientsText = "Retinal, Panthenol, Allantoin, Bisabolol, Squalane, Aqua",
            shortNote = "0.05% Retinaldehyde evening serum"
        ),
        PresetProduct(
            id = "face_bepanthen",
            name = "Bepanthen Cream",
            brand = "Bayer",
            group = PresetGroup.FACE,
            category = "MOISTURIZER",
            defaultSlot = "EVENING",
            ingredientsText = "Panthenol, Petrolatum, Cetearyl Alcohol, Aqua",
            shortNote = "Soothing dexpanthenol recovery cream"
        ),

        // Vitamins & Daily Supplements
        PresetProduct(
            id = "vit_d3_2000",
            name = "Vitamin D3 2000 IU",
            brand = "Supplement",
            group = PresetGroup.VITAMIN,
            category = "VITAMIN",
            defaultSlot = "MORNING",
            ingredientsText = "Vitamin D3 (Cholecalciferol 50 µg / 2000 IU)",
            shortNote = "Daily Vitamin D3 immune & bone support"
        ),
        PresetProduct(
            id = "vit_c_1000",
            name = "Vitamin C 1000 mg",
            brand = "Supplement",
            group = PresetGroup.VITAMIN,
            category = "VITAMIN",
            defaultSlot = "MORNING",
            ingredientsText = "Vitamin C (Ascorbic Acid 1000 mg), Rosehip Extract",
            shortNote = "Antioxidant & collagen synthesis support"
        ),
        PresetProduct(
            id = "vit_magnesium",
            name = "Magnesium Bisglycinate",
            brand = "Supplement",
            group = PresetGroup.VITAMIN,
            category = "VITAMIN",
            defaultSlot = "EVENING",
            ingredientsText = "Magnesium Bisglycinate, Vitamin B6",
            shortNote = "Gentle chelated magnesium for sleep & muscle recovery"
        ),
        PresetProduct(
            id = "vit_omega3",
            name = "Omega-3 EPA + DHA",
            brand = "Supplement",
            group = PresetGroup.VITAMIN,
            category = "VITAMIN",
            defaultSlot = "MORNING",
            ingredientsText = "Omega-3 Fish Oil, EPA, DHA, Vitamin E (Tocopherol)",
            shortNote = "Essential fatty acids for skin barrier & heart health"
        ),
        PresetProduct(
            id = "vit_zinc",
            name = "Zinc 15 mg",
            brand = "Supplement",
            group = PresetGroup.VITAMIN,
            category = "VITAMIN",
            defaultSlot = "MORNING",
            ingredientsText = "Zinc Bisglycinate 15 mg",
            shortNote = "Skin clarity, hair & immune mineral support"
        ),
        PresetProduct(
            id = "vit_b_complex",
            name = "Vitamin B-Complex + B12",
            brand = "Supplement",
            group = PresetGroup.VITAMIN,
            category = "VITAMIN",
            defaultSlot = "MORNING",
            ingredientsText = "Vitamin B12 (Cobalamin), Folate (B9), Biotin, Niacin, Vitamin B6",
            shortNote = "Energy metabolism & nervous system B-vitamins"
        ),
        PresetProduct(
            id = "vit_multivitamin",
            name = "Daily Multivitamin",
            brand = "Supplement",
            group = PresetGroup.VITAMIN,
            category = "VITAMIN",
            defaultSlot = "MORNING",
            ingredientsText = "Multivitamin, Vitamin A, Vitamin C, Vitamin D3, Vitamin E, Vitamin B12, Folate, Magnesium, Zinc, Iron, Calcium",
            shortNote = "Broad-spectrum daily vitamin & mineral complex"
        ),

        // Everyday Medications & Pharmacy Ointments
        PresetProduct(
            id = "med_sudocrem",
            name = "Sudocrem Antiseptic Cream",
            brand = "Teva",
            group = PresetGroup.MEDICATION,
            category = "MEDICATION",
            defaultSlot = "EVENING",
            ingredientsText = "Zinc Oxide, Benzyl Alcohol, Lanolin, Panthenol",
            shortNote = "Zinc oxide barrier ointment for irritated skin"
        ),
        PresetProduct(
            id = "med_bepanthen_plus",
            name = "Bepanthen Plus Cream",
            brand = "Bayer",
            group = PresetGroup.MEDICATION,
            category = "MEDICATION",
            defaultSlot = "NONE",
            ingredientsText = "Dexpanthenol (Panthenol), Chlorhexidine",
            shortNote = "Antiseptic skin-healing pharmacy cream"
        ),
        PresetProduct(
            id = "med_ibuprofen",
            name = "Ibuprofen 400 mg",
            brand = "Pharmacy OTC",
            group = PresetGroup.MEDICATION,
            category = "MEDICATION",
            defaultSlot = "NONE",
            ingredientsText = "Ibuprofen 400 mg",
            shortNote = "OTC anti-inflammatory & pain relief tablet"
        ),
        PresetProduct(
            id = "med_cetirizine",
            name = "Cetirizine 10 mg",
            brand = "Pharmacy OTC",
            group = PresetGroup.MEDICATION,
            category = "MEDICATION",
            defaultSlot = "EVENING",
            ingredientsText = "Cetirizine Dihydrochloride 10 mg",
            shortNote = "Daily antihistamine for allergy relief"
        )
    )

    fun byGroup(group: PresetGroup): List<PresetProduct> =
        allPresets.filter { it.group == group }

    fun search(query: String): List<PresetProduct> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return allPresets.filter { preset ->
            preset.name.contains(q, ignoreCase = true) ||
                preset.brand.contains(q, ignoreCase = true) ||
                preset.ingredientsText.contains(q, ignoreCase = true) ||
                preset.shortNote.contains(q, ignoreCase = true)
        }
    }
}
