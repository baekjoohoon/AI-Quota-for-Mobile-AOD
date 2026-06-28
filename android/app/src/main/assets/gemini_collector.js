(function() {
  if (PROVIDER_ID !== "gemini") return;

  var api = window.SAGE_USAGE_EXTRACTOR || {};
  window.SAGE_USAGE_EXTRACTOR = api;

  function compact(value) {
    return safeText(value).toLowerCase().replace(/[^a-z0-9]+/g, "");
  }

  function normalizePlan(value) {
    var key = compact(value);
    if (!key) return null;
    if (key === "standardtier" || key === "paid") return "Paid";
    if (key === "legacytier" || key === "legacy") return "Legacy";
    if (key === "freetier" || key === "workspace") return "Free";
    if (key === "pro" || key === "aipro" || key === "googleaipro" || key === "geminipro") {
      return "GEMINI_PRO";
    }
    if (key === "plus" || key === "aiplus" || key === "googleaiplus" || key === "geminiplus") {
      return "GEMINI_PLUS";
    }
    if (key === "ultra" || key === "aiultra" || key === "googleaiultra" || key === "geminiultra") {
      return "GEMINI_ULTRA";
    }
    if (key === "free" || key === "googleaifree" || key === "geminifree") {
      return "GEMINI_FREE";
    }
    if (key === "geminiunknown" || key === "unknown") return null;
    return null;
  }

  function detectPlanFromText(text) {
    var rows = [
      /google\s+ai\s+ultra/i,
      /google\s+ai\s+pro/i,
      /google\s+ai\s+plus/i,
      /gemini\s+ultra/i,
      /gemini\s+pro/i,
      /gemini\s+plus/i,
      /\bultra\b/i,
      /\bpro\b/i,
      /\bplus\b/i,
      /\bfree\b/i
    ];
    for (var index = 0; index < rows.length; index += 1) {
      var match = rows[index].exec(text);
      var plan = match && normalizePlan(match[0]);
      if (plan) return plan;
    }
    return null;
  }

  function detectPlanBadgeFromText(text) {
    var lines = safeText(text).split(/\n+/).map(function(line) {
      return safeText(line).trim();
    }).filter(Boolean);
    for (var index = 0; index < lines.length; index += 1) {
      var plan = normalizePlan(lines[index]);
      if (plan) return plan;
    }
    return null;
  }

  function detectEmail(text) {
    var match = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.exec(text || "");
    return match ? match[0] : null;
  }

  function labelFromPlan(plan) {
    if (plan === "GEMINI_PRO" || plan === "GEMINI_PLUS" || plan === "GEMINI_ULTRA") return "5-hour limit";
    return null;
  }

  function isWeeklyWindowText(value) {
    var text = safeText(value).toLowerCase();
    return text.indexOf("주간") >= 0 || text.indexOf("weekly") >= 0 || text.indexOf("7 day") >= 0 || text.indexOf("seven day") >= 0;
  }

  function isUsageWindowBoundaryText(value) {
    var text = safeText(value).toLowerCase();
    if (!text) return false;
    if (/%/.test(text) || text.indexOf("초기화") >= 0 || /reset/i.test(text)) return false;
    return text.indexOf("현재 사용량") >= 0 ||
      text.indexOf("사용량 한도") >= 0 ||
      text.indexOf("주간") >= 0 ||
      text.indexOf("weekly") >= 0 ||
      text.indexOf("5-hour") >= 0 ||
      text.indexOf("5 hour") >= 0 ||
      text.indexOf("current usage") >= 0 ||
      text.indexOf("usage limit") >= 0;
  }

  function nearbyResetText(lines, index) {
    for (var offset = 1; offset <= 4; offset += 1) {
      var after = safeText(lines[index + offset]);
      if (isUsageWindowBoundaryText(after)) break;
      if (after && (after.indexOf("초기화") >= 0 || /reset/i.test(after))) return after;
    }
    for (var beforeOffset = 1; beforeOffset <= 3; beforeOffset += 1) {
      var before = safeText(lines[index - beforeOffset]);
      if (isUsageWindowBoundaryText(before)) break;
      if (before && (before.indexOf("초기화") >= 0 || /reset/i.test(before))) return before;
    }
    return null;
  }

  function nearbyWindowText(lines, index) {
    for (var offset = 1; offset <= 4; offset += 1) {
      var value = safeText(lines[index - offset]);
      if (!value) continue;
      if (/%/.test(value) || value.indexOf("초기화") >= 0 || /reset/i.test(value)) continue;
      return value;
    }
    return null;
  }

  function collectUsagePageText(combinedText, limits, plan) {
    var text = safeText(combinedText);
    var added = 0;
    if (!text) return added;
    if (
      text.indexOf("사용량 한도") < 0 &&
      text.indexOf("현재 사용량") < 0 &&
      !/usage\s+limit|current\s+usage/i.test(text)
    ) {
      return added;
    }
    var label = labelFromPlan(plan) || "5-hour limit";
    var lines = text.split(/\n+/).map(function(line) { return safeText(line).trim(); }).filter(Boolean);
    for (var index = 0; index < lines.length; index += 1) {
      var line = lines[index];
      var match = /(\d+(?:[.,]\d+)?)\s*%\s*(?:사용됨|used)/i.exec(line);
      if (!match) continue;
      var usedRate = parseNumber(match[1].replace(",", "."));
      if (!isNumber(usedRate)) continue;
      var windowText = nearbyWindowText(lines, index);
      var lineLabel = isWeeklyWindowText(windowText) ? "Weekly limit" : label;
      limits.push({
        l: lineLabel,
        u: Math.max(0, Math.min(1, usedRate / 100)),
        t: nearbyResetText(lines, index),
        windowText: windowText,
        source: "gemini_usage_page_text",
        confidence: 0.78
      });
      added += 1;
    }
    return added;
  }

  function normalizeLabel(value) {
    var label = safeText(value);
    var key = compact(label);
    if (key.indexOf("deepresearch") >= 0) return "Gemini Deep Research";
    if (key.indexOf("gemini") >= 0 && key.indexOf("flash") >= 0) return "Gemini Flash";
    if (key.indexOf("weekly") >= 0 || key.indexOf("sevenday") >= 0) return "Weekly limit";
    if (key.indexOf("5hour") >= 0 || key.indexOf("fivehour") >= 0) return "5-hour limit";
    if (key.indexOf("gemini") >= 0 && key.indexOf("pro") >= 0) return "5-hour limit";
    if (key === "flash" || key.indexOf("flash") >= 0) return "Gemini Flash";
    if (key === "pro" || key.indexOf("pro") >= 0) return "5-hour limit";
    return null;
  }

  function resetValue(limit) {
    return safeText(firstValue(limit, ["t", "resetText", "reset_text"]));
  }

  function resetAtValue(limit) {
    var value = firstValue(limit, ["r", "resetsAt", "resets_at", "resetAt", "reset_at"]);
    if (value === null || value === undefined || value === "") return null;
    return value;
  }

  function firstValue(object, keys) {
    for (var index = 0; index < keys.length; index += 1) {
      var key = keys[index];
      if (object && Object.prototype.hasOwnProperty.call(object, key)) {
        var value = object[key];
        if (value !== null && value !== undefined && value !== "") return value;
      }
    }
    return null;
  }

  function numberValue(object, keys) {
    var value = firstValue(object, keys);
    if (value === null || value === undefined || value === "") return null;
    return parseNumber(value);
  }

  function usedRateFromLimit(limit) {
    var cap = numberValue(limit, ["limit", "limitAmount", "limit_amount"]);
    var remaining = numberValue(limit, ["remaining", "remainingAmount", "remaining_amount"]);
    var used = numberValue(limit, ["used", "usedAmount", "used_amount"]);
    if (isNumber(cap) && cap > 0) {
      if (!isNumber(used) && isNumber(remaining)) used = cap - remaining;
      if (isNumber(used)) return Math.max(0, Math.min(1, used / cap));
    }
    var rawRate = numberValue(limit, ["u", "usage", "usageRate", "usedRate", "utilization"]);
    var usedPercent = numberValue(limit, ["usedPercent", "used_percent", "usedPercentage", "used_percentage", "percentUsed", "percent_used", "totalPercentUsed", "total_percent_used"]);
    var remainingFraction = numberValue(limit, ["remainingFraction", "remaining_fraction"]);
    var remainingPercent = numberValue(limit, ["remainingPercent", "remaining_percent", "remainingPercentage", "remaining_percentage"]);
    if (isNumber(remainingFraction)) {
      var remainingRate = remainingFraction <= 1 ? remainingFraction : remainingFraction / 100;
      return Math.max(0, Math.min(1, 1 - remainingRate));
    }
    if (isNumber(remainingPercent)) {
      return Math.max(0, Math.min(1, 1 - remainingPercent / 100));
    }
    if (isNumber(usedPercent)) {
      return Math.max(0, Math.min(1, usedPercent / 100));
    }
    if (!isNumber(rawRate)) return null;
    return Math.max(0, Math.min(1, rawRate <= 1 ? rawRate : rawRate / 100));
  }

  function lineFromLimit(limit) {
    if (!limit) return null;
    var label = normalizeLabel(limit.l || limit.label || limit.title || limit.name);
    if (!label) return null;
    var usedRate = usedRateFromLimit(limit);
    if (!isNumber(usedRate)) return null;
    var line = {
      l: label,
      u: Math.round(usedRate * 10000) / 10000,
      source: limit.source || limit.sourceLabel || "gemini_collector.js",
      confidence: limit.confidence || 0.9
    };
    var resetText = resetValue(limit);
    if (resetText) line.t = resetText;
    var resetAt = resetAtValue(limit);
    if (resetAt) line.r = resetAt;
    if (limit.window || limit.windowText) line.window = limit.window || limit.windowText;
    if (limit.category) line.category = limit.category;
    return line;
  }

  function dedupeLines(lines) {
    var order = {
      "5-hour limit": 0,
      "Weekly limit": 1,
      "Gemini Flash": 2,
      "Gemini Deep Research": 3
    };
    var byLabel = {};
    function lineScore(line) {
      var score = 0;
      var usedRate = parseNumber(line && line.u);
      if (isNumber(usedRate) && usedRate > 0.0001) score += 64;
      if (isNumber(usedRate)) score += 8;
      if (line && (line.r || line.t)) score += 4;
      if (line && line.source === "gemini_usage_page_text") score += 128;
      if (line && line.source) score += 1;
      if (line && /checkgeminiquota/i.test(line.source || "") && isNumber(usedRate) && usedRate <= 0.0001) score -= 8;
      if (line && /starts when a message is sent/i.test(line.t || "")) score -= 16;
      return score;
    }
    lines.forEach(function(line) {
      if (!line || !line.l) return;
      var existing = byLabel[line.l];
      if (!existing) {
        byLabel[line.l] = line;
        return;
      }
      var score = lineScore(line);
      var existingScore = lineScore(existing);
      if (
        score > existingScore ||
        (score === existingScore && parseNumber(line.u) > parseNumber(existing.u)) ||
        (score === existingScore && parseNumber(line.u) === parseNumber(existing.u) && (line.confidence || 0) >= (existing.confidence || 0))
      ) {
        byLabel[line.l] = line;
      }
    });
    return Object.keys(byLabel)
      .map(function(label) { return byLabel[label]; })
      .sort(function(left, right) {
        var leftOrder = Object.prototype.hasOwnProperty.call(order, left.l) ? order[left.l] : 100;
        var rightOrder = Object.prototype.hasOwnProperty.call(order, right.l) ? order[right.l] : 100;
        if (leftOrder !== rightOrder) return leftOrder - rightOrder;
        return left.l.localeCompare(right.l);
      });
  }

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

  function collectJsonQuotaRows(rows, limits) {
    (rows || []).forEach(function(row) {
      extractJsonCandidates(row).forEach(function(candidate) {
        try {
          scanGeminiQuotaResponse(
            JSON.parse(candidate),
            ["gemini_collector_asset"],
            "gemini_collector.js",
            limits
          );
        } catch (error) {}
      });
    });
  }

  api.buildGeminiUsagePayload = function(context) {
    var rows = context.rows || [];
    var limits = (context.limits || []).slice(0);
    collectJsonQuotaRows(rows, limits);

    var usagePage = !!context.usagePage;
    var pageText = safeText(context.pageText);
    var combinedText = safeText(context.combinedText || rows.join("\n"));
    var explicitPlan = normalizePlan(context.plan);
    var pagePlan = detectPlanBadgeFromText(pageText);
    var plan = explicitPlan || pagePlan || (usagePage ? "GEMINI_FREE" : detectPlanFromText(combinedText));
    var account = {};
    if (plan) account.p = plan;
    var email = detectEmail(pageText) || detectEmail(combinedText);
    if (email) account.e = email;
    var pageUsageLineCount = collectUsagePageText(pageText, limits, plan);
    if (pageUsageLineCount === 0) {
      collectUsagePageText(combinedText, limits, plan);
    }

    var usageLines = dedupeLines(limits.map(lineFromLimit).filter(Boolean));
    if (usageLines.length > 0) {
      return {
        s: "s",
        provider: "gemini",
        account: account,
        usage: { x: usageLines },
        m: null
      };
    }
    if (context.login) {
      return {
        s: "login_required",
        provider: "gemini",
        account: account,
        usage: { x: [] },
        m: "Gemini login is required."
      };
    }
    return null;
  };
})();
