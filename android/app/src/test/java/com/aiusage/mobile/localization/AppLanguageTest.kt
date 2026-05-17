package com.aiusage.mobile.localization

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun koreanDeviceLanguageUsesKorean() {
        assertEquals(AppLanguage.KOREAN, appLanguageForDeviceLanguage("ko"))
        assertEquals(AppLanguage.KOREAN, appLanguageForDeviceLanguage("KO"))
    }

    @Test
    fun nonKoreanDeviceLanguageUsesEnglish() {
        assertEquals(AppLanguage.ENGLISH, appLanguageForDeviceLanguage("en"))
        assertEquals(AppLanguage.ENGLISH, appLanguageForDeviceLanguage("ja"))
        assertEquals(AppLanguage.ENGLISH, appLanguageForDeviceLanguage(""))
        assertEquals(AppLanguage.ENGLISH, appLanguageForDeviceLanguage(null))
    }

    @Test
    fun appLanguageMapsToResourceLocale() {
        assertEquals(Locale.KOREAN.language, localeForAppLanguage(AppLanguage.KOREAN).language)
        assertEquals(Locale.ENGLISH.language, localeForAppLanguage(AppLanguage.ENGLISH).language)
    }
}
