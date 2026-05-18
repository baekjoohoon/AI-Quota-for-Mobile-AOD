package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.json.JSONArray

object ProviderLocalUsageCollector {
    val SCRIPT: String
        get() = scriptFor(ProviderId.CLAUDE)

    fun scriptFor(providerId: ProviderId): String {
        return scriptFor(providerId, hookOnly = false)
    }

    fun hookScriptFor(providerId: ProviderId): String {
        return scriptFor(providerId, hookOnly = true)
    }

    private fun scriptFor(providerId: ProviderId, hookOnly: Boolean): String {
        val profile = profileFor(providerId)
        return SCRIPT_TEMPLATE
            .replace("__PROVIDER_ID__", providerId.storageId)
            .replace("__PROVIDER_NAME__", providerId.displayName)
            .replace("__PLAN_HINTS__", profile.planHints.joinToString("|"))
            .replace("__LABEL_HINTS__", profile.labelHints.joinToString("|"))
            .replace("__HOOK_ONLY__", hookOnly.toString())
    }

    private fun profileFor(providerId: ProviderId): ProviderScriptProfile {
        return when (providerId) {
            ProviderId.CLAUDE -> ProviderScriptProfile(
                planHints = listOf(
                    "Claude Enterprise",
                    "Claude Team",
                    "Claude Max",
                    "Claude Pro",
                    "Free",
                    "Pro",
                    "Max",
                    "Team",
                    "Enterprise"
                ),
                labelHints = listOf("Claude", "Weekly limits", "Message limit", "Usage", "Remaining")
            )
            ProviderId.CODEX -> ProviderScriptProfile(
                planHints = listOf("Free", "Go", "Plus", "Prolite", "Pro", "Team", "Business", "Enterprise", "Edu"),
                labelHints = listOf("ChatGPT", "GPT-5", "GPT-4o", "Thinking", "Messages", "Usage")
            )
            ProviderId.GEMINI -> ProviderScriptProfile(
                planHints = listOf(
                    "Google AI Ultra",
                    "Google AI Pro",
                    "Google One AI Premium",
                    "Gemini Advanced",
                    "AI Ultra",
                    "AI Pro",
                    "AI Premium",
                    "Free",
                    "Advanced",
                    "Ultra",
                    "Pro"
                ),
                labelHints = listOf("Gemini", "Flash", "Pro", "Advanced", "Usage", "Remaining")
            )
            ProviderId.COPILOT -> ProviderScriptProfile(
                planHints = listOf("Free", "Pro", "Business", "Enterprise"),
                labelHints = listOf("Copilot", "Premium requests", "Chat messages", "Completions", "Usage")
            )
            ProviderId.CURSOR -> ProviderScriptProfile(
                planHints = listOf("Free", "Pro", "Ultra", "Teams", "Business"),
                labelHints = listOf("Cursor", "Fast requests", "Premium requests", "Included usage", "Usage")
            )
        }
    }

    private data class ProviderScriptProfile(
        val planHints: List<String>,
        val labelHints: List<String>
    )

