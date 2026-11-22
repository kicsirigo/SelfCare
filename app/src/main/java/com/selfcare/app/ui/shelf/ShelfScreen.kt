package com.selfcare.app.ui.shelf

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.R
import com.selfcare.app.data.preset.PresetGroup
import com.selfcare.app.data.preset.PresetProduct
import com.selfcare.app.domain.inci.DetectedConflict
import com.selfcare.app.domain.inci.InciConflictDetector
import com.selfcare.app.domain.inci.InciMatch
import com.selfcare.app.ui.icons.AppIcons

@Composable
fun ShelfScreen(
    viewModel: ShelfViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val barcodeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        viewModel.onBarcodeBitmapCaptured(bitmap)
    }

    val ocrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        viewModel.onOcrBitmapCaptured(bitmap)
    }

    ShelfScreenContent(
        state = state,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onLookupClick = viewModel::performLookup,
        onSelectCategoryFilter = viewModel::selectCategoryFilter,
        onSelectPresetGroup = viewModel::selectPresetGroup,
        onAddPreset = viewModel::addPresetProduct,
        onScanBarcodeClick = {
            try {
                barcodeLauncher.launch(null)
            } catch (_: ActivityNotFoundException) {
                viewModel.openNewProductEditor()
            }
        },
        onScanOcrClick = {
            try {
                ocrLauncher.launch(null)
            } catch (_: ActivityNotFoundException) {
                viewModel.openNewProductEditor()
            }
        },
        onAddProductClick = { viewModel.openNewProductEditor() },
        onSelectRemoteProduct = viewModel::selectRemoteProduct,
        onDismissOfflineFallback = viewModel::dismissOfflineFallback,
        onEditProduct = viewModel::openEditProductEditor,
        onDeleteProduct = viewModel::deleteProduct,
        onEditorNameChange = viewModel::updateEditorName,
        onEditorBrandChange = viewModel::updateEditorBrand,
        onEditorBarcodeChange = viewModel::updateEditorBarcode,
        onEditorCategoryChange = viewModel::updateEditorCategory,
        onEditorIngredientsChange = viewModel::updateEditorIngredients,
        onEditorSlotChange = viewModel::updateEditorSlot,
        onEditorDefaultChange = viewModel::updateEditorDefault,
        onSaveEditor = viewModel::saveEditorProduct,
        onCloseEditor = viewModel::closeEditor,
        modifier = modifier
    )
}

