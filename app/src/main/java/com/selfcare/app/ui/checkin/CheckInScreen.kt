package com.selfcare.app.ui.checkin

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.R
import com.selfcare.app.domain.cv.LightingQualityChecker
import com.selfcare.app.domain.cv.LightingWarningReason
import com.selfcare.app.ui.icons.AppIcons
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun CheckInScreen(
    viewModel: CheckInViewModel,
    modifier: Modifier = Modifier,
    onCaptureFallbackBitmap: (() -> Bitmap)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateCameraPermission(granted)
    }

    LaunchedEffect(context) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.updateCameraPermission(granted)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.checkin_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (uiState.isHaircutReminderTriggered) {
            HaircutReminderBanner(
                growthPercent = uiState.currentHairGrowthPercent,
                thresholdPercent = uiState.haircutThresholdPercent
            )
        }

        if (!uiState.hasCameraPermission) {
            MissingCameraPermissionCard(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        } else {
            CameraPreviewCard(
                cameraUnavailable = uiState.cameraUnavailable,
                lightingReason = uiState.lightingAssessment?.warningReason,
                onImageCaptureReady = { imageCapture = it },
                onCameraUnavailable = { viewModel.setCameraUnavailable(true) },
                onLightingSampled = { pixels, width, height ->
                    val assessment = LightingQualityChecker.assess(
                        pixels = pixels,
                        width = width,
                        height = height
                    )
                    viewModel.onLightingAssessed(assessment)
                },
                cameraExecutor = cameraExecutor
            )

            val warningReason = uiState.lightingAssessment?.warningReason
            if (warningReason != null && warningReason != LightingWarningReason.OK) {
                LightingWarningBanner(reason = warningReason)
            }

            Button(
                onClick = {
                    val activeCapture = imageCapture
                    if (activeCapture != null && !uiState.cameraUnavailable) {
                        activeCapture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = try {
                                        image.toOrientedBitmap()
                                    } finally {
                                        image.close()
                                    }
                                    viewModel.submitBitmapCheckIn(bitmap)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    val fallback = onCaptureFallbackBitmap?.invoke()
                                        ?: createFallbackCheckInBitmap()
                                    viewModel.submitBitmapCheckIn(fallback)
                                }
                            }
                        )
                    } else {
                        val fallback = onCaptureFallbackBitmap?.invoke()
                            ?: createFallbackCheckInBitmap()
                        viewModel.submitBitmapCheckIn(fallback)
                    }
                },
                enabled = !uiState.isCapturing,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("checkin_capture_btn")
            ) {
                if (uiState.isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.checkin_analyzing))
                } else {
                    Icon(
                        imageVector = AppIcons.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.checkin_take_photo))
                }
            }
        }

        uiState.errorMessage?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        AutoDeletePrivacyCard(
            enabled = uiState.autoDeletePhotos,
            onToggle = { viewModel.setAutoDeleteCheckInPhotos(it) }
        )

        uiState.lastResult?.let { result ->
            CheckInResultSummaryCard(
                underEyeDelta = result.checkIn.underEyeDeltaLab,
                redness = result.checkIn.skinRednessIndex,
                texture = result.checkIn.skinTextureVariance,
                lipDryness = result.checkIn.lipDrynessScore,
                lightingScore = result.checkIn.lightingQualityScore
            )
        }

        HaircutBaselineCard(
            hasBaseline = uiState.activeBaseline != null,
            growthPercent = uiState.currentHairGrowthPercent,
            thresholdPercent = uiState.haircutThresholdPercent,
            baselineResetAtMillis = uiState.baselineResetAtMillis,
            onResetBaseline = { viewModel.resetHaircutBaseline() }
        )
    }
}

@Composable
private fun MissingCameraPermissionCard(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("checkin_permission_required"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = AppIcons.Camera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.checkin_permission_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.checkin_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRequestPermission) {
                Text(text = stringResource(R.string.checkin_grant_permission))
            }
        }
    }
}

