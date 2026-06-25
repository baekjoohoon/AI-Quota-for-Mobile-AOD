package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import java.net.URI
import java.util.Locale
import org.json.JSONObject

object ProviderWebCollectorScripts {
    fun isRefreshLoginPage(providerId: ProviderId, url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        return when (providerId) {
            ProviderId.CLAUDE ->
                host.endsWith("claude.ai") && path.contains("login") ||
                    host == "accounts.google.com"
            ProviderId.CODEX ->
                host == "auth.openai.com" ||
                    host == "accounts.google.com" ||
                    ((host == "chatgpt.com" || host.endsWith(".chatgpt.com")) && path == "/auth/login")
            ProviderId.GLM ->
                host == "accounts.google.com" ||
                    ((host == "z.ai" || host == "www.z.ai" || host == "chat.z.ai") &&
                        (path.contains("login") || path.contains("signin") || path.contains("auth")))
            ProviderId.OPENCODE ->
                host == "accounts.google.com" ||
                    host == "github.com" &&
                    (path.startsWith("/login") || path.startsWith("/sessions") || path.startsWith("/session")) ||
                    ((host == "opencode.ai" || host == "www.opencode.ai") &&
                        (path.contains("login") || path.contains("signin")))
            ProviderId.GEMINI ->
                host == "accounts.google.com"
            ProviderId.COPILOT ->
                host == "github.com" &&
                    (path.startsWith("/login") ||
                        path.startsWith("/sessions") ||
                        path.startsWith("/session") ||
                        path.contains("two-factor"))
            ProviderId.ANTIGRAVITY ->
                host == "accounts.google.com"
            ProviderId.CURSOR ->
                host == "accounts.google.com" ||
                    (host == "github.com" &&
                        (path.startsWith("/login") ||
                            path.startsWith("/sessions") ||
                            path.startsWith("/session") ||
                            path.contains("two-factor"))) ||
                    ((host == "cursor.com" || host == "www.cursor.com") &&
                        (path.contains("login") || path.contains("signin")))
        }
    }

    fun isRefreshLoginPage(providerId: ProviderId, url: String, pageText: String): Boolean {
        if (isRefreshLoginPage(providerId, url)) return true
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        if (providerId == ProviderId.CODEX) {
            if (host != "chatgpt.com" && !host.endsWith(".chatgpt.com")) return false
            if (path != "/" && path.isNotBlank()) return false
            return looksLikeChatGptLoginText(pageText)
        }
        if (providerId == ProviderId.GLM) {
            if (host != "z.ai" && host != "www.z.ai" && host != "chat.z.ai") return false
            val text = pageText.lowercase(Locale.US)
            return text.contains("login") &&
                !text.contains("coding plan") &&
                !text.contains("quota") &&
                !text.contains("usage")
        }
        if (providerId == ProviderId.OPENCODE) {
            if (host != "opencode.ai" && host != "www.opencode.ai" && host != "auth.opencode.ai") return false
            val text = pageText.lowercase(Locale.US)
            if (host == "auth.opencode.ai") return looksLikeOpenCodeLoginText(text)
            return (path == "/auth" || path.contains("login") || path.contains("signin")) &&
                looksLikeOpenCodeLoginText(text)
        }
        if (providerId == ProviderId.GEMINI) {
            if (!GeminiUsagePageRoutes.isLoginLandingUrl(url) && !GeminiUsagePageRoutes.isUsageUrl(url)) return false
            return looksLikeGeminiLoginText(pageText)
        }
        if (providerId == ProviderId.CURSOR) {
            if (host !in CURSOR_AUTH_EXCHANGE_HOSTS &&
                host != "cursor.com" &&
                host != "www.cursor.com"
            ) {
                return false
            }
            val text = pageText.lowercase(Locale.US)
            if (host in CURSOR_AUTH_EXCHANGE_HOSTS) return looksLikeCursorLoginText(text)
            return (path.contains("login") || path.contains("signin")) &&
                looksLikeCursorLoginText(text)
        }
        return false
    }

    private fun looksLikeGeminiLoginText(pageText: String): Boolean {
        val text = pageText.lowercase(Locale.US)
        if (text.isBlank()) return false
        val hasUsage = text.contains("usage limit") ||
            text.contains("current usage") ||
            text.contains("5-hour") ||
            text.contains("weekly") ||
            text.contains("사용량") ||
            text.contains("한도")
        if (hasUsage) return false
        val hasLoginAction = text.contains("로그인") ||
            text.contains("sign in") ||
            text.contains("log in")
        val hasGeminiLandingCopy = text.contains("gemini와의 대화") ||
            text.contains("개인 ai") ||
            text.contains("meet gemini") ||
            text.contains("personal ai")
        return hasLoginAction && hasGeminiLandingCopy
    }

    fun shouldRunCollector(providerId: ProviderId, url: String, cookies: Map<String, String>, pageText: String): Boolean {
        if ((providerId == ProviderId.COPILOT ||
                providerId == ProviderId.GEMINI ||
                providerId == ProviderId.ANTIGRAVITY) &&
            url == "about:blank"
        ) {
            return true
        }
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        val text = pageText.lowercase(Locale.US)
        return when (providerId) {
            ProviderId.CLAUDE ->
                host.endsWith("claude.ai") &&
                    !path.contains("login") &&
                    !path.contains("logout") &&
                    (cookies["lastActiveOrg"].isNullOrBlank().not() || path == "/" || path == "/new" || text.contains("claude"))
            ProviderId.CODEX ->
                (host == "chatgpt.com" || host.endsWith(".chatgpt.com") || host == "chat.openai.com") &&
                    path != "/auth/login" &&
                    (path != "/" || pageText.isNotBlank()) &&
                    !looksLikeChatGptLoginText(pageText)
            ProviderId.GLM ->
                (host == "z.ai" || host == "www.z.ai" || host == "chat.z.ai") &&
                    !path.contains("login") &&
                    !path.contains("signin") &&
                    !path.contains("auth") &&
                    (
                        path.startsWith("/manage-apikey/coding-plan") ||
                            path.startsWith("/manage-apikey/subscription") ||
                            path.startsWith("/manage-apikey") ||
                            (host == "chat.z.ai" && path == "/") ||
                            text.contains("coding plan") ||
                            text.contains("quota") ||
                            text.contains("usage")
                        )
            ProviderId.OPENCODE ->
                (host == "opencode.ai" || host == "www.opencode.ai") &&
                    !path.startsWith("/docs") &&
                    !path.startsWith("/brand") &&
                    !path.contains("login") &&
                    !path.contains("signin") &&
                    !looksLikeOpenCodeLoginText(text) &&
                    (
                        path.contains("console") ||
                            path.contains("usage") ||
                            path.contains("billing") ||
                            path.contains("credit") ||
                            path.contains("balance") ||
                            path.contains("zen") ||
                            path.contains("go") ||
                            text.contains("usage") ||
                            text.contains("limit") ||
                            text.contains("balance") ||
                            text.contains("credit")
                        )
            ProviderId.GEMINI ->
                host == "gemini.google.com" &&
                    (path == "/" || path.startsWith("/app") || path.startsWith("/usage"))
            ProviderId.COPILOT ->
                host == "github.com" &&
                    !path.startsWith("/login") &&
                    !path.startsWith("/sessions") &&
                    !path.startsWith("/session") &&
                    !path.contains("two-factor") &&
                    (
                        path.startsWith("/settings/copilot") ||
                            path.startsWith("/settings/billing/premium_requests_usage") ||
                            path.startsWith("/github-copilot")
                        )
            ProviderId.ANTIGRAVITY ->
                (host == "antigravity.google" || host == "www.antigravity.google") &&
                    !path.startsWith("/docs") &&
                    !path.contains("login") &&
                    !path.contains("signin")
            ProviderId.CURSOR ->
                (host == "cursor.com" || host == "www.cursor.com") &&
                    (path.contains("dashboard") || path.contains("settings") || path.contains("account") || path.contains("billing"))
        }
    }

    fun collectorReadinessDiagnostic(
        providerId: ProviderId,
        url: String,
        cookies: Map<String, String>,
        pageText: String
    ): String {
        if (shouldRunCollector(providerId, url, cookies, pageText)) return "ready"
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host.orEmpty().lowercase(Locale.US)
        val path = uri?.path.orEmpty().lowercase(Locale.US)
        val text = pageText.lowercase(Locale.US)
        return when {
            uri == null -> "invalid_url"
            providerId == ProviderId.CLAUDE ->
                "host=$host path=$path hasLastActiveOrg=${!cookies["lastActiveOrg"].isNullOrBlank()} hasClaudeText=${text.contains("claude")}"
            providerId == ProviderId.CODEX ->
                "host=$host path=$path textPresent=${pageText.isNotBlank()} loginText=${looksLikeChatGptLoginText(pageText)}"
            providerId == ProviderId.GLM ->
                "host=$host path=$path hasQuotaText=${text.contains("quota") || text.contains("usage") || text.contains("coding plan")}"
            providerId == ProviderId.OPENCODE ->
                "host=$host path=$path loginText=${looksLikeOpenCodeLoginText(text)} hasUsageText=${text.contains("usage") || text.contains("limit") || text.contains("balance") || text.contains("credit")}"
            providerId == ProviderId.GEMINI ->
                "host=$host path=$path usageUrl=${path.startsWith("/usage")} appUrl=${path.startsWith("/app")}"
            providerId == ProviderId.COPILOT ->
                "host=$host path=$path"
            providerId == ProviderId.ANTIGRAVITY ->
                "host=$host path=$path"
            providerId == ProviderId.CURSOR ->
                "host=$host path=$path"
            else ->
                "host=$host path=$path"
        }
    }

    fun shouldAllowCollectorReinjection(providerId: ProviderId): Boolean {
        return when (providerId) {
            ProviderId.CLAUDE,
            ProviderId.OPENCODE,
            ProviderId.COPILOT,
            ProviderId.CURSOR,
            ProviderId.GLM,
            ProviderId.GEMINI -> true
            ProviderId.CODEX,
            ProviderId.ANTIGRAVITY -> false
        }
    }

    private fun looksLikeChatGptLoginText(pageText: String): Boolean {
        val text = pageText.lowercase(Locale.US)
        return (text.contains("로그인") && (
            text.contains("회원가입") ||
                text.contains("회원 가입") ||
                text.contains("지금 무슨 생각")
            )) ||
            text.contains("google 계정으로 계속하기") ||
            text.contains("log in or sign up") ||
            (text.contains("log in") && text.contains("sign up")) ||
            text.contains("continue with google")
    }

    private fun looksLikeOpenCodeLoginText(pageText: String): Boolean {
        val text = pageText.lowercase(Locale.US)
        if (text.isBlank()) return false
        val hasUsage = text.contains("usage") ||
            text.contains("limit") ||
            text.contains("balance") ||
            text.contains("credit")
        return !hasUsage &&
            (text.contains("sign in") ||
                text.contains("login") ||
                text.contains("log in") ||
                text.contains("continue with google") ||
                text.contains("continue with github"))
    }

    private fun looksLikeCursorLoginText(pageText: String): Boolean {
        val text = pageText.lowercase(Locale.US)
        if (text.isBlank()) return false
        val hasUsage = text.contains("usage") ||
            text.contains("dashboard") ||
            text.contains("billing") ||
            text.contains("requests")
        return !hasUsage &&
            (text.contains("sign in") ||
                text.contains("login") ||
                text.contains("log in") ||
                text.contains("continue with google") ||
                text.contains("continue with github") ||
                text.contains("single sign-on"))
    }

    private val CURSOR_AUTH_EXCHANGE_HOSTS = setOf(
        "api.workos.com",
        "auth.workos.com",
        "workos.com",
        "authenticate.cursor.sh",
        "authenticator.cursor.sh"
    )

    fun shouldRunCollectorOnResource(providerId: ProviderId, url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        return when (providerId) {
            ProviderId.CLAUDE ->
                host.endsWith("claude.ai") &&
                    (path == "/api/account_profile" ||
                        path == "/api/organizations" ||
                        path == "/api/organizations/me" ||
                        (path.startsWith("/api/bootstrap/") && path.endsWith("/current_user_access")) ||
                        (path.startsWith("/api/organizations/") && path.endsWith("/subscription_details")))
            ProviderId.CODEX ->
                (host == "chatgpt.com" || host.endsWith(".chatgpt.com") || host == "chat.openai.com") &&
                    (path == "/api/auth/session" ||
                        path == "/backend-api/subscriptions" ||
                        path == "/backend-api/me" ||
                        path.startsWith("/backend-api/accounts/check"))
            ProviderId.GLM ->
                (host == "api.z.ai" &&
                    (path == "/api/monitor/usage/quota/limit" ||
                        path == "/api/biz/subscription/list" ||
                        path.contains("/api/monitor/usage"))) ||
                    (host == "chat.z.ai" &&
                        (path.contains("usage") ||
                            path.contains("quota") ||
                            path.contains("plan") ||
                            path.contains("subscription"))) ||
                    ((host == "z.ai" || host == "www.z.ai" || host == "chat.z.ai") &&
                        path.startsWith("/manage-apikey"))
            ProviderId.OPENCODE ->
                (host == "opencode.ai" || host == "www.opencode.ai") &&
                    !path.startsWith("/docs") &&
                    !path.startsWith("/brand") &&
                    (path == "/auth" ||
                        path.contains("usage") ||
                        path.contains("billing") ||
                        path.contains("credit") ||
                        path.contains("balance") ||
                        path.contains("subscription") ||
                        path.contains("console") ||
                        path.contains("zen") ||
                        path.contains("go"))
            ProviderId.CURSOR ->
                (host == "cursor.com" || host == "www.cursor.com") &&
                    (path == "/api/auth/stripe" ||
                        path == "/api/usage" ||
                        path == "/api/auth/usage" ||
                        path == "/api/usage-summary" ||
                        path == "/api/dashboard/get-credit-grants-balance")
            ProviderId.GEMINI ->
                host == "gemini.google.com" && (path.startsWith("/app") || path.startsWith("/usage"))
            ProviderId.COPILOT ->
                (host == "github.com" &&
                    (path == "/github-copilot/chat/entitlement" ||
                        path == "/github-copilot/chat/token" ||
                        path == "/settings/billing/premium_requests_usage" ||
                        path == "/settings/billing/copilot_usage_card" ||
                        path == "/copilot_internal/user")) ||
                    (host == "api.github.com" && path == "/copilot_internal/user")
            ProviderId.ANTIGRAVITY ->
                (host == "antigravity.google" || host == "www.antigravity.google") &&
                    (path.contains("usage") || path.contains("quota") || path.contains("plan"))
            else -> false
        }
    }

    fun shouldRunCollectorFromResource(providerId: ProviderId, pageUrl: String, resourceUrl: String): Boolean {
        return shouldRunCollectorOnResource(providerId, resourceUrl) &&
            (shouldRunCollector(providerId, pageUrl, emptyMap(), "") || isCodexChatGptRoot(providerId, pageUrl))
    }

    fun shouldAcceptCollectorPayload(providerId: ProviderId, pageUrl: String): Boolean {
        if ((providerId == ProviderId.COPILOT ||
                providerId == ProviderId.GEMINI ||
                providerId == ProviderId.ANTIGRAVITY) &&
            pageUrl == "about:blank"
        ) {
            return true
        }
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        return when (providerId) {
            ProviderId.CLAUDE ->
                host.endsWith("claude.ai") &&
                    !path.contains("login") &&
                    !path.contains("logout")
            ProviderId.CODEX ->
                (host == "chatgpt.com" || host.endsWith(".chatgpt.com") || host == "chat.openai.com") &&
                    path != "/auth/login"
            else -> shouldRunCollector(providerId, pageUrl, emptyMap(), "")
        }
    }

    fun shouldAcceptCollectorPayload(providerId: ProviderId, pageUrl: String, rawPayload: String): Boolean {
        if (!shouldAcceptCollectorPayload(providerId, pageUrl)) return false
        val payloadProvider = runCatching {
            JSONObject(rawPayload)
                .optString("provider")
                .trim()
                .lowercase(Locale.US)
        }.getOrDefault("")
        return payloadProvider == providerId.storageId
    }

    fun shouldAcceptCollectorError(providerId: ProviderId, pageUrl: String, rawError: String? = null): Boolean {
        if (isCodexChatGptRoot(providerId, pageUrl)) {
            return isTrustedRootAuthCollectorError(providerId, pageUrl, rawError)
        }
        return shouldAcceptCollectorPayload(providerId, pageUrl) ||
            isRefreshLoginPage(providerId, pageUrl) ||
            isTrustedRootAuthCollectorError(providerId, pageUrl, rawError)
    }

    private fun isTrustedRootAuthCollectorError(providerId: ProviderId, pageUrl: String, rawError: String?): Boolean {
        if (providerId != ProviderId.CODEX || rawError.isNullOrBlank()) return false
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        if ((host != "chatgpt.com" && !host.endsWith(".chatgpt.com")) || (path != "/" && path.isNotBlank())) return false
        val payloadProvider = runCatching {
            JSONObject(rawError)
                .optString("provider")
                .trim()
                .lowercase(Locale.US)
        }.getOrDefault("")
        if (payloadProvider != providerId.storageId) return false
        val errorKind = ProviderCollectorErrorPolicy.errorKind(rawError)
        return errorKind == "codex_auth_required" ||
            errorKind == "codex_usage_unavailable"
    }

    private fun isCodexChatGptRoot(providerId: ProviderId, pageUrl: String): Boolean {
        if (providerId != ProviderId.CODEX) return false
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        return (host == "chatgpt.com" || host.endsWith(".chatgpt.com") || host == "chat.openai.com") &&
            (path == "/" || path.isBlank())
    }

    fun build(
        providerId: ProviderId,
        cookies: Map<String, String>,
        geminiCollectorAsset: String,
        antigravityCollectorAsset: String = "",
        observedAccountId: String? = null,
        pageText: String = "",
        pageUrl: String = "",
        awaitInteractiveLoginUsage: Boolean = false
    ): String {
        return common(providerId, cookies, observedAccountId, pageText, pageUrl, awaitInteractiveLoginUsage) + "\n" +
            ProviderScriptProviders.providerFor(providerId).collectorScript(
                ProviderCollectorAssets(
                    geminiCollectorAsset = geminiCollectorAsset,
                    antigravityCollectorAsset = antigravityCollectorAsset
                )
            )
    }

    internal fun commonForTest(
        providerId: ProviderId,
        cookies: Map<String, String>,
        observedAccountId: String?,
        pageText: String,
        pageUrl: String = "",
        awaitInteractiveLoginUsage: Boolean = false
    ): String = common(providerId, cookies, observedAccountId, pageText, pageUrl, awaitInteractiveLoginUsage)

    private fun common(
        providerId: ProviderId,
        cookies: Map<String, String>,
        observedAccountId: String?,
        pageText: String,
        pageUrl: String,
        awaitInteractiveLoginUsage: Boolean
    ): String {
        val collectorCookies = when (providerId) {
            ProviderId.CLAUDE -> cookies.filterKeys { it == "lastActiveOrg" }
            else -> emptyMap()
        }
        val cookieJson = JSONObject(collectorCookies).toString()
        val observedAccountJson = JSONObject.quote(observedAccountId.orEmpty())
        val pageTextJson = JSONObject.quote(pageText)
        val pageUrlJson = JSONObject.quote(pageUrl)
        val awaitInteractiveLoginUsageJson = if (awaitInteractiveLoginUsage) "true" else "false"
        return """
            (function(){
              if (!window.__AIQuotaStartProviderCollector) {
                window.__AIQuotaStartProviderCollector = function(provider, force) {
                  var now = Date.now ? Date.now() : new Date().getTime();
                  var collectorStartTtlMs = 30000;
                  var href = "";
                  try { href = String(location && location.href || ""); } catch (error) {}
                  var state = window.__AIQuotaProviderCollectorState || {};
                  if (!force && state.provider === provider && state.href === href && now - (state.startedAt || 0) < collectorStartTtlMs) {
                    return false;
                  }
                  window.__AIQuotaProviderCollectorState = {
                    provider: provider,
                    startedAt: now,
                    href: href
                  };
                  return true;
                };
              }
              window.__AIQuotaCollector = {
                provider: "${providerId.storageId}",
                cookies: $cookieJson,
                observedAccountId: $observedAccountJson,
                pageText: $pageTextJson,
                pageUrl: $pageUrlJson,
                awaitInteractiveLoginUsage: $awaitInteractiveLoginUsageJson,
                post: function(payload) {
                  payload = payload || {};
                  payload.provider = payload.provider || "${providerId.storageId}";
                  payload.collectorMode = payload.collectorMode || "webview-js";
                  window.AIQuotaCollectorBridge.postUsagePayload(JSON.stringify(payload));
                },
                fail: function(kind, message) {
                  window.AIQuotaCollectorBridge.postCollectorError(JSON.stringify({
                    provider: "${providerId.storageId}",
                    errorKind: kind || "collector_error",
                    message: message || ""
                  }));
                },
                fetchJson: function(url) {
                  return fetch(url, {
                    credentials: "include",
                    headers: { "accept": "application/json, text/html" }
                  }).then(function(response) {
                    return response.text().then(function(text) {
                      var json = {};
                      try { json = text ? JSON.parse(text) : {}; } catch (error) { json = { rawText: text }; }
                      return { ok: response.ok, status: response.status, url: url, json: json };
                    });
                  });
                },
                text: function() {
                  return ((document.documentElement && document.documentElement.innerText) || document.title || "").slice(0, 50000);
                },
                rows: function() {
                  var values = [];
                  function push(value) {
                    if (value === null || value === undefined) return;
                    var text = String(value);
                    if (text.length > 0) values.push(text.slice(0, 200000));
                  }
                  try {
                    for (var i = 0; i < localStorage.length; i += 1) push(localStorage.getItem(localStorage.key(i)));
                  } catch (error) {}
                  try {
                    for (var j = 0; j < sessionStorage.length; j += 1) push(sessionStorage.getItem(sessionStorage.key(j)));
                  } catch (error) {}
                  try {
                    Array.prototype.slice.call(document.scripts || []).forEach(function(script) {
                      if (script && script.textContent) push(script.textContent);
                    });
                  } catch (error) {}
                  values.push(this.text());
                  return values;
                },
                first: function(object, keys) {
                  if (!object) return null;
                  for (var index = 0; index < keys.length; index += 1) {
                    var key = keys[index];
                    if (Object.prototype.hasOwnProperty.call(object, key) && object[key] !== null && object[key] !== undefined && object[key] !== "") return object[key];
                  }
                  return null;
                },
                number: function(value) {
                  if (value === null || value === undefined || value === "") return null;
                  var parsed = Number(value);
                  return Number.isFinite(parsed) ? parsed : null;
                },
                line: function(value, fallbackLabel) {
                  if (!value) return null;
                  var used = this.number(this.first(value, ["used_percent", "usedPercent", "totalPercentUsed", "utilization", "u"]));
                  var remainingFraction = this.number(this.first(value, ["remainingFraction", "remaining_fraction"]));
                  var remainingPercent = this.number(this.first(value, ["remaining_percent", "remainingPercent"]));
                  var amountRemaining = this.number(this.first(value, ["remaining", "remainingAmount", "remaining_amount", "balance"]));
                  var amountUsed = this.number(this.first(value, ["used", "usedAmount", "used_amount"]));
                  var amountLimit = this.number(this.first(value, ["limit", "limitAmount", "limit_amount", "total", "total_credits"]));
                  var line = {};
                  if (used !== null) line.used_percent = used;
                  if (remainingFraction !== null) line.remaining_fraction = remainingFraction;
                  if (remainingPercent !== null) line.remaining_percent = remainingPercent;
                  if (used === null && remainingFraction === null && remainingPercent === null && amountLimit !== null && amountLimit > 0) {
                    if (amountRemaining !== null) line.remaining_percent = (amountRemaining / amountLimit) * 100;
                    if (amountUsed !== null) line.used_percent = (amountUsed / amountLimit) * 100;
                  }
                  if (line.used_percent === undefined && line.remaining_fraction === undefined && line.remaining_percent === undefined) return null;
                  line.reset_text = this.first(value, ["reset_text", "resetText", "t"]);
                  line.resetAt = this.first(value, ["resetAt", "resets_at", "r"]);
                  line.label = this.first(value, ["label", "name", "title"]) || fallbackLabel;
                  return line;
                }
              };
            })();
        """.trimIndent()
    }

