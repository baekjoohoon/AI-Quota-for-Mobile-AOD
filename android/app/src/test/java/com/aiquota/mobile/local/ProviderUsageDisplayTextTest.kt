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
    fun koreanOpenCodeLabelsUseLimitNames() {
        val locale = Locale.KOREAN

        assertEquals("5시간 한도", displayUsageLabel("opencode", "Go 5-Hour Limit", 0, locale))
        assertEquals("주간 한도", displayUsageLabel("opencode", "Go Weekly Limit", 1, locale))
        assertEquals("월간 한도", displayUsageLabel("opencode", "Go Monthly Limit", 2, locale))
    }

    @Test
    fun koreanGlmLabelsUseLimitNames() {
        val locale = Locale.KOREAN

        assertEquals("5시간 한도", displayUsageLabel("glm", "5-Hour Token Limit", 0, locale))
        assertEquals("주간 한도", displayUsageLabel("glm", "Weekly Token Limit", 1, locale))
        assertEquals("월간 한도", displayUsageLabel("glm", "MCP Monthly Quota", 2, locale))
    }

    @Test
    fun koreanCodexSparkLabelsUseDisplayOnlyShortNames() {
        val locale = Locale.KOREAN

        assertEquals("GPT-5.3-Spark 5H", displayUsageLabel("codex", "GPT-5.3-Codex-Spark 5h", 2, locale))
        assertEquals("GPT-5.3-Spark 주간", displayUsageLabel("codex", "GPT-5.3-Codex-Spark Weekly", 3, locale))
        assertEquals("GPT-5.3-Spark 5H", displayUsageLabel("codex", "ChatGPT-5.3-Codex-Spark 5h", 2, locale))
        assertEquals("GPT-5.3-Spark 주간", displayUsageLabel("codex", "ChatGPT-5.3-Codex-Spark Weekly", 3, locale))
        assertEquals(
            "GPT-5.3-Spark 5H",
            displayUsageLabel("codex", "GPT-5.3-Codex-Spark 5 hour usage limit", 2, locale)
        )
        assertEquals(
            "GPT-5.3-Spark 주간",
            displayUsageLabel("codex", "GPT-5.3-Codex-Spark Weekly usage limit", 3, locale)
        )
    }

    @Test
    fun englishCodexSparkLabelsUseWidgetFriendlyShortNames() {
        val locale = Locale.US

        assertEquals(
            "GPT-5.3-Spark 5H",
            displayUsageLabel("codex", "GPT-5.3-Codex-Spark 5 hour usage limit", 2, locale)
        )
        assertEquals(
            "GPT-5.3-Spark Week",
            displayUsageLabel("codex", "GPT-5.3-Codex-Spark Weekly usage limit", 3, locale)
        )
    }

    @Test
    fun koreanResetTextTranslatesCodexRelativeDurations() {
        val locale = Locale.KOREAN

        assertEquals("4일 3시간 후 초기화", displayResetTextForLocale("Resets in 4d 3h", locale))
        assertEquals("3시간 15분 후 초기화", displayResetTextForLocale("Resets in 3h 15m", locale))
    }
}
