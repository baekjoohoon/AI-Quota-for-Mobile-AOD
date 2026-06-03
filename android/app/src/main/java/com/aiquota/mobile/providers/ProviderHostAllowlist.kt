package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId

object ProviderHostAllowlist {
    fun isAllowed(providerId: ProviderId, url: String): Boolean {
        return ProviderDefinitionRegistry.isAllowed(providerId, url)
    }
}