    internal fun claude(): String {
        return """
            (function(){
              if (!window.__AIQuotaStartProviderCollector || !window.__AIQuotaStartProviderCollector("claude")) return;
              var c = window.__AIQuotaCollector;
              if (!c) return;
              function orgFromText(text) {
                var apiMatch = /\/api\/organizations\/([A-Za-z0-9_-]{8,}(?:-[A-Za-z0-9_-]+)*)/.exec(text || "");
                if (apiMatch && apiMatch[1] !== "discoverable") return apiMatch[1];
                var match = /org[_-][A-Za-z0-9_-]+/.exec(text || "");
                return match ? match[0] : null;
              }
              function isNumericClaudeOrgId(value) {
                return /^\d+$/.test(String(value || ""));
              }
              function preferClaudeUsageOrgId(current, candidate) {
                if (!candidate) return current || null;
                if (!current || (isNumericClaudeOrgId(current) && !isNumericClaudeOrgId(candidate))) return candidate;
                return current;
              }
              function pickOrg(value) {
                if (!value) return null;
                if (typeof value === "string") return orgFromText(value) || value;
                if (Array.isArray(value)) {
                  for (var i = 0; i < value.length; i += 1) {
                    var picked = pickOrg(value[i]);
                    if (picked) return picked;
                  }
                }
                if (typeof value === "object") {
                  return value.uuid || value.organization_uuid || value.organizationUuid || value.id || value.organization_id || value.organizationId || orgFromText(JSON.stringify(value));
                }
                return null;
              }
              function pickClaudePlan(value) {
                if (!value) return null;
                if (typeof value === "string") return value.length > 0 && value.length < 80 ? value : null;
                if (Array.isArray(value)) {
                  for (var i = 0; i < value.length; i += 1) {
                    var fromArray = pickClaudePlan(value[i]);
                    if (fromArray) return fromArray;
                  }
                  return null;
                }
                if (typeof value !== "object") return null;
                var directKeys = [
                  "plan",
                  "plan_name",
                  "planName",
                  "plan_type",
                  "planType",
                  "subscription_plan",
                  "subscriptionPlan",
                  "tier",
                  "membershipType",
                  "product_name",
                  "productName"
                ];
                for (var d = 0; d < directKeys.length; d += 1) {
                  var direct = value[directKeys[d]];
                  if (typeof direct === "string" && direct.length > 0 && direct.length < 80) return direct;
                }
                var containers = [
                  "subscription",
                  "subscription_details",
                  "subscriptionDetails",
                  "organization",
                  "billing",
                  "plan_info",
                  "planInfo"
                ];
                for (var cIndex = 0; cIndex < containers.length; cIndex += 1) {
                  var nested = value[containers[cIndex]];
                  if (nested && typeof nested === "object") {
                    var fromNested = pickClaudePlan(nested);
                    if (fromNested) return fromNested;
                  }
                }
                return null;
              }
              function looksLikeClaudeUsageText(text) {
                var lower = String(text || "").toLowerCase();
                return lower.indexOf("claude: session reset") >= 0 ||
                  lower.indexOf("claude: weekly reset") >= 0 ||
                  lower.indexOf("claude: sonnet reset") >= 0 ||
                  lower.indexOf("claude: opus reset") >= 0 ||
                  lower.indexOf("claude: cowork reset") >= 0 ||
                  lower.indexOf("claude: design reset") >= 0 ||
                  lower.indexOf("claude session limit resets") >= 0 ||
                  lower.indexOf("claude weekly limit resets") >= 0 ||
                  lower.indexOf("claude weekly sonnet limit resets") >= 0 ||
                  lower.indexOf("claude weekly opus limit resets") >= 0 ||
                  lower.indexOf("claude weekly cowork limit resets") >= 0 ||
                  lower.indexOf("claude weekly design limit resets") >= 0 ||
                  lower.indexOf("five_hour") >= 0 ||
                  lower.indexOf("seven_day") >= 0 ||
                  lower.indexOf("seven_day_omelette") >= 0 ||
                  (lower.indexOf("claude") >= 0 && lower.indexOf("reset") >= 0 && lower.indexOf("%") >= 0);
              }
              function pushClaudeNetworkRow(url, text) {
                if (!looksLikeClaudeUsageText(String(url || "") + "\n" + String(text || ""))) return;
                window.__AIQuotaClaudeNetworkRows = window.__AIQuotaClaudeNetworkRows || [];
                window.__AIQuotaClaudeNetworkRows.push(String(url || "") + "\n" + String(text || "").slice(0, 200000));
                if (window.__AIQuotaClaudeNetworkRows.length > 80) window.__AIQuotaClaudeNetworkRows.shift();
              }
              function installClaudeNetworkHook() {
                if (window.__AIQuotaClaudeNetworkHookInstalled) return;
                window.__AIQuotaClaudeNetworkHookInstalled = true;
                window.__AIQuotaClaudeNetworkRows = window.__AIQuotaClaudeNetworkRows || [];
                try {
                  var originalFetch = window.fetch;
                  if (originalFetch) {
                    window.fetch = function(input, init) {
                      var url = typeof input === "string" ? input : (input && input.url) || "";
                      return originalFetch.apply(this, arguments).then(function(response) {
                        try {
                          response.clone().text().then(function(text) {
                            pushClaudeNetworkRow(url, text);
                          }).catch(function(){});
                        } catch (error) {}
                        return response;
                      });
                    };
                  }
                } catch (error) {}
                try {
                  var originalOpen = XMLHttpRequest.prototype.open;
                  var originalSend = XMLHttpRequest.prototype.send;
                  XMLHttpRequest.prototype.open = function(method, url) {
                    this.__aiQuotaClaudeUrl = url;
                    return originalOpen.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener("load", function() {
                      try { pushClaudeNetworkRow(this.__aiQuotaClaudeUrl || "", this.responseText || ""); } catch (error) {}
                    });
                    return originalSend.apply(this, arguments);
                  };
                } catch (error) {}
              }
              function parseClaudeJsonCandidates(row) {
                var text = String(row || "");
                var candidates = [];
                try { candidates.push(JSON.parse(text)); } catch (error) {}
                var objectStart = text.indexOf("{");
                var objectEnd = text.lastIndexOf("}");
                if (objectStart >= 0 && objectEnd > objectStart) {
                  try { candidates.push(JSON.parse(text.slice(objectStart, objectEnd + 1))); } catch (error) {}
                }
                var arrayStart = text.indexOf("[");
                var arrayEnd = text.lastIndexOf("]");
                if (arrayStart >= 0 && arrayEnd > arrayStart) {
                  try { candidates.push(JSON.parse(text.slice(arrayStart, arrayEnd + 1))); } catch (error) {}
                }
                return candidates;
              }
              function pickClaudePlanFromText(text) {
                var value = String(text || "");
                var match = /\bClaude\s+(Free|Pro|Max|Team|Enterprise)(?:\s+\d+x)?\b/i.exec(value);
                if (match) return match[0].replace(/^Claude\s+/i, "");
                var planAfterLabel = /(?:current\s+plan|plan|subscription|membership|요금제|플랜)[\s:：\-]*[\r\n ]{0,20}(?:Claude\s+)?(Free|Pro|Max|Team|Enterprise)(?:\s+(\d+x))?/i.exec(value);
                if (planAfterLabel) return planAfterLabel[1] + (planAfterLabel[2] ? " " + planAfterLabel[2] : "");
                var planBeforeLabel = /(?:Claude\s+)?(Free|Pro|Max|Team|Enterprise)(?:\s+(\d+x))?\s+(?:plan|subscription|membership|요금제|플랜)\b/i.exec(value);
                if (planBeforeLabel) return planBeforeLabel[1] + (planBeforeLabel[2] ? " " + planBeforeLabel[2] : "");
                return null;
              }
              function claudeRemainingPercentFromText(text) {
                var match = /(\d{1,3}(?:\.\d+)?)\s*%\s*(?:left|remaining)/i.exec(text);
                if (!match) match = /(?:left|remaining)[^\d]{0,24}(\d{1,3}(?:\.\d+)?)\s*%/i.exec(text);
                if (!match) match = /(\d{1,3}(?:\.\d+)?)\s*%/.exec(text);
                if (!match) return null;
                var parsed = Number(match[1]);
                if (!Number.isFinite(parsed)) return null;
                return Math.max(0, Math.min(100, parsed));
              }
              function claudeResetTextFromWindow(text) {
                var lines = String(text || "").split(/\r?\n/);
                for (var i = 0; i < lines.length; i += 1) {
                  var value = String(lines[i] || "").replace(/\s+/g, " ").trim();
                  if (!value || claudeUsageBoundaryLine(value)) continue;
                  var match = /\bresets?\b[^.\n]{0,120}/i.exec(value) ||
                    /\breset\b\s+(?:in|at|on|after|by)[^.\n]{0,120}/i.exec(value);
                  if (match) return match[0].replace(/\s+/g, " ").trim();
                }
                return null;
              }
              function claudeUsageBoundaryLine(line) {
                var value = String(line || "").replace(/\s+/g, " ").trim();
                if (!value) return false;
                if (/\blimit\s+resets?\b/i.test(value) || /\bresets?\s+in\b/i.test(value)) return false;
                return /^Claude:\s*(Session|Weekly|Sonnet|Opus|Cowork|Design)\s+Reset$/i.test(value) ||
                  /^Claude\s+(Session|Weekly|Sonnet|Opus|Cowork|Design)\b/i.test(value) ||
                  /^(Session|Weekly|Sonnet|Opus|Cowork|Design)\s+Reset$/i.test(value);
              }
              function claudeWindowText(text, label) {
                var lines = String(text || "").split(/\r?\n/);
                var lowerLabel = label.toLowerCase();
                for (var lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
                  if (String(lines[lineIndex] || "").toLowerCase().indexOf(lowerLabel) < 0) continue;
                  var windowLines = [];
                  for (var windowIndex = lineIndex; windowIndex < lines.length; windowIndex += 1) {
                    if (windowIndex > lineIndex && claudeUsageBoundaryLine(lines[windowIndex])) break;
                    windowLines.push(lines[windowIndex]);
                    if (windowLines.join("\n").length >= 700) break;
                  }
                  return windowLines.join("\n");
                }
                var lower = String(text || "").toLowerCase();
                var index = lower.indexOf(lowerLabel);
                return index >= 0 ? text.slice(index, index + 700) : null;
              }
              function claudeTextLine(text, labels) {
                for (var i = 0; i < labels.length; i += 1) {
                  var label = labels[i];
                  var windowText = claudeWindowText(text, label);
                  if (!windowText) continue;
                  var remaining = claudeRemainingPercentFromText(windowText);
                  if (remaining === null) continue;
                  var line = { remaining_percent: remaining };
                  var resetText = claudeResetTextFromWindow(windowText);
                  if (resetText) line.resetText = resetText;
                  return line;
                }
                return null;
              }
              function scanClaudeUsageText(row) {
                var text = String(row || "");
                if (!looksLikeClaudeUsageText(text)) return null;
                var usage = {};
                var session = claudeTextLine(text, ["Claude: Session Reset", "Claude session limit resets", "Claude Session", "Session Reset"]);
                if (session) usage.session = session;
                var weekly = claudeTextLine(text, ["Claude: Weekly Reset", "Claude weekly limit resets", "Claude Weekly", "Weekly Reset"]);
                if (weekly) usage.weekly = weekly;
                var sonnet = claudeTextLine(text, ["Claude: Sonnet Reset", "Claude weekly Sonnet limit resets", "Claude Sonnet", "Sonnet Reset"]);
                if (sonnet) usage.sonnet = sonnet;
                var opus = claudeTextLine(text, ["Claude: Opus Reset", "Claude weekly Opus limit resets", "Claude Opus", "Opus Reset"]);
                if (opus) usage.opus = opus;
                var cowork = claudeTextLine(text, ["Claude: Cowork Reset", "Claude weekly Cowork limit resets", "Claude Cowork", "Cowork Reset"]);
                if (cowork) usage.cowork = cowork;
                var design = claudeTextLine(text, ["Claude: Design Reset", "Claude weekly Design limit resets", "Claude Design", "Design Reset"]);
                if (design) usage.design = design;
                return Object.keys(usage).length > 0 ? usage : null;
              }
              var claudeScanBudget = 0;
              function scanClaudeUsageCandidate(value, depth) {
                if (!value || depth > 7 || claudeScanBudget <= 0) return null;
                claudeScanBudget -= 1;
                if (hasClaudeUsagePayload(value)) return value.usage || value;
                if (Array.isArray(value)) {
                  for (var index = 0; index < value.length && index < 80; index += 1) {
                    var fromArray = scanClaudeUsageCandidate(value[index], depth + 1);
                    if (fromArray) return fromArray;
                  }
                  return null;
                }
                if (typeof value !== "object") return null;
                var keys = Object.keys(value);
                for (var keyIndex = 0; keyIndex < keys.length && keyIndex < 80; keyIndex += 1) {
                  var fromChild = scanClaudeUsageCandidate(value[keys[keyIndex]], depth + 1);
                  if (fromChild) return fromChild;
                }
                return null;
              }
              function extractClaudeUsageFromRows() {
                var rows = (window.__AIQuotaClaudeNetworkRows || []).concat(c.rows ? c.rows() : []);
                for (var rowIndex = 0; rowIndex < rows.length; rowIndex += 1) {
                  if (!looksLikeClaudeUsageText(rows[rowIndex])) continue;
                  var textUsage = scanClaudeUsageText(rows[rowIndex]);
                  if (textUsage) return textUsage;
                  var candidates = parseClaudeJsonCandidates(rows[rowIndex]);
                  for (var candidateIndex = 0; candidateIndex < candidates.length; candidateIndex += 1) {
                    claudeScanBudget = 1200;
                    var found = scanClaudeUsageCandidate(candidates[candidateIndex], 0);
                    if (found) return found;
                  }
                }
                return null;
              }
              function scanClaudePageState() {
                var globals = [];
                try { globals.push(window.__NEXT_DATA__); } catch (error) {}
                try { globals.push(window.__remixContext); } catch (error) {}
                try { globals.push(window.__reactRouterContext); } catch (error) {}
                try { globals.push(window.__APP_DATA__); } catch (error) {}
                try { globals.push(window.__INITIAL_STATE__); } catch (error) {}
                for (var i = 0; i < globals.length; i += 1) {
                  claudeScanBudget = 1200;
                  var found = scanClaudeUsageCandidate(globals[i], 0);
                  if (found) return found;
                }
                return extractClaudeUsageFromRows();
              }
              function claudeUsagePayloadRoot(value) {
                if (value && typeof value === "object" && value.usage && typeof value.usage === "object") return value.usage;
                return value;
              }
              function mergeClaudeObjectKeepingExisting(base, extra) {
                var merged = {};
                var baseKeys = Object.keys(base || {});
                for (var i = 0; i < baseKeys.length; i += 1) merged[baseKeys[i]] = base[baseKeys[i]];
                var extraKeys = Object.keys(extra || {});
                for (var j = 0; j < extraKeys.length; j += 1) {
                  var key = extraKeys[j];
                  var left = merged[key];
                  var right = extra[key];
                  if (!Object.prototype.hasOwnProperty.call(merged, key)) {
                    merged[key] = right;
                  } else if (left && right && typeof left === "object" && typeof right === "object" && !Array.isArray(left) && !Array.isArray(right)) {
                    merged[key] = mergeClaudeObjectKeepingExisting(left, right);
                  }
                }
                return merged;
              }
              function mergeClaudeUsagePayload(base, extra) {
                var left = claudeUsagePayloadRoot(base);
                var right = claudeUsagePayloadRoot(extra);
                if (!left || typeof left !== "object") return right || null;
                if (!right || typeof right !== "object") return left;
                return mergeClaudeObjectKeepingExisting(left, right);
              }
              async function probeClaudeSession() {
                installClaudeNetworkHook();
                var result = {
                  loggedIn: false,
                  usageOk: false,
                  accountOk: false,
                  account: null,
                  plan: null,
                  orgId: null,
                  subscription: null,
                  usage: null
                };
                var orgId = c.cookies.lastActiveOrg || orgFromText(c.rows().join("\n"));
                var meta = {};
                var pageUsage = scanClaudePageState();
                if (pageUsage) {
                  result.loggedIn = true;
                  result.usageOk = true;
                  result.usage = pageUsage;
                }
                result.plan = pickClaudePlanFromText(c.text());
                try {
                  var organizations = await c.fetchJson("https://claude.ai/api/organizations");
                  if (organizations.ok) {
                    result.loggedIn = true;
                    result.accountOk = true;
                    meta = organizations.json || {};
                    orgId = preferClaudeUsageOrgId(orgId, pickOrg(meta));
                  }
                } catch (error) {}
                if (!orgId) {
                  try {
                    var organization = await c.fetchJson("https://claude.ai/api/organizations/me");
                    if (organization.ok) {
                      result.loggedIn = true;
                      result.accountOk = true;
                      meta = organization.json || meta;
                      orgId = preferClaudeUsageOrgId(orgId, pickOrg(organization.json));
                    }
                  } catch (error) {}
                }
                result.orgId = orgId || null;
                result.account = meta.email || meta.account || meta.name || null;
                result.plan = result.plan || pickClaudePlan(meta);
                if (orgId) {
                  try {
                    var subscription = await c.fetchJson("https://claude.ai/api/organizations/" + encodeURIComponent(orgId) + "/subscription_details");
                    if (subscription.ok) {
                      result.loggedIn = true;
                      result.accountOk = true;
                      result.subscription = subscription.json || {};
                      result.plan = result.plan || pickClaudePlan(result.subscription);
                    }
                  } catch (error) {}
                }
                if (orgId) {
                  try {
                    var usage = await c.fetchJson("https://claude.ai/api/organizations/" + encodeURIComponent(orgId) + "/usage");
                    if (usage.ok && usage.json) {
                      var mergedUsage = mergeClaudeUsagePayload(result.usage, usage.json);
                      if (hasClaudeUsagePayload(mergedUsage)) {
                        result.usage = mergedUsage;
                        result.usageOk = true;
                      }
                      result.loggedIn = true;
                    }
                  } catch (error) {}
                }
                if (!result.usageOk) {
                  var latePageUsage = scanClaudePageState();
                  if (latePageUsage) {
                    result.loggedIn = true;
                    result.usageOk = true;
                    result.usage = latePageUsage;
                  }
                }
                return result;
              }
              function hasClaudeUsagePayload(value) {
                if (!value || typeof value !== "object") return false;
                if (value.usage ||
                  value.five_hour || value.fiveHour ||
                  value.seven_day || value.sevenDay ||
                  value.seven_day_omelette || value.sevenDayOmelette ||
                  value.session || value.weekly ||
                  value.weekly_opus || value.weeklyOpus || value.opus_weekly || value.opusWeekly || value.opus ||
                  value.weekly_sonnet || value.weeklySonnet || value.sonnet_weekly || value.sonnetWeekly || value.sonnet ||
                  value.weekly_cowork || value.weeklyCowork || value.cowork_weekly || value.coworkWeekly || value.cowork ||
                  value.weekly_design || value.weeklyDesign || value.design_weekly || value.designWeekly || value.design ||
                  value.models || value.model_usage || value.modelUsage || value.model_limits || value.modelLimits ||
                  value.limits || value.quotas || value.quotaBuckets || value.quota_buckets || value.buckets || value.windows) {
                  return true;
                }
                return false;
              }
              function runProbe() {
                probeClaudeSession().then(function(result) {
                  if (result.loggedIn && result.usageOk && hasClaudeUsagePayload(result.usage)) {
                    c.post({
                      provider: "claude",
                      account: result.account,
                      plan: result.plan,
                      organizationId: result.orgId,
                      usage: result.usage
                    });
                    return;
                  }
                  setTimeout(runProbe, 2500);
                }).catch(function() {
                  setTimeout(runProbe, 2500);
                });
              }
              setTimeout(runProbe, 1200);
            })();
        """.trimIndent()
    }

