package com.selfcare.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    val appLanguage: AppLanguage = AppLanguage.AUTO,
    val morningReminderEnabled: Boolean = false,
    val morningReminderHour: Int = 8,
    val morningReminderMinute: Int = 0,
    val eveningReminderEnabled: Boolean = false,
    val eveningReminderHour: Int = 21,
    val eveningReminderMinute: Int = 0,
    val autoDeleteCheckInPhotos: Boolean = false,
    val haircutThresholdPercent: Int = DEFAULT_HAIRCUT_THRESHOLD_PERCENT,
    val geminiApiKey: String = "",
    val shaveIntervalDays: Int = DEFAULT_SHAVE_INTERVAL_DAYS,
    val lastShaveEpochDay: Long? = null,
    val birthDateIso: String? = null,
    val blockScreenshots: Boolean = false,
    val lastSeenVersionCode: Int = 0,
    val dailyCalorieTarget: Int = DEFAULT_CALORIE_TARGET,
    val dailyProteinTargetGrams: Int = DEFAULT_PROTEIN_TARGET,
    val dailyCarbsTargetGrams: Int = DEFAULT_CARBS_TARGET,
    val dailyFatTargetGrams: Int = DEFAULT_FAT_TARGET,
    val dailyWaterTargetMl: Int = DEFAULT_WATER_TARGET_ML,
    val bodyWeightKg: Float? = null,
    val targetWeightKg: Float? = null,
    val bodyHeightCm: Int? = null,
    val bodyIsMale: Boolean = true,
    val bodyActivityLevel: String = "MODERATE",
    val bodyMacroGoal: String = "MAINTAIN"
) {
    companion object {
        const val DEFAULT_HAIRCUT_THRESHOLD_PERCENT = 20
        const val MIN_HAIRCUT_THRESHOLD_PERCENT = 5
        const val MAX_HAIRCUT_THRESHOLD_PERCENT = 60
        const val DEFAULT_SHAVE_INTERVAL_DAYS = 3
        const val MIN_SHAVE_INTERVAL_DAYS = 1
        const val MAX_SHAVE_INTERVAL_DAYS = 14
        const val DEFAULT_CALORIE_TARGET = 2200
        const val DEFAULT_PROTEIN_TARGET = 150
        const val DEFAULT_CARBS_TARGET = 220
        const val DEFAULT_FAT_TARGET = 70
        const val DEFAULT_WATER_TARGET_ML = 2500
    }
}

