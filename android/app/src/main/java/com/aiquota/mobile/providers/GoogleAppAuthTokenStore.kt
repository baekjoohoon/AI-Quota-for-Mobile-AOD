package com.aiquota.mobile.providers

import android.content.Context
import com.aiquota.mobile.local.ProviderId

class GoogleAppAuthTokenStore(context: Context) {
    private val secureStore = SecureStringStore(context.applicationContext, PREFERENCES)

    fun save(
        providerId: ProviderId,
        accessToken: String,
        refreshToken: String,
        expiresAtMillis: Long,
        accountName: String?,
        email: String?
    ) {
        secureStore.putString(key(providerId, KEY_ACCESS_TOKEN), accessToken)
        secureStore.putString(key(providerId, KEY_REFRESH_TOKEN), refreshToken)
        secureStore.putLong(key(providerId, KEY_ACCESS_EXPIRES_AT), expiresAtMillis)
        if (!accountName.isNullOrBlank()) {
            secureStore.putString(key(providerId, KEY_ACCOUNT_NAME), accountName)
        }
        if (!email.isNullOrBlank()) {
            secureStore.putString(key(providerId, KEY_EMAIL), email)
        }
    }

    fun get(providerId: ProviderId): StoredGoogleAppAuthToken? {
        val refreshToken = secureStore.getString(key(providerId, KEY_REFRESH_TOKEN))?.takeIf { it.isNotBlank() }
            ?: return null
        return StoredGoogleAppAuthToken(
            accessToken = secureStore.getString(key(providerId, KEY_ACCESS_TOKEN)).orEmpty(),
            refreshToken = refreshToken,
            expiresAtMillis = secureStore.getLong(key(providerId, KEY_ACCESS_EXPIRES_AT), 0L),
            accountName = secureStore.getString(key(providerId, KEY_ACCOUNT_NAME))?.takeIf { it.isNotBlank() },
            email = secureStore.getString(key(providerId, KEY_EMAIL))?.takeIf { it.isNotBlank() }
        )
    }

    fun hasStoredCredential(providerId: ProviderId): Boolean {
        return get(providerId) != null
    }

    fun clear(providerId: ProviderId) {
        secureStore.remove(
            key(providerId, KEY_ACCESS_TOKEN),
            key(providerId, KEY_REFRESH_TOKEN),
            key(providerId, KEY_ACCESS_EXPIRES_AT),
            key(providerId, KEY_ACCOUNT_NAME),
            key(providerId, KEY_EMAIL)
        )
    }

    private fun key(providerId: ProviderId, suffix: String): String {
        return "${providerId.storageId}_$suffix"
    }

    private companion object {
        const val PREFERENCES = "ai_quota_google_appauth"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        const val KEY_ACCOUNT_NAME = "account_name"
        const val KEY_EMAIL = "email"
    }
}

data class StoredGoogleAppAuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val accountName: String?,
    val email: String?
)