@Composable
fun ShelfScreenContent(
    state: ShelfUiState,
    onSearchQueryChange: (String) -> Unit,
    onLookupClick: () -> Unit,
    onSelectCategoryFilter: (ShelfCategoryFilter) -> Unit,
    onSelectPresetGroup: (PresetGroup) -> Unit,
    onAddPreset: (PresetProduct) -> Unit,
    onScanBarcodeClick: () -> Unit,
    onScanOcrClick: () -> Unit,
    onAddProductClick: () -> Unit,
    onSelectRemoteProduct: (com.selfcare.app.data.remote.OpenBeautyFactsProduct) -> Unit,
    onDismissOfflineFallback: () -> Unit,
    onEditProduct: (com.selfcare.app.data.local.entity.ProductEntity) -> Unit,
    onDeleteProduct: (Long) -> Unit,
    onEditorNameChange: (String) -> Unit,
    onEditorBrandChange: (String) -> Unit,
    onEditorBarcodeChange: (String) -> Unit,
    onEditorCategoryChange: (String) -> Unit,
    onEditorIngredientsChange: (String) -> Unit,
    onEditorSlotChange: (String) -> Unit,
    onEditorDefaultChange: (Boolean) -> Unit,
    onSaveEditor: () -> Unit,
    onCloseEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editor = state.editor
    if (editor != null) {
        ProductEditorView(
            editor = editor,
            onNameChange = onEditorNameChange,
            onBrandChange = onEditorBrandChange,
            onBarcodeChange = onEditorBarcodeChange,
            onCategoryChange = onEditorCategoryChange,
            onIngredientsChange = onEditorIngredientsChange,
            onSlotChange = onEditorSlotChange,
            onDefaultChange = onEditorDefaultChange,
            onScanOcrClick = onScanOcrClick,
            onSaveClick = onSaveEditor,
            onCancelClick = onCloseEditor,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.shelf_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedButton(
                onClick = onAddProductClick,
                modifier = Modifier.testTag("shelf_add_product_btn")
            ) {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(text = stringResource(R.string.shelf_add_manual))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                placeholder = { Text(text = stringResource(R.string.shelf_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("shelf_search_input")
            )
            Button(
                onClick = onLookupClick,
                modifier = Modifier.testTag("shelf_lookup_btn")
            ) {
                Text(text = stringResource(R.string.shelf_lookup))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onScanBarcodeClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("shelf_scan_barcode_btn")
            ) {
                Icon(
                    imageVector = AppIcons.Barcode,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = stringResource(R.string.shelf_scan_barcode))
            }
            OutlinedButton(
                onClick = onScanOcrClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("shelf_scan_ocr_btn")
            ) {
                Icon(
                    imageVector = AppIcons.Scan,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = stringResource(R.string.shelf_scan_label))
            }
        }

        AnimatedVisibility(
            visible = state.isLookingUp,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (state.offlineFallbackMessage != null) {
            OfflineFallbackBanner(
                code = state.offlineFallbackMessage,
                onScanOcrClick = onScanOcrClick,
                onAddManualClick = { onAddProductClick() },
                onDismiss = onDismissOfflineFallback
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.conflicts.isNotEmpty()) {
                item {
                    ConflictBanner(conflicts = state.conflicts)
                }
            }

            if (state.presetMatchesForSearch.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.shelf_presets_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        state.presetMatchesForSearch.take(4).forEach { preset ->
                            PresetMatchRowCard(
                                preset = preset,
                                alreadyAdded = preset.id in state.addedPresetIds,
                                onAdd = { onAddPreset(preset) }
                            )
                        }
                    }
                }
            }

            if (state.remoteResults.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.shelf_remote_results),
                            style = MaterialTheme.typography.titleSmall
                        )
                        state.remoteResults.take(3).forEach { remote ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = remote.name.ifBlank { remote.barcode },
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        if (remote.brand.isNotBlank()) {
                                            Text(
                                                text = remote.brand,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TextButton(onClick = { onSelectRemoteProduct(remote) }) {
                                        Text(text = stringResource(R.string.shelf_use_result))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ShelfCategoryFilter.entries) { filter ->
                        val labelRes = when (filter) {
                            ShelfCategoryFilter.ALL -> R.string.shelf_cat_all
                            ShelfCategoryFilter.FACE -> R.string.shelf_cat_face
                            ShelfCategoryFilter.RX -> R.string.shelf_cat_rx
                            ShelfCategoryFilter.HAIR -> R.string.shelf_cat_hair
                        }
                        FilterChip(
                            selected = state.categoryFilter == filter,
                            onClick = { onSelectCategoryFilter(filter) },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.testTag("shelf_filter_${filter.name}")
                        )
                    }
                }
            }

            if (state.products.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shelf_empty_state"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Bottle,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.shelf_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.products, key = { it.entity.id }) { item ->
                    ProductShelfCard(
                        item = item,
                        onEdit = { onEditProduct(item.entity) },
                        onDelete = { onDeleteProduct(item.entity.id) }
                    )
                }
            }

            // Quick-Add Presets (Rx creams without barcodes, Hair & Scalp, Skincare)
            item {
                QuickPresetsCard(
                    selectedGroup = state.selectedPresetGroup,
                    presets = state.visiblePresets,
                    addedIds = state.addedPresetIds,
                    onSelectGroup = onSelectPresetGroup,
                    onAddPreset = onAddPreset
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun QuickPresetsCard(
    selectedGroup: PresetGroup,
    presets: List<PresetProduct>,
    addedIds: Set<String>,
    onSelectGroup: (PresetGroup) -> Unit,
    onAddPreset: (PresetProduct) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shelf_presets_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.shelf_presets_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.shelf_presets_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetGroup.entries.forEach { group ->
                    val labelRes = when (group) {
                        PresetGroup.RX -> R.string.shelf_cat_rx
                        PresetGroup.HAIR -> R.string.shelf_cat_hair
                        PresetGroup.FACE -> R.string.shelf_cat_face
                    }
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = { onSelectGroup(group) },
                        label = { Text(stringResource(labelRes)) },
                        modifier = Modifier.testTag("shelf_preset_group_${group.name}")
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets, key = { it.id }) { preset ->
                    val added = preset.id in addedIds
                    Card(
                        onClick = { onAddPreset(preset) },
                        modifier = Modifier
                            .width(210.dp)
                            .testTag("shelf_preset_${preset.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (added) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = preset.brand,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = preset.shortNote,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (added) AppIcons.Check else AppIcons.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (added) {
                                        stringResource(R.string.shelf_preset_added)
                                    } else {
                                        slotLabel(preset.defaultSlot)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetMatchRowCard(
    preset: PresetProduct,
    alreadyAdded: Boolean,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = preset.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${preset.brand} - ${preset.shortNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onAdd) {
                Text(
                    text = if (alreadyAdded) {
                        stringResource(R.string.shelf_preset_added)
                    } else {
                        stringResource(R.string.shelf_use_result)
                    }
                )
            }
        }
    }
}

@Composable
private fun OfflineFallbackBanner(
    code: String,
    onScanOcrClick: () -> Unit,
    onAddManualClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val message = when (code) {
        "BARCODE_NONE" -> stringResource(R.string.shelf_barcode_not_detected)
        "OCR_EMPTY" -> stringResource(R.string.shelf_ocr_no_text)
        else -> stringResource(R.string.shelf_offline_fallback)
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shelf_offline_fallback_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = stringResource(R.string.action_close),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onScanOcrClick) {
                    Text(text = stringResource(R.string.shelf_scan_label))
                }
                TextButton(onClick = onAddManualClick) {
                    Text(text = stringResource(R.string.shelf_add_manual))
                }
            }
        }
    }
}

@Composable
private fun ConflictBanner(conflicts: List<DetectedConflict>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inci_conflict_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.shelf_conflicts_detected),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            conflicts.forEach { conflict ->
                Text(
                    text = "${conflict.title} (${conflict.ingredientAName} + ${conflict.ingredientBName} - ${conflict.slot}): ${conflict.recommendation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ProductShelfCard(
    item: ShelfProductItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val product = item.entity
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shelf_product_card_${product.id}"),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (product.brand.isNotBlank()) {
                        Text(
                            text = product.brand,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = AppIcons.Edit,
                            contentDescription = stringResource(R.string.action_edit),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = AppIcons.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = slotLabel(product.routineSlot),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (product.isDefaultInRoutine) {
                    Text(
                        text = stringResource(R.string.routine_default_products),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (!product.barcode.isNullOrBlank()) {
                    Text(
                        text = product.barcode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.matches.isNotEmpty()) {
                InciMatchChips(matches = item.matches)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InciMatchChips(matches: List<InciMatch>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inci_match_list"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        matches.forEach { match ->
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = "${match.canonicalName} (${match.cautionLevel})",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductEditorView(
    editor: ProductEditorState,
    onNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onIngredientsChange: (String) -> Unit,
    onSlotChange: (String) -> Unit,
    onDefaultChange: (Boolean) -> Unit,
    onScanOcrClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    if (editor.id == 0L) R.string.shelf_add_title else R.string.shelf_edit_title
                ),
                style = MaterialTheme.typography.headlineSmall
            )
            IconButton(onClick = onCancelClick) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.action_close)
                )
            }
        }

        OutlinedTextField(
            value = editor.name,
            onValueChange = onNameChange,
            label = { Text(text = stringResource(R.string.shelf_product_name)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("shelf_product_name_input")
        )

        OutlinedTextField(
            value = editor.brand,
            onValueChange = onBrandChange,
            label = { Text(text = stringResource(R.string.shelf_product_brand)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("shelf_product_brand_input")
        )

        OutlinedTextField(
            value = editor.barcode,
            onValueChange = onBarcodeChange,
            label = { Text(text = stringResource(R.string.shelf_product_barcode)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("shelf_product_barcode_input")
        )

        Text(
            text = stringResource(R.string.shelf_category_label),
            style = MaterialTheme.typography.titleSmall
        )
        val categories = listOf(
            "OTHER" to stringResource(R.string.shelf_cat_face),
            "RX_TREATMENT" to stringResource(R.string.shelf_cat_rx),
            "SHAMPOO" to stringResource(R.string.shelf_cat_hair)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            categories.forEach { (catCode, label) ->
                val selected = when (catCode) {
                    "SHAMPOO" -> editor.category in setOf("SHAMPOO", "CONDITIONER", "SCALP_TONIC", "HAIR_OIL", "HAIR")
                    "RX_TREATMENT" -> editor.category == "RX_TREATMENT"
                    else -> editor.category != "RX_TREATMENT" &&
                        editor.category !in setOf("SHAMPOO", "CONDITIONER", "SCALP_TONIC", "HAIR_OIL", "HAIR")
                }
                FilterChip(
                    selected = selected,
                    onClick = { onCategoryChange(catCode) },
                    label = { Text(label) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.shelf_product_ingredients),
                style = MaterialTheme.typography.titleSmall
            )
            TextButton(onClick = onScanOcrClick) {
                Icon(
                    imageVector = AppIcons.Scan,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(text = stringResource(R.string.shelf_scan_label))
            }
        }

        OutlinedTextField(
            value = editor.rawIngredientsText,
            onValueChange = onIngredientsChange,
            minLines = 3,
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("shelf_product_ingredients_input")
        )

        Text(
            text = stringResource(R.string.shelf_routine_slot),
            style = MaterialTheme.typography.titleSmall
        )

        val slots = listOf(
            InciConflictDetector.SLOT_MORNING,
            InciConflictDetector.SLOT_EVENING,
            InciConflictDetector.SLOT_BOTH,
            InciConflictDetector.SLOT_HAIR,
            InciConflictDetector.SLOT_NONE
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("shelf_product_slot_selector"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            slots.forEach { slot ->
                FilterChip(
                    selected = editor.routineSlot == slot,
                    onClick = { onSlotChange(slot) },
                    label = { Text(text = slotLabel(slot)) },
                    modifier = Modifier.testTag("shelf_slot_$slot")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.shelf_default_in_routine),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = editor.isDefaultInRoutine,
                onCheckedChange = onDefaultChange,
                modifier = Modifier.testTag("shelf_product_default_toggle")
            )
        }

        Text(
            text = stringResource(R.string.shelf_parsed_inci),
            style = MaterialTheme.typography.titleSmall
        )

        if (editor.parsedMatches.isEmpty()) {
            Text(
                text = stringResource(R.string.shelf_no_inci_matches),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            InciMatchChips(matches = editor.parsedMatches)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancelClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
            Button(
                onClick = onSaveClick,
                enabled = editor.name.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("shelf_save_product_btn")
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun slotLabel(slot: String): String = when (slot.uppercase()) {
    InciConflictDetector.SLOT_MORNING -> stringResource(R.string.shelf_slot_morning)
    InciConflictDetector.SLOT_EVENING -> stringResource(R.string.shelf_slot_evening)
    InciConflictDetector.SLOT_BOTH -> stringResource(R.string.shelf_slot_both)
    InciConflictDetector.SLOT_HAIR -> stringResource(R.string.shelf_slot_hair)
    else -> stringResource(R.string.shelf_slot_none)
}