    internal fun codexWebViewState(): String {
        return """
            (function(){
              if (!window.__AIQuotaStartProviderCollector || !window.__AIQuotaStartProviderCollector("codex")) return;
              var c = window.__AIQuotaCollector;
              if (!c) return;
              var attempts = 0;
              var codexUsageDashboardUrls = [
                "https://chatgpt.com/codex/cloud/settings/analytics#usage",
                "https://chatgpt.com/codex/settings/usage"
              ];
              var codexInitialProbeDelayMs = 100;
              var codexProbeRetryDelayMs = 600;
              console.log("AIQuotaCodex collector started");
              window.__AIQuotaCodexNetworkRows = window.__AIQuotaCodexNetworkRows || [];
              function looksLikeCodexAccountId(value) {
                if (typeof value !== "string") return false;
                return /^acc_[A-Za-z0-9_-]{8,}$/.test(value) ||
                  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(value);
              }
              function pickCodexAccountId(value) {
                if (!value) return null;
                if (typeof value === "string") {
                  if (looksLikeCodexAccountId(value)) return value;
                  var patterns = [
                    /"chatgpt_account_id"\s*:\s*"([^"]+)"/i,
                    /"chatgptAccountId"\s*:\s*"([^"]+)"/i,
                    /"current_account_id"\s*:\s*"([^"]+)"/i,
                    /"currentAccountId"\s*:\s*"([^"]+)"/i,
                    /"selected_account_id"\s*:\s*"([^"]+)"/i,
                    /"selectedAccountId"\s*:\s*"([^"]+)"/i,
                    /"account_id"\s*:\s*"([^"]+)"/i,
                    /"accountId"\s*:\s*"([^"]+)"/i,
                    /(acc_[A-Za-z0-9_-]+)/,
                    /([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/
                  ];
                  for (var i = 0; i < patterns.length; i += 1) {
                    var match = patterns[i].exec(value);
                    if (match && match[1]) return match[1];
                  }
                  return null;
                }
                if (Array.isArray(value)) {
                  for (var a = 0; a < value.length; a += 1) {
                    var fromArray = pickCodexAccountId(value[a]);
                    if (fromArray) return fromArray;
                  }
                  return null;
                }
                if (typeof value !== "object") return null;
                if (looksLikeCodexAccountId(value.id)) return value.id;
                var directKeys = ["chatgpt_account_id","chatgptAccountId","current_account_id","currentAccountId","selected_account_id","selectedAccountId","account_id","accountId"];
                for (var d = 0; d < directKeys.length; d += 1) {
                  var direct = value[directKeys[d]];
                  if (typeof direct === "string" && direct.length > 3) return direct;
                }
                if (value.accounts) {
                  var fromAccounts = pickCodexAccountId(value.accounts);
                  if (fromAccounts) return fromAccounts;
                }
                var keys = Object.keys(value);
                for (var k = 0; k < keys.length; k += 1) {
                  if (looksLikeCodexAccountId(keys[k])) return keys[k];
                  var fromChild = pickCodexAccountId(value[keys[k]]);
                  if (fromChild) return fromChild;
                }
                return null;
              }
              function normalizeCodexPlanValue(value) {
                var normalized = String(value || "").replace(/\s+/g, " ").trim();
                if (!normalized || normalized.length >= 80 || normalized === "null") return null;
                var compact = normalized.toLowerCase().replace(/[^a-z0-9]+/g, "");
                if (compact === "prolite" || compact === "chatgptprolite" || compact === "pro5x" || compact === "chatgptpro5x") return "Pro 5x";
                if (compact === "pro" || compact === "chatgptpro20x" || compact === "pro20x") return "Pro 20x";
                if (compact === "chatgptpro") return "Pro";
                if (compact === "plus" || compact === "chatgptplus") return "Plus";
                if (compact === "free" || compact === "chatgptfree") return "Free";
                if (compact === "team" || compact === "chatgptteam") return "Team";
                if (compact === "business" || compact === "chatgptbusiness") return "Business";
                if (compact === "enterprise" || compact === "chatgptenterprise") return "Enterprise";
                if (compact === "unknown" || compact === "none" || compact === "null") return null;
                if (
                  compact.indexOf("chatgpt") >= 0 ||
                  compact.indexOf("codex") >= 0 ||
                  compact.indexOf("pro") >= 0 ||
                  compact.indexOf("plus") >= 0 ||
                  compact.indexOf("team") >= 0 ||
                  compact.indexOf("business") >= 0 ||
                  compact.indexOf("enterprise") >= 0
                ) {
                  return normalized.replace(/^ChatGPT\s+/i, "");
                }
                return null;
              }
              function pickCodexPlan(value, depth) {
                if (!value || depth > 6) return null;
                if (typeof value === "string") {
                  return normalizeCodexPlanValue(value);
                }
                if (Array.isArray(value)) {
                  for (var a = 0; a < value.length && a < 40; a += 1) {
                    var fromArray = pickCodexPlan(value[a], depth + 1);
                    if (fromArray) return fromArray;
                  }
                  return null;
                }
                if (typeof value !== "object") return null;
                var directKeys = [
                  "plan_type",
                  "planType",
                  "subscription_type",
                  "subscriptionType",
                  "subscription_name",
                  "subscriptionName",
                  "chatgpt_plan_type",
                  "chatgptPlanType",
                  "chatgpt_subscription_plan",
                  "chatgptSubscriptionPlan",
                  "plan",
                  "plan_name",
                  "planName",
                  "plan_slug",
                  "planSlug",
                  "subscription_plan",
                  "subscriptionPlan",
                  "account_plan",
                  "accountPlan",
                  "billing_plan",
                  "billingPlan",
                  "tier",
                  "sku",
                  "product_name",
                  "productName",
                  "id",
                  "slug",
                  "name",
                  "title",
                  "display_name",
                  "displayName",
                  "label"
                ];
                for (var d = 0; d < directKeys.length; d += 1) {
                  var direct = value[directKeys[d]];
                  var fromDirect = pickCodexPlan(direct, depth + 1);
                  if (fromDirect) return fromDirect;
                }
                var containers = [
                  "data",
                  "items",
                  "result",
                  "value",
                  "subscription",
                  "subscriptions",
                  "chatgpt_subscription",
                  "chatgptSubscription",
                  "active_subscription",
                  "activeSubscription",
                  "current_subscription",
                  "currentSubscription",
                  "current_plan",
                  "currentPlan",
                  "plan",
                  "plan_info",
                  "planInfo",
                  "billing",
                  "entitlement",
                  "entitlements",
                  "account",
                  "accounts",
                  "user",
                  "users",
                  "workspace",
                  "organization",
                  "product",
                  "products"
                ];
                for (var cIndex = 0; cIndex < containers.length; cIndex += 1) {
                  var nested = value[containers[cIndex]];
                  var fromNested = pickCodexPlan(nested, depth + 1);
                  if (fromNested) return fromNested;
                }
                return null;
              }
              async function fetchCodexJson(url, timeoutMs) {
                var controller = typeof AbortController !== "undefined" ? new AbortController() : null;
                var timer = null;
                var timeout = timeoutMs || 5000;
                var request = (async function() {
                  var options = {
                    credentials: "include",
                    headers: { "accept": "application/json" }
                  };
                  if (controller) {
                    options.signal = controller.signal;
                  }
                  var response = await fetch(url, options);
                  var json = null;
                  try { json = await response.json(); } catch (error) {}
                  return { ok: response.ok, status: response.status, json: json };
                })();
                var timeoutResult = new Promise(function(resolve) {
                  timer = setTimeout(function() {
                    try { if (controller) controller.abort(); } catch (error) {}
                    resolve({ ok: false, status: "timeout", json: null });
                  }, timeout);
                });
                try {
                  return await Promise.race([request, timeoutResult]);
                } finally {
                  if (timer) clearTimeout(timer);
                }
              }
              async function fetchCodexSubscriptionPlan(accountId) {
                var urls = [];
                if (accountId) {
                  urls.push("https://chatgpt.com/backend-api/subscriptions?account_id=" + encodeURIComponent(accountId));
                }
                urls.push("https://chatgpt.com/backend-api/subscriptions");
                var lastStatus = null;
                for (var i = 0; i < urls.length; i += 1) {
                  var response = await fetchCodexJson(urls[i], 3000).catch(function(error) {
                    return { ok: false, status: error && error.name === "AbortError" ? "timeout" : "error", json: null };
                  });
                  lastStatus = response.status;
                  if (response.ok) {
                    return {
                      ok: true,
                      status: response.status,
                      json: response.json,
                      plan: pickCodexPlan(response.json, 0)
                    };
                  }
                }
                return { ok: false, status: lastStatus, json: null, plan: null };
              }
              function safeCodexAuthHints(value) {
                var hints = {
                  objectPresent: false,
                  keyCount: 0,
                  accessKeyPresent: false,
                  tokenKeyPresent: false,
                  authKeyPresent: false,
                  bearerValuePresent: false,
                  nonIdTokenStringPresent: false
                };
                function visit(node, keyHint, depth) {
                  if (!node || depth > 5) return;
                  var normalizedKey = String(keyHint || "").toLowerCase();
                  var idTokenKey = normalizedKey.indexOf("id_token") >= 0 || normalizedKey.indexOf("idtoken") >= 0;
                  if (typeof node === "string") {
                    var usefulKey = normalizedKey.indexOf("access") >= 0 ||
                      normalizedKey.indexOf("token") >= 0 ||
                      normalizedKey.indexOf("auth") >= 0;
                    if (/^bearer\s+/i.test(node)) hints.bearerValuePresent = true;
                    if (!idTokenKey && usefulKey && node.length > 20) hints.nonIdTokenStringPresent = true;
                    return;
                  }
                  if (Array.isArray(node)) {
                    for (var arrayIndex = 0; arrayIndex < node.length && arrayIndex < 20; arrayIndex += 1) {
                      visit(node[arrayIndex], keyHint, depth + 1);
                    }
                    return;
                  }
                  if (typeof node !== "object") return;
                  hints.objectPresent = true;
                  var keys = Object.keys(node);
                  hints.keyCount += keys.length;
                  for (var keyIndex = 0; keyIndex < keys.length && keyIndex < 80; keyIndex += 1) {
                    var key = String(keys[keyIndex] || "");
                    var lower = key.toLowerCase();
                    if (lower.indexOf("access") >= 0) hints.accessKeyPresent = true;
                    if (lower.indexOf("token") >= 0) hints.tokenKeyPresent = true;
                    if (lower.indexOf("auth") >= 0) hints.authKeyPresent = true;
                    if (lower === "id_token" || lower === "idtoken") continue;
                    visit(node[keys[keyIndex]], key, depth + 1);
                  }
                }
                visit(value, "", 0);
                return hints;
              }
              function codexKo() {
                return String.fromCharCode.apply(String, arguments);
              }
              var KO_CODEX_HOUR = codexKo(0xC2DC, 0xAC04);
              var KO_CODEX_MINUTE = codexKo(0xBD84);
              var KO_CODEX_DAY = codexKo(0xC77C);
              var KO_CODEX_AFTER = codexKo(0xD6C4);
              var KO_CODEX_RESET = codexKo(0xCD08, 0xAE30, 0xD654);
              var KO_CODEX_REMAINING = codexKo(0xB0A8, 0xC74C);
              var KO_CODEX_REMAINING_MODIFIER = codexKo(0xB0A8, 0xC740);
              var KO_CODEX_SESSION = codexKo(0xC138, 0xC158);
              var KO_CODEX_WEEKLY = codexKo(0xC8FC, 0xAC04);
              var KO_CODEX_USAGE = codexKo(0xC0AC, 0xC6A9);
              var KO_CODEX_LIMIT = codexKo(0xD55C, 0xB3C4);
              var KO_CODEX_CREDIT = codexKo(0xD06C, 0xB808, 0xB527);
              var KO_CODEX_AM = codexKo(0xC624, 0xC804);
              var KO_CODEX_PM = codexKo(0xC624, 0xD6C4);
              function codexEscapeRegex(text) {
                return String(text || "").replace(/[.*+?^${'$'}{}()|[\]\\]/g, "\\${'$'}&");
              }
              function looksLikeCodexUsageText(text) {
                var lower = String(text || "").toLowerCase();
                return lower.indexOf("rate_limit") >= 0 ||
                  lower.indexOf("rate_limits") >= 0 ||
                  lower.indexOf("primary_window") >= 0 ||
                  lower.indexOf("secondary_window") >= 0 ||
                  lower.indexOf("spark_rate") >= 0 ||
                  lower.indexOf("code_review_rate") >= 0 ||
                  lower.indexOf("codex session") >= 0 ||
                  lower.indexOf("codex weekly") >= 0 ||
                  (lower.indexOf("codex") >= 0 && lower.indexOf('"l"') >= 0 && (
                    lower.indexOf('"u"') >= 0 ||
                    lower.indexOf("used_percent") >= 0 ||
                    lower.indexOf("usedpercent") >= 0 ||
                    lower.indexOf("remaining_percent") >= 0 ||
                    lower.indexOf("remainingpercent") >= 0 ||
                    lower.indexOf("remainingfraction") >= 0 ||
                    lower.indexOf("remaining_fraction") >= 0
                  )) ||
                  (lower.indexOf("codex") >= 0 && (
                    String(text || "").indexOf(KO_CODEX_REMAINING) >= 0 ||
                    String(text || "").indexOf(KO_CODEX_RESET) >= 0 ||
                    String(text || "").indexOf(KO_CODEX_SESSION) >= 0 ||
                    String(text || "").indexOf(KO_CODEX_WEEKLY) >= 0
                  ));
              }
              function isCodexCompactUsageLabel(label) {
                var lower = String(label || "").toLowerCase().replace(/\s+/g, " ").trim();
                if (lower.indexOf("codex") >= 0) return true;
                var compact = lower.replace(/[^a-z0-9가-힣]+/g, "");
                var sessionCompact = ("5" + KO_CODEX_HOUR + KO_CODEX_SESSION).toLowerCase().replace(/[^a-z0-9가-힣]+/g, "");
                var weeklyCompact = (KO_CODEX_WEEKLY + KO_CODEX_SESSION).toLowerCase().replace(/[^a-z0-9가-힣]+/g, "");
                return compact === sessionCompact ||
                  compact === weeklyCompact ||
                  lower === "5h session" ||
                  lower === "5-hour session" ||
                  lower === "weekly session";
              }
              function safeCodexStorageAuthHints() {
                var hints = {
                  localKeyCount: null,
                  sessionKeyCount: null,
                  localAuthKeyPresent: false,
                  sessionAuthKeyPresent: false,
                  localTokenStringPresent: false,
                  sessionTokenStringPresent: false,
                  usageLabelPresent: false
                };
                function inspectStorage(storage, prefix) {
                  var keyCountName = prefix + "KeyCount";
                  var authKeyName = prefix + "AuthKeyPresent";
                  var tokenStringName = prefix + "TokenStringPresent";
                  try {
                    hints[keyCountName] = storage.length;
                    for (var i = 0; i < storage.length && i < 120; i += 1) {
                      var key = String(storage.key(i) || "");
                      var lowerKey = key.toLowerCase();
                      if (lowerKey.indexOf("access") >= 0 || lowerKey.indexOf("token") >= 0 || lowerKey.indexOf("auth") >= 0 || lowerKey.indexOf("session") >= 0) {
                        hints[authKeyName] = true;
                      }
                      var text = String(storage.getItem(key) || "");
                      var lowerText = text.toLowerCase();
                      if (lowerText.indexOf("accesstoken") >= 0 || lowerText.indexOf("access_token") >= 0 || lowerText.indexOf("authorization") >= 0 || /^bearer\s+/i.test(text)) {
                        hints[tokenStringName] = true;
                      }
                      if (looksLikeCodexUsageText(lowerText)) hints.usageLabelPresent = true;
                    }
                  } catch (error) {}
                }
                inspectStorage(localStorage, "local");
                inspectStorage(sessionStorage, "session");
                return hints;
              }
              function shouldCaptureCodex(url, text) {
                return looksLikeCodexUsageText(String(url || "") + "\n" + String(text || ""));
              }
              function pushCodexNetworkRow(url, text) {
                if (!shouldCaptureCodex(url, text)) return;
                window.__AIQuotaCodexNetworkRows = window.__AIQuotaCodexNetworkRows || [];
                window.__AIQuotaCodexNetworkRows.push(String(url || "") + "\n" + String(text || "").slice(0, 200000));
                if (window.__AIQuotaCodexNetworkRows.length > 80) window.__AIQuotaCodexNetworkRows.shift();
              }
              function installCodexNetworkHook() {
                if (window.__AIQuotaCodexNetworkHookInstalled) return;
                window.__AIQuotaCodexNetworkHookInstalled = true;
                window.__AIQuotaCodexNetworkRows = window.__AIQuotaCodexNetworkRows || [];
                try {
                  var originalFetch = window.fetch;
                  if (originalFetch) {
                    window.fetch = function(input, init) {
                      var url = typeof input === "string" ? input : (input && input.url) || "";
                      return originalFetch.apply(this, arguments).then(function(response) {
                        try {
                          response.clone().text().then(function(text) {
                            pushCodexNetworkRow(url, text);
                          }).catch(function(){});
                        } catch (error) {}
                        return response;
                      });
                    };
                  }
                } catch (error) {}
                try {
                  var originalOpen = XMLHttpRequest.prototype.open;
                  var originalSend = XMLHttpRequest.prototype.send;
                  XMLHttpRequest.prototype.open = function(method, url) {
                    this.__aiQuotaCodexUrl = url;
                    return originalOpen.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener("load", function() {
                      try { pushCodexNetworkRow(this.__aiQuotaCodexUrl || "", this.responseText || ""); } catch (error) {}
                    });
                    return originalSend.apply(this, arguments);
                  };
                } catch (error) {}
              }
              function hasCompactCodexRows(value) {
                var rows = value && value.x;
                if (!Array.isArray(rows)) return false;
                for (var i = 0; i < rows.length; i += 1) {
                  var row = rows[i] || {};
                  if (isCodexCompactUsageLabel(row.l || row.label || "") && (
                    row.u !== undefined ||
                    row.used_percent !== undefined ||
                    row.usedPercent !== undefined ||
                    row.remaining_percent !== undefined ||
                    row.remainingPercent !== undefined ||
                    row.remainingFraction !== undefined ||
                    row.remaining_fraction !== undefined
                  )) return true;
                }
                return false;
              }
              function hasCodexUsageShape(value) {
                if (!value || typeof value !== "object") return false;
                return !!(
                  hasCompactCodexRows(value) ||
                  value.rate_limit ||
                  value.rate_limits ||
                  value.primary_window ||
                  value.secondary_window ||
                  value.code_review_rate_limit ||
                  value.spark_rate_limit ||
                  value.spark_rate_limits ||
                  (value.credits && typeof value.credits === "object" && value.credits.balance !== undefined)
                );
              }
              var codexScanBudget = 0;
              function scanCodexUsageCandidate(value, depth) {
                if (!value || depth > 7 || codexScanBudget <= 0) return null;
                codexScanBudget -= 1;
                if (hasCodexUsageShape(value)) return value;
                if (Array.isArray(value)) {
                  for (var index = 0; index < value.length && index < 80; index += 1) {
                    var fromArray = scanCodexUsageCandidate(value[index], depth + 1);
                    if (fromArray) return fromArray;
                  }
                  return null;
                }
                if (typeof value !== "object") return null;
                if (value.usage && hasCodexUsageShape(value.usage)) {
                  return {
                    account: value.account || value.email || value.account_email || null,
                    plan: value.plan || value.plan_type || null,
                    usage: value.usage
                  };
                }
                var keys = Object.keys(value);
                for (var keyIndex = 0; keyIndex < keys.length && keyIndex < 80; keyIndex += 1) {
                  var fromChild = scanCodexUsageCandidate(value[keys[keyIndex]], depth + 1);
                  if (fromChild) return fromChild;
                }
                return null;
              }
              function parseJsonCandidates(row) {
                var text = String(row || "");
                var candidates = [];
                try { candidates.push(JSON.parse(text)); } catch (error) {}
                var objectStart = text.indexOf("{");
                var objectEnd = text.lastIndexOf("}");
                if (objectStart >= 0 && objectEnd > objectStart) {
                  try { candidates.push(JSON.parse(text.slice(objectStart, objectEnd + 1))); } catch (error) {}
                }
                var arrayStart = text.indexOf("[");
                var arrayEnd = text.lastIndexOf("]");
                if (arrayStart >= 0 && arrayEnd > arrayStart) {
                  try { candidates.push(JSON.parse(text.slice(arrayStart, arrayEnd + 1))); } catch (error) {}
                }
                return candidates;
              }
              function remainingPercentFromText(text) {
                var remaining = codexEscapeRegex(KO_CODEX_REMAINING);
                var match = new RegExp("(\\d{1,3}(?:\\.\\d+)?)\\s*%\\s*(?:" + remaining + "|remaining|left)", "i").exec(text);
                if (!match) match = new RegExp("(?:" + remaining + "|remaining|left)[^\\d]{0,24}(\\d{1,3}(?:\\.\\d+)?)\\s*%", "i").exec(text);
                if (!match) return null;
                var parsed = Number(match[1]);
                if (!Number.isFinite(parsed)) return null;
                return Math.max(0, Math.min(100, parsed));
              }
              function codexMeridiemHour(meridiem, value) {
                var hour = Number(value);
                if (!Number.isFinite(hour) || hour < 1 || hour > 12) return null;
                if (meridiem === KO_CODEX_PM && hour < 12) hour += 12;
                if (meridiem === KO_CODEX_AM && hour === 12) hour = 0;
                return hour;
              }
              function codexRelativeResetText(resetDate) {
                if (!resetDate || !Number.isFinite(resetDate.getTime())) return null;
                var nowMs = Date.now ? Date.now() : new Date().getTime();
                var diffMinutes = Math.floor((resetDate.getTime() - nowMs) / 60000);
                if (!Number.isFinite(diffMinutes) || diffMinutes < 0) return null;
                var days = Math.floor(diffMinutes / 1440);
                var hours = Math.floor((diffMinutes % 1440) / 60);
                var minutes = diffMinutes % 60;
                if (days > 0) return "Resets in " + days + "d " + hours + "h";
                if (hours > 0) return "Resets in " + hours + "h " + minutes + "m";
                return "Resets in " + minutes + "m";
              }
              function normalizeCodexKoreanResetText(raw, allowTimeOnly, allowTimeOnlyTomorrow) {
                var text = String(raw || "").replace(/\s+/g, " ").trim();
                var reset = codexEscapeRegex(KO_CODEX_RESET);
                var am = codexEscapeRegex(KO_CODEX_AM);
                var pm = codexEscapeRegex(KO_CODEX_PM);
                var absolute = new RegExp("^(\\d{4})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*(" + am + "|" + pm + ")\\s*(\\d{1,2}):(\\d{2})\\s*" + reset + "$").exec(text);
                if (absolute) {
                  var absoluteHour = codexMeridiemHour(absolute[4], absolute[5]);
                  var absoluteMinute = Number(absolute[6]);
                  if (absoluteHour !== null && Number.isFinite(absoluteMinute)) {
                    return codexRelativeResetText(new Date(Number(absolute[1]), Number(absolute[2]) - 1, Number(absolute[3]), absoluteHour, absoluteMinute, 0, 0));
                  }
                }
                if (!allowTimeOnly) return null;
                var timeOnly = new RegExp("^(" + am + "|" + pm + ")\\s*(\\d{1,2}):(\\d{2})\\s*" + reset + "$").exec(text);
                if (!timeOnly) return null;
                var hour = codexMeridiemHour(timeOnly[1], timeOnly[2]);
                var minute = Number(timeOnly[3]);
                if (hour === null || !Number.isFinite(minute)) return null;
                var nowMs = Date.now ? Date.now() : new Date().getTime();
                var now = new Date(nowMs);
                var resetDate = new Date(now.getFullYear(), now.getMonth(), now.getDate(), hour, minute, 0, 0);
                if (resetDate.getTime() <= nowMs) {
                  if (!allowTimeOnlyTomorrow) return null;
                  resetDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, hour, minute, 0, 0);
                }
                return codexRelativeResetText(resetDate);
              }
              function resetTextFromCodexWindow(text, allowTimeOnly, allowTimeOnlyTomorrow) {
                var day = codexEscapeRegex(KO_CODEX_DAY);
                var hour = codexEscapeRegex(KO_CODEX_HOUR);
                var minute = codexEscapeRegex(KO_CODEX_MINUTE);
                var after = codexEscapeRegex(KO_CODEX_AFTER);
                var reset = codexEscapeRegex(KO_CODEX_RESET);
                var am = codexEscapeRegex(KO_CODEX_AM);
                var pm = codexEscapeRegex(KO_CODEX_PM);
                var koreanReset = "(?:\\d+\\s*" + day + "\\s*)?(?:\\d+\\s*" + hour + "\\s*)?(?:\\d+\\s*" + minute + "\\s*)?" + after + "\\s*" + reset;
                var koreanAbsoluteClockReset = "\\d{4}\\.\\s*\\d{1,2}\\.\\s*\\d{1,2}\\.\\s*(?:" + am + "|" + pm + ")\\s*\\d{1,2}:\\d{2}\\s*" + reset;
                var koreanClockReset = "(?:(?:" + am + "|" + pm + ")\\s*)?\\d{1,2}:\\d{2}\\s*" + reset;
                var resetAlternates = [koreanAbsoluteClockReset, koreanReset];
                if (allowTimeOnly) resetAlternates.push(koreanClockReset);
                resetAlternates.push("resets?[^.\\n]{0,80}");
                resetAlternates.push("reset[^.\\n]{0,80}");
                var match = new RegExp("(" + resetAlternates.join("|") + ")", "i").exec(text);
                if (!match) return null;
                var rawResetText = match[1].replace(/\s+/g, " ").trim();
                var normalizedResetText = normalizeCodexKoreanResetText(rawResetText, !!allowTimeOnly, !!allowTimeOnlyTomorrow);
                if (normalizedResetText) return normalizedResetText;
                if (new RegExp("^(" + am + "|" + pm + ")\\s*\\d{1,2}:\\d{2}\\s*" + reset + "$").test(rawResetText)) return null;
                return rawResetText;
              }
              function codexPercentNumber(value) {
                if (value === null || value === undefined || value === "") return null;
                var parsed = Number(value);
                if (!Number.isFinite(parsed)) return null;
                return Math.max(0, Math.min(100, parsed >= 0 && parsed <= 1 ? parsed * 100 : parsed));
              }
              function codexDomLines(text) {
                return String(text || "").split(/\n+/).map(function(line) {
                  return String(line || "").replace(/\s+/g, " ").trim();
                }).filter(function(line) { return line.length > 0; });
              }
              function codexLineHas5Hour(line) {
                return line.indexOf("5" + KO_CODEX_HOUR) >= 0 || /5\s*(?:h|hour)/i.test(line);
              }
              function codexLineHasWeekly(line) {
                return line.indexOf(KO_CODEX_WEEKLY) >= 0 || /weekly/i.test(line);
              }
              function codexLineHasUsageLimit(line) {
                var lower = line.toLowerCase();
                return (line.indexOf(KO_CODEX_USAGE) >= 0 && line.indexOf(KO_CODEX_LIMIT) >= 0) ||
                  lower.indexOf("usage limit") >= 0 ||
                  lower.indexOf("limit") >= 0;
              }
              function codexLineHasSessionLabel(line) {
                var lower = line.toLowerCase();
                return line.indexOf(KO_CODEX_SESSION) >= 0 ||
                  lower.indexOf("session") >= 0;
              }
              function codexVisibleTitleMatches(line, type) {
                var lower = line.toLowerCase();
                var isSpark = lower.indexOf("spark") >= 0;
                if (!codexLineHasUsageLimit(line) && !codexLineHasSessionLabel(line)) return false;
                if (type === "primary") return !isSpark && codexLineHas5Hour(line);
                if (type === "secondary") return !isSpark && codexLineHasWeekly(line);
                if (type === "spark_primary") return isSpark && codexLineHas5Hour(line);
                if (type === "spark_secondary") return isSpark && codexLineHasWeekly(line);
                return false;
              }
              function codexVisibleWindowText(lines, startIndex) {
                var end = Math.min(lines.length, startIndex + 8);
                var types = ["primary", "secondary", "spark_primary", "spark_secondary"];
                for (var i = startIndex + 1; i < end; i += 1) {
                  for (var j = 0; j < types.length; j += 1) {
                    if (codexVisibleTitleMatches(lines[i], types[j])) {
                      end = i;
                      break;
                    }
                  }
                  if (end === i) break;
                }
                return lines.slice(startIndex, end).join("\n");
              }
              function codexVisibleSparkLabel(line, fallbackLabel) {
                var label = String(line || "").replace(/\s+/g, " ").trim();
                label = label.replace(new RegExp("\\s*" + codexEscapeRegex(KO_CODEX_USAGE) + "\\s*" + codexEscapeRegex(KO_CODEX_LIMIT), "g"), "");
                label = label.replace(new RegExp("5\\s*" + codexEscapeRegex(KO_CODEX_HOUR), "g"), "5h");
                label = label.replace(new RegExp(codexEscapeRegex(KO_CODEX_WEEKLY), "g"), "Weekly");
                label = label.replace(/\s+/g, " ").trim();
                return label || fallbackLabel;
              }
              function codexVisibleUsageLine(lines, type, fallbackLabel) {
                for (var i = 0; i < lines.length; i += 1) {
                  if (!codexVisibleTitleMatches(lines[i], type)) continue;
                  var windowText = codexVisibleWindowText(lines, i);
                  var remainingPercent = remainingPercentFromText(windowText);
                  if (remainingPercent === null) continue;
                  var usedPercent = Math.max(0, Math.min(100, Number((100 - remainingPercent).toFixed(3))));
                  var label = type.indexOf("spark_") === 0 ? codexVisibleSparkLabel(lines[i], fallbackLabel) : fallbackLabel;
                  var line = {
                    label: label,
                    remaining_percent: remainingPercent,
                    used_percent: usedPercent
                  };
                  var allowTimeOnlyReset = type === "primary" || type === "spark_primary";
                  var resetText = resetTextFromCodexWindow(windowText, allowTimeOnlyReset, false);
                  if (!resetText && (type === "secondary" || type === "spark_secondary")) {
                    resetText = resetTextFromCodexWindow(windowText, true, true);
                  }
                  if (resetText) line.reset_text = resetText;
                  return line;
                }
                return null;
              }
              function inheritCodexVisibleResetText(line, fallbackLine) {
                if (!line || line.reset_text || !fallbackLine || !fallbackLine.reset_text) return line;
                line.reset_text = fallbackLine.reset_text;
                return line;
              }
              function codexVisibleCredits(lines) {
                for (var i = 0; i < lines.length; i += 1) {
                  var line = lines[i];
                  var lower = line.toLowerCase();
                  var isCreditLine = ((line.indexOf(KO_CODEX_REMAINING) >= 0 || line.indexOf(KO_CODEX_REMAINING_MODIFIER) >= 0) && line.indexOf(KO_CODEX_CREDIT) >= 0) ||
                    lower.indexOf("remaining credits") >= 0 ||
                    lower.indexOf("credits remaining") >= 0;
                  if (!isCreditLine) continue;
                  for (var j = i; j < lines.length && j < i + 5; j += 1) {
                    var match = /-?\d+(?:\.\d+)?/.exec(lines[j]);
                    if (match) return { remaining: Number(match[0]) };
                  }
                }
                return null;
              }
              function extractCodexVisibleDomUsage(accountId) {
                var text = "";
                try { text = c.text(); } catch (error) {}
                var lower = text.toLowerCase();
                if (!text || (lower.indexOf("codex") < 0 && !isCodexUsageDashboardLocation())) return null;
                if (text.indexOf(KO_CODEX_REMAINING) < 0 && lower.indexOf("remaining") < 0 && lower.indexOf("left") < 0) return null;
                var lines = codexDomLines(text);
                var limits = {};
                var primary = codexVisibleUsageLine(lines, "primary", "Codex Session");
                if (primary) limits.primary_window = primary;
                var secondary = codexVisibleUsageLine(lines, "secondary", "Codex Weekly");
                if (secondary) limits.secondary_window = secondary;
                var sparkPrimary = codexVisibleUsageLine(lines, "spark_primary", "Codex Spark 5h");
                sparkPrimary = inheritCodexVisibleResetText(sparkPrimary, primary);
                if (sparkPrimary) limits.spark_primary_window = sparkPrimary;
                var sparkSecondary = codexVisibleUsageLine(lines, "spark_secondary", "Codex Spark Weekly");
                sparkSecondary = inheritCodexVisibleResetText(sparkSecondary, secondary);
                if (sparkSecondary) limits.spark_secondary_window = sparkSecondary;
                if (Object.keys(limits).length === 0) return null;
                var payload = {
                  provider: "codex",
                  source: "visible-dom",
                  account: accountId || null,
                  usage: { rate_limits: limits },
                  collectorMode: "webview-js-visible-dom"
                };
                var credits = codexVisibleCredits(lines);
                if (credits) payload.usage.credits = credits;
                return payload;
              }
              function codexTextLine(text, labels, fallbackLabel, allowTimeOnly, allowTimeOnlyTomorrow) {
                var lower = text.toLowerCase();
                for (var i = 0; i < labels.length; i += 1) {
                  var label = labels[i];
                  var index = lower.indexOf(label.toLowerCase());
                  if (index < 0) continue;
                  var windowText = text.slice(index, index + 500);
                  var remainingPercent = remainingPercentFromText(windowText);
                  if (remainingPercent === null) continue;
                  return {
                    l: fallbackLabel,
                    u: Math.max(0, Math.min(1, 1 - remainingPercent / 100)),
                    remaining_percent: remainingPercent,
                    t: resetTextFromCodexWindow(windowText, allowTimeOnly, allowTimeOnlyTomorrow)
                  };
                }
                return null;
              }
              function scanCodexUsageText(row, accountId) {
                var text = String(row || "");
                if (text.toLowerCase().indexOf("codex") < 0) return null;
                var rows = [];
                var sessionLine = codexTextLine(
                  text,
                  ["Codex 5" + KO_CODEX_HOUR + " " + KO_CODEX_SESSION, "5" + KO_CODEX_HOUR + " " + KO_CODEX_SESSION, "Codex Session", "Codex 5h Session", "5h Session", "5-hour Session", "Codex " + KO_CODEX_SESSION],
                  "Codex Session",
                  true,
                  false
                );
                if (sessionLine) rows.push(sessionLine);
                var weeklyLine = codexTextLine(
                  text,
                  ["Codex " + KO_CODEX_WEEKLY + " " + KO_CODEX_SESSION, KO_CODEX_WEEKLY + " " + KO_CODEX_SESSION, "Codex Weekly", "Codex Weekly Session", "Weekly Session"],
                  "Codex Weekly",
                  true,
                  true
                );
                if (weeklyLine) rows.push(weeklyLine);
                if (rows.length === 0) return null;
                return {
                  provider: "codex",
                  account: accountId || null,
                  usage: { x: rows },
                  collectorMode: "webview-js-state"
                };
              }
              function extractCodexUsageFromRows(accountId) {
                var rows = window.__AIQuotaCodexNetworkRows || [];
                for (var rowIndex = 0; rowIndex < rows.length; rowIndex += 1) {
                  if (!looksLikeCodexUsageText(rows[rowIndex])) continue;
                  var textPayload = scanCodexUsageText(rows[rowIndex], accountId);
                  if (textPayload) return textPayload;
                  var candidates = parseJsonCandidates(rows[rowIndex]);
                  for (var candidateIndex = 0; candidateIndex < candidates.length; candidateIndex += 1) {
                    var parsed = candidates[candidateIndex];
                    codexScanBudget = 1200;
                    var found = scanCodexUsageCandidate(parsed, 0);
                    if (found) {
                      found.provider = "codex";
                      found.account = found.account || accountId || pickCodexAccountId(parsed);
                      found.collectorMode = "webview-js-state";
                      return found;
                    }
                  }
                }
                return null;
              }
              function summarizeCodexRows() {
                var rows = (window.__AIQuotaCodexNetworkRows || []).concat(c.rows ? c.rows() : []);
                var candidates = [];
                var documentText = "";
                try { documentText = c.text(); } catch (error) {}
                for (var i = 0; i < rows.length && candidates.length < 8; i += 1) {
                  var text = String(rows[i] || "");
                  if (!looksLikeCodexUsageText(text)) continue;
                  var lower = text.toLowerCase();
                  var jsonCandidates = parseJsonCandidates(text);
                  var compactCandidate = false;
                  for (var j = 0; j < jsonCandidates.length; j += 1) {
                    if (hasCodexUsageShape(jsonCandidates[j])) compactCandidate = true;
                  }
                  candidates.push({
                    index: i,
                    length: text.length,
                    hasRateLimit: lower.indexOf("rate_limit") >= 0 || lower.indexOf("rate_limits") >= 0,
                    hasPrimaryWindow: lower.indexOf("primary_window") >= 0,
                    hasSecondaryWindow: lower.indexOf("secondary_window") >= 0,
                    hasCodex: lower.indexOf("codex") >= 0,
                    hasRemainingText: lower.indexOf("remaining") >= 0 || lower.indexOf("left") >= 0 || text.indexOf(KO_CODEX_REMAINING) >= 0,
                    hasResetText: lower.indexOf("reset") >= 0 || text.indexOf(KO_CODEX_RESET) >= 0,
                    hasPercent: text.indexOf("%") >= 0,
                    jsonCandidateCount: jsonCandidates.length,
                    compactCandidate: compactCandidate
                  });
                }
                return {
                  totalRows: rows.length,
                  networkRows: (window.__AIQuotaCodexNetworkRows || []).length,
                  documentTextLength: documentText.length,
                  documentHasCodex: documentText.toLowerCase().indexOf("codex") >= 0,
                  documentHasRemainingText: documentText.toLowerCase().indexOf("remaining") >= 0 || documentText.toLowerCase().indexOf("left") >= 0 || documentText.indexOf(KO_CODEX_REMAINING) >= 0,
                  candidates: candidates
                };
              }
              function scanCodexPageState(accountId) {
                var globals = [];
                try { globals.push(window.__NEXT_DATA__); } catch (error) {}
                try { globals.push(window.__remixContext); } catch (error) {}
                try { globals.push(window.__reactRouterContext); } catch (error) {}
                try { globals.push(window.__APP_DATA__); } catch (error) {}
                try { globals.push(window.__INITIAL_STATE__); } catch (error) {}
                try {
                  Object.keys(window).slice(0, 800).forEach(function(key) {
                    var lower = String(key || "").toLowerCase();
                    if (lower.indexOf("codex") >= 0 || lower.indexOf("usage") >= 0 || lower.indexOf("quota") >= 0) {
                      try { globals.push(window[key]); } catch (error) {}
                    }
                  });
                } catch (error) {}
                return extractCodexVisibleDomUsage(accountId);
              }
              function buildCodexDiagnostics(result) {
                result = result || {};
                return {
                  sessionStatus: result.sessionStatus || null,
                  meStatus: result.meStatus || null,
                  accountCheckStatus: result.accountCheckStatus || null,
                  codexUsageStatus: result.codexUsageStatus || null,
                  codexUsageStatuses: result.codexUsageStatuses || [],
                  whamStatus: null,
                  whamCookieOnlyStatus: null,
                  whamQueryStatus: null,
                  whamSkippedNoToken: false,
                  sessionOk: !!result.sessionOk,
                  accountOk: !!result.accountOk,
                  usageOk: !!result.usageOk,
                  tokenPresent: false,
                  accountIdPresent: !!result.accountId,
                  observedAccountIdPresent: !!c.observedAccountId,
                  sessionAuthHint: result.sessionAuthHint || {},
                  meAuthHint: result.meAuthHint || {},
                  accountCheckAuthHint: result.accountCheckAuthHint || {},
                  storageAuthHint: result.storageAuthHint || {},
                  rowSummary: summarizeCodexRows(),
                  rowsUsagePresent: !!result.rowsUsagePresent
                };
              }
              async function probeCodexSession() {
                installCodexNetworkHook();
                var result = {
                  loggedIn: false,
                  sessionOk: false,
                  usageOk: false,
                  accountOk: false,
                  usage: null,
                  account: null,
                  accountCheck: null,
                  accountId: null,
                  plan: null,
                  subscriptionStatus: null,
                  codexUsageStatus: null,
                  codexUsageStatuses: []
                };
                result.storageAuthHint = safeCodexStorageAuthHints();
                var probeResults = await Promise.all([
                  fetchCodexJson("https://chatgpt.com/api/auth/session", 5000).catch(function(error) {
                    return { ok: false, status: error && error.name === "AbortError" ? "timeout" : "error", json: null };
                  }),
                  fetchCodexJson("https://chatgpt.com/backend-api/me", 5000).catch(function(error) {
                    return { ok: false, status: error && error.name === "AbortError" ? "timeout" : "error", json: null };
                  }),
                  fetchCodexJson("https://chatgpt.com/backend-api/accounts/check/v4-2023-04-27", 5000).catch(function(error) {
                    return { ok: false, status: error && error.name === "AbortError" ? "timeout" : "error", json: null };
                  })
                ]);
                var sessionRes = probeResults[0];
                result.sessionStatus = sessionRes.status;
                result.sessionAuthHint = safeCodexAuthHints(sessionRes.json);
                if (sessionRes.ok) {
                  result.loggedIn = true;
                  result.sessionOk = true;
                  result.plan = result.plan || pickCodexPlan(sessionRes.json, 0);
                }
                var meRes = probeResults[1];
                result.meStatus = meRes.status;
                result.meAuthHint = safeCodexAuthHints(meRes.json);
                if (meRes.ok) {
                  result.loggedIn = true;
                  result.sessionOk = true;
                  result.accountOk = true;
                  result.account = meRes.json;
                  result.plan = result.plan || pickCodexPlan(meRes.json, 0);
                }
                var checkRes = probeResults[2];
                result.accountCheckStatus = checkRes.status;
                result.accountCheckAuthHint = safeCodexAuthHints(checkRes.json);
                if (checkRes.ok) {
                  result.loggedIn = true;
                  result.sessionOk = true;
                  result.accountOk = true;
                  result.accountCheck = checkRes.json;
                  result.plan = result.plan || pickCodexPlan(checkRes.json, 0);
                }
                result.accountId =
                  pickCodexAccountId(result.account) ||
                  pickCodexAccountId(result.accountCheck) ||
                  pickCodexAccountId(c.rows()) ||
                  c.observedAccountId;
                result.usage = scanCodexPageState(result.accountId);
                result.rowsUsagePresent = !!result.usage;
                if (result.usage) {
                  result.plan = result.plan || result.usage.plan || result.usage.plan_type || null;
                  if (!result.plan) {
                    var subscriptionRes = await fetchCodexSubscriptionPlan(result.accountId);
                    result.subscriptionStatus = subscriptionRes.status;
                    if (subscriptionRes.ok) {
                      result.loggedIn = true;
                      result.sessionOk = true;
                      result.plan = subscriptionRes.plan || null;
                    }
                  }
                  if (result.plan && !result.usage.plan && !result.usage.plan_type) result.usage.plan = result.plan;
                  result.loggedIn = true;
                  result.sessionOk = true;
                  result.usageOk = true;
                }
                return result;
              }
              function looksLikeChatGptApp() {
                try {
                  var text = c.text().toLowerCase();
                  if (location.pathname.indexOf("/auth/login") >= 0) return false;
                  return !!(
                    document.querySelector("textarea, [contenteditable='true'], form button") ||
                    text.indexOf("message chatgpt") >= 0 ||
                    text.indexOf("chatgpt") >= 0
                  );
                } catch (error) {
                  return false;
                }
              }
              function looksLikeChatGptLogin() {
                try {
                  var text = c.text().toLowerCase();
                  return (text.indexOf("로그인") >= 0 && (
                      text.indexOf("회원가입") >= 0 ||
                      text.indexOf("회원 가입") >= 0 ||
                      text.indexOf("지금 무슨 생각") >= 0
                    )) ||
                    text.indexOf("google 계정으로 계속하기") >= 0 ||
                    text.indexOf("log in or sign up") >= 0 ||
                    (text.indexOf("log in") >= 0 && text.indexOf("sign up") >= 0) ||
                    text.indexOf("continue with google") >= 0;
                } catch (error) {
                  return false;
                }
              }
              function isCodexUsageDashboardLocation() {
                var path = "";
                try { path = location.pathname || ""; } catch (error) {}
                return path.indexOf("/codex/") >= 0 &&
                  (path.indexOf("/settings/analytics") >= 0 || path.indexOf("/settings/usage") >= 0);
              }
              function navigateCodexUsageDashboardIfNeeded(result) {
                if (!result || !result.sessionOk || result.usageOk) return false;
                if (isCodexUsageDashboardLocation()) return false;
                var target = codexUsageDashboardUrls[0];
                console.log("AIQuotaCodex navigate usage dashboard");
                try { location.assign(target); } catch (error) { location.href = target; }
                return true;
              }
              function continueCodexInteractiveLoginUntilUsagePayload(reason) {
                if (!c.awaitInteractiveLoginUsage) return false;
                console.log("AIQuotaCodex awaiting usage payload reason=" + reason + " attempt=" + attempts);
                setTimeout(runProbe, codexProbeRetryDelayMs);
                return true;
              }
              function runProbe() {
                attempts += 1;
                probeCodexSession().then(function(result) {
                  console.log("AIQuotaCodex probe attempt=" + attempts +
                    " session=" + result.sessionStatus +
                    " me=" + result.meStatus +
                    " account=" + result.accountCheckStatus +
                    " subscription=" + result.subscriptionStatus +
                    " plan=" + !!result.plan +
                    " usage=" + result.usageOk +
                    " rows=" + ((window.__AIQuotaCodexNetworkRows || []).length));
                  if (result.loggedIn && result.usageOk && result.usage && typeof result.usage === "object") {
                    result.usage.provider = "codex";
                    c.post(result.usage);
                    return;
                  }
                  if (navigateCodexUsageDashboardIfNeeded(result)) {
                    return;
                  }
                  if (attempts >= 2 && looksLikeChatGptLogin()) {
                    if (continueCodexInteractiveLoginUntilUsagePayload("login")) return;
                    c.fail("codex_auth_required", "Codex login page reached instead of a trusted usage payload.");
                    return;
                  }
                  if (attempts >= 8 && (result.sessionOk || looksLikeChatGptApp())) {
                    if (continueCodexInteractiveLoginUntilUsagePayload("usage_unavailable")) return;
                    c.fail("codex_usage_unavailable", "Codex session reached, but trusted usage payload was not available. diagnostics=" + JSON.stringify(buildCodexDiagnostics(result)));
                    return;
                  }
                  setTimeout(runProbe, codexProbeRetryDelayMs);
                }).catch(function() {
                  setTimeout(runProbe, codexProbeRetryDelayMs);
                });
              }
              installCodexNetworkHook();
              setTimeout(runProbe, codexInitialProbeDelayMs);
            })();
        """.trimIndent()
    }

