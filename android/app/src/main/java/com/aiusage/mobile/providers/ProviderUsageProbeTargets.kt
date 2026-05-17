package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId

object ProviderUsageProbeTargets {
    fun urls(providerId: ProviderId): List<String> {
        return when (providerId) {
            ProviderId.CLAUDE -> listOf(
                "https://claude.ai/",
                "https://claude.ai/settings/billing",
                "https://claude.ai/settings/usage",
                "https://claude.ai/settings/profile"
            )
            ProviderId.CODEX -> listOf(
                "https://chatgpt.com/",
                "https://chatgpt.com/gpts",
                "https://chatgpt.com/auth/login"
            )
            ProviderId.GEMINI -> listOf(
                "https://gemini.google.com/app",
                "https://gemini.google.com/app/settings",
                "https://gemini.google.com/app/u/0"
            )
            ProviderId.COPILOT -> listOf(
                "https://github.com/github-copilot/chat",
                "https://github.com/settings/copilot",
                "https://github.com/settings/copilot/features",
                "https://github.com/settings/copilot/billing",
                "https://github.com/settings/copilot/usage",
                "https://github.com/settings/copilot/plans",
                "https://github.com/settings/billing",
                "https://github.com/settings/billing/usage",
                "https://github.com/features/copilot/plans"
            )
            ProviderId.CURSOR -> listOf(
                "https://cursor.com/dashboard",
                "https://cursor.com/dashboard/usage",
                "https://cursor.com/settings",
                "https://cursor.com/api/usage",
                "https://cursor.com/api/usage-summary",
                "https://cursor.com/api/subscription",
                "https://cursor.com/api/billing",
                "https://cursor.com/api/me",
                "https://cursor.com/api/dashboard/get-plan-info",
                "https://cursor.com/api/dashboard/get-current-billing-cycle",
                "https://cursor.com/api/dashboard/get-credit-grants-balance",
                "https://api2.cursor.sh/auth/usage",
                "https://api2.cursor.sh/auth/me"
            )
        }.filter { url -> ProviderHostAllowlist.isAllowed(providerId, url) }
    }
}
