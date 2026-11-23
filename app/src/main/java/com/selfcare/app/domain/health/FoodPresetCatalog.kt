package com.selfcare.app.domain.health

data class FoodPreset(
    val id: String,
    val nameEn: String,
    val nameHu: String,
    val defaultGrams: Float,
    val per100gKcal: Float,
    val per100gProtein: Float,
    val per100gCarbs: Float,
    val per100gFat: Float,
    val per100gFiber: Float = 0f,
    val per100gSugar: Float = 0f
)

/**
 * 100% offline curated everyday food catalog (per 100g nutritional values).
 */
object FoodPresetCatalog {
    val items: List<FoodPreset> = listOf(
        FoodPreset("chicken_breast", "Chicken Breast (cooked)", "Csirkemell filé (sült/főtt)", 150f, 165f, 31f, 0f, 3.6f),
        FoodPreset("turkey_breast", "Turkey Breast", "Pulykamell filé", 150f, 135f, 29f, 0f, 1.5f),
        FoodPreset("egg_boiled", "Boiled Egg (1 = 60g)", "Főtt tojás (1 db = 60g)", 120f, 155f, 13f, 1.1f, 11f),
        FoodPreset("whey_protein", "Whey Protein Powder", "Whey Protein por (1 adag = 30g)", 30f, 395f, 78f, 6.5f, 5.5f, 1f, 4f),
        FoodPreset("cottage_cheese", "Low-Fat Cottage Cheese", "Zsírszegény túró", 150f, 98f, 15f, 3.4f, 2.5f, 0f, 3.2f),
        FoodPreset("greek_yogurt", "Greek Yogurt 2%", "Görög joghurt 2%", 150f, 73f, 10f, 3.6f, 2f, 0f, 3.6f),
        FoodPreset("protein_pudding", "Protein Pudding", "Protein puding", 200f, 76f, 10f, 5.5f, 1.5f, 0.5f, 4f),
        FoodPreset("salmon", "Salmon Fillet", "Lazac filé", 150f, 208f, 20f, 0f, 13f),
        FoodPreset("tuna_water", "Canned Tuna (in water)", "Tonhal konzerv (sós lében)", 120f, 116f, 26f, 0f, 0.8f),
        FoodPreset("jasmine_rice_cooked", "Jasmine Rice (cooked)", "Jázmin rizs (főtt)", 200f, 130f, 2.7f, 28f, 0.3f, 0.4f),
        FoodPreset("oats", "Rolled Oats (dry)", "Zabpehely (száraz)", 60f, 379f, 13.2f, 67.7f, 6.5f, 10.1f, 1f),
        FoodPreset("whole_wheat_bread", "Whole Wheat Bread", "Teljes kiőrlésű kenyér", 70f, 247f, 13f, 41f, 3.4f, 7f, 5.6f),
        FoodPreset("potato_boiled", "Boiled Potato", "Főtt burgonya", 200f, 87f, 1.9f, 20f, 0.1f, 1.8f, 0.9f),
        FoodPreset("pasta_cooked", "Pasta (cooked)", "Tészta (főtt)", 200f, 158f, 5.8f, 31f, 0.9f, 1.8f, 0.6f),
        FoodPreset("banana", "Banana", "Banán", 120f, 89f, 1.1f, 22.8f, 0.3f, 2.6f, 12.2f),
        FoodPreset("apple", "Apple", "Alma", 150f, 52f, 0.3f, 13.8f, 0.2f, 2.4f, 10.4f),
        FoodPreset("broccoli", "Broccoli", "Brokkoli", 150f, 34f, 2.8f, 6.6f, 0.4f, 2.6f, 1.7f),
        FoodPreset("avocado", "Avocado", "Avokádó", 80f, 160f, 2f, 8.5f, 14.7f, 6.7f, 0.7f),
        FoodPreset("peanut_butter", "Peanut Butter", "Mogyoróvaj", 20f, 588f, 25f, 20f, 50f, 6f, 9f),
        FoodPreset("almonds", "Almonds", "Mandula", 30f, 579f, 21f, 22f, 50f, 12.5f, 4.4f),
        FoodPreset("olive_oil", "Olive Oil (1 tbsp = 14g)", "Olívaolaj (1 ek = 14g)", 14f, 884f, 0f, 0f, 100f),
        FoodPreset("milk_1_5", "Milk 1.5%", "Tej 1,5%", 250f, 47f, 3.4f, 4.8f, 1.5f, 0f, 4.8f),
        FoodPreset("trappista_cheese", "Semi-Hard Cheese (Trappista)", "Trappista sajt", 40f, 350f, 25f, 1.5f, 27f),
        FoodPreset("beef_lean", "Lean Beef (cooked)", "Sovány marhahús", 150f, 217f, 26f, 0f, 12f),
        FoodPreset("turo_rudi", "Túró Rudi (1 bar = 30g)", "Túró Rudi (1 db = 30g)", 30f, 360f, 9.5f, 38f, 18.5f, 0.5f, 34f)
    )

    fun search(query: String): List<FoodPreset> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter {
            it.nameEn.lowercase().contains(q) || it.nameHu.lowercase().contains(q)
        }
    }
}
