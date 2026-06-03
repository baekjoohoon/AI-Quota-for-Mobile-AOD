package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId

object ProviderHiddenWebViewRetentionPolicy {
    fun shouldRetain(providerId: ProviderId): Boolean {
        return when (providerId) {
            ProviderId.CLAUDE,
            ProviderId.CODEX,
            ProviderId.COPILOT,
            ProviderId.GEMINI,
            ProviderId.CURSOR -> true
            ProviderId.ANTIGRAVITY -> false
        }
    }

    fun shouldRecreateAfterFailure(kind: ProviderRefreshFailureKind): Boolean {
        return kind == ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED
    }
}