private val Context.selfCareSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "selfcare_settings"
)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val secretCipher: SecretTextCipher = AndroidKeystoreSecretCipher
) {

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        val decryptedApiKey = prefs[KEY_GEMINI_API_KEY_ENC]
            ?.let { secretCipher.decrypt(it) }
            .orEmpty()
        UserSettings(
            themeMode = ThemeMode.entries.firstOrNull {
                it.name == prefs[KEY_THEME_MODE]
            } ?: ThemeMode.AMOLED,
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
                ),
            geminiApiKey = decryptedApiKey,
            shaveIntervalDays = (prefs[KEY_SHAVE_INTERVAL_DAYS]
                ?: UserSettings.DEFAULT_SHAVE_INTERVAL_DAYS)
                .coerceIn(UserSettings.MIN_SHAVE_INTERVAL_DAYS, UserSettings.MAX_SHAVE_INTERVAL_DAYS),
            lastShaveEpochDay = prefs[KEY_LAST_SHAVE_EPOCH_DAY],
            birthDateIso = prefs[KEY_BIRTH_DATE_ISO]?.takeIf { it.isNotBlank() },
            blockScreenshots = prefs[KEY_BLOCK_SCREENSHOTS] ?: false,
            lastSeenVersionCode = prefs[KEY_LAST_SEEN_VERSION_CODE] ?: 0,
            dailyCalorieTarget = (prefs[KEY_DAILY_CALORIE_TARGET] ?: UserSettings.DEFAULT_CALORIE_TARGET).coerceIn(1000, 6000),
            dailyProteinTargetGrams = (prefs[KEY_DAILY_PROTEIN_TARGET] ?: UserSettings.DEFAULT_PROTEIN_TARGET).coerceIn(40, 400),
            dailyCarbsTargetGrams = (prefs[KEY_DAILY_CARBS_TARGET] ?: UserSettings.DEFAULT_CARBS_TARGET).coerceIn(20, 700),
            dailyFatTargetGrams = (prefs[KEY_DAILY_FAT_TARGET] ?: UserSettings.DEFAULT_FAT_TARGET).coerceIn(20, 250),
            dailyWaterTargetMl = (prefs[KEY_DAILY_WATER_TARGET_ML] ?: UserSettings.DEFAULT_WATER_TARGET_ML).coerceIn(1000, 6000),
            bodyWeightKg = prefs[KEY_BODY_WEIGHT_KG]?.coerceIn(30f, 250f),
            targetWeightKg = prefs[KEY_TARGET_WEIGHT_KG]?.coerceIn(30f, 250f),
            bodyHeightCm = prefs[KEY_BODY_HEIGHT_CM]?.coerceIn(120, 240),
            bodyIsMale = prefs[KEY_BODY_IS_MALE] ?: true,
            bodyActivityLevel = prefs[KEY_BODY_ACTIVITY_LEVEL] ?: "MODERATE",
            bodyMacroGoal = prefs[KEY_BODY_MACRO_GOAL] ?: "MAINTAIN"
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

    suspend fun setBlockScreenshots(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_BLOCK_SCREENSHOTS] = enabled
        }
    }

    suspend fun setLastSeenVersionCode(versionCode: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SEEN_VERSION_CODE] = versionCode
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

    suspend fun setShaveIntervalDays(days: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_SHAVE_INTERVAL_DAYS] = days.coerceIn(
                UserSettings.MIN_SHAVE_INTERVAL_DAYS,
                UserSettings.MAX_SHAVE_INTERVAL_DAYS
            )
        }
    }

    suspend fun setLastShaveEpochDay(epochDay: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SHAVE_EPOCH_DAY] = epochDay
        }
    }

    suspend fun setBirthDateIso(isoDate: String?) {
        val clean = isoDate?.trim().orEmpty()
        dataStore.edit { prefs ->
            if (clean.isEmpty()) {
                prefs.remove(KEY_BIRTH_DATE_ISO)
            } else {
                prefs[KEY_BIRTH_DATE_ISO] = clean
            }
        }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        val clean = apiKey.trim()
        dataStore.edit { prefs ->
            // Always scrub any legacy plaintext key
            prefs.remove(KEY_GEMINI_API_KEY)
            if (clean.isEmpty()) {
                prefs.remove(KEY_GEMINI_API_KEY_ENC)
            } else {
                val encrypted = secretCipher.encrypt(clean)
                if (!encrypted.isNullOrEmpty()) {
                    prefs[KEY_GEMINI_API_KEY_ENC] = encrypted
                } else {
                    prefs.remove(KEY_GEMINI_API_KEY_ENC)
                }
            }
        }
    }

    suspend fun updateNutritionTargets(
        caloriesKcal: Int,
        proteinGrams: Int,
        carbsGrams: Int,
        fatGrams: Int,
        waterMl: Int
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_DAILY_CALORIE_TARGET] = caloriesKcal.coerceIn(1000, 6000)
            prefs[KEY_DAILY_PROTEIN_TARGET] = proteinGrams.coerceIn(40, 400)
            prefs[KEY_DAILY_CARBS_TARGET] = carbsGrams.coerceIn(20, 700)
            prefs[KEY_DAILY_FAT_TARGET] = fatGrams.coerceIn(20, 250)
            prefs[KEY_DAILY_WATER_TARGET_ML] = waterMl.coerceIn(1000, 6000)
        }
    }

    suspend fun saveBodyProfile(
        weightKg: Float?,
        targetWeightKg: Float?,
        heightCm: Int?,
        isMale: Boolean,
        activityLevel: String,
        macroGoal: String
    ) {
        dataStore.edit { prefs ->
            if (weightKg != null) prefs[KEY_BODY_WEIGHT_KG] = weightKg.coerceIn(30f, 250f) else prefs.remove(KEY_BODY_WEIGHT_KG)
            if (targetWeightKg != null) prefs[KEY_TARGET_WEIGHT_KG] = targetWeightKg.coerceIn(30f, 250f) else prefs.remove(KEY_TARGET_WEIGHT_KG)
            if (heightCm != null) prefs[KEY_BODY_HEIGHT_CM] = heightCm.coerceIn(120, 240) else prefs.remove(KEY_BODY_HEIGHT_CM)
            prefs[KEY_BODY_IS_MALE] = isMale
            prefs[KEY_BODY_ACTIVITY_LEVEL] = activityLevel
            prefs[KEY_BODY_MACRO_GOAL] = macroGoal
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
            prefs[KEY_SHAVE_INTERVAL_DAYS] = userSettings.shaveIntervalDays.coerceIn(
                UserSettings.MIN_SHAVE_INTERVAL_DAYS,
                UserSettings.MAX_SHAVE_INTERVAL_DAYS
            )
            if (userSettings.birthDateIso.isNullOrBlank()) {
                prefs.remove(KEY_BIRTH_DATE_ISO)
            } else {
                prefs[KEY_BIRTH_DATE_ISO] = userSettings.birthDateIso.trim()
            }
            prefs[KEY_BLOCK_SCREENSHOTS] = userSettings.blockScreenshots
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
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_GEMINI_API_KEY_ENC = stringPreferencesKey("gemini_api_key_enc")
        private val KEY_SHAVE_INTERVAL_DAYS = intPreferencesKey("shave_interval_days")
        private val KEY_LAST_SHAVE_EPOCH_DAY = longPreferencesKey("last_shave_epoch_day")
        private val KEY_BIRTH_DATE_ISO = stringPreferencesKey("birth_date_iso")
        private val KEY_BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
        private val KEY_LAST_SEEN_VERSION_CODE = intPreferencesKey("last_seen_version_code")
        private val KEY_DAILY_CALORIE_TARGET = intPreferencesKey("daily_calorie_target")
        private val KEY_DAILY_PROTEIN_TARGET = intPreferencesKey("daily_protein_target")
        private val KEY_DAILY_CARBS_TARGET = intPreferencesKey("daily_carbs_target")
        private val KEY_DAILY_FAT_TARGET = intPreferencesKey("daily_fat_target")
        private val KEY_DAILY_WATER_TARGET_ML = intPreferencesKey("daily_water_target_ml")
        private val KEY_BODY_WEIGHT_KG = floatPreferencesKey("body_weight_kg")
        private val KEY_TARGET_WEIGHT_KG = floatPreferencesKey("target_weight_kg")
        private val KEY_BODY_HEIGHT_CM = intPreferencesKey("body_height_cm")
        private val KEY_BODY_IS_MALE = booleanPreferencesKey("body_is_male")
        private val KEY_BODY_ACTIVITY_LEVEL = stringPreferencesKey("body_activity_level")
        private val KEY_BODY_MACRO_GOAL = stringPreferencesKey("body_macro_goal")

        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.selfCareSettingsDataStore)
    }
}