    internal fun gemini(asset: String): String {
        val collectorAsset = asset.ifBlank {
            "window.SAGE_USAGE_EXTRACTOR={buildGeminiUsagePayload:function(){return null;}};"
        }
        return """
            (function(){
              if (!window.__AIQuotaStartProviderCollector ||
                !window.__AIQuotaStartProviderCollector("gemini", window.__AIQuotaCollector && window.__AIQuotaCollector.awaitInteractiveLoginUsage)
              ) return;
              function safeText(value) { return value === null || value === undefined ? "" : String(value); }
              function parseNumber(value) { var n = Number(value); return Number.isFinite(n) ? n : null; }
              function isNumber(value) { return typeof value === "number" && Number.isFinite(value); }
              function extractJsonCandidates(value) {
                var text = safeText(value);
                var candidates = [];
                var objectStart = text.indexOf("{");
                var objectEnd = text.lastIndexOf("}");
                if (objectStart >= 0 && objectEnd > objectStart) candidates.push(text.slice(objectStart, objectEnd + 1));
                var arrayStart = text.indexOf("[");
                var arrayEnd = text.lastIndexOf("]");
                if (arrayStart >= 0 && arrayEnd > arrayStart) candidates.push(text.slice(arrayStart, arrayEnd + 1));
                return candidates;
              }
              function scanGeminiQuotaResponse(value, path, source, limits) {
                if (!value || limits.length > 40) return;
                if (Array.isArray(value)) {
                  value.forEach(function(item, index) { scanGeminiQuotaResponse(item, path.concat(String(index)), source, limits); });
                  return;
                }
                if (typeof value !== "object") return;
                var label = value.l || value.label || value.name || value.title || value.modelId || value.model_id || value.model || value.feature;
                var usedRateValue = parseNumber(value.u !== undefined ? value.u : (value.usageRate !== undefined ? value.usageRate : (value.usedRate !== undefined ? value.usedRate : value.utilization)));
                    var usedPercentValue = parseNumber(value.usedPercent !== undefined ? value.usedPercent : (value.used_percent !== undefined ? value.used_percent : (value.usedPercentage !== undefined ? value.usedPercentage : (value.used_percentage !== undefined ? value.used_percentage : (value.percentUsed !== undefined ? value.percentUsed : (value.percent_used !== undefined ? value.percent_used : (value.totalPercentUsed !== undefined ? value.totalPercentUsed : value.total_percent_used)))))));
                var remainingFractionValue = parseNumber(value.remainingFraction !== undefined ? value.remainingFraction : value.remaining_fraction);
                var remainingPercentValue = parseNumber(value.remainingPercent !== undefined ? value.remainingPercent : (value.remaining_percent !== undefined ? value.remaining_percent : (value.remainingPercentage !== undefined ? value.remainingPercentage : value.remaining_percentage)));
                if (label && (usedRateValue !== null || usedPercentValue !== null || remainingFractionValue !== null || remainingPercentValue !== null)) {
                  var line = {
                    l: label,
                    r: value.r || value.resetAt || value.resets_at,
                    t: value.t || value.resetText || value.reset_text,
                    source: source,
                    confidence: value.confidence
                  };
                  if (usedPercentValue !== null) {
                    line.used_percent = Math.max(0, Math.min(100, usedPercentValue));
                  } else if (usedRateValue !== null) {
                    line.u = Math.max(0, Math.min(1, usedRateValue <= 1 ? usedRateValue : usedRateValue / 100));
                  } else if (remainingPercentValue !== null) {
                    line.remaining_percent = Math.max(0, Math.min(100, remainingPercentValue));
                  } else {
                    line.remaining_fraction = Math.max(0, Math.min(1, remainingFractionValue <= 1 ? remainingFractionValue : remainingFractionValue / 100));
                  }
                  limits.push(line);
                }
                Object.keys(value).forEach(function(key) {
                  scanGeminiQuotaResponse(value[key], path.concat(key), source, limits);
                });
              }
              function pushGeminiNetworkRow(url, text) {
                if (!text) return;
                var lower = text.toLowerCase();
                if (
                  lower.indexOf("remainingfraction") < 0 &&
                  lower.indexOf("modelid") < 0 &&
                  lower.indexOf("retrieveuserquota") < 0 &&
                  lower.indexOf("loadcodeassist") < 0 &&
                  lower.indexOf("gemini") < 0
                ) return;
                window.__AIQuotaGeminiNetworkRows = window.__AIQuotaGeminiNetworkRows || [];
                window.__AIQuotaGeminiNetworkRows.push((url || "") + "\n" + text.slice(0, 200000));
                if (window.__AIQuotaGeminiNetworkRows.length > 30) window.__AIQuotaGeminiNetworkRows.shift();
              }
              function installGeminiNetworkHook() {
                if (window.__AIQuotaGeminiNetworkHookInstalled) return;
                window.__AIQuotaGeminiNetworkHookInstalled = true;
                window.__AIQuotaGeminiNetworkRows = window.__AIQuotaGeminiNetworkRows || [];
                try {
                  var originalFetch = window.fetch;
                  if (originalFetch) {
                    window.fetch = function(input, init) {
                      var url = typeof input === "string" ? input : (input && input.url) || "";
                      return originalFetch.apply(this, arguments).then(function(response) {
                        try {
                          response.clone().text().then(function(text) {
                            pushGeminiNetworkRow(url, text);
                          }).catch(function(){});
                        } catch (error) {}
                        return response;
                      });
                    };
                  }
                } catch (error) {}
                try {
                  var originalOpen = XMLHttpRequest.prototype.open;
                  var originalSend = XMLHttpRequest.prototype.send;
                  XMLHttpRequest.prototype.open = function(method, url) {
                    this.__aiQuotaUrl = url;
                    return originalOpen.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener("load", function() {
                      try { pushGeminiNetworkRow(this.__aiQuotaUrl || "", this.responseText || ""); } catch (error) {}
                    });
                    return originalSend.apply(this, arguments);
                  };
                } catch (error) {}
              }
              function clickGeminiSetupSkip() {
                var labels = ["skip", "not now", "건너뛰기", "나중에"];
                var elements = Array.prototype.slice.call(document.querySelectorAll("button, [role='button'], a"));
                for (var i = 0; i < elements.length; i += 1) {
                  var text = safeText(elements[i].innerText || elements[i].textContent).trim().toLowerCase();
                  if (!text) continue;
                  for (var j = 0; j < labels.length; j += 1) {
                    if (text.indexOf(labels[j]) >= 0) {
                      elements[i].click();
                      return true;
                    }
                  }
                }
                return false;
              }
              function isGeminiUsagePageUrl(value) {
                var text = safeText(value);
                return /^https?:\/\/([^\/]+\.)?gemini\.google\.com\/usage(?:[/?#]|${'$'})/i.test(text);
              }
              function looksLikeGeminiLoginText(value) {
                var text = safeText(value).toLowerCase();
                if (!text) return false;
                if (
                  text.indexOf("usage limit") >= 0 ||
                  text.indexOf("current usage") >= 0 ||
                  text.indexOf("5-hour") >= 0 ||
                  text.indexOf("weekly") >= 0 ||
                  text.indexOf("사용량") >= 0 ||
                  text.indexOf("한도") >= 0
                ) return false;
                var hasLoginAction = text.indexOf("로그인") >= 0 ||
                  text.indexOf("sign in") >= 0 ||
                  text.indexOf("log in") >= 0;
                var hasGeminiLandingCopy = text.indexOf("gemini와의 대화") >= 0 ||
                  text.indexOf("개인 ai") >= 0 ||
                  text.indexOf("meet gemini") >= 0 ||
                  text.indexOf("personal ai") >= 0;
                return hasLoginAction && hasGeminiLandingCopy;
              }
              function clickGeminiSignIn() {
                window.__AIQuotaGeminiSignInClicks = window.__AIQuotaGeminiSignInClicks || 0;
                if (window.__AIQuotaGeminiSignInClicks >= 2) return false;
                var labels = ["로그인", "sign in", "log in"];
                var elements = Array.prototype.slice.call(document.querySelectorAll("a, button, [role='button']"));
                for (var i = 0; i < elements.length; i += 1) {
                  var text = safeText(elements[i].innerText || elements[i].textContent).trim().toLowerCase();
                  var href = safeText(elements[i].href || elements[i].getAttribute && elements[i].getAttribute("href") || "");
                  if (!text && !href) continue;
                  for (var j = 0; j < labels.length; j += 1) {
                    if (text.indexOf(labels[j]) >= 0 || href.indexOf("accounts.google.com/ServiceLogin") >= 0) {
                      window.__AIQuotaGeminiSignInClicks += 1;
                      if (href.indexOf("accounts.google.com/ServiceLogin") >= 0) {
                        location.href = href;
                      } else {
                        elements[i].click();
                      }
                      return true;
                    }
                  }
                }
                return false;
              }
              window.PROVIDER_ID = "gemini";
              $collectorAsset
              var c = window.__AIQuotaCollector;
              if (!c) return;
              installGeminiNetworkHook();
              var skipAttempts = 0;
              var collectAttempts = 0;
              function postGeminiObservedPayload() {
                try {
                  var rows = c.rows().concat(window.__AIQuotaGeminiNetworkRows || []);
                  var visibleText = c.text ? c.text() : (c.pageText || "");
                  var limits = [];
                  if (looksLikeGeminiLoginText(visibleText)) {
                    if (c.awaitInteractiveLoginUsage && clickGeminiSignIn()) {
                      return true;
                    }
                    c.fail("gemini_login_required", "Gemini login is required.");
                    return true;
                  }
                  rows.forEach(function(row) {
                    extractJsonCandidates(row).forEach(function(candidate) {
                      try { scanGeminiQuotaResponse(JSON.parse(candidate), ["root"], "page-state", limits); } catch (error) {}
                    });
                  });
                  var payload = window.SAGE_USAGE_EXTRACTOR &&
                    window.SAGE_USAGE_EXTRACTOR.buildGeminiUsagePayload &&
                    window.SAGE_USAGE_EXTRACTOR.buildGeminiUsagePayload({
                      rows: rows,
                      limits: limits,
                      pageText: visibleText,
                      combinedText: [visibleText, rows.join("\n")].join("\n"),
                      usagePage: isGeminiUsagePageUrl(location && location.href) || isGeminiUsagePageUrl(c.pageUrl),
                      authenticatedApp: location.hostname === "gemini.google.com" &&
                        (location.pathname.indexOf("/app") === 0 || location.pathname.indexOf("/usage") === 0),
                      providerPage: true
                    });
                  if (payload && payload.usage && payload.usage.x && payload.usage.x.length > 0) {
                    c.post(payload);
                    return true;
                  }
                } catch (error) {
                  c.fail("gemini_collector_error", String(error && error.message || error));
                  return true;
                }
                return false;
              }
              function finishGeminiNoObservedPayload() {
                if (collectAttempts < 6) {
                  setTimeout(collectGeminiUsage, 5000);
                } else {
                  c.fail("gemini_no_trusted_payload", "Gemini usage payload was not available.");
                }
              }
              function collectGeminiUsage() {
                collectAttempts += 1;
                if (skipAttempts < 3 && clickGeminiSetupSkip()) {
                  skipAttempts += 1;
                  setTimeout(collectGeminiUsage, 2200);
                  return;
                }
                if (postGeminiObservedPayload()) return;
                finishGeminiNoObservedPayload();
              }
              setTimeout(collectGeminiUsage, 1800);
            })();
        """.trimIndent()
    }

