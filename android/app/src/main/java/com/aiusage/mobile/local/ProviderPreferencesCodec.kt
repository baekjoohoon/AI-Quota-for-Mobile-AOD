package com.aiusage.mobile.local

object ProviderPreferencesCodec {
    fun decodeOrder(value: String): List<ProviderId> {
        val decoded = value
            .split(STORAGE_SEPARATOR)
            .mapNotNull { ProviderId.fromStorageId(it) }
            .distinct()
        return normalizeOrder(decoded)
    }

    fun encodeOrder(order: List<ProviderId>): String {
        return order.distinct().joinToString(STORAGE_SEPARATOR) { it.storageId }
    }

    fun decodeHidden(value: String): Set<ProviderId> {
        return value
            .split(STORAGE_SEPARATOR)
            .mapNotNull { ProviderId.fromStorageId(it) }
            .toSet()
    }

    fun encodeHidden(hidden: Set<ProviderId>): String {
        return ProviderId.defaultOrder()
            .filter { it in hidden }
            .joinToString(STORAGE_SEPARATOR) { it.storageId }
    }

    fun moveProvider(order: List<ProviderId>, providerId: ProviderId, targetIndex: Int): List<ProviderId> {
        val normalized = normalizeOrder(order).toMutableList()
        normalized.remove(providerId)
        normalized.add(targetIndex.coerceIn(0, normalized.size), providerId)
        return normalized
    }

    fun setHidden(hidden: Set<ProviderId>, providerId: ProviderId, hiddenState: Boolean): Set<ProviderId> {
        return if (hiddenState) {
            hidden + providerId
        } else {
            hidden - providerId
        }
    }

    fun visibleProviders(order: List<ProviderId>, hidden: Set<ProviderId>): List<ProviderId> {
        return normalizeOrder(order).filterNot { it in hidden }
    }

    private fun normalizeOrder(order: List<ProviderId>): List<ProviderId> {
        return (order + ProviderId.defaultOrder()).distinct()
    }

    private const val STORAGE_SEPARATOR = ","
}
