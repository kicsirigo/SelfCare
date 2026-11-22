package com.selfcare.app.ui.shelf

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.preset.PresetGroup
import com.selfcare.app.data.preset.PresetProduct
import com.selfcare.app.data.preset.PresetProductCatalog
import com.selfcare.app.data.remote.OpenBeautyFactsProduct
import com.selfcare.app.data.remote.OpenBeautyFactsSearchResult
import com.selfcare.app.data.repository.BarcodeLookupOutcome
import com.selfcare.app.data.repository.ProductRepository
import com.selfcare.app.data.repository.RoutineRepository
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

enum class ShelfCategoryFilter {
    ALL,
    FACE,
    RX,
    HAIR
}

data class ShelfProductItem(
    val entity: ProductEntity,
    val matches: List<InciMatch>
)

data class ProductEditorState(
    val id: Long = 0L,
    val name: String = "",
    val brand: String = "",
    val barcode: String = "",
    val category: String = "OTHER",
    val routineSlot: String = InciConflictDetector.SLOT_NONE,
    val isDefaultInRoutine: Boolean = false,
    val rawIngredientsText: String = "",
    val parsedMatches: List<InciMatch> = emptyList(),
    val source: String = "MANUAL"
)

data class ShelfUiState(
    val searchQuery: String = "",
    val categoryFilter: ShelfCategoryFilter = ShelfCategoryFilter.ALL,
    val selectedPresetGroup: PresetGroup = PresetGroup.RX,
    val visiblePresets: List<PresetProduct> = PresetProductCatalog.byGroup(PresetGroup.RX),
    val presetMatchesForSearch: List<PresetProduct> = emptyList(),
    val addedPresetIds: Set<String> = emptySet(),
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
    private val categoryFilterFlow = MutableStateFlow(ShelfCategoryFilter.ALL)
    private val presetGroupFlow = MutableStateFlow(PresetGroup.RX)
    private val lookupStateFlow = MutableStateFlow(LookupTransientState())
    private val editorFlow = MutableStateFlow<ProductEditorState?>(null)

    val uiState: StateFlow<ShelfUiState> = combine(
        productRepository.observeAllProducts(),
        searchQueryFlow,
        categoryFilterFlow,
        presetGroupFlow,
        lookupStateFlow,
        editorFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allProducts = values[0] as List<ProductEntity>
        val query = values[1] as String
        val catFilter = values[2] as ShelfCategoryFilter
        val presetGroup = values[3] as PresetGroup
        val lookup = values[4] as LookupTransientState
        val editor = values[5] as ProductEditorState?

        val trimmed = query.trim()
        val filteredByCategory = allProducts.filter { product ->
            when (catFilter) {
                ShelfCategoryFilter.ALL -> true
                ShelfCategoryFilter.RX -> product.category.equals("RX_TREATMENT", ignoreCase = true)
                ShelfCategoryFilter.HAIR ->
                    product.routineSlot.equals(InciConflictDetector.SLOT_HAIR, ignoreCase = true) ||
                        RoutineRepository.isHairCategory(product.category)
                ShelfCategoryFilter.FACE ->
                    !product.category.equals("RX_TREATMENT", ignoreCase = true) &&
                        !product.routineSlot.equals(InciConflictDetector.SLOT_HAIR, ignoreCase = true) &&
                        !RoutineRepository.isHairCategory(product.category)
            }
        }
        val filtered = if (trimmed.isEmpty()) {
            filteredByCategory
        } else {
            filteredByCategory.filter { p ->
                p.name.contains(trimmed, ignoreCase = true) ||
                    p.brand.contains(trimmed, ignoreCase = true) ||
                    (p.barcode?.contains(trimmed, ignoreCase = true) == true) ||
                    p.rawIngredientsText.contains(trimmed, ignoreCase = true)
            }
        }

        val items = filtered.map { entity ->
            ShelfProductItem(
                entity = entity,
                matches = productRepository.parseProductMatches(entity)
            )
        }
        val conflicts = productRepository.computeShelfConflicts(allProducts)

        val existingNames = allProducts.map { it.name.lowercase() }.toSet()
        val addedPresetIds = PresetProductCatalog.allPresets
            .filter { it.name.lowercase() in existingNames }
            .map { it.id }
            .toSet()

        ShelfUiState(
            searchQuery = query,
            categoryFilter = catFilter,
            selectedPresetGroup = presetGroup,
            visiblePresets = PresetProductCatalog.byGroup(presetGroup),
            presetMatchesForSearch = lookup.presetMatches,
            addedPresetIds = addedPresetIds,
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

    fun selectCategoryFilter(filter: ShelfCategoryFilter) {
        categoryFilterFlow.value = filter
    }

    fun selectPresetGroup(group: PresetGroup) {
        presetGroupFlow.value = group
    }

    fun addPresetProduct(preset: PresetProduct, autoAssignToRoutine: Boolean = true) {
        viewModelScope.launch {
            productRepository.saveProduct(
                name = preset.name,
                brand = preset.brand,
                barcode = null,
                category = preset.category,
                routineSlot = preset.defaultSlot,
                isDefaultInRoutine = autoAssignToRoutine && preset.defaultSlot != InciConflictDetector.SLOT_NONE,
                rawIngredientsText = preset.ingredientsText,
                source = "PRESET_${preset.group.name}"
            )
            lookupStateFlow.update {
                it.copy(
                    presetMatches = emptyList(),
                    offlineFallbackMessage = null
                )
            }
        }
    }

    fun performLookup() {
        val query = searchQueryFlow.value.trim()
        if (query.isEmpty()) return

        if (query.all { it.isDigit() } && query.length >= 4) {
            lookupBarcode(query)
        } else {
            searchRemoteAndPresetsByName(query)
        }
    }

    fun lookupBarcode(barcode: String) {
        val clean = barcode.trim()
        if (clean.isEmpty()) return

        viewModelScope.launch {
            lookupStateFlow.update {
                it.copy(
                    isLookingUp = true,
                    offlineFallbackMessage = null,
                    remoteResults = emptyList(),
                    presetMatches = emptyList()
                )
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

    private fun searchRemoteAndPresetsByName(query: String) {
        val localPresetHits = PresetProductCatalog.search(query)
        viewModelScope.launch {
            lookupStateFlow.update {
                it.copy(
                    isLookingUp = true,
                    offlineFallbackMessage = null,
                    remoteResults = emptyList(),
                    presetMatches = localPresetHits
                )
            }
            when (val result = productRepository.searchRemoteByName(query)) {
                is OpenBeautyFactsSearchResult.Success -> {
                    lookupStateFlow.update {
                        it.copy(
                            isLookingUp = false,
                            remoteResults = result.products,
                            presetMatches = localPresetHits,
                            offlineFallbackMessage = if (result.products.isEmpty() && localPresetHits.isEmpty()) {
                                "NOT_FOUND:$query"
                            } else null
                        )
                    }
                }
                is OpenBeautyFactsSearchResult.OfflineOrError -> {
                    lookupStateFlow.update {
                        it.copy(
                            isLookingUp = false,
                            presetMatches = localPresetHits,
                            offlineFallbackMessage = if (localPresetHits.isEmpty()) {
                                result.reason.ifBlank { "OFFLINE" }
                            } else null
                        )
                    }
                }
            }
        }
    }

    fun onSmartPhotoCaptured(bitmap: Bitmap?) {
        if (bitmap == null) return
        viewModelScope.launch {
            lookupStateFlow.update {
                it.copy(
                    isLookingUp = true,
                    offlineFallbackMessage = null,
                    remoteResults = emptyList(),
                    presetMatches = emptyList()
                )
            }
            // 1. Try offline barcode scan first on the full-resolution photo
            val scannedBarcode = barcodeScanner.scanBarcode(bitmap)
            if (!scannedBarcode.isNullOrBlank()) {
                searchQueryFlow.value = scannedBarcode
                lookupBarcode(scannedBarcode)
                return@launch
            }

            // 2. Fall back to offline on-device OCR on the same photo (photo never leaves device)
            val recognized = ocrScanner.recognizeText(bitmap).trim()
            if (recognized.isEmpty()) {
                lookupStateFlow.update {
                    it.copy(isLookingUp = false, offlineFallbackMessage = "SMART_SCAN_EMPTY")
                }
                return@launch
            }

            val inciMatches = productRepository.parseIngredients(recognized)
            val extractedQuery = SmartScanQueryExtractor.extractProductSearchQuery(recognized)
            val presetHits = SmartScanQueryExtractor.findPresetMatchesFromOcr(recognized, extractedQuery)

            // 3. If the back INCI ingredient list was photographed (and no preset matched), open editor prefilled
            if (SmartScanQueryExtractor.looksLikeIngredientList(recognized, inciMatches.size) && presetHits.isEmpty()) {
                lookupStateFlow.update { it.copy(isLookingUp = false) }
                editorFlow.value = ProductEditorState(
                    rawIngredientsText = recognized,
                    parsedMatches = inciMatches,
                    source = "OCR"
                )
                return@launch
            }

            // 4. Otherwise search offline presets + anonymous Open Beauty Facts text query
            val searchTerm = extractedQuery.ifBlank { recognized.take(40) }
            searchQueryFlow.value = searchTerm
            lookupStateFlow.update { it.copy(presetMatches = presetHits) }

            when (val result = productRepository.searchRemoteByName(searchTerm)) {
                is OpenBeautyFactsSearchResult.Success -> {
                    if (result.products.isNotEmpty() || presetHits.isNotEmpty()) {
                        lookupStateFlow.update {
                            it.copy(
                                isLookingUp = false,
                                remoteResults = result.products,
                                presetMatches = presetHits,
                                offlineFallbackMessage = null
                            )
                        }
                    } else {
                        lookupStateFlow.update { it.copy(isLookingUp = false) }
                        editorFlow.value = ProductEditorState(
                            name = searchTerm,
                            rawIngredientsText = if (inciMatches.isNotEmpty()) recognized else "",
                            parsedMatches = inciMatches,
                            source = "SMART_OCR"
                        )
                    }
                }
                is OpenBeautyFactsSearchResult.OfflineOrError -> {
                    if (presetHits.isNotEmpty()) {
                        lookupStateFlow.update {
                            it.copy(
                                isLookingUp = false,
                                presetMatches = presetHits,
                                offlineFallbackMessage = null
                            )
                        }
                    } else {
                        lookupStateFlow.update { it.copy(isLookingUp = false) }
                        editorFlow.value = ProductEditorState(
                            name = searchTerm,
                            rawIngredientsText = if (inciMatches.isNotEmpty()) recognized else "",
                            parsedMatches = inciMatches,
                            source = "SMART_OCR"
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
        lookupStateFlow.update {
            it.copy(remoteResults = emptyList(), presetMatches = emptyList(), offlineFallbackMessage = null)
        }
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
            category = product.category,
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

    fun updateEditorCategory(category: String) {
        editorFlow.update { current ->
            if (current == null) null
            else {
                val suggestedSlot = if (RoutineRepository.isHairCategory(category) &&
                    current.routineSlot == InciConflictDetector.SLOT_NONE
                ) {
                    InciConflictDetector.SLOT_HAIR
                } else {
                    current.routineSlot
                }
                current.copy(category = category, routineSlot = suggestedSlot)
            }
        }
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
                category = current.category,
                routineSlot = current.routineSlot,
                isDefaultInRoutine = current.isDefaultInRoutine,
                rawIngredientsText = current.rawIngredientsText,
                source = current.source
            )
            editorFlow.value = null
            lookupStateFlow.update {
                it.copy(offlineFallbackMessage = null, remoteResults = emptyList(), presetMatches = emptyList())
            }
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
        val remoteResults: List<OpenBeautyFactsProduct> = emptyList(),
        val presetMatches: List<PresetProduct> = emptyList()
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
