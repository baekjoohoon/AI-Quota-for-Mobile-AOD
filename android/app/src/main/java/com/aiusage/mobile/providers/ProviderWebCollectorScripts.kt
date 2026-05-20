package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import java.net.URI
import java.util.Locale
import org.json.JSONObject

object ProviderWebCollectorScripts {
    fun shouldRunCollector(providerId: ProviderId, url: String, cookies: Map<String, String>, pageText: String): Boolean {
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
                host == "github.com" && path.startsWith("/settings/copilot")
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
            else -> false
        }
    }

    fun shouldRunCollectorFromResource(providerId: ProviderId, pageUrl: String, resourceUrl: String): Boolean {
        return shouldRunCollectorOnResource(providerId, resourceUrl) &&
            shouldRunCollector(providerId, pageUrl, emptyMap(), "")
    }

    fun shouldAcceptCollectorPayload(providerId: ProviderId, pageUrl: String): Boolean {
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
                  var key = "__AIUsageProviderCollectorRunning_" + provider;
                  if (window[key]) return false;
                  window[key] = true;
                  return true;
                };
              }
              if (window.__AIUsageCollectorRunning) return;
              window.__AIUsageCollectorRunning = true;
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
                    headers: { "accept": "application/json" }
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
              function first() {
                for (var i = 0; i < arguments.length; i += 1) {
                  var value = arguments[i];
                  if (value !== null && value !== undefined && value !== "") return value;
                }
                return null;
              }
              function nativeJson(url) {
                try {
                  if (window.AIUsageCollectorBridge && window.AIUsageCollectorBridge.fetchCopilotJson) {
                    var nativeResult = JSON.parse(window.AIUsageCollectorBridge.fetchCopilotJson(url));
                    if (nativeResult && nativeResult.ok) return Promise.resolve(nativeResult);
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
              function quotaSnapshots(raw) {
                return raw && (raw.quota_snapshots || raw.quotaSnapshots);
              }
              function buildPayload(entitlementRaw, internalRaw) {
                var raw = entitlementRaw || {};
                var quotas = raw.quotas || raw.quota || raw.usage || {};
                var snapshots = quotaSnapshots(internalRaw) || quotaSnapshots(raw);
                if (snapshots && !quotas.quota_snapshots) quotas.quota_snapshots = snapshots;
                var resetDate = first(
                  internalRaw && (internalRaw.quota_reset_date || internalRaw.resetDate),
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
                  plan: first(raw.plan, raw.sku, raw.copilot_plan, raw.license, raw.licenseType, raw.license_type, internalRaw && (internalRaw.plan || internalRaw.sku || internalRaw.licenseType || internalRaw.license_type)),
                  account: first(raw.account, raw.login, raw.user_login, raw.user && (raw.user.login || raw.user.name), internalRaw && (internalRaw.account || internalRaw.login || internalRaw.user_login)),
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
                return !!(quotas.premium_requests || quotas.premiumRequests || quotas.chat || quotas.completions);
              }
              setTimeout(function(){
                Promise.all([
                  nativeJson("https://github.com/github-copilot/chat/entitlement"),
                  nativeJson("https://api.github.com/copilot_internal/user")
                ]).then(function(results) {
                  var entitlement = results[0] || {};
                  var internal = results[1] || {};
                  if (!entitlement.ok && !internal.ok) {
                    throw new Error("entitlement_http_" + (entitlement.status || entitlement.error || "failed") + "_internal_http_" + (internal.status || internal.error || "failed"));
                  }
                  var payload = buildPayload(entitlement.json || {}, internal.ok ? (internal.json || {}) : null);
                  if (!hasCopilotUsage(payload)) throw new Error("copilot_usage_payload_missing");
                  c.post(payload);
                }).catch(function(error) {
                  c.fail("copilot_entitlement_unavailable", String(error && error.message || error));
                });
              }, 1200);
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
