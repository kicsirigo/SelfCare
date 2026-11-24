package com.selfcare.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.BuildConfig
import com.selfcare.app.data.local.AppLanguage
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.remote.AppUpdateInfo
import com.selfcare.app.data.remote.AppUpdateManager
import com.selfcare.app.data.remote.GeminiKeyTestResult
import com.selfcare.app.data.remote.ProductVisionClient
import com.selfcare.app.data.remote.UpdateCheckResult
import com.selfcare.app.data.remote.UpdateDownloadResult
import com.selfcare.app.data.repository.BackupRepository
import com.selfcare.app.data.repository.ImportSummary
import com.selfcare.app.notifications.ReminderScheduler
import com.selfcare.app.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateManagerState {
    data object Idle : UpdateManagerState
    data object Checking : UpdateManagerState
    data class UpToDate(val latestVersion: String) : UpdateManagerState
    data class Available(val info: AppUpdateInfo) : UpdateManagerState
    data class Downloading(
        val info: AppUpdateInfo,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateManagerState
    data class ReadyToInstall(
        val info: AppUpdateInfo,
        val apkUri: Uri,
        val sha256Verified: Boolean
    ) : UpdateManagerState
    data class Error(val message: String) : UpdateManagerState
}

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object ExportSuccess : BackupStatus
    data class ImportSuccess(val summary: ImportSummary) : BackupStatus
    data class Error(val message: String) : BackupStatus
}

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val backupStatus: BackupStatus = BackupStatus.Idle,
    val isBusy: Boolean = false,
    val isTestingGeminiKey: Boolean = false,
    val geminiTestResult: GeminiKeyTestResult? = null,
    val updateState: UpdateManagerState = UpdateManagerState.Idle
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val reminderScheduler: ReminderScheduler,
    private val productVisionClient: ProductVisionClient? = null,
    private val appUpdateManager: AppUpdateManager? = null
) : ViewModel() {

    private val backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    private val isBusy = MutableStateFlow(false)
    private val isTestingGeminiKey = MutableStateFlow(false)
    private val geminiTestResult = MutableStateFlow<GeminiKeyTestResult?>(null)
    private val updateState = MutableStateFlow<UpdateManagerState>(UpdateManagerState.Idle)
    private var downloadJob: Job? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        backupStatus,
        isBusy,
        isTestingGeminiKey,
        geminiTestResult,
        updateState
    ) { values ->
        SettingsUiState(
            settings = values[0] as UserSettings,
            backupStatus = values[1] as BackupStatus,
            isBusy = values[2] as Boolean,
            isTestingGeminiKey = values[3] as Boolean,
            geminiTestResult = values[4] as GeminiKeyTestResult?,
            updateState = values[5] as UpdateManagerState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(themeMode)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
        }
    }

    fun setAutoDeleteCheckInPhotos(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoDeleteCheckInPhotos(enabled)
        }
    }

    fun setBlockScreenshots(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBlockScreenshots(enabled)
        }
    }

    fun setAiWebSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAiWebSearchEnabled(enabled)
        }
    }

    fun setHaircutThresholdPercent(percent: Int) {
        viewModelScope.launch {
            settingsRepository.setHaircutThresholdPercent(percent)
        }
    }

    fun setShaveIntervalDays(days: Int) {
        viewModelScope.launch {
            settingsRepository.setShaveIntervalDays(days)
        }
    }

    fun setBirthDateIso(isoDate: String?) {
        viewModelScope.launch {
            settingsRepository.setBirthDateIso(isoDate)
        }
    }

    fun setGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepository.setGeminiApiKey(apiKey)
            geminiTestResult.value = null
        }
    }

    fun testGeminiApiKey(apiKey: String) {
        val client = productVisionClient ?: return
        viewModelScope.launch {
            isTestingGeminiKey.value = true
            geminiTestResult.value = null
            settingsRepository.setGeminiApiKey(apiKey)
            geminiTestResult.value = client.testApiKey(apiKey)
            isTestingGeminiKey.value = false
        }
    }

    fun setMorningReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.getSettingsOnce()
            settingsRepository.setMorningReminder(
                enabled = enabled,
                hour = current.morningReminderHour,
                minute = current.morningReminderMinute
            )
            reminderScheduler.syncReminders(
                current.copy(morningReminderEnabled = enabled)
            )
        }
    }

    fun adjustMorningReminderTime(deltaMinutes: Int) {
        viewModelScope.launch {
            val current = settingsRepository.getSettingsOnce()
            val (newHour, newMinute) = shiftTime(
                current.morningReminderHour,
                current.morningReminderMinute,
                deltaMinutes
            )
            settingsRepository.setMorningReminder(
                enabled = current.morningReminderEnabled,
                hour = newHour,
                minute = newMinute
            )
            reminderScheduler.syncReminders(
                current.copy(
                    morningReminderHour = newHour,
                    morningReminderMinute = newMinute
                )
            )
        }
    }

    fun setEveningReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.getSettingsOnce()
            settingsRepository.setEveningReminder(
                enabled = enabled,
                hour = current.eveningReminderHour,
                minute = current.eveningReminderMinute
            )
            reminderScheduler.syncReminders(
                current.copy(eveningReminderEnabled = enabled)
            )
        }
    }

    fun adjustEveningReminderTime(deltaMinutes: Int) {
        viewModelScope.launch {
            val current = settingsRepository.getSettingsOnce()
            val (newHour, newMinute) = shiftTime(
                current.eveningReminderHour,
                current.eveningReminderMinute,
                deltaMinutes
            )
            settingsRepository.setEveningReminder(
                enabled = current.eveningReminderEnabled,
                hour = newHour,
                minute = newMinute
            )
            reminderScheduler.syncReminders(
                current.copy(
                    eveningReminderHour = newHour,
                    eveningReminderMinute = newMinute
                )
            )
        }
    }

    fun setWaterReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.getSettingsOnce()
            settingsRepository.setWaterReminder(
                enabled = enabled,
                hour = current.waterReminderHour,
                minute = current.waterReminderMinute
            )
            reminderScheduler.syncReminders(
                current.copy(waterReminderEnabled = enabled)
            )
        }
    }

    fun adjustWaterReminderTime(deltaMinutes: Int) {
        viewModelScope.launch {
            val current = settingsRepository.getSettingsOnce()
            val (newHour, newMinute) = shiftTime(
                current.waterReminderHour,
                current.waterReminderMinute,
                deltaMinutes
            )
            settingsRepository.setWaterReminder(
                enabled = current.waterReminderEnabled,
                hour = newHour,
                minute = newMinute
            )
            reminderScheduler.syncReminders(
                current.copy(
                    waterReminderHour = newHour,
                    waterReminderMinute = newMinute
                )
            )
        }
    }

    fun exportBackup(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            isBusy.value = true
            val result = backupRepository.exportToUri(contentResolver, uri)
            backupStatus.value = result.fold(
                onSuccess = { BackupStatus.ExportSuccess },
                onFailure = { error -> BackupStatus.Error(error.message ?: "Export failed") }
            )
            isBusy.value = false
        }
    }

    fun importBackup(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            isBusy.value = true
            val result = backupRepository.importFromUri(contentResolver, uri)
            result.onSuccess {
                val updatedSettings = settingsRepository.getSettingsOnce()
                reminderScheduler.syncReminders(updatedSettings)
            }
            backupStatus.value = result.fold(
                onSuccess = { summary -> BackupStatus.ImportSuccess(summary) },
                onFailure = { error -> BackupStatus.Error(error.message ?: "Import failed") }
            )
            isBusy.value = false
        }
    }

    fun clearBackupStatus() {
        backupStatus.value = BackupStatus.Idle
    }

    fun checkForUpdates() {
        val manager = appUpdateManager ?: return
        viewModelScope.launch {
            updateState.value = UpdateManagerState.Checking
            updateState.value = when (val res = manager.checkForUpdate(BuildConfig.VERSION_NAME)) {
                is UpdateCheckResult.UpdateAvailable -> UpdateManagerState.Available(res.info)
                is UpdateCheckResult.UpToDate -> UpdateManagerState.UpToDate(res.latestVersion)
                is UpdateCheckResult.Error -> UpdateManagerState.Error(res.reason)
            }
        }
    }

    fun startUpdateDownload(info: AppUpdateInfo) {
        val manager = appUpdateManager ?: return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            updateState.value = UpdateManagerState.Downloading(
                info = info,
                downloadedBytes = 0L,
                totalBytes = info.apkSizeBytes
            )
            val result = manager.downloadAndVerifyUpdate(info) { downloaded, total ->
                updateState.value = UpdateManagerState.Downloading(
                    info = info,
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            }
            updateState.value = when (result) {
                is UpdateDownloadResult.ReadyToInstall -> UpdateManagerState.ReadyToInstall(
                    info = info,
                    apkUri = result.contentUri,
                    sha256Verified = result.sha256Verified
                )
                is UpdateDownloadResult.Error -> UpdateManagerState.Error(result.reason)
            }
        }
    }

    fun cancelUpdateDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val current = updateState.value
        updateState.value = if (current is UpdateManagerState.Downloading) {
            UpdateManagerState.Available(current.info)
        } else {
            UpdateManagerState.Idle
        }
    }

    private fun shiftTime(hour: Int, minute: Int, deltaMinutes: Int): Pair<Int, Int> {
        val totalMinutes = ((hour * 60 + minute + deltaMinutes) % 1440 + 1440) % 1440
        return (totalMinutes / 60) to (totalMinutes % 60)
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            backupRepository: BackupRepository,
            reminderScheduler: ReminderScheduler,
            productVisionClient: ProductVisionClient? = null,
            appUpdateManager: AppUpdateManager? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    settingsRepository = settingsRepository,
                    backupRepository = backupRepository,
                    reminderScheduler = reminderScheduler,
                    productVisionClient = productVisionClient,
                    appUpdateManager = appUpdateManager
                ) as T
            }
        }
    }
}
