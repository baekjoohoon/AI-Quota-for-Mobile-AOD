package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderWebCollectorScriptsTest {
    @Test
    fun collectorRunsOnlyAfterProviderShellsAreReached() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CLAUDE, "https://claude.ai/new", mapOf("lastActiveOrg" to "org_123"), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CODEX, "https://chatgpt.com/", emptyMap(), "ChatGPT"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GEMINI, "https://gemini.google.com/app", emptyMap(), "Gemini"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.COPILOT, "about:blank", emptyMap(), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.COPILOT, "https://github.com/settings/copilot", emptyMap(), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.COPILOT, "https://github.com/settings/billing/premium_requests_usage", emptyMap(), ""))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CURSOR, "https://cursor.com/dashboard", emptyMap(), ""))

        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CLAUDE, "https://claude.ai/login", emptyMap(), ""))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CLAUDE, "https://claude.ai/login", mapOf("lastActiveOrg" to "org_123"), "Claude"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CODEX, "https://chatgpt.com/auth/login", emptyMap(), "ChatGPT"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GEMINI, "https://accounts.google.com/signin", emptyMap(), ""))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.COPILOT, "https://github.com/login", emptyMap(), ""))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.COPILOT, "https://github.com/", mapOf("logged_in" to "yes"), ""))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.CURSOR, "https://api.workos.com/sso/authorize", emptyMap(), ""))
    }

    @Test
    fun geminiCollectorRunsOnlyOnAuthenticatedAppShell() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GEMINI, "https://gemini.google.com/app", emptyMap(), "Gemini"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.GEMINI, "https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuota"))

        assertFalse(ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GEMINI, "https://accounts.google.com/signin", emptyMap(), ""))
        assertFalse(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.GEMINI, "https://accounts.google.com/signin"))
    }

    @Test
    fun claudeCollectorCanStartFromAuthenticatedSpaApiResources() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CLAUDE, "https://claude.ai/api/account_profile"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CLAUDE, "https://claude.ai/api/organizations/org_123/subscription_details?cached=true"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CLAUDE, "https://claude.ai/api/bootstrap/org_123/current_user_access"))

        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CLAUDE, "https://claude.ai/login"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CLAUDE, "https://accounts.google.com/signin/v2/challenge/sms"))
    }

    @Test
    fun codexCollectorCanStartFromAuthenticatedChatGptResources() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CODEX, "https://chatgpt.com/backend-api/wham/usage"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CODEX, "https://chatgpt.com/backend-api/me"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CODEX, "https://chatgpt.com/backend-api/accounts/check/v4-2023-04-27"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CODEX, "https://chatgpt.com/backend-api/subscriptions?account_id=301d47ae-f627-4ddc-b2c2-330419bdc6ba"))

        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorFromResource(ProviderId.CODEX, "https://chatgpt.com/auth/login", "https://chatgpt.com/backend-api/wham/usage"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorFromResource(ProviderId.CODEX, "https://chatgpt.com/", "https://chatgpt.com/backend-api/wham/usage"))
    }

    @Test
    fun cursorCollectorCanStartFromDashboardUsageResources() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CURSOR, "https://cursor.com/api/usage?user=user_123"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.CURSOR, "https://cursor.com/api/usage-summary"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorFromResource(ProviderId.CURSOR, "https://cursor.com/dashboard", "https://cursor.com/api/usage?user=user_123"))
        assertFalse(ProviderWebCollectorScripts.shouldRunCollectorFromResource(ProviderId.CURSOR, "https://cursor.com/login", "https://cursor.com/api/usage?user=user_123"))
    }

    @Test
    fun copilotCollectorCanStartFromBillingResources() {
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.COPILOT, "https://github.com/github-copilot/chat/entitlement"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.COPILOT, "https://github.com/github-copilot/chat/token"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.COPILOT, "https://github.com/settings/billing/premium_requests_usage"))
        assertTrue(ProviderWebCollectorScripts.shouldRunCollectorOnResource(ProviderId.COPILOT, "https://github.com/settings/billing/copilot_usage_card?customer_id=abc123&period=3&query="))
        assertTrue(
            ProviderWebCollectorScripts.shouldRunCollectorFromResource(
                ProviderId.COPILOT,
                "about:blank",
                "https://github.com/github-copilot/chat/entitlement"
            )
        )
        assertTrue(
            ProviderWebCollectorScripts.shouldRunCollectorFromResource(
                ProviderId.COPILOT,
                "https://github.com/settings/billing/premium_requests_usage",
                "https://github.com/settings/billing/copilot_usage_card?customer_id=abc123&period=3&query="
            )
        )
        assertFalse(
            ProviderWebCollectorScripts.shouldRunCollectorFromResource(
                ProviderId.COPILOT,
                "https://github.com/login",
                "https://github.com/settings/billing/premium_requests_usage"
            )
        )
    }

    @Test
    fun claudeResourceTriggerDoesNotStartCollectorWhileMainPageIsLogin() {
        assertFalse(
            ProviderWebCollectorScripts.shouldRunCollectorFromResource(
                ProviderId.CLAUDE,
                "https://claude.ai/login",
                "https://claude.ai/api/bootstrap/org_123/current_user_access"
            )
        )
        assertTrue(
            ProviderWebCollectorScripts.shouldRunCollectorFromResource(
                ProviderId.CLAUDE,
                "https://claude.ai/new",
                "https://claude.ai/api/bootstrap/org_123/current_user_access"
            )
        )
    }

    @Test
    fun claudeCollectorPayloadIsIgnoredOnLoginOrGooglePages() {
        assertFalse(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.CLAUDE, "https://claude.ai/login"))
        assertFalse(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.CLAUDE, "https://accounts.google.com/signin/v2/challenge/sms"))
        assertTrue(ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.CLAUDE, "https://claude.ai/new"))
    }

    @Test
    fun collectorScriptsUseSameSessionProviderApis() {
        val claude = ProviderWebCollectorScripts.build(ProviderId.CLAUDE, mapOf("lastActiveOrg" to "org_123"), "")
        val codex = ProviderWebCollectorScripts.build(ProviderId.CODEX, emptyMap(), "")
        val gemini = ProviderWebCollectorScripts.build(ProviderId.GEMINI, emptyMap(), "window.SAGE_USAGE_EXTRACTOR={buildGeminiUsagePayload:function(){return null;}};")
        val copilot = ProviderWebCollectorScripts.build(ProviderId.COPILOT, emptyMap(), "")
        val cursor = ProviderWebCollectorScripts.build(ProviderId.CURSOR, emptyMap(), "")

        assertTrue(claude.contains("/api/organizations/"))
        assertTrue(claude.contains("probeClaudeSession"))
        assertTrue(claude.contains("usageOk"))
        assertFalse(claude.contains("claude_usage_unavailable"))
        assertTrue(codex.contains("/backend-api/wham/usage"))
        assertTrue(codex.contains("/api/auth/session"))
        assertTrue(codex.contains("Authorization"))
        assertTrue(codex.contains("Bearer"))
        assertTrue(codex.contains("observedAccountId"))
        assertTrue(codex.contains("c.observedAccountId"))
        assertTrue(codex.contains("/backend-api/me"))
        assertTrue(codex.contains("/backend-api/accounts/check"))
        assertTrue(codex.contains("pickCodexAccountId"))
        assertTrue(codex.contains("ChatGPT-Account-Id"))
        assertTrue(codex.contains("extractCodexUsageFromRows"))
        assertTrue(codex.contains("scanCodexUsageCandidate"))
        assertTrue(codex.contains("usageRes.ok"))
        assertTrue(codex.contains("sessionOk"))
        assertTrue(codex.contains("codex_usage_unavailable"))
        assertFalse(codex.contains("hasCodexUsagePayload(result.usage)"))
        assertTrue(gemini.contains("buildGeminiUsagePayload"))
        assertTrue(gemini.contains("__AIUsageGeminiNetworkRows"))
        assertTrue(gemini.contains("XMLHttpRequest"))
        assertTrue(gemini.contains("remainingFraction"))
        assertTrue(gemini.contains("modelId"))
        assertTrue(gemini.contains("extractJsonCandidates"))
        assertTrue(gemini.contains("c.pageText"))
        assertTrue(gemini.contains("setTimeout(collectGeminiUsage, 5000)"))
        assertTrue(gemini.contains("remainingValue <= 1 ? remainingValue : remainingValue / 100"))
        assertFalse(gemini.contains("100 - remaining"))
        assertFalse(gemini.contains("Gemini Web Session"))
        assertFalse(gemini.contains("Quota is not exposed by the current Gemini web page."))
        assertFalse(gemini.contains("gemini_usage_unavailable"))
        assertTrue(gemini.contains("건너뛰기"))
        assertTrue(copilot.contains("github-copilot/chat/entitlement"))
        assertTrue(copilot.contains("github-copilot/chat/token"))
        assertTrue(copilot.contains("fetchCopilotJsonWithAuthorization"))
        assertTrue(copilot.contains("GitHub-Bearer"))
        assertTrue(copilot.contains("GitHub-Verified-Fetch"))
        assertTrue(copilot.contains("fetch-nonce"))
        assertTrue(copilot.contains("https://github.com/settings/copilot"))
        assertTrue(copilot.contains("/settings/billing/copilot_usage_card"))
        assertTrue(copilot.contains("limited_user_quotas"))
        assertTrue(copilot.contains("monthly_quotas"))
        assertTrue(copilot.contains("script[data-target='react-app.embeddedData']"))
        assertTrue(copilot.contains("nativeJson(\"https://github.com/settings/billing/premium_requests_usage\")"))
        assertTrue(copilot.contains("premium_billing"))
        assertTrue(copilot.contains("amountLimit !== null && amountLimit > 0"))
        assertFalse(copilot.contains("line.remaining = remaining"))
        assertTrue(cursor.contains("/api/auth/stripe"))
        assertTrue(cursor.contains("/api/usage"))
        assertTrue(cursor.contains("/api/auth/usage"))
        assertTrue(cursor.contains("__AIUsageCursorNetworkRows"))
        assertTrue(cursor.contains("pushCursorNetworkRow"))
        assertTrue(cursor.contains("/api/usage?user="))
        assertTrue(cursor.contains("hasTrustedCursorPayload"))
        assertTrue(cursor.contains("hasTrustedRequestUsage"))
        assertTrue(cursor.contains("fetchCursorJson"))
        assertTrue(cursor.contains("api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage"))
        assertTrue(cursor.contains("api2.cursor.sh/aiserver.v1.DashboardService/GetPlanInfo"))
        assertTrue(cursor.contains("api2.cursor.sh/aiserver.v1.DashboardService/GetCreditGrantsBalance"))
        assertTrue(cursor.contains("/api/dashboard/get-credit-grants-balance"))
        assertTrue(cursor.contains("requestUsage"))
        assertFalse(cursor.contains("cursor_usage_unavailable"))
        listOf(claude, codex, gemini, copilot, cursor).forEach { script ->
            assertTrue(script.contains("__AIUsageStartProviderCollector"))
            assertFalse(script.contains("__AIUsageCollectorRunning"))
            assertFalse(script.contains("__AIUsageProviderCollectorRunning_"))
            assertTrue(script.contains("credentials: \"include\""))
            assertTrue(script.contains("AIUsageCollectorBridge.postUsagePayload"))
        }
    }
}
