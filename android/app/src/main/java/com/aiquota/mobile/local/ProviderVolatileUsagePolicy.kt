package com.aiquota.mobile.local

object ProviderVolatileUsagePolicy {
    fun removeExpiredLines(snapshot: ProviderUsageSnapshot, now: java.time.Instant): ProviderUsageSnapshot = snapshot
}

internal fun ProviderUsageSnapshot.withExpiredProviderSpecificLinesRemoved(now: java.time.Instant): ProviderUsageSnapshot {
    return ProviderVolatileUsagePolicy.removeExpiredLines(this, now)
}
