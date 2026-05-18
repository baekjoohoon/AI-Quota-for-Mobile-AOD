package com.aiusage.mobile.local

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderUsageDisplayTextTest {
    @Test
    fun koreanLabelsTranslateKnownProviderUsageTitles() {
        val korean = Locale.KOREAN

        assertEquals("Codex 5시간 한도", displayUsageLabel("codex", "Codex 5-hour limit", 0, korean))
        assertEquals("Codex 주간 한도", displayUsageLabel("codex", "Codex weekly limit", 1, korean))
        assertEquals("Claude 5시간 한도", displayUsageLabel("claude", "Five_hour", 0, korean))
        assertEquals("Claude 주간 한도", displayUsageLabel("claude", "Seven_day", 1, korean))
        assertEquals("세션", displayUsageLabel("claude", "Rate_limit", 0, korean))
        assertEquals("주간 한도", displayUsageLabel("claude", "Rate_limit", 1, korean))
        assertEquals("채팅", displayUsageLabel("copilot", "Chat", 0, korean))
        assertEquals("자동완성", displayUsageLabel("copilot", "Completions", 1, korean))
        assertEquals("전체 사용량", displayUsageLabel("cursor", "Total usage", 0, korean))
        assertEquals("포함 사용량", displayUsageLabel("cursor", "Included usage", 0, korean))
    }

    @Test
    fun koreanRemainingAndResetTextUseKoreanUnits() {
        val korean = Locale.KOREAN

        assertEquals("99% 남음", displayRemainingText("99% left", korean))
        assertEquals("12 크레딧 남음", displayRemainingText("12 credits left", korean))
        assertEquals("4시간 44분 후 초기화", displayResetTextForLocale("Resets in 4h 44m", korean))
        assertEquals("6일 13시간 후 초기화", displayResetTextForLocale("Resets in 6d 13h", korean))
        assertEquals("메시지를 보내면 시작", displayResetTextForLocale("Starts when a message is sent", korean))
    }

    @Test
    fun englishLocaleKeepsOriginalProviderUsageText() {
        val english = Locale.ENGLISH

        assertEquals("Codex 5-hour limit", displayUsageLabel("codex", "Codex 5-hour limit", 0, english))
        assertEquals("Claude 5-hour limit", displayUsageLabel("claude", "Five_hour", 0, english))
        assertEquals("Session", displayUsageLabel("claude", "Rate_limit", 0, english))
        assertEquals("99% left", displayRemainingText("99% left", english))
        assertEquals("Resets in 4h 44m", displayResetTextForLocale("Resets in 4h 44m", english))
    }
}
