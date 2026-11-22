package com.selfcare.app.ui.localization

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.selfcare.app.data.local.AppLanguage
import java.util.Locale

object LocaleHelper {

    fun resolveLocale(language: AppLanguage): Locale {
        return when (language) {
            AppLanguage.EN -> Locale.forLanguageTag("en")
            AppLanguage.HU -> Locale.forLanguageTag("hu")
            AppLanguage.AUTO -> {
                val sysLocales = Resources.getSystem().configuration.locales
                if (!sysLocales.isEmpty) sysLocales[0] else Locale.getDefault()
            }
        }
    }

    fun createLocalizedContext(baseContext: Context, language: AppLanguage): Context {
        val targetLocale = resolveLocale(language)
        val config = Configuration(baseContext.resources.configuration).apply {
            setLocale(targetLocale)
            setLayoutDirection(targetLocale)
        }
        return baseContext.createConfigurationContext(config)
    }

    fun syncPlatformLocale(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
                localeManager.applicationLocales = when (language) {
                    AppLanguage.AUTO -> LocaleList.getEmptyLocaleList()
                    AppLanguage.EN -> LocaleList.forLanguageTags("en")
                    AppLanguage.HU -> LocaleList.forLanguageTags("hu")
                }
            }
        }
    }
}
