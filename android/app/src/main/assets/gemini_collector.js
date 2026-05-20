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

  function detectEmail(text) {
    var match = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.exec(text || "");
    return match ? match[0] : null;
  }

  function normalizeLabel(value) {
    var label = safeText(value);
    var key = compact(label);
    if (key.indexOf("deepresearch") >= 0) return "Gemini Deep Research";
    if (key.indexOf("gemini") >= 0 && key.indexOf("flash") >= 0) return "Gemini Flash";
    if (key.indexOf("gemini") >= 0 && key.indexOf("pro") >= 0) return "Gemini Pro";
    if (key === "flash" || key.indexOf("flash") >= 0) return "Gemini Flash";
    if (key === "pro" || key.indexOf("pro") >= 0) return "Gemini Pro";
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

  function usedRateFromLimit(limit) {
    var cap = parseNumber(firstValue(limit, ["limit", "limitAmount", "limit_amount"]));
    var remaining = parseNumber(firstValue(limit, ["remaining", "remainingAmount", "remaining_amount"]));
    var used = parseNumber(firstValue(limit, ["used", "usedAmount", "used_amount"]));
    if (isNumber(cap) && cap > 0) {
      if (!isNumber(used) && isNumber(remaining)) used = cap - remaining;
      if (isNumber(used)) return Math.max(0, Math.min(1, used / cap));
    }
    var rawRate = parseNumber(firstValue(limit, ["u", "usage", "usageRate", "usedRate", "usedPercent"]));
    var remainingFraction = parseNumber(firstValue(limit, ["remainingFraction", "remaining_fraction", "remainingPercent", "remaining_percent"]));
    if (isNumber(remainingFraction)) {
      var remainingRate = remainingFraction <= 1 ? remainingFraction : remainingFraction / 100;
      return Math.max(0, Math.min(1, 1 - remainingRate));
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
      "Gemini Pro": 0,
      "Gemini Flash": 1,
      "Gemini Deep Research": 2
    };
    var byLabel = {};
    function lineScore(line) {
      var score = 0;
      var usedRate = parseNumber(line && line.u);
      if (isNumber(usedRate) && usedRate > 0.0001) score += 64;
      if (isNumber(usedRate)) score += 8;
      if (line && (line.r || line.t)) score += 4;
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

    var combinedText = safeText(context.combinedText || rows.join("\n"));
    var plan = normalizePlan(context.plan) || detectPlanFromText(combinedText);
    var account = {};
    if (plan) account.p = plan;
    var email = detectEmail(combinedText);
    if (email) account.e = email;

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
