package com.aiusage.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {
    @Test
    fun windowsThemeIsStoredAsCurrentThemeId() {
        assertEquals("windows", AppTheme.WINDOWS.storageId)
        assertEquals(AppTheme.WINDOWS, AppTheme.fromStorageId("windows"))
    }

    @Test
    fun macosThemeIsDefaultThemeId() {
        assertEquals("macos", AppTheme.MACOS.storageId)
        assertEquals(AppTheme.MACOS, AppTheme.DEFAULT)
        assertEquals(AppTheme.MACOS, AppTheme.fromStorageId("macos"))
    }

    @Test
    fun unknownStoredThemeFallsBackToMacosTheme() {
        assertEquals(AppTheme.MACOS, AppTheme.fromStorageId(""))
        assertEquals(AppTheme.MACOS, AppTheme.fromStorageId("unknown"))
    }

    @Test
    fun selectableThemesKeepWindowsAndMacos() {
        assertEquals(listOf(AppTheme.MACOS, AppTheme.WINDOWS), AppTheme.selectableThemes())
    }

    @Test
    fun legacyWindowsDefaultMigratesToMacosOnlyBeforeMigrationCompletes() {
        assertEquals(true, AppTheme.shouldMigrateLegacyWindowsDefault("windows", false))
        assertEquals(false, AppTheme.shouldMigrateLegacyWindowsDefault("windows", true))
        assertEquals(false, AppTheme.shouldMigrateLegacyWindowsDefault("macos", false))
    }
}
