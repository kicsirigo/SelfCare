package com.selfcare.app.domain.health

import java.text.Normalizer

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
    val per100gSugar: Float = 0f,
    val per100gSalt: Float = 0f,
    val aliases: String = ""
)

/**
 * 100% offline curated Hungarian & international cooked dishes and everyday ingredients
 * (per 100g nutritional values including realistic salt estimates).
 */
object FoodPresetCatalog {
    val items: List<FoodPreset> = listOf(
        // Hungarian cooked dishes & stews
        FoodPreset("csirkeporkolt", "Chicken Paprikash / Chicken Stew", "Csirkepörkölt", 250f, 138f, 16.5f, 3.2f, 6.4f, 0.5f, 1.4f, 1.3f, "csirke porkolt, paprikas csirke, chicken stew"),
        FoodPreset("csirkepaprikas", "Creamy Chicken Paprikash", "Csirkepaprikás (tejfölös)", 250f, 162f, 15.8f, 4.5f, 8.9f, 0.4f, 1.8f, 1.4f, "paprikas csirke, tejfolos csirke"),
        FoodPreset("marhaporkolt", "Hungarian Beef Stew (Pörkölt)", "Marhapörkölt", 250f, 168f, 17.5f, 3.0f, 9.5f, 0.5f, 1.2f, 1.4f, "marha porkolt, beef goulash"),
        FoodPreset("sertesporkolt", "Hungarian Pork Stew (Pörkölt)", "Sertéspörkölt", 250f, 185f, 15.2f, 3.1f, 12.2f, 0.4f, 1.2f, 1.4f, "sertes porkolt, pork stew"),
        FoodPreset("nokedli", "Hungarian Egg Dumplings (Nokedli)", "Nokedli / Galuska", 200f, 152f, 4.8f, 29.5f, 1.6f, 1.1f, 0.4f, 0.9f, "galuska, spaetzle, dumplings"),
        FoodPreset("gulyasleves", "Hungarian Goulash Soup", "Gulyásleves", 350f, 78f, 6.2f, 6.8f, 2.8f, 1.1f, 1.8f, 1.2f, "gulyas, goulash"),
        FoodPreset("husleves", "Chicken Noodle Soup", "Húsleves (tésztával)", 350f, 42f, 3.5f, 4.2f, 1.2f, 0.4f, 0.8f, 1.1f, "tyukhusleves, broth"),
        FoodPreset("bableves", "Hungarian Bean Soup", "Bableves", 350f, 96f, 5.8f, 11.2f, 3.1f, 3.2f, 1.5f, 1.3f, "jokai bableves, bean soup"),
        FoodPreset("lecso", "Hungarian Pepper & Tomato Stew (Lecsó)", "Lecsó", 300f, 88f, 2.6f, 6.5f, 5.8f, 1.9f, 3.8f, 1.2f, "tojasos lecso, kolbaszos lecso"),
        FoodPreset("toltott_kaposzta", "Stuffed Cabbage", "Töltött káposzta", 300f, 128f, 8.2f, 7.8f, 7.2f, 2.1f, 2.0f, 1.5f, "kaposzta"),
        FoodPreset("rakott_krumpli", "Layered Potato Casserole", "Rakott krumpli", 300f, 158f, 7.4f, 13.5f, 8.2f, 1.4f, 1.6f, 1.4f, "rakott burgonya"),
        FoodPreset("paprikas_krumpli", "Paprika Potatoes with Sausage", "Paprikás krumpli", 300f, 118f, 4.5f, 14.2f, 4.8f, 1.8f, 1.4f, 1.3f, "paprikas burgonya"),
        FoodPreset("brassoi", "Garlic Pork & Roasted Potatoes (Brassói)", "Brassói aprópecsenye", 300f, 182f, 12.0f, 14.5f, 8.4f, 1.5f, 0.9f, 1.4f, "brassoi apropecsenye"),
        FoodPreset("rantott_csirkemell", "Breaded Fried Chicken Breast", "Rántott csirkemell", 180f, 232f, 21.5f, 13.5f, 10.2f, 0.7f, 0.6f, 1.3f, "rantott hus, schnitzel"),
        FoodPreset("rantott_sajt", "Breaded Fried Cheese", "Rántott sajt", 150f, 325f, 16.5f, 16.0f, 21.8f, 0.8f, 0.9f, 1.9f, "fried cheese"),
        FoodPreset("bolognai", "Spaghetti Bolognese", "Bolognai spagetti", 320f, 142f, 8.4f, 16.5f, 4.6f, 1.5f, 2.6f, 1.1f, "spagetti, bolognese, pasta"),
        FoodPreset("carbonara", "Spaghetti Carbonara", "Carbonara spagetti", 300f, 195f, 9.2f, 20.5f, 8.4f, 1.0f, 0.8f, 1.4f, "spagetti carbonara"),
        FoodPreset("turos_teszta", "Cottage Cheese Pasta", "Túrós tészta (tejföllel)", 280f, 165f, 8.5f, 21.0f, 5.2f, 1.1f, 1.8f, 0.9f, "turos csusza"),
        FoodPreset("lencsefozelek", "Lentil Stew (Főzelék)", "Lencsefőzelék", 300f, 112f, 6.8f, 14.8f, 2.6f, 4.8f, 1.5f, 1.2f, "fozelek, lentil"),
        FoodPreset("borsofozelek", "Green Pea Stew (Főzelék)", "Zöldborsófőzelék", 300f, 88f, 4.2f, 12.5f, 2.2f, 3.6f, 4.2f, 1.0f, "fozelek, pea stew"),
        FoodPreset("tokfozelek", "Marrow Squash Stew (Főzelék)", "Tökfőzelék", 300f, 64f, 2.0f, 7.5f, 2.8f, 1.4f, 2.9f, 1.0f, "fozelek"),
        FoodPreset("spenotfozelek", "Creamed Spinach (Főzelék)", "Spenótfőzelék", 280f, 76f, 3.6f, 6.4f, 3.9f, 2.2f, 1.5f, 1.1f, "spenot, paraj"),
        FoodPreset("gyros_tal", "Gyros Platter (Chicken, Fries, Salad)", "Gyros tál", 400f, 178f, 12.5f, 15.2f, 7.6f, 1.8f, 2.1f, 1.8f, "gyros, kebab, pita"),
        FoodPreset("pizza_slice", "Ham & Cheese Pizza", "Sonkás-sajtos pizza", 250f, 255f, 11.4f, 28.5f, 10.5f, 1.8f, 2.5f, 1.6f, "pizza"),
        FoodPreset("hamburger", "Classic Beef Burger", "Hamburger", 240f, 248f, 13.5f, 22.0f, 11.8f, 1.5f, 4.2f, 1.5f, "burger, cheeseburger"),
        FoodPreset("ham_sandwich", "Ham & Cheese Sandwich", "Sonkás-sajtos szendvics", 160f, 242f, 12.2f, 25.5f, 9.8f, 2.2f, 2.5f, 1.9f, "szendvics, sandwich, toast, melegszendvics"),
        FoodPreset("palacsinta", "Stuffed Crepe (Palacsinta)", "Palacsinta (túrós/lekváros)", 140f, 212f, 6.2f, 31.0f, 6.8f, 0.9f, 14.5f, 0.6f, "crepe, pancake"),
        FoodPreset("langos", "Hungarian Fried Dough (Lángos)", "Lángos (sajtos-tejfölös)", 220f, 318f, 9.2f, 34.5f, 15.8f, 1.3f, 2.0f, 1.7f, "langos"),
        // Everyday lean proteins & dairy
        FoodPreset("chicken_breast", "Chicken Breast (cooked)", "Csirkemell filé (sült/főtt)", 150f, 165f, 31f, 0f, 3.6f, 0f, 0f, 0.4f, "csirke, grillezett csirkemell"),
        FoodPreset("turkey_breast", "Turkey Breast", "Pulykamell filé", 150f, 135f, 29f, 0f, 1.5f, 0f, 0f, 0.4f, "pulyka"),
        FoodPreset("beef_lean", "Lean Beef (cooked)", "Sovány marhahús", 150f, 217f, 26f, 0f, 12f, 0f, 0f, 0.5f, "marha, steak"),
        FoodPreset("salmon", "Salmon Fillet", "Lazac filé", 150f, 208f, 20f, 0f, 13f, 0f, 0f, 0.3f, "hal, lazac"),
        FoodPreset("tuna_water", "Canned Tuna (in water)", "Tonhal konzerv (sós lében)", 120f, 116f, 26f, 0f, 0.8f, 0f, 0f, 1.1f, "tonhal"),
        FoodPreset("egg_boiled", "Boiled / Fried Egg (1 = 60g)", "Főtt / tükörtojás (1 db = 60g)", 120f, 155f, 13f, 1.1f, 11f, 0f, 0.5f, 0.4f, "tojas, rantotta, tukortojas, omelette"),
        FoodPreset("whey_protein", "Whey Protein Powder", "Whey Protein por (1 adag = 30g)", 30f, 395f, 78f, 6.5f, 5.5f, 1f, 4f, 0.6f, "feherje, protein shake, tejsavo"),
        FoodPreset("cottage_cheese", "Low-Fat Cottage Cheese (Túró)", "Zsírszegény túró", 150f, 98f, 15f, 3.4f, 2.5f, 0f, 3.2f, 0.3f, "turo"),
        FoodPreset("greek_yogurt", "Greek Yogurt 2%", "Görög joghurt 2%", 150f, 73f, 10f, 3.6f, 2f, 0f, 3.6f, 0.1f, "joghurt"),
        FoodPreset("skyr", "Skyr Natural", "Skyr natúr", 150f, 63f, 11f, 4.0f, 0.2f, 0f, 4.0f, 0.1f, "skyr"),
        FoodPreset("protein_pudding", "Protein Pudding", "Protein puding", 200f, 76f, 10f, 5.5f, 1.5f, 0.5f, 4f, 0.2f, "puding"),
        FoodPreset("kefir", "Kefir", "Kefir", 175f, 58f, 3.4f, 4.6f, 3.0f, 0f, 4.5f, 0.1f, "kefir"),
        FoodPreset("tejfol_12", "Sour Cream 12%", "Tejföl 12%", 50f, 136f, 3.1f, 3.8f, 12.0f, 0f, 3.8f, 0.1f, "tejfol"),
        FoodPreset("milk_1_5", "Milk 1.5%", "Tej 1,5%", 250f, 47f, 3.4f, 4.8f, 1.5f, 0f, 4.8f, 0.1f, "tej"),
        FoodPreset("trappista_cheese", "Semi-Hard Cheese (Trappista)", "Trappista sajt", 40f, 350f, 25f, 1.5f, 27f, 0f, 0.5f, 1.8f, "sajt"),
        FoodPreset("mozzarella", "Mozzarella", "Mozzarella", 100f, 254f, 18.5f, 1.0f, 19.5f, 0f, 1.0f, 0.9f, "sajt, mozzarella"),
        FoodPreset("chicken_ham", "Chicken Breast Ham", "Csirkemell sonka", 50f, 108f, 19.5f, 1.5f, 2.4f, 0f, 0.8f, 2.2f, "sonka, felvagott"),
        FoodPreset("virsli", "Frankfurter Sausage (Virsli)", "Virsli", 100f, 265f, 12.5f, 2.0f, 23.0f, 0f, 0.5f, 2.1f, "hotdog, virsli"),
        // Carbs, sides & bakery
        FoodPreset("jasmine_rice_cooked", "Jasmine / White Rice (cooked)", "Jázmin / fehér rizs (főtt)", 200f, 130f, 2.7f, 28f, 0.3f, 0.4f, 0.1f, 0.2f, "rizs, rice"),
        FoodPreset("bulgur_cooked", "Bulgur (cooked)", "Bulgur (főtt)", 200f, 83f, 3.1f, 18.6f, 0.2f, 4.5f, 0.1f, 0.2f, "bulgur"),
        FoodPreset("oats", "Rolled Oats (dry)", "Zabpehely (száraz)", 60f, 379f, 13.2f, 67.7f, 6.5f, 10.1f, 1f, 0.02f, "zab, zabkasa, oatmeal"),
        FoodPreset("whole_wheat_bread", "Whole Wheat Bread", "Teljes kiőrlésű kenyér", 70f, 247f, 13f, 41f, 3.4f, 7f, 5.6f, 1.2f, "kenyer, bread, piritos"),
        FoodPreset("white_bread", "White Bread", "Fehér kenyér", 70f, 265f, 8.8f, 50.5f, 2.6f, 2.5f, 3.5f, 1.3f, "kenyer, toast"),
        FoodPreset("kifli_zsemle", "Bread Roll (Kifli / Zsemle)", "Kifli / Zsemle (1 db = 55g)", 55f, 278f, 9.0f, 54.0f, 2.5f, 2.4f, 3.0f, 1.4f, "kifli, zsemle, zsomle"),
        FoodPreset("kakaos_csiga", "Cocoa Swirl Pastry", "Kakaós csiga (1 db = 100g)", 100f, 368f, 7.2f, 54.0f, 13.5f, 2.4f, 22.0f, 0.8f, "pekseg, csiga"),
        FoodPreset("pogacsa", "Cheese Scone (Pogácsa)", "Sajtos pogácsa", 80f, 375f, 10.5f, 40.5f, 19.0f, 1.8f, 2.5f, 1.9f, "pogacsa"),
        FoodPreset("potato_boiled", "Boiled Potato", "Főtt burgonya", 200f, 87f, 1.9f, 20f, 0.1f, 1.8f, 0.9f, 0.3f, "krumpli, burgonya"),
        FoodPreset("fries", "French Fries", "Sült krumpli / Hasábburgonya", 150f, 312f, 3.4f, 41.4f, 14.7f, 3.8f, 0.3f, 1.0f, "hasab, sultkrumpli, fries"),
        FoodPreset("pasta_cooked", "Pasta (cooked)", "Tészta (főtt)", 200f, 158f, 5.8f, 31f, 0.9f, 1.8f, 0.6f, 0.3f, "teszta, makaroni, penne"),
        // Fruits, veggies, fats & snacks
        FoodPreset("banana", "Banana", "Banán", 120f, 89f, 1.1f, 22.8f, 0.3f, 2.6f, 12.2f, 0f, "banan"),
        FoodPreset("apple", "Apple", "Alma", 150f, 52f, 0.3f, 13.8f, 0.2f, 2.4f, 10.4f, 0f, "alma"),
        FoodPreset("broccoli", "Broccoli", "Brokkoli", 150f, 34f, 2.8f, 6.6f, 0.4f, 2.6f, 1.7f, 0.08f, "brokkoli"),
        FoodPreset("avocado", "Avocado", "Avokádó", 80f, 160f, 2f, 8.5f, 14.7f, 6.7f, 0.7f, 0.02f, "avokado"),
        FoodPreset("peanut_butter", "Peanut Butter", "Mogyoróvaj", 20f, 588f, 25f, 20f, 50f, 6f, 9f, 0.5f, "mogyorovaj"),
        FoodPreset("almonds", "Almonds", "Mandula", 30f, 579f, 21f, 22f, 50f, 12.5f, 4.4f, 0.02f, "mandula, olajos mag"),
        FoodPreset("olive_oil", "Olive Oil (1 tbsp = 14g)", "Olívaolaj (1 ek = 14g)", 14f, 884f, 0f, 0f, 100f, 0f, 0f, 0f, "olaj, oliva"),
        FoodPreset("turo_rudi", "Túró Rudi (1 bar = 30g)", "Túró Rudi (1 db = 30g)", 30f, 360f, 9.5f, 38f, 18.5f, 0.5f, 34f, 0.2f, "pottyos, rudi")
    ).plus(FoodPresetCatalogExtra.extraItems).distinctBy { it.id }

    fun normalizeForSearch(text: String): String {
        val decomposed = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun search(query: String): List<FoodPreset> {
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) return items
        val compactQuery = normalizedQuery.replace(" ", "")
        val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }

        return items.filter { item ->
            val searchable = normalizeForSearch("${item.nameHu} ${item.nameEn} ${item.aliases}")
            val compactSearchable = searchable.replace(" ", "")
            searchable.contains(normalizedQuery) ||
                compactSearchable.contains(compactQuery) ||
                tokens.all { token -> searchable.contains(token) }
        }
    }
}
