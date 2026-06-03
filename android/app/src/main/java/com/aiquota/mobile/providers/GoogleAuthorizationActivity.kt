package com.aiquota.mobile.providers

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.local.ProviderId
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.common.AccountPicker
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService

class GoogleAuthorizationActivity : Activity() {
    private lateinit var providerId: ProviderId
    private var authorizationStarted = false
    private var pendingTokenBrokerAccountName: String? = null
    private var tokenBrokerRecoveryStarted = false
    private var authService: AuthorizationService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authorizationStarted = savedInstanceState?.getBoolean(KEY_AUTHORIZATION_STARTED) ?: false
        pendingTokenBrokerAccountName = savedInstanceState?.getString(KEY_TOKEN_BROKER_ACCOUNT_NAME)
        tokenBrokerRecoveryStarted = savedInstanceState?.getBoolean(KEY_TOKEN_BROKER_RECOVERY_STARTED) ?: false
        providerId = ProviderId.fromStorageId(intent.getStringExtra(EXTRA_PROVIDER_ID)) ?: run {
            finish()
            return
        }
        if (providerId != ProviderId.GEMINI && providerId != ProviderId.ANTIGRAVITY) {
            finish()
            return
        }
        setContentView(FrameLayout(this))
        LocalUsageRepository(applicationContext).markConnecting(providerId)
        if (!authorizationStarted) {
            authorizationStarted = true
            startAuthorization()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_AUTHORIZATION_STARTED, authorizationStarted)
        outState.putString(KEY_TOKEN_BROKER_ACCOUNT_NAME, pendingTokenBrokerAccountName)
        outState.putBoolean(KEY_TOKEN_BROKER_RECOVERY_STARTED, tokenBrokerRecoveryStarted)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        authService?.dispose()
        authService = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_AUTHORIZE -> handleAuthorizationActivityResult(resultCode, data)
            REQUEST_ACCOUNT_PICKER -> handleAccountPickerResult(resultCode, data)
            REQUEST_TOKEN_BROKER_RECOVERY -> handleTokenBrokerRecoveryResult(resultCode)
        }
    }

    private fun handleAuthorizationActivityResult(resultCode: Int, data: Intent?) {
        Log.i(
            "GoogleOAuth",
            "provider=${providerId.name} authorization_activity_result resultCode=$resultCode data=${data != null}"
        )
        if (data == null) {
            LocalUsageRepository(applicationContext).markLoginCancelled(
                providerId,
                "Google authorization was cancelled."
            )
            finish()
            return
        }
        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)
        Log.i("GoogleOAuth", "provider=${providerId.name} auth_response_received=${response != null}")
        if (response == null) {
            finishAuthorizationFailure(
                "Google authorization failed.",
                GoogleAppAuthUsageAuthorizer.authorizationDiagnostic(exception)
            )
            return
        }
        exchangeAppAuthCodeAndStore(response)
    }

    private fun handleAccountPickerResult(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null) {
            LocalUsageRepository(applicationContext).markLoginCancelled(
                providerId,
                "Google account selection was cancelled."
            )
            UsageSurfaceRefresher.refresh(applicationContext, LocalUsageRepository(applicationContext))
            finish()
            return
        }
        val accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.takeIf { it.isNotBlank() }
        if (accountName == null) {
            finishAuthorizationFailure("Google account selection failed.", "token_broker_account_missing")
            return
        }
        GoogleIdentityCredentialStore(applicationContext).save(providerId, accountName, accountName)
        requestTokenBrokerAccess(accountName)
    }

    private fun handleTokenBrokerRecoveryResult(resultCode: Int) {
        val accountName = pendingTokenBrokerAccountName
        if (resultCode != RESULT_OK || accountName.isNullOrBlank()) {
            finishAuthorizationFailure("Google authorization requires sign-in.", "token_broker_recoverable_auth")
            return
        }
        requestTokenBrokerAccess(accountName)
    }

    private fun startAuthorization() {
        Log.i("GoogleOAuth", "provider=${providerId.name} start_authorization")
        if (!GoogleAppAuthUsageAuthorizer.isConfigured()) {
            finishAuthorizationFailure(
                "Google Android OAuth client id is not configured.",
                "appauth_client_id_missing"
            )
            return
        }
        runCatching {
            val service = AuthorizationService(this).also { authService = it }
            val authIntent = service.getAuthorizationRequestIntent(
                GoogleAppAuthUsageAuthorizer.authorizationRequest()
            )
            startActivityForResult(authIntent, REQUEST_AUTHORIZE)
        }.onFailure { error ->
            finishAuthorizationFailure(
                "Google authorization failed.",
                "appauth_start_${error.javaClass.simpleName}"
            )
        }
    }

    private fun exchangeAppAuthCodeAndStore(response: AuthorizationResponse) {
        Log.i("GoogleOAuth", "provider=${providerId.name} token_exchange_start")
        val service = authService ?: AuthorizationService(this).also { authService = it }
        service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
            runOnUiThread {
                if (tokenResponse == null) {
                    finishAuthorizationFailure(
                        "Google authorization failed.",
                        GoogleAppAuthUsageAuthorizer.tokenDiagnostic(exception)
                    )
                    return@runOnUiThread
                }
                val storeResult = GoogleAppAuthUsageAuthorizer.storeTokenResponse(
                    applicationContext,
                    providerId,
                    tokenResponse
                )
                if (!storeResult.stored) {
                    finishAuthorizationFailure(
                        "Google authorization failed.",
                        storeResult.diagnostic ?: "appauth_token_store_failed"
                    )
                    return@runOnUiThread
                }
                val repository = LocalUsageRepository(applicationContext)
                repository.markGoogleUsagePending(
                    providerId,
                    GoogleUsagePendingRetryPolicy.PENDING_MESSAGE
                )
                UsageSurfaceRefresher.refresh(applicationContext, repository)
                startGoogleUsageCollection(tokenResponse.accessToken.orEmpty())
                finish()
            }
        }
    }

    private fun startGoogleUsageCollection(accessToken: String) {
        val appContext = applicationContext
        val currentProviderId = providerId
        Thread({
            val usageResult = GoogleIdentityUsageAuthorizer.usagePayloadFromAccessToken(
                appContext,
                currentProviderId,
                accessToken,
                accountName = null,
                email = null
            )
            if (!usageResult.payload.isNullOrBlank()) {
                ProviderUsageCollectionService.start(
                    context = appContext,
                    providerId = currentProviderId,
                    source = ProviderUsageCollectionService.SOURCE_PAYLOAD,
                    rawPayload = usageResult.payload
                )
            } else {
                ProviderUsageCollectionService.start(
                    context = appContext,
                    providerId = currentProviderId,
                    source = ProviderUsageCollectionService.SOURCE_REFRESH
                )
                Log.w(
                    "AIQuotaGoogleIdentity",
                    "provider=${currentProviderId.storageId} appAuthUsagePayload=false diagnostic=${safeDiagnostic(usageResult.diagnostic)}"
                )
            }
        }, "AIQuotaGoogleIdentityUsage").start()
    }

    private fun finishAuthorizationFailure(message: String, diagnostic: String) {
        Log.w(
            "AIQuotaGoogleIdentity",
            "provider=${providerId.storageId} authorize=false diagnostic=${safeDiagnostic(diagnostic)}"
        )
        if (diagnostic.canUseTokenBrokerFallback() && startTokenBrokerFallback(diagnostic)) {
            return
        }
        val repository = LocalUsageRepository(applicationContext)
        if (diagnostic.requiresInteractiveAuth()) {
            ProviderSessionResetter(applicationContext).disconnect(providerId)
            repository.markSessionExpired(
                providerId,
                "Google authorization requires sign-in."
            )
        } else {
            repository.failKeepingPrevious(providerId, message)
        }
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        finish()
    }

    private fun startTokenBrokerFallback(diagnostic: String): Boolean {
        Log.i(
            "GoogleOAuth",
            "provider=${providerId.name} token_broker_fallback_start diagnostic=${safeDiagnostic(diagnostic)}"
        )
        val store = GoogleIdentityCredentialStore(applicationContext)
        val accountName = store.accountName(providerId) ?: store.email(providerId)
        if (!accountName.isNullOrBlank()) {
            requestTokenBrokerAccess(accountName)
            return true
        }
        return launchAccountPicker()
    }

    private fun launchAccountPicker(): Boolean {
        return runCatching {
            val intent = AccountPicker.newChooseAccountIntent(
                AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE))
                    .setAlwaysShowAccountPicker(true)
                    .build()
            )
            startActivityForResult(intent, REQUEST_ACCOUNT_PICKER)
        }.onFailure { error ->
            Log.w(
                "AIQuotaGoogleIdentity",
                "provider=${providerId.storageId} accountPicker=false diagnostic=${error.javaClass.simpleName}"
            )
        }.isSuccess
    }

    private fun requestTokenBrokerAccess(accountName: String) {
        pendingTokenBrokerAccountName = accountName
        Log.i("GoogleOAuth", "provider=${providerId.name} token_broker_get_token_start")
        val appContext = applicationContext
        val currentProviderId = providerId
        Thread({
            val tokenResult = GoogleIdentityUsageAuthorizer.requestGoogleAuthToken(appContext, accountName)
            val accessToken = tokenResult.accessToken
            when {
                !accessToken.isNullOrBlank() -> {
                    val usageResult = GoogleIdentityUsageAuthorizer.usagePayloadFromAccessToken(
                        appContext,
                        currentProviderId,
                        accessToken,
                        accountName,
                        accountName
                    )
                    if (!usageResult.payload.isNullOrBlank()) {
                        ProviderUsageCollectionService.start(
                            context = appContext,
                            providerId = currentProviderId,
                            source = ProviderUsageCollectionService.SOURCE_PAYLOAD,
                            rawPayload = usageResult.payload
                        )
                    } else {
                        val repository = LocalUsageRepository(appContext)
                        repository.markGoogleUsagePending(
                            currentProviderId,
                            GoogleUsagePendingRetryPolicy.PENDING_MESSAGE
                        )
                        UsageSurfaceRefresher.refresh(appContext, repository)
                    }
                    runOnUiThread { finish() }
                }
                tokenResult.recoveryIntent != null && !tokenBrokerRecoveryStarted -> {
                    tokenBrokerRecoveryStarted = true
                    runOnUiThread {
                        runCatching {
                            startActivityForResult(
                                tokenResult.recoveryIntent,
                                REQUEST_TOKEN_BROKER_RECOVERY
                            )
                        }.onFailure { error ->
                            finishAuthorizationFailure(
                                "Google authorization requires sign-in.",
                                "token_broker_recovery_${error.javaClass.simpleName}"
                            )
                        }
                    }
                }
                else -> runOnUiThread {
                    finishAuthorizationFailure(
                        "Google authorization failed.",
                        tokenResult.diagnostic ?: "token_broker_access_token_missing"
                    )
                }
            }
        }, "AIQuotaGoogleTokenBroker").start()
    }

    private fun safeDiagnostic(message: String?): String {
        return message
            ?.replace(Regex("code=[^\\s&]+"), "code=redacted")
            ?.replace(Regex("token=[^\\s&]+"), "token=redacted")
            ?.take(180)
            ?: "none"
    }

    private fun String.requiresInteractiveAuth(): Boolean {
        return this == "identity_resolution_missing" ||
            this == "identity_status_4" ||
            this == "identity_status_6" ||
            this == "identity_status_8" ||
            this == "identity_status_16" ||
            this == "token_broker_recoverable_auth" ||
            this == "token_broker_access_token_missing" ||
            this == "token_broker_GoogleAuthException" ||
            this == "appauth_client_id_missing" ||
            this == "appauth_refresh_token_missing" ||
            this.startsWith("appauth_start_") ||
            this.startsWith("appauth_authorization_") ||
            this.startsWith("appauth_token_")
    }

    private fun String.canUseTokenBrokerFallback(): Boolean {
        return this == "identity_status_8"
    }

    companion object {
        private const val EXTRA_PROVIDER_ID = "providerId"
        private const val REQUEST_AUTHORIZE = 4102
        private const val REQUEST_ACCOUNT_PICKER = 4103
        private const val REQUEST_TOKEN_BROKER_RECOVERY = 4104
        private const val KEY_AUTHORIZATION_STARTED = "authorizationStarted"
        private const val KEY_TOKEN_BROKER_ACCOUNT_NAME = "tokenBrokerAccountName"
        private const val KEY_TOKEN_BROKER_RECOVERY_STARTED = "tokenBrokerRecoveryStarted"

        fun createIntent(context: Context, providerId: ProviderId): Intent {
            return Intent(context, GoogleAuthorizationActivity::class.java)
                .putExtra(EXTRA_PROVIDER_ID, providerId.storageId)
        }
    }
}
