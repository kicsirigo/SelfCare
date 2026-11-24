package com.selfcare.app.data.remote

data class GroundedSourceRef(
    val title: String,
    val sourceLabel: String,
    val url: String? = null
)

data class FoodNutritionItem(
    val barcode: String?,
    val name: String,
    val brand: String = "",
    val per100gKcal: Float,
    val per100gProtein: Float,
    val per100gCarbs: Float,
    val per100gFat: Float,
    val per100gFiber: Float = 0f,
    val per100gSugar: Float = 0f,
    val per100gSalt: Float = 0f,
    val defaultGrams: Float = 100f,
    val sourceLabel: String = "Web Search",
    val sourceUrl: String? = null
)

sealed interface FoodLookupResult {
    data class Found(val item: FoodNutritionItem) : FoodLookupResult
    data class NotFound(val barcode: String) : FoodLookupResult
    data class Error(val reason: String) : FoodLookupResult
}

sealed interface FoodSearchResult {
    data class Success(val items: List<FoodNutritionItem>) : FoodSearchResult
    data class Error(val reason: String) : FoodSearchResult
}

data class RemoteProductItem(
    val barcode: String,
    val name: String,
    val brand: String = "",
    val ingredientsText: String = "",
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val category: String = "OTHER",
    val routineSlot: String = "NONE"
)

sealed interface ProductWebLookupResult {
    data class Found(val product: RemoteProductItem) : ProductWebLookupResult
    data class NotFound(val barcode: String) : ProductWebLookupResult
    data class Error(val reason: String) : ProductWebLookupResult
}

sealed interface ProductWebSearchResult {
    data class Success(val products: List<RemoteProductItem>) : ProductWebSearchResult
    data class Error(val reason: String) : ProductWebSearchResult
}
