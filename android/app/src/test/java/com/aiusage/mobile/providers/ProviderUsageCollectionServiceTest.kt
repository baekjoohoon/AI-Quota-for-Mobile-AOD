package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderUsageCollectionServiceTest {
    @Test
    fun waitsForClaudeDirectUsageApiBeforeExistingUsageFallback() {
        assertTrue(
            shouldWaitForClaudeDirectUsageResult(
                provider = ProviderId.CLAUDE,
                directUsageStarted = true,
                completed = false,
                url = "https://claude.ai/api/organizations/6d7e7f53-6216-45b3-93bb-764f73f98c92/usage"
            )
        )
    }

    @Test
    fun doesNotWaitForClaudeDirectUsageAfterLeavingUsageApi() {
        assertFalse(
            shouldWaitForClaudeDirectUsageResult(
                provider = ProviderId.CLAUDE,
                directUsageStarted = true,
                completed = false,
                url = "https://claude.ai/settings/billing"
            )
        )
    }

    @Test
    fun doesNotTreatClaudeUsageLimitsAsDirectUsageApi() {
        assertFalse(
            shouldWaitForClaudeDirectUsageResult(
                provider = ProviderId.CLAUDE,
                directUsageStarted = true,
                completed = false,
                url = "https://claude.ai/api/organizations/6d7e7f53-6216-45b3-93bb-764f73f98c92/usage_limits"
            )
        )
    }

    @Test
    fun doesNotBlockOtherProviders() {
        assertFalse(
            shouldWaitForClaudeDirectUsageResult(
                provider = ProviderId.GEMINI,
                directUsageStarted = true,
                completed = false,
                url = "https://gemini.google.com/app"
            )
        )
    }

    @Test
    fun waitsForCursorAuthenticatorRedirectInsteadOfAdvancingProbe() {
        assertTrue(
            shouldWaitForCursorAuthenticatorRedirect(
                provider = ProviderId.CURSOR,
                completed = false,
                attempt = 0,
                url = "https://authenticator.cursor.sh/"
            )
        )
    }

    @Test
    fun stopsWaitingForCursorAuthenticatorAfterMaxAttempts() {
        assertFalse(
            shouldWaitForCursorAuthenticatorRedirect(
                provider = ProviderId.CURSOR,
                completed = false,
                attempt = 8,
                url = "https://authenticator.cursor.sh/"
            )
        )
    }
}