    private val SCRIPT_TEMPLATE =
        """
        (function() {
          var PROVIDER_ID = "__PROVIDER_ID__";
          var PROVIDER_NAME = "__PROVIDER_NAME__";
          var PLAN_HINTS = "__PLAN_HINTS__";
          var LABEL_HINTS = "__LABEL_HINTS__";
          var HOOK_ONLY = __HOOK_ONLY__;
          var KEYWORD_PATTERN = /usage|limit|quota|plan|subscription|billing|rate|cap|remaining|reset|credit|entitlement/i;
          var SKIP_KEY_PATTERN = /token|secret|session|cookie|auth|jwt|credential|password|csrf|key/i;
          var PROVIDER_LABEL_PATTERN = new RegExp(PROVIDER_NAME + "|" + LABEL_HINTS, "i");
          var PLAN_PATTERN = new RegExp("\\b(" + PLAN_HINTS + ")\\b", "i");
          var SAGE_USAGE_EXTRACTOR = window.SAGE_USAGE_EXTRACTOR || {};
          window.SAGE_USAGE_EXTRACTOR = SAGE_USAGE_EXTRACTOR;

          function safeText(value) {
            return (value || '').toString().replace(/\s+/g, ' ').trim();
          }
          function shouldIncludeKey(key) {
            return KEYWORD_PATTERN.test(key || '');
          }
          function shouldSkipKey(key) {
            return SKIP_KEY_PATTERN.test(key || '');
          }
          function shouldIncludeStorageValue(key, value) {
            if (shouldIncludeKey(key)) return true;
            return KEYWORD_PATTERN.test((value || '').toString().slice(0, 20000));
          }
          function rememberSignal(value) {
            var text = safeText(value);
            if (!text || !KEYWORD_PATTERN.test(text)) return;
            if (!window.__AI_USAGE_PROVIDER_EVENTS__) window.__AI_USAGE_PROVIDER_EVENTS__ = [];
            window.__AI_USAGE_PROVIDER_EVENTS__.push(text.slice(0, 4000));
            if (window.__AI_USAGE_PROVIDER_EVENTS__.length > 32) {
              window.__AI_USAGE_PROVIDER_EVENTS__.shift();
            }
          }
          function endpointSummaries() {
            if (!window.__AI_USAGE_ENDPOINT_SUMMARIES__) window.__AI_USAGE_ENDPOINT_SUMMARIES__ = [];
            return window.__AI_USAGE_ENDPOINT_SUMMARIES__;
          }
          function derivedPlans() {
            if (!window.__AI_USAGE_DERIVED_PLANS__) window.__AI_USAGE_DERIVED_PLANS__ = [];
            return window.__AI_USAGE_DERIVED_PLANS__;
          }
          function derivedLimits() {
            if (!window.__AI_USAGE_DERIVED_LIMITS__) window.__AI_USAGE_DERIVED_LIMITS__ = [];
            return window.__AI_USAGE_DERIVED_LIMITS__;
          }
          function rememberDerivedPlan(plan) {
            var value = normalizedPlanValue(plan);
            if (!value) return;
            var list = derivedPlans();
            if (list.indexOf(value) < 0) list.push(value);
            if (list.length > 8) list.shift();
          }
          function safeEndpointLabel(endpoint) {
            try {
              var url = new URL(endpoint, location.origin);
              return safeText(url.pathname)
                .replace(/[A-Za-z0-9_-]{16,}/g, ":id")
                .slice(0, 96) || "/";
            } catch (error) {
              return safeText(endpoint).slice(0, 96) || "unknown";
            }
          }
          function rememberEndpointSummary(endpoint, response, value) {
            try {
              var text = (value || "").toString();
              var summary = {
                e: safeEndpointLabel(endpoint),
                s: response && typeof response.status === "number" ? response.status : 0,
                n: text.length,
                j: false,
                lk: 0,
                rk: 0,
                pk: 0,
                k: []
              };
              var candidate = extractJsonCandidate(text);
              if (candidate) {
                summary.j = true;
                var parsed = JSON.parse(candidate);
                summarizeJsonKeys(parsed, summary, 0, "");
                rememberDerivedPlan(scanStructuredPlan(parsed, "", 0));
                var endpointLabel = safeEndpointLabel(endpoint);
                var providerApiMatched = scanClaudeOrganizationUsage(parsed, endpointLabel, derivedLimits());
                if (!providerApiMatched) scanJson(parsed, ["endpoint"], endpointLabel, derivedLimits(), 0);
                if (derivedLimits().length > 8) {
                  window.__AI_USAGE_DERIVED_LIMITS__ = PROVIDER_ID === "claude"
                    ? derivedLimits().slice(0, 8)
                    : derivedLimits().slice(-8);
                }
              }
              var list = endpointSummaries();
              list.push(summary);
              if (list.length > 16) list.shift();
            } catch (error) {}
          }
          function rememberEndpointError(endpoint, statusCode) {
            try {
              var list = endpointSummaries();
              list.push({
                e: safeEndpointLabel(endpoint),
                s: statusCode,
                n: 0,
                j: false,
                lk: 0,
                rk: 0,
                pk: 0,
                k: []
              });
              if (list.length > 16) list.shift();
            } catch (error) {}
          }
          function safeKeySegment(key) {
            var value = safeText(key).replace(/[^A-Za-z0-9_$-]+/g, "_");
            if (!value) return "key";
            if (value.length > 32 || /^[A-Za-z0-9_-]{16,}$/.test(value)) return ":key";
            return value;
          }
          function rememberKeyPath(summary, path) {
            if (!summary || !summary.k || summary.k.length >= 8) return;
            var value = safeText(path).slice(0, 120);
            if (!value || summary.k.indexOf(value) >= 0) return;
            summary.k.push(value);
          }
          function summarizeJsonKeys(value, summary, depth, path) {
            if (!value || depth > 8) return;
            if (Array.isArray(value)) {
              value.slice(0, 8).forEach(function(item) {
                summarizeJsonKeys(item, summary, depth + 1, path + "[]");
              });
              return;
            }
            if (typeof value !== "object") return;
            Object.keys(value).slice(0, 80).forEach(function(key) {
              if (shouldSkipKey(key)) return;
              var keyPath = path ? path + "." + safeKeySegment(key) : safeKeySegment(key);
              if (/limit|quota|cap|max|allowance|included|rate/i.test(key)) summary.lk += 1;
              if (/remaining|left|available|balance/i.test(key)) summary.rk += 1;
              if (/plan|subscription|tier|entitlement/i.test(key)) summary.pk += 1;
              if (/limit|quota|cap|max|allowance|included|rate|remaining|left|available|balance|plan|subscription|tier|entitlement/i.test(key)) {
                rememberKeyPath(summary, keyPath);
              }
              summarizeJsonKeys(value[key], summary, depth + 1, keyPath);
            });
          }
          function installNetworkHooks() {
            if (window.__AI_USAGE_EXTRACTOR_INSTALLED__) return;
            window.__AI_USAGE_EXTRACTOR_INSTALLED__ = true;
            if (!window.__AI_USAGE_PROVIDER_EVENTS__) window.__AI_USAGE_PROVIDER_EVENTS__ = [];
            try {
              var originalFetch = window.fetch;
              if (typeof originalFetch === 'function') {
                window.fetch = function() {
                  var requestUrl = "";
                  try {
                    var firstArgument = arguments[0];
                    requestUrl = typeof firstArgument === "string" ? firstArgument : firstArgument && firstArgument.url;
                  } catch (argumentError) {}
                  var result = originalFetch.apply(this, arguments);
                  try {
                    result.then(function(response) {
                      try {
                        response.clone().text().then(function(value) {
                          rememberEndpointSummary(response.url || requestUrl, response, value);
                          rememberSignal(value);
                        }).catch(function() {});
                      } catch (error) {}
                    }).catch(function() {});
                  } catch (error) {}
                  return result;
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
                try {
                  this.addEventListener('loadend', function() {
                    try {
                      rememberEndpointSummary(
                        this.responseURL || this.__aiUsageUrl,
                        { status: this.status },
                        this.responseText
                      );
                      rememberSignal(this.__aiUsageUrl + ' ' + this.responseText);
                    } catch (error) {}
                  });
                } catch (error) {}
                return originalSend.apply(this, arguments);
              };
            } catch (error) {}
          }
          function collectStorage(storage, label) {
            var rows = [];
            if (!storage) return rows;
            try {
              for (var index = 0; index < storage.length; index += 1) {
                var key = storage.key(index);
                if (!key || shouldSkipKey(key)) continue;
                var value = storage.getItem(key);
                if (!value || value.length > 500000) continue;
                if (!shouldIncludeStorageValue(key, value)) continue;
                rows.push(label + ':' + key + '=' + value.slice(0, 20000));
              }
            } catch (error) {}
            return rows;
          }
          function safeStorage(name) {
            try {
              return window[name];
            } catch (error) {
              return null;
            }
          }
          function stringifyValue(value) {
            if (typeof value === "string") return value;
            try {
              return JSON.stringify(value);
            } catch (error) {
              return safeText(value);
            }
          }
          function collectScripts() {
            var rows = [];
            var scripts = Array.prototype.slice.call(document.scripts || []);
            scripts.forEach(function(script) {
              var value = safeText(script.textContent);
              if (!value || value.length > 50000) return;
              if (/usage|limit|quota|plan|subscription|billing|remaining|reset|entitlement/i.test(value)) {
                rows.push(value);
              }
            });
            try {
              if (window.__NEXT_DATA__) rows.push(JSON.stringify(window.__NEXT_DATA__));
            } catch (error) {}
            return rows;
          }
          var trustedPlanSourcePattern = /\b(account|subscription|billing|plan|tier|entitlement|product|sku|package|license|membership|google_one|googleOne|account_plan|accountPlan|planType|plan_type|workspace_plan|subscription_details|current_user_access|is_free_plan|isFreePlan)\b/i;
          function findPlan(text) {
            var explicit = new RegExp("\\b(?:plan|subscription|tier|entitlement|product|sku|package|license|membership|account_plan|planType|plan_type|is_free_plan|isFreePlan)\\b[^\\n]{0,160}\\b(" + PLAN_HINTS + ")\\b", "i").exec(text);
            return explicit ? safeText(explicit[1]) : null;
          }
          function findTrustedPlan(rows) {
            for (var index = 0; index < rows.length; index += 1) {
              var row = safeText(rows[index]);
              if (!trustedPlanSourcePattern.test(row)) continue;
              var plan = findPlan(row);
              if (plan) return plan;
            }
            return null;
          }
          function normalizedPlanValue(value) {
            var text = safeText(value);
            if (!text || text.length > 80) return null;
            var direct = new RegExp("^\\s*(" + PLAN_HINTS + ")\\s*$", "i").exec(text);
            if (direct) return canonicalPlanHint(direct[1]);
            var compact = text.replace(/[_-]+/g, " ");
            var embedded = new RegExp("(?:^|\\s)(" + PLAN_HINTS + ")(?:\\s|$)", "i").exec(compact);
            if (embedded) return canonicalPlanHint(embedded[1]);
            var hints = PLAN_HINTS.split("|");
            var lower = text.toLowerCase();
            var compactLower = lower.replace(/[^a-z0-9]+/g, "");
            for (var index = 0; index < hints.length; index += 1) {
              var hint = safeText(hints[index]);
              if (!hint) continue;
              var hintLower = hint.toLowerCase();
              if (
                compactLower.indexOf("chatgpt" + hintLower) >= 0 ||
                compactLower.indexOf(hintLower + "plan") >= 0 ||
                compactLower.indexOf(hintLower + "subscription") >= 0 ||
                compactLower.indexOf(hintLower + "tier") >= 0 ||
                compactLower.indexOf("plan" + hintLower) >= 0 ||
                compactLower.indexOf("subscription" + hintLower) >= 0
              ) return hint;
            }
            return null;
          }
          function canonicalPlanHint(value) {
            var text = safeText(value).toLowerCase().replace(/[^a-z0-9]+/g, "");
            var hints = PLAN_HINTS.split("|");
            for (var index = 0; index < hints.length; index += 1) {
              var hint = safeText(hints[index]);
              if (!hint) continue;
              if (hint.toLowerCase().replace(/[^a-z0-9]+/g, "") === text) return hint;
            }
            return safeText(value);
          }
          function booleanPlanValue(key, value) {
            var keyText = safeText(key).replace(/[_-]+/g, "").toLowerCase();
            if (keyText.indexOf("isfreeplan") < 0 && keyText.indexOf("freeplan") < 0) return null;
            if (value === true) return "Free";
            if (value === false) return null;
            if (typeof value === "string") {
              var text = safeText(value).toLowerCase();
              if (/^(true|1|yes)$/.test(text)) return "Free";
              if (/^(false|0|no)$/.test(text)) return null;
            }
            return null;
          }
          function isPlanKey(key) {
            return /plan|subscription|tier|entitlement|product|sku|package|license|membership|google_one|googleOne|account_plan|planType|plan_type|workspace_plan|is_free_plan|isFreePlan/i.test(key);
          }
          function scanStructuredPlan(value, path, depth) {
            if (depth > 8 || value == null) return null;
            if (typeof value === "string") {
              var stringBooleanPlan = booleanPlanValue(path, value);
              if (stringBooleanPlan) return stringBooleanPlan;
              return trustedPlanSourcePattern.test(path) ? normalizedPlanValue(value) : null;
            }
            if (typeof value === "boolean") return booleanPlanValue(path, value);
            if (Array.isArray(value)) {
              for (var arrayIndex = 0; arrayIndex < value.length && arrayIndex < 12; arrayIndex += 1) {
                var arrayPlan = scanStructuredPlan(value[arrayIndex], path + "[]", depth + 1);
                if (arrayPlan) return arrayPlan;
              }
              return null;
            }
            if (typeof value !== "object") return null;
            var keys = Object.keys(value).slice(0, 100);
            for (var index = 0; index < keys.length; index += 1) {
              var key = keys[index];
              if (shouldSkipKey(key)) continue;
              var keyPath = path ? path + "." + key : key;
              var keyBooleanPlan = booleanPlanValue(keyPath, value[key]);
              if (keyBooleanPlan) return keyBooleanPlan;
              if (isPlanKey(key)) {
                var keyPlan = normalizedPlanValue(key);
                if (keyPlan) return keyPlan;
                var directPlan = normalizedPlanValue(value[key]);
                if (directPlan) return directPlan;
              }
              var nestedPlan = scanStructuredPlan(value[key], keyPath, depth + 1);
              if (nestedPlan) return nestedPlan;
            }
            return null;
          }
          function findStructuredPlan(rows) {
            for (var index = 0; index < rows.length; index += 1) {
              var row = safeText(rows[index]);
              if (!trustedPlanSourcePattern.test(row)) continue;
              var separator = row.indexOf("=");
              var candidate = separator >= 0 ? row.slice(separator + 1) : row;
              candidate = decodeJsonString(candidate);
              candidate = extractJsonCandidate(candidate);
              if (!candidate) continue;
              try {
                var plan = scanStructuredPlan(JSON.parse(candidate), "", 0);
                if (plan) return plan;
              } catch (error) {}
            }
            return null;
          }
          function findVisiblePaidPlan(text) {
            if (PROVIDER_ID !== "codex") return null;
            var codexPlans = "(Free|Go|Plus|Prolite|Pro|Team|Business|Enterprise|Edu)";
            var safeCodexPlanMatch = new RegExp("\\b(ChatGPT|profile|account|subscription|plan|\\ud504\\ub85c|\\uacc4\\uc815|\\uad6c\\ub3c5)[^\\n]{0,80}\\b" + codexPlans + "\\b", "i").exec(text);
            if (safeCodexPlanMatch) return safeText(safeCodexPlanMatch[2]);
            safeCodexPlanMatch = new RegExp("\\b" + codexPlans + "\\b[^\\n]{0,24}(?:plan|account|profile|\\uad6c\\ub3c5|\\uacc4\\uc815|\\ud504\\ub85c)", "i").exec(text);
            if (safeCodexPlanMatch) return safeText(safeCodexPlanMatch[1]);
            var match = new RegExp("\\b(ChatGPT|profile|account|subscription|plan|\\ud504\\ub85c\\ud544|\\uacc4\\uc815|\\uad6c\\ub3c5)[^\\n]{0,80}\\b" + codexPlans + "\\b", "i").exec(text);
            if (match) return safeText(match[2]);
            match = new RegExp("\\b" + codexPlans + "\\b[^\\n]{0,24}(?:\\ub2d8|plan|account|profile|\\uad6c\\ub3c5|\\uacc4\\uc815|\\ud504\\ub85c\\ud544)", "i").exec(text);
            return match ? safeText(match[1]) : null;
          }
          function findReset(text) {
            var match = /\b(?:resets?|reset)\b(?:\s+[^.!?;|,\n]{1,70})?/i.exec(text);
            return match ? safeText(match[0]) : null;
          }
          function toTitle(value, fallback) {
            var text = safeText(value).replace(/[:=]+$/g, '');
            if (!text || text.length < 2 || !/[A-Za-z]/.test(text)) text = fallback;
            if (text.length > 48) text = text.slice(0, 48);
            return text.charAt(0).toUpperCase() + text.slice(1);
          }
          function parseNumber(value) {
            if (typeof value === 'number') return isFinite(value) ? value : null;
            if (typeof value !== 'string') return null;
            var cleaned = value.replace(/,/g, '').trim();
            if (!cleaned) return null;
            var number = Number(cleaned);
            return isFinite(number) ? number : null;
          }
          function isNumber(value) {
            return typeof value === "number" && isFinite(value);
          }
          function inferUnit(text, fallback) {
            var lower = safeText(text).toLowerCase();
            if (/premium\s+requests?/.test(lower)) return "premium requests";
            if (/fast\s+requests?/.test(lower)) return "fast requests";
            if (/requests?/.test(lower)) return "requests";
            if (/messages?/.test(lower)) return "messages";
            if (/completions?/.test(lower)) return "completions";
            if (/credits?/.test(lower)) return "credits";
            if (/\busd\b|dollars?/.test(lower)) return "USD";
            if (/tokens?/.test(lower)) return "tokens";
            return safeText(fallback) || null;
          }
          function inferCategory(text, unit) {
            var lower = (safeText(text) + " " + safeText(unit)).toLowerCase();
            if (/premium\s+requests?/.test(lower)) return "premium_requests";
            if (/fast\s+requests?/.test(lower)) return "fast_requests";
            if (/included\s+usage|\busd\b|dollars?|credits?/.test(lower)) return "included_usage";
            if (/messages?|chat/.test(lower)) return "messages";
            if (/completions?/.test(lower)) return "completions";
            if (/tokens?/.test(lower)) return "tokens";
            if (/images?/.test(lower)) return "images";
            if (/videos?/.test(lower)) return "videos";
            return null;
          }
          function inferWindow(text) {
            var lower = safeText(text).toLowerCase();
            var rolling = /\b([0-9]+)\s*(m|min|minutes?|h|hr|hours?|d|days?)\b/.exec(lower);
            if (rolling) return rolling[1] + rolling[2].charAt(0);
            if (/daily|per day|today/.test(lower)) return "daily";
            if (/weekly|per week|this week/.test(lower)) return "weekly";
            if (/monthly|per month|this month/.test(lower)) return "monthly";
            return null;
          }
          function sourceLabel(context) {
            var path = safeText(location.pathname).slice(0, 80);
            var title = safeText(document.title).slice(0, 80);
            var contextText = safeText(context).slice(0, 80);
            return path || title || contextText || PROVIDER_ID;
          }
          function shouldSkipLimitCandidate(title, unit, context) {
            var labelText = [title, unit].map(safeText).join(" ").toLowerCase();
            var contextText = safeText(context).toLowerCase();
            if (/\b(sitemap|completed)\b/.test(labelText)) return true;
            if (PROVIDER_ID === "copilot") {
              var copilotText = labelText + " " + contextText;
              if (/\/features\/copilot\/plans/.test(copilotText)) return true;
              if (/\b[a-z0-9-]+\.(com|net|org|io|dev|ai)\b/.test(labelText)) return true;
              if (!/\b(copilot|premium\s+requests?|chat|messages?|code\s+completions?|completions?|usage|limit|quota|remaining|billing|entitlement)\b/.test(copilotText)) return true;
            }
            if (PROVIDER_ID === "claude" && /^(rate[_\s-]?limit|session|weekly)$/.test(labelText)) return true;
            if (
              /\b(sitemap|completed)\b/.test(contextText) &&
              !/\b(usage|limit|quota|remaining|request|message|credit|cap|billing|subscription|entitlement)\b/.test(labelText)
            ) return true;
            return false;
          }
          function metadata(title, unit, context, confidence) {
            var joined = [title, unit, context].map(safeText).join(" ");
            return {
              unit: inferUnit(joined, unit),
              category: inferCategory(joined, unit),
              window: inferWindow(joined),
              source: sourceLabel(context),
              confidence: confidence
            };
          }
          function pushLimit(limits, title, utilization, context) {
            var used = Number(utilization);
            if (!isFinite(used)) return;
            used = Math.max(0, Math.min(100, used));
            if (shouldSkipLimitCandidate(title, null, context)) return;
            var meta = metadata(title, null, context, 0.7);
            var limit = {
              l: toTitle(title, "Usage"),
              u: Math.round(used * 10) / 10,
              category: meta.category,
              unit: meta.unit,
              window: meta.window,
              source: meta.source,
              confidence: meta.confidence
            };
            var reset = findReset(context);
            if (reset) limit.t = reset;
            limits.push(limit);
          }
          function pushAmountLimit(limits, title, usedValue, limitValue, remainingValue, unitValue, context, confidence) {
            var cap = parseNumber(limitValue);
            var used = parseNumber(usedValue);
            var remaining = parseNumber(remainingValue);
            if (!isNumber(cap) || cap <= 0) return;
            if (!isNumber(used) && isNumber(remaining)) used = cap - remaining;
            if (!isNumber(remaining) && isNumber(used)) remaining = cap - used;
            if (!isNumber(used) || !isNumber(remaining)) return;
            used = Math.max(0, used);
            remaining = Math.max(0, remaining);
            if (shouldSkipLimitCandidate(title, unitValue, context)) return;
            var meta = metadata(title, unitValue, context, confidence || 0.85);
            var limit = {
              l: toTitle(title || unitValue, "Usage"),
              u: Math.round(Math.max(0, Math.min(100, (used / cap) * 100)) * 10) / 10,
              used: Math.round(used * 1000) / 1000,
              limit: Math.round(cap * 1000) / 1000,
              remaining: Math.round(remaining * 1000) / 1000,
              unit: meta.unit,
              category: meta.category,
              window: meta.window,
              source: meta.source,
              confidence: meta.confidence
            };
            var reset = findReset(context);
            if (reset) limit.t = reset;
            limits.push(limit);
          }
          function pushRemainingOnlyLimit(limits, title, remainingValue, unitValue, context, confidence) {
            var remaining = parseNumber(remainingValue);
            if (!isNumber(remaining)) return;
            remaining = Math.max(0, remaining);
            if (shouldSkipLimitCandidate(title, unitValue, context)) return;
            var meta = metadata(title, unitValue, context, confidence || 0.78);
            var limit = {
              l: toTitle(title || unitValue, "Usage"),
              remaining: Math.round(remaining * 1000) / 1000,
              unit: meta.unit,
              category: meta.category,
              window: meta.window,
              source: meta.source,
              confidence: meta.confidence
            };
            var reset = findReset(context);
            if (reset) limit.t = reset;
            limits.push(limit);
          }
          function cursorMoneyAmount(value) {
            var amount = parseNumber(value);
            if (!isNumber(amount)) return null;
            if (Math.abs(amount) >= 100 && Math.round(amount) === amount) {
              return amount / 100;
            }
            return amount;
          }
          function rememberCursorPlan(value, individualUsage) {
            if (PROVIDER_ID !== "cursor" || !isPlainObject(value)) return;
            var candidates = [
              propertyText(value, ["plan", "planName", "plan_name", "planLabel", "plan_label", "planType", "plan_type", "membershipType", "membership_type", "limitType", "limit_type", "subscription", "tier"])
            ];
            if (isPlainObject(value.planInfo)) {
              candidates.push(propertyText(value.planInfo, ["plan", "planName", "plan_name", "planLabel", "plan_label", "planType", "plan_type", "membershipType", "membership_type", "tier"]));
            }
            if (isPlainObject(value.plan)) {
              candidates.push(propertyText(value.plan, ["plan", "name", "label", "type", "planName", "plan_name", "planLabel", "plan_label", "planType", "plan_type", "tier"]));
            }
            if (isPlainObject(individualUsage)) {
              candidates.push(propertyText(individualUsage, ["plan", "planName", "plan_name", "planLabel", "plan_label", "planType", "plan_type", "membershipType", "membership_type", "limitType", "limit_type", "subscription", "tier"]));
              if (isPlainObject(individualUsage.plan)) {
                candidates.push(propertyText(individualUsage.plan, ["plan", "name", "label", "type", "planName", "plan_name", "planLabel", "plan_label", "planType", "plan_type", "tier"]));
              }
            }
            for (var index = 0; index < candidates.length; index += 1) {
              var plan = normalizedPlanValue(candidates[index]);
              if (plan) {
                rememberDerivedPlan(plan);
                return;
              }
            }
          }
          function isCursorPlanUsageObject(value) {
            return PROVIDER_ID === "cursor" &&
              isPlainObject(value) &&
              (
                propertyNumber(value, ["totalSpend", "total_spend", "totalUsage", "total_usage", "spend", "used", "usage"]) != null ||
                propertyNumber(value, ["totalPercentUsed", "total_percent_used", "percentUsed", "percent_used", "usedPercent", "used_percent"]) != null
              ) &&
              (
                propertyNumber(value, ["limit", "limitAmount", "limit_amount", "monthlyLimit", "monthly_limit", "totalLimit", "total_limit"]) != null ||
                propertyNumber(value, ["remaining", "remainingAmount", "remaining_amount", "totalRemaining", "total_remaining"]) != null ||
                propertyNumber(value, ["totalPercentUsed", "total_percent_used", "percentUsed", "percent_used", "usedPercent", "used_percent"]) != null
              );
          }
          function pushCursorUsageLine(limits, label, used, cap, remaining, unit, context, confidence, windowObject) {
            if (limits.length >= 8) return;
            if (isNumber(cap) && cap > 0 && (isNumber(used) || isNumber(remaining))) {
              pushAmountLimit(limits, label, used, cap, remaining, unit, context, confidence);
              attachWindowTimes(limits[limits.length - 1], windowObject || {});
              return;
            }
            if (isNumber(cap) && cap === 0 && (!isNumber(remaining) || remaining === 0) && (!isNumber(used) || used === 0)) {
              pushLimit(limits, label, 0, context);
              attachWindowTimes(limits[limits.length - 1], windowObject || {});
            }
          }
          function scanCursorPlanUsageObject(planUsage, path, source, limits, windowObject) {
            if (!isCursorPlanUsageObject(planUsage)) return false;
            var context = source + " " + path.join(".") + " planUsage billingCycle monthly USD";
            var used = cursorMoneyAmount(propertyNumber(planUsage, ["totalSpend", "total_spend", "totalUsage", "total_usage", "spend", "used", "usedAmount", "used_amount", "usage", "usageAmount", "usage_amount"]));
            var cap = cursorMoneyAmount(propertyNumber(planUsage, ["limit", "limitAmount", "limit_amount", "monthlyLimit", "monthly_limit", "totalLimit", "total_limit"]));
            var remaining = cursorMoneyAmount(propertyNumber(planUsage, ["remaining", "remainingAmount", "remaining_amount", "totalRemaining", "total_remaining", "balance"]));
            var percentUsed = propertyNumber(planUsage, ["totalPercentUsed", "total_percent_used", "percentUsed", "percent_used", "usedPercent", "used_percent", "usagePercent", "usage_percent"]);
            if (isNumber(cap) && cap > 0 && !isNumber(remaining) && isNumber(used)) {
              remaining = Math.max(0, cap - used);
            }
            if (isNumber(cap) && cap > 0 && (isNumber(used) || isNumber(remaining))) {
              pushCursorUsageLine(limits, "Total usage", used, cap, remaining, "USD", context, 0.96, windowObject || planUsage);
            } else if (isNumber(percentUsed)) {
              pushLimit(limits, "Total usage", percentUsed, context);
              attachWindowTimes(limits[limits.length - 1], windowObject || planUsage);
            } else {
              pushCursorUsageLine(limits, "Total usage", used, cap, remaining, "USD", context, 0.9, windowObject || planUsage);
            }

            var breakdowns = [
              { label: "Auto usage", keys: ["autoUsage", "auto_usage", "auto", "automatic", "includedAuto"] },
              { label: "API usage", keys: ["apiUsage", "api_usage", "api", "usageBasedApi"] },
              { label: "On-demand", keys: ["onDemand", "on_demand", "onDemandUsage", "on_demand_usage", "usageBased", "usage_based"] }
            ];
            breakdowns.forEach(function(entry) {
              if (limits.length >= 8) return;
              var object = null;
              for (var index = 0; index < entry.keys.length; index += 1) {
                var key = entry.keys[index];
                if (isPlainObject(planUsage[key])) {
                  object = planUsage[key];
                  break;
                }
              }
              if (!object && isPlainObject(planUsage.breakdown)) {
                for (var breakdownIndex = 0; breakdownIndex < entry.keys.length; breakdownIndex += 1) {
                  var breakdownKey = entry.keys[breakdownIndex];
                  if (isPlainObject(planUsage.breakdown[breakdownKey])) {
                    object = planUsage.breakdown[breakdownKey];
                    break;
                  }
                }
              }
              if (!object) return;
              var itemUsed = cursorMoneyAmount(propertyNumber(object, ["totalSpend", "total_spend", "spend", "used", "usedAmount", "used_amount", "usage", "usageAmount", "usage_amount"]));
              var itemCap = cursorMoneyAmount(propertyNumber(object, ["limit", "limitAmount", "limit_amount", "included", "includedAmount", "included_amount"]));
              var itemRemaining = cursorMoneyAmount(propertyNumber(object, ["remaining", "remainingAmount", "remaining_amount", "balance"]));
              if (isNumber(itemCap) && itemCap > 0 && !isNumber(itemRemaining) && isNumber(itemUsed)) {
                itemRemaining = Math.max(0, itemCap - itemUsed);
              }
              pushCursorUsageLine(
                limits,
                entry.label,
                itemUsed,
                itemCap,
                itemRemaining,
                "USD",
                context + " " + entry.label,
                0.9,
                windowObject || planUsage
              );
            });
            return true;
          }
          function scanCursorPlanUsage(value, path, source, limits) {
            if (PROVIDER_ID !== "cursor" || !isPlainObject(value)) return false;
            var found = false;
            if (isPlainObject(value.planUsage)) {
              found = scanCursorPlanUsageObject(value.planUsage, path.concat("planUsage"), source, limits, value) || found;
            }
            if (isPlainObject(value.usage) && isPlainObject(value.usage.planUsage)) {
              found = scanCursorPlanUsageObject(value.usage.planUsage, path.concat("usage", "planUsage"), source, limits, value) || found;
            }
            if (isCursorPlanUsageObject(value)) {
              found = scanCursorPlanUsageObject(value, path, source, limits, value) || found;
            }
            return found;
          }
          function cursorRequestMetric(value) {
            if (PROVIDER_ID !== "cursor" || !isPlainObject(value)) return null;
            var used = propertyNumber(value, ["numRequests", "num_requests", "requestUsage", "request_usage", "currentRequestUsage", "current_request_usage", "usedRequestUsage", "used_request_usage", "requests", "used", "usage"]);
            var cap = propertyNumber(value, ["maxRequestUsage", "max_request_usage", "maxRequests", "max_requests", "requestLimit", "request_limit", "limit", "limitAmount", "limit_amount"]);
            if (!isNumber(used) || !isNumber(cap)) return null;
            return {
              used: Math.max(0, used),
              cap: Math.max(0, cap)
            };
          }
          function scanCursorRequestUsage(value, path, source, limits) {
            if (PROVIDER_ID !== "cursor" || !isPlainObject(value) || limits.length >= 8) return false;
            var totalUsed = 0;
            var totalCap = 0;
            var found = false;
            var directMetric = cursorRequestMetric(value);
            if (directMetric) {
              totalUsed += directMetric.used;
              totalCap += directMetric.cap;
              found = true;
            }
            Object.keys(value).slice(0, 24).forEach(function(key) {
              if (shouldSkipKey(key)) return;
              var metric = cursorRequestMetric(value[key]);
              if (!metric) return;
              totalUsed += metric.used;
              totalCap += metric.cap;
              found = true;
            });
            if (!found) return false;
            var context = source + " " + path.join(".") + " requestUsage monthly requests";
            if (totalCap > 0) {
              pushAmountLimit(limits, "Total usage", totalUsed, totalCap, Math.max(0, totalCap - totalUsed), "requests", context, 0.92);
              attachWindowTimes(limits[limits.length - 1], value);
            } else {
              pushLimit(limits, "Total usage", 0, context);
              attachWindowTimes(limits[limits.length - 1], value);
            }
            return true;
          }
          function scanCursorUsageSummary(value, path, source, limits) {
            if (PROVIDER_ID !== "cursor" || !isPlainObject(value)) return;
            var individualUsage = value.individualUsage;
            rememberCursorPlan(value, individualUsage);
            if (!isPlainObject(individualUsage)) return;
            var plan = individualUsage.plan;
            if (isPlainObject(plan)) {
              var planLimit = propertyNumber(plan, ["limit", "included", "includedAmount", "included_amount"]);
              var planRemaining = propertyNumber(plan, ["remaining", "remainingAmount", "remaining_amount"]);
              if (planLimit == null && isPlainObject(plan.breakdown)) {
                planLimit = propertyNumber(plan.breakdown, ["included", "limit", "total"]);
              }
              if (planRemaining != null || planLimit != null) {
                var remaining = cursorMoneyAmount(planRemaining != null ? planRemaining : planLimit);
                var cap = cursorMoneyAmount(planLimit);
                var context = source + " " + path.join(".") + " billingCycle monthly";
                if (isNumber(cap) && cap > 0) {
                  pushAmountLimit(limits, "Total usage", null, cap, remaining, "USD", context, 0.96);
                } else if (isNumber(cap) && cap === 0 && (!isNumber(remaining) || remaining === 0)) {
                  pushLimit(limits, "Total usage", 0, context);
                } else {
                  pushRemainingOnlyLimit(limits, "Total usage", remaining != null ? remaining : 0, "USD", context, 0.9);
                }
                attachWindowTimes(limits[limits.length - 1], value);
              }
            }
            var onDemand = individualUsage.onDemand;
            if (isPlainObject(onDemand)) {
              var onDemandLimit = cursorMoneyAmount(propertyNumber(onDemand, ["limit", "included", "includedAmount", "included_amount"]));
              var onDemandRemaining = cursorMoneyAmount(propertyNumber(onDemand, ["remaining", "remainingAmount", "remaining_amount"]));
              if (onDemandRemaining != null || onDemandLimit != null) {
                if (isNumber(onDemandLimit) && onDemandLimit > 0) {
                  pushAmountLimit(limits, "On-demand usage", null, onDemandLimit, onDemandRemaining, "USD", source + " " + path.join(".") + " onDemand", 0.9);
                } else {
                  pushRemainingOnlyLimit(limits, "On-demand usage", onDemandRemaining != null ? onDemandRemaining : 0, "USD", source + " " + path.join(".") + " onDemand", 0.82);
                }
                attachWindowTimes(limits[limits.length - 1], value);
              }
            }
          }
          function propertyNumber(object, keys) {
            for (var index = 0; index < keys.length; index += 1) {
              var key = keys[index];
              if (object && Object.prototype.hasOwnProperty.call(object, key)) {
                var value = parseNumber(object[key]);
                if (isNumber(value)) return value;
              }
            }
            return null;
          }
          function propertyText(object, keys) {
            for (var index = 0; index < keys.length; index += 1) {
              var key = keys[index];
              if (object && Object.prototype.hasOwnProperty.call(object, key)) {
                var value = safeText(object[key]);
                if (value) return value;
              }
            }
            return null;
          }
          function propertyRaw(object, keys) {
            for (var index = 0; index < keys.length; index += 1) {
              var key = keys[index];
              if (object && Object.prototype.hasOwnProperty.call(object, key)) {
                var value = object[key];
                if (value !== null && value !== undefined && safeText(value)) return value;
              }
            }
            return null;
          }
          function isPlainObject(value) {
            return value && typeof value === "object" && !Array.isArray(value);
          }
          function labelForQuotaKey(key) {
            return safeText(key)
              .replace(/[_-]+/g, " ")
              .replace(/\b([a-z])/g, function(match) { return match.toUpperCase(); });
          }
          function scanSiblingQuotaMaps(value, path, source, limits) {
            if (!isPlainObject(value)) return;
            var limitsObject = value.limits || value.limit || value.usageLimits || value.usage_limits || value.caps || value.maximums;
            var remainingObject = value.remaining || value.remainingLimits || value.remaining_limits || value.left || value.available;
            var usedObject = value.used || value.usages || value.usage || value.consumed;
            if (!isPlainObject(limitsObject)) return;
            function pushQuotaPair(key, capValue, remainingValue, usedValue) {
              if (limits.length >= 8 || shouldSkipKey(key)) return;
              var cap = parseNumber(capValue);
              var remaining = parseNumber(remainingValue);
              var used = parseNumber(usedValue);
              if (!isNumber(cap) || (!isNumber(remaining) && !isNumber(used))) return;
              var label = labelForQuotaKey(key);
              pushAmountLimit(
                limits,
                label,
                used,
                cap,
                remaining,
                inferUnit(label, null),
                source + " " + path.concat(key).join("."),
                0.94
              );
            }
            Object.keys(limitsObject).forEach(function(key) {
              var capValue = limitsObject[key];
              var remainingValue = isPlainObject(remainingObject) ? remainingObject[key] : null;
              var usedValue = isPlainObject(usedObject) ? usedObject[key] : null;
              if (isPlainObject(capValue)) {
                Object.keys(capValue).forEach(function(childKey) {
                  var childRemaining = isPlainObject(remainingValue) ? remainingValue[childKey] : null;
                  var childUsed = isPlainObject(usedValue) ? usedValue[childKey] : null;
                  pushQuotaPair(key + " " + childKey, capValue[childKey], childRemaining, childUsed);
                });
              } else {
                pushQuotaPair(key, capValue, remainingValue, usedValue);
              }
            });
          }
          function resetDateToIso(value) {
            var text = safeText(value);
            if (!text) return null;
            if (/^\d{4}-\d{2}-\d{2}$/.test(text)) return text + "T00:00:00Z";
            return text;
          }
          function pushRemainingCounter(limits, title, remainingValue, unitValue, remainingPercentValue, limitValue, context, resetValue) {
            if (limits.length >= 8) return;
            var remaining = parseNumber(remainingValue);
            if (!isNumber(remaining)) return;
            var cap = parseNumber(limitValue);
            if (isNumber(cap) && cap > 0) {
              pushAmountLimit(limits, title, null, cap, remaining, unitValue, context, 0.98);
              var amountLast = limits[limits.length - 1];
              if (amountLast) {
                amountLast.window = "monthly";
                var amountReset = resetDateToIso(resetValue);
                if (amountReset) amountLast.r = amountReset;
              }
              return;
            }
            var percent = parseNumber(remainingPercentValue);
            if (isNumber(percent)) {
              var usedPercent = Math.max(0, Math.min(100, 100 - percent));
              pushLimit(limits, title, usedPercent, context);
              var last = limits[limits.length - 1];
              if (last) {
                last.remaining = Math.round(Math.max(0, remaining) * 1000) / 1000;
                last.unit = inferUnit(title + " " + unitValue, unitValue);
                last.category = inferCategory(title, last.unit);
                last.window = "monthly";
                last.source = sourceLabel(context);
                last.confidence = 0.98;
                var reset = resetDateToIso(resetValue);
                if (reset) last.r = reset;
              }
              return;
            }
            pushRemainingOnlyLimit(limits, title, remaining, unitValue, context, 0.98);
            var remainingLast = limits[limits.length - 1];
            if (remainingLast) {
              remainingLast.window = "monthly";
              var resetOnly = resetDateToIso(resetValue);
              if (resetOnly) remainingLast.r = resetOnly;
            }
          }
          function scanCopilotEntitlement(value, path, source, limits) {
            if (PROVIDER_ID !== "copilot" || !isPlainObject(value) || !isPlainObject(value.quotas)) return false;
            var quotas = value.quotas;
            var remaining = quotas.remaining;
            if (!isPlainObject(remaining)) return false;
            var quotaLimits = isPlainObject(quotas.limits) ? quotas.limits : {};
            var resetDate = quotas.resetDate || quotas.reset_date || quotas.resetAt || quotas.reset_at;
            var planText = safeText(value.plan || value.sku || value.licenseType || value.license_type).toLowerCase();
            var completionsRemaining = parseNumber(remaining.completions);
            var defaultCompletionsLimit = /(^|[^a-z])(free|licensed_limited)([^a-z]|$)/.test(planText) ||
              (isNumber(completionsRemaining) && completionsRemaining >= 0 && completionsRemaining <= 4000) ? 4000 : null;
            pushRemainingCounter(
              limits,
              "Chat",
              remaining.chat,
              "messages",
              remaining.chatPercentage,
              quotaLimits.chat || quotaLimits.messages,
              source + " " + path.concat("quotas", "remaining", "chat").join("."),
              resetDate
            );
            pushRemainingCounter(
              limits,
              "Completions",
              remaining.completions,
              "completions",
              remaining.completionsPercentage,
              quotaLimits.completions || quotaLimits.completion || quotaLimits.codeCompletions || quotaLimits.code_completions || defaultCompletionsLimit,
              source + " " + path.concat("quotas", "remaining", "completions").join("."),
              resetDate
            );
            var premiumLimit = parseNumber(quotaLimits.premiumInteractions);
            var premiumRemaining = parseNumber(remaining.premiumInteractions);
            var premiumRemainingPercent = parseNumber(remaining.premiumInteractionsPercentage);
            if ((isNumber(premiumLimit) && premiumLimit > 0) || (isNumber(premiumRemaining) && premiumRemaining > 0)) {
              pushAmountLimit(
                limits,
                "Premium requests",
                isNumber(premiumLimit) && isNumber(premiumRemaining) ? premiumLimit - premiumRemaining : null,
                premiumLimit,
                premiumRemaining,
                "premium requests",
                source + " " + path.concat("quotas", "premiumInteractions").join("."),
                0.98
              );
              var premiumLast = limits[limits.length - 1];
              if (premiumLast) {
                premiumLast.window = "monthly";
                if (isNumber(premiumRemainingPercent)) {
                  premiumLast.u = Math.max(0, Math.min(100, 100 - premiumRemainingPercent));
                }
                var premiumReset = resetDateToIso(resetDate);
                if (premiumReset) premiumLast.r = premiumReset;
              }
            }
            return true;
          }
          function geminiQuotaLabel(action) {
            switch (Number(action)) {
              case 3:
                return "Pro";
              case 4:
                return "Flash";
              case 5:
                return "Deep Research";
              default:
                return null;
            }
          }
          function geminiTimestampToIso(value) {
            if (!Array.isArray(value) || value.length < 1) return null;
            var seconds = parseNumber(value[0]);
            var nanos = parseNumber(value[1]);
            if (!isNumber(seconds) || seconds <= 0) return null;
            var millis = seconds * 1000 + (isNumber(nanos) ? Math.floor(nanos / 1000000) : 0);
            try {
              return new Date(millis).toISOString();
            } catch (error) {
              return null;
            }
          }
          function scanGeminiQuotaResponse(value, path, source, limits) {
            if (PROVIDER_ID !== "gemini" || !Array.isArray(value)) return false;
            var rows = [];
            function collect(node, depth) {
              if (!Array.isArray(node) || depth > 8) return;
              if (
                node.length >= 6 &&
                Array.isArray(node[0]) &&
                Array.isArray(node[3]) &&
                isNumber(parseNumber(node[4])) &&
                isNumber(parseNumber(node[5]))
              ) {
                rows.push(node);
                return;
              }
              node.forEach(function(child) { collect(child, depth + 1); });
            }
            collect(value, 0);
            if (!rows.length) return false;
            var order = { 3: 0, 4: 1, 5: 2 };
            rows
              .filter(function(row) {
                var action = parseNumber(row[0] && row[0][1]);
                var cap = parseNumber(row[5]);
                return isNumber(action) && geminiQuotaLabel(action) && isNumber(cap) && cap > 0;
              })
              .sort(function(left, right) {
                var leftAction = parseNumber(left[0] && left[0][1]);
                var rightAction = parseNumber(right[0] && right[0][1]);
                var leftOrder = Object.prototype.hasOwnProperty.call(order, leftAction) ? order[leftAction] : 100 + leftAction;
                var rightOrder = Object.prototype.hasOwnProperty.call(order, rightAction) ? order[rightAction] : 100 + rightAction;
                return leftOrder - rightOrder;
              })
              .forEach(function(row) {
                if (limits.length >= 8) return;
                var action = parseNumber(row[0] && row[0][1]);
                var remaining = parseNumber(row[4]);
                var cap = parseNumber(row[5]);
                if (!isNumber(action) || !isNumber(remaining) || !isNumber(cap) || cap <= 0) return;
                var label = geminiQuotaLabel(action);
                pushAmountLimit(
                  limits,
                  label,
                  null,
                  cap,
                  remaining,
                  "requests",
                  source + " CheckGeminiQuota action " + action,
                  0.97
                );
                var last = limits[limits.length - 1];
                if (last) {
                  last.window = "daily";
                  var reset = geminiTimestampToIso(row[3]);
                  if (reset) last.r = reset;
                  last.category = "usage_window";
                }
              });
            return true;
          }
          function pushClaudeUsageApiLimit(limits, label, utilizationValue, resetsAtValue, source, windowText) {
            var utilization = parseNumber(utilizationValue);
            if (!isNumber(utilization)) return;
            var used = Math.max(0, Math.min(100, utilization));
            var reset = resetDateToIso(resetsAtValue);
            var line = {
              l: label,
              u: Math.round(used * 10) / 10,
              category: "usage_window",
              window: windowText,
              source: source,
              confidence: 0.98
            };
            if (reset) line.r = reset;
            for (var index = limits.length - 1; index >= 0; index -= 1) {
              if (safeText(limits[index] && limits[index].l).toLowerCase() === label.toLowerCase()) {
                limits.splice(index, 1);
              }
            }
            limits.unshift(line);
          }
          function scanClaudeOrganizationUsage(value, source, limits) {
            if (PROVIDER_ID !== "claude" || !isPlainObject(value)) return false;
            if (!/\/api\/organizations\/:id\/usage/.test(source)) return false;
            var matched = false;
            [
              { key: "seven_day_omelette", label: "Seven_day_omelette", window: "7 days" },
              { key: "seven_day", label: "Seven_day", window: "7 days" },
              { key: "five_hour", label: "Five_hour", window: "5 hours" }
            ].forEach(function(metric) {
              var item = value[metric.key];
              if (!isPlainObject(item)) return;
              pushClaudeUsageApiLimit(limits, metric.label, item.utilization, item.resets_at || item.resetsAt, source, metric.window);
              matched = true;
            });
            return matched;
          }
          function attachWindowTimes(limit, object) {
            if (!limit || !object) return limit;
            var start = propertyRaw(object, ["s", "startsAt", "starts_at", "startAt", "start_at", "startTime", "start_time", "periodStart", "period_start", "windowStart", "window_start", "billingCycleStart", "billing_cycle_start", "startOfMonth", "start_of_month"]);
            var reset = propertyRaw(object, ["r", "resetsAt", "resets_at", "resetAt", "reset_at", "resetTime", "reset_time", "periodEnd", "period_end", "windowEnd", "window_end", "expiresAt", "expires_at", "billingCycleEnd", "billing_cycle_end", "endOfMonth", "end_of_month"]);
            if (start !== null && start !== undefined) limit.s = start;
            if (reset !== null && reset !== undefined) limit.r = reset;
            return limit;
          }
          function scanJson(value, path, source, limits, depth) {
            if (limits.length >= 8 || depth > 10 || value == null) return;
            if (typeof value === "string") {
              var stringCandidate = extractJsonCandidate(value);
              if (!stringCandidate) return;
              try {
                scanJson(JSON.parse(stringCandidate), path.concat("json"), source, limits, depth + 1);
              } catch (error) {}
              return;
            }
            if (Array.isArray(value)) {
              if (scanGeminiQuotaResponse(value, path, source, limits)) return;
              value.forEach(function(item, index) {
                scanJson(item, path.concat(String(index)), source, limits, depth + 1);
              });
              return;
            }
            if (typeof value !== "object") return;
            if (PROVIDER_ID === "cursor") {
              rememberCursorPlan(value, value.individualUsage);
              scanCursorPlanUsage(value, path, source, limits);
              if (scanCursorRequestUsage(value, path, source, limits)) return;
              if (isCursorPlanUsageObject(value) || cursorRequestMetric(value)) return;
            }
            if (scanCopilotEntitlement(value, path, source, limits)) return;
            var title = propertyText(value, ["l", "label", "name", "title", "type", "category", "model", "display_name", "plan_type", "product", "sku", "feature", "feature_name", "slug", "key"]) || path[path.length - 1] || "Usage";
            var unit = propertyText(value, ["unit", "units", "unitType", "unit_type", "quotaUnit", "quota_unit", "displayUnit", "display_unit", "metric"]);
            var used = propertyNumber(value, ["used", "usedAmount", "used_amount", "usedCount", "used_count", "usage", "usageAmount", "usage_amount", "currentUsage", "current_usage", "current", "consumed", "spent", "quantity", "grossQuantity", "netQuantity", "usage_count", "num_used", "message_count", "messages_used", "used_messages", "request_count", "requests_used", "requestUsage", "request_usage", "currentRequestUsage", "current_request_usage", "usedRequestUsage", "used_request_usage", "numRequests", "num_requests", "requests", "credit_used", "credits_used"]);
            var cap = propertyNumber(value, ["limit", "limitAmount", "limit_amount", "limitCount", "limit_count", "usageLimit", "usage_limit", "quota", "cap", "max", "maximum", "total", "included", "allowance", "includedQuantity", "included_quantity", "includedAllowance", "included_allowance", "totalAvailable", "total_available", "granted", "grantedQuantity", "granted_quantity", "message_cap", "messageCap", "message_limit", "max_messages", "requestLimit", "request_limit", "maxRequests", "max_requests", "maxRequestUsage", "max_request_usage", "maxPremiumRequests", "max_premium_requests", "monthly_limit", "weekly_limit", "daily_limit", "rateLimit", "rate_limit", "hard_limit", "soft_limit", "hard_limit_usd", "soft_limit_usd", "credit_limit", "credits_total", "total_credits"]);
            var remaining = propertyNumber(value, ["remaining", "remainingAmount", "remaining_amount", "amount_remaining", "remainingCount", "remaining_count", "left", "available", "availableAmount", "available_amount", "balance", "remainingBalance", "remaining_balance", "balanceRemaining", "balance_remaining", "remainingQuantity", "remaining_quantity", "remainingCap", "remaining_cap", "capRemaining", "cap_remaining", "remaining_messages", "messages_remaining", "remaining_requests", "requests_remaining", "remainingRequestUsage", "remaining_request_usage", "requestUsageRemaining", "request_usage_remaining", "remaining_limit", "limit_remaining", "remaining_credits", "credits_remaining", "available_credits"]);
            scanSiblingQuotaMaps(value, path, source, limits);
            scanCursorUsageSummary(value, path, source, limits);
            if (cap != null && (used != null || remaining != null)) {
              pushAmountLimit(limits, title, used, cap, remaining, unit, source + " " + path.join("."), 0.92);
              attachWindowTimes(limits[limits.length - 1], value);
            }
            if (PROVIDER_ID === "cursor" && cap == null && used == null) {
              var remainingCap = propertyNumber(value, ["remainingCap", "remaining_cap", "capRemaining", "cap_remaining"]);
              if (remainingCap != null) {
                var capWindowLabel = propertyText(value, ["capWindowLabel", "cap_window_label", "windowLabel", "window_label", "limitType", "limit_type"]);
                pushRemainingOnlyLimit(
                  limits,
                  "Included usage",
                  remainingCap,
                  "USD",
                  source + " " + path.join(".") + " " + (capWindowLabel || ""),
                  0.84
                );
                attachWindowTimes(limits[limits.length - 1], value);
              }
            }
            if (
              cap == null &&
              used == null &&
              remaining != null &&
              !(PROVIDER_ID === "cursor" && propertyNumber(value, ["remainingCap", "remaining_cap", "capRemaining", "cap_remaining"]) != null)
            ) {
              pushRemainingOnlyLimit(limits, title, remaining, unit, source + " " + path.join("."), 0.78);
              attachWindowTimes(limits[limits.length - 1], value);
            }
            var utilization = propertyNumber(value, ["u", "utilization", "usagePercent", "usage_percent", "usedPercent", "used_percent", "percentUsed", "percent_used", "usedPercentage", "used_percentage"]);
            if (utilization != null && cap == null && used == null && remaining == null) {
              pushLimit(limits, title, utilization, source + " " + path.join("."));
              attachWindowTimes(limits[limits.length - 1], value);
            }
            Object.keys(value).forEach(function(key) {
              if (shouldSkipKey(key)) return;
              if (PROVIDER_ID === "cursor" && /^planUsage$/i.test(key) && isPlainObject(value[key])) return;
              scanJson(value[key], path.concat(key), source, limits, depth + 1);
            });
          }
          function extractJsonLimits(rows) {
            var limits = [];
            rows.forEach(function(row) {
              if (limits.length >= 8) return;
              var text = safeText(row);
              var separator = text.indexOf("=");
              var candidate = separator >= 0 ? text.slice(separator + 1) : text;
              candidate = decodeJsonString(candidate);
              candidate = extractJsonCandidate(candidate);
              if (!candidate) return;
              try {
                scanJson(JSON.parse(candidate), ["root"], text.slice(0, separator > 0 ? separator : 80), limits, 0);
              } catch (error) {}
            });
            return limits;
          }
          function decodeJsonString(value) {
            var decoded = (value || '').toString().trim();
            for (var depth = 0; depth < 2; depth += 1) {
              if (!decoded || decoded.charAt(0) !== "\"") return decoded;
              try {
                var parsed = JSON.parse(decoded);
                if (typeof parsed !== "string") return decoded;
                decoded = parsed.trim();
              } catch (error) {
                return decoded;
              }
            }
            return decoded;
          }
          function extractJsonCandidate(value) {
            var candidate = decodeJsonString(value);
            if (!candidate) return null;
            var objectIndex = candidate.indexOf("{");
            var arrayIndex = candidate.indexOf("[");
            var start = objectIndex >= 0 && arrayIndex >= 0
              ? Math.min(objectIndex, arrayIndex)
              : Math.max(objectIndex, arrayIndex);
            if (start < 0) return null;
            candidate = candidate.slice(start);
            var open = candidate.charAt(0);
            var close = open === "{" ? "}" : "]";
            var depth = 0;
            var inString = false;
            var escaping = false;
            for (var index = 0; index < candidate.length; index += 1) {
              var char = candidate.charAt(index);
              if (escaping) {
                escaping = false;
                continue;
              }
              if (char === "\\") {
                escaping = inString;
                continue;
              }
              if (char === "\"") {
                inString = !inString;
                continue;
              }
              if (inString) continue;
              if (char === open) depth += 1;
              if (char === close) {
                depth -= 1;
                if (depth === 0) return candidate.slice(0, index + 1).trim();
              }
            }
            return candidate.trim();
          }
          function extractLimits(text) {
            var limits = [];
            var percent = /([A-Za-z][A-Za-z0-9+ ._-]{0,48})?[^.\n]{0,100}\b([0-9]{1,3}(?:\.[0-9]+)?)\s*%\s*(left|remaining|used)\b[^.\n]{0,100}/gi;
            var match;
            while ((match = percent.exec(text)) && limits.length < 8) {
              var amount = Number(match[2]);
              if (!isFinite(amount)) continue;
              var word = safeText(match[3]).toLowerCase();
              var utilization = word === "used" ? amount : 100 - amount;
              pushLimit(limits, match[1], utilization, match[0]);
            }
            var remainingQuota = /([A-Za-z][A-Za-z0-9+ ._-]{0,48})?[^.\n]{0,100}\b([0-9][0-9,]*(?:\.[0-9]+)?)\s*(?:\/|of)\s*([0-9][0-9,]*(?:\.[0-9]+)?)(?:\s+([A-Za-z][A-Za-z -]{0,40}?))?\s*(?:left|remaining)\b[^.\n]{0,100}/gi;
            while ((match = remainingQuota.exec(text)) && limits.length < 8) {
              pushAmountLimit(limits, match[4] || match[1], null, match[3], match[2], match[4], match[0], 0.9);
            }
            var quota = /([A-Za-z][A-Za-z0-9+ ._-]{0,48})?[^.\n]{0,100}\b([0-9][0-9,]*(?:\.[0-9]+)?)\s*(?:\/|of)\s*([0-9][0-9,]*(?:\.[0-9]+)?)(?:\s+([A-Za-z][A-Za-z -]{0,40}?))?\s*(?:used)?\b[^.\n]{0,100}/gi;
            while ((match = quota.exec(text)) && limits.length < 8) {
              var used = Number(match[2].replace(/,/g, ""));
              var cap = Number(match[3].replace(/,/g, ""));
              if (!isFinite(used) || !isFinite(cap) || cap <= 0) continue;
              pushAmountLimit(limits, match[4] || match[1], used, cap, null, match[4], match[0], 0.85);
            }
            var safeKoreanRemaining = /([A-Za-z\uAC00-\uD7A30-9+ ._-]{0,48})?[^.\n]{0,100}\b([0-9][0-9,]*(?:\.[0-9]+)?)\s*(\uac1c|\uba54\uc2dc\uc9c0|\uc694\uccad|\ud06c\ub808\ub527|messages?|requests?|credits?)\s*(\ub0a8\uc74c|\ub0a8\uc558\uc2b5\ub2c8\ub2e4|\ub0a8\uc558\uc5b4\uc694|\uc794\uc5ec|\uc0ac\uc6a9 \uac00\ub2a5)\b[^.\n]{0,100}/gi;
            while ((match = safeKoreanRemaining.exec(text)) && limits.length < 8) {
              pushRemainingOnlyLimit(limits, match[1] || match[3], match[2], match[3], match[0], 0.76);
            }
            var koreanRemaining = /([A-Za-z가-힣0-9+ ._-]{0,48})?[^.\n]{0,100}\b([0-9][0-9,]*(?:\.[0-9]+)?)\s*(회|개|메시지|요청|크레딧|messages?|requests?|credits?)\s*(남음|남았습니다|남았어요|잔여|사용 가능)\b[^.\n]{0,100}/gi;
            while ((match = koreanRemaining.exec(text)) && limits.length < 8) {
              pushRemainingOnlyLimit(limits, match[1] || match[3], match[2], match[3], match[0], 0.76);
            }
            return limits;
          }
          function hasLoginPrompt(text) {
            var value = safeText(text).toLowerCase();
            if (!value) return false;
            var safeCredentialFormPattern = /(username or email|username or email address|email address|password|\uc774\uba54\uc77c \uc8fc\uc18c|\uc774\uba54\uc77c \ub610\ub294 \uc804\ud654 \ubc88\ud638|\ube44\ubc00\ubc88\ud638)/i;
            var safeExplicitLoginPattern = /(log in to|login to|sign in to|sign up for|continue with google|continue with apple|\ub85c\uadf8\uc778 \ub610\ub294 \ud68c\uc6d0\uac00\uc785|google \uacc4\uc815\uc73c\ub85c \uacc4\uc18d|apple \uacc4\uc815\uc73c\ub85c \uacc4\uc18d|google\ub85c \uacc4\uc18d|apple\ub85c \uacc4\uc18d)/i;
            var safeVerificationPattern = /(2-step verification|two-factor authentication|verification code|\uc778\uc99d \ucf54\ub4dc)/i;
            if (
              (safeCredentialFormPattern.test(value) && safeExplicitLoginPattern.test(value)) ||
              (safeVerificationPattern.test(value) && safeExplicitLoginPattern.test(value))
            ) return true;
            var credentialFormPattern = /(username or email|username or email address|email address|password|이메일 주소|휴대 전화 번호|비밀번호)/i;
            var explicitLoginPattern = /(log in to|login to|sign in to|sign up for|continue with google|continue with apple|로그인 또는 회원가입|google 계정으로 계속|apple 계정으로 계속)/i;
            var verificationPattern = /(2-step verification|two-factor authentication|verification code|인증 코드)/i;
            return (
              credentialFormPattern.test(value) && explicitLoginPattern.test(value)
            ) || (
              verificationPattern.test(value) && explicitLoginPattern.test(value)
            );
          }
          function domMatches(selector) {
            try {
              return !!document.querySelector(selector);
            } catch (error) {
              return false;
            }
          }
          function authenticatedAppShellMarker(text) {
            if (hasLoginPrompt(text)) return false;
            var value = safeText(text).toLowerCase();
            if (PROVIDER_ID === "claude" && /(new chat|recent chats|message claude|projects|artifacts|\uc0c8 \ucc44\ud305|\ucd5c\uadfc \ucc44\ud305|claude\uc640 \ud568\uaed8 \ucee4\ud53c \ud55c\uc794|\ub2ec\ube5b \uc544\ub798 \ub300\ud654\ud560\uae4c\uc694|\uc624\ub298 \uc5b4\ub5a4 \ub3c4\uc6c0\uc744 \ub4dc\ub9b4\uae4c\uc694|sonnet|claude\uc5d0\uac8c \uba54\uc2dc\uc9c0)/i.test(value)) return true;
            if (PROVIDER_ID === "gemini" && /(ask gemini|chat with gemini|recent chats|gemini\uc640\uc758 \ub300\ud654|[\uac00-\ud7a3A-Za-z0-9._ -]{1,32}\ub2d8, \uc548\ub155\ud558\uc138\uc694|gemini\uc5d0\uac8c \ubb3c\uc5b4|\uacc4\ud68d, \ud559\uc2b5, \uc544\uc774\ub514\uc5b4|\uc0c8 \ucc44\ud305|\ucd5c\uadfc \ucc44\ud305)/i.test(value)) return true;
            if (PROVIDER_ID === "claude") {
              return /(new chat|recent chats|message claude|projects|artifacts|새 채팅|무엇을 도와)/i.test(value) ||
                domMatches('[data-testid*="composer"],[data-testid*="account"],[data-testid*="sidebar"],button[aria-label*="account"],button[aria-label*="profile"]');
            }
            if (PROVIDER_ID === "codex") {
              return /(message chatgpt|new chat|library|projects|temporary chat|chatgpt\s*(?:\uc5d0|\uc5d0\uac8c)\s*\uba54\uc2dc\uc9c0|\uba54\uc2dc\uc9c0 chatgpt|\uc0c8 \ucc44\ud305|\ub77c\uc774\ube0c\ub7ec\ub9ac|\ud504\ub85c\uc81d\ud2b8|\uc784\uc2dc \ucc44\ud305)/i.test(value) ||
                domMatches('[data-testid*="conversation"],[data-testid*="composer"],[data-testid*="sidebar"],[data-testid*="profile"],[data-testid*="account"],textarea,[contenteditable="true"],button[aria-label*="account"],button[aria-label*="profile"]');
            }
            if (PROVIDER_ID === "codex") {
              return /(message chatgpt|ask anything|what can i help|new chat|library|projects|temporary chat|chatgpt에게 메시지|무엇이든 물어)/i.test(value) ||
                domMatches('[data-testid*="sidebar"],[data-testid*="profile"],[data-testid*="account"],button[aria-label*="account"],button[aria-label*="profile"]');
            }
            if (PROVIDER_ID === "gemini") {
              return /(ask gemini|chat with gemini|recent chats|gemini에게 물어|새 채팅)/i.test(value) ||
                domMatches('[aria-label*="account"],button[aria-label*="account"],[data-testid*="account"]');
            }
            if (PROVIDER_ID === "copilot") {
              return /(copilot settings|premium request|billing and licensing|copilot premium|settings)/i.test(value) ||
                domMatches('[data-testid*="settings"],[data-testid*="billing"],summary[aria-label*="View profile"]');
            }
            if (PROVIDER_ID === "cursor") {
              return /(dashboard|usage|settings|account|workspace)/i.test(value) ||
                domMatches('[data-testid*="dashboard"],[data-testid*="settings"],[data-testid*="usage"]');
            }
            return false;
          }
          function hasSuccessfulEndpoint(pathPattern) {
            try {
              var summaries = endpointSummaries();
              for (var index = 0; index < summaries.length; index += 1) {
                var summary = summaries[index] || {};
                var endpoint = safeText(summary.e);
                var status = Number(summary.s);
                if (status >= 200 && status < 300 && pathPattern.test(endpoint)) return true;
              }
            } catch (error) {}
            return false;
          }
          function authenticatedEndpointMarker() {
            if (PROVIDER_ID === "cursor") {
              return hasSuccessfulEndpoint(/^\/(?:api\/(?:usage|auth\/me)|auth\/(?:usage|me))$/);
            }
            return false;
          }
          function hostEndsWith(expectedHost) {
            try {
              var host = location.hostname.toLowerCase();
              return host === expectedHost || host.slice(-(expectedHost.length + 1)) === "." + expectedHost;
            } catch (error) {
              return false;
            }
          }
          function isProviderOrigin() {
            if (PROVIDER_ID === "claude") return hostEndsWith("claude.ai");
            if (PROVIDER_ID === "codex") return hostEndsWith("chatgpt.com") || hostEndsWith("chat.openai.com");
            if (PROVIDER_ID === "gemini") return hostEndsWith("gemini.google.com") || hostEndsWith("gemini.google") || hostEndsWith("one.google.com");
            if (PROVIDER_ID === "copilot") return hostEndsWith("github.com") || hostEndsWith("githubcopilot.com");
            if (PROVIDER_ID === "cursor") return hostEndsWith("cursor.com") || hostEndsWith("cursor.sh");
            return false;
          }
          function providerEndpointCandidates() {
            if (PROVIDER_ID === "claude") {
              return [
                "/api/bootstrap",
                "/api/organizations",
                "/api/organizations/me",
                "/api/account_profile",
                "/api/account",
                "/api/profile",
                "/api/settings",
                "/api/usage",
                "/api/usage_limits",
                "/api/limits",
                "/api/subscription",
                "/api/billing"
              ];
            }
            if (PROVIDER_ID === "codex") {
              return [
                "/backend-anon/accounts/check/v4-2023-04-27",
                "/backend-api/accounts/check/v4-2023-04-27",
                "/backend-api/codex/usage",
                "/backend-api/wham/usage",
                "/backend-api/me",
                "/backend-api/models",
                "/backend-api/settings/user",
                "/backend-api/accounts/default/features",
                "/backend-api/accounts/default/subscription",
                "/backend-api/accounts/default/billing",
                "/backend-api/accounts/default/usage",
                "/backend-api/accounts/default/usage_limits",
                "/backend-api/accounts/default/message_limits",
                "/backend-api/accounts/default/limits",
                "/backend-api/accounts/default/rate_limits",
                "/backend-api/subscriptions",
                "/backend-api/subscriptions/active",
                "/backend-api/user_system_messages",
                "/backend-api/conversation_limit"
              ];
            }
            if (PROVIDER_ID === "gemini") {
              if (hostEndsWith("one.google.com")) {
                return [
                  "/settings",
                  "/settings?hl=ko",
                  "/explore-plan/gemini-advanced",
                  "/explore-plan/gemini-advanced?hl=ko"
                ];
              }
              if (hostEndsWith("gemini.google")) {
                return [
                  "/subscriptions",
                  "/subscriptions?hl=ko"
                ];
              }
              return ["/app", "/app/settings", "/app/u/0", "/app?hl=ko"];
            }
            if (PROVIDER_ID === "copilot") {
              return [
                "/github-copilot/chat/entitlement",
                "/github-copilot/chat",
                "/settings/copilot",
                "/settings/copilot/features",
                "/settings/copilot/billing",
                "/settings/copilot/usage",
                "/settings/copilot/usage_metrics",
                "/settings/copilot/plans",
                "/settings/billing",
                "/settings/billing/usage"
              ];
            }
            if (PROVIDER_ID === "cursor") {
              if (hostEndsWith("api2.cursor.sh")) {
                return [
                  "/auth/usage",
                  "/auth/me"
                ];
              }
              return [
                "/dashboard",
                "/dashboard/usage",
                "/settings",
                "/api/dashboard",
                "/api/usage",
                "/api/usage-summary",
                "/api/subscription",
                "/api/billing",
                "/api/settings",
                "/api/me",
                "/api/auth/session",
                "/api/limits",
                "/api/dashboard/get-plan-info",
                "/api/dashboard/get-current-billing-cycle",
                "/api/dashboard/get-credit-grants-balance"
              ];
            }
            return [];
          }
          function emitResponse(response) {
            try {
              if (
                window.AIUsageLocalCollector &&
                typeof window.AIUsageLocalCollector.onUsagePayload === "function"
              ) {
                window.AIUsageLocalCollector.onUsagePayload(JSON.stringify(response));
              }
            } catch (error) {}
          }
          function buildResponse() {
            var visibleText = safeText(document.body && document.body.innerText);
            var rows = [
              'url=' + location.href,
              'title=' + document.title,
              visibleText
            ];
            rows = rows
              .concat(collectScripts())
              .concat(collectStorage(safeStorage('localStorage'), 'localStorage'))
              .concat(collectStorage(safeStorage('sessionStorage'), 'sessionStorage'))
              .concat(window.__AI_USAGE_PROVIDER_EVENTS__ || []);
            var combinedText = rows.filter(Boolean).join('\n');
            var limits = derivedLimits().concat(extractJsonLimits(rows)).concat(extractLimits(combinedText)).slice(0, 8);
            var plan = derivedPlans()[0] || findTrustedPlan(rows) || findStructuredPlan(rows) || findVisiblePaidPlan(visibleText);
            var providerPage = PROVIDER_LABEL_PATTERN.test(combinedText);
            var authenticatedApp = authenticatedAppShellMarker(visibleText) || authenticatedEndpointMarker();
            return {
              s: (limits.length > 0 || plan || (authenticatedApp && !hasLoginPrompt(visibleText))) ? "s" : "e",
              provider: PROVIDER_ID,
              c: {
                login: hasLoginPrompt(visibleText),
                providerPage: providerPage || authenticatedApp,
                authenticatedApp: authenticatedApp,
                textLength: visibleText.length
              },
              d: {
                p: plan,
                x: limits,
                l: Date.now()
              },
              q: endpointSummaries().slice(-8),
              m: limits.length > 0 || plan ? null : "No usage limits found in local provider session."
            };
          }
          function rememberIndexedDbValue(label, value) {
            try {
              var text = stringifyValue(value);
              if (!text || text.length > 200000) return false;
              if (!KEYWORD_PATTERN.test(label) && !KEYWORD_PATTERN.test(text.slice(0, 20000))) return false;
              rememberSignal("indexedDB:" + label + "=" + text.slice(0, 20000));
              return true;
            } catch (error) {
              return false;
            }
          }
          function collectIndexedDbSignals() {
            try {
              if (!isProviderOrigin()) return;
              if (window.__AI_USAGE_INDEXEDDB_STARTED__) return;
              if (!window.indexedDB || typeof window.indexedDB.databases !== "function") return;
              window.__AI_USAGE_INDEXEDDB_STARTED__ = true;
              window.indexedDB.databases().then(function(databases) {
                (databases || []).slice(0, 8).forEach(function(databaseInfo) {
                  var databaseName = databaseInfo && databaseInfo.name;
                  if (!databaseName || shouldSkipKey(databaseName)) return;
                  var openRequest = window.indexedDB.open(databaseName);
                  openRequest.onsuccess = function() {
                    var database = openRequest.result;
                    try {
                      var storeNames = Array.prototype.slice.call(database.objectStoreNames || []).slice(0, 20);
                      if (!storeNames.length) {
                        database.close();
                        return;
                      }
                      var transaction = database.transaction(storeNames, "readonly");
                      var remembered = 0;
                      transaction.oncomplete = function() {
                        try { database.close(); } catch (error) {}
                        if (remembered > 0) emitResponse(buildResponse());
                      };
                      transaction.onerror = function() {
                        try { database.close(); } catch (error) {}
                      };
                      storeNames.forEach(function(storeName) {
                        if (shouldSkipKey(storeName)) return;
                        try {
                          var cursorRequest = transaction.objectStore(storeName).openCursor();
                          var scanned = 0;
                          cursorRequest.onsuccess = function(event) {
                            var cursor = event.target.result;
                            if (!cursor || scanned >= 30 || remembered >= 40) return;
                            scanned += 1;
                            if (rememberIndexedDbValue(databaseName + ":" + storeName, cursor.value)) {
                              remembered += 1;
                            }
                            cursor.continue();
                          };
                        } catch (error) {}
                      });
                    } catch (error) {
                      try { database.close(); } catch (closeError) {}
                    }
                  };
                });
              }).catch(function() {});
            } catch (error) {}
          }
          function extractAccountIds(text) {
            var ids = [];
            var seen = {};
            function isLikelyAccountId(id) {
              var value = safeText(id);
              if (!value) return false;
              if (/^(account|accounts|account-owner|account_user_id|owner|default|me|user|users)$/i.test(value)) return false;
              if (/^acct_[A-Za-z0-9_-]{8,}$/.test(value)) return true;
              if (/^[0-9a-fA-F-]{16,}$/.test(value)) return true;
              if (/^[A-Za-z0-9_-]{18,}$/.test(value) && !/account|owner|default|user/i.test(value)) return true;
              return false;
            }
            function remember(id) {
              var value = safeText(id);
              if (!isLikelyAccountId(value) || seen[value] || ids.length >= 3) return;
              seen[value] = true;
              ids.push(value);
            }
            try {
              var accountIdPattern = /"account_id"\s*:\s*"([^"]+)"/gi;
              var accountIdMatch;
              while ((accountIdMatch = accountIdPattern.exec(text))) {
                remember(accountIdMatch[1]);
              }
              [
                /"accountId"\s*:\s*"([^"]+)"/gi,
                /"accountID"\s*:\s*"([^"]+)"/gi,
                /"default_account_id"\s*:\s*"([^"]+)"/gi,
                /"current_account_id"\s*:\s*"([^"]+)"/gi,
                /"account"\s*:\s*\{[^}]{0,500}"id"\s*:\s*"([^"]+)"/gi,
                /"accounts"\s*:\s*\[[^\]]{0,2000}?"id"\s*:\s*"([^"]+)"/gi,
                /\/backend-api\/accounts\/([^/"?#\s]+)/g
              ].forEach(function(pattern) {
                var match;
                while ((match = pattern.exec(text))) {
                  remember(match[1]);
                }
              });
              var accountSlugPattern = /\bacct_[A-Za-z0-9_-]+\b/g;
              var accountSlugMatch;
              while ((accountSlugMatch = accountSlugPattern.exec(text))) {
                remember(accountSlugMatch[0]);
              }
              var accountPathSlugPattern = /\baccount[-_][A-Za-z0-9_-]+\b/g;
              var accountPathSlugMatch;
              while ((accountPathSlugMatch = accountPathSlugPattern.exec(text))) {
                remember(accountPathSlugMatch[0]);
              }
            } catch (error) {}
            return ids;
          }
          function extractClaudeOrganizationIds(text) {
            var ids = [];
            var seen = {};
            function remember(id) {
              var value = safeText(id);
              if (!value || seen[value] || ids.length >= 3) return;
              seen[value] = true;
              ids.push(value);
            }
            try {
              [
                /"organization_uuid"\s*:\s*"([^"]+)"/gi,
                /"active_organization_uuid"\s*:\s*"([^"]+)"/gi,
                /"current_organization_uuid"\s*:\s*"([^"]+)"/gi,
                /"organizationId"\s*:\s*"([^"]+)"/gi,
                /"organization_id"\s*:\s*"([^"]+)"/gi,
                /"lastActiveOrg"\s*:\s*"([0-9a-fA-F-]{16,})"/gi,
                /(?:^|;\s*)lastActiveOrg=([0-9a-fA-F-]{16,})/g,
                /"organizations"\s*:\s*\[[^\]]{0,2000}?"uuid"\s*:\s*"([0-9a-fA-F-]{16,})"/gi,
                /"uuid"\s*:\s*"([0-9a-fA-F-]{16,})"/gi,
                /\/api\/organizations\/([0-9a-fA-F-]{16,})/g
              ].forEach(function(pattern) {
                var match;
                while ((match = pattern.exec(text))) {
                  remember(match[1]);
                }
              });
            } catch (error) {}
            return ids;
          }
          function fetchEndpoint(endpoint, requestHeaders) {
            try {
              if (!isProviderOrigin()) return;
              if (typeof fetch !== "function") {
                rememberEndpointError(endpoint, -2);
                return;
              }
              if (!window.__AI_USAGE_FETCHED_ENDPOINTS__) window.__AI_USAGE_FETCHED_ENDPOINTS__ = {};
              var targetUrl = new URL(endpoint, location.origin).toString();
              var headers = requestHeaders || {};
              var headerKey = Object.keys(headers).sort().join(",");
              var dedupeKey = targetUrl + "|" + headerKey;
              if (window.__AI_USAGE_FETCHED_ENDPOINTS__[dedupeKey]) return;
              window.__AI_USAGE_FETCHED_ENDPOINTS__[dedupeKey] = true;
              var options = { credentials: 'include' };
              if (headerKey) options.headers = headers;
              fetch(targetUrl, options)
                .then(function(response) {
                  return response.clone().text()
                    .then(function(value) {
                      rememberEndpointSummary(endpoint, response, value);
                      return value;
                    })
                    .catch(function() {
                      rememberEndpointSummary(endpoint, response, "");
                      return "";
                    });
                })
                .then(function(value) {
                  rememberSignal(endpoint + " " + value);
                  fetchProviderScopedEndpoints(value);
                  emitResponse(buildResponse());
                })
                .catch(function() {
                  rememberEndpointError(endpoint, -1);
                  emitResponse(buildResponse());
                });
            } catch (error) {}
          }
          function findFirstStringByKey(value, pattern, depth) {
            if (!value || depth > 8) return null;
            if (Array.isArray(value)) {
              for (var arrayIndex = 0; arrayIndex < value.length; arrayIndex += 1) {
                var arrayValue = findFirstStringByKey(value[arrayIndex], pattern, depth + 1);
                if (arrayValue) return arrayValue;
              }
              return null;
            }
            if (typeof value !== "object") return null;
            var keys = Object.keys(value);
            for (var keyIndex = 0; keyIndex < keys.length; keyIndex += 1) {
              var key = keys[keyIndex];
              var child = value[key];
              if (pattern.test(key) && typeof child === "string" && child.length > 20) return child;
              var nested = findFirstStringByKey(child, pattern, depth + 1);
              if (nested) return nested;
            }
            return null;
          }
          function fetchCodexAuthenticatedUsage() {
            try {
              if (PROVIDER_ID !== "codex" || !isProviderOrigin()) return;
              if (window.__AI_USAGE_CODEX_AUTH_USAGE_STARTED__) return;
              window.__AI_USAGE_CODEX_AUTH_USAGE_STARTED__ = true;
              fetch(new URL("/api/auth/session", location.origin).toString(), { credentials: "include" })
                .then(function(response) {
                  return response.clone().text()
                    .then(function(value) {
                      rememberEndpointSummary("/api/auth/session", response, value);
                      return value;
                    })
                    .catch(function() {
                      rememberEndpointSummary("/api/auth/session", response, "");
                      return "";
                    });
                })
                .then(function(value) {
                  var candidate = extractJsonCandidate(value) || value;
                  var parsed = JSON.parse(candidate);
                  var accessToken = findFirstStringByKey(parsed, /^(accessToken|access_token)$/i, 0);
                  if (!accessToken) {
                    rememberEndpointError("/api/auth/session#token", -3);
                    emitResponse(buildResponse());
                    return;
                  }
                  var accountIds = extractAccountIds(value);
                  rememberEndpointError("/api/auth/session#token", accountIds.length);
                  var baseHeaders = { "Authorization": "Bearer " + accessToken };
                  [
                    "/backend-api/codex/usage",
                    "/backend-api/wham/usage"
                  ].forEach(function(endpoint) {
                    fetchEndpoint(endpoint, baseHeaders);
                    accountIds.forEach(function(accountId) {
                      fetchEndpoint(endpoint, {
                        "Authorization": "Bearer " + accessToken,
                        "ChatGPT-Account-Id": accountId
                      });
                    });
                  });
                  emitResponse(buildResponse());
                })
                .catch(function() {
                  rememberEndpointError("/api/auth/session", -1);
                  emitResponse(buildResponse());
                });
            } catch (error) {}
          }
          function fetchClaudeScopedEndpoints(text) {
            if (PROVIDER_ID !== "claude") return;
            var ids = extractClaudeOrganizationIds(text);
            ids.forEach(function(organizationId) {
              [
                "/api/bootstrap/" + organizationId + "/current_user_access",
                "/api/organizations/" + organizationId + "/usage",
                "/api/organizations/" + organizationId + "/usage_limits",
                "/api/organizations/" + organizationId + "/limits",
                "/api/organizations/" + organizationId + "/subscription_details",
                "/api/organizations/" + organizationId + "/subscription",
                "/api/organizations/" + organizationId + "/billing",
                "/api/organizations/" + organizationId + "/trial_status",
                "/api/organizations/" + organizationId + "/overage_spend_limit",
                "/api/organizations/" + organizationId + "/profile",
                "/api/organizations/" + organizationId + "/settings"
              ].forEach(fetchEndpoint);
            });
          }
          function geminiAtToken() {
            try {
              if (window.WIZ_global_data && window.WIZ_global_data.SNlM0e) {
                return safeText(window.WIZ_global_data.SNlM0e);
              }
            } catch (error) {}
            try {
              var input = document.querySelector('input[name="at"]');
              if (input && input.value) return safeText(input.value);
            } catch (error) {}
            try {
              var match = (document.documentElement && document.documentElement.innerHTML || "").match(/"SNlM0e"\s*:\s*"([^"]+)"/);
              if (match) return safeText(match[1]);
            } catch (error) {}
            return "";
          }
          function fetchGeminiBatchExecute(rpcId, payload, label) {
            try {
              if (PROVIDER_ID !== "gemini" || !isProviderOrigin()) return;
              if (typeof fetch !== "function") {
                rememberEndpointError(label || rpcId, -2);
                return;
              }
              if (!window.__AI_USAGE_GEMINI_RPC_STARTED__) window.__AI_USAGE_GEMINI_RPC_STARTED__ = {};
              var key = rpcId + ":" + JSON.stringify(payload || []);
              if (window.__AI_USAGE_GEMINI_RPC_STARTED__[key]) return;
              window.__AI_USAGE_GEMINI_RPC_STARTED__[key] = true;
              var endpoint = "/_/BardChatUi/data/batchexecute?rpcids=" +
                encodeURIComponent(rpcId) +
                "&source-path=" + encodeURIComponent(location.pathname || "/app") +
                "&_reqid=" + Math.floor(Math.random() * 900000 + 100000) +
                "&rt=c";
              var body = "f.req=" + encodeURIComponent(JSON.stringify([[[rpcId, JSON.stringify(payload || []), null, "generic"]]]));
              var at = geminiAtToken();
              if (at) body += "&at=" + encodeURIComponent(at);
              fetch(new URL(endpoint, location.origin).toString(), {
                method: "POST",
                credentials: "include",
                headers: {
                  "content-type": "application/x-www-form-urlencoded;charset=UTF-8",
                  "x-same-domain": "1"
                },
                body: body
              })
                .then(function(response) {
                  return response.clone().text()
                    .then(function(value) {
                      rememberEndpointSummary("/_/BardChatUi/data/batchexecute#" + (label || rpcId), response, value);
                      return value;
                    })
                    .catch(function() {
                      rememberEndpointSummary("/_/BardChatUi/data/batchexecute#" + (label || rpcId), response, "");
                      return "";
                    });
                })
                .then(function(value) {
                  rememberSignal("/_/BardChatUi/data/batchexecute#" + (label || rpcId) + " " + value);
                  emitResponse(buildResponse());
                })
                .catch(function() {
                  rememberEndpointError("/_/BardChatUi/data/batchexecute#" + (label || rpcId), -1);
                  emitResponse(buildResponse());
                });
            } catch (error) {}
          }
          function fetchGeminiQuotaEndpoints() {
            if (PROVIDER_ID !== "gemini" || !hostEndsWith("gemini.google.com")) return;
            fetchGeminiBatchExecute("VxUbXb", [], "CheckModeFeatureQuota");
            [1, 3, 6].forEach(function(mode) {
              fetchGeminiBatchExecute("aPya6c", [mode], "CheckQuota:" + mode);
            });
            var contexts = [];
            [1, 3, 6].forEach(function(mode) {
              [3, 4, 5, 6, 7, 8, 9, 11, 12, 14, 17, 21, 27].forEach(function(action) {
                contexts.push([mode, action]);
              });
            });
            fetchGeminiBatchExecute("qpEbW", [contexts], "CheckGeminiQuota");
          }
          function fetchAccountScopedEndpoints(text) {
            if (PROVIDER_ID !== "codex") return;
            var ids = extractAccountIds(text);
            ids.forEach(function(accountId) {
              [
                "/backend-api/codex/usage",
                "/backend-api/wham/usage"
              ].forEach(function(endpoint) {
                fetchEndpoint(endpoint, { "ChatGPT-Account-Id": accountId });
              });
              [
                "/backend-api/accounts/" + accountId + "/subscription",
                "/backend-api/accounts/" + accountId + "/billing/subscription",
                "/backend-api/accounts/" + accountId + "/billing",
                "/backend-api/accounts/" + accountId + "/usage",
                "/backend-api/accounts/" + accountId + "/usage_limits",
                "/backend-api/accounts/" + accountId + "/message_limits",
                "/backend-api/accounts/" + accountId + "/limits",
                "/backend-api/accounts/" + accountId + "/rate_limits",
                "/backend-api/accounts/" + accountId + "/features"
              ].forEach(fetchEndpoint);
            });
          }
          function fetchProviderScopedEndpoints(text) {
            fetchClaudeScopedEndpoints(text);
            fetchAccountScopedEndpoints(text);
          }
          function fetchProviderEndpoints() {
            if (!isProviderOrigin()) return;
            if (window.__AI_USAGE_ENDPOINT_PROBES_STARTED__) return;
            window.__AI_USAGE_ENDPOINT_PROBES_STARTED__ = true;
            if (PROVIDER_ID === "claude") {
              fetchClaudeScopedEndpoints(document.cookie || "");
            }
            fetchGeminiQuotaEndpoints();
            var endpoints = providerEndpointCandidates();
            endpoints.forEach(fetchEndpoint);
          }
          installNetworkHooks();
          if (HOOK_ONLY) return "hooked";
          fetchProviderEndpoints();
          fetchCodexAuthenticatedUsage();
          collectIndexedDbSignals();
          var response = buildResponse();
          emitResponse(response);
          setTimeout(function() { emitResponse(buildResponse()); }, 1200);
          setTimeout(function() { emitResponse(buildResponse()); }, 3000);
          return JSON.stringify(response);
        })()
        """

    fun decodeJavascriptString(rawValue: String?): String {
        val encoded = rawValue?.takeIf { it.isNotBlank() && it != "null" } ?: return ""
        return runCatching { JSONArray("[$encoded]").optString(0) }.getOrDefault("")
    }
}
