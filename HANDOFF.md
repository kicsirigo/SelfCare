# SelfCare Android (`com.selfcare.app`) — HANDOFF
Updated: v1.1.0 (AMOLED Black + Neon Green UI overhaul; libs.versions.toml FROZEN)

## Packages & Core Classes
- `com.selfcare.app`: `SelfCareApp`, `MainActivity` (`SelfCareRoot` with `AnimatedContent` tabs, `AppDestination`)
- `com.selfcare.app.di`: `AppContainer` (`database`, `settingsRepository`, `routineRepository`, `hygieneRepository`, `productRepository`, `backupRepository`, `faceCheckInRepository`, `haircutTracker`, `inciEngine`, `openBeautyFactsClient`, `notificationHelper`, `reminderScheduler`, `faceAnalyzer`, `ocrScanner`, `barcodeScanner`)
- `com.selfcare.app.data.preset`: `PresetProductCatalog` (`PresetGroup`: `RX`, `HAIR`, `FACE` — Davercin, Skinoren, Nizoral, Alpecin, etc.)
- `com.selfcare.app.domain.cv`: `LightingQualityChecker`, `FaceMetricCalculator` (`HairDetailedMetrics`, `computeDetailedHairMetrics`), `MetricInterpreter` (`MetricStatusLevel`, qualitative gauges & hair analysis), `HaircutTracker`
- `com.selfcare.app.domain.ml` & `com.selfcare.app.data.ml`: `FaceAnalyzer` (`MlKitFaceAnalyzer`, `StubFaceAnalyzer`), `OcrScanner` (`MlKitOcrScanner`), `BarcodeScanner` (`MlKitBarcodeScanner`)
- `com.selfcare.app.domain.inci`: `InciDatabaseLoader`, `InciParser`, `InciConflictDetector` (`SLOT_HAIR`), `InciEngine` (69 ingredients, 7 conflicts)
- `com.selfcare.app.data.remote`: `OpenBeautyFactsClient` (anonymous read-only GET)
- `com.selfcare.app.ui.theme` & `ui.components`: `ThemeMode` (default `AMOLED` `#000000` + `#00FF66`), `SelfCareTheme`, `MetricGaugeComponents` (`MetricGaugeRow`, `HairHealthAnalysisCard`)
- `com.selfcare.app.ui.icons` / `ui.localization` / `notifications`: `AppIcons`, `LocaleHelper`, `NotificationHelper`, `ReminderReceiver`, `ReminderScheduler`

## Data, Repositories & ViewModels
- `com.selfcare.app.data.local`: `SelfCareDatabase` (5 DAOs), `SettingsRepository` (`AppLanguage`, `UserSettings`)
- `com.selfcare.app.data.repository`: `RoutineRepository` (`PERIOD_MORNING`, `PERIOD_EVENING`, `PERIOD_HAIR`), `HygieneRepository`, `ProductRepository`, `BackupRepository`, `FaceCheckInRepository`
- ViewModels: `RoutineViewModel`, `HygieneViewModel`, `ShelfViewModel` (category filters + offline presets), `CheckInViewModel`, `TrendsViewModel`, `SettingsViewModel`

## Active `testTag` Prefixes & Tags
- `app_root`, `nav_`: `nav_routine`, `nav_hygiene`, `nav_shelf`, `nav_checkin`, `nav_trends`, `nav_settings`
- `routine_`: `routine_period_morning`, `routine_period_evening`, `routine_period_hair`, `routine_complete_morning_btn`, `routine_complete_evening_btn`, `routine_complete_hair_btn`, `routine_completed_badge`, `routine_product_toggle_<id>`, `routine_empty_state`
- `hygiene_`: `hygiene_item_<SHOWER|TEETH_AM|TEETH_PM|LIP_BALM|customKey>`, `hygiene_add_custom_input`, `hygiene_add_custom_btn`
- `shelf_` / `inci_`: `shelf_search_input`, `shelf_lookup_btn`, `shelf_scan_barcode_btn`, `shelf_scan_ocr_btn`, `shelf_filter_<ALL|FACE|RX|HAIR>`, `shelf_presets_card`, `shelf_preset_<id>`, `shelf_slot_<MORNING|EVENING|BOTH|HAIR|NONE>`, `shelf_product_default_toggle`, `shelf_save_product_btn`, `shelf_empty_state`, `shelf_product_card_<id>`, `inci_match_list`, `inci_conflict_banner`
- `settings_` / `backup_`: `settings_theme_<LIGHT|DARK|AMOLED>`, `settings_lang_<AUTO|EN|HU>`, `settings_auto_delete_toggle`, `settings_reminder_toggle`, `settings_version_label`, `backup_export_btn`, `backup_import_btn`
- `checkin_` / `haircut_` / `trends_`: `checkin_permission_required`, `checkin_framing_overlay`, `checkin_lighting_warning`, `checkin_capture_btn`, `checkin_hair_health_card`, `checkin_auto_delete_toggle`, `checkin_haircut_reset_btn`, `haircut_reminder_banner`, `trends_empty_state`, `trends_7d_baseline`, `trends_30d_baseline`, `trends_metric_card_<under_eye|redness|texture|lip|hair>`, `trends_routine_correlation`, `trends_history_list`
