package com.selfcare.app.ui.shelf

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.remote.OpenBeautyFactsProduct
import com.selfcare.app.data.remote.OpenBeautyFactsSearchResult
import com.selfcare.app.data.repository.BarcodeLookupOutcome
import com.selfcare.app.data.repository.ProductRepository
import com.selfcare.app.domain.inci.DetectedConflict
import com.selfcare.app.domain.inci.InciConflictDetector
import com.selfcare.app.domain.inci.InciMatch
import com.selfcare.app.domain.ml.BarcodeScanner
import com.selfcare.app.domain.ml.OcrScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShelfProductItem(
    val entity: ProductEntity,
    val matches: List<InciMatch>
)

data class ProductEditorState(
    val id: Long = 0L,
    val name: String = "",
    val brand: String = "",
    val barcode: String = "",
    val routineSlot: String = InciConflictDetector.SLOT_NONE,
    val isDefaultInRoutine: Boolean = false,
    val rawIngredientsText: String = "",
    val parsedMatches: List<InciMatch> = emptyList(),
    val source: String = "MANUAL"
)

data class ShelfUiState(
    val searchQuery: String = "",
    val products: List<ShelfProductItem> = emptyList(),
    val conflicts: List<DetectedConflict> = emptyList(),
    val isLookingUp: Boolean = false,
    val offlineFallbackMessage: String? = null,
    val remoteResults: List<OpenBeautyFactsProduct> = emptyList(),
    val editor: ProductEditorState? = null
)

