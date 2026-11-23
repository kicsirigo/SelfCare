package com.selfcare.app.ui.shelf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.FileOutputStream
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.R
import com.selfcare.app.data.preset.PresetGroup
import com.selfcare.app.data.preset.PresetProduct
import com.selfcare.app.domain.health.NutrientCode
import com.selfcare.app.domain.health.VitaminCalculator
import com.selfcare.app.domain.health.VitaminPlan
import com.selfcare.app.domain.inci.DetectedConflict
import com.selfcare.app.domain.inci.InciConflictDetector
import com.selfcare.app.domain.inci.InciMatch
import com.selfcare.app.ui.icons.AppIcons
import com.selfcare.app.ui.theme.NeonWarning
import java.io.File

@Composable
fun ShelfScreen(
    viewModel: ShelfViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val fullResOcrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        pendingFile = null
        if (!success) {
            ScanPhotoHelper.cleanupTempFile(file)
            return@rememberLauncherForActivityResult
        }
        val bitmap = ScanPhotoHelper.decodeAndDeleteTempPhoto(file)
        viewModel.onOcrBitmapCaptured(bitmap)
    }

    fun launchFullResOcr() {
        try {
            val capture = ScanPhotoHelper.createTempScanCapture(context)
            pendingFile = capture.file
            fullResOcrLauncher.launch(capture.uri)
        } catch (_: ActivityNotFoundException) {
            ScanPhotoHelper.cleanupTempFile(pendingFile)
            pendingFile = null
            viewModel.openNewProductEditor()
        } catch (_: Exception) {
            ScanPhotoHelper.cleanupTempFile(pendingFile)
            pendingFile = null
            viewModel.openNewProductEditor()
        }
    }

    if (state.isLiveScannerOpen) {
        ProductLiveScannerView(
            liveProductDetected = state.liveProductDetected,
            isGeminiVisionConfigured = state.isGeminiVisionConfigured,
            onLiveFrame = viewModel::onLiveScannerFrame,
            onCaptureBitmap = viewModel::onSmartPhotoCaptured,
            onClose = viewModel::closeLiveScanner,
            modifier = modifier
        )
        return
    }

    ShelfScreenContent(
        state = state,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onLookupClick = viewModel::performLookup,
        onSelectCategoryFilter = viewModel::selectCategoryFilter,
        onAddPreset = viewModel::addPresetProduct,
        onSmartScanClick = viewModel::openLiveScanner,
        onScanOcrClick = { launchFullResOcr() },
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
        onConfirmScanSave = viewModel::confirmPendingScanSave,
        onConfirmScanEdit = viewModel::confirmPendingScanEdit,
        onConfirmScanRetake = viewModel::retakePendingScan,
        onDismissScanConfirm = viewModel::dismissPendingScan,
        onSaveBirthDate = viewModel::setBirthDateIso,
        modifier = modifier
    )
}

