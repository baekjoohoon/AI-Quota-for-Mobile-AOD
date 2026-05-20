package com.aiusage.mobile.providers

import android.app.Activity
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.OAuthProvider

class CopilotOAuthRepository(context: Context) {
    private val secureStore = SecureStringStore(context.applicationContext, PREFERENCES)

    fun githubAccessToken(): String? {
        return secureStore.getString(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() }
    }

    fun accountLogin(): String? {
        return secureStore.getString(KEY_ACCOUNT)?.takeIf { it.isNotBlank() }
    }

    fun signIn(activity: Activity, onComplete: (Result<Unit>) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val provider = OAuthProvider.newBuilder(PROVIDER_ID)
            .setScopes(listOf("read:user", "user:email"))
            .build()

        fun consume(task: Task<AuthResult>) {
            task
                .addOnSuccessListener { result ->
                    onComplete(runCatching { persistAuthResult(result) })
                }
                .addOnFailureListener { error ->
                    onComplete(Result.failure(error))
                }
        }

        val pending = auth.pendingAuthResult
        if (pending != null) {
            consume(pending)
        } else {
            consume(auth.startActivityForSignInWithProvider(activity, provider))
        }
    }

    fun clear() {
        secureStore.remove(KEY_ACCESS_TOKEN, KEY_ACCOUNT)
        runCatching { FirebaseAuth.getInstance().signOut() }
    }

    private fun persistAuthResult(result: AuthResult) {
        val credential = result.credential as? OAuthCredential
        val accessToken = credential?.accessToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("GitHub access token was not returned.")
        secureStore.putString(KEY_ACCESS_TOKEN, accessToken)
        val account = result.additionalUserInfo?.username
            ?: result.user?.email
            ?: result.user?.displayName
        secureStore.putString(KEY_ACCOUNT, account)
    }

    companion object {
        private const val PROVIDER_ID = "github.com"
        private const val PREFERENCES = "account_data_copilot"
        private const val KEY_ACCESS_TOKEN = "github_access_token"
        private const val KEY_ACCOUNT = "github_account"
    }
}
