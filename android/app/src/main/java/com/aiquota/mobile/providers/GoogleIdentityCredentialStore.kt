package com.aiquota.mobile.providers

import android.content.Context
import com.aiquota.mobile.local.ProviderId

class GoogleIdentityCredentialStore(context: Context) {
    private val secureStore = SecureStringStore(context.applicationContext, PREFERENCES)

    fun accountName(providerId: ProviderId): String? {
        return secureStore.getString(key(providerId, KEY_ACCOUNT_NAME))?.takeIf { it.isNotBlank() }
    }

    fun email(providerId: ProviderId): String? {
        return secureStore.getString(key(providerId, KEY_EMAIL))?.takeIf { it.isNotBlank() }
    }

    fun hasStoredCredential(providerId: ProviderId): Boolean {
        return accountName(providerId) != null || email(providerId) != null
    }

    fun save(providerId: ProviderId, accountName: String?, email: String?) {
        if (!accountName.isNullOrBlank()) {
            secureStore.putString(key(providerId, KEY_ACCOUNT_NAME), accountName)
        }
        if (!email.isNullOrBlank()) {
            secureStore.putString(key(providerId, KEY_EMAIL), email)
        }
    }

    fun clear(providerId: ProviderId) {
        secureStore.remove(
            key(providerId, KEY_ACCOUNT_NAME),
            key(providerId, KEY_EMAIL)
        )
    }

    private fun key(providerId: ProviderId, suffix: String): String {
        return "${providerId.storageId}_$suffix"
    }

    private companion object {
        const val PREFERENCES = "ai_quota_google_identity"
        const val KEY_ACCOUNT_NAME = "account_name"
        const val KEY_EMAIL = "email"
    }
}
