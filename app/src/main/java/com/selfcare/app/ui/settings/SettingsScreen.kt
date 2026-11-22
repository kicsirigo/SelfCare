package com.selfcare.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.BuildConfig
import com.selfcare.app.R
import com.selfcare.app.data.local.AppLanguage
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.remote.AppUpdateInfo
import com.selfcare.app.data.remote.GeminiKeyTestResult
import com.selfcare.app.domain.health.VitaminCalculator
import com.selfcare.app.ui.icons.AppIcons
import com.selfcare.app.ui.theme.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(context.contentResolver, uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(context.contentResolver, uri)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission is optional; reminders gracefully no-op if denied
    }

    SettingsScreenContent(
        state = state,
        onSelectTheme = viewModel::setThemeMode,
        onSelectLanguage = viewModel::setAppLanguage,
        onToggleAutoDelete = viewModel::setAutoDeleteCheckInPhotos,
        onChangeHaircutThreshold = viewModel::setHaircutThresholdPercent,
        onChangeShaveInterval = viewModel::setShaveIntervalDays,
        onSaveBirthDate = viewModel::setBirthDateIso,
        onToggleMorningReminder = { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            viewModel.setMorningReminderEnabled(enabled)
        },
        onAdjustMorningTime = viewModel::adjustMorningReminderTime,
        onToggleEveningReminder = { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            viewModel.setEveningReminderEnabled(enabled)
        },
        onAdjustEveningTime = viewModel::adjustEveningReminderTime,
        onSaveGeminiApiKey = viewModel::setGeminiApiKey,
        onTestGeminiApiKey = viewModel::testGeminiApiKey,
        onCheckUpdates = viewModel::checkForUpdates,
        onDownloadUpdate = viewModel::startUpdateDownload,
        onCancelUpdateDownload = viewModel::cancelUpdateDownload,
        onInstallUpdate = { apkUri ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(permIntent) }
            } else {
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(installIntent) }
            }
        },
        onExportBackup = { exportLauncher.launch("selfcare-backup.json") },
        onImportBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        modifier = modifier
    )
}

