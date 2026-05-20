package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId

object ProviderHostAllowlist {
    fun isAllowed(providerId: ProviderId, url: String): Boolean {
        return ProviderDefinitionRegistry.isAllowed(providerId, url)
    }
}