@Composable
private fun CameraPreviewCard(
    cameraUnavailable: Boolean,
    lightingReason: LightingWarningReason?,
    onImageCaptureReady: (ImageCapture?) -> Unit,
    onCameraUnavailable: () -> Unit,
    onLightingSampled: (IntArray, Int, Int) -> Unit,
    cameraExecutor: java.util.concurrent.ExecutorService
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (!cameraUnavailable) {
            val previewView = remember {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }

            DisposableEffect(lifecycleOwner, previewView) {
                var cameraProvider: ProcessCameraProvider? = null
                var disposed = false
                try {
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        if (disposed) return@addListener
                        try {
                            val provider = future.get()
                            cameraProvider = provider
                            val preview = Preview.Builder().build().apply {
                                surfaceProvider = previewView.surfaceProvider
                            }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()

                            var lastAnalyzedMs = 0L
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .apply {
                                    setAnalyzer(cameraExecutor) { image ->
                                        val now = System.currentTimeMillis()
                                        if (now - lastAnalyzedMs >= 400L) {
                                            lastAnalyzedMs = now
                                            extractLuminanceSample(image)?.let { sample ->
                                                onLightingSampled(
                                                    sample.pixels,
                                                    sample.width,
                                                    sample.height
                                                )
                                            }
                                        }
                                        image.close()
                                    }
                                }

                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                capture,
                                analysis
                            )
                            onImageCaptureReady(capture)
                        } catch (_: Throwable) {
                            onImageCaptureReady(null)
                            onCameraUnavailable()
                        }
                    }, ContextCompat.getMainExecutor(context))
                } catch (_: Throwable) {
                    onImageCaptureReady(null)
                    onCameraUnavailable()
                }

                onDispose {
                    disposed = true
                    runCatching { cameraProvider?.unbindAll() }
                    onImageCaptureReady(null)
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = AppIcons.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.checkin_camera_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Guided framing overlay
        val guideColor = if (lightingReason == null || lightingReason == LightingWarningReason.OK) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("checkin_framing_overlay")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ovalW = size.width * 0.62f
                val ovalH = size.height * 0.66f
                val left = (size.width - ovalW) / 2f
                val top = (size.height - ovalH) / 2f
                drawRoundRect(
                    color = guideColor.copy(alpha = 0.85f),
                    topLeft = Offset(left, top),
                    size = Size(ovalW, ovalH),
                    cornerRadius = CornerRadius(ovalW / 2f, ovalW / 2f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.checkin_framing_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun LightingWarningBanner(reason: LightingWarningReason) {
    val messageRes = when (reason) {
        LightingWarningReason.TOO_DARK -> R.string.checkin_lighting_too_dark
        LightingWarningReason.TOO_BRIGHT -> R.string.checkin_lighting_too_bright
        LightingWarningReason.UNEVEN -> R.string.checkin_lighting_uneven
        LightingWarningReason.OK -> R.string.checkin_lighting_warning
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("checkin_lighting_warning")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = AppIcons.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun AutoDeletePrivacyCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_auto_delete_photo),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.checkin_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("checkin_auto_delete_toggle")
            )
        }
    }
}

@Composable
private fun CheckInResultSummaryCard(
    underEyeDelta: Float,
    redness: Float,
    texture: Float,
    lipDryness: Float,
    lightingScore: Float
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.checkin_last_result_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    R.string.checkin_last_result_metrics,
                    String.format(Locale.US, "%.1f", underEyeDelta),
                    String.format(Locale.US, "%.2f", redness),
                    String.format(Locale.US, "%.1f", texture),
                    String.format(Locale.US, "%.2f", lipDryness),
                    (lightingScore * 100f).toInt()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.trends_baseline_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HaircutReminderBanner(
    growthPercent: Float,
    thresholdPercent: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("haircut_reminder_banner")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = AppIcons.Haircut,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stringResource(
                    R.string.haircut_reminder_banner_text,
                    growthPercent.toInt(),
                    thresholdPercent
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun HaircutBaselineCard(
    hasBaseline: Boolean,
    growthPercent: Float,
    thresholdPercent: Int,
    baselineResetAtMillis: Long?,
    onResetBaseline: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Haircut,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.checkin_haircut_section_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = if (hasBaseline) {
                    stringResource(
                        R.string.checkin_haircut_status,
                        String.format(Locale.US, "%.1f", growthPercent),
                        thresholdPercent
                    )
                } else {
                    stringResource(R.string.checkin_haircut_no_baseline)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (baselineResetAtMillis != null) {
                Text(
                    text = stringResource(R.string.checkin_haircut_reset_confirmed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedButton(
                onClick = onResetBaseline,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("checkin_haircut_reset_btn")
            ) {
                Icon(
                    imageVector = AppIcons.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.checkin_haircut_reset))
            }
        }
    }
}

private data class LuminanceSample(
    val pixels: IntArray,
    val width: Int,
    val height: Int
)

private fun extractLuminanceSample(image: ImageProxy): LuminanceSample? {
    val plane = image.planes.firstOrNull() ?: return null
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null

    val step = 4
    val outW = (width + step - 1) / step
    val outH = (height + step - 1) / step
    val pixels = IntArray(outW * outH)
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val limit = buffer.limit()

    var outIdx = 0
    for (y in 0 until height step step) {
        val rowBase = y * rowStride
        for (x in 0 until width step step) {
            val offset = rowBase + x * pixelStride
            val yVal = if (offset in 0 until limit) buffer.get(offset).toInt() and 0xFF else 128
            pixels[outIdx++] = (0xFF shl 24) or (yVal shl 16) or (yVal shl 8) or yVal
        }
    }
    return LuminanceSample(pixels = pixels, width = outW, height = outH)
}

private fun ImageProxy.toOrientedBitmap(): Bitmap {
    val raw = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

private fun createFallbackCheckInBitmap(): Bitmap {
    return Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply {
        val canvas = AndroidCanvas(this)
        canvas.drawColor(AndroidColor.rgb(185, 145, 130))
    }
}