@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleAutoDelete: (Boolean) -> Unit,
    onChangeHaircutThreshold: (Int) -> Unit,
    onChangeShaveInterval: (Int) -> Unit = {},
    onSaveBirthDate: (String?) -> Unit = {},
    onToggleMorningReminder: (Boolean) -> Unit,
    onAdjustMorningTime: (Int) -> Unit,
    onToggleEveningReminder: (Boolean) -> Unit,
    onAdjustEveningTime: (Int) -> Unit,
    onSaveGeminiApiKey: (String) -> Unit = {},
    onTestGeminiApiKey: (String) -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onDownloadUpdate: (AppUpdateInfo) -> Unit = {},
    onCancelUpdateDownload: () -> Unit = {},
    onInstallUpdate: (Uri) -> Unit = {},
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = state.settings

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Theme
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_theme),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val labelRes = when (mode) {
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                                ThemeMode.AMOLED -> R.string.theme_amoled
                            }
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onSelectTheme(mode) },
                                label = { Text(stringResource(labelRes)) },
                                modifier = Modifier.testTag("settings_theme_${mode.name}")
                            )
                        }
                    }
                }
            }
        }

        // Language
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Globe,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_language),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            val labelRes = when (lang) {
                                AppLanguage.AUTO -> R.string.lang_auto
                                AppLanguage.EN -> R.string.lang_en
                                AppLanguage.HU -> R.string.lang_hu
                            }
                            FilterChip(
                                selected = settings.appLanguage == lang,
                                onClick = { onSelectLanguage(lang) },
                                leadingIcon = if (lang == AppLanguage.AUTO) {
                                    {
                                        Icon(
                                            imageVector = AppIcons.Globe,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                label = { Text(stringResource(labelRes)) },
                                modifier = Modifier.testTag("settings_lang_${lang.name}")
                            )
                        }
                    }
                }
            }
        }

        // Reminders
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_reminders),
                        style = MaterialTheme.typography.titleMedium
                    )

                    ReminderRow(
                        title = stringResource(R.string.settings_morning_reminder),
                        hour = settings.morningReminderHour,
                        minute = settings.morningReminderMinute,
                        enabled = settings.morningReminderEnabled,
                        onToggle = onToggleMorningReminder,
                        onAdjustTime = onAdjustMorningTime,
                        toggleTag = "settings_reminder_toggle"
                    )

                    ReminderRow(
                        title = stringResource(R.string.settings_evening_reminder),
                        hour = settings.eveningReminderHour,
                        minute = settings.eveningReminderMinute,
                        enabled = settings.eveningReminderEnabled,
                        onToggle = onToggleEveningReminder,
                        onAdjustTime = onAdjustEveningTime,
                        toggleTag = "settings_evening_reminder_toggle"
                    )
                }
            }
        }

        // Privacy & Check-in settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_privacy_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_privacy_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_auto_delete_photo),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.autoDeleteCheckInPhotos,
                            onCheckedChange = onToggleAutoDelete,
                            modifier = Modifier.testTag("settings_auto_delete_toggle")
                        )
                    }

                    Text(
                        text = "${stringResource(R.string.settings_haircut_threshold)}: " +
                            stringResource(
                                R.string.settings_haircut_threshold_value,
                                settings.haircutThresholdPercent
                            ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = settings.haircutThresholdPercent.toFloat(),
                        onValueChange = { onChangeHaircutThreshold(it.roundToInt()) },
                        valueRange = UserSettings.MIN_HAIRCUT_THRESHOLD_PERCENT.toFloat()..
                            UserSettings.MAX_HAIRCUT_THRESHOLD_PERCENT.toFloat()
                    )

                    Text(
                        text = "${stringResource(R.string.settings_shave_interval)}: " +
                            stringResource(
                                R.string.settings_shave_interval_value,
                                settings.shaveIntervalDays
                            ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = settings.shaveIntervalDays.toFloat(),
                        onValueChange = { onChangeShaveInterval(it.roundToInt()) },
                        valueRange = UserSettings.MIN_SHAVE_INTERVAL_DAYS.toFloat()..
                            UserSettings.MAX_SHAVE_INTERVAL_DAYS.toFloat()
                    )

                    // Offline Birthdate for Age-Based Vitamin Calculator
                    val parsedDob = remember(settings.birthDateIso) {
                        VitaminCalculator.parseBirthDate(settings.birthDateIso)
                    }
                    val ageYears = remember(settings.birthDateIso) {
                        VitaminCalculator.computeAgeYears(settings.birthDateIso)
                    }
                    var yearInput by remember(settings.birthDateIso) {
                        mutableStateOf(parsedDob?.year?.toString().orEmpty())
                    }
                    var monthInput by remember(settings.birthDateIso) {
                        mutableStateOf(parsedDob?.monthValue?.toString()?.padStart(2, '0').orEmpty())
                    }
                    var dayInput by remember(settings.birthDateIso) {
                        mutableStateOf(parsedDob?.dayOfMonth?.toString()?.padStart(2, '0').orEmpty())
                    }
                    val candidateIso = remember(yearInput, monthInput, dayInput) {
                        val y = yearInput.toIntOrNull()
                        val m = monthInput.toIntOrNull()
                        val d = dayInput.toIntOrNull()
                        if (y != null && m != null && d != null && y in 1905..2025) {
                            val iso = VitaminCalculator.formatBirthDate(y, m, d)
                            if (VitaminCalculator.computeAgeYears(iso) != null) iso else null
                        } else null
                    }
                    Text(
                        text = if (ageYears != null) {
                            "${stringResource(R.string.settings_birthday_title)} ($ageYears)"
                        } else {
                            stringResource(R.string.settings_birthday_title)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_birthday_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = yearInput,
                            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) yearInput = it },
                            label = { Text(stringResource(R.string.birthday_year)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("settings_birthday_year")
                        )
                        OutlinedTextField(
                            value = monthInput,
                            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) monthInput = it },
                            label = { Text(stringResource(R.string.birthday_month)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_birthday_month")
                        )
                        OutlinedTextField(
                            value = dayInput,
                            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) dayInput = it },
                            label = { Text(stringResource(R.string.birthday_day)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_birthday_day")
                        )
                    }
                    Button(
                        onClick = { onSaveBirthDate(candidateIso) },
                        enabled = candidateIso != null && candidateIso != settings.birthDateIso,
                        modifier = Modifier.testTag("settings_birthday_save_btn")
                    ) {
                        Text(text = stringResource(R.string.action_save))
                    }
                }
            }
        }

        // Optional AI Visual Product Scan (Gemini API Key)
        item {
            var keyInput by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
            var savedNotice by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            text = stringResource(R.string.settings_ai_vision_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_ai_vision_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it
                            savedNotice = false
                        },
                        label = { Text(stringResource(R.string.settings_gemini_key_label)) },
                        placeholder = { Text(stringResource(R.string.settings_gemini_key_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_gemini_key_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onSaveGeminiApiKey(keyInput)
                                savedNotice = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_gemini_key_save_btn")
                        ) {
                            Text(text = stringResource(R.string.action_save))
                        }
                        if (keyInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    savedNotice = false
                                    onTestGeminiApiKey(keyInput)
                                },
                                enabled = !state.isTestingGeminiKey,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = stringResource(R.string.settings_gemini_test_btn))
                            }
                        }
                        if (settings.geminiApiKey.isNotBlank() || keyInput.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    keyInput = ""
                                    onSaveGeminiApiKey("")
                                    savedNotice = true
                                }
                            ) {
                                Text(text = stringResource(R.string.settings_gemini_key_clear))
                            }
                        }
                    }
                    if (savedNotice) {
                        Text(
                            text = stringResource(R.string.settings_gemini_key_saved),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    when (val testResult = state.geminiTestResult) {
                        is GeminiKeyTestResult.Success -> {
                            Text(
                                text = stringResource(R.string.settings_gemini_test_ok, testResult.modelUsed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is GeminiKeyTestResult.Failure -> {
                            Text(
                                text = stringResource(R.string.settings_gemini_test_fail, testResult.reason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        null -> Unit
                    }
                }
            }
        }

        // App Updates (In-App GitHub Releases Download Manager)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_updates_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Import,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_updates_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_updates_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when (val upd = state.updateState) {
                        UpdateManagerState.Idle -> {
                            OutlinedButton(
                                onClick = onCheckUpdates,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_check_updates_btn")
                            ) {
                                Text(text = stringResource(R.string.settings_updates_check_btn))
                            }
                        }
                        UpdateManagerState.Checking -> {
                            Text(
                                text = stringResource(R.string.settings_updates_checking),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        is UpdateManagerState.UpToDate -> {
                            Text(
                                text = stringResource(R.string.settings_updates_up_to_date, upd.latestVersion),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = onCheckUpdates,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.settings_updates_check_btn))
                            }
                        }
                        is UpdateManagerState.Available -> {
                            val sizeMb = upd.info.apkSizeBytes / (1024f * 1024f)
                            Text(
                                text = stringResource(
                                    R.string.settings_updates_available,
                                    upd.info.versionName,
                                    sizeMb
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = { onDownloadUpdate(upd.info) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_download_update_btn")
                            ) {
                                Text(text = stringResource(R.string.settings_updates_download_btn))
                            }
                        }
                        is UpdateManagerState.Downloading -> {
                            val progress = if (upd.totalBytes > 0L) {
                                (upd.downloadedBytes.toFloat() / upd.totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val pct = (progress * 100f).roundToInt().coerceIn(0, 100)
                            val dlMb = upd.downloadedBytes / (1024f * 1024f)
                            val totMb = upd.totalBytes / (1024f * 1024f)
                            Text(
                                text = stringResource(R.string.settings_updates_downloading, pct, dlMb, totMb),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (upd.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            OutlinedButton(
                                onClick = onCancelUpdateDownload,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.action_cancel))
                            }
                        }
                        is UpdateManagerState.ReadyToInstall -> {
                            Text(
                                text = stringResource(
                                    if (upd.sha256Verified) {
                                        R.string.settings_updates_ready_verified
                                    } else {
                                        R.string.settings_updates_ready
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = { onInstallUpdate(upd.apkUri) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_install_update_btn")
                            ) {
                                Text(text = stringResource(R.string.settings_updates_install_btn))
                            }
                        }
                        is UpdateManagerState.Error -> {
                            Text(
                                text = stringResource(R.string.settings_updates_error, upd.message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            OutlinedButton(
                                onClick = onCheckUpdates,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.settings_updates_check_btn))
                            }
                        }
                    }
                }
            }
        }

        // Local JSON Backup
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_backup_section),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExportBackup,
                            enabled = !state.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_export_btn")
                        ) {
                            Icon(
                                imageVector = AppIcons.Export,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.settings_export_backup))
                        }
                        OutlinedButton(
                            onClick = onImportBackup,
                            enabled = !state.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_import_btn")
                        ) {
                            Icon(
                                imageVector = AppIcons.Import,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.settings_import_backup))
                        }
                    }

                    when (val status = state.backupStatus) {
                        BackupStatus.Idle -> Unit
                        BackupStatus.ExportSuccess -> Text(
                            text = stringResource(R.string.settings_backup_export_success),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        is BackupStatus.ImportSuccess -> Text(
                            text = stringResource(
                                R.string.settings_backup_import_success,
                                status.summary.routineLogsCount,
                                status.summary.productsCount,
                                status.summary.hygieneLogsCount,
                                status.summary.faceCheckInsCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        is BackupStatus.Error -> Text(
                            text = stringResource(R.string.settings_backup_error, status.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(
                    R.string.settings_version_format,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("settings_version_label")
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ReminderRow(
    title: String,
    hour: Int,
    minute: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onAdjustTime: (Int) -> Unit,
    toggleTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.settings_reminder_time, hour, minute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag(toggleTag)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onAdjustTime(-15) }) {
                Text(stringResource(R.string.settings_time_minus_15))
            }
            TextButton(onClick = { onAdjustTime(15) }) {
                Text(stringResource(R.string.settings_time_plus_15))
            }
        }
    }
}
