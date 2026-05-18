package com.aiusage.mobile.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLocalUsageCollectorTest {
    @Test
    fun providerExtractorsReadLocalSessionSignalsWithoutCredentialKeys() {
        com.aiusage.mobile.local.ProviderId.defaultOrder().forEach { providerId ->
            val script = ProviderLocalUsageCollector.scriptFor(providerId)

            assertTrue(script.contains("SAGE_USAGE_EXTRACTOR"))
            assertTrue(script.contains(providerId.storageId))
        assertTrue(script.contains("localStorage"))
        assertTrue(script.contains("sessionStorage"))
        assertTrue(script.contains("indexedDB"))
        assertTrue(script.contains("document.scripts"))
        assertTrue(script.contains("usage|limit|quota|plan|subscription"))
        assertTrue(script.contains("token|secret|session|cookie|auth|jwt|credential|password"))
        }
    }

    @Test
    fun providerExtractorsHookInPageNetworkWithoutExportingData() {
        com.aiusage.mobile.local.ProviderId.defaultOrder().forEach { providerId ->
            val script = ProviderLocalUsageCollector.scriptFor(providerId).lowercase()

            assertTrue(script.contains("fetch"))
            assertTrue(script.contains("xmlhttprequest"))
            assertTrue(script.contains("aiusagelocalcollector"))
            assertTrue(script.contains("fetchproviderendpoints"))
            assertTrue(script.contains("rememberendpointsummary"))
            assertTrue(script.contains("rememberendpointerror"))
            assertTrue(script.contains("derivedplans"))
            assertTrue(script.contains("derivedlimits"))
            assertTrue(script.contains("endpointsummaries().slice(-8)"))
            assertTrue(script.contains("credentials: 'include'"))
            assertFalse(script.contains("navigator.sendbeacon"))
        }
    }

    @Test
    fun providerExtractorsExposeHookOnlyScriptForEarlyNetworkCapture() {
        com.aiusage.mobile.local.ProviderId.defaultOrder().forEach { providerId ->
            val hookScript = ProviderLocalUsageCollector.hookScriptFor(providerId)
            val fullScript = ProviderLocalUsageCollector.scriptFor(providerId)

            assertTrue(hookScript.contains("var HOOK_ONLY = true;"))
            assertTrue(fullScript.contains("var HOOK_ONLY = false;"))
            assertTrue(hookScript.contains("installNetworkHooks();"))
            assertTrue(hookScript.contains("if (HOOK_ONLY) return"))
        }
    }

    @Test
    fun providerExtractorsProbeEndpointsOnlyOnProviderOrigins() {
        com.aiusage.mobile.local.ProviderId.defaultOrder().forEach { providerId ->
            val script = ProviderLocalUsageCollector.scriptFor(providerId)

            assertTrue(script.contains("function isProviderOrigin()"))
            assertTrue(script.contains("if (!isProviderOrigin()) return;"))
        }
    }

    @Test
    fun providerExtractorsProbeKnownSameOriginSubscriptionEndpoints() {
        val claudeScript = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CLAUDE)
        val codexScript = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CODEX)

        assertTrue(claudeScript.contains("/api/bootstrap"))
        assertTrue(claudeScript.contains("/api/organizations"))
        assertTrue(claudeScript.contains("/api/account_profile"))
        assertTrue(claudeScript.contains("fetchClaudeScopedEndpoints"))
        assertTrue(claudeScript.contains("/api/bootstrap/\" + organizationId + \"/current_user_access"))
        assertTrue(claudeScript.contains("/api/organizations/\" + organizationId + \"/usage_limits"))
        assertTrue(claudeScript.contains("/api/organizations/\" + organizationId + \"/subscription_details"))
        assertTrue(codexScript.contains("/backend-api/accounts/check/v4-2023-04-27"))
        assertTrue(codexScript.contains("/backend-api/accounts/default/usage_limits"))
        assertTrue(codexScript.contains("/backend-api/subscriptions/active"))
        assertTrue(codexScript.contains("fetchAccountScopedEndpoints"))
        assertTrue(codexScript.contains("/backend-api/accounts/\" + accountId + \"/usage"))
        assertTrue(codexScript.contains("/backend-api/accounts/\" + accountId + \"/message_limits"))
        assertFalse(codexScript.contains("platform.openai.com"))
    }

    @Test
    fun providerExtractorsReadBrowserStorageThroughSafeAccessors() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CLAUDE)

        assertTrue(script.contains("function safeStorage"))
        assertTrue(script.contains("safeStorage('localStorage')"))
        assertTrue(script.contains("safeStorage('sessionStorage')"))
        assertTrue(script.contains("function shouldIncludeStorageValue"))
        assertTrue(script.contains("function decodeJsonString"))
        assertTrue(script.contains("value.slice(0, 20000)"))
        assertFalse(script.contains("collectStorage(window.localStorage"))
        assertFalse(script.contains("collectStorage(window.sessionStorage"))
    }

    @Test
    fun providerExtractorsCollectIndexedDbSignalsInsideProviderOrigin() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CLAUDE)

        assertTrue(script.contains("function collectIndexedDbSignals"))
        assertTrue(script.contains("window.indexedDB.databases"))
        assertTrue(script.contains("rememberIndexedDbValue"))
        assertTrue(script.contains("indexedDB:"))
        assertTrue(script.contains("collectIndexedDbSignals();"))
    }

    @Test
    fun providerExtractorsDetectKoreanLoginPrompts() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CODEX)

        assertTrue(script.contains("로그인") || script.contains("\\ub85c\\uadf8\\uc778"))
        assertTrue(script.contains("회원가입") || script.contains("\\ud68c\\uc6d0\\uac00\\uc785"))
        assertTrue(script.contains("이메일 주소") || script.contains("\\uc774\\uba54\\uc77c \\uc8fc\\uc18c"))
        assertTrue(script.contains("safeCredentialFormPattern"))
        assertTrue(script.contains("safeExplicitLoginPattern"))
        assertTrue(script.contains("safeKoreanRemaining"))
    }

    @Test
    fun providerExtractorsAvoidUntrustedFreePlanFallbacks() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CODEX)

        assertTrue(script.contains("findTrustedPlan"))
        assertTrue(script.contains("findStructuredPlan"))
        assertTrue(script.contains("scanStructuredPlan"))
        assertTrue(script.contains("rememberDerivedPlan(scanStructuredPlan"))
        assertTrue(script.contains("var plan = derivedPlans()[0]"))
        assertTrue(script.contains("trustedPlanSourcePattern"))
        assertTrue(script.contains("booleanPlanValue"))
        assertTrue(script.contains("is_free_plan"))
        assertTrue(script.contains("findVisiblePaidPlan"))
        assertFalse(script.contains("var direct = explicit || PLAN_PATTERN"))
        assertFalse(script.contains("defaultPaidPlanValue"))
    }

    @Test
    fun providerExtractorsExposeExplicitPaidPlanHints() {
        val claudeScript = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CLAUDE)
        val geminiScript = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.GEMINI)

        assertTrue(claudeScript.contains("Claude Pro"))
        assertTrue(claudeScript.contains("Claude Max"))
        assertTrue(geminiScript.contains("Google AI Pro"))
        assertTrue(geminiScript.contains("Google AI Ultra"))
        assertTrue(geminiScript.contains("Google One AI Premium"))
    }

    @Test
    fun providerExtractorsUseVisibleTextOnlyForLoginPromptState() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CODEX)

        assertTrue(script.contains("var visibleText = safeText(document.body && document.body.innerText);"))
        assertTrue(script.contains("login: hasLoginPrompt(visibleText)"))
        assertTrue(script.contains("!hasLoginPrompt(visibleText)"))
        assertFalse(script.contains("login: hasLoginPrompt(combinedText)"))
    }

    @Test
    fun providerExtractorsUseCredentialFormSignalsForLoginPromptState() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.COPILOT)

        assertTrue(script.contains("function hasLoginPrompt(text)"))
        assertTrue(script.contains("credentialFormPattern"))
        assertTrue(script.contains("explicitLoginPattern"))
        assertFalse(script.contains("function hasLoginPrompt(text)\n            return"))
    }

    @Test
    fun providerExtractorsExposeAuthenticatedAppShellSignals() {
        com.aiusage.mobile.local.ProviderId.defaultOrder().forEach { providerId ->
            val script = ProviderLocalUsageCollector.scriptFor(providerId)

            assertTrue(script.contains("function authenticatedAppShellMarker"))
            assertTrue(script.contains("var authenticatedApp = authenticatedAppShellMarker(visibleText) || authenticatedEndpointMarker();"))
            assertTrue(script.contains("authenticatedApp: authenticatedApp"))
            assertTrue(script.contains("limits.length > 0 || plan || (authenticatedApp && !hasLoginPrompt(visibleText))"))
        }
    }

    @Test
    fun claudeExtractorRecognizesKoreanAuthenticatedAppShell() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CLAUDE)

        assertTrue(
            script.contains("\u0043laude\uc640 \ud568\uaed8 \ucee4\ud53c \ud55c\uc794", ignoreCase = true) ||
                script.contains("claude\\uc640 \\ud568\\uaed8 \\ucee4\\ud53c \\ud55c\\uc794", ignoreCase = true)
        )
        assertTrue(
            script.contains("\\ub2ec\\ube5b \\uc544\\ub798 \\ub300\\ud654\\ud560\\uae4c\\uc694") ||
                script.contains("\ub2ec\ube5b \uc544\ub798 \ub300\ud654\ud560\uae4c\uc694")
        )
    }

    @Test
    fun geminiExtractorRecognizesKoreanAuthenticatedAppShell() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.GEMINI)

        assertTrue(
            script.contains("\u0047emini\uc640\uc758 \ub300\ud654", ignoreCase = true) ||
                script.contains("gemini\\uc640\\uc758 \\ub300\\ud654", ignoreCase = true)
        )
        assertTrue(
            script.contains("\\ub2d8, \\uc548\\ub155\\ud558\\uc138\\uc694") ||
                script.contains("\ub2d8, \uc548\ub155\ud558\uc138\uc694")
        )
    }

    @Test
    fun providerExtractorsAvoidGenericNavigationAsAuthenticatedShellSignal() {
        com.aiusage.mobile.local.ProviderId.defaultOrder().forEach { providerId ->
            val script = ProviderLocalUsageCollector.scriptFor(providerId)

            assertFalse(script.contains("textarea,[contenteditable=\"true\"],nav"))
            assertFalse(script.contains("domMatches('nav"))
            assertFalse(script.contains(",nav,"))
        }
    }

    @Test
    fun providerExtractorsParseEndpointPrefixedJsonRows() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CODEX)

        assertTrue(script.contains("function extractJsonCandidate"))
        assertTrue(script.contains("candidate.indexOf(\"{\")"))
        assertTrue(script.contains("candidate.indexOf(\"[\")"))
        assertTrue(script.contains("return candidate.slice(0, index + 1).trim()"))
        assertTrue(script.contains("window.__AI_USAGE_PROVIDER_EVENTS__.length > 32"))
    }

    @Test
    fun providerExtractorsPreserveWindowTimesFromStructuredProviderRows() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CLAUDE)

        assertTrue(script.contains("function attachWindowTimes"))
        assertTrue(script.contains("\"resets_at\""))
        assertTrue(script.contains("\"period_end\""))
        assertTrue(script.contains("limit.s = start"))
        assertTrue(script.contains("limit.r = reset"))
    }

    @Test
    fun providerExtractorsUnderstandGitHubBillingUsageKeys() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.COPILOT)

        assertTrue(script.contains("grossQuantity"))
        assertTrue(script.contains("netQuantity"))
        assertTrue(script.contains("unitType"))
        assertTrue(script.contains("/settings/billing/usage"))
        assertTrue(script.contains("/settings/copilot/features"))
    }

    @Test
    fun providerExtractorsCollectRemainingOnlyLimits() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CODEX)

        assertTrue(script.contains("pushRemainingOnlyLimit"))
        assertTrue(script.contains("remaining_messages"))
        assertTrue(script.contains("messages_remaining"))
        assertTrue(script.contains("remaining_credits"))
        assertTrue(script.contains("available_credits"))
        assertTrue(script.contains("max_messages"))
        assertTrue(script.contains("usage_limit"))
    }

    @Test
    fun providerExtractorsProbeProviderSpecificUsageSurfaces() {
        val claudeScript = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CLAUDE)
        val codexScript = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CODEX)
        val copilotScript = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.COPILOT)
        val cursorScript = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CURSOR)

        assertTrue(claudeScript.contains("/api/usage_limits"))
        assertTrue(claudeScript.contains("/api/billing"))
        assertTrue(claudeScript.contains("lastActiveOrg"))
        assertTrue(claudeScript.contains("fetchClaudeScopedEndpoints(document.cookie"))
        assertTrue(claudeScript.contains("scanClaudeOrganizationUsage"))
        assertTrue(claudeScript.contains("/api\\/organizations\\/:id\\/usage"))
        assertTrue(claudeScript.contains("/subscription_details"))
        assertTrue(claudeScript.contains("/trial_status"))
        assertTrue(codexScript.contains("/backend-anon/accounts/check/v4-2023-04-27"))
        assertTrue(codexScript.contains("/backend-api/codex/usage"))
        assertTrue(codexScript.contains("/backend-api/wham/usage"))
        assertTrue(codexScript.contains("/api/auth/session"))
        assertTrue(codexScript.contains("fetchCodexAuthenticatedUsage"))
        assertTrue(codexScript.contains("ChatGPT-Account-Id"))
        assertTrue(copilotScript.contains("/github-copilot/chat/entitlement"))
        assertTrue(copilotScript.contains("scanCopilotEntitlement"))
        assertTrue(copilotScript.contains("chatPercentage"))
        assertTrue(copilotScript.contains("quotaLimits.completions"))
        assertTrue(copilotScript.contains("completionsRemaining <= 4000"))
        assertTrue(copilotScript.contains("premiumInteractionsPercentage"))
        assertTrue(copilotScript.contains("quotas.resetDate"))
        assertTrue(copilotScript.contains("/settings/copilot/usage"))
        assertTrue(copilotScript.contains("/settings/copilot/plans"))
        assertFalse(copilotScript.contains("/features/copilot/plans"))
        assertTrue(cursorScript.contains("/api/usage"))
        assertTrue(cursorScript.contains("/api/usage-summary"))
        assertTrue(cursorScript.contains("/api/subscription"))
        assertTrue(cursorScript.contains("/api/auth/session"))
        assertTrue(cursorScript.contains("/auth/usage"))
        assertTrue(cursorScript.contains("/auth/me"))
    }

    @Test
    fun cursorExtractorUnderstandsRequestUsageApiShape() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.CURSOR)

        assertTrue(script.contains("maxRequestUsage"))
        assertTrue(script.contains("numRequests"))
        assertTrue(script.contains("requestUsage"))
        assertTrue(script.contains("remainingRequestUsage"))
        assertTrue(script.contains("remainingCap"))
        assertTrue(script.contains("authenticatedEndpointMarker"))
        assertTrue(script.contains("/^\\/(?:api\\/(?:usage|auth\\/me)|auth\\/(?:usage|me))$/"))
        assertTrue(script.contains("scanCursorUsageSummary"))
        assertTrue(script.contains("rememberCursorPlan"))
        assertTrue(script.contains("scanCursorPlanUsage"))
        assertTrue(script.contains("planUsage"))
        assertTrue(script.contains("totalPercentUsed"))
        assertTrue(script.contains("scanCursorRequestUsage"))
        assertTrue(script.contains("Total usage"))
        assertTrue(script.contains("individualUsage"))
        assertTrue(script.contains("membershipType"))
        assertTrue(script.contains("planInfo"))
        assertTrue(script.contains("billingCycleStart"))
    }

    @Test
    fun geminiExtractorFetchesQuotaRpcEndpoints() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.GEMINI)

        assertTrue(script.contains("fetchGeminiQuotaEndpoints"))
        assertTrue(script.contains("VxUbXb"))
        assertTrue(script.contains("qpEbW"))
        assertTrue(script.contains("aPya6c"))
        assertTrue(script.contains("scanGeminiQuotaResponse"))
        assertTrue(script.contains("CheckGeminiQuota action"))
        assertTrue(script.contains("hostEndsWith(\"gemini.google.com\")"))
        assertTrue(script.contains("hostEndsWith(\"one.google.com\")"))
        assertTrue(script.contains("/subscriptions?hl=ko"))
        assertTrue(script.contains("case 3:"))
        assertTrue(script.contains("case 4:"))
    }

    @Test
    fun providerExtractorCombinesSiblingQuotaMapsAndSkipsNavigationCounts() {
        val script = ProviderLocalUsageCollector.scriptFor(com.aiusage.mobile.local.ProviderId.COPILOT)

        assertTrue(script.contains("scanSiblingQuotaMaps"))
        assertTrue(script.contains("value.limits"))
        assertTrue(script.contains("value.remaining"))
        assertTrue(script.contains("sitemap|completed"))
    }

    @Test
    fun providerExtractorsReturnRemainingLimitMetadataWhenAvailable() {
        com.aiusage.mobile.local.ProviderId.defaultOrder().forEach { providerId ->
            val script = ProviderLocalUsageCollector.scriptFor(providerId)

            assertTrue(script.contains("remaining"))
            assertTrue(script.contains("used"))
            assertTrue(script.contains("limit"))
            assertTrue(script.contains("unit"))
            assertTrue(script.contains("category"))
            assertTrue(script.contains("window"))
            assertTrue(script.contains("source"))
            assertTrue(script.contains("confidence"))
        }
    }
}