    internal fun glm(): String {
        return """
            (function(){
              if (!window.__AIQuotaStartProviderCollector || !window.__AIQuotaStartProviderCollector("glm")) return;
              var c = window.__AIQuotaCollector;
              if (!c) return;
              var glmNoSubscriptionText = "you don't have any subscription";
              function hasGlmNoSubscriptionText(value) {
                return String(value || "").toLowerCase().indexOf(glmNoSubscriptionText) >= 0;
              }
              function number(value) {
                if (value === null || value === undefined || value === "") return null;
                var parsed = Number(value);
                return Number.isFinite(parsed) ? parsed : null;
              }
              function first(object, keys) {
                if (!object || typeof object !== "object") return null;
                for (var i = 0; i < keys.length; i += 1) {
                  var value = object[keys[i]];
                  if (value !== null && value !== undefined && value !== "") return value;
                }
                return null;
              }
              function normalizePlan(value) {
                var text = String(value || "").toLowerCase();
                if (text.indexOf("lite") >= 0) return "Lite";
                if (text.indexOf("pro") >= 0) return "Pro";
                if (text.indexOf("max") >= 0) return "Max";
                return null;
              }
              function percentFromObject(object) {
                var direct = number(first(object, ["percentage", "usedPercent", "used_percent", "percentUsed", "percent_used"]));
                if (direct !== null) return direct;
                var utilization = number(first(object, ["utilization", "u"]));
                if (utilization !== null) return utilization >= 0 && utilization <= 1 ? utilization * 100 : utilization;
                var remainingPercent = number(first(object, ["remainingPercent", "remaining_percent", "percentRemaining", "percent_remaining"]));
                if (remainingPercent !== null) return 100 - remainingPercent;
                var remainingFraction = number(first(object, ["remainingFraction", "remaining_fraction"]));
                if (remainingFraction !== null) return 100 - (remainingFraction * 100);
                return null;
              }
              function inferLimitType(object, fallbackLabel) {
                var raw = String(first(object, ["type", "limitType", "quotaType", "key", "name", "label", "title"]) || fallbackLabel || "");
                var normalized = raw.toLowerCase();
                if (normalized.indexOf("token") >= 0 ||
                    normalized.indexOf("5-hour") >= 0 ||
                    normalized.indexOf("5 hour") >= 0 ||
                    normalized.indexOf("5h") >= 0 ||
                    normalized.indexOf("weekly") >= 0 ||
                    normalized.indexOf("7-day") >= 0 ||
                    normalized.indexOf("7 day") >= 0) {
                  return "TOKENS_LIMIT";
                }
                if (normalized.indexOf("time_limit") >= 0 ||
                    normalized.indexOf("mcp") >= 0 ||
                    normalized.indexOf("search") >= 0 ||
                    normalized.indexOf("reader") >= 0 ||
                    normalized.indexOf("zread") >= 0 ||
                    normalized.indexOf("tool") >= 0) {
                  return "TIME_LIMIT";
                }
                return null;
              }
              function isWeeklyLimit(object, fallbackLabel) {
                var raw = String(first(object, ["type", "limitType", "quotaType", "key", "name", "label", "title"]) || fallbackLabel || "").toLowerCase();
                var unit = number(first(object, ["unit"]));
                var period = number(first(object, ["number", "period", "window"]));
                return raw.indexOf("weekly") >= 0 ||
                  raw.indexOf("7-day") >= 0 ||
                  raw.indexOf("7 day") >= 0 ||
                  unit === 6 ||
                  period === 7;
              }
              function appendLimit(payload, object, fallbackLabel) {
                if (!object || typeof object !== "object") return;
                var type = inferLimitType(object, fallbackLabel);
                if (!type) return;
                var percentage = percentFromObject(object);
                var usage = number(first(object, ["usage", "limit", "total", "totalValue", "max", "quota"]));
                var currentValue = number(first(object, ["currentValue", "current_value", "used", "usedValue", "consumed", "value"]));
                var remaining = number(first(object, ["remaining", "left", "remainingValue"]));
                if (usage === null && currentValue !== null && remaining !== null) usage = currentValue + remaining;
                if (currentValue === null && usage !== null && remaining !== null) currentValue = usage - remaining;
                if (percentage !== null && usage === null) usage = 100;
                if (percentage !== null && currentValue === null) currentValue = percentage;
                if (usage === null || currentValue === null || usage <= 0) return;
                payload.data = payload.data || {};
                payload.data.limits = payload.data.limits || [];
                var output = {
                  type: type,
                  usage: usage,
                  currentValue: currentValue,
                  percentage: percentage !== null ? percentage : (currentValue / usage) * 100
                };
                if (remaining !== null) output.remaining = remaining;
                var reset = first(object, ["nextResetTime", "next_reset_time", "resetTime", "resetAt", "reset_at", "resetsAt", "resets_at"]);
                if (reset !== null && reset !== undefined && reset !== "") output.nextResetTime = reset;
                if (type === "TOKENS_LIMIT") {
                  if (isWeeklyLimit(object, fallbackLabel)) {
                    output.unit = 6;
                    output.number = 7;
                  } else {
                    output.unit = 3;
                    output.number = 5;
                  }
                } else {
                  output.unit = number(first(object, ["unit"])) || 5;
                  output.number = number(first(object, ["number"])) || 1;
                  var details = object.usageDetails || object.usage_details || object.details;
                  if (Array.isArray(details)) output.usageDetails = details;
                }
                payload.data.limits.push(output);
              }
              function rememberPlan(object, payload) {
                payload.plan = payload.plan || normalizePlan(
                  first(object, ["productName", "product_name", "planName", "plan_name", "plan", "packageName", "package_name", "tier"])
                );
                payload.account = payload.account || first(object, ["email", "account", "userEmail"]);
              }
              function scan(object, payload, depth) {
                if (!object || typeof object !== "object" || depth > 6) return;
                rememberPlan(object, payload);
                if (Array.isArray(object)) {
                  object.forEach(function(item) { scan(item, payload, depth + 1); });
                  return;
                }
                if (Array.isArray(object.limits)) {
                  object.limits.forEach(function(item) { appendLimit(payload, item); });
                }
                appendLimit(payload, object);
                Object.keys(object).forEach(function(key) {
                  var value = object[key];
                  if (value && typeof value === "object") scan(value, payload, depth + 1);
                });
              }
              function scanJsonText(text, payload) {
                var raw = String(text || "");
                var parsed = null;
                try { parsed = JSON.parse(raw); } catch (error) {}
                if (!parsed) {
                  var start = raw.indexOf("{");
                  var end = raw.lastIndexOf("}");
                  if (start >= 0 && end > start) {
                    try { parsed = JSON.parse(raw.slice(start, end + 1)); } catch (error) {}
                  }
                }
                if (parsed) scan(parsed, payload, 0);
              }
              function pushGlmNetworkRow(url, text) {
                var value = String(url || "") + "\n" + String(text || "");
                var lower = value.toLowerCase();
                if (lower.indexOf("tokens_limit") < 0 &&
                    lower.indexOf("time_limit") < 0 &&
                    lower.indexOf("/api/monitor/usage") < 0 &&
                    lower.indexOf("/api/biz/subscription") < 0 &&
                    !hasGlmNoSubscriptionText(lower)) {
                  return;
                }
                window.__AIQuotaGlmNetworkRows = window.__AIQuotaGlmNetworkRows || [];
                window.__AIQuotaGlmNetworkRows.push(value.slice(0, 200000));
                if (window.__AIQuotaGlmNetworkRows.length > 50) window.__AIQuotaGlmNetworkRows.shift();
              }
              function installNetworkHook() {
                if (window.__AIQuotaGlmNetworkHookInstalled) return;
                window.__AIQuotaGlmNetworkHookInstalled = true;
                window.__AIQuotaGlmNetworkRows = window.__AIQuotaGlmNetworkRows || [];
                try {
                  var originalFetch = window.fetch;
                  if (originalFetch) {
                    window.fetch = function(input, init) {
                      var url = typeof input === "string" ? input : (input && input.url) || "";
                      return originalFetch.apply(this, arguments).then(function(response) {
                        try {
                          response.clone().text().then(function(text) {
                            pushGlmNetworkRow(url, text);
                          }).catch(function(){});
                        } catch (error) {}
                        return response;
                      });
                    };
                  }
                } catch (error) {}
                try {
                  var originalOpen = XMLHttpRequest.prototype.open;
                  var originalSend = XMLHttpRequest.prototype.send;
                  XMLHttpRequest.prototype.open = function(method, url) {
                    this.__aiQuotaGlmUrl = url;
                    return originalOpen.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener("load", function() {
                      try { pushGlmNetworkRow(this.__aiQuotaGlmUrl || "", this.responseText || ""); } catch (error) {}
                    });
                    return originalSend.apply(this, arguments);
                  };
                } catch (error) {}
              }
              function scanRows(payload) {
                var rows = (window.__AIQuotaGlmNetworkRows || []).concat(c.rows ? c.rows() : []);
                rows.forEach(function(row) { scanJsonText(row, payload); });
              }
              function hasGlmNoSubscription() {
                if (hasGlmNoSubscriptionText(c.text ? c.text() : "")) return true;
                var rows = (window.__AIQuotaGlmNetworkRows || []).concat(c.rows ? c.rows() : []);
                for (var i = 0; i < rows.length; i += 1) {
                  if (hasGlmNoSubscriptionText(rows[i])) return true;
                }
                try {
                  var scripts = document && document.scripts ? document.scripts : [];
                  for (var j = 0; j < scripts.length; j += 1) {
                    if (hasGlmNoSubscriptionText(scripts[j].textContent || "")) return true;
                  }
                } catch (error) {}
                return false;
              }
              function scanPageState(payload) {
                var candidates = [];
                try { candidates.push(window.__NEXT_DATA__); } catch (error) {}
                try { candidates.push(window.__INITIAL_STATE__); } catch (error) {}
                try { candidates.push(window.__APP_DATA__); } catch (error) {}
                try { candidates.push(window.__remixContext); } catch (error) {}
                try {
                  Object.keys(window).slice(0, 1000).forEach(function(key) {
                    var lower = String(key || "").toLowerCase();
                    if (lower.indexOf("glm") >= 0 ||
                        lower.indexOf("zai") >= 0 ||
                        lower.indexOf("usage") >= 0 ||
                        lower.indexOf("quota") >= 0 ||
                        lower.indexOf("plan") >= 0 ||
                        lower.indexOf("subscription") >= 0) {
                      try { candidates.push(window[key]); } catch (error) {}
                    }
                  });
                } catch (error) {}
                candidates.forEach(function(candidate) { scan(candidate, payload, 0); });
              }
              function appendVisibleTextLimit(payload, label, usedPercent, isWeekly, isTool) {
                appendLimit(payload, {
                  type: isTool ? "TIME_LIMIT" : "TOKENS_LIMIT",
                  label: label,
                  usage: 100,
                  currentValue: usedPercent,
                  percentage: usedPercent,
                  unit: isTool ? 5 : (isWeekly ? 6 : 3),
                  number: isTool ? 1 : (isWeekly ? 7 : 5)
                }, label);
              }
              function scanVisibleText(payload) {
                var text = c.text ? c.text() : "";
                var lower = text.toLowerCase();
                payload.plan = payload.plan || normalizePlan(text);
                var percentRe = /(\d{1,3}(?:\.\d+)?)\s*%/g;
                var match;
                while ((match = percentRe.exec(text)) !== null) {
                  var percent = Number(match[1]);
                  if (!Number.isFinite(percent) || percent < 0 || percent > 100) continue;
                  var start = Math.max(0, match.index - 180);
                  var end = Math.min(text.length, match.index + 180);
                  var context = text.slice(start, end).toLowerCase();
                  var isRemaining = context.indexOf("remaining") >= 0 || context.indexOf("left") >= 0 || context.indexOf("남음") >= 0;
                  var usedPercent = isRemaining ? 100 - percent : percent;
                  if (context.indexOf("weekly") >= 0 || context.indexOf("7-day") >= 0 || context.indexOf("7 day") >= 0) {
                    appendVisibleTextLimit(payload, "주간 한도", usedPercent, true, false);
                  } else if (context.indexOf("5-hour") >= 0 || context.indexOf("5 hour") >= 0 || context.indexOf("5h") >= 0 || context.indexOf("token") >= 0) {
                    appendVisibleTextLimit(payload, "5시간 한도", usedPercent, false, false);
                  } else if (context.indexOf("mcp") >= 0 || context.indexOf("search") >= 0 || context.indexOf("reader") >= 0 || context.indexOf("zread") >= 0) {
                    appendVisibleTextLimit(payload, "월간 한도", usedPercent, false, true);
                  }
                }
              }
              function dedupeLimits(payload) {
                var limits = payload && payload.data && payload.data.limits;
                if (!Array.isArray(limits)) return;
                var seen = {};
                payload.data.limits = limits.filter(function(item) {
                  var type = String(item.type || "");
                  var key = type + ":" + String(item.unit || "") + ":" + String(item.number || "");
                  if (seen[key]) return false;
                  seen[key] = true;
                  return true;
                });
              }
              function hasTrustedPayload(payload) {
                var limits = payload && payload.data && payload.data.limits;
                return Array.isArray(limits) && limits.length > 0;
              }
              function collect(attempt) {
                var payload = { provider: "glm", source: "visible-dom" };
                scanPageState(payload);
                scanRows(payload);
                scanVisibleText(payload);
                dedupeLimits(payload);
                if (hasTrustedPayload(payload)) {
                  c.post(payload);
                  return;
                }
                if (hasGlmNoSubscription()) {
                  c.fail("glm_no_subscription", "You don't have any subscription");
                  return;
                }
                if (attempt < 8) {
                  setTimeout(function(){ collect(attempt + 1); }, 1500);
                } else {
                  c.fail("glm_no_trusted_payload", "GLM Web OAuth usage payload was not available.");
                }
              }
              installNetworkHook();
              setTimeout(function(){ collect(0); }, 800);
            })();
        """.trimIndent()
    }

