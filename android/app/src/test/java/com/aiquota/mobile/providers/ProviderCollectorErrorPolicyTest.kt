package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCollectorErrorPolicyTest {
    @Test
    fun codexUsageUnavailableIsPayloadFailureNotInteractiveAuth() {
        val failure = ProviderCollectorErrorPolicy.failureFor(
            providerId = ProviderId.CODEX,
            rawError = """
            {
              "provider": "codex",
              "errorKind": "codex_usage_unavailable",
              "message": "Codex session reached, but trusted usage payload was not available. diagnostics={\"sessionStatus\":200,\"meStatus\":200,\"accountCheckStatus\":200,\"usageOk\":false,\"rowsUsagePresent\":false}"
            }
            """.trimIndent()
        )

        assertEquals(ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD, failure.kind)
        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderId.CODEX, failure.kind))
    }

    @Test
    fun codexAuthRequiredStaysInteractiveAuth() {
        val failure = ProviderCollectorErrorPolicy.failureFor(
            providerId = ProviderId.CODEX,
            rawError = """{"provider":"codex","errorKind":"codex_auth_required"}"""
        )

        assertEquals(ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED, failure.kind)
        assertTrue(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderId.CODEX, failure.kind))
    }

    @Test
    fun codexUsageUnavailableRetriesBeforeFailing() {
        assertTrue(CodexCollectorRetryPolicy.shouldRetry(ProviderId.CODEX, "codex_usage_unavailable", retryCount = 0))
        assertTrue(CodexCollectorRetryPolicy.shouldRetry(ProviderId.CODEX, "codex_usage_unavailable", retryCount = 1))
        assertFalse(CodexCollectorRetryPolicy.shouldRetry(ProviderId.CODEX, "codex_usage_unavailable", retryCount = 2))
        assertFalse(CodexCollectorRetryPolicy.shouldRetry(ProviderId.CLAUDE, "codex_usage_unavailable", retryCount = 0))
        assertFalse(CodexCollectorRetryPolicy.shouldRetry(ProviderId.CODEX, "codex_auth_required", retryCount = 0))
    }

    @Test
    fun diagnosticsDoNotRequireRawTokenCookieOrHtml() {
        val service = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertFalse(service.contains("Log.d(TAG, rawPayload"))
        assertFalse(service.contains("Log.e(TAG, rawPayload"))
        assertFalse(service.contains("Log.d(TAG, cookiesFor"))
        assertFalse(service.contains("Log.e(TAG, cookiesFor"))
    }
}
