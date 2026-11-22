# SelfCare Android (`com.selfcare.app`) — HANDOFF
Updated: Stage 5 (E2E Suite & Final Audit complete; libs.versions.toml FROZEN)

## Packages & Core Classes
- `com.selfcare.app`: `SelfCareApp` (`container`, `setTestContainer`, `resetContainer`), `MainActivity` (`SelfCareRoot`, `AppDestination`)
- `com.selfcare.app.di`: `AppContainer` (`database`, `settingsRepository`, `routineRepository`, `hygieneRepository`, `productRepository`, `backupRepository`, `faceCheckInRepository`, `haircutTracker`, `inciEngine`, `openBeautyFactsClient`, `notificationHelper`, `reminderScheduler`, `faceAnalyzer`, `ocrScanner`, `barcodeScanner`)
- `com.selfcare.app.domain.cv`: `LightingQualityChecker` (`LightingAssessment`, `LightingWarningReason`), `FaceMetricCalculator` (`PixelRect`, `LabColor`, CIELAB/under-eye/redness/texture/lip/hair-to-face), `HaircutTracker` (`HaircutEvaluation`, `evaluate`, `resetBaseline`)
- `com.selfcare.app.domain.ml` & `com.selfcare.app.data.ml`: `FaceAnalyzer` (`MlKitFaceAnalyzer`, `StubFaceAnalyzer`), `OcrScanner` (`MlKitOcrScanner`), `BarcodeScanner` (`MlKitBarcodeScanner`)
- `com.selfcare.app.domain.inci`: `InciDatabaseLoader`, `InciParser`, `InciConflictDetector`, `InciEngine` (`InciMatch`, `DetectedConflict`)
- `com.selfcare.app.data.remote`: `OpenBeautyFactsClient` (anonymous read-only GET, `OpenBeautyFactsLookupResult`, `OpenBeautyFactsSearchResult`)
- `com.selfcare.app.ui.theme`: `ThemeMode` (`LIGHT`, `DARK`, `AMOLED`), `SelfCareTheme`
- `com.selfcare.app.ui.icons`: `AppIcons` (24dp monochrome vectors)
- `com.selfcare.app.ui.localization`: `LocaleHelper`
- `com.selfcare.app.notifications`: `NotificationHelper`, `ReminderReceiver`, `ReminderScheduler`

## Data, Repositories & ViewModels
- `com.selfcare.app.data.local`: `SelfCareDatabase` (`RoutineDao`, `ProductDao`, `FaceCheckInDao`, `HygieneDao`, `HaircutBaselineDao`), `SettingsRepository` (`AppLanguage`, `UserSettings`)
- `com.selfcare.app.data.repository`:
  - `RoutineRepository` (`completeRoutine`, `toggleProductOverride`, `setProductRoutineDefault`, `currentEpochDay`)
  - `HygieneRepository` (`SHOWER`, `TEETH_AM`, `TEETH_PM`, `LIP_BALM`, custom habits)
  - `ProductRepository` (Room CRUD, barcode cache -> OBF -> offline fallback, INCI parse & conflicts)
  - `BackupRepository` (`SelfCareBackupPayload`, `exportToJson`, `importFromJson`, `exportToUri`, `importFromUri`)
  - `FaceCheckInRepository` (`performCheckIn`, `saveAnalyzedCheckIn`, private `filesDir/checkins` + `.nomedia` + auto-delete, `computePersonalTrends`, `observePersonalTrends`, `PersonalTrendsSummary`)
- ViewModels: `RoutineViewModel` (`ui.routine`), `HygieneViewModel` (`ui.hygiene`), `ShelfViewModel` (`ui.shelf`), `CheckInViewModel` (`ui.checkin`), `TrendsViewModel` (`ui.trends`), `SettingsViewModel` (`ui.settings`)

## Active `testTag` Prefixes & Tags
- `app_root`
- `nav_`: `nav_routine`, `nav_hygiene`, `nav_shelf`, `nav_checkin`, `nav_trends`, `nav_settings`
- `routine_`: `routine_period_morning`, `routine_period_evening`, `routine_complete_morning_btn`, `routine_complete_evening_btn`, `routine_completed_badge`, `routine_product_toggle_<id>`, `routine_empty_state`
- `hygiene_`: `hygiene_item_SHOWER`, `hygiene_item_TEETH_AM`, `hygiene_item_TEETH_PM`, `hygiene_item_LIP_BALM`, `hygiene_item_<customKey>`, `hygiene_add_custom_input`, `hygiene_add_custom_btn`
- `shelf_` / `inci_`: `shelf_search_input`, `shelf_lookup_btn`, `shelf_scan_barcode_btn`, `shelf_scan_ocr_btn`, `shelf_offline_fallback_banner`, `shelf_add_product_btn`, `shelf_product_name_input`, `shelf_product_brand_input`, `shelf_product_barcode_input`, `shelf_product_ingredients_input`, `shelf_product_slot_selector`, `shelf_slot_<MORNING|EVENING|BOTH|NONE>`, `shelf_product_default_toggle`, `shelf_save_product_btn`, `shelf_empty_state`, `shelf_product_card_<id>`, `inci_match_list`, `inci_conflict_banner`
- `settings_` / `backup_`: `settings_theme_<LIGHT|DARK|AMOLED>`, `settings_lang_<AUTO|EN|HU>`, `settings_auto_delete_toggle`, `settings_reminder_toggle`, `backup_export_btn`, `backup_import_btn`
- `checkin_` / `haircut_`: `checkin_permission_required`, `checkin_framing_overlay`, `checkin_lighting_warning`, `checkin_capture_btn`, `checkin_auto_delete_toggle`, `checkin_haircut_reset_btn`, `haircut_reminder_banner`
- `trends_`: `trends_empty_state`, `trends_7d_baseline`, `trends_30d_baseline`, `trends_metric_card_under_eye`, `trends_metric_card_redness`, `trends_metric_card_texture`, `trends_metric_card_lip`, `trends_metric_card_hair`, `trends_routine_correlation`, `trends_history_list`

## Stage 5 E2E Test Suite (`app/src/test/java/com/selfcare/app/e2e/`)
- `E2ETestFixtures.kt`: `MutableClock`, `FakeFaceAnalyzer`, `FakeOcrScanner`, `FakeBarcodeScanner`, `TestAppContainer` (MockWebServer OBF), `SelfCareE2ERule` (`SelfCareApp.setTestContainer` / `resetContainer`).
- `Tier1FeatureCoverageTest.kt`, `Tier2BoundaryCornerTest.kt`, `Tier3PersistenceAndLocalizationTest.kt`, `Tier4UserJourneyTest.kt`: Robolectric SDK 35 + Compose UI E2E coverage, Room 5-entity roundtrip, SAF JSON backup, EN/HU parity, privacy/zero-emoji audit.
- Bugfixes landed during Stage 5: preserved `LocalActivityResultRegistryOwner` across `LocaleHelper` context wrap in `MainActivity.kt`; cleared stale `haircutEvaluation.isThresholdCrossed` on `CheckInViewModel.resetHaircutBaseline()`.