    internal fun opencode(): String {
        return """
            (function(){
              if (!window.__AIQuotaStartProviderCollector || !window.__AIQuotaStartProviderCollector("opencode")) return;
              var c = window.__AIQuotaCollector;
              if (!c) return;
              window.__AIQuotaOpenCodeRows = window.__AIQuotaOpenCodeRows || [];
              function lower(value) {
                return String(value || "").toLowerCase();
              }
              function isRelevantUrl(url) {
                var value = lower(url);
                if (value.indexOf("opencode.ai") < 0) return false;
                if (value.indexOf("/docs") >= 0 || value.indexOf("/brand") >= 0) return false;
                return value.indexOf("/auth") >= 0 ||
                  value.indexOf("usage") >= 0 ||
                  value.indexOf("billing") >= 0 ||
                  value.indexOf("credit") >= 0 ||
                  value.indexOf("balance") >= 0 ||
                  value.indexOf("subscription") >= 0 ||
                  value.indexOf("console") >= 0 ||
                  value.indexOf("/zen") >= 0 ||
                  value.indexOf("/go") >= 0;
              }
              function pushNetworkRow(url, text) {
                if (!isRelevantUrl(url) || !text) return;
                window.__AIQuotaOpenCodeRows.push(String(text).slice(0, 250000));
                if (window.__AIQuotaOpenCodeRows.length > 30) window.__AIQuotaOpenCodeRows.shift();
              }
              function installNetworkHook() {
                try {
                  var originalFetch = window.fetch;
                  if (originalFetch) {
                    window.fetch = function(input, init) {
                      var url = typeof input === "string" ? input : (input && input.url) || "";
                      return originalFetch.apply(this, arguments).then(function(response) {
                        try {
                          response.clone().text().then(function(text) {
                            pushNetworkRow(url, text);
                          }).catch(function(){});
                        } catch (error) {}
                        return response;
                      });
                    };
                  }
                } catch (error) {}
                try {
                  var originalOpen = XMLHttpRequest.prototype.open;
                  var originalSend = XMLHttpRequest.prototype.send;
                  XMLHttpRequest.prototype.open = function(method, url) {
                    this.__aiQuotaOpenCodeUrl = url;
                    return originalOpen.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener("load", function() {
                      try { pushNetworkRow(this.__aiQuotaOpenCodeUrl || "", this.responseText || ""); } catch (error) {}
                    });
                    return originalSend.apply(this, arguments);
                  };
                } catch (error) {}
              }
              function number(value) {
                if (value === null || value === undefined || value === "") return null;
                if (typeof value === "number" && Number.isFinite(value)) return value;
                var cleaned = String(value).replace(/,/g, "").trim();
                var match = /-?\d+(?:\.\d+)?/.exec(cleaned);
                if (!match) return null;
                var parsed = Number(match[0]);
                return Number.isFinite(parsed) ? parsed : null;
              }
              function displayLimitLabel(value) {
                var raw = String(value || "").trim();
                var text = lower(raw);
                if (((text.indexOf("5 hour") >= 0 || text.indexOf("5-hour") >= 0 || text.indexOf("5h") >= 0) && text.indexOf("limit") >= 0) ||
                    (text.indexOf("rolling") >= 0 && text.indexOf("usage") >= 0) ||
                    (raw.indexOf("롤링") >= 0 && raw.indexOf("사용량") >= 0)) {
                  return "Go 5 hour limit";
                }
                if ((text.indexOf("weekly") >= 0 && (text.indexOf("limit") >= 0 || text.indexOf("usage") >= 0)) ||
                    (raw.indexOf("주간") >= 0 && raw.indexOf("사용량") >= 0)) return "Go weekly limit";
                if ((text.indexOf("monthly") >= 0 && (text.indexOf("limit") >= 0 || text.indexOf("usage") >= 0)) ||
                    (raw.indexOf("월간") >= 0 && raw.indexOf("사용량") >= 0)) return "Go monthly limit";
                if (text.indexOf("balance") >= 0 || text.indexOf("credit") >= 0) return "Zen Credits";
                return raw || null;
              }
              function lineKey(label) {
                return lower(label).replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
              }
              function appendLimit(payload, item, fallbackLabel) {
                if (!item) return;
                var rawLabel = item.label || item.name || item.title || fallbackLabel;
                var label = displayLimitLabel(rawLabel);
                if (!label) return;
                var line = c.line ? c.line(item, label) : null;
                if (!line) {
                  var used = number(item.used || item.usage || item.currentValue || item.current_value);
                  var remaining = number(item.remaining || item.remainingAmount || item.remaining_amount);
                  var limit = number(item.limit || item.total || item.limitAmount || item.limit_amount);
                  if (limit !== null && limit > 0 && (used !== null || remaining !== null)) {
                    line = { label: label };
                    if (remaining !== null) line.remaining_percent = (remaining / limit) * 100;
                    else line.used_percent = (used / limit) * 100;
                  }
                }
                if (!line) return;
                line.label = label;
                line.key = line.key || "opencode:" + lineKey(label);
                var usedAmount = number(item.used || item.usage || item.currentValue || item.current_value);
                var remainingAmount = number(item.remaining || item.remainingAmount || item.remaining_amount);
                var limitAmount = number(item.limit || item.total || item.limitAmount || item.limit_amount);
                if (usedAmount !== null) line.used = usedAmount;
                if (remainingAmount !== null) line.remaining = remainingAmount;
                if (limitAmount !== null) line.limit = limitAmount;
                line.unit = item.unit || item.currency || "usd";
                payload.data.limits.push(line);
              }
              function appendCredits(payload, balance) {
                var amount = number(balance);
                if (amount === null) return;
                if (!payload.data.credits || amount > number(payload.data.credits.balance)) {
                  payload.data.credits = { balance: amount };
                }
              }
              function extractDomLine(label, chunk) {
                var text = chunk.join(" ");
                var remainingMatch = /(\d{1,3}(?:\.\d+)?)\s*%\s*(?:remaining|left|available|남음)/i.exec(text);
                var usedMatch = /(\d{1,3}(?:\.\d+)?)\s*%\s*(?:used|사용)/i.exec(text);
                var goUsageMatch = /(\d{1,3}(?:\.\d+)?)\s*%/.exec(text);
                var amountMatch = /(?:USD\s*)?\$?\s*(\d+(?:\.\d+)?)\s*(?:\/|of)\s*(?:USD\s*)?\$?\s*(\d+(?:\.\d+)?)/i.exec(text);
                var line = { label: label, unit: "usd" };
                if (remainingMatch) {
                  line.remaining_percent = number(remainingMatch[1]);
                } else if (usedMatch) {
                  line.used_percent = number(usedMatch[1]);
                } else if (goUsageMatch && lower(label).indexOf("go ") >= 0) {
                  line.used_percent = number(goUsageMatch[1]);
                } else if (amountMatch) {
                  var first = number(amountMatch[1]);
                  var limit = number(amountMatch[2]);
                  if (first !== null && limit !== null && limit > 0) {
                    line.limit = limit;
                    if (lower(text).indexOf("remaining") >= 0 || lower(text).indexOf("left") >= 0) {
                      line.remaining = first;
                      line.remaining_percent = (first / limit) * 100;
                    } else {
                      line.used = first;
                      line.used_percent = (first / limit) * 100;
                    }
                  }
                }
                var resetMatch = /(resets?\s+in\s+[0-9a-zA-Z\s]+|reset[s]?\s+[a-zA-Z0-9:\s]+)/i.exec(text);
                if (resetMatch) line.reset_text = resetMatch[1].trim();
                var koreanResetMatch = /초기화까지\s*남은\s*시간\s*:?\s*((?:\d+\s*(?:일|시간|분)\s*)+)/.exec(text);
                if (!line.reset_text && koreanResetMatch) line.reset_text = koreanResetText(koreanResetMatch[1]);
                if (line.remaining_percent === undefined && line.used_percent === undefined) return null;
                return line;
              }
              function koreanResetText(value) {
                var parts = [];
                var match;
                var regex = /(\d+)\s*(일|시간|분)/g;
                while ((match = regex.exec(String(value || ""))) !== null) {
                  var amount = number(match[1]);
                  if (amount === null) continue;
                  if (match[2] === "일") parts.push(amount + "d");
                  if (match[2] === "시간") parts.push(amount + "h");
                  if (match[2] === "분") parts.push(amount + "m");
                }
                return parts.length > 0 ? "Resets in " + parts.join(" ") : null;
              }
              function scanVisibleText(payload) {
                var text = c.text ? c.text() : "";
                if (!text) return;
                if (!payload.data.plan && lower(text).indexOf("opencode go") >= 0) payload.data.plan = "Go";
                var lines = text.split(/\n+/).map(function(line) {
                  return String(line || "").trim();
                }).filter(Boolean);
                for (var i = 0; i < lines.length; i += 1) {
                  var current = lines[i];
                  var label = displayLimitLabel(current);
                  var labelText = lower(label || "");
                  if (label && labelText.indexOf("go ") >= 0 && labelText.indexOf("limit") >= 0) {
                    var limitLine = extractDomLine(label, lines.slice(i, Math.min(lines.length, i + 6)));
                    appendLimit(payload, limitLine, label);
                  }
                  var currentText = lower(current);
                  if (currentText.indexOf("balance") >= 0 || currentText.indexOf("credit") >= 0) {
                    var chunk = lines.slice(i, Math.min(lines.length, i + 4)).join(" ");
                    var money = /(?:USD\s*)?\$\s*(\d+(?:\.\d+)?)/i.exec(chunk);
                    var credit = /(\d+(?:\.\d+)?)\s*(?:credits?|balance)/i.exec(chunk);
                    appendCredits(payload, money ? money[1] : (credit ? credit[1] : null));
                  }
                }
              }
              function scanObject(value, payload, hint, depth) {
                if (!value || depth > 7) return;
                if (Array.isArray(value)) {
                  value.forEach(function(item) { scanObject(item, payload, hint, depth + 1); });
                  return;
                }
                if (typeof value !== "object") return;
                var label = value.label || value.name || value.title || value.displayName || value.display_name || hint;
                var hintText = lower(String(hint || "") + " " + String(label || ""));
                if (hintText.indexOf("limit") >= 0 || hintText.indexOf("usage") >= 0 || hintText.indexOf("quota") >= 0 || hintText.indexOf("go") >= 0) {
                  appendLimit(payload, value, label);
                }
                if (hintText.indexOf("credit") >= 0 || hintText.indexOf("balance") >= 0 || hintText.indexOf("zen") >= 0) {
                  var balance = value.balance || value.creditBalance || value.credit_balance || value.remainingCredits || value.remaining_credits || value.credits;
                  appendCredits(payload, balance);
                }
                Object.keys(value).forEach(function(key) {
                  var child = value[key];
                  if (key === "access_token" || key === "id_token" || key === "refresh_token" || key === "cookie") return;
                  scanObject(child, payload, key, depth + 1);
                });
              }
              function scanRows(payload) {
                var rows = (window.__AIQuotaOpenCodeRows || []).concat(c.rows ? c.rows() : []);
                rows.forEach(function(row) {
                  if (!row) return;
                  var text = String(row);
                  if (lower(text).indexOf("opencode") < 0 &&
                      lower(text).indexOf("usage") < 0 &&
                      lower(text).indexOf("limit") < 0 &&
                      lower(text).indexOf("credit") < 0 &&
                      lower(text).indexOf("balance") < 0) return;
                  try { scanObject(JSON.parse(text), payload, "", 0); } catch (error) {}
                });
              }
              function scanPageState(payload) {
                var candidates = [];
                try { candidates.push(window.__NEXT_DATA__); } catch (error) {}
                try { candidates.push(window.__INITIAL_STATE__); } catch (error) {}
                try { candidates.push(window.__APP_DATA__); } catch (error) {}
                try { candidates.push(window.__remixContext); } catch (error) {}
                candidates.forEach(function(candidate) { scanObject(candidate, payload, "", 0); });
              }
              function dedupe(payload) {
                var seen = {};
                payload.data.limits = payload.data.limits.filter(function(item) {
                  var key = item.key || item.label;
                  if (!key || seen[key]) return false;
                  seen[key] = true;
                  return true;
                });
              }
              function buildPayload() {
                var payload = { provider: "opencode", source: "visible-dom", data: { limits: [] } };
                scanPageState(payload);
                scanRows(payload);
                scanVisibleText(payload);
                dedupe(payload);
                if (payload.data.plan) payload.plan = payload.data.plan;
                if (payload.data.account) payload.account = payload.data.account;
                if (payload.data.limits.length === 0 && !payload.data.credits) return null;
                return payload;
              }
              function collect(attempt) {
                var payload = buildPayload();
                if (payload) {
                  c.post(payload);
                  return;
                }
                if (attempt < 8) {
                  setTimeout(function(){ collect(attempt + 1); }, 1500);
                } else {
                  c.fail("opencode_no_trusted_payload", "OpenCode usage payload was not available.");
                }
              }
              installNetworkHook();
              setTimeout(function(){ collect(0); }, 800);
            })();
        """.trimIndent()
    }

