package com.selfcare.app.data.remote

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
