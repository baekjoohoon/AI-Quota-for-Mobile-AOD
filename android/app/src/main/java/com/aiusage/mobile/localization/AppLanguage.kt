package com.aiusage.mobile.localization

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

enum class AppLanguage {
    KOREAN,
    ENGLISH
}

fun appLanguageForDeviceLanguage(language: String?): AppLanguage {
    return if (language.equals("ko", ignoreCase = true)) {
        AppLanguage.KOREAN
    } else {
        AppLanguage.ENGLISH
    }
}

fun Context.withAppLanguageForDeviceLanguage(): Context {
    val deviceLanguage = resources.configuration.primaryLanguage()
    return withAppLanguage(appLanguageForDeviceLanguage(deviceLanguage))
}

fun Context.withAppLanguage(appLanguage: AppLanguage): Context {
    val locale = localeForAppLanguage(appLanguage)
    val localizedConfiguration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(localizedConfiguration)
}

fun localeForAppLanguage(appLanguage: AppLanguage): Locale {
    return when (appLanguage) {
        AppLanguage.KOREAN -> Locale.KOREAN
        AppLanguage.ENGLISH -> Locale.ENGLISH
    }
}

private fun Configuration.primaryLanguage(): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        locales.get(0)?.language
    } else {
        @Suppress("DEPRECATION")
        locale?.language
    }
}
