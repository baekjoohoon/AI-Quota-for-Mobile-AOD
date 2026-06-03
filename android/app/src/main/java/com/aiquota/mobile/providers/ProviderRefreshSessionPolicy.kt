package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId

object ProviderRefreshSessionPolicy {
    fun shouldClearCredentialsOnRefreshAuthFailure(providerId: ProviderId): Boolean {
        return ProviderDefinitionRegistry.definitionFor(providerId).authStoreKind == ProviderAuthStoreKind.NATIVE_TOKEN
    }
}