@Composable
fun ShelfScreenContent(
    state: ShelfUiState,
    onSearchQueryChange: (String) -> Unit,
    onLookupClick: () -> Unit,
    onSelectCategoryFilter: (ShelfCategoryFilter) -> Unit,
    onAddPreset: (PresetProduct) -> Unit,
    onSmartScanClick: () -> Unit = {},
    onScanOcrClick: () -> Unit = {},
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
    onConfirmScanSave: () -> Unit = {},
    onConfirmScanEdit: () -> Unit = {},
    onConfirmScanRetake: () -> Unit = {},
    onDismissScanConfirm: () -> Unit = {},
    onSaveBirthDate: (String?) -> Unit = {},
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

    var pendingDeleteProduct by remember {
        mutableStateOf<com.selfcare.app.data.local.entity.ProductEntity?>(null)
    }

    state.pendingScanConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = onDismissScanConfirm,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isVisionAi = pending.source == "GEMINI_VISION" || pending.source == "GOOGLE_LENS"
                    val pillColor = if (isVisionAi) Color(0xFF38BDF8) else NeonWarning
                    Surface(
                        color = pillColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, pillColor.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("shelf_scan_ai_model_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(pillColor)
                            )
                            Text(
                                text = when (pending.source) {
                                    "GEMINI_VISION" -> stringResource(
                                        R.string.shelf_scan_ai_model_gemini,
                                        pending.modelUsed.ifBlank { "llama-4-scout (Groq)" }
                                    )
                                    "GOOGLE_LENS" -> stringResource(R.string.shelf_scan_source_google_lens)
                                    else -> stringResource(R.string.shelf_scan_source_ocr)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = pillColor
                            )
                        }
                    }
                    Text(text = stringResource(R.string.shelf_scan_confirm_title))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pending.geminiErrorDetail != null) {
                        var showTechnicalDetail by remember(pending.geminiErrorDetail) {
                            mutableStateOf(false)
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.shelf_gemini_friendly_error),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                TextButton(
                                    onClick = { showTechnicalDetail = !showTechnicalDetail }
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (showTechnicalDetail) {
                                                R.string.shelf_gemini_hide_details
                                            } else {
                                                R.string.shelf_gemini_show_details
                                            }
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                if (showTechnicalDetail) {
                                    Text(
                                        text = pending.geminiErrorDetail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // Visual comparison: Your captured photo + up to 3 matched reference photos
                    if (pending.capturedPhoto != null || pending.referencePhotos.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (pending.capturedPhoto != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = pending.capturedPhoto.asImageBitmap(),
                                            contentDescription = stringResource(R.string.shelf_scan_your_photo),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.shelf_scan_your_photo),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.shelf_scan_photo_saved_offline),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val lensContext = LocalContext.current
                                        val lensChooserTitle = stringResource(R.string.shelf_scan_google_lens_chooser)
                                        OutlinedButton(
                                            onClick = {
                                                openPhotoInGoogleLens(
                                                    context = lensContext,
                                                    bitmap = pending.capturedPhoto,
                                                    chooserTitle = lensChooserTitle
                                                )
                                            },
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                                .testTag("shelf_scan_google_lens_btn")
                                        ) {
                                            Text(
                                                text = stringResource(R.string.shelf_scan_open_google_lens),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = if (pending.referencePhotos.isNotEmpty()) {
                                    stringResource(
                                        R.string.shelf_scan_matched_photos_count,
                                        pending.referencePhotos.size
                                    )
                                } else {
                                    stringResource(R.string.shelf_scan_matched_photo)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (pending.referencePhotos.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(pending.referencePhotos) { refBmp ->
                                        Box(
                                            modifier = Modifier
                                                .size(92.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = refBmp.asImageBitmap(),
                                                contentDescription = stringResource(R.string.shelf_scan_matched_photo),
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Bottle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.shelf_scan_no_ref_photo),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = pending.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (pending.brand.isNotBlank()) {
                        Text(
                            text = pending.brand,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${stringResource(R.string.shelf_routine_slot)}: ${slotLabel(pending.routineSlot)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (pending.parsedMatches.isNotEmpty()) {
                        Text(
                            text = pending.parsedMatches.take(5).joinToString(", ") { it.canonicalName },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (pending.ingredientsText.isNotBlank()) {
                        Text(
                            text = pending.ingredientsText.take(140),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmScanSave,
                    modifier = Modifier.testTag("shelf_scan_confirm_save_btn")
                ) {
                    Text(text = stringResource(R.string.shelf_scan_confirm_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onConfirmScanRetake) {
                        Text(text = stringResource(R.string.shelf_scan_confirm_retake))
                    }
                    TextButton(onClick = onConfirmScanEdit) {
                        Text(text = stringResource(R.string.shelf_scan_confirm_edit))
                    }
                }
            }
        )
    }

    pendingDeleteProduct?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteProduct = null },
            title = { Text(text = stringResource(R.string.confirm_delete_title)) },
            text = { Text(text = stringResource(R.string.confirm_delete_product_body, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProduct(target.id)
                        pendingDeleteProduct = null
                    },
                    modifier = Modifier.testTag("confirm_delete_btn")
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProduct = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
            OutlinedButton(
                onClick = onLookupClick,
                modifier = Modifier.testTag("shelf_lookup_btn")
            ) {
                Text(text = stringResource(R.string.shelf_lookup))
            }
        }

        // Sleek 1-row primary action bar: Smart Scan (Live Barcode + Groq Vision) + INCI OCR + Manual Add
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSmartScanClick,
                modifier = Modifier
                    .weight(1.5f)
                    .testTag("shelf_smart_scan_btn")
            ) {
                Icon(
                    imageVector = AppIcons.Camera,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.shelf_smart_scan),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onScanOcrClick,
                modifier = Modifier.testTag("shelf_scan_ocr_btn")
            ) {
                Icon(
                    imageVector = AppIcons.Scan,
                    contentDescription = stringResource(R.string.shelf_scan_label),
                    modifier = Modifier.size(18.dp)
                )
            }
            OutlinedButton(
                onClick = onAddProductClick,
                modifier = Modifier.testTag("shelf_add_product_btn")
            ) {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = stringResource(R.string.shelf_add_manual),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (state.isGeminiVisionConfigured) AppIcons.Sparkle else AppIcons.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(
                    if (state.isGeminiVisionConfigured) {
                        R.string.shelf_vision_ai_active_badge
                    } else {
                        R.string.shelf_smart_scan_privacy
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = state.isLookingUp,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (state.geminiErrorBanner != null && state.pendingScanConfirmation == null) {
            var showBannerDetails by remember(state.geminiErrorBanner) { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.shelf_gemini_friendly_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissOfflineFallback, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    TextButton(onClick = { showBannerDetails = !showBannerDetails }) {
                        Text(
                            text = stringResource(
                                if (showBannerDetails) {
                                    R.string.shelf_gemini_hide_details
                                } else {
                                    R.string.shelf_gemini_show_details
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (showBannerDetails) {
                        Text(
                            text = state.geminiErrorBanner,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        if (state.offlineFallbackMessage != null) {
            OfflineFallbackBanner(
                code = state.offlineFallbackMessage,
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
                            ShelfCategoryFilter.VITAMIN -> R.string.shelf_cat_vitamin
                            ShelfCategoryFilter.MEDICATION -> R.string.shelf_cat_medication
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
                        onDelete = { pendingDeleteProduct = item.entity }
                    )
                }
            }

            // Age-based 100% offline Daily Vitamin & Mineral Reference Calculator
            item {
                VitaminCalculatorCard(
                    birthDateIso = state.birthDateIso,
                    vitaminPlan = state.vitaminPlan,
                    onSaveBirthDate = onSaveBirthDate
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
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
    onAddManualClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val message = when (code) {
        "SMART_SCAN_EMPTY" -> stringResource(R.string.shelf_smart_scan_empty)
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
                val title = localizedConflictTitle(conflict)
                val recommendation = localizedConflictRecommendation(conflict)
                val slotText = slotLabel(conflict.slot)
                Text(
                    text = "$title (${conflict.ingredientAName} + ${conflict.ingredientBName} - $slotText): $recommendation",
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
    val localPhotoBitmap = remember(product.photoPath) {
        val path = product.photoPath
        if (path.isNullOrBlank()) null
        else runCatching {
            val f = File(path)
            if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
        }.getOrNull()
    }
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (localPhotoBitmap != null) {
                    Image(
                        bitmap = localPhotoBitmap.asImageBitmap(),
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
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
                        text = "${match.canonicalName} (${cautionLevelLabel(match.cautionLevel)})",
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
            "SHAMPOO" to stringResource(R.string.shelf_cat_hair),
            "VITAMIN" to stringResource(R.string.shelf_cat_vitamin),
            "MEDICATION" to stringResource(R.string.shelf_cat_medication)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            categories.forEach { (catCode, label) ->
                val selected = when (catCode) {
                    "SHAMPOO" -> editor.category in setOf("SHAMPOO", "CONDITIONER", "SCALP_TONIC", "HAIR_OIL", "HAIR")
                    "RX_TREATMENT" -> editor.category == "RX_TREATMENT"
                    "VITAMIN" -> editor.category == "VITAMIN"
                    "MEDICATION" -> editor.category == "MEDICATION"
                    else -> editor.category !in setOf(
                        "RX_TREATMENT", "VITAMIN", "MEDICATION",
                        "SHAMPOO", "CONDITIONER", "SCALP_TONIC", "HAIR_OIL", "HAIR"
                    )
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

@Composable
private fun cautionLevelLabel(level: String): String = when (level.uppercase()) {
    "SAFE" -> stringResource(R.string.inci_caution_safe)
    "MODERATE" -> stringResource(R.string.inci_caution_moderate)
    "ACTIVE" -> stringResource(R.string.inci_caution_active)
    "CAUTION" -> stringResource(R.string.inci_caution_caution)
    else -> level
}

@Composable
private fun localizedConflictTitle(conflict: DetectedConflict): String = when (conflict.ruleId) {
    "retinoid_aha_bha" -> stringResource(R.string.inci_conflict_retinoid_aha_bha_title)
    "rx_antibiotic_strong_acid" -> stringResource(R.string.inci_conflict_rx_antibiotic_title)
    "benzoyl_peroxide_vitamin_c" -> stringResource(R.string.inci_conflict_bpo_vitc_title)
    "retinoid_benzoyl_peroxide" -> stringResource(R.string.inci_conflict_retinoid_bpo_title)
    "retinoid_direct_vitamin_c" -> stringResource(R.string.inci_conflict_retinoid_vitc_title)
    "acids_copper_peptides" -> stringResource(R.string.inci_conflict_acids_peptides_title)
    "aha_bha_stacking" -> stringResource(R.string.inci_conflict_aha_bha_title)
    else -> conflict.title
}

@Composable
private fun localizedConflictRecommendation(conflict: DetectedConflict): String = when (conflict.ruleId) {
    "retinoid_aha_bha" -> stringResource(R.string.inci_conflict_retinoid_aha_bha_rec)
    "rx_antibiotic_strong_acid" -> stringResource(R.string.inci_conflict_rx_antibiotic_rec)
    "benzoyl_peroxide_vitamin_c" -> stringResource(R.string.inci_conflict_bpo_vitc_rec)
    "retinoid_benzoyl_peroxide" -> stringResource(R.string.inci_conflict_retinoid_bpo_rec)
    "retinoid_direct_vitamin_c" -> stringResource(R.string.inci_conflict_retinoid_vitc_rec)
    "acids_copper_peptides" -> stringResource(R.string.inci_conflict_acids_peptides_rec)
    "aha_bha_stacking" -> stringResource(R.string.inci_conflict_aha_bha_rec)
    else -> conflict.recommendation
}

@Composable
private fun VitaminCalculatorCard(
    birthDateIso: String?,
    vitaminPlan: VitaminPlan?,
    onSaveBirthDate: (String?) -> Unit
) {
    var showBirthDateDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    if (showBirthDateDialog) {
        BirthDateInputDialog(
            currentIso = birthDateIso,
            onConfirm = { newIso ->
                onSaveBirthDate(newIso)
                showBirthDateDialog = false
            },
            onDismiss = { showBirthDateDialog = false }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vitamin_calculator_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vitamin_calc_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (vitaminPlan != null) {
                            stringResource(
                                R.string.vitamin_calc_age_summary,
                                vitaminPlan.ageYears,
                                vitaminPlan.coveredCount,
                                vitaminPlan.requirements.size
                            )
                        } else {
                            stringResource(R.string.vitamin_calc_set_birthday_prompt)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { showBirthDateDialog = true },
                    modifier = Modifier.testTag("vitamin_set_birthday_btn")
                ) {
                    Text(
                        text = stringResource(
                            if (birthDateIso == null) {
                                R.string.vitamin_calc_set_birthday
                            } else {
                                R.string.vitamin_calc_edit_birthday
                            }
                        )
                    )
                }
            }

            if (vitaminPlan != null) {
                val visibleItems = if (expanded) {
                    vitaminPlan.requirements
                } else {
                    vitaminPlan.requirements.take(4)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    visibleItems.forEach { req ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nutrientLabel(req.code),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (req.isCoveredOnShelf) {
                                    Text(
                                        text = stringResource(
                                            R.string.vitamin_calc_on_shelf,
                                            req.coveredByProducts.joinToString(", ")
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = req.dailyAmount,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (req.isCoveredOnShelf) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                if (vitaminPlan.requirements.size > 4) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = stringResource(
                                if (expanded) R.string.vitamin_calc_show_less else R.string.vitamin_calc_show_all
                            )
                        )
                    }
                }
            }

            // Mandatory non-medical / diet disclaimer
            Text(
                text = stringResource(R.string.vitamin_calc_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("vitamin_calc_disclaimer")
            )
        }
    }
}

@Composable
private fun BirthDateInputDialog(
    currentIso: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val parsed = remember(currentIso) { VitaminCalculator.parseBirthDate(currentIso) }
    var yearText by remember(currentIso) { mutableStateOf(parsed?.year?.toString().orEmpty()) }
    var monthText by remember(currentIso) { mutableStateOf(parsed?.monthValue?.toString()?.padStart(2, '0').orEmpty()) }
    var dayText by remember(currentIso) { mutableStateOf(parsed?.dayOfMonth?.toString()?.padStart(2, '0').orEmpty()) }

    val validatedIso = remember(yearText, monthText, dayText) {
        val y = yearText.toIntOrNull()
        val m = monthText.toIntOrNull()
        val d = dayText.toIntOrNull()
        if (y != null && m != null && d != null && y in 1905..2025) {
            val iso = VitaminCalculator.formatBirthDate(y, m, d)
            if (VitaminCalculator.computeAgeYears(iso) != null) iso else null
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_birthday_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_birthday_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) yearText = it },
                        label = { Text(stringResource(R.string.birthday_year)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("birthday_year_input")
                    )
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) monthText = it },
                        label = { Text(stringResource(R.string.birthday_month)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("birthday_month_input")
                    )
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) dayText = it },
                        label = { Text(stringResource(R.string.birthday_day)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("birthday_day_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(validatedIso) },
                enabled = validatedIso != null,
                modifier = Modifier.testTag("birthday_save_btn")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun nutrientLabel(code: NutrientCode): String = when (code) {
    NutrientCode.VITAMIN_D -> stringResource(R.string.nutrient_vitamin_d)
    NutrientCode.VITAMIN_C -> stringResource(R.string.nutrient_vitamin_c)
    NutrientCode.VITAMIN_B12 -> stringResource(R.string.nutrient_vitamin_b12)
    NutrientCode.FOLATE -> stringResource(R.string.nutrient_folate)
    NutrientCode.VITAMIN_A -> stringResource(R.string.nutrient_vitamin_a)
    NutrientCode.VITAMIN_E -> stringResource(R.string.nutrient_vitamin_e)
    NutrientCode.MAGNESIUM -> stringResource(R.string.nutrient_magnesium)
    NutrientCode.ZINC -> stringResource(R.string.nutrient_zinc)
    NutrientCode.IRON -> stringResource(R.string.nutrient_iron)
    NutrientCode.CALCIUM -> stringResource(R.string.nutrient_calcium)
    NutrientCode.OMEGA_3 -> stringResource(R.string.nutrient_omega_3)
}

private fun openPhotoInGoogleLens(
    context: Context,
    bitmap: Bitmap,
    chooserTitle: String
) {
    runCatching {
        val dir = File(context.cacheDir, "scan_tmp").apply { mkdirs() }
        val file = File(dir, "lens_query.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val candidatePackages = listOf(
            "com.google.ar.lens",
            "com.google.android.googlequicksearchbox"
        )
        for (pkg in candidatePackages) {
            val targeted = Intent(baseIntent).setPackage(pkg)
            if (targeted.resolveActivity(context.packageManager) != null) {
                context.startActivity(targeted)
                return
            }
        }
        context.startActivity(Intent.createChooser(baseIntent, chooserTitle))
    }
}
