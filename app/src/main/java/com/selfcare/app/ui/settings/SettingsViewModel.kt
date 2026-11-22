package com.selfcare.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.data.local.AppLanguage
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.remote.GeminiKeyTestResult
import com.selfcare.app.data.remote.ProductVisionClient
import com.selfcare.app.data.repository.BackupRepository
import com.selfcare.app.data.repository.ImportSummary
import com.selfcare.app.notifications.ReminderScheduler
import com.selfcare.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val geminiTestResult: GeminiKeyTestResult? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val reminderScheduler: ReminderScheduler,
    private val productVisionClient: ProductVisionClient? = null
) : ViewModel() {

    private val backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    private val isBusy = MutableStateFlow(false)
    private val isTestingGeminiKey = MutableStateFlow(false)
    private val geminiTestResult = MutableStateFlow<GeminiKeyTestResult?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        backupStatus,
        isBusy,
        isTestingGeminiKey,
        geminiTestResult
    ) { settings, status, busy, testingKey, keyResult ->
        SettingsUiState(
            settings = settings,
            backupStatus = status,
            isBusy = busy,
            isTestingGeminiKey = testingKey,
            geminiTestResult = keyResult
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

    fun setHaircutThresholdPercent(percent: Int) {
        viewModelScope.launch {
            settingsRepository.setHaircutThresholdPercent(percent)
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

    private fun shiftTime(hour: Int, minute: Int, deltaMinutes: Int): Pair<Int, Int> {
        val totalMinutes = ((hour * 60 + minute + deltaMinutes) % 1440 + 1440) % 1440
        return (totalMinutes / 60) to (totalMinutes % 60)
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            backupRepository: BackupRepository,
            reminderScheduler: ReminderScheduler,
            productVisionClient: ProductVisionClient? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    settingsRepository = settingsRepository,
                    backupRepository = backupRepository,
                    reminderScheduler = reminderScheduler,
                    productVisionClient = productVisionClient
                ) as T
            }
        }
    }
}
