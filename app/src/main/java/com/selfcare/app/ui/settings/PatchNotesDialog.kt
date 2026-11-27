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
            version = "v3.19.0",
            title = "First-Launch Onboarding Cards & Beginner Groq API Key Guide",
            highlights = listOf(
                "Added swipeable 5-card first-launch Onboarding guide (120Hz Expressive indicators, skip anytime, replayable from Settings -> App Guide) introducing Privacy, Today Hub, Nutrition & Water, Offline Face/INCI, and Optional Free AI.",
                "Added a beginner-friendly 4-step 'How to get a free Groq key?' guide accessible from Onboarding, Settings -> AI Engine, and AI Coach (when no key is saved), complete with 1-tap console.groq.com/keys link and 1-tap Paste from clipboard.",
                "Removed unused Google Lens manifest queries and aligned local-first privacy documentation."
            )
        ),
        PatchNoteRelease(
            version = "v3.18.1",
            title = "Live Routine % Ring, 'To Do' Uncheck State & Custom Step Reordering",
            highlights = listOf(
                "Routine Hero ring % now updates live whenever you check or uncheck any product (even after completing the routine), and unchecked items show 'To do' instead of 'Skipped today'.",
                "Added compact Up / Down reorder arrows right on every Routine product row with smooth 120Hz spring gliding (saved persistently on device)."
            )
        ),
        PatchNoteRelease(
            version = "v3.18.0",
            title = "Routine Today Hub & Coach, Product Nicknames, AI Language Fix & Codebase Cleanup",
            highlights = listOf(
                "Upgraded the Routine tab into a unified Today Hub & Smart Routine Coach: dead-center 100% ring, single Hero card with Morning/Evening/Hair status & completion time, Today Pulse bar (kcal, water +250 ml 1-tap, shave countdown), automatic skincare/vitamin step ordering, live INCI conflict alerts, and custom Daily Habits.",
                "Added 1-tap Product Nickname / Rename right on Routine rows (e.g. 'Advanced Repair Ointment' -> 'CeraVe Lip Balm') while preserving all INCI data, strikethrough & dimmed completed steps, calm 'Completed · Update' state, and 92dp bottom scroll clearance for the floating AI FAB.",
                "Fixed AI Coach replying in Hungarian when addressed in English (now strictly matches the user's message language even with Hungarian meal names in context), plus Ponytail Audit cleanup (deleted orphaned ScanPhotoHelper.kt, dead settings, and 34 unused strings)."
            )
        ),
        PatchNoteRelease(
            version = "v3.17.0",
            title = "2026 Material 3 Expressive Motion Pack & 100% In-App CameraX",
            highlights = listOf(
                "Migrated the final remaining system camera (Product Shelf INCI OCR) to our custom in-app CameraX viewfinder — 100% of camera features across the entire app now run natively inside SelfCare with zero system camera handoffs.",
                "Upgraded the bottom navigation bar, theme chips, and tab transitions with 2026 Material 3 Expressive Shape Morphing (pill -> squircle spring morph) and spatial spring physics.",
                "Added a 120Hz zero-recomposition GPU ExpressiveThinkingDots wave across AI Coach Chat, Nutrition AI search, and Product Shelf lookup, plus bouncy spring container resizing across Settings, Shelf, and Coach."
            )
        ),
        PatchNoteRelease(
            version = "v3.16.1",
            title = "Accidental API Key Deletion Protection",
            highlights = listOf(
                "Saved Groq API keys are now displayed in a locked, masked field (gsk_••••••••1234) so scrolling can never accidentally overwrite them.",
                "Added an explicit 'Change' button to unlock editing, disabled saving an empty key, and added a confirmation dialog before clearing the saved API key."
            )
        ),
        PatchNoteRelease(
            version = "v3.16.0",
            title = "AI Web Search EOF Fix & Live Groq API Quota Monitor",
            highlights = listOf(
                "Fixed the 'Expected start of object {, but had EOF' error on Groq Web Search (gpt-oss-20b / 120b) by raising max_completion_tokens to 3072 with reasoning_effort='low' and adding safe JSON fallback extraction.",
                "Added a zero-overhead Live Groq API Quota Monitor in Settings -> AI Engine showing daily tokens used vs. Free Tier reference, estimated remaining photo scans & web searches today, live remaining daily requests from Groq headers, and per-minute TPM."
            )
        ),
        PatchNoteRelease(
            version = "v3.15.0",
            title = "Databases Restored (OFF + USDA + OpenBeautyFacts) + On-Demand Web Search Buttons",
            highlights = listOf(
                "Restored OpenFoodFacts (3M+ HU+Global), USDA FoodData Central (400k+ cooked & raw foods with HU->EN bridge), and OpenBeautyFacts as the fast, reliable default databases with clickable source links.",
                "Meal photo & text AI estimation now grounds against the 3-layer database (250+ Offline HU + OpenFoodFacts + USDA) by default so cooked Hungarian dishes stay accurate and fast.",
                "Added explicit on-demand 'Web Search (AI)' buttons in Nutrition search, inside the Meal Confirmation dialog ('Verify on Web'), and on the Product Shelf so you can trigger live Groq browser_search whenever you want."
            )
        ),
        PatchNoteRelease(
            version = "v3.14.0",
            title = "Streamlined AI + Web Search Primary Architecture (Zero External DBs)",
            highlights = listOf(
                "Removed all external food & beauty database clients (OpenFoodFacts, USDA FoodData Central, OpenBeautyFacts) and removed Cloud Save for a leaner, 100% local-storage + single-engine architecture.",
                "Promoted live AI Web Search (Groq browser_search on gpt-oss-20b / gpt-oss-120b) to the primary online lookup & verifier for meal photos, text meals, food barcodes, and skincare/INCI products — complete with clickable web source links.",
                "Kept the bundled 250+ Offline Hungarian & international food catalog and 100% on-device INCI conflict analyzer as instant 0ms offline fallbacks."
            )
        ),
        PatchNoteRelease(
            version = "v3.13.0",
            title = "Layer-4 AI Web Search Fallback & Zero-Knowledge Cloud Save",
            highlights = listOf(
                "Added Layer-4 AI Web Search Fallback (Groq browser_search on gpt-oss-20b / gpt-oss-120b): when a food isn't found in Offline HU DB, OpenFoodFacts, or USDA, SelfCare searches the web automatically and attaches clickable source URLs (can be toggled in Settings).",
                "Added Privacy-First Zero-Knowledge Cloud Save (Settings -> Cloud Save): username + password login backed by Supabase (or self-hosted Supabase) with client-side GZIP + AES-256-GCM encryption and a one-time Recovery Key so neither the server nor admins can ever read your health data.",
                "Added ready-to-run Supabase SQL + Row Level Security schema in docs/supabase_cloud_save.sql and in-app Server URL / Anon Key configuration."
            )
        ),
        PatchNoteRelease(
            version = "v3.12.1",
            title = "Clickable Verified Database Source Links (OpenFoodFacts & USDA)",
            highlights = listOf(
                "Added clickable verified database source links inside the Meal Confirmation dialog so you can open the exact OpenFoodFacts product page or USDA FoodData Central nutrient sheet in your browser.",
                "Works for both AI -> Database -> AI ('DB + AI Verified') Food RAG grounding and direct barcode / online database search results."
            )
        ),
        PatchNoteRelease(
            version = "v3.12.0",
            title = "3-Layer Mega Food Database (250+ Offline HU + 3M+ OFF + 400k+ USDA) & Food RAG",
            highlights = listOf(
                "Expanded the 100% offline Hungarian & international food catalog to 250+ items (bakery/sandwich items, cold cuts, cheeses, fast food like McDonald's/Burger King/KFC, soups, stews, fruits, veggies, sweets, and drinks).",
                "Upgraded remote search to query OpenFoodFacts (Hungarian + automatic global fallback for 3M+ products) and USDA FoodData Central (400k+ raw & restaurant foods via built-in DEMO_KEY with HU->EN ingredient bridge) in parallel.",
                "Added AI -> Database -> AI Food RAG grounding: after Qwen identifies a meal photo or plain-language description, SelfCare looks up verified per-100g facts from the 3-layer DB and feeds them back to Qwen for database-grounded macro & salt calculation ('DB + AI Verified')."
            )
        ),
        PatchNoteRelease(
            version = "v3.11.1",
            title = "Free Portion Multiplier (Any ×) for Every Meal",
            highlights = listOf(
                "Added quick portion chips (0.5x, 1x, 1.5x, 2x, 3x) plus a free numeric Multiplier / Servings input (e.g. 2.5, 4, 0.75) inside Meal Confirmation & Edit dialogs for every meal type (presets, barcodes, recipes, photos, AI text, and edited meals)."
            )
        ),
        PatchNoteRelease(
            version = "v3.11.0",
            title = "Tap-to-Edit Logged Meals, Animated Flame Streak Pill & Hydration Reminder",
            highlights = listOf(
                "Tap any logged meal card on the Nutrition tab to edit its name, grams, calories, macros, salt, meal slot, or exact HH:mm time in place.",
                "Added a 120Hz zero-recomposition Animated Streak Flame Pill (flickering dual-layer Bezier flame + rising ember sparks + spring rolling counter) on both the Routine and Nutrition tabs.",
                "Added an optional daily Hydration Reminder (toggle + customizable time) in Settings."
            )
        ),
        PatchNoteRelease(
            version = "v3.10.1",
            title = "True 120Hz Display Mode & Zero-Recomposition Animation Engine",
            highlights = listOf(
                "Explicitly requests the display's highest supported refresh rate (120Hz/144Hz) in MainActivity so HyperOS/LTPO devices never throttle the app down to 60Hz.",
                "Moved breathing AI FAB halo, floating +ml popups, confetti overlay, calorie ring, and macro progress bars to deferred GPU draw/graphicsLayer state reads (0 recompositions/sec & 0 re-layouts during animations).",
                "Eliminated per-frame Path allocations in AnimatedWaterGlass and switched tab transitions to jank-free GPU fade+micro-scale.",
                "Codebase lean audit: removed dead Hygiene UI/ViewModel files, unused strings and test dependency, and deduplicated Groq response parsing."
            )
        ),
        PatchNoteRelease(
            version = "v3.10.0",
            title = "Material 3 Expressive Motion Pack & Larger Breathing AI Coach Button",
            highlights = listOf(
                "Introduced Material 3 Expressive spring physics (ExpressiveMotion) across navigation, routines, nutrition, and charts with tactile haptic feedback.",
                "Upgraded the Qwen AI Coach button to a larger 56dp FloatingActionButton with a soft breathing neon aura on the right side (auto-hides during live camera Check-In and Settings).",
                "Added odometer-style rolling calorie & water counters, a glowing calorie ring tip, floating '+250 ml' / '+500 ml' hydration popups, smooth animated meal-list transitions, bouncy Routine completion cards, directional shared-axis tab transitions, and a staggered wave-rise Calorie Bar Chart."
            )
        ),
        PatchNoteRelease(
            version = "v3.9.0",
            title = "In-App Custom Food Camera, 14/30/90/365d Calorie Chart & Interactive AI Ingredient Clarification",
            highlights = listOf(
                "Replaced the external system camera redirect with a custom in-app CameraX capture screen (clean rounded frame, flash/torch toggle, in-memory Bitmap capture) for Meal Photo AI, Nutrition Label Scan, and Barcode Scan.",
                "Added an interactive Calorie History Bar Chart on the Trends tab with 14d / 30d / 90d / 365d range selectors, color-coded daily bars, and a dashed daily calorie target line.",
                "Upgraded Meal Photo AI & Natural-Language Text Estimation to show AI-guessed ingredients, ask a short clarifying question for ambiguous meals (like sandwiches/wraps), and recalculate macros & salt live in the confirmation dialog from your plain-language reply."
            )
        ),
        PatchNoteRelease(
            version = "v3.8.1",
            title = "Smarter Time-Aware Qwen AI Coach, Clean Chat Formatting & Right-Side Camera-Aware FAB",
            highlights = listOf(
                "Upgraded Qwen AI Coach to be time-of-day aware (passes current local time so morning check-ins never scold low daily totals), stop regurgitating raw dashboard numbers, and give concise, actionable 2nd-person coaching.",
                "Added clean Markdown rendering in the AI Coach chat bubbles (renders **bold**, *italic*, and bullet points cleanly without raw asterisks).",
                "Moved the floating AI Coach button to the right side and automatically hide it during live camera Check-In, Settings, or foreground sheets so it never blocks camera views or controls."
            )
        ),
        PatchNoteRelease(
            version = "v3.8.0",
            title = "Meal Timing & Slot Grouping + Floating Qwen AI Coach + Groq ZDR Privacy Alert",
            highlights = listOf(
                "Upgraded Meal Logging to track WHEN and WHAT you eat: auto-selects Breakfast, Lunch, Snack, or Dinner by time of day, adds Meal Slot chips and editable HH:mm eating time inside every logging dialog, groups today's meals by slot with kcal subtotals, and displays your daily Eating Window & late-night meal alerts.",
                "Added a Floating AI Coach Chat button (Qwen 3.8 27B) accessible from every main tab with granular opt-in data scope toggles (Nutrition & Timing, Exercise & Weight, Routine & Shelf, Face Trends) and a 'What AI sees' context preview — photos, names, and birth dates are never sent.",
                "Added a prominent Zero Data Retention (ZDR) privacy alert dialog when saving a Groq API key in Settings, with a direct link to console.groq.com Data Controls."
            )
        ),
        PatchNoteRelease(
            version = "v3.7.0",
            title = "Day Switcher, Custom Recipes, Exercise Tracking, Weight Trend & Nutrition Label Scanner",
            highlights = listOf(
                "Added a Day Switcher (< Yesterday | Today >) on the Nutrition tab so you can browse, log, or edit meals, water, and workouts for previous days.",
                "Added a Custom Recipe Builder to combine multiple ingredients, divide by servings, save to Room v7 (MIGRATION_6_7), and log 1 serving with 1 tap.",
                "Added Exercise & Burned Calories tracking with MET presets (Running, Walking, Cycling, Swimming, Gym, HIIT, Football, Yoga) that automatically adds burned kcal to your daily calorie budget.",
                "Added Body Weight logging and a 30-day Weight & 7-day Calorie/Protein Trend card with sparkline graph on the Trends tab.",
                "Added a Nutrition Label Photo Scanner ('Nutrition Label') powered by Groq Vision (qwen/qwen3.8-27b) to read per-100g nutrition tables directly off packaging with live gram scaling."
            )
        ),
        PatchNoteRelease(
            version = "v3.6.0",
            title = "Hungarian Dishes, Salt-Adjusted Hydration, Offline Body Profile & Safe Trends History",
            highlights = listOf(
                "Expanded offline food catalog to 65+ Hungarian & international dishes (e.g. Csirkepörkölt / Chicken Paprikash, Nokedli, Lecsó, Gulyásleves, Gyros, Sandwiches) with accent- and language-tolerant search.",
                "Added Groq AI Text Meal Estimation ('AI Estimate' button) to estimate calories, macros, and salt for any typed Hungarian or English dish in ~1s, plus 1-tap Recent Meals and Hungarian OpenFoodFacts priority.",
                "Added automatic Salt (g) tracking across photos, AI text, barcodes, and presets (Room v6 MIGRATION_5_6) — automatically adds extra recommended water (+150 ml per excess gram above 5g WHO reference) directly to your animated Water Glass target.",
                "Added a 100% offline Body Profile (current weight, target weight, height, gender, activity, goal) that automatically reuses your saved birthday/age.",
                "Clarified in Settings, Nutrition, and README that images/text sent to Groq API are never used for AI model training.",
                "Upgraded Trends Measurement History with exact second-precision timestamps (YYYY-MM-DD · HH:mm:ss) and a safe 'Manage' bulk-selection delete mode to prevent accidental deletions."
            )
        ),
        PatchNoteRelease(
            version = "v3.5.0",
            title = "Calorie, Macro & Animated Water Tracker + Celebration Animations",
            highlights = listOf(
                "Replaced the Hygiene tab with a full Calorie, Protein, Carbs, Fat, Fiber & Sugar tracker backed by Room v5 (MIGRATION_4_5) and SAF JSON Backup/Restore.",
                "Added 3 fast logging paths: OpenFoodFacts Barcode scanning, Groq Vision (qwen/qwen3.8-27b) Meal Photo AI estimation with 0.5x/1x/1.5x portion scaling, plus 25 offline everyday food presets and a Mifflin-St Jeor daily goal calculator.",
                "Added an animated live-wave Water Glass tracker (+250 ml, +500 ml, and Custom ml) with rising micro-bubbles, a joyful spring bounce, and twinkling stars when your hydration target is reached.",
                "Added a non-blocking Confetti & Twinkling Stars celebration overlay across Nutrition (water/protein/calorie targets), Daily Routine completion, and great Face Check-In scores (>= 7.5/10)."
            )
        ),
        PatchNoteRelease(
            version = "v3.4.2",
            title = "Groq Vision Upgraded to Qwen 3.8 27B",
            highlights = listOf(
                "Migrated Groq Vision from retired Llama 4 Scout (deprecated July 2026) to Groq's current native multimodal model qwen/qwen3.8-27b (with meta-models/muse-spark-open fallback).",
                "Updated live /openai/v1/models discovery to prioritize qwen3.8 and muse-spark vision models and added <think> block stripping for clean JSON parsing."
            )
        ),
        PatchNoteRelease(
            version = "v3.4.1",
            title = "Groq Vision Dynamic Model Discovery & UI Polish",
            highlights = listOf(
                "Completely removed the Google Gemini API client — Shelf visual scanning now runs exclusively on Groq Vision alongside Live Barcode and 100% Offline INCI OCR.",
                "Added live model discovery via Groq's /openai/v1/models endpoint so the app automatically picks active vision models enabled on your account (removing the retired Llama 4 Maverick 404).",
                "Fixed the Settings 'Patch Notes' button text wrapping on narrow screens."
            )
        ),
        PatchNoteRelease(
            version = "v3.4.0",
            title = "Groq Vision (Llama 4 Scout) + Live Barcode + Offline INCI OCR",
            highlights = listOf(
                "Added Groq Vision support (meta-llama/llama-4-scout-17b-16e-instruct primary + llama-4-maverick fallback) with automatic key-prefix routing: paste a free gsk_... key from console.groq.com (1,000 req/day, fast ~1s responses, no Gemini 503 overload) or a Gemini key in Settings.",
                "Restored the Yuka/INCI Beauty-style Shelf workflow combining instant Live Barcode scanning, Vision AI front-label recognition, dedicated Offline INCI OCR for back-of-bottle ingredients, and Google Lens / Share-to-SelfCare fallback.",
                "Fixed GitHub Release asset naming (SelfCare-release.apk) so the in-app Download Manager seamlessly detects, SHA-256 verifies, and installs updates.",
                "Updated README documentation to reflect the full v3.4.0 architecture."
            )
        ),
        PatchNoteRelease(
            version = "v3.3.0",
            title = "Google Lens-First Product Shelf & Share-to-SelfCare",
            highlights = listOf(
                "Replaced Shelf Gemini Vision, Barcode, and OCR scanners with a streamlined Google Lens workflow — zero API keys and zero HTTP 503 errors.",
                "Added Android Share-to-SelfCare (ACTION_SEND) support: share any product name or link from Google Lens or Chrome directly into SelfCare to auto-fill and look up ingredients.",
                "Added a one-tap Paste button on the Shelf screen that automatically attaches your captured product photo and looks up catalog details.",
                "On-device Check-In AI engines (YOLOv8s, EfficientNet-B0, ML Kit FaceMesh & SelfieSegmentation) remain 100% offline and unchanged."
            )
        ),
        PatchNoteRelease(
            version = "v3.2.1",
            title = "Gemini 503 Retry & Google Lens Product Search",
            highlights = listOf(
                "Added automatic exponential backoff retries (800 ms -> 1800 ms) for transient HTTP 503 / 429 overload responses on gemini-3.8-flash and gemini-3.5-flash-lite.",
                "Added a 'Search in Google Lens' button on the Shelf product scan confirmation dialog to open any captured product photo directly in Google Lens."
            )
        ),
        PatchNoteRelease(
            version = "v3.2.0",
            title = "Hybrid Multi-Head Skin Signals AI & Directional Stubble Detector",
            highlights = listOf(
                "Bundled a 100% offline 4-head EfficientNet-B0 INT8 skin signals model (skin_signals_int8.onnx) alongside the YOLOv8s INT8 blemish detector, running in parallel on-device.",
                "Added hybrid fusion combining global 224x224 EfficientNet-B0 skin predictions (structure, hydration, erythema/UV, elasticity) with 1080px local CIELAB ROI metrics for better lighting resilience.",
                "Upgraded jawline stubble detection with directional Sobel bristle edge-density analysis to further reduce overhead shadow false positives.",
                "Updated the Active AI Engines strip and telemetry dialog to display both ONNX YOLOv8s and ONNX EfficientNet-B0 status."
            )
        ),
        PatchNoteRelease(
            version = "v3.1.0",
            title = "Active AI Engine Telemetry UI/UX",
            highlights = listOf(
                "Added a live Local AI indicator pill (ONNX YOLOv8s + ML Kit) on the Check-In camera viewfinder HUD.",
                "Added a clickable 'Active AI engines' breakdown strip on the Check-In result card showing which engine analyzed blemishes, facial landmarks, skin/stubble, and hair, plus an expandable details dialog with runtime metrics.",
                "Updated the Shelf product scan confirmation dialog to display the exact AI model used (e.g. Gemini 3.8 Flash, Gemini 3.5 Flash-Lite, or Offline ML Kit OCR)."
            )
        ),
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