    internal fun copilot(): String {
        return """
            (function(){
              if (!window.__AIQuotaStartProviderCollector || !window.__AIQuotaStartProviderCollector("copilot")) return;
              var c = window.__AIQuotaCollector;
              if (!c) return;
              try { console.log("AIQuotaCopilot collector_start href=" + String(location && location.href || "")); } catch (logError) {}
              function first() {
                for (var i = 0; i < arguments.length; i += 1) {
                  var value = arguments[i];
                  if (value !== null && value !== undefined && value !== "") return value;
                }
                return null;
              }
              function nativeJson(url, authorizationHeader) {
                function nativeBridgeJson() {
                  try {
                    if (window.AIQuotaCollectorBridge && window.AIQuotaCollectorBridge.fetchCopilotJson) {
                      return JSON.parse(
                        authorizationHeader && window.AIQuotaCollectorBridge.fetchCopilotJsonWithAuthorization
                          ? window.AIQuotaCollectorBridge.fetchCopilotJsonWithAuthorization(url, authorizationHeader)
                          : window.AIQuotaCollectorBridge.fetchCopilotJson(url)
                      );
                    }
                  } catch (error) {
                    return { ok: false, url: url, error: String(error && error.message || error) };
                  }
                  return { ok: false, url: url, error: "native_bridge_unavailable" };
                }
                if (authorizationHeader) return Promise.resolve(nativeBridgeJson());
                return c.fetchJson(url).then(function(webResult) {
                  if (webResult && webResult.ok) return webResult;
                  var nativeResult = nativeBridgeJson();
                  return nativeResult && nativeResult.ok ? nativeResult : webResult;
                }).catch(function(error) {
                  var nativeResult = nativeBridgeJson();
                  return nativeResult && nativeResult.ok
                    ? nativeResult
                    : { ok: false, url: url, error: String(error && error.message || error) };
                });
              }
              function githubMeta(name) {
                var node = document.querySelector("meta[name='" + name + "']");
                return node && node.content ? node.content : "";
              }
              function githubVerifiedHeaders() {
                var headers = {
                  "Accept": "application/json",
                  "Content-Type": "application/json",
                  "GitHub-Verified-Fetch": "true",
                  "X-Requested-With": "XMLHttpRequest"
                };
                var nonce = githubMeta("fetch-nonce");
                if (nonce) headers["X-Fetch-Nonce"] = nonce;
                var release = githubMeta("release");
                if (release) headers["X-GitHub-Client-Version"] = release;
                return headers;
              }
              function githubPostJson(url) {
                return fetch(url, {
                  method: "POST",
                  credentials: "same-origin",
                  cache: "no-cache",
                  headers: githubVerifiedHeaders()
                }).then(function(response) {
                  return response.text().then(function(text) {
                    var parsed = {};
                    try { parsed = text ? JSON.parse(text) : {}; } catch (error) { parsed = { rawText: text || "" }; }
                    return { ok: response.ok, status: response.status, url: url, json: parsed };
                  });
                }).catch(function(error) {
                  return { ok: false, url: url, error: String(error && error.message || error) };
                });
              }
              function quotaSnapshots(raw) {
                return raw && (raw.quota_snapshots || raw.quotaSnapshots);
              }
              function copilotAuthorizationHeader(tokenRaw) {
                if (!tokenRaw || typeof tokenRaw !== "object") return null;
                var direct = first(tokenRaw.authorizationHeaderValue, tokenRaw.authorization_header_value, tokenRaw.authorizationHeader);
                if (direct) return direct;
                var token = first(tokenRaw.token, tokenRaw.value, tokenRaw.accessToken, tokenRaw.access_token);
                return token ? ("GitHub-Bearer " + token) : null;
              }
              function copilotUsageFromToken(tokenRaw) {
                if (!tokenRaw || typeof tokenRaw !== "object") return null;
                var usage = {};
                [
                  "limited_user_quotas",
                  "limitedUserQuotas",
                  "monthly_quotas",
                  "monthlyQuotas",
                  "quota_snapshots",
                  "quotaSnapshots",
                  "quota_reset_date",
                  "quota_reset_date_utc",
                  "limited_user_reset_date",
                  "copilot_plan",
                  "plan",
                  "sku"
                ].forEach(function(key) {
                  if (tokenRaw[key] !== undefined && tokenRaw[key] !== null) usage[key] = tokenRaw[key];
                });
                return Object.keys(usage).length > 0 ? usage : null;
              }
              function githubApiAuthorizationHeader(value) {
                if (!value || typeof value !== "string") return null;
                var trimmed = value.trim();
                return trimmed.indexOf("Bearer ") === 0 || trimmed.indexOf("token ") === 0 ? trimmed : null;
              }
              function githubApiAuthorizationHeaderFromToken(tokenRaw) {
                if (!tokenRaw || typeof tokenRaw !== "object") return null;
                var direct = first(
                  tokenRaw.apiAuthorizationHeader,
                  tokenRaw.api_authorization_header,
                  tokenRaw.githubAuthorizationHeader,
                  tokenRaw.github_authorization_header,
                  tokenRaw.authorizationHeaderValue,
                  tokenRaw.authorization_header_value,
                  tokenRaw.authorizationHeader
                );
                var directApi = githubApiAuthorizationHeader(direct);
                if (directApi) return directApi;
                var token = first(tokenRaw.github_token, tokenRaw.githubToken, tokenRaw.accessToken, tokenRaw.access_token, tokenRaw.token, tokenRaw.value);
                return token ? ("token " + token) : null;
              }
              function quotaSummary(raw) {
                var value = raw && (raw.json || raw);
                var envelope = quotaEnvelope(value, 0);
                var limited = envelope && (envelope.limited_user_quotas || envelope.limitedUserQuotas);
                var monthly = envelope && (envelope.monthly_quotas || envelope.monthlyQuotas);
                var snapshots = quotaSnapshots(envelope || value);
                return "limited=" + !!limited +
                  " monthly=" + !!monthly +
                  " completions=" + !!(limited && limited.completions !== undefined && monthly && monthly.completions !== undefined) +
                  " snapshots=" + !!snapshots;
              }
              function rawTextMarkerSummary(raw) {
                var value = raw && (raw.json || raw);
                var text = value && value.rawText ? String(value.rawText) : "";
                return "rawLen=" + text.length +
                  " limitedIdx=" + text.indexOf("limited_user_quotas") +
                  " monthlyIdx=" + text.indexOf("monthly_quotas") +
                  " completionsIdx=" + text.indexOf("completions") +
                  " internalIdx=" + text.indexOf("copilot_internal/user");
              }
              function hasLabel(text, labels) {
                var lower = String(text || "").toLowerCase();
                for (var i = 0; i < labels.length; i += 1) {
                  if (lower.indexOf(String(labels[i]).toLowerCase()) >= 0) return true;
                }
                return false;
              }
              function percentAfterLabel(text, labels) {
                var lower = String(text || "").toLowerCase();
                for (var i = 0; i < labels.length; i += 1) {
                  var index = lower.indexOf(String(labels[i]).toLowerCase());
                  if (index < 0) continue;
                  var segment = String(text || "").slice(index, index + 180);
                  var match = segment.match(/([0-9]+(?:\.[0-9]+)?)\s*%/);
                  if (match) {
                    var value = Number(match[1]);
                    var segmentLower = segment.toLowerCase();
                    if (segmentLower.indexOf("left") >= 0 ||
                        segmentLower.indexOf("remaining") >= 0 ||
                        segment.indexOf("\ub0a8\uc74c") >= 0) {
                      return Math.max(0, Math.min(100, 100 - value));
                    }
                    return Math.max(0, Math.min(100, value));
                  }
                }
                return null;
              }
              function featureResetText(text) {
                var lines = String(text || "").split(/\n+/);
                for (var i = 0; i < lines.length; i += 1) {
                  var line = lines[i].trim();
                  var lower = line.toLowerCase();
                  if (line.length > 0 && line.length < 220 &&
                      (lower.indexOf("reset") >= 0 || line.indexOf("\ucd08\uae30\ud654") >= 0)) {
                    return line;
                  }
                }
                return null;
              }
              function featuresPageUsageFromText(text) {
                var completionLabels = [
                  "code completion",
                  "code autocomplete",
                  "inline suggestions",
                  "\ucf54\ub4dc \uc790\ub3d9 \uc644\uc131"
                ];
                var chatLabels = [
                  "chat messages",
                  "chat message",
                  "\ucc44\ud305 \uba54\uc2dc\uc9c0"
                ];
                var completion = percentAfterLabel(text, completionLabels);
                var chat = percentAfterLabel(text, chatLabels);
                var seenCompletion = hasLabel(text, completionLabels);
                var seenChat = hasLabel(text, chatLabels);
                return {
                  seen: seenCompletion || seenChat,
                  complete: completion !== null && chat !== null,
                  completionPercent: completion,
                  chatPercent: chat,
                  plan: featuresPlanFromText(text),
                  resetText: featureResetText(text)
                };
              }
              function featuresPlanFromText(text) {
                var value = String(text || "");
                var lower = value.toLowerCase();
                var index = lower.indexOf("github copilot");
                if (index < 0) return null;
                var segment = value.slice(index, index + 220).toLowerCase();
                if (segment.indexOf("무료") >= 0 || segment.indexOf("free") >= 0) return "free";
                if (segment.indexOf("enterprise") >= 0) return "enterprise";
                if (segment.indexOf("business") >= 0) return "business";
                if (segment.indexOf("pro") >= 0) return "pro";
                return null;
              }
              function buildFeaturesPayload(featureUsage) {
                var quotas = {};
                if (featureUsage.completionPercent !== null) {
                  quotas.completions = { used_percent: featureUsage.completionPercent };
                }
                if (featureUsage.chatPercent !== null) {
                  quotas.chat = { used_percent: featureUsage.chatPercent };
                }
                var resetDate = first(featureUsage.resetDate, featureUsage.resetText);
                if (resetDate) quotas.quota_reset_date = resetDate;
                return {
                  provider: "copilot",
                  collectorMode: "webview-features-page",
                  plan: featureUsage.plan,
                  quotas: quotas
                };
              }
              function mergeFeaturesMetadata(featurePayload, metadataPayload) {
                var metadataQuotas = metadataPayload && metadataPayload.quotas || {};
                var resetDate = first(
                  metadataQuotas.quota_reset_date,
                  metadataQuotas.resetDate,
                  metadataPayload && metadataPayload.quota_reset_date,
                  metadataPayload && metadataPayload.resetDate
                );
                if (resetDate) featurePayload.quotas.quota_reset_date = resetDate;
                featurePayload.plan = first(featurePayload.plan, metadataPayload && metadataPayload.plan);
                featurePayload.account = first(featurePayload.account, metadataPayload && metadataPayload.account);
                return featurePayload;
              }
              function currentFeaturesPageUsage() {
                var usage = mergeFeatureUsage(featuresPageUsageFromText(c.text()), scanCopilotPageState());
                try {
                  console.log("AIQuotaCopilot features_dom seen=" + !!(usage && usage.seen) +
                    " complete=" + !!(usage && usage.complete) +
                    " completion=" + (usage && usage.completionPercent !== null) +
                    " chat=" + (usage && usage.chatPercent !== null) +
                    " plan=" + !!(usage && usage.plan) +
                    " reset=" + !!(usage && usage.resetText));
                } catch (logError) {}
                return usage;
              }
              function clampPercent(value) {
                var parsed = Number(value);
                return Number.isFinite(parsed) ? Math.max(0, Math.min(100, parsed)) : null;
              }
              function clampFractionPercent(value) {
                var parsed = Number(value);
                return Number.isFinite(parsed) ? Math.max(0, Math.min(100, parsed <= 1 ? parsed * 100 : parsed)) : null;
              }
              function numberFrom(value, keys) {
                if (!value || typeof value !== "object") return null;
                for (var i = 0; i < keys.length; i += 1) {
                  var candidate = value[keys[i]];
                  if (candidate !== null && candidate !== undefined && candidate !== "") {
                    var parsed = Number(candidate);
                    if (Number.isFinite(parsed)) return parsed;
                  }
                }
                return null;
              }
              function copilotKindFromText(text) {
                var lower = String(text || "").toLowerCase();
                if (lower.indexOf("chat") >= 0 ||
                    lower.indexOf("message") >= 0 ||
                    lower.indexOf("\ucc44\ud305") >= 0) {
                  return "chat";
                }
                if (lower.indexOf("completion") >= 0 ||
                    lower.indexOf("autocomplete") >= 0 ||
                    lower.indexOf("inline") >= 0 ||
                    lower.indexOf("suggestion") >= 0 ||
                    lower.indexOf("\ucf54\ub4dc") >= 0 ||
                    lower.indexOf("\uc790\ub3d9") >= 0 ||
                    lower.indexOf("\uc778\ub77c\uc778") >= 0) {
                  return "completions";
                }
                return null;
              }
              function normalizeCopilotPlan(value) {
                if (value === null || value === undefined) return null;
                var text = String(value || "").trim();
                if (!text || text.length > 120) return null;
                var compact = text.toLowerCase().replace(/[^a-z0-9]+/g, "");
                if (compact === "copilotpro" || compact === "pro" || compact === "copilotprofessional") return "Copilot Pro";
                if (compact === "copilotfree" || compact === "free" || compact === "copilotindividualfree") return "Copilot Free";
                if (compact === "copilotbusiness" || compact === "business") return "Copilot Business";
                if (compact === "copilotenterprise" || compact === "enterprise") return "Copilot Enterprise";
                if (compact === "copilotunknown" || compact === "unknown") return null;
                if (compact.indexOf("copilot") >= 0 && compact.indexOf("pro") >= 0) return "Copilot Pro";
                if (compact.indexOf("copilot") >= 0 && compact.indexOf("free") >= 0) return "Copilot Free";
                return text;
              }
              function pickCopilotPlanFromObject(value) {
                if (!value || typeof value !== "object") return null;
                return normalizeCopilotPlan(first(
                  value.planName,
                  value.plan_name,
                  value.plan,
                  value.sku,
                  value.copilotPlan,
                  value.copilot_plan,
                  value.licenseType,
                  value.license_type,
                  value.tier
                ));
              }
              function pickCopilotResetFromObject(value) {
                if (!value || typeof value !== "object") return null;
                return first(
                  value.quota_reset_date,
                  value.quotaResetDate,
                  value.resetDate,
                  value.reset_date,
                  value.resetsAt,
                  value.resets_at,
                  value.limited_user_reset_date,
                  value.limitedUserResetDate
                );
              }
              function copilotUsedPercentFromObject(value) {
                var usedPercent = numberFrom(value, [
                  "used_percent",
                  "usedPercent",
                  "usedPercentage",
                  "used_percentage",
                  "percent_used",
                  "percentUsed",
                  "usagePercent",
                  "usage_percentage"
                ]);
                if (usedPercent !== null) return clampPercent(usedPercent);
                var usedFraction = numberFrom(value, [
                  "utilization",
                  "u"
                ]);
                if (usedFraction !== null) return clampFractionPercent(usedFraction);
                var remainingPercent = numberFrom(value, [
                  "remaining_percent",
                  "remainingPercent",
                  "percent_remaining",
                  "percentRemaining",
                  "remainingPercentage",
                  "remaining_percentage"
                ]);
                if (remainingPercent !== null) {
                  var remaining = clampPercent(remainingPercent);
                  return remaining !== null ? Math.max(0, Math.min(100, 100 - remaining)) : null;
                }
                var remainingFraction = numberFrom(value, [
                  "remaining_fraction",
                  "remainingFraction"
                ]);
                if (remainingFraction !== null) {
                  var remaining = clampFractionPercent(remainingFraction);
                  return remaining !== null ? Math.max(0, Math.min(100, 100 - remaining)) : null;
                }
                var remaining = numberFrom(value, ["remaining", "remainingAmount", "remaining_amount", "balance"]);
                var limit = numberFrom(value, ["limit", "limitAmount", "limit_amount", "entitlement", "quota", "total"]);
                if (remaining !== null && limit !== null && limit > 0) return clampPercent((1 - (remaining / limit)) * 100);
                var usedAmount = numberFrom(value, ["used", "usedAmount", "used_amount", "usage"]);
                if (usedAmount !== null && limit !== null && limit > 0) return clampPercent((usedAmount / limit) * 100);
                return null;
              }
              function applyCopilotUsageValue(result, kind, usedPercent, resetDate) {
                if (!kind || usedPercent === null) return;
                if (kind === "chat" && result.chatPercent === null) result.chatPercent = usedPercent;
                if (kind === "completions" && result.completionPercent === null) result.completionPercent = usedPercent;
                if (resetDate && !result.resetDate) result.resetDate = resetDate;
                result.seen = true;
              }
              function mergeFeatureUsage(primary, secondary) {
                primary = primary || {};
                secondary = secondary || {};
                var result = {
                  seen: !!(primary.seen || secondary.seen),
                  complete: false,
                  completionPercent: primary.completionPercent !== null && primary.completionPercent !== undefined ? primary.completionPercent : (secondary.completionPercent !== undefined ? secondary.completionPercent : null),
                  chatPercent: primary.chatPercent !== null && primary.chatPercent !== undefined ? primary.chatPercent : (secondary.chatPercent !== undefined ? secondary.chatPercent : null),
                  plan: first(primary.plan, secondary.plan),
                  resetText: first(primary.resetText, secondary.resetText),
                  resetDate: first(primary.resetDate, secondary.resetDate)
                };
                result.complete = result.completionPercent !== null && result.chatPercent !== null;
                return result;
              }
              function scanCopilotUsageText(text) {
                var usage = featuresPageUsageFromText(text);
                if (usage && usage.seen) return usage;
                return null;
              }
              function scanCopilotUsageCandidate(value, result, depth, hint) {
                if (copilotScanBudget <= 0 || depth > 8 || value === null || value === undefined) return;
                copilotScanBudget -= 1;
                if (typeof value === "string") {
                  var textUsage = scanCopilotUsageText(value);
                  if (textUsage) {
                    var merged = mergeFeatureUsage(result, textUsage);
                    Object.keys(merged).forEach(function(key) { result[key] = merged[key]; });
                  }
                  var normalizedPlan = normalizeCopilotPlan(value);
                  if (normalizedPlan && !result.plan && String(value).toLowerCase().indexOf("copilot") >= 0) result.plan = normalizedPlan;
                  return;
                }
                if (Array.isArray(value)) {
                  for (var i = 0; i < value.length && i < 80; i += 1) {
                    scanCopilotUsageCandidate(value[i], result, depth + 1, hint);
                  }
                  return;
                }
                if (typeof value !== "object") return;
                result.plan = result.plan || pickCopilotPlanFromObject(value);
                result.resetDate = result.resetDate || pickCopilotResetFromObject(value);
                var label = first(
                  value.label,
                  value.title,
                  value.name,
                  value.displayName,
                  value.display_name,
                  value.feature,
                  value.featureName,
                  value.feature_name,
                  value.kind,
                  value.type,
                  value.key
                );
                var kind = copilotKindFromText([hint || "", label || ""].join(" "));
                var usedPercent = copilotUsedPercentFromObject(value);
                applyCopilotUsageValue(result, kind, usedPercent, pickCopilotResetFromObject(value));
                var keys = Object.keys(value);
                for (var j = 0; j < keys.length && j < 80; j += 1) {
                  scanCopilotUsageCandidate(value[keys[j]], result, depth + 1, [hint || "", label || "", keys[j]].join(" "));
                }
              }
              function parseCopilotJsonCandidates(text) {
                var raw = String(text || "");
                var candidates = [];
                var objectStart = raw.indexOf("{");
                var objectEnd = raw.lastIndexOf("}");
                if (objectStart >= 0 && objectEnd > objectStart) candidates.push(raw.slice(objectStart, objectEnd + 1));
                var arrayStart = raw.indexOf("[");
                var arrayEnd = raw.lastIndexOf("]");
                if (arrayStart >= 0 && arrayEnd > arrayStart) candidates.push(raw.slice(arrayStart, arrayEnd + 1));
                return candidates;
              }
              function looksLikeCopilotUsageText(text) {
                var lower = String(text || "").toLowerCase();
                return lower.indexOf("copilot") >= 0 ||
                  lower.indexOf("limited_user_quotas") >= 0 ||
                  lower.indexOf("monthly_quotas") >= 0 ||
                  lower.indexOf("quota_reset_date") >= 0 ||
                  lower.indexOf("chat messages") >= 0 ||
                  lower.indexOf("inline suggestions") >= 0 ||
                  lower.indexOf("code completion") >= 0 ||
                  lower.indexOf("\ucc44\ud305") >= 0 ||
                  lower.indexOf("\ucf54\ub4dc") >= 0;
              }
              function pushCopilotNetworkRow(url, text) {
                if (!text || !looksLikeCopilotUsageText([url || "", text].join("\n"))) return;
                window.__AIQuotaCopilotNetworkRows = window.__AIQuotaCopilotNetworkRows || [];
                window.__AIQuotaCopilotNetworkRows.push((url || "") + "\n" + String(text).slice(0, 200000));
                if (window.__AIQuotaCopilotNetworkRows.length > 30) window.__AIQuotaCopilotNetworkRows.shift();
              }
              function installCopilotNetworkHook() {
                if (window.__AIQuotaCopilotNetworkHookInstalled) return;
                window.__AIQuotaCopilotNetworkHookInstalled = true;
                window.__AIQuotaCopilotNetworkRows = window.__AIQuotaCopilotNetworkRows || [];
                try {
                  var originalFetch = window.fetch;
                  if (originalFetch) {
                    window.fetch = function(input, init) {
                      var url = typeof input === "string" ? input : (input && input.url) || "";
                      return originalFetch.apply(this, arguments).then(function(response) {
                        try {
                          if (response && response.clone) {
                            response.clone().text().then(function(text) {
                              pushCopilotNetworkRow(url, text);
                            }).catch(function(){});
                          }
                        } catch (error) {}
                        return response;
                      });
                    };
                  }
                } catch (error) {}
                try {
                  var originalOpen = XMLHttpRequest.prototype.open;
                  var originalSend = XMLHttpRequest.prototype.send;
                  XMLHttpRequest.prototype.open = function(method, url) {
                    this.__aiQuotaCopilotUrl = url;
                    return originalOpen.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener("load", function() {
                      try { pushCopilotNetworkRow(this.__aiQuotaCopilotUrl || "", this.responseText || ""); } catch (error) {}
                    });
                    return originalSend.apply(this, arguments);
                  };
                } catch (error) {}
              }
              var copilotScanBudget = 0;
              function scanCopilotPageState() {
                var result = {
                  seen: false,
                  complete: false,
                  completionPercent: null,
                  chatPercent: null,
                  plan: null,
                  resetText: null,
                  resetDate: null
                };
                var candidates = [];
                try { candidates.push(window.__NEXT_DATA__); } catch (error) {}
                try { candidates.push(window.__INITIAL_STATE__); } catch (error) {}
                try { candidates.push(window.__APP_DATA__); } catch (error) {}
                try { candidates.push(window.__remixContext); } catch (error) {}
                try {
                  Object.keys(window).slice(0, 800).forEach(function(key) {
                    var lower = String(key || "").toLowerCase();
                    if (lower.indexOf("copilot") >= 0 || lower.indexOf("quota") >= 0 || lower.indexOf("usage") >= 0) {
                      try { candidates.push(window[key]); } catch (error) {}
                    }
                  });
                } catch (error) {}
                (window.__AIQuotaCopilotNetworkRows || []).forEach(function(row) {
                  candidates.push(row);
                  parseCopilotJsonCandidates(row).forEach(function(candidate) {
                    try { candidates.push(JSON.parse(candidate)); } catch (error) {}
                  });
                });
                c.rows().forEach(function(row) {
                  if (!looksLikeCopilotUsageText(row)) return;
                  candidates.push(row);
                  parseCopilotJsonCandidates(row).forEach(function(candidate) {
                    try { candidates.push(JSON.parse(candidate)); } catch (error) {}
                  });
                });
                copilotScanBudget = 1600;
                for (var i = 0; i < candidates.length; i += 1) {
                  scanCopilotUsageCandidate(candidates[i], result, 0, "");
                  if (result.complete && result.plan && result.resetDate) break;
                }
                result.complete = result.completionPercent !== null && result.chatPercent !== null;
                return result.seen ? result : null;
              }
              function quotaEnvelope(value, depth) {
                if (!value || typeof value !== "object" || depth > 8) return null;
                if (value.limited_user_quotas || value.limitedUserQuotas ||
                    value.monthly_quotas || value.monthlyQuotas ||
                    value.quota_snapshots || value.quotaSnapshots) {
                  return value;
                }
                if (Array.isArray(value)) {
                  for (var i = 0; i < value.length; i += 1) {
                    var arrayMatch = quotaEnvelope(value[i], depth + 1);
                    if (arrayMatch) return arrayMatch;
                  }
                  return null;
                }
                var keys = Object.keys(value);
                for (var j = 0; j < keys.length; j += 1) {
                  var match = quotaEnvelope(value[keys[j]], depth + 1);
                  if (match) return match;
                }
                return null;
              }
              function copyQuotaObjects(source, target) {
                if (!source || !target) return;
                var limited = source.limited_user_quotas || source.limitedUserQuotas ||
                  (source.quotas && (source.quotas.limited_user_quotas || source.quotas.limitedUserQuotas));
                var monthly = source.monthly_quotas || source.monthlyQuotas ||
                  (source.quotas && (source.quotas.monthly_quotas || source.quotas.monthlyQuotas));
                if (limited && !target.limited_user_quotas && !target.limitedUserQuotas) target.limited_user_quotas = limited;
                if (monthly && !target.monthly_quotas && !target.monthlyQuotas) target.monthly_quotas = monthly;
              }
              function htmlDecode(value) {
                var textarea = document.createElement("textarea");
                textarea.innerHTML = value || "";
                return textarea.value;
              }
              function parseEmbeddedBillingData(rawText) {
                if (!rawText) return null;
                var markerIndex = rawText.indexOf("react-app.embeddedData");
                var scriptText = null;
                if (markerIndex >= 0) {
                  var openEnd = rawText.indexOf(">", markerIndex);
                  var closeStart = openEnd >= 0 ? rawText.indexOf("</script>", openEnd) : -1;
                  if (openEnd >= 0 && closeStart > openEnd) scriptText = rawText.slice(openEnd + 1, closeStart);
                }
                if (!scriptText) {
                  var match = rawText.match(/<script[^>]+data-target=["']react-app\.embeddedData["'][^>]*>([\s\S]*?)<\/script>/i);
                  if (match) scriptText = match[1];
                }
                if (!scriptText) return null;
                try {
                  return JSON.parse(htmlDecode(scriptText).trim());
                } catch (error) {
                  return null;
                }
              }
              function copilotSettingsUsage(settingsRaw) {
                if (!settingsRaw) return null;
                var direct = quotaEnvelope(settingsRaw, 0);
                if (direct) return direct;
                var rawText = settingsRaw.rawText || "";
                if (!rawText) return null;
                var scripts = [];
                var embedded = rawText.match(/<script[^>]+data-target=["']react-app\.embeddedData["'][^>]*>([\s\S]*?)<\/script>/ig) || [];
                embedded.forEach(function(block) {
                  var content = block.replace(/^[\s\S]*?>/, "").replace(/<\/script>[\s\S]*$/i, "");
                  scripts.push(content);
                });
                var jsonScripts = rawText.match(/<script[^>]+type=["']application\/json["'][^>]*>([\s\S]*?)<\/script>/ig) || [];
                jsonScripts.forEach(function(block) {
                  var content = block.replace(/^[\s\S]*?>/, "").replace(/<\/script>[\s\S]*$/i, "");
                  scripts.push(content);
                });
                for (var i = 0; i < scripts.length; i += 1) {
                  try {
                    var parsed = JSON.parse(htmlDecode(scripts[i]).trim());
                    var envelope = quotaEnvelope(parsed, 0);
                    if (envelope) return envelope;
                  } catch (error) {}
                }
                return null;
              }
              function premiumBillingInputFromEmbedded(embedded) {
                var payload = embedded && (embedded.payload || embedded);
                if (!payload) return null;
                var customer = payload.customer || {};
                var customerSelections = payload.customer_selections || payload.customerSelections || [];
                var firstCustomer = customerSelections && customerSelections.length ? customerSelections[0] : {};
                var periods = payload.period_selections || payload.periodSelections || [];
                var selectedPeriod = null;
                for (var i = 0; i < periods.length; i += 1) {
                  var periodOption = periods[i] || {};
                  if (periodOption.selected || periodOption.default || periodOption.type === 3) {
                    selectedPeriod = first(periodOption.type, periodOption.id, periodOption.value);
                    break;
                  }
                }
                return {
                  customerId: first(customer.customerId, customer.id, payload.customer_id, firstCustomer.id),
                  account: first(customer.displayId, customer.slug, customer.name, firstCustomer.displayId, firstCustomer.name),
                  period: selectedPeriod || 3
                };
              }
              function premiumBillingInput(billingPageRaw) {
                var rawText = billingPageRaw && billingPageRaw.rawText;
                try {
                  console.log("AIQuotaCopilot billing_raw len=" + (rawText ? rawText.length : 0) +
                    " embedded=" + (rawText ? rawText.indexOf("react-app.embeddedData") : -1) +
                    " customer=" + (rawText ? rawText.indexOf("customer") : -1) +
                    " card=" + (rawText ? rawText.indexOf("copilot_usage_card") : -1));
                } catch (logError) {}
                return premiumBillingInputFromEmbedded(parseEmbeddedBillingData(rawText));
              }
              function currentPagePremiumBillingInput() {
                try {
                  var node = document.querySelector("script[data-target='react-app.embeddedData']");
                  var raw = node && (node.textContent || node.innerHTML || "");
                  var input = raw ? premiumBillingInputFromEmbedded(JSON.parse(htmlDecode(raw).trim())) : null;
                  console.log("AIQuotaCopilot billing_dom embedded=" + !!raw + " input=" + !!input);
                  return input;
                } catch (error) {
                  try { console.log("AIQuotaCopilot billing_dom_error " + String(error && error.message || error)); } catch (logError) {}
                  return null;
                }
              }
              function premiumBillingCardUrl(input) {
                if (!input || !input.customerId) return null;
                return "https://github.com/settings/billing/copilot_usage_card?customer_id=" +
                  encodeURIComponent(input.customerId) + "&period=" + encodeURIComponent(input.period || 3) + "&query=";
              }
              function normalizePremiumBilling(cardRaw) {
                if (!cardRaw || typeof cardRaw !== "object") return null;
                var raw = cardRaw.payload || cardRaw.data || cardRaw;
                var hasUsage = raw.discountQuantity !== undefined ||
                  raw.userPremiumRequestEntitlement !== undefined ||
                  raw.filteredUserPremiumRequestEntitlement !== undefined ||
                  raw.netQuantity !== undefined;
                if (!hasUsage) return null;
                return {
                  discountQuantity: raw.discountQuantity,
                  userPremiumRequestEntitlement: raw.userPremiumRequestEntitlement,
                  filteredUserPremiumRequestEntitlement: raw.filteredUserPremiumRequestEntitlement,
                  netQuantity: raw.netQuantity,
                  netBilledAmount: raw.netBilledAmount
                };
              }
              function buildPayload(entitlementRaw, internalRaw, settingsRaw, premiumBillingRaw, billingInput) {
                var raw = entitlementRaw || {};
                var quotas = raw.quotas || raw.quota || raw.usage || {};
                var snapshots = quotaSnapshots(internalRaw) || quotaSnapshots(raw);
                if (snapshots && !quotas.quota_snapshots) quotas.quota_snapshots = snapshots;
                copyQuotaObjects(internalRaw, quotas);
                copyQuotaObjects(copilotSettingsUsage(settingsRaw), quotas);
                var premiumBilling = normalizePremiumBilling(premiumBillingRaw);
                if (premiumBilling) quotas.premium_billing = premiumBilling;
                var resetDate = first(
                  internalRaw && (internalRaw.quota_reset_date || internalRaw.resetDate),
                  settingsRaw && (settingsRaw.quota_reset_date || settingsRaw.resetDate || settingsRaw.limited_user_reset_date),
                  raw.quota_reset_date,
                  raw.resetDate,
                  quotas.quota_reset_date,
                  quotas.resetDate
                );
                if (resetDate && !quotas.quota_reset_date) quotas.quota_reset_date = resetDate;
                if (!snapshots && !quotas.premium_requests && !quotas.premiumRequests) {
                  var premium = raw.premium_requests || raw.premiumRequests || raw.premium_request_quota || raw.premiumRequestQuota;
                  var line = c.line(premium, "Premium requests");
                  if (line) quotas.premium_requests = line;
                }
                return {
                  provider: "copilot",
                  plan: first(raw.plan, raw.sku, raw.copilot_plan, raw.license, raw.licenseType, raw.license_type, internalRaw && (internalRaw.plan || internalRaw.sku || internalRaw.licenseType || internalRaw.license_type), settingsRaw && (settingsRaw.plan || settingsRaw.sku || settingsRaw.licenseType || settingsRaw.license_type)),
                  account: first(raw.account, raw.login, raw.user_login, raw.user && (raw.user.login || raw.user.name), internalRaw && (internalRaw.account || internalRaw.login || internalRaw.user_login), settingsRaw && (settingsRaw.account || settingsRaw.login || settingsRaw.user_login), billingInput && billingInput.account),
                  quotas: quotas
                };
              }
              function hasCopilotUsage(payload) {
                var quotas = payload && payload.quotas;
                if (!quotas || typeof quotas !== "object") return false;
                if (quotas.quota_snapshots || quotas.quotaSnapshots) return true;
                if (quotas.remaining && (
                  quotas.remaining.chat !== undefined ||
                  quotas.remaining.completions !== undefined ||
                  quotas.remaining.premiumInteractions !== undefined ||
                  quotas.remaining.premium_interactions !== undefined
                )) return true;
                var limited = quotas.limited_user_quotas || quotas.limitedUserQuotas;
                var monthly = quotas.monthly_quotas || quotas.monthlyQuotas;
                if (limited && monthly && (
                  limited.chat !== undefined ||
                  limited.completions !== undefined ||
                  monthly.chat !== undefined ||
                  monthly.completions !== undefined
                )) return true;
                return !!(quotas.premium_billing || quotas.premiumBilling || quotas.premium_requests || quotas.premiumRequests || quotas.chat || quotas.completions);
              }
              function collectFeaturesUsageWithMetadata(featureUsage) {
                var featurePayload = buildFeaturesPayload(featureUsage);
                Promise.all([
                  nativeJson("https://github.com/github-copilot/chat/entitlement"),
                  githubPostJson("https://github.com/github-copilot/chat/token"),
                  nativeJson("https://github.com/settings/copilot")
                ]).then(function(results) {
                  var entitlement = results[0] || {};
                  var token = results[1] || {};
                  var settingsPage = results[2] || {};
                  var authHeader = copilotAuthorizationHeader(token.json || {});
                  var tokenUsage = copilotUsageFromToken(token.json || {});
                  var apiAuthHeader = githubApiAuthorizationHeader(authHeader) || githubApiAuthorizationHeaderFromToken(token.json || {});
                  var internalPromise = apiAuthHeader
                    ? nativeJson("https://api.github.com/copilot_internal/user", apiAuthHeader)
                    : Promise.resolve({ ok: false, error: "github_api_token_unavailable" });
                  internalPromise.then(function(internal) {
                    var internalUsage = internal && internal.ok
                      ? Object.assign({}, tokenUsage || {}, internal.json || {})
                      : tokenUsage;
                    var metadataPayload = buildPayload(
                      entitlement.json || {},
                      internalUsage,
                      settingsPage.ok ? (settingsPage.json || {}) : null,
                      null,
                      null
                    );
                    c.post(mergeFeaturesMetadata(featurePayload, metadataPayload));
                  }).catch(function() {
                    c.post(featurePayload);
                  });
                }).catch(function() {
                  c.post(featurePayload);
                });
              }
              function collectEntitlementFallback(billingInput) {
                Promise.all([
                  nativeJson("https://github.com/github-copilot/chat/entitlement"),
                  githubPostJson("https://github.com/github-copilot/chat/token"),
                  nativeJson("https://github.com/settings/copilot"),
                  nativeJson("https://github.com/settings/billing/premium_requests_usage")
                ]).then(function(results) {
                   var entitlement = results[0] || {};
                   var token = results[1] || {};
                   var settingsPage = results[2] || {};
                   var billingPage = results[3] || {};
                   var authHeader = copilotAuthorizationHeader(token.json || {});
                   var tokenUsage = copilotUsageFromToken(token.json || {});
                   var apiAuthHeader = githubApiAuthorizationHeader(authHeader) || githubApiAuthorizationHeaderFromToken(token.json || {});
                   try {
                     console.log("AIQuotaCopilot token status=" + (token && token.status) +
                       " ok=" + !!(token && token.ok) +
                       " usage=" + quotaSummary(token || {}) +
                       " apiAuth=" + !!apiAuthHeader);
                   } catch (logError) {}
                   try {
                     console.log("AIQuotaCopilot settings status=" + (settingsPage && settingsPage.status) +
                       " ok=" + !!(settingsPage && settingsPage.ok) +
                       " usage=" + quotaSummary(settingsPage || {}) +
                       " markers=" + rawTextMarkerSummary(settingsPage || {}));
                   } catch (logError) {}
                    var internalPromise = (apiAuthHeader
                      ? nativeJson("https://api.github.com/copilot_internal/user", apiAuthHeader)
                      : Promise.resolve({ ok: false, error: "github_api_token_unavailable" }))
                      .then(function(primaryInternal) {
                        if (primaryInternal && primaryInternal.ok) return primaryInternal;
                        return nativeJson("https://github.com/copilot_internal/user").then(function(sessionInternal) {
                          try {
                            console.log("AIQuotaCopilot internal_session status=" + (sessionInternal && sessionInternal.status) +
                              " ok=" + !!(sessionInternal && sessionInternal.ok) +
                              " usage=" + quotaSummary(sessionInternal || {}));
                          } catch (logError) {}
                          return sessionInternal && sessionInternal.ok ? sessionInternal : primaryInternal;
                        }).catch(function() {
                          return primaryInternal;
                        });
                      });
                   internalPromise.then(function(internal) {
                   try {
                     console.log("AIQuotaCopilot internal status=" + (internal && internal.status) +
                       " ok=" + !!(internal && internal.ok) +
                       " usage=" + quotaSummary(internal || {}));
                   } catch (logError) {}
                   var internalUsage = internal.ok
                    ? Object.assign({}, tokenUsage || {}, internal.json || {})
                    : tokenUsage;
                  billingInput = billingInput || currentPagePremiumBillingInput() || premiumBillingInput(billingPage.json || {});
                  var cardUrl = premiumBillingCardUrl(billingInput);
                  try { console.log("AIQuotaCopilot billing_page input=" + !!billingInput + " card=" + !!cardUrl); } catch (logError) {}
                  if (!cardUrl && !entitlement.ok && !internal.ok) {
                    throw new Error("entitlement_http_" + (entitlement.status || entitlement.error || "failed") + "_internal_http_" + (internal.status || internal.error || "failed"));
                  }
                  var premiumPromise = cardUrl ? nativeJson(cardUrl) : Promise.resolve({ ok: false });
                  premiumPromise.then(function(premiumResult) {
                    try { console.log("AIQuotaCopilot billing_card status=" + (premiumResult && premiumResult.status) + " ok=" + !!(premiumResult && premiumResult.ok)); } catch (logError) {}
                    var payload = buildPayload(
                      entitlement.json || {},
                      internalUsage,
                      settingsPage.ok ? (settingsPage.json || {}) : null,
                      premiumResult && premiumResult.ok ? (premiumResult.json || {}) : null,
                      billingInput
                    );
                     if (!hasCopilotUsage(payload)) throw new Error("copilot_usage_payload_missing");
                     c.post(payload);
                   }).catch(function(error) {
                     var payload = buildPayload(entitlement.json || {}, internalUsage, settingsPage.ok ? (settingsPage.json || {}) : null, null, billingInput);
                     if (!hasCopilotUsage(payload)) throw error;
                     c.post(payload);
                   });
                  }).catch(function(error) {
                    throw error;
                  });
                }).catch(function(error) {
                  c.fail("copilot_entitlement_unavailable", String(error && error.message || error));
                });
              }
              function collectPremiumBilling(attempt) {
                var featureUsage = currentFeaturesPageUsage();
                if (featureUsage && featureUsage.complete) {
                  collectFeaturesUsageWithMetadata(featureUsage);
                  return;
                }
                if (featureUsage && featureUsage.seen && location.pathname.indexOf("/settings/copilot/features") >= 0) {
                  if (attempt < 5) {
                    setTimeout(function(){ collectPremiumBilling(attempt + 1); }, 600);
                  } else {
                    c.fail("copilot_features_usage_incomplete", "Copilot features usage was visible but incomplete.");
                  }
                  return;
                }
                var billingInput = currentPagePremiumBillingInput();
                var cardUrl = premiumBillingCardUrl(billingInput);
                try { console.log("AIQuotaCopilot billing_page input=" + !!billingInput + " card=" + !!cardUrl + " attempt=" + attempt); } catch (logError) {}
                if (!cardUrl) {
                  if (attempt < 3) {
                    setTimeout(function(){ collectPremiumBilling(attempt + 1); }, 500);
                  } else {
                    collectEntitlementFallback(billingInput);
                  }
                  return;
                }
                nativeJson(cardUrl).then(function(premiumResult) {
                  try { console.log("AIQuotaCopilot billing_card status=" + (premiumResult && premiumResult.status) + " ok=" + !!(premiumResult && premiumResult.ok)); } catch (logError) {}
                  var payload = buildPayload(
                    {},
                    null,
                    null,
                    premiumResult && premiumResult.ok ? (premiumResult.json || {}) : null,
                    billingInput
                  );
                  if (!hasCopilotUsage(payload)) {
                    collectEntitlementFallback(billingInput);
                    return;
                  }
                  c.post(payload);
                }).catch(function() {
                  collectEntitlementFallback(billingInput);
                });
              }
              installCopilotNetworkHook();
              setTimeout(function(){ collectPremiumBilling(0); }, 800);
            })();
        """.trimIndent()
    }

