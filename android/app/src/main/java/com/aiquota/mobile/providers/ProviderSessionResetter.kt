package com.aiquota.mobile.providers

import android.content.Context
import com.aiquota.mobile.local.ProviderId

class ProviderSessionResetter(context: Context) {
    private val appContext = context.applicationContext

    fun disconnect(providerId: ProviderId) {
        clearStoredProviderCredentials(providerId)
        ProviderWebSessionCleaner.clearProviderWebSession(appContext, providerId)
        notifyProviderSessionReset(providerId)
    }

    suspend fun disconnectAndWait(providerId: ProviderId) {
        clearStoredProviderCredentials(providerId)
        ProviderWebSessionCleaner.clearProviderWebSessionAndWait(appContext, providerId)
        notifyProviderSessionReset(providerId)
    }

    suspend fun disconnectAllAndWait(providerIds: List<ProviderId>) {
        providerIds.forEach { providerId ->
            disconnectAndWait(providerId)
        }
    }

    private fun notifyProviderSessionReset(providerId: ProviderId) {
        appContext.sendBroadcast(
            ProviderBackgroundRefreshService.createSessionResetIntent(appContext, providerId)
        )
    }

    private fun clearStoredProviderCredentials(providerId: ProviderId) {
        when (providerId) {
            ProviderId.GEMINI -> {
                GoogleAppAuthTokenStore(appContext).clear(providerId)
                GoogleIdentityCredentialStore(appContext).clear(providerId)
                GeminiCliOAuthRepository(appContext).disconnect()
            }
            ProviderId.ANTIGRAVITY -> {
                GoogleAppAuthTokenStore(appContext).clear(providerId)
                GoogleIdentityCredentialStore(appContext).clear(providerId)
                AntigravityOAuthRepository(appContext).disconnect()
            }
            ProviderId.GLM -> {
                GlmUsageRepository(appContext).clear()
            }
            ProviderId.OPENCODE -> {
                ProviderScopedStateRepository(appContext).clearOpenCodeUsageUrl()
            }
            ProviderId.CODEX,
            ProviderId.CLAUDE,
            ProviderId.COPILOT,
            ProviderId.CURSOR -> Unit
        }
    }
}