class ShelfViewModel(
    private val productRepository: ProductRepository,
    private val barcodeScanner: BarcodeScanner,
    private val ocrScanner: OcrScanner
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val lookupStateFlow = MutableStateFlow(LookupTransientState())
    private val editorFlow = MutableStateFlow<ProductEditorState?>(null)

    val uiState: StateFlow<ShelfUiState> = combine(
        productRepository.observeAllProducts(),
        searchQueryFlow,
        lookupStateFlow,
        editorFlow
    ) { allProducts, query, lookup, editor ->
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            allProducts
        } else {
            allProducts.filter { p ->
                p.name.contains(trimmed, ignoreCase = true) ||
                    p.brand.contains(trimmed, ignoreCase = true) ||
                    (p.barcode?.contains(trimmed, ignoreCase = true) == true)
            }
        }
        val items = filtered.map { entity ->
            ShelfProductItem(
                entity = entity,
                matches = productRepository.parseProductMatches(entity)
            )
        }
        val conflicts = productRepository.computeShelfConflicts(allProducts)
        ShelfUiState(
            searchQuery = query,
            products = items,
            conflicts = conflicts,
            isLookingUp = lookup.isLookingUp,
            offlineFallbackMessage = lookup.offlineFallbackMessage,
            remoteResults = lookup.remoteResults,
            editor = editor
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShelfUiState()
    )

    fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
    }

    fun performLookup() {
        val query = searchQueryFlow.value.trim()
        if (query.isEmpty()) return

        if (query.all { it.isDigit() } && query.length >= 4) {
            lookupBarcode(query)
        } else {
            searchRemoteByName(query)
        }
    }

    fun lookupBarcode(barcode: String) {
        val clean = barcode.trim()
        if (clean.isEmpty()) return

        viewModelScope.launch {
            lookupStateFlow.update {
                it.copy(isLookingUp = true, offlineFallbackMessage = null, remoteResults = emptyList())
            }
            when (val outcome = productRepository.lookupByBarcode(clean)) {
                is BarcodeLookupOutcome.LocalFound -> {
                    lookupStateFlow.update { it.copy(isLookingUp = false) }
                    openEditProductEditor(outcome.product)
                }
                is BarcodeLookupOutcome.RemoteFound -> {
                    lookupStateFlow.update { it.copy(isLookingUp = false) }
                    editorFlow.value = ProductEditorState(
                        name = outcome.product.name,
                        brand = outcome.product.brand,
                        barcode = outcome.product.barcode,
                        rawIngredientsText = outcome.product.ingredientsText,
                        parsedMatches = outcome.parsedMatches,
                        source = "OPEN_BEAUTY_FACTS"
                    )
                }
                is BarcodeLookupOutcome.NotFound -> {
                    lookupStateFlow.update {
                        it.copy(
                            isLookingUp = false,
                            offlineFallbackMessage = "NOT_FOUND:$clean"
                        )
                    }
                }
                is BarcodeLookupOutcome.OfflineFallback -> {
                    lookupStateFlow.update {
                        it.copy(
                            isLookingUp = false,
                            offlineFallbackMessage = outcome.reason.ifBlank { "OFFLINE" }
                        )
                    }
                }
            }
        }
    }

    private fun searchRemoteByName(query: String) {
        viewModelScope.launch {
            lookupStateFlow.update {
                it.copy(isLookingUp = true, offlineFallbackMessage = null, remoteResults = emptyList())
            }
            when (val result = productRepository.searchRemoteByName(query)) {
                is OpenBeautyFactsSearchResult.Success -> {
                    lookupStateFlow.update {
                        it.copy(
                            isLookingUp = false,
                            remoteResults = result.products,
                            offlineFallbackMessage = if (result.products.isEmpty()) "NOT_FOUND:$query" else null
                        )
                    }
                }
                is OpenBeautyFactsSearchResult.OfflineOrError -> {
                    lookupStateFlow.update {
                        it.copy(
                            isLookingUp = false,
                            offlineFallbackMessage = result.reason.ifBlank { "OFFLINE" }
                        )
                    }
                }
            }
        }
    }

    fun onBarcodeBitmapCaptured(bitmap: Bitmap?) {
        if (bitmap == null) return
        viewModelScope.launch {
            lookupStateFlow.update { it.copy(isLookingUp = true, offlineFallbackMessage = null) }
            val scanned = barcodeScanner.scanBarcode(bitmap)
            if (scanned.isNullOrBlank()) {
                lookupStateFlow.update {
                    it.copy(isLookingUp = false, offlineFallbackMessage = "BARCODE_NONE")
                }
            } else {
                searchQueryFlow.value = scanned
                lookupBarcode(scanned)
            }
        }
    }

    fun onOcrBitmapCaptured(bitmap: Bitmap?) {
        if (bitmap == null) return
        viewModelScope.launch {
            val recognized = ocrScanner.recognizeText(bitmap).trim()
            if (recognized.isEmpty()) {
                lookupStateFlow.update {
                    it.copy(offlineFallbackMessage = "OCR_EMPTY")
                }
            } else {
                val currentEditor = editorFlow.value
                val matches = productRepository.parseIngredients(recognized)
                if (currentEditor != null) {
                    editorFlow.value = currentEditor.copy(
                        rawIngredientsText = recognized,
                        parsedMatches = matches,
                        source = "OCR"
                    )
                } else {
                    editorFlow.value = ProductEditorState(
                        rawIngredientsText = recognized,
                        parsedMatches = matches,
                        source = "OCR"
                    )
                }
            }
        }
    }

    fun selectRemoteProduct(product: OpenBeautyFactsProduct) {
        val matches = productRepository.parseIngredients(product.ingredientsText)
        editorFlow.value = ProductEditorState(
            name = product.name,
            brand = product.brand,
            barcode = product.barcode,
            rawIngredientsText = product.ingredientsText,
            parsedMatches = matches,
            source = "OPEN_BEAUTY_FACTS"
        )
        lookupStateFlow.update { it.copy(remoteResults = emptyList(), offlineFallbackMessage = null) }
    }

    fun dismissOfflineFallback() {
        lookupStateFlow.update { it.copy(offlineFallbackMessage = null) }
    }

    fun openNewProductEditor(
        initialBarcode: String = "",
        initialIngredients: String = "",
        source: String = "MANUAL"
    ) {
        editorFlow.value = ProductEditorState(
            barcode = initialBarcode,
            rawIngredientsText = initialIngredients,
            parsedMatches = productRepository.parseIngredients(initialIngredients),
            source = source
        )
    }

    fun openEditProductEditor(product: ProductEntity) {
        editorFlow.value = ProductEditorState(
            id = product.id,
            name = product.name,
            brand = product.brand,
            barcode = product.barcode.orEmpty(),
            routineSlot = product.routineSlot,
            isDefaultInRoutine = product.isDefaultInRoutine,
            rawIngredientsText = product.rawIngredientsText,
            parsedMatches = productRepository.parseProductMatches(product),
            source = product.source
        )
    }

    fun closeEditor() {
        editorFlow.value = null
    }

    fun updateEditorName(value: String) {
        editorFlow.update { it?.copy(name = value) }
    }

    fun updateEditorBrand(value: String) {
        editorFlow.update { it?.copy(brand = value) }
    }

    fun updateEditorBarcode(value: String) {
        editorFlow.update { it?.copy(barcode = value) }
    }

    fun updateEditorIngredients(value: String) {
        val matches = productRepository.parseIngredients(value)
        editorFlow.update { it?.copy(rawIngredientsText = value, parsedMatches = matches) }
    }

    fun updateEditorSlot(slot: String) {
        editorFlow.update { it?.copy(routineSlot = slot) }
    }

    fun updateEditorDefault(isDefault: Boolean) {
        editorFlow.update { it?.copy(isDefaultInRoutine = isDefault) }
    }

    fun saveEditorProduct() {
        val current = editorFlow.value ?: return
        if (current.name.isBlank()) return

        viewModelScope.launch {
            productRepository.saveProduct(
                id = current.id,
                name = current.name,
                brand = current.brand,
                barcode = current.barcode.takeIf { it.isNotBlank() },
                routineSlot = current.routineSlot,
                isDefaultInRoutine = current.isDefaultInRoutine,
                rawIngredientsText = current.rawIngredientsText,
                source = current.source
            )
            editorFlow.value = null
            lookupStateFlow.update { it.copy(offlineFallbackMessage = null, remoteResults = emptyList()) }
        }
    }

    fun deleteProduct(id: Long) {
        viewModelScope.launch {
            productRepository.deleteProduct(id)
        }
    }

    private data class LookupTransientState(
        val isLookingUp: Boolean = false,
        val offlineFallbackMessage: String? = null,
        val remoteResults: List<OpenBeautyFactsProduct> = emptyList()
    )

    companion object {
        fun factory(
            productRepository: ProductRepository,
            barcodeScanner: BarcodeScanner,
            ocrScanner: OcrScanner
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ShelfViewModel(
                    productRepository = productRepository,
                    barcodeScanner = barcodeScanner,
                    ocrScanner = ocrScanner
                ) as T
            }
        }
    }
}
