package com.aiquota.mobile.local

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderUsageDisplayTextTest {
    @Test
    fun koreanLabelsTranslateSessionAndWeeklyWithoutProviderPrefixes() {
        val locale = Locale.KOREAN

        assertEquals("5시간 세션", displayUsageLabel("claude", "Claude Session", 0, locale))
        assertEquals("주간 세션", displayUsageLabel("claude", "Claude Weekly", 1, locale))
        assertEquals("5시간 세션", displayUsageLabel("codex", "Codex Session", 0, locale))
        assertEquals("주간 세션", displayUsageLabel("codex", "Codex Weekly", 1, locale))
    }

    @Test
    fun koreanLabelsTranslateGenericUsageWords() {
        val locale = Locale.KOREAN

        assertEquals("5시간 세션", displayUsageLabel("claude", "Session", 0, locale))
        assertEquals("주간 세션", displayUsageLabel("claude", "Weekly", 1, locale))
        assertEquals("인라인 제안", displayUsageLabel("copilot", "Inline suggestions", 0, locale))
    }

    @Test
    fun koreanGeminiLabelsUseLimitNames() {
        val locale = Locale.KOREAN

        assertEquals("5시간 한도", displayUsageLabel("gemini", "5-hour limit", 0, locale))
        assertEquals("주간 한도", displayUsageLabel("gemini", "Weekly limit", 1, locale))
        assertEquals("5시간 한도", displayUsageLabel("gemini", "Gemini Pro", 0, locale))
    }

    @Test
    fun koreanCodexSparkLabelsUseDisplayOnlyShortNames() {
        val locale = Locale.KOREAN

        assertEquals("GPT-5.3-Spark 5시간", displayUsageLabel("codex", "GPT-5.3-Codex-Spark 5h", 2, locale))
        assertEquals("GPT-5.3-Spark 주간", displayUsageLabel("codex", "GPT-5.3-Codex-Spark Weekly", 3, locale))
        assertEquals("GPT-5.3-Spark 5시간", displayUsageLabel("codex", "ChatGPT-5.3-Codex-Spark 5h", 2, locale))
        assertEquals("GPT-5.3-Spark 주간", displayUsageLabel("codex", "ChatGPT-5.3-Codex-Spark Weekly", 3, locale))
    }
}
