package com.aiusage.mobile.ui.dashboard

import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesCodec

object ProviderCardOrder {
    fun moveUp(order: List<ProviderId>, providerId: ProviderId): List<ProviderId> {
        return moveToTargetIndex(order, providerId, targetIndex(order, providerId, -1))
    }

    fun moveDown(order: List<ProviderId>, providerId: ProviderId): List<ProviderId> {
        return moveToTargetIndex(order, providerId, targetIndex(order, providerId, 1))
    }

    fun moveToTargetIndex(
        order: List<ProviderId>,
        providerId: ProviderId,
        targetIndex: Int
    ): List<ProviderId> {
        return ProviderPreferencesCodec.moveProvider(order, providerId, targetIndex)
    }

    fun targetIndex(order: List<ProviderId>, providerId: ProviderId, offset: Int): Int {
        val normalized = normalizedOrder(order)
        val currentIndex = normalized.indexOf(providerId).takeIf { it >= 0 } ?: 0
        return (currentIndex + offset).coerceIn(0, normalized.lastIndex)
    }

    fun normalizedOrder(order: List<ProviderId>): List<ProviderId> {
        return ProviderPreferencesCodec.decodeOrder(ProviderPreferencesCodec.encodeOrder(order))
    }
}
