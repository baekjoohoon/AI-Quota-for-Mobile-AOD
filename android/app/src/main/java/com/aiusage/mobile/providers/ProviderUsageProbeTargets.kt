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
                "https://gemini.google.com/app/usage",
                "https://gemini.google.com/app/settings",
                "https://gemini.google.com/app/u/0",
                "https://gemini.google/subscriptions?hl=ko",
                "https://one.google.com/settings?hl=ko",
                "https://one.google.com/explore-plan/gemini-advanced?hl=ko"
            )
            ProviderId.COPILOT -> listOf(
                "https://github.com/github-copilot/chat/entitlement",
                "https://github.com/github-copilot/chat",
                "https://github.com/settings/copilot",
                "https://github.com/settings/copilot/features",
                "https://github.com/settings/copilot/billing",
                "https://github.com/settings/copilot/usage",
                "https://github.com/settings/copilot/plans",
                "https://github.com/settings/billing",
                "https://github.com/settings/billing/usage"
            )
            ProviderId.CURSOR -> listOf(
                "https://cursor.com/dashboard",
                "https://cursor.com/dashboard/usage",
                "https://cursor.com/settings"
            )
        }.filter { url -> ProviderHostAllowlist.isAllowed(providerId, url) }
    }
}
