package com.selfcare.app.ui.shelf

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.selfcare.app.R
import com.selfcare.app.ui.icons.AppIcons
import java.util.concurrent.Executors

/**
 * In-app CameraX rear-camera scanner for products with live on-device feedback
 * showing when a product label is clearly visible inside the frame.
 */
@Composable
fun ProductLiveScannerView(
    liveProductDetected: Boolean,
    isGeminiVisionConfigured: Boolean,
    onLiveFrame: (Bitmap) -> Unit,
    onCaptureBitmap: (Bitmap) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("shelf_live_scanner_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.shelf_live_scanner_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("shelf_live_scanner_close")
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.action_close)
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isGeminiVisionConfigured) AppIcons.Sparkle else AppIcons.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(
                        if (isGeminiVisionConfigured) {
                            R.string.shelf_gemini_active_badge
                        } else {
                            R.string.shelf_offline_ocr_badge
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.shelf_camera_permission_needed),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(text = stringResource(R.string.checkin_grant_permission))
                    }
                }
            }
        } else {
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
                                                if (now - lastAnalyzedMs >= 550L) {
                                                    lastAnalyzedMs = now
                                                    runCatching { image.toScannerBitmap() }
                                                        .getOrNull()
                                                        ?.let { bmp -> onLiveFrame(bmp) }
                                                }
                                                image.close()
                                            }
                                        }

                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        capture,
                                        analysis
                                    )
                                    imageCapture = capture
                                } catch (_: Throwable) {
                                    imageCapture = null
                                    cameraUnavailable = true
                                }
                            }, ContextCompat.getMainExecutor(context))
                        } catch (_: Throwable) {
                            imageCapture = null
                            cameraUnavailable = true
                        }

                        onDispose {
                            disposed = true
                            runCatching { cameraProvider?.unbindAll() }
                            imageCapture = null
                        }
                    }

                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Live product viewfinder frame
                val frameColor = if (liveProductDetected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxW = size.width * 0.78f
                    val boxH = size.height * 0.62f
                    val left = (size.width - boxW) / 2f
                    val top = (size.height - boxH) / 2f
                    drawRoundRect(
                        color = frameColor,
                        topLeft = Offset(left, top),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                        style = Stroke(width = if (liveProductDetected) 3.5.dp.toPx() else 2.dp.toPx())
                    )
                }

                Surface(
                    color = if (liveProductDetected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                        .testTag("shelf_live_status_badge")
                ) {
                    Text(
                        text = stringResource(
                            if (liveProductDetected) {
                                R.string.shelf_live_product_detected
                            } else {
                                R.string.shelf_live_hold_product
                            }
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (liveProductDetected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        val capture = imageCapture
                        if (capture != null && !cameraUnavailable) {
                            capture.takePicture(
                                cameraExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bmp = try {
                                            image.toScannerBitmap()
                                        } finally {
                                            image.close()
                                        }
                                        onCaptureBitmap(bmp)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        onClose()
                                    }
                                }
                            )
                        } else {
                            onClose()
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("shelf_live_capture_btn")
                ) {
                    Icon(
                        imageVector = AppIcons.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.shelf_live_capture_btn))
                }
            }
        }
    }
}

private fun ImageProxy.toScannerBitmap(): Bitmap {
    val raw = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (rotated !== raw) {
        raw.recycle()
    }
    return rotated
}
