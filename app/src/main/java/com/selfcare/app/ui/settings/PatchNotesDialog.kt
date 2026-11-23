package com.selfcare.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class PatchNoteRelease(
    val version: String,
    val title: String,
    val highlights: List<String>
)

/**
 * Always-English changelog displayed once automatically after an update and accessible
 * anytime from Settings.
 */
object PatchNotesCatalog {
    val releases: List<PatchNoteRelease> = listOf(
        PatchNoteRelease(
            version = "v3.0.1",
            title = "Security Hardening, Versioned ONNX Model & Codebase Cleanup",
            highlights = listOf(
                "Enforced fail-closed SHA-256 verification, bounded metadata/checksum reads, and allowlisted redirect validation in the in-app Download Manager.",
                "Versioned the extracted on-device ONNX acne model file (acne_detector_v<versionCode>.onnx) and cleaned up stale older model files on update.",
                "Removed unused WorkManager dependency and dead UI/ViewModel code for a leaner APK."
            )
        ),
        PatchNoteRelease(
            version = "v3.0.0",
            title = "On-Device Local AI Blemish Detection & Patch Notes",
            highlights = listOf(
                "Bundled a 100% offline INT8 YOLOv8s acne & blemish lesion detector (ONNX Runtime) alongside ML Kit FaceMesh and CIELAB skin metrics — zero cloud uploads, works in airplane mode.",
                "Automatic graceful fallback to CIELAB blemish analysis if native ONNX is unavailable.",
                "Added this English Patch Notes dialog: shown once automatically after updating and viewable anytime in Settings."
            )
        ),
        PatchNoteRelease(
            version = "v2.4.2",
            title = "Gemini 3.8 Flash & Friendly Scan Errors",
            highlights = listOf(
                "Updated Gemini Vision model chain to gemini-3.8-flash (primary) + gemini-3.5-flash-lite (fallback).",
                "Replaced raw API errors with a user-friendly notice and an expandable 'Show error details' button."
            )
        ),
        PatchNoteRelease(
            version = "v2.4.1",
            title = "Toggleable Screenshot Protection",
            highlights = listOf(
                "Added 'Block screenshots & screen recording' toggle in Settings -> Privacy (off by default)."
            )
        ),
        PatchNoteRelease(
            version = "v2.4.0",
            title = "3-Photo Visual Verification & Private Shelf Photos",
            highlights = listOf(
                "Upgraded scan confirmation to fetch up to 3 reference photos across OpenBeautyFacts, OpenFoodFacts (vitamins), and Wikimedia Commons.",
                "Privately saves your captured product photo on-device (filesDir/products with .nomedia) to display on your Shelf card."
            )
        ),
        PatchNoteRelease(
            version = "v2.3.1",
            title = "Balanced Calibration & Jaw-Shadow Stubble Fix",
            highlights = listOf(
                "Added jaw-shadow compensation so overhead indoor lighting does not trigger false stubble readings.",
                "Balanced thresholds across all skin metrics with a 10% weakest-link penalty."
            )
        ),
        PatchNoteRelease(
            version = "v2.3.0",
            title = "Holistic Advice Engine & Raw-Like Camera2Interop",
            highlights = listOf(
                "Added Camera2Interop raw-like capture settings + 1080px analysis + traffic-light status colors.",
                "Holistic advice now accounts for systemic oral medications (e.g. isotretinoin/Medinac + Vitamin A safety warning), vitamins, topical creams, routine logs, and hygiene logs.",
                "Removed Quick-Add Presets from the Shelf UI for a cleaner layout."
            )
        ),
        PatchNoteRelease(
            version = "v2.2.0",
            title = "Vitamins, Medications, Offline Vitamin Calculator & Centered Check-In",
            highlights = listOf(
                "Added Vitamins and Meds & Ointments categories to the Shelf and scanner.",
                "Added 100% offline age-based Daily Vitamin Reference Calculator (EFSA/NIH) with birthday setting.",
                "Centered the Face Check-In tab in the floating pill bottom dock."
            )
        )
    )
}

@Composable
fun PatchNotesDialog(
    isNewUpdate: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("patch_notes_dialog"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isNewUpdate) "What's New in SelfCare" else "Patch Notes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Latest release: ${PatchNotesCatalog.releases.first().version}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PatchNotesCatalog.releases.forEachIndexed { idx, release ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${release.version} — ${release.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        release.highlights.forEach { bullet ->
                            Text(
                                text = "• $bullet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (idx < PatchNotesCatalog.releases.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("patch_notes_close_btn")
            ) {
                Text(text = if (isNewUpdate) "Got it" else "Close")
            }
        }
    )
}
