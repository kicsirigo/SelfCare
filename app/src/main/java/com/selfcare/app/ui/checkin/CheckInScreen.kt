package com.selfcare.app.ui.checkin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.hardware.camera2.CaptureRequest
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import com.selfcare.app.domain.cv.CheckInDailyContext
import com.selfcare.app.domain.cv.ImprovementTipCode
import com.selfcare.app.domain.cv.LightingQualityChecker
import com.selfcare.app.domain.cv.LightingWarningReason
import com.selfcare.app.domain.cv.MetricInterpreter
import com.selfcare.app.domain.cv.MetricStatusLevel
import com.selfcare.app.domain.cv.ShelfProductSummary
import com.selfcare.app.domain.cv.TipVariant
import com.selfcare.app.ui.components.HairHealthAnalysisCard
import com.selfcare.app.ui.components.HeroScoreRing
import com.selfcare.app.ui.components.MetricGaugeRow
import com.selfcare.app.ui.components.statusLevelColor
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
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Snapchat-style screen flash: boost window brightness to 100% while enabled and restore on exit
    DisposableEffect(uiState.screenFlashEnabled, context) {
        val window = context.findActivity()?.window
        val previousBrightness = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (uiState.screenFlashEnabled && window != null) {
            val lp = window.attributes
            lp.screenBrightness = 1.0f
            window.attributes = lp
        }
        onDispose {
            if (window != null) {
                val lp = window.attributes
                lp.screenBrightness = previousBrightness
                window.attributes = lp
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.checkin_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = uiState.screenFlashEnabled,
                    onClick = { viewModel.toggleScreenFlash(!uiState.screenFlashEnabled) },
                    label = { Text(text = stringResource(R.string.checkin_screen_flash)) },
                    leadingIcon = {
                        Icon(
                            imageVector = AppIcons.Sun,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("checkin_flash_toggle")
                )
            }

            if (uiState.isHaircutReminderTriggered) {
                HaircutReminderBanner(
                    growthPercent = uiState.currentHairGrowthPercent,
                    thresholdPercent = uiState.haircutThresholdPercent
                )
            }

            if (uiState.isShaveReminderDue) {
                val shaveReason = if (uiState.daysSinceLastShave != null && uiState.daysSinceLastShave!! >= uiState.shaveIntervalDays) {
                    stringResource(R.string.shave_days_since, uiState.daysSinceLastShave!!, uiState.shaveIntervalDays)
                } else {
                    stringResource(R.string.metric_stubble_heavy)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shave_reminder_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Sparkle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.shave_reminder_banner, shaveReason),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
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
                    isFaceDetected = uiState.isFaceDetected,
                    liveBrightnessPercent = uiState.liveBrightnessPercent,
                    screenFlashEnabled = uiState.screenFlashEnabled,
                    onImageCaptureReady = { imageCapture = it },
                    onCameraUnavailable = { viewModel.setCameraUnavailable(true) },
                    onLiveFrameSampled = { bitmap, pixels, width, height ->
                        val assessment = LightingQualityChecker.assess(
                            pixels = pixels,
                            width = width,
                            height = height
                        )
                        viewModel.onLiveFrameSampled(bitmap, assessment)
                    },
                    cameraExecutor = cameraExecutor
                )

                val canCapture = !uiState.isCapturing

                Button(
                    onClick = {
                        val activeCapture = imageCapture
                        fun triggerActualCapture() {
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
                                            viewModel.setFlashFiring(false)
                                            viewModel.submitBitmapCheckIn(bitmap)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            viewModel.setFlashFiring(false)
                                            val fallback = onCaptureFallbackBitmap?.invoke()
                                                ?: createFallbackCheckInBitmap()
                                            viewModel.submitBitmapCheckIn(fallback)
                                        }
                                    }
                                )
                            } else {
                                viewModel.setFlashFiring(false)
                                val fallback = onCaptureFallbackBitmap?.invoke()
                                    ?: createFallbackCheckInBitmap()
                                viewModel.submitBitmapCheckIn(fallback)
                            }
                        }

                        if (uiState.screenFlashEnabled && activeCapture != null && !uiState.cameraUnavailable) {
                            coroutineScope.launch {
                                viewModel.setFlashFiring(true)
                                delay(220L)
                                triggerActualCapture()
                            }
                        } else {
                            triggerActualCapture()
                        }
                    },
                    enabled = canCapture,
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
                lightingScore = result.checkIn.lightingQualityScore,
                blemishScore = result.checkIn.blemishScore,
                blemishCount = result.checkIn.blemishCount,
                stubbleScore = result.checkIn.stubbleScore,
                shaveDue = uiState.isShaveReminderDue
            )
            HairHealthAnalysisCard(
                summary = MetricInterpreter.interpretHairDetails(
                    crownVolumeScore = result.analysis.hairCrownVolumeScore,
                    shineScore = result.analysis.hairShineScore,
                    foreheadCoverage = result.analysis.hairForeheadCoverage,
                    templeSymmetryScore = result.analysis.hairTempleSymmetryScore
                ),
                growthPercent = uiState.currentHairGrowthPercent,
                thresholdPercent = uiState.haircutThresholdPercent
            )
        } ?: uiState.latestCheckIn?.let { latest ->
            CheckInResultSummaryCard(
                underEyeDelta = latest.underEyeDeltaLab,
                redness = latest.skinRednessIndex,
                texture = latest.skinTextureVariance,
                lipDryness = latest.lipDrynessScore,
                lightingScore = latest.lightingQualityScore,
                blemishScore = latest.blemishScore,
                blemishCount = latest.blemishCount,
                stubbleScore = latest.stubbleScore,
                shaveDue = uiState.isShaveReminderDue,
                shelfProducts = uiState.shelfProducts,
                dailyContext = uiState.dailyContext
            )
            HairHealthAnalysisCard(
                summary = MetricInterpreter.interpretHairDetails(
                    crownVolumeScore = (latest.hairToFaceRatio * 1.4f).coerceIn(0f, 1f),
                    shineScore = 0.76f,
                    foreheadCoverage = (latest.hairToFaceRatio * 0.5f).coerceIn(0f, 1f),
                    templeSymmetryScore = 0.88f
                ),
                growthPercent = uiState.currentHairGrowthPercent,
                thresholdPercent = uiState.haircutThresholdPercent,
                hairSupportProduct = MetricInterpreter.findHairSupportProduct(uiState.shelfProducts)
            )
        }

        ShaveTrackerCard(
            daysSinceLastShave = uiState.daysSinceLastShave,
            shaveIntervalDays = uiState.shaveIntervalDays,
            onJustShaved = { viewModel.markShavedToday() }
        )

        HaircutBaselineCard(
            hasBaseline = uiState.activeBaseline != null,
            growthPercent = uiState.currentHairGrowthPercent,
            thresholdPercent = uiState.haircutThresholdPercent,
            baselineResetAtMillis = uiState.baselineResetAtMillis,
            onResetBaseline = { viewModel.resetHaircutBaseline() }
        )
        }

        // Snapchat-style full-screen white flash burst during capture
        if (uiState.isFlashFiring) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .testTag("checkin_flash_overlay")
            )
        }
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

