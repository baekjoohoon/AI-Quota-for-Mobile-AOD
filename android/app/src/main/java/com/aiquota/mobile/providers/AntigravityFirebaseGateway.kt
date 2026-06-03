package com.aiquota.mobile.providers

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class AntigravityFirebaseGateway(
    context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    private val appContext: Context = context.applicationContext

    suspend fun startOAuth(): String {
        ensureSignedIn()
        val result = functions
            .getHttpsCallable("startAntigravityOAuth")
            .call(mapOf("returnToApp" to true))
            .await()
        val data = result.getData() as? Map<*, *> ?: error("antigravity_oauth_response_invalid")
        return data["authorizationUrl"]?.toString()?.takeIf { it.isNotBlank() }
            ?: error("antigravity_oauth_url_missing")
    }

    suspend fun completeOAuth(callbackUrl: String): AntigravityTokenExchangeResult {
        ensureSignedIn()
        val result = functions
            .getHttpsCallable("completeAntigravityOAuth")
            .call(mapOf("callbackUrl" to callbackUrl))
            .await()
        return AntigravityTokenExchangeResult.from(result.getData())
    }

    suspend fun refreshAccessToken(refreshToken: String): AntigravityTokenExchangeResult {
        ensureSignedIn()
        val result = functions
            .getHttpsCallable("refreshAntigravityAccessToken")
            .call(mapOf("refreshToken" to refreshToken))
            .await()
        return AntigravityTokenExchangeResult.from(result.getData())
    }

    suspend fun collectUsage(force: Boolean = false): GatewayResult {
        return runCatching {
            ensureSignedIn()
            val result = functions
                .getHttpsCallable("collectAntigravityUsage")
                .call(mapOf("force" to force))
                .await()
            GatewayResult.from(result.getData())
        }.getOrElse(GatewayResult::fromThrowable)
    }

    suspend fun disconnect() {
        ensureSignedIn()
        functions
            .getHttpsCallable("disconnectAntigravity")
            .call(emptyMap<String, Any>())
            .await()
    }

    fun applicationContext(): Context = appContext

    private suspend fun ensureSignedIn() {
        if (auth.currentUser != null) return
        auth.signInAnonymously().await()
        if (auth.currentUser == null) error("firebase_auth_missing")
    }
}

data class AntigravityTokenExchangeResult(
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
        fun from(value: Any?): AntigravityTokenExchangeResult {
            val data = value as? Map<*, *> ?: error("antigravity_oauth_response_invalid")
            return AntigravityTokenExchangeResult(
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

data class GatewayResult(
    val ok: Boolean,
    val payload: String?,
    val errorKind: String?,
    val requiresAuth: Boolean,
    val retryable: Boolean
) {
    companion object {
        fun from(value: Any?): GatewayResult {
            val data = value as? Map<*, *>
                ?: return GatewayResult(false, null, "gateway_response_invalid", false, true)
            val payload = (data["payload"] as? Map<*, *>)?.toJsonString()
            return GatewayResult(
                ok = data["ok"] == true,
                payload = payload,
                errorKind = data["errorKind"]?.toString(),
                requiresAuth = data["requiresAuth"] == true,
                retryable = data["retryable"] != false
            )
        }

        fun fromThrowable(error: Throwable): GatewayResult {
            val functionsError = error as? FirebaseFunctionsException
                ?: return GatewayResult(false, null, "FIREBASE_GATEWAY_CALL_FAILED", false, true)
            return when (functionsError.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> GatewayResult(
                    false,
                    null,
                    "FIREBASE_AUTH_REQUIRED",
                    true,
                    false
                )
                FirebaseFunctionsException.Code.PERMISSION_DENIED,
                FirebaseFunctionsException.Code.FAILED_PRECONDITION -> GatewayResult(
                    false,
                    null,
                    "FIREBASE_GATEWAY_REJECTED",
                    false,
                    false
                )
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> GatewayResult(
                    false,
                    null,
                    "FIREBASE_GATEWAY_TEMPORARY_FAILURE",
                    false,
                    true
                )
                else -> GatewayResult(false, null, "FIREBASE_GATEWAY_CALL_FAILED", false, true)
            }
        }
    }
}

private fun Map<*, *>.toJsonString(): String {
    val stringMap = entries.associate { (key, value) -> key.toString() to value }
    return JSONObject(stringMap).toString()
}