    internal fun antigravity(): String {
        return """
            (function(){
              if (!window.__AIQuotaStartProviderCollector || !window.__AIQuotaStartProviderCollector("antigravity")) return;
              var c = window.__AIQuotaCollector;
              if (!c) return;
              function first(object, keys) {
                if (!object || typeof object !== "object") return null;
                for (var i = 0; i < keys.length; i += 1) {
                  var value = object[keys[i]];
                  if (value !== null && value !== undefined && value !== "") return value;
                }
                return null;
              }
              function normalizePlan(value) {
                if (value === null || value === undefined) return null;
                var text = String(value);
                var compact = text.toUpperCase().replace(/[^A-Z0-9]+/g, "_").replace(/^_+|_+$/g, "");
                if (compact === "UNKNOWN") return null;
                if (compact.length > 0 && compact.length < 80 && text === text.toUpperCase()) return compact;
                return text.length > 0 && text.length < 80 ? text : null;
              }
              function rememberPlan(object, payload) {
                payload.plan = payload.plan || normalizePlan(first(object, ["plan", "planName", "plan_name", "tier", "membershipType", "displayName", "display_name", "subscriptionName"]));
                payload.account = payload.account || first(object, ["account", "email"]);
              }
              function appendLine(payload, line) {
                if (!line) return;
                var label = line.l || line.label || line.name || line.title || "";
                if (!label) return;
                line.l = label;
                payload.usage = payload.usage || {};
                payload.usage.x = payload.usage.x || [];
                if (payload.usage.x.some(function(existing) { return existing && existing.l === label; })) return;
                payload.usage.x.push(line);
              }
              function scanLineCandidate(object, payload, fallbackLabel) {
                var label = first(object, ["l", "label", "name", "title"]) || fallbackLabel;
                var line = c.line(object, label || "Antigravity Usage");
                appendLine(payload, line);
              }
              function scan(object, payload, depth) {
                if (!object || depth > 5) return;
                if (typeof object === "string") {
                  var start = object.indexOf("{");
                  var end = object.lastIndexOf("}");
                  if (start >= 0 && end > start) {
                    try { scan(JSON.parse(object.slice(start, end + 1)), payload, depth + 1); } catch (error) {}
                  }
                  return;
                }
                if (Array.isArray(object)) {
                  object.forEach(function(item) { scan(item, payload, depth + 1); });
                  return;
                }
                if (typeof object !== "object") return;
                rememberPlan(object, payload);
                var reset = first(object, ["resetAt", "resetsAt", "reset_at", "resets_at", "quota_reset_date"]);
                var line = c.line(object, first(object, ["l", "label", "name", "title"]));
                if (line) {
                  if (reset && !line.resetAt) line.resetAt = reset;
                  appendLine(payload, line);
                }
                Object.keys(object).forEach(function(key) {
                  var value = object[key];
                  if ((key === "x" || key === "limits" || key === "usage" || key === "quotas") && Array.isArray(value)) {
                    value.forEach(function(item) { scanLineCandidate(item, payload, first(item, ["l", "label", "name", "title"])); });
                  } else {
                    scan(value, payload, depth + 1);
                  }
                });
              }
              function scanAntigravityPageState(payload) {
                var candidates = [];
                try { candidates.push(window.__NEXT_DATA__); } catch (error) {}
                try { candidates.push(window.__INITIAL_STATE__); } catch (error) {}
                try { candidates.push(window.__APP_DATA__); } catch (error) {}
                try {
                  Object.keys(window).slice(0, 800).forEach(function(key) {
                    var lower = String(key || "").toLowerCase();
                    if (lower.indexOf("antigravity") >= 0 ||
                        lower.indexOf("usage") >= 0 ||
                        lower.indexOf("quota") >= 0 ||
                        lower.indexOf("plan") >= 0) {
                      try { candidates.push(window[key]); } catch (error) {}
                    }
                  });
                } catch (error) {}
                candidates.forEach(function(candidate) { scan(candidate, payload, 0); });
              }
              function scanRows(payload) {
                var rows = (window.__AIQuotaAntigravityNetworkRows || []).concat(c.rows ? c.rows() : []);
                rows.forEach(function(row) { scan(row, payload, 0); });
              }
              function pushAntigravityNetworkRow(url, text) {
                var value = String(url || "").toLowerCase() + "\n" + String(text || "").toLowerCase();
                if (value.indexOf("antigravity") < 0 && value.indexOf("quota") < 0 && value.indexOf("usage") < 0 && value.indexOf("plan") < 0) return;
                window.__AIQuotaAntigravityNetworkRows = window.__AIQuotaAntigravityNetworkRows || [];
                window.__AIQuotaAntigravityNetworkRows.push(String(url || "") + "\n" + String(text || "").slice(0, 200000));
                if (window.__AIQuotaAntigravityNetworkRows.length > 50) window.__AIQuotaAntigravityNetworkRows.shift();
              }
              function installAntigravityNetworkHook() {
                if (window.__AIQuotaAntigravityNetworkHookInstalled) return;
                window.__AIQuotaAntigravityNetworkHookInstalled = true;
                window.__AIQuotaAntigravityNetworkRows = window.__AIQuotaAntigravityNetworkRows || [];
                try {
                  var originalFetch = window.fetch;
                  if (originalFetch) {
                    window.fetch = function(input, init) {
                      var url = typeof input === "string" ? input : (input && input.url) || "";
                      return originalFetch.apply(this, arguments).then(function(response) {
                        try {
                          response.clone().text().then(function(text) {
                            pushAntigravityNetworkRow(url, text);
                          }).catch(function(){});
                        } catch (error) {}
                        return response;
                      });
                    };
                  }
                } catch (error) {}
                try {
                  var originalOpen = XMLHttpRequest.prototype.open;
                  var originalSend = XMLHttpRequest.prototype.send;
                  XMLHttpRequest.prototype.open = function(method, url) {
                    this.__aiQuotaAntigravityUrl = url;
                    return originalOpen.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener("load", function() {
                      try { pushAntigravityNetworkRow(this.__aiQuotaAntigravityUrl || "", this.responseText || ""); } catch (error) {}
                    });
                    return originalSend.apply(this, arguments);
                  };
                } catch (error) {}
              }
              function scanAntigravityText(payload) {
                var text = c.text ? c.text() : "";
                var plan = /(Antigravity|Google AI)\s+([^\n]{1,60})/i.exec(text);
                if (plan && !payload.plan) payload.plan = (plan[1] + " " + plan[2]).trim();
                var match = /(Antigravity[^\n]{0,40})\n\s*(\d{1,3})%\s*(?:remaining|left|남음)/i.exec(text);
                if (match) {
                  appendLine(payload, {
                    l: match[1].trim(),
                    remaining_percent: Number(match[2])
                  });
                }
              }
              function hasTrustedAntigravityPayload(payload) {
                var rows = payload && payload.usage && payload.usage.x;
                if (!Array.isArray(rows)) return false;
                return rows.some(function(row) {
                  return row && (
                    row.used_percent !== undefined ||
                    row.u !== undefined ||
                    row.remaining_percent !== undefined ||
                    row.remainingPercent !== undefined ||
                    row.remainingFraction !== undefined ||
                    row.remaining_fraction !== undefined
                  );
                });
              }
              function postAntigravityObservedPayload() {
                var payload = { provider: "antigravity" };
                scanAntigravityPageState(payload);
                scanRows(payload);
                scanAntigravityText(payload);
                if (hasTrustedAntigravityPayload(payload)) {
                  c.post(payload);
                  return true;
                }
                return false;
              }
              function collectAntigravityUsage(attempt) {
                if (postAntigravityObservedPayload()) return;
                if (attempt < 5) {
                  setTimeout(function(){ collectAntigravityUsage(attempt + 1); }, 800);
                } else {
                  c.fail("antigravity_no_trusted_payload", "Antigravity usage payload was not available.");
                }
              }
              installAntigravityNetworkHook();
              setTimeout(function(){ collectAntigravityUsage(0); }, 800);
            })();
        """.trimIndent()
    }

    internal fun cursor(): String {
        return """
            (function(){
              if (!window.__AIQuotaStartProviderCollector || !window.__AIQuotaStartProviderCollector("cursor")) return;
              var c = window.__AIQuotaCollector;
              if (!c) return;
              function number(value) {
                if (value === null || value === undefined || value === "") return null;
                var parsed = Number(value);
                return Number.isFinite(parsed) ? parsed : null;
              }
              function first(object, keys) {
                if (!object || typeof object !== "object") return null;
                for (var i = 0; i < keys.length; i += 1) {
                  var value = object[keys[i]];
                  if (value !== null && value !== undefined && value !== "") return value;
                }
                return null;
              }
              function copyUsage(object) {
                var keys = [
                  "totalPercentUsed", "usedPercent", "used_percent", "utilization",
                  "autoPercentUsed", "auto_percent_used", "autoUsagePercent", "auto_usage_percent",
                  "apiPercentUsed", "api_percent_used", "apiUsagePercent", "api_usage_percent",
                  "totalSpend", "totalUsage", "spend", "used", "usage",
                  "limit", "monthlyLimit", "totalLimit", "remaining", "remainingPercent",
                  "remaining_percent", "remainingFraction", "remaining_fraction",
                  "individualLimit", "individualRemaining", "individualUsed", "individualUsage",
                  "onDemandLimit", "onDemandRemaining", "onDemandUsed", "onDemandUsage",
                  "totalRemaining", "balance", "billingCycleStart", "billingCycleEnd",
                  "resetAt", "resetsAt", "resetText", "unit",
                  "breakdown", "usageBreakdown", "spendLimitUsage", "onDemand"
                ];
                var output = {};
                keys.forEach(function(key) {
                  if (object && object[key] !== undefined && object[key] !== null) output[key] = object[key];
                });
                return Object.keys(output).length > 0 ? output : null;
              }
              function copyRequestBucket(object) {
                var used = first(object, ["numRequests", "numRequestsTotal", "requestUsage", "currentRequestUsage", "requests", "used", "usage"]);
                var limit = first(object, ["maxRequestUsage", "maxRequests", "requestLimit", "limit", "limitAmount"]);
                if (number(used) === null || number(limit) === null) return null;
                return {
                  numRequests: number(used),
                  maxRequestUsage: number(limit)
                };
              }
              function rememberPlan(object, payload) {
                payload.membershipType = payload.membershipType ||
                  first(object, ["membershipType", "plan", "planName", "plan_label", "planType", "limitType", "subscription", "tier"]);
                payload.email = payload.email || first(object, ["email", "account"]);
              }
              function scan(object, payload, depth) {
                if (!object || typeof object !== "object" || depth > 5) return;
                rememberPlan(object, payload);
                if (object.billingCycleStart && !payload.billingCycleStart) payload.billingCycleStart = object.billingCycleStart;
                if (object.billingCycleEnd && !payload.billingCycleEnd) payload.billingCycleEnd = object.billingCycleEnd;
                if (object.planUsage && typeof object.planUsage === "object") payload.planUsage = payload.planUsage || copyUsage(object.planUsage);
                if (object.individualUsage && typeof object.individualUsage === "object") payload.individualUsage = payload.individualUsage || object.individualUsage;
                if (!payload.planUsage && copyUsage(object) && (
                  object.totalPercentUsed !== undefined ||
                  object.totalSpend !== undefined ||
                  object.totalUsage !== undefined ||
                  object.autoPercentUsed !== undefined ||
                  object.auto_percent_used !== undefined ||
                  object.apiPercentUsed !== undefined ||
                  object.api_percent_used !== undefined ||
                  object.remainingPercent !== undefined ||
                  object.remaining_percent !== undefined ||
                  object.remainingFraction !== undefined ||
                  object.remaining_fraction !== undefined ||
                  object.breakdown !== undefined ||
                  object.usageBreakdown !== undefined
                )) {
                  payload.planUsage = copyUsage(object);
                }
                var requestBucket = copyRequestBucket(object);
                if (requestBucket) {
                  payload.requestUsage = payload.requestUsage || {};
                  payload.requestUsage["bucket_" + Object.keys(payload.requestUsage).length] = requestBucket;
                }
                Object.keys(object).forEach(function(key) {
                  var value = object[key];
                  if (value && typeof value === "object") scan(value, payload, depth + 1);
                });
              }
              function nativeJson(url, body) {
                try {
                  if (!window.AIQuotaCollectorBridge || !window.AIQuotaCollectorBridge.fetchCursorJson) {
                    return Promise.resolve({ ok: false, error: "native_bridge_missing" });
                  }
                  return Promise.resolve(JSON.parse(window.AIQuotaCollectorBridge.fetchCursorJson(url, body || "")));
                } catch (error) {
                  return Promise.resolve({ ok: false, error: String(error && error.message || error) });
                }
              }
              function shouldCaptureCursor(url, text) {
                var value = String(url || "").toLowerCase() + "\n" + String(text || "").toLowerCase();
                return value.indexOf("/api/usage") >= 0 ||
                  value.indexOf("/api/usage-summary") >= 0 ||
                  value.indexOf("/api/auth/usage") >= 0 ||
                  value.indexOf("/api/auth/stripe") >= 0 ||
                  value.indexOf("/api/dashboard/get-credit-grants-balance") >= 0 ||
                  value.indexOf("getcurrentperiodusage") >= 0 ||
                  value.indexOf("planusage") >= 0 ||
                  value.indexOf("individualusage") >= 0 ||
                  value.indexOf("maxrequestusage") >= 0 ||
                  value.indexOf("numrequests") >= 0;
              }
              function pushCursorNetworkRow(url, text) {
                if (!shouldCaptureCursor(url, text)) return;
                window.__AIQuotaCursorNetworkRows = window.__AIQuotaCursorNetworkRows || [];
                window.__AIQuotaCursorNetworkRows.push(String(url || "") + "\n" + String(text || "").slice(0, 200000));
                if (window.__AIQuotaCursorNetworkRows.length > 50) window.__AIQuotaCursorNetworkRows.shift();
              }
              function installCursorNetworkHook() {
                if (window.__AIQuotaCursorNetworkHookInstalled) return;
                window.__AIQuotaCursorNetworkHookInstalled = true;
                window.__AIQuotaCursorNetworkRows = window.__AIQuotaCursorNetworkRows || [];
                try {
                  var originalFetch = window.fetch;
                  if (originalFetch) {
                    window.fetch = function(input, init) {
                      var url = typeof input === "string" ? input : (input && input.url) || "";
                      return originalFetch.apply(this, arguments).then(function(response) {
                        try {
                          response.clone().text().then(function(text) {
                            pushCursorNetworkRow(url, text);
                          }).catch(function(){});
                        } catch (error) {}
                        return response;
                      });
                    };
                  }
                } catch (error) {}
                try {
                  var originalOpen = XMLHttpRequest.prototype.open;
                  var originalSend = XMLHttpRequest.prototype.send;
                  XMLHttpRequest.prototype.open = function(method, url) {
                    this.__aiQuotaCursorUrl = url;
                    return originalOpen.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener("load", function() {
                      try { pushCursorNetworkRow(this.__aiQuotaCursorUrl || "", this.responseText || ""); } catch (error) {}
                    });
                    return originalSend.apply(this, arguments);
                  };
                } catch (error) {}
              }
              function scanRows(payload) {
                var rows = (window.__AIQuotaCursorNetworkRows || []).concat(c.rows ? c.rows() : []);
                rows.forEach(function(row) {
                  String(row || "").split(/\n(?=https?:\/\/)/g).forEach(function(chunk) {
                    var start = chunk.indexOf("{");
                    var end = chunk.lastIndexOf("}");
                    if (start >= 0 && end > start) {
                      try { scan(JSON.parse(chunk.slice(start, end + 1)), payload, 0); } catch (error) {}
                    }
                  });
                });
              }
              function scanCursorPageState(payload) {
                var candidates = [];
                try { candidates.push(window.__NEXT_DATA__); } catch (error) {}
                try { candidates.push(window.__INITIAL_STATE__); } catch (error) {}
                try { candidates.push(window.__APP_DATA__); } catch (error) {}
                try { candidates.push(window.__remixContext); } catch (error) {}
                try {
                  Object.keys(window).slice(0, 800).forEach(function(key) {
                    var lower = String(key || "").toLowerCase();
                    if (lower.indexOf("cursor") >= 0 ||
                        lower.indexOf("dashboard") >= 0 ||
                        lower.indexOf("usage") >= 0 ||
                        lower.indexOf("billing") >= 0 ||
                        lower.indexOf("request") >= 0 ||
                        lower.indexOf("plan") >= 0) {
                      try { candidates.push(window[key]); } catch (error) {}
                    }
                  });
                } catch (error) {}
                candidates.forEach(function(candidate) {
                  scan(candidate, payload, 0);
                });
              }
              function cursorUserIds() {
                var ids = [];
                var rows = (window.__AIQuotaCursorNetworkRows || []).concat(c.rows ? c.rows() : []);
                rows.join("\n").replace(/\/api\/usage\?user=([^&\s"']+)/g, function(_, id) {
                  if (ids.indexOf(id) < 0) ids.push(id);
                  return _;
                });
                rows.join("\n").replace(/user_[A-Za-z0-9_-]+/g, function(id) {
                  if (ids.indexOf(id) < 0) ids.push(id);
                  return id;
                });
                return ids.slice(0, 3);
              }
              function hasTrustedPlanUsage(usage) {
                if (!usage || typeof usage !== "object") return false;
                var usedPercent = number(first(usage, ["totalPercentUsed", "usedPercent", "used_percent", "utilization"]));
                var autoPercent = number(first(usage, ["autoPercentUsed", "auto_percent_used", "autoUsagePercent", "auto_usage_percent"]));
                var apiPercent = number(first(usage, ["apiPercentUsed", "api_percent_used", "apiUsagePercent", "api_usage_percent"]));
                var remainingPercent = number(first(usage, ["remainingPercent", "remaining_percent", "remainingFraction", "remaining_fraction"]));
                var limit = number(first(usage, ["limit", "monthlyLimit", "totalLimit", "individualLimit", "onDemandLimit"]));
                var used = number(first(usage, ["totalSpend", "totalUsage", "spend", "used", "usage", "individualUsed", "individualUsage", "onDemandUsed", "onDemandUsage"]));
                var remaining = number(first(usage, ["remaining", "totalRemaining", "balance", "individualRemaining", "onDemandRemaining"]));
                if (usedPercent !== null || autoPercent !== null || apiPercent !== null || remainingPercent !== null) return true;
                if (hasTrustedCursorBreakdown(usage.breakdown) || hasTrustedCursorBreakdown(usage.usageBreakdown)) return true;
                if (hasTrustedPlanUsage(usage.onDemand) || hasTrustedPlanUsage(usage.onDemandUsage) || hasTrustedPlanUsage(usage.spendLimitUsage)) return true;
                return limit !== null && limit > 0 && (used !== null || remaining !== null);
              }
              function hasTrustedCursorBreakdown(breakdown) {
                if (!breakdown || typeof breakdown !== "object") return false;
                if (Array.isArray(breakdown)) {
                  return breakdown.some(function(item) { return hasTrustedPlanUsage(item); });
                }
                return Object.keys(breakdown).some(function(key) {
                  var value = breakdown[key];
                  if (typeof value === "number" && Number.isFinite(value)) return true;
                  return hasTrustedPlanUsage(value);
                });
              }
              function hasTrustedRequestUsage(requestUsage) {
                if (!requestUsage || typeof requestUsage !== "object") return false;
                return Object.keys(requestUsage).some(function(key) {
                  return !!copyRequestBucket(requestUsage[key]);
                });
              }
              function hasTrustedIndividualUsage(individualUsage) {
                if (!individualUsage || typeof individualUsage !== "object") return false;
                return hasTrustedPlanUsage(individualUsage.plan) || hasTrustedPlanUsage(individualUsage.onDemand);
              }
              function hasTrustedCursorPayload(payload) {
                return hasTrustedPlanUsage(payload.planUsage) ||
                  hasTrustedRequestUsage(payload.requestUsage) ||
                  hasTrustedIndividualUsage(payload.individualUsage);
              }
              function collectCursorUsage(attempt) {
                installCursorNetworkHook();
                var userUsageProbes = cursorUserIds().map(function(userId) {
                  var url = "https://cursor.com/api/usage?user=" + encodeURIComponent(userId);
                  return nativeJson(url, "");
                });
                var probes = [
                  c.fetchJson("https://cursor.com/api/auth/stripe"),
                  c.fetchJson("https://cursor.com/api/usage"),
                  c.fetchJson("https://cursor.com/api/auth/usage"),
                  c.fetchJson("https://cursor.com/api/usage-summary"),
                  c.fetchJson("https://cursor.com/api/dashboard/get-credit-grants-balance"),
                  nativeJson("https://cursor.com/api/auth/stripe", ""),
                  nativeJson("https://cursor.com/api/usage", ""),
                  nativeJson("https://cursor.com/api/dashboard/get-credit-grants-balance", ""),
                  nativeJson("https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage", "{}"),
                  nativeJson("https://api2.cursor.sh/aiserver.v1.DashboardService/GetPlanInfo", "{}"),
                  nativeJson("https://api2.cursor.sh/aiserver.v1.DashboardService/GetCreditGrantsBalance", "{}"),
                  nativeJson("https://api2.cursor.sh/auth/usage", "{}")
                ].concat(userUsageProbes);
                Promise.allSettled(probes).then(function(results) {
                  var payload = { provider: "cursor" };
                  results.forEach(function(result) {
                    if (result.status === "fulfilled" && result.value && result.value.ok && result.value.json) {
                      scan(result.value.json, payload, 0);
                    }
                  });
                  scanCursorPageState(payload);
                  scanRows(payload);
                  try {
                    var nextData = document.getElementById("__NEXT_DATA__");
                    if (nextData && nextData.textContent) scan(JSON.parse(nextData.textContent), payload, 0);
                  } catch (error) {}
                  if (hasTrustedCursorPayload(payload)) {
                    c.post(payload);
                  } else {
                    setTimeout(function(){ collectCursorUsage(attempt + 1); }, attempt < 8 ? 3000 : 5000);
                  }
                }).catch(function(error) {
                  setTimeout(function(){ collectCursorUsage(attempt + 1); }, attempt < 8 ? 3000 : 5000);
                });
              }
              installCursorNetworkHook();
              setTimeout(function(){ collectCursorUsage(1); }, 2500);
            })();
        """.trimIndent()
    }
}
