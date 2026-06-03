package com.aiquota.mobile.providers

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class GeminiCliFirebaseGateway(
    context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    private val appContext = context.applicationContext

    suspend fun startOAuth(): String {
        ensureSignedIn()
        val result = functions
            .getHttpsCallable("startGeminiCliOAuth")
            .call(emptyMap<String, Any>())
            .await()
        val data = result.getData() as? Map<*, *> ?: error("gemini_cli_oauth_response_invalid")
        return data["authorizationUrl"]?.toString()?.takeIf { it.isNotBlank() }
            ?: error("gemini_cli_oauth_url_missing")
    }

    suspend fun completeOAuth(callbackUrl: String): GeminiCliTokenExchangeResult {
        ensureSignedIn()
        val result = functions
            .getHttpsCallable("completeGeminiCliOAuth")
            .call(mapOf("callbackUrl" to callbackUrl))
            .await()
        return GeminiCliTokenExchangeResult.from(result.getData())
    }

    suspend fun refreshAccessToken(refreshToken: String): GeminiCliTokenExchangeResult {
        ensureSignedIn()
        val result = functions
            .getHttpsCallable("refreshGeminiCliAccessToken")
            .call(mapOf("refreshToken" to refreshToken))
            .await()
        return GeminiCliTokenExchangeResult.from(result.getData())
    }

    fun applicationContext(): Context = appContext

    private suspend fun ensureSignedIn() {
        if (auth.currentUser != null) return
        auth.signInAnonymously().await()
        if (auth.currentUser == null) error("firebase_auth_missing")
    }
}

data class GeminiCliTokenExchangeResult(
    val ok: Boolean,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
    val idToken: String?,
    val scope: String?,
    val tokenType: String?
) {
    fun toJsonObject(): JSONObject {
        return JSONObject()
            .put("access_token", accessToken ?: JSONObject.NULL)
            .put("refresh_token", refreshToken ?: JSONObject.NULL)
            .put("expires_in", expiresInSeconds ?: JSONObject.NULL)
            .put("id_token", idToken ?: JSONObject.NULL)
            .put("scope", scope ?: JSONObject.NULL)
            .put("token_type", tokenType ?: JSONObject.NULL)
    }

    companion object {
        fun from(value: Any?): GeminiCliTokenExchangeResult {
            val data = value as? Map<*, *> ?: error("gemini_cli_oauth_response_invalid")
            return GeminiCliTokenExchangeResult(
                ok = data["ok"] == true,
                accessToken = data["accessToken"]?.toString(),
                refreshToken = data["refreshToken"]?.toString(),
                expiresInSeconds = (data["expiresIn"] as? Number)?.toLong(),
                idToken = data["idToken"]?.toString(),
                scope = data["scope"]?.toString(),
                tokenType = data["tokenType"]?.toString()
            )
        }
    }
}
