package com.selfcare.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.selfcare.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AppLanguage(val languageTag: String?) {
    AUTO(null),
    EN("en"),
    HU("hu");

    companion object {
        fun fromName(name: String?): AppLanguage =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTO
    }
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val appLanguage: AppLanguage = AppLanguage.AUTO,
    val morningReminderEnabled: Boolean = false,
    val morningReminderHour: Int = 8,
    val morningReminderMinute: Int = 0,
    val eveningReminderEnabled: Boolean = false,
    val eveningReminderHour: Int = 21,
    val eveningReminderMinute: Int = 0,
    val autoDeleteCheckInPhotos: Boolean = false,
    val haircutThresholdPercent: Int = DEFAULT_HAIRCUT_THRESHOLD_PERCENT
) {
    companion object {
        const val DEFAULT_HAIRCUT_THRESHOLD_PERCENT = 20
        const val MIN_HAIRCUT_THRESHOLD_PERCENT = 5
        const val MAX_HAIRCUT_THRESHOLD_PERCENT = 60
    }
}

private val Context.selfCareSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "selfcare_settings"
)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            themeMode = ThemeMode.entries.firstOrNull {
                it.name == prefs[KEY_THEME_MODE]
            } ?: ThemeMode.DARK,
            appLanguage = AppLanguage.fromName(prefs[KEY_APP_LANGUAGE]),
            morningReminderEnabled = prefs[KEY_MORNING_REMINDER_ENABLED] ?: false,
            morningReminderHour = (prefs[KEY_MORNING_REMINDER_HOUR] ?: 8).coerceIn(0, 23),
            morningReminderMinute = (prefs[KEY_MORNING_REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
            eveningReminderEnabled = prefs[KEY_EVENING_REMINDER_ENABLED] ?: false,
            eveningReminderHour = (prefs[KEY_EVENING_REMINDER_HOUR] ?: 21).coerceIn(0, 23),
            eveningReminderMinute = (prefs[KEY_EVENING_REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
            autoDeleteCheckInPhotos = prefs[KEY_AUTO_DELETE_CHECKIN_PHOTOS] ?: false,
            haircutThresholdPercent = (prefs[KEY_HAIRCUT_THRESHOLD_PERCENT]
                ?: UserSettings.DEFAULT_HAIRCUT_THRESHOLD_PERCENT)
                .coerceIn(
                    UserSettings.MIN_HAIRCUT_THRESHOLD_PERCENT,
                    UserSettings.MAX_HAIRCUT_THRESHOLD_PERCENT
                )
        )
    }

    suspend fun getSettingsOnce(): UserSettings = settings.first()

    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = themeMode.name
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { prefs ->
            prefs[KEY_APP_LANGUAGE] = language.name
        }
    }

    suspend fun setMorningReminder(enabled: Boolean, hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_MORNING_REMINDER_ENABLED] = enabled
            prefs[KEY_MORNING_REMINDER_HOUR] = hour.coerceIn(0, 23)
            prefs[KEY_MORNING_REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setEveningReminder(enabled: Boolean, hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_EVENING_REMINDER_ENABLED] = enabled
            prefs[KEY_EVENING_REMINDER_HOUR] = hour.coerceIn(0, 23)
            prefs[KEY_EVENING_REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setAutoDeleteCheckInPhotos(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_DELETE_CHECKIN_PHOTOS] = enabled
        }
    }

    suspend fun setHaircutThresholdPercent(percent: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_HAIRCUT_THRESHOLD_PERCENT] = percent.coerceIn(
                UserSettings.MIN_HAIRCUT_THRESHOLD_PERCENT,
                UserSettings.MAX_HAIRCUT_THRESHOLD_PERCENT
            )
        }
    }

    suspend fun replaceAll(userSettings: UserSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = userSettings.themeMode.name
            prefs[KEY_APP_LANGUAGE] = userSettings.appLanguage.name
            prefs[KEY_MORNING_REMINDER_ENABLED] = userSettings.morningReminderEnabled
            prefs[KEY_MORNING_REMINDER_HOUR] = userSettings.morningReminderHour.coerceIn(0, 23)
            prefs[KEY_MORNING_REMINDER_MINUTE] = userSettings.morningReminderMinute.coerceIn(0, 59)
            prefs[KEY_EVENING_REMINDER_ENABLED] = userSettings.eveningReminderEnabled
            prefs[KEY_EVENING_REMINDER_HOUR] = userSettings.eveningReminderHour.coerceIn(0, 23)
            prefs[KEY_EVENING_REMINDER_MINUTE] = userSettings.eveningReminderMinute.coerceIn(0, 59)
            prefs[KEY_AUTO_DELETE_CHECKIN_PHOTOS] = userSettings.autoDeleteCheckInPhotos
            prefs[KEY_HAIRCUT_THRESHOLD_PERCENT] = userSettings.haircutThresholdPercent.coerceIn(
                UserSettings.MIN_HAIRCUT_THRESHOLD_PERCENT,
                UserSettings.MAX_HAIRCUT_THRESHOLD_PERCENT
            )
        }
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_MORNING_REMINDER_ENABLED = booleanPreferencesKey("morning_reminder_enabled")
        private val KEY_MORNING_REMINDER_HOUR = intPreferencesKey("morning_reminder_hour")
        private val KEY_MORNING_REMINDER_MINUTE = intPreferencesKey("morning_reminder_minute")
        private val KEY_EVENING_REMINDER_ENABLED = booleanPreferencesKey("evening_reminder_enabled")
        private val KEY_EVENING_REMINDER_HOUR = intPreferencesKey("evening_reminder_hour")
        private val KEY_EVENING_REMINDER_MINUTE = intPreferencesKey("evening_reminder_minute")
        private val KEY_AUTO_DELETE_CHECKIN_PHOTOS =
            booleanPreferencesKey("auto_delete_checkin_photos")
        private val KEY_HAIRCUT_THRESHOLD_PERCENT =
            intPreferencesKey("haircut_threshold_percent")

        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.selfCareSettingsDataStore)
    }
}
