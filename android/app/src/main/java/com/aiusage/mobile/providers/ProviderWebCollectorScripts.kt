package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
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
            ProviderId.GEMINI ->
                host == "accounts.google.com"
            ProviderId.COPILOT ->
                host == "github.com" &&
                    (path.startsWith("/login") ||
                        path.startsWith("/sessions") ||
                        path.startsWith("/session") ||
                        path.contains("two-factor"))
            ProviderId.CURSOR ->
                host == "accounts.google.com" ||
                    (host == "github.com" &&
                        (path.startsWith("/login") ||
                            path.startsWith("/sessions") ||
                            path.startsWith("/session") ||
                            path.contains("two-factor"))) ||
                    host == "api.workos.com" ||
                    host == "auth.workos.com" ||
                    host == "workos.com" ||
                    host == "authenticate.cursor.sh" ||
                    host == "authenticator.cursor.sh" ||
                    ((host == "cursor.com" || host == "www.cursor.com") &&
                        (path.contains("login") || path.contains("signin") || path.contains("auth")))
        }
    }

    fun shouldRunCollector(providerId: ProviderId, url: String, cookies: Map<String, String>, pageText: String): Boolean {
        if (providerId == ProviderId.COPILOT && url == "about:blank") return true
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
                    path != "/auth/login"
            ProviderId.GEMINI ->
                host == "gemini.google.com" &&
                    (path == "/" || path.startsWith("/app"))
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
            ProviderId.CURSOR ->
                (host == "cursor.com" || host == "www.cursor.com") &&
                    (path.contains("dashboard") || path.contains("settings") || path.contains("account") || path.contains("billing"))
        }
    }

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
                    (path == "/backend-api/wham/usage" ||
                        path == "/backend-api/subscriptions" ||
                        path == "/backend-api/me" ||
                        path.startsWith("/backend-api/accounts/check"))
            ProviderId.CURSOR ->
                (host == "cursor.com" || host == "www.cursor.com") &&
                    (path == "/api/auth/stripe" ||
                        path == "/api/usage" ||
                        path == "/api/auth/usage" ||
                        path == "/api/usage-summary" ||
                        path == "/api/dashboard/get-credit-grants-balance")
            ProviderId.GEMINI ->
                (host == "cloudcode-pa.googleapis.com" && path.contains("v1internal")) ||
                    (host == "gemini.google.com" && path.startsWith("/app"))
            ProviderId.COPILOT ->
                (host == "github.com" &&
                    (path == "/github-copilot/chat/entitlement" ||
                        path == "/github-copilot/chat/token" ||
                        path == "/settings/billing/premium_requests_usage" ||
                        path == "/settings/billing/copilot_usage_card" ||
                        path == "/copilot_internal/user")) ||
                    (host == "api.github.com" && path == "/copilot_internal/user")
            else -> false
        }
    }

    fun shouldRunCollectorFromResource(providerId: ProviderId, pageUrl: String, resourceUrl: String): Boolean {
        return shouldRunCollectorOnResource(providerId, resourceUrl) &&
            shouldRunCollector(providerId, pageUrl, emptyMap(), "")
    }

    fun shouldAcceptCollectorPayload(providerId: ProviderId, pageUrl: String): Boolean {
        if (providerId == ProviderId.COPILOT && pageUrl == "about:blank") return true
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        return when (providerId) {
            ProviderId.CLAUDE ->
                host.endsWith("claude.ai") &&
                    !path.contains("login") &&
                    !path.contains("logout")
            else -> shouldRunCollector(providerId, pageUrl, emptyMap(), "")
        }
    }

    fun build(
        providerId: ProviderId,
        cookies: Map<String, String>,
        geminiCollectorAsset: String,
        observedAccountId: String? = null,
        pageText: String = ""
    ): String {
        return common(providerId, cookies, observedAccountId, pageText) + "\n" + when (providerId) {
            ProviderId.CLAUDE -> claude()
            ProviderId.CODEX -> codex()
            ProviderId.GEMINI -> gemini(geminiCollectorAsset)
            ProviderId.COPILOT -> copilot()
            ProviderId.CURSOR -> cursor()
        }
    }

    private fun common(providerId: ProviderId, cookies: Map<String, String>, observedAccountId: String?, pageText: String): String {
        val cookieJson = JSONObject(cookies).toString()
        val observedAccountJson = JSONObject.quote(observedAccountId.orEmpty())
        val pageTextJson = JSONObject.quote(pageText)
        return """
            (function(){
              if (!window.__AIUsageStartProviderCollector) {
                window.__AIUsageStartProviderCollector = function(provider) {
                  return true;
                };
              }
              window.__AIUsageCollector = {
                provider: "${providerId.storageId}",
                cookies: $cookieJson,
                observedAccountId: $observedAccountJson,
                pageText: $pageTextJson,
                post: function(payload) {
                  payload = payload || {};
                  payload.provider = payload.provider || "${providerId.storageId}";
                  payload.collectorMode = payload.collectorMode || "webview-js";
                  window.AIUsageCollectorBridge.postUsagePayload(JSON.stringify(payload));
                },
                fail: function(kind, message) {
                  window.AIUsageCollectorBridge.postCollectorError(JSON.stringify({
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

    private fun claude(): String {
        return """
            (function(){
              if (!window.__AIUsageStartProviderCollector || !window.__AIUsageStartProviderCollector("claude")) return;
              var c = window.__AIUsageCollector;
              if (!c) return;
              function orgFromText(text) {
                var match = /org[_-][A-Za-z0-9_-]+/.exec(text || "");
                return match ? match[0] : null;
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
                  return value.id || value.uuid || value.organization_id || value.organizationId || orgFromText(JSON.stringify(value));
                }
                return null;
              }
              async function probeClaudeSession() {
                var result = {
                  loggedIn: false,
                  usageOk: false,
                  accountOk: false,
                  account: null,
                  plan: null,
                  orgId: null,
                  usage: null
                };
                var orgId = c.cookies.lastActiveOrg || orgFromText(c.rows().join("\n"));
                var meta = {};
                try {
                  var organizations = await c.fetchJson("https://claude.ai/api/organizations");
                  if (organizations.ok) {
                    result.loggedIn = true;
                    result.accountOk = true;
                    meta = organizations.json || {};
                    orgId = orgId || pickOrg(meta);
                  }
                } catch (error) {}
                if (!orgId) {
                  try {
                    var organization = await c.fetchJson("https://claude.ai/api/organizations/me");
                    if (organization.ok) {
                      result.loggedIn = true;
                      result.accountOk = true;
                      meta = organization.json || meta;
                      orgId = pickOrg(organization.json) || orgId;
                    }
                  } catch (error) {}
                }
                result.orgId = orgId || null;
                result.account = meta.email || meta.account || meta.name || null;
                result.plan = meta.plan || meta.subscription_plan || meta.subscriptionPlan || null;
                if (orgId) {
                  try {
                    var usage = await c.fetchJson("https://claude.ai/api/organizations/" + encodeURIComponent(orgId) + "/usage");
                    if (usage.ok && usage.json) {
                      result.loggedIn = true;
                      result.usageOk = true;
                      result.usage = usage.json;
                    }
                  } catch (error) {}
                }
                return result;
              }
              function hasClaudeUsagePayload(value) {
                if (!value || typeof value !== "object") return false;
                return !!(value.usage || value.five_hour || value.seven_day || value.seven_day_omelette || value.session || value.weekly || value.opus || value.sonnet || value.cowork || value.design);
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

    private fun codex(): String {
        return """
            (function(){
              if (!window.__AIUsageStartProviderCollector || !window.__AIUsageStartProviderCollector("codex")) return;
              var c = window.__AIUsageCollector;
              if (!c) return;
              var attempts = 0;
              function pickCodexAccountId(value) {
                if (!value) return null;
                if (typeof value === "string") {
                  var patterns = [
                    /"chatgpt_account_id"\s*:\s*"([^"]+)"/i,
                    /"chatgptAccountId"\s*:\s*"([^"]+)"/i,
                    /"current_account_id"\s*:\s*"([^"]+)"/i,
                    /"currentAccountId"\s*:\s*"([^"]+)"/i,
                    /"selected_account_id"\s*:\s*"([^"]+)"/i,
                    /"selectedAccountId"\s*:\s*"([^"]+)"/i,
                    /"account_id"\s*:\s*"([^"]+)"/i,
                    /"accountId"\s*:\s*"([^"]+)"/i,
                    /(acc_[A-Za-z0-9_-]+)/
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
                var directKeys = [
                  "chatgpt_account_id",
                  "chatgptAccountId",
                  "current_account_id",
                  "currentAccountId",
                  "selected_account_id",
                  "selectedAccountId",
                  "account_id",
                  "accountId"
                ];
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
                  var fromChild = pickCodexAccountId(value[keys[k]]);
                  if (fromChild) return fromChild;
                }
                return null;
              }
              function codexHeaders(accountId, accessToken) {
                var headers = {
                  "accept": "application/json",
                  "originator": "codex_cli_rs"
                };
                if (accountId) headers["ChatGPT-Account-Id"] = accountId;
                if (accessToken) headers["Authorization"] = "Bearer " + accessToken;
                return headers;
              }
              async function fetchCodexJson(url, accountId, accessToken) {
                var response = await fetch(url, {
                  credentials: "include",
                  headers: codexHeaders(accountId, accessToken)
                });
                var json = null;
                try { json = await response.json(); } catch (error) {}
                return { ok: response.ok, status: response.status, json: json };
              }
              function pickAccessToken(value) {
                if (!value) return null;
                if (typeof value === "string") return value.length > 20 ? value : null;
                if (Array.isArray(value)) {
                  for (var index = 0; index < value.length; index += 1) {
                    var fromArray = pickAccessToken(value[index]);
                    if (fromArray) return fromArray;
                  }
                  return null;
                }
                if (typeof value !== "object") return null;
                var direct = value.accessToken || value.access_token || value.token || value.id_token;
                if (typeof direct === "string" && direct.length > 20) return direct;
                var keys = Object.keys(value);
                for (var keyIndex = 0; keyIndex < keys.length; keyIndex += 1) {
                  var fromChild = pickAccessToken(value[keys[keyIndex]]);
                  if (fromChild) return fromChild;
                }
                return null;
              }
              function hasCodexUsageShape(value) {
                if (!value || typeof value !== "object") return false;
                return !!(
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
              function scanCodexUsageCandidate(value, depth) {
                if (!value || depth > 8) return null;
                if (hasCodexUsageShape(value)) return value;
                if (Array.isArray(value)) {
                  for (var index = 0; index < value.length; index += 1) {
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
                for (var keyIndex = 0; keyIndex < keys.length; keyIndex += 1) {
                  var fromChild = scanCodexUsageCandidate(value[keys[keyIndex]], depth + 1);
                  if (fromChild) return fromChild;
                }
                return null;
              }
              function parseCodexRow(row) {
                if (!row || typeof row !== "string") return null;
                if (
                  row.indexOf("rate_limit") < 0 &&
                  row.indexOf("rate_limits") < 0 &&
                  row.indexOf("primary_window") < 0 &&
                  row.indexOf("secondary_window") < 0 &&
                  row.indexOf("spark_rate") < 0 &&
                  row.indexOf("code_review_rate") < 0
                ) return null;
                try { return JSON.parse(row); } catch (error) {}
                return null;
              }
              function extractCodexUsageFromRows(accountId) {
                var rows = c.rows();
                for (var rowIndex = 0; rowIndex < rows.length; rowIndex += 1) {
                  var parsed = parseCodexRow(rows[rowIndex]);
                  var found = scanCodexUsageCandidate(parsed, 0);
                  if (found) {
                    found.provider = "codex";
                    found.account = found.account || accountId || pickCodexAccountId(parsed);
                    found.collectorMode = "webview-js-state";
                    return found;
                  }
                }
                return null;
              }
              async function probeCodexSession() {
                var result = {
                  loggedIn: false,
                  sessionOk: false,
                  usageOk: false,
                  accountOk: false,
                  usage: null,
                  account: null,
                  accountCheck: null,
                  accountId: null,
                  accessToken: null,
                  whamStatus: null
                };
                try {
                  var sessionRes = await fetchCodexJson("https://chatgpt.com/api/auth/session", null, null);
                  if (sessionRes.ok) {
                    result.loggedIn = true;
                    result.sessionOk = true;
                    result.accessToken = pickAccessToken(sessionRes.json);
                  }
                } catch (error) {}
                try {
                  var meRes = await fetchCodexJson("https://chatgpt.com/backend-api/me", null, result.accessToken);
                  if (meRes.ok) {
                    result.loggedIn = true;
                    result.sessionOk = true;
                    result.accountOk = true;
                    result.account = meRes.json;
                  }
                } catch (error) {}
                try {
                  var checkRes = await fetchCodexJson("https://chatgpt.com/backend-api/accounts/check/v4-2023-04-27", null, result.accessToken);
                  if (checkRes.ok) {
                    result.loggedIn = true;
                    result.sessionOk = true;
                    result.accountOk = true;
                    result.accountCheck = checkRes.json;
                  }
                } catch (error) {}
                result.accountId =
                  c.observedAccountId ||
                  pickCodexAccountId(result.account) ||
                  pickCodexAccountId(result.accountCheck) ||
                  pickCodexAccountId(c.rows());
                result.usage = extractCodexUsageFromRows(result.accountId);
                if (result.usage) {
                  result.loggedIn = true;
                  result.sessionOk = true;
                  result.usageOk = true;
                  return result;
                }
                try {
                  var usageRes = await fetchCodexJson("https://chatgpt.com/backend-api/wham/usage", result.accountId, result.accessToken);
                  result.whamStatus = usageRes.status;
                  if (usageRes.ok) {
                    result.usageOk = true;
                    result.loggedIn = true;
                    result.sessionOk = true;
                    result.usage = usageRes.json;
                    if (result.usage && typeof result.usage === "object") {
                      result.usage.account = result.usage.account || result.accountId;
                    }
                  }
                } catch (error) {}
                return result;
              }
              function looksLikeChatGptApp() {
                try {
                  var text = c.text().toLowerCase();
                  if (location.pathname.indexOf("/auth/login") >= 0) return false;
                  return !!(
                    document.querySelector("textarea, [contenteditable='true'], form button") ||
                    text.indexOf("오늘은 무슨 이야기를 할까요") >= 0 ||
                    text.indexOf("무엇이든 물어보세요") >= 0 ||
                    text.indexOf("message chatgpt") >= 0
                  );
                } catch (error) {
                  return false;
                }
              }
              function runProbe() {
                attempts += 1;
                probeCodexSession().then(function(result) {
                  if (result.loggedIn && result.usageOk && result.usage && typeof result.usage === "object") {
                    result.usage.provider = "codex";
                    c.post(result.usage);
                    return;
                  }
                  if (attempts >= 4 && (result.sessionOk || looksLikeChatGptApp())) {
                    c.fail("codex_usage_unavailable", "Codex session reached, but trusted usage payload was not available. whamStatus=" + (result.whamStatus || "unknown"));
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

    private fun gemini(asset: String): String {
        val collectorAsset = asset.ifBlank {
            "window.SAGE_USAGE_EXTRACTOR={buildGeminiUsagePayload:function(){return null;}};"
        }
        return """
            (function(){
              if (!window.__AIUsageStartProviderCollector || !window.__AIUsageStartProviderCollector("gemini")) return;
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
                var usedValue = parseNumber(value.u !== undefined ? value.u : (value.usedPercent !== undefined ? value.usedPercent : (value.used_percent !== undefined ? value.used_percent : (value.usageRate !== undefined ? value.usageRate : (value.usedRate !== undefined ? value.usedRate : value.utilization)))));
                var remainingValue = parseNumber(value.remainingFraction !== undefined ? value.remainingFraction : (value.remaining_fraction !== undefined ? value.remaining_fraction : (value.remainingPercent !== undefined ? value.remainingPercent : value.remaining_percent)));
                if (label && (usedValue !== null || remainingValue !== null)) {
                  var usedRate = usedValue !== null
                    ? (usedValue <= 1 ? usedValue : usedValue / 100)
                    : 1 - (remainingValue <= 1 ? remainingValue : remainingValue / 100);
                  limits.push({
                    l: label,
                    u: Math.max(0, Math.min(1, usedRate)),
                    r: value.r || value.resetAt || value.resets_at,
                    t: value.t || value.resetText || value.reset_text,
                    source: source,
                    confidence: value.confidence
                  });
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
                window.__AIUsageGeminiNetworkRows = window.__AIUsageGeminiNetworkRows || [];
                window.__AIUsageGeminiNetworkRows.push((url || "") + "\n" + text.slice(0, 200000));
                if (window.__AIUsageGeminiNetworkRows.length > 30) window.__AIUsageGeminiNetworkRows.shift();
              }
              function installGeminiNetworkHook() {
                if (window.__AIUsageGeminiNetworkHookInstalled) return;
                window.__AIUsageGeminiNetworkHookInstalled = true;
                window.__AIUsageGeminiNetworkRows = window.__AIUsageGeminiNetworkRows || [];
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
                    this.__aiUsageUrl = url;
                    return originalOpen.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener("load", function() {
                      try { pushGeminiNetworkRow(this.__aiUsageUrl || "", this.responseText || ""); } catch (error) {}
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
              window.PROVIDER_ID = "gemini";
              $collectorAsset
              var c = window.__AIUsageCollector;
              if (!c) return;
              installGeminiNetworkHook();
              var skipAttempts = 0;
              var collectAttempts = 0;
              function collectGeminiUsage() {
                collectAttempts += 1;
                if (skipAttempts < 3 && clickGeminiSetupSkip()) {
                  skipAttempts += 1;
                  setTimeout(collectGeminiUsage, 2200);
                  return;
                }
                try {
                  var rows = c.rows().concat(window.__AIUsageGeminiNetworkRows || []);
                  var limits = [];
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
                      combinedText: [c.pageText || "", rows.join("\n")].join("\n"),
                      authenticatedApp: location.hostname === "gemini.google.com" && location.pathname.indexOf("/app") === 0,
                      providerPage: true
                    });
                  if (payload && payload.usage && payload.usage.x && payload.usage.x.length > 0) {
                    c.post(payload);
                  } else {
                    setTimeout(collectGeminiUsage, 5000);
                  }
                } catch (error) {
                  c.fail("gemini_collector_error", String(error && error.message || error));
                }
              }
              setTimeout(collectGeminiUsage, 1800);
            })();
        """.trimIndent()
    }

    private fun copilot(): String {
        return """
            (function(){
              if (!window.__AIUsageStartProviderCollector || !window.__AIUsageStartProviderCollector("copilot")) return;
              var c = window.__AIUsageCollector;
              if (!c) return;
              try { console.log("AIUsageCopilot collector_start href=" + String(location && location.href || "")); } catch (logError) {}
              function first() {
                for (var i = 0; i < arguments.length; i += 1) {
                  var value = arguments[i];
                  if (value !== null && value !== undefined && value !== "") return value;
                }
                return null;
              }
              function nativeJson(url, authorizationHeader) {
                try {
                  if (window.AIUsageCollectorBridge && window.AIUsageCollectorBridge.fetchCopilotJson) {
                    var nativeResult = JSON.parse(
                      authorizationHeader && window.AIUsageCollectorBridge.fetchCopilotJsonWithAuthorization
                        ? window.AIUsageCollectorBridge.fetchCopilotJsonWithAuthorization(url, authorizationHeader)
                        : window.AIUsageCollectorBridge.fetchCopilotJson(url)
                    );
                    if (nativeResult && nativeResult.ok) return Promise.resolve(nativeResult);
                    if (authorizationHeader || url.indexOf("/github-copilot/chat/token") >= 0) return Promise.resolve(nativeResult);
                    return c.fetchJson(url).catch(function() { return nativeResult; });
                  }
                } catch (error) {
                  return c.fetchJson(url).catch(function() {
                    return { ok: false, url: url, error: String(error && error.message || error) };
                  });
                }
                return c.fetchJson(url).catch(function(error) {
                  return { ok: false, url: url, error: String(error && error.message || error) };
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
                  console.log("AIUsageCopilot billing_raw len=" + (rawText ? rawText.length : 0) +
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
                  console.log("AIUsageCopilot billing_dom embedded=" + !!raw + " input=" + !!input);
                  return input;
                } catch (error) {
                  try { console.log("AIUsageCopilot billing_dom_error " + String(error && error.message || error)); } catch (logError) {}
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
                     console.log("AIUsageCopilot token status=" + (token && token.status) +
                       " ok=" + !!(token && token.ok) +
                       " usage=" + quotaSummary(token || {}) +
                       " apiAuth=" + !!apiAuthHeader);
                   } catch (logError) {}
                   try {
                     console.log("AIUsageCopilot settings status=" + (settingsPage && settingsPage.status) +
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
                            console.log("AIUsageCopilot internal_session status=" + (sessionInternal && sessionInternal.status) +
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
                     console.log("AIUsageCopilot internal status=" + (internal && internal.status) +
                       " ok=" + !!(internal && internal.ok) +
                       " usage=" + quotaSummary(internal || {}));
                   } catch (logError) {}
                   var internalUsage = internal.ok
                    ? Object.assign({}, tokenUsage || {}, internal.json || {})
                    : tokenUsage;
                  billingInput = billingInput || currentPagePremiumBillingInput() || premiumBillingInput(billingPage.json || {});
                  var cardUrl = premiumBillingCardUrl(billingInput);
                  try { console.log("AIUsageCopilot billing_page input=" + !!billingInput + " card=" + !!cardUrl); } catch (logError) {}
                  if (!cardUrl && !entitlement.ok && !internal.ok) {
                    throw new Error("entitlement_http_" + (entitlement.status || entitlement.error || "failed") + "_internal_http_" + (internal.status || internal.error || "failed"));
                  }
                  var premiumPromise = cardUrl ? nativeJson(cardUrl) : Promise.resolve({ ok: false });
                  premiumPromise.then(function(premiumResult) {
                    try { console.log("AIUsageCopilot billing_card status=" + (premiumResult && premiumResult.status) + " ok=" + !!(premiumResult && premiumResult.ok)); } catch (logError) {}
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
                var billingInput = currentPagePremiumBillingInput();
                var cardUrl = premiumBillingCardUrl(billingInput);
                try { console.log("AIUsageCopilot billing_page input=" + !!billingInput + " card=" + !!cardUrl + " attempt=" + attempt); } catch (logError) {}
                if (!cardUrl) {
                  if (attempt < 3) {
                    setTimeout(function(){ collectPremiumBilling(attempt + 1); }, 500);
                  } else {
                    collectEntitlementFallback(billingInput);
                  }
                  return;
                }
                nativeJson(cardUrl).then(function(premiumResult) {
                  try { console.log("AIUsageCopilot billing_card status=" + (premiumResult && premiumResult.status) + " ok=" + !!(premiumResult && premiumResult.ok)); } catch (logError) {}
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
              setTimeout(function(){ collectPremiumBilling(0); }, 800);
            })();
        """.trimIndent()
    }

    private fun cursor(): String {
        return """
            (function(){
              if (!window.__AIUsageStartProviderCollector || !window.__AIUsageStartProviderCollector("cursor")) return;
              var c = window.__AIUsageCollector;
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
                  "totalSpend", "totalUsage", "spend", "used", "usage",
                  "limit", "monthlyLimit", "totalLimit", "remaining", "remainingPercent",
                  "totalRemaining", "balance", "billingCycleStart", "billingCycleEnd",
                  "resetAt", "resetsAt", "resetText", "unit"
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
                  object.totalPercentUsed !== undefined || object.totalSpend !== undefined || object.totalUsage !== undefined
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
                  if (!window.AIUsageCollectorBridge || !window.AIUsageCollectorBridge.fetchCursorJson) {
                    return Promise.resolve({ ok: false, error: "native_bridge_missing" });
                  }
                  return Promise.resolve(JSON.parse(window.AIUsageCollectorBridge.fetchCursorJson(url, body || "")));
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
                window.__AIUsageCursorNetworkRows = window.__AIUsageCursorNetworkRows || [];
                window.__AIUsageCursorNetworkRows.push(String(url || "") + "\n" + String(text || "").slice(0, 200000));
                if (window.__AIUsageCursorNetworkRows.length > 50) window.__AIUsageCursorNetworkRows.shift();
              }
              function installCursorNetworkHook() {
                if (window.__AIUsageCursorNetworkHookInstalled) return;
                window.__AIUsageCursorNetworkHookInstalled = true;
                window.__AIUsageCursorNetworkRows = window.__AIUsageCursorNetworkRows || [];
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
                    this.__aiUsageCursorUrl = url;
                    return originalOpen.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener("load", function() {
                      try { pushCursorNetworkRow(this.__aiUsageCursorUrl || "", this.responseText || ""); } catch (error) {}
                    });
                    return originalSend.apply(this, arguments);
                  };
                } catch (error) {}
              }
              function scanRows(payload) {
                var rows = (window.__AIUsageCursorNetworkRows || []).concat(c.rows ? c.rows() : []);
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
              function cursorUserIds() {
                var ids = [];
                var rows = (window.__AIUsageCursorNetworkRows || []).concat(c.rows ? c.rows() : []);
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
                var remainingPercent = number(first(usage, ["remainingPercent", "remaining_percent"]));
                var limit = number(first(usage, ["limit", "monthlyLimit", "totalLimit"]));
                var used = number(first(usage, ["totalSpend", "totalUsage", "spend", "used", "usage"]));
                var remaining = number(first(usage, ["remaining", "totalRemaining", "balance"]));
                if (usedPercent !== null || remainingPercent !== null) return true;
                return limit !== null && limit > 0 && (used !== null || remaining !== null);
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
