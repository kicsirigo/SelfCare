package com.selfcare.app.ui.nutrition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.selfcare.app.R
import com.selfcare.app.ui.icons.AppIcons
import com.selfcare.app.ui.theme.NeonGreen
import java.util.concurrent.Executors

/**
 * Full-screen in-app CameraX capture dialog for Meal Photo AI, Nutrition Label Scan, and Barcode Scan.
 * Clean rounded camera frame with no inner guide lines, torch toggle, and in-memory Bitmap capture.
 */
@Composable
fun InAppFoodCameraDialog(
    mode: String,
    onDismiss: () -> Unit,
    onCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
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

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    val titleRes = when (mode) {
        "LABEL" -> R.string.nutrition_label_scan_btn
        "BARCODE" -> R.string.nutrition_barcode_btn
        "OCR" -> R.string.shelf_scan_label
        else -> R.string.nutrition_meal_photo_ai
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("in_app_food_camera_dialog"),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar: Title + Torch + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasCameraPermission && boundCamera != null) {
                            OutlinedButton(
                                onClick = {
                                    val next = !torchEnabled
                                    torchEnabled = next
                                    boundCamera?.cameraControl?.enableTorch(next)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (torchEnabled) NeonGreen else Color.White.copy(alpha = 0.4f)
                                )
                            ) {
                                Text(
                                    text = stringResource(
                                        if (torchEnabled) R.string.nutrition_camera_torch_on
                                        else R.string.nutrition_camera_torch_off
                                    ),
                                    color = if (torchEnabled) NeonGreen else Color.White
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("food_camera_close_btn")
                        ) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Center: Clean Rounded Camera Preview Frame (no inner guide lines)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            width = 2.dp,
                            color = NeonGreen.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .background(Color(0xFF0A0A0A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!hasCameraPermission) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.nutrition_camera_permission_needed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text(stringResource(R.string.nutrition_camera_grant_permission))
                            }
                        }
                    } else {
                        val previewView = remember {
                            PreviewView(context).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                        }

                        DisposableEffect(lifecycleOwner) {
                            var provider: ProcessCameraProvider? = null
                            val future = ProcessCameraProvider.getInstance(context)
                            future.addListener(
                                {
                                    try {
                                        val camProvider = future.get()
                                        provider = camProvider
                                        val preview = Preview.Builder().build().apply {
                                            surfaceProvider = previewView.surfaceProvider
                                        }
                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                            .build()
                                        camProvider.unbindAll()
                                        val cam = camProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            capture
                                        )
                                        boundCamera = cam
                                        imageCapture = capture
                                    } catch (_: Exception) {
                                        imageCapture = null
                                        boundCamera = null
                                    }
                                },
                                ContextCompat.getMainExecutor(context)
                            )
                            onDispose {
                                boundCamera?.cameraControl?.enableTorch(false)
                                provider?.unbindAll()
                            }
                        }

                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Bottom Shutter Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = {
                            val cap = imageCapture ?: return@Surface
                            if (isCapturing) return@Surface
                            isCapturing = true
                            cap.takePicture(
                                cameraExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bmp = try {
                                            decodeOrientedFoodBitmap(image)
                                        } finally {
                                            image.close()
                                        }
                                        ContextCompat.getMainExecutor(context).execute {
                                            isCapturing = false
                                            if (bmp != null) {
                                                onCaptured(bmp)
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        ContextCompat.getMainExecutor(context).execute {
                                            isCapturing = false
                                        }
                                    }
                                }
                            )
                        },
                        enabled = hasCameraPermission && imageCapture != null && !isCapturing,
                        shape = CircleShape,
                        color = NeonGreen,
                        border = BorderStroke(4.dp, Color.White),
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("food_camera_shutter_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = AppIcons.Camera,
                                    contentDescription = stringResource(R.string.nutrition_camera_capture),
                                    tint = Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeOrientedFoodBitmap(image: ImageProxy): Bitmap? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return try {
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
            if (it !== raw) raw.recycle()
        }
    } catch (_: Exception) {
        raw
    }
}
