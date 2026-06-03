package com.aiquota.mobile.ui.dashboard

import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderPreferencesCodec

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

    fun moveToVisibleTargetIndex(
        order: List<ProviderId>,
        hidden: Set<ProviderId>,
        providerId: ProviderId,
        targetVisibleIndex: Int
    ): List<ProviderId> {
        val normalized = normalizedOrder(order)
        val visible = ProviderPreferencesCodec.visibleProviders(normalized, hidden)
        if (providerId !in visible || visible.size <= 1) return normalized

        val reorderedVisible = visible.toMutableList()
        reorderedVisible.remove(providerId)
        reorderedVisible.add(targetVisibleIndex.coerceIn(0, reorderedVisible.size), providerId)

        val nextVisible = ArrayDeque(reorderedVisible)
        return normalized.map { candidate ->
            if (candidate in hidden) candidate else nextVisible.removeFirst()
        }
    }

    fun previewVisibleOrder(
        visibleOrder: List<ProviderId>,
        draggedProvider: ProviderId?,
        targetVisibleIndex: Int?
    ): List<ProviderId> {
        val normalizedVisibleOrder = visibleOrder.distinct()
        if (
            draggedProvider == null ||
            targetVisibleIndex == null ||
            draggedProvider !in normalizedVisibleOrder
        ) {
            return normalizedVisibleOrder
        }
        if (normalizedVisibleOrder.size <= 1) return normalizedVisibleOrder

        val preview = normalizedVisibleOrder.toMutableList()
        preview.remove(draggedProvider)
        preview.add(targetVisibleIndex.coerceIn(0, preview.size), draggedProvider)
        return preview
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
