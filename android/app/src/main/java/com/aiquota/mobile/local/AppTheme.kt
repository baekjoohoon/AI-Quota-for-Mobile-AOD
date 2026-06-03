package com.aiquota.mobile.local

import android.content.Context
import android.content.SharedPreferences

enum class AppTheme(val storageId: String) {
    MACOS("macos"),
    WINDOWS("windows");

    companion object {
        val DEFAULT = MACOS

        fun fromStorageId(value: String?): AppTheme {
            val normalized = value.orEmpty().trim()
            return entries.firstOrNull { it.storageId.equals(normalized, ignoreCase = true) }
                ?: DEFAULT
        }

        fun selectableThemes(): List<AppTheme> {
            return listOf(MACOS, WINDOWS)
        }

        fun shouldMigrateLegacyWindowsDefault(storedThemeId: String?, migrationComplete: Boolean): Boolean {
            return !migrationComplete &&
                storedThemeId.orEmpty().trim().equals(WINDOWS.storageId, ignoreCase = true)
        }
    }
}

class ThemePreferencesRepository(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    init {
        val storedThemeId = preferences.getString(KEY_SELECTED_THEME, null)
        val migrationComplete = preferences.getBoolean(KEY_MACOS_DEFAULT_MIGRATED, false)
        val resolvedTheme = when {
            !preferences.contains(KEY_SELECTED_THEME) -> AppTheme.DEFAULT
            AppTheme.shouldMigrateLegacyWindowsDefault(storedThemeId, migrationComplete) -> AppTheme.MACOS
            else -> AppTheme.fromStorageId(storedThemeId)
        }

        preferences.edit()
            .putString(KEY_SELECTED_THEME, resolvedTheme.storageId)
            .putBoolean(KEY_MACOS_DEFAULT_MIGRATED, true)
            .apply()
    }

    fun currentTheme(): AppTheme {
        return AppTheme.fromStorageId(preferences.getString(KEY_SELECTED_THEME, AppTheme.DEFAULT.storageId))
    }

    fun saveTheme(theme: AppTheme) {
        preferences.edit()
            .putString(KEY_SELECTED_THEME, theme.storageId)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "ai_quota_theme_preferences"
        private const val KEY_SELECTED_THEME = "selected_theme"
        private const val KEY_MACOS_DEFAULT_MIGRATED = "macos_default_migrated"
    }
}
