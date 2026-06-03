package com.aiquota.mobile.providers

import android.content.Context
import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageSnapshot

class AntigravityFirebaseConnector(
    context: Context,
    private val gateway: AntigravityFirebaseGateway = AntigravityFirebaseGateway(context)
) : ProviderConnector {
    override val providerId: ProviderId = ProviderId.ANTIGRAVITY
    override val startUrl: String = ""

    suspend fun beginAuthorizationUrl(): String {
        return gateway.startOAuth()
    }

    override suspend fun refreshUsage(rawPayload: String): ProviderUsageSnapshot {
        val result = gateway.collectUsage()
        if (result.ok && !result.payload.isNullOrBlank()) {
            return ProviderUsageNormalizer.normalize(
                ProviderId.ANTIGRAVITY,
                result.payload,
                ProviderPayloadSource.PROVIDER_API
            ) ?: ProviderUsageSnapshot.connectedWithoutUsage(
                ProviderId.ANTIGRAVITY,
                "Antigravity gateway returned no trusted usage payload."
            )
        }
        return when {
            result.requiresAuth -> ProviderUsageSnapshot.disconnected(ProviderId.ANTIGRAVITY).copy(
                message = result.errorKind
            )
            result.errorKind == "BACKEND_API_FORBIDDEN" -> ProviderUsageSnapshot.unavailable(
                ProviderId.ANTIGRAVITY,
                "Antigravity private API is not available for this backend project."
            )
            result.retryable -> ProviderUsageSnapshot.failedKeepingPrevious(
                ProviderId.ANTIGRAVITY,
                previous = null,
                message = result.errorKind ?: "Antigravity gateway collection failed."
            )
            else -> ProviderUsageSnapshot(
                providerId = ProviderId.ANTIGRAVITY,
                connectionState = ProviderConnectionState.UNAVAILABLE,
                refreshState = ProviderRefreshState.IDLE,
                message = result.errorKind ?: "Antigravity gateway is unavailable."
            )
        }
    }

    override suspend fun disconnect() {
        gateway.disconnect()
    }
}
