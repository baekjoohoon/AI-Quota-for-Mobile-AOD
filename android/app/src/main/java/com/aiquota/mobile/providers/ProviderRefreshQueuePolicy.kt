package com.aiquota.mobile.providers

object ProviderRefreshQueuePolicy {
    fun merge(
        existing: List<ProviderRefreshJob>,
        incoming: List<ProviderRefreshJob>,
        prioritize: Boolean
    ): List<ProviderRefreshJob> {
        val existingByProvider = existing.associateBy { it.providerId }
        val bestIncoming = incoming
            .withIndex()
            .sortedWith(compareBy<IndexedValue<ProviderRefreshJob>> { it.value.qos }.thenBy { it.index })
            .map { it.value }
            .distinctBy { it.providerId }
        val acceptedIncoming = bestIncoming.filter { job ->
            val existingJob = existingByProvider[job.providerId]
            existingJob == null || prioritize || job.qos < existingJob.qos
        }
        val acceptedProviders = acceptedIncoming.map { it.providerId }.toSet()
        val remainingExisting = existing.filterNot { it.providerId in acceptedProviders }
        val acceptedHasCriticalQos = acceptedIncoming.any { it.qos <= ProviderRefreshPlan.RESET_REFRESH_QOS }
        return if (prioritize || acceptedHasCriticalQos) {
            acceptedIncoming + remainingExisting
        } else {
            remainingExisting + acceptedIncoming
        }
    }
}