@OptIn(ExperimentalCamera2Interop::class)
@Composable
private fun CameraPreviewCard(
    cameraUnavailable: Boolean,
    lightingReason: LightingWarningReason?,
    isFaceDetected: Boolean?,
    liveBrightnessPercent: Int?,
    screenFlashEnabled: Boolean,
    onImageCaptureReady: (ImageCapture?) -> Unit,
    onCameraUnavailable: () -> Unit,
    onLiveFrameSampled: (Bitmap, IntArray, Int, Int) -> Unit,
    cameraExecutor: java.util.concurrent.ExecutorService
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (screenFlashEnabled) {
                    Modifier.border(22.dp, Color.White, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
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
                            val captureBuilder = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            // Minimize OEM skin-smoothing / beauty post-processing for honest CV skin metrics
                            runCatching {
                                Camera2Interop.Extender(captureBuilder)
                                    .setCaptureRequestOption(
                                        CaptureRequest.NOISE_REDUCTION_MODE,
                                        CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
                                    )
                                    .setCaptureRequestOption(
                                        CaptureRequest.EDGE_MODE,
                                        CaptureRequest.EDGE_MODE_OFF
                                    )
                                    .setCaptureRequestOption(
                                        CaptureRequest.CONTROL_SCENE_MODE,
                                        CaptureRequest.CONTROL_SCENE_MODE_DISABLED
                                    )
                                    .setCaptureRequestOption(
                                        CaptureRequest.CONTROL_EFFECT_MODE,
                                        CaptureRequest.CONTROL_EFFECT_MODE_OFF
                                    )
                            }
                            val capture = captureBuilder.build()

                            var lastAnalyzedMs = 0L
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .apply {
                                    setAnalyzer(cameraExecutor) { image ->
                                        val now = System.currentTimeMillis()
                                        if (now - lastAnalyzedMs >= 350L) {
                                            lastAnalyzedMs = now
                                            val sample = extractLuminanceSample(image)
                                            if (sample != null) {
                                                val oriented = runCatching { image.toOrientedBitmap() }.getOrNull()
                                                if (oriented != null) {
                                                    onLiveFrameSampled(
                                                        oriented,
                                                        sample.pixels,
                                                        sample.width,
                                                        sample.height
                                                    )
                                                }
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

        // Guided framing overlay: green when face detected, red when no face detected (no brightness restriction)
        val guideColor = when (isFaceDetected) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.outline
        }

        val infiniteTransition = rememberInfiniteTransition(label = "checkin_scanner")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "guide_pulse"
        )
        val scanProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scan_line"
        )

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
                    color = guideColor.copy(alpha = pulseAlpha),
                    topLeft = Offset(left, top),
                    size = Size(ovalW, ovalH),
                    cornerRadius = CornerRadius(ovalW / 2f, ovalW / 2f),
                    style = Stroke(width = 3.dp.toPx())
                )
                val lineY = top + ovalH * scanProgress
                drawLine(
                    color = guideColor.copy(alpha = 0.55f),
                    start = Offset(left + 16.dp.toPx(), lineY),
                    end = Offset(left + ovalW - 16.dp.toPx(), lineY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Top live telemetry HUD: Face detection status + live brightness %
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = if (screenFlashEnabled) 26.dp else 12.dp, vertical = if (screenFlashEnabled) 26.dp else 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = when (isFaceDetected) {
                        true -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                        false -> MaterialTheme.colorScheme.error.copy(alpha = 0.88f)
                        null -> MaterialTheme.colorScheme.surface.copy(alpha = 0.80f)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("checkin_face_status_badge")
                ) {
                    Text(
                        text = when (isFaceDetected) {
                            true -> stringResource(R.string.checkin_face_detected)
                            false -> stringResource(R.string.checkin_no_face_detected)
                            null -> stringResource(R.string.checkin_framing_hint)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when (isFaceDetected) {
                            true -> MaterialTheme.colorScheme.onPrimary
                            false -> MaterialTheme.colorScheme.onError
                            null -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (liveBrightnessPercent != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("checkin_brightness_badge")
                    ) {
                        Text(
                            text = stringResource(R.string.checkin_brightness_live, liveBrightnessPercent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(if (screenFlashEnabled) 26.dp else 12.dp)
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
    lightingScore: Float,
    blemishScore: Float = 0f,
    blemishCount: Int = 0,
    stubbleScore: Float = 0f,
    shaveDue: Boolean = false,
    shelfProducts: List<ShelfProductSummary> = emptyList(),
    dailyContext: CheckInDailyContext = CheckInDailyContext()
) {
    val underEye = MetricInterpreter.interpretUnderEye(underEyeDelta)
    val rednessEval = MetricInterpreter.interpretRedness(redness)
    val textureEval = MetricInterpreter.interpretTexture(texture)
    val lipEval = MetricInterpreter.interpretLipDryness(lipDryness)
    val blemishEval = MetricInterpreter.interpretBlemish(blemishScore, blemishCount)
    val stubbleEval = MetricInterpreter.interpretStubble(stubbleScore)
    val ratingSummary = MetricInterpreter.computeObjectiveRating(
        underEyeDelta = underEyeDelta,
        redness = redness,
        texture = texture,
        lipDryness = lipDryness,
        blemishScore = blemishScore,
        blemishCount = blemishCount,
        stubbleScore = stubbleScore,
        shaveDue = shaveDue,
        shelfProducts = shelfProducts,
        dailyContext = dailyContext
    )
    val ratingText = String.format(Locale.US, "%.1f", ratingSummary.scoreOutOfTen)
    val ratingAccent = statusLevelColor(ratingSummary.level)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroScoreRing(
                    progress = (ratingSummary.scoreOutOfTen / 10f).coerceIn(0.05f, 1f),
                    centerValue = ratingText,
                    centerSubtitle = "/ 10",
                    ringSize = 92.dp,
                    accentColor = ratingAccent
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.checkin_rating_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.checkin_rating_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Actionable improvement tips ranked by lowest metrics
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("checkin_advice_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.checkin_advice_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (ratingSummary.usedShelfProductCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.checkin_advice_shelf_badge,
                                    ratingSummary.usedShelfProductCount
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    ratingSummary.contextualTips.forEach { tip ->
                        val productName = tip.matchedProductName
                        val tipText = when (tip.code) {
                            ImprovementTipCode.ISOTRETINOIN_VITAMIN_A_WARNING -> {
                                stringResource(
                                    R.string.tip_isotretinoin_vitamin_a_warning,
                                    productName.orEmpty()
                                )
                            }
                            ImprovementTipCode.BLEMISHES -> when {
                                tip.variant == TipVariant.SYSTEMIC_MEDICATION_CARE && productName != null ->
                                    stringResource(R.string.tip_blemishes_systemic_med, productName)
                                tip.variant == TipVariant.HAS_TARGETED_PRODUCT && productName != null ->
                                    stringResource(R.string.tip_blemishes_with_product, productName)
                                tip.variant == TipVariant.HAS_SUPPLEMENT_SUPPORT && productName != null ->
                                    stringResource(R.string.tip_blemishes_with_supplement, productName)
                                else -> stringResource(R.string.tip_blemishes)
                            }
                            ImprovementTipCode.REDNESS -> when {
                                tip.variant == TipVariant.SYSTEMIC_MEDICATION_CARE && productName != null ->
                                    stringResource(R.string.tip_redness_systemic_med, productName)
                                tip.variant == TipVariant.ACTIVE_IRRITATION_CAUTION && productName != null ->
                                    stringResource(R.string.tip_redness_active_caution, productName)
                                tip.variant == TipVariant.HAS_TARGETED_PRODUCT && productName != null ->
                                    stringResource(R.string.tip_redness_with_product, productName)
                                else -> stringResource(R.string.tip_redness)
                            }
                            ImprovementTipCode.TEXTURE -> when {
                                tip.variant == TipVariant.SYSTEMIC_MEDICATION_CARE && productName != null ->
                                    stringResource(R.string.tip_texture_systemic_med, productName)
                                tip.variant == TipVariant.HAS_TARGETED_PRODUCT && productName != null ->
                                    stringResource(R.string.tip_texture_with_product, productName)
                                tip.variant == TipVariant.HAS_SUPPLEMENT_SUPPORT && productName != null ->
                                    stringResource(R.string.tip_texture_with_supplement, productName)
                                else -> stringResource(R.string.tip_texture)
                            }
                            ImprovementTipCode.UNDER_EYE -> when {
                                tip.variant == TipVariant.HAS_TARGETED_PRODUCT && productName != null ->
                                    stringResource(R.string.tip_under_eye_with_product, productName)
                                tip.variant == TipVariant.HAS_SUPPLEMENT_SUPPORT && productName != null ->
                                    stringResource(R.string.tip_under_eye_with_supplement, productName)
                                else -> stringResource(R.string.tip_under_eye)
                            }
                            ImprovementTipCode.LIPS -> when {
                                tip.variant == TipVariant.SYSTEMIC_MEDICATION_CARE && productName != null ->
                                    stringResource(R.string.tip_lips_systemic_med, productName)
                                tip.variant == TipVariant.HYGIENE_UNLOGGED_REMINDER && productName != null ->
                                    stringResource(R.string.tip_lips_unlogged_with_product, productName)
                                tip.variant == TipVariant.HYGIENE_UNLOGGED_REMINDER ->
                                    stringResource(R.string.tip_lips_unlogged)
                                tip.variant == TipVariant.HAS_TARGETED_PRODUCT && productName != null ->
                                    stringResource(R.string.tip_lips_with_product, productName)
                                else -> stringResource(R.string.tip_lips)
                            }
                            ImprovementTipCode.SHAVE -> when {
                                tip.variant == TipVariant.ACTIVE_IRRITATION_CAUTION && productName != null ->
                                    stringResource(R.string.tip_shave_with_actives, productName)
                                tip.variant == TipVariant.HAS_TARGETED_PRODUCT && productName != null ->
                                    stringResource(R.string.tip_shave_with_aftershave, productName)
                                else -> stringResource(R.string.tip_shave)
                            }
                            ImprovementTipCode.SPF_WITH_ACTIVES -> {
                                if (productName != null) {
                                    stringResource(R.string.tip_spf_with_actives, productName)
                                } else {
                                    stringResource(R.string.tip_spf_generic)
                                }
                            }
                            ImprovementTipCode.ROUTINE_REMINDER -> stringResource(R.string.tip_routine_reminder)
                            ImprovementTipCode.MAINTAIN -> stringResource(R.string.tip_maintain)
                        }
                        Text(
                            text = "• $tipText",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tip.code == ImprovementTipCode.ISOTRETINOIN_VITAMIN_A_WARNING) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            MetricGaugeRow(
                title = stringResource(R.string.metric_stubble_title),
                assessment = stubbleEval,
                explanation = stringResource(
                    when (stubbleEval.level) {
                        MetricStatusLevel.OPTIMAL -> R.string.metric_stubble_clean
                        MetricStatusLevel.GOOD -> R.string.metric_stubble_light
                        MetricStatusLevel.MODERATE -> R.string.metric_stubble_medium
                        MetricStatusLevel.HIGH -> R.string.metric_stubble_heavy
                    }
                )
            )

            MetricGaugeRow(
                title = stringResource(R.string.trends_metric_blemish),
                assessment = blemishEval,
                explanation = stringResource(
                    when (blemishEval.level) {
                        MetricStatusLevel.OPTIMAL -> R.string.metric_blemish_desc_optimal
                        MetricStatusLevel.GOOD -> R.string.metric_blemish_desc_good
                        MetricStatusLevel.MODERATE -> R.string.metric_blemish_desc_moderate
                        MetricStatusLevel.HIGH -> R.string.metric_blemish_desc_high
                    },
                    blemishCount
                )
            )

            MetricGaugeRow(
                title = stringResource(R.string.trends_metric_under_eye),
                assessment = underEye,
                explanation = stringResource(
                    when (underEye.level) {
                        MetricStatusLevel.OPTIMAL -> R.string.metric_under_eye_desc_optimal
                        MetricStatusLevel.GOOD -> R.string.metric_under_eye_desc_good
                        MetricStatusLevel.MODERATE -> R.string.metric_under_eye_desc_moderate
                        MetricStatusLevel.HIGH -> R.string.metric_under_eye_desc_high
                    }
                ),
                technicalDetail = stringResource(R.string.metric_score_label, underEye.scorePercent) +
                    " (DeltaE ${String.format(Locale.US, "%.1f", underEyeDelta)})"
            )

            MetricGaugeRow(
                title = stringResource(R.string.trends_metric_redness),
                assessment = rednessEval,
                explanation = stringResource(
                    when (rednessEval.level) {
                        MetricStatusLevel.OPTIMAL -> R.string.metric_redness_desc_optimal
                        MetricStatusLevel.GOOD -> R.string.metric_redness_desc_good
                        MetricStatusLevel.MODERATE -> R.string.metric_redness_desc_moderate
                        MetricStatusLevel.HIGH -> R.string.metric_redness_desc_high
                    }
                ),
                technicalDetail = stringResource(R.string.metric_score_label, rednessEval.scorePercent) +
                    " (${String.format(Locale.US, "%.2f", redness)})"
            )

            MetricGaugeRow(
                title = stringResource(R.string.trends_metric_texture),
                assessment = textureEval,
                explanation = stringResource(
                    when (textureEval.level) {
                        MetricStatusLevel.OPTIMAL -> R.string.metric_texture_desc_optimal
                        MetricStatusLevel.GOOD -> R.string.metric_texture_desc_good
                        MetricStatusLevel.MODERATE -> R.string.metric_texture_desc_moderate
                        MetricStatusLevel.HIGH -> R.string.metric_texture_desc_high
                    }
                ),
                technicalDetail = stringResource(R.string.metric_score_label, textureEval.scorePercent) +
                    " (${String.format(Locale.US, "%.1f", texture)})"
            )

            MetricGaugeRow(
                title = stringResource(R.string.trends_metric_lip),
                assessment = lipEval,
                explanation = stringResource(
                    when (lipEval.level) {
                        MetricStatusLevel.OPTIMAL -> R.string.metric_lip_desc_optimal
                        MetricStatusLevel.GOOD -> R.string.metric_lip_desc_good
                        MetricStatusLevel.MODERATE -> R.string.metric_lip_desc_moderate
                        MetricStatusLevel.HIGH -> R.string.metric_lip_desc_high
                    }
                ),
                technicalDetail = stringResource(R.string.metric_score_label, lipEval.scorePercent) +
                    " (${String.format(Locale.US, "%.2f", lipDryness)})"
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
private fun ShaveTrackerCard(
    daysSinceLastShave: Int?,
    shaveIntervalDays: Int,
    onJustShaved: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("checkin_shave_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Sparkle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.shave_card_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = if (daysSinceLastShave != null) {
                    stringResource(R.string.shave_days_since, daysSinceLastShave, shaveIntervalDays)
                } else {
                    stringResource(R.string.shave_no_log_yet)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onJustShaved,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("checkin_just_shaved_btn")
            ) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.shave_just_shaved_btn))
            }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
