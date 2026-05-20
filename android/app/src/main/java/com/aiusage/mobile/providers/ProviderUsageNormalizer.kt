package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageLine
import com.aiusage.mobile.local.ProviderUsageSnapshot
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

enum class ProviderPayloadSource(val label: String, val confidence: Float) {
    PROVIDER_API("provider-api", 0.99f),
    NETWORK_RESPONSE("webview-network", 0.96f),
    APP_STATE("app-state", 0.92f),
    STRUCTURED_SCRIPT("structured-script", 0.90f),
    DOM_TEXT("dom-text", 0.40f)
}

object ProviderUsageNormalizer {
    fun normalize(
        providerId: ProviderId,
        rawPayload: String,
        source: ProviderPayloadSource,
        fetchedAt: String = Instant.now().toString()
    ): ProviderUsageSnapshot? {
        if (source == ProviderPayloadSource.DOM_TEXT) return null
        val json = runCatching { JSONObject(rawPayload) }.getOrNull() ?: return null
        val snapshot = when (providerId) {
            ProviderId.CLAUDE -> normalizeClaude(json, source, fetchedAt)
            ProviderId.CODEX -> normalizeCodex(json, source, fetchedAt)
            ProviderId.GEMINI -> normalizeGemini(json, source, fetchedAt)
            ProviderId.COPILOT -> normalizeCopilot(json, source, fetchedAt)
            ProviderId.CURSOR -> normalizeCursor(json, source, fetchedAt)
        }
        return snapshot?.takeIf { it.lines.isNotEmpty() }
    }

    private fun normalizeClaude(json: JSONObject, source: ProviderPayloadSource, fetchedAt: String): ProviderUsageSnapshot? {
        val usage = json.optObject("usage") ?: json
        val lines = listOfNotNull(
            (usage.optObject("five_hour") ?: usage.optObject("session"))?.toLine("claude:session", "Claude Session", source),
            (usage.optObject("seven_day") ?: usage.optObject("weekly"))?.toLine("claude:weekly", "Claude Weekly", source),
            usage.optObject("opus")?.toLine("claude:opus", "Claude Opus", source),
            usage.optObject("sonnet")?.toLine("claude:sonnet", "Claude Sonnet", source),
            usage.optObject("cowork")?.toLine("claude:cowork", "Claude Cowork", source),
            (usage.optObject("seven_day_omelette") ?: usage.optObject("design"))?.toLine("claude:design", "Claude Design", source)
        )
        return snapshot(ProviderId.CLAUDE, json.optionalString("plan"), json.optionalString("account"), fetchedAt, lines)
    }

    private fun normalizeCodex(json: JSONObject, source: ProviderPayloadSource, fetchedAt: String): ProviderUsageSnapshot? {
        val usage = json.optObject("usage") ?: json
        val limits = usage.optObject("rate_limits") ?: usage.optObject("rate_limit") ?: usage
        val lines = listOfNotNull(
            limits.optObject("primary_window")?.toLine("codex:primary_window", "Codex Session", source),
            limits.optObject("secondary_window")?.toLine("codex:secondary_window", "Codex Weekly", source),
            (limits.optObject("code_review") ?: usage.optObject("code_review_rate_limit") ?: json.optObject("code_review_rate_limit"))?.toLine(
                "codex:code_review",
                "Codex Code Review",
                source
            ),
            (limits.optObject("spark") ?: usage.optObject("spark_rate_limit") ?: usage.optObject("spark_rate_limits") ?: json.optObject("spark_rate_limit") ?: json.optObject("spark_rate_limits"))?.toLine(
                "codex:spark",
                "Codex Spark",
                source
            ),
            (limits.optObject("credits") ?: usage.optObject("credits") ?: json.optObject("credits"))?.toCreditsLine("codex:credits", "Codex Credits", source)
        )
        return snapshot(
            providerId = ProviderId.CODEX,
            plan = codexPlan(json.optionalString("plan_type") ?: json.optionalString("plan")),
            account = json.optionalString("account") ?: json.optionalString("email"),
            fetchedAt = fetchedAt,
            lines = lines
        )
    }

    private fun normalizeGemini(json: JSONObject, source: ProviderPayloadSource, fetchedAt: String): ProviderUsageSnapshot? {
        val account = json.optObject("account")
        val usage = json.optObject("usage") ?: json
        val limits = usage.optJSONArray("x")
        val lines = buildList {
            if (limits != null) {
                for (index in 0 until limits.length()) {
                    val item = limits.optJSONObject(index) ?: continue
                    val label = geminiLineLabel(item.optionalString("l")) ?: continue
                    item.toLine("gemini:${label.lowercase(Locale.US).replace(" ", "_")}", label, source)?.let(::add)
                }
            }
        }.ifEmpty {
            geminiCodeAssistLines(json.optJSONArray("limits") ?: usage.optJSONArray("limits") ?: usage.optJSONArray("quotaBuckets"), source)
        }
        return snapshot(
            providerId = ProviderId.GEMINI,
            plan = geminiPlan(account?.optionalString("p") ?: json.optionalString("plan")),
            account = account?.optionalString("e") ?: json.optionalString("account"),
            fetchedAt = fetchedAt,
            lines = lines
        )
    }

    private fun geminiCodeAssistLines(limits: JSONArray?, source: ProviderPayloadSource): List<ProviderUsageLine> {
        if (limits == null) return emptyList()
        val selected = linkedMapOf<String, ProviderUsageLine>()
        for (index in 0 until limits.length()) {
            val item = limits.optJSONObject(index) ?: continue
            val label = geminiLineLabel(
                item.optionalString("modelId")
                    ?: item.optionalString("model_id")
                    ?: item.optionalString("title")
                    ?: item.optionalString("l")
                    ?: item.optionalString("label")
            ) ?: continue
            val remaining = item.optionalNumber("remainingFraction")
                ?: item.optionalNumber("remaining_fraction")
                ?: item.optionalNumber("remainingPercent")
                ?: item.optionalNumber("remaining_percent")
                ?: continue
            val remainingPercent = (if (remaining > 1.0) remaining / 100.0 else remaining).toFloat().coerceIn(0f, 1f)
            val line = ProviderUsageLine(
                key = "gemini:${label.lowercase(Locale.US).replace(" ", "_")}",
                label = label,
                remainingPercent = remainingPercent,
                resetsAt = item.optionalString("resetTime") ?: item.optionalString("reset_time") ?: item.optionalString("resetAt") ?: item.optionalString("resetsAt") ?: item.optionalString("resets_at"),
                resetText = item.optionalString("resetText") ?: item.optionalString("reset_text"),
                sourceLabel = source.label,
                confidence = source.confidence
            )
            val existing = selected[label]
            if (existing == null || (line.remainingPercent ?: 1f) < (existing.remainingPercent ?: 1f)) {
                selected[label] = line
            }
        }
        val order = mapOf("Gemini Pro" to 0, "Gemini Flash" to 1, "Gemini Deep Research" to 2)
        return selected.values.sortedWith(compareBy({ order[it.label] ?: 100 }, { it.label }))
    }

    private fun normalizeCopilot(json: JSONObject, source: ProviderPayloadSource, fetchedAt: String): ProviderUsageSnapshot? {
        val quotas = json.optObject("quotas") ?: json.optObject("usage") ?: json
        val snapshotLines = copilotQuotaSnapshotLines(
            snapshots = quotas.optObject("quota_snapshots")
                ?: quotas.optObject("quotaSnapshots")
                ?: json.optObject("quota_snapshots")
                ?: json.optObject("quotaSnapshots"),
            resetDate = quotas.optionalString("quota_reset_date")
                ?: json.optionalString("quota_reset_date")
                ?: quotas.optionalString("resetDate")
                ?: json.optionalString("resetDate"),
            source = source
        )
        val limitedLines = copilotLimitedQuotaLines(
            remaining = quotas.optObject("limited_user_quotas") ?: json.optObject("limited_user_quotas"),
            limits = quotas.optObject("monthly_quotas") ?: json.optObject("monthly_quotas"),
            resetDate = quotas.optionalString("limited_user_reset_date")
                ?: json.optionalString("limited_user_reset_date")
                ?: quotas.optionalString("quota_reset_date")
                ?: json.optionalString("quota_reset_date"),
            source = source
        )
        val legacyNestedLines = listOfNotNull(
            quotas.toCopilotQuotaLine("chat", "copilot:chat", "Chat", source),
            quotas.toCopilotQuotaLine("completions", "copilot:completions", "Completions", source),
            quotas.toCopilotQuotaLine("premiumInteractions", "copilot:premium_requests", "Premium requests", source)
        )
        val explicitLines = listOfNotNull(
            quotas.optObject("chat")?.toLine("copilot:chat", "Chat", source),
            quotas.optObject("completions")?.toLine("copilot:completions", "Completions", source),
            quotas.optObject("premium_requests")?.toLine("copilot:premium_requests", "Premium requests", source),
            quotas.optObject("premiumRequests")?.toLine("copilot:premium_requests", "Premium requests", source)
        )
        val lines = mergeCopilotLines(snapshotLines, limitedLines, legacyNestedLines, explicitLines)
        val plan = json.optionalString("plan")
            ?: json.optionalString("sku")
            ?: json.optionalString("copilot_plan")
            ?: json.optionalString("license")
            ?: json.optionalString("licenseType")
            ?: json.optionalString("license_type")
            ?: quotas.optionalString("plan")
            ?: quotas.optionalString("sku")
            ?: quotas.optionalString("licenseType")
            ?: quotas.optionalString("license_type")
        val account = json.optionalString("account")
            ?: json.optionalString("login")
            ?: json.optionalString("user_login")
            ?: json.optObject("user")?.optionalString("login")
            ?: json.optObject("user")?.optionalString("name")
        return snapshot(
            ProviderId.COPILOT,
            copilotPlan(plan),
            account,
            fetchedAt,
            lines
        )
    }

    private fun copilotQuotaSnapshotLines(
        snapshots: JSONObject?,
        resetDate: String?,
        source: ProviderPayloadSource
    ): List<ProviderUsageLine> {
        if (snapshots == null) return emptyList()
        return listOfNotNull(
            snapshots.optObject("chat")?.toCopilotSnapshotLine("copilot:chat", "Chat", resetDate, source),
            snapshots.optObject("completions")?.toCopilotSnapshotLine("copilot:completions", "Completions", resetDate, source),
            (snapshots.optObject("premium_interactions")
                ?: snapshots.optObject("premiumInteractions")
                ?: snapshots.optObject("premium_requests")
                ?: snapshots.optObject("premiumRequests"))?.toCopilotSnapshotLine(
                "copilot:premium_requests",
                "Premium requests",
                resetDate,
                source
            )
        )
    }

    private fun copilotLimitedQuotaLines(
        remaining: JSONObject?,
        limits: JSONObject?,
        resetDate: String?,
        source: ProviderPayloadSource
    ): List<ProviderUsageLine> {
        if (remaining == null || limits == null) return emptyList()
        return listOfNotNull(
            copilotAmountLine(remaining, limits, "chat", "copilot:chat", "Chat", resetDate, source),
            copilotAmountLine(remaining, limits, "completions", "copilot:completions", "Completions", resetDate, source)
        )
    }

    private fun copilotAmountLine(
        remaining: JSONObject,
        limits: JSONObject,
        amountKey: String,
        key: String,
        label: String,
        resetDate: String?,
        source: ProviderPayloadSource
    ): ProviderUsageLine? {
        val remainingValue = remaining.optionalNumber(amountKey) ?: return null
        val limitValue = limits.optionalNumber(amountKey) ?: return null
        if (limitValue <= 0.0) return null
        val line = JSONObject().put("remaining_percent", (remainingValue / limitValue) * 100.0)
        resetDate?.let { line.put("resetAt", it) }
        return line.toLine(key, label, source)
    }

    private fun mergeCopilotLines(vararg groups: List<ProviderUsageLine>): List<ProviderUsageLine> {
        val linesByKey = linkedMapOf<String, ProviderUsageLine>()
        groups.forEach { group ->
            group.forEach { line ->
                if (!linesByKey.containsKey(line.key)) {
                    linesByKey[line.key] = line
                }
            }
        }
        val order = mapOf(
            "copilot:chat" to 0,
            "copilot:completions" to 1,
            "copilot:premium_requests" to 2
        )
        return linesByKey.values.sortedWith(compareBy({ order[it.key] ?: 100 }, { it.label }))
    }

    private fun JSONObject.toCopilotSnapshotLine(
        key: String,
        label: String,
        resetDate: String?,
        source: ProviderPayloadSource
    ): ProviderUsageLine? {
        val remainingPercent = optionalNumber("percent_remaining")
            ?: optionalNumber("percentRemaining")
            ?: optionalNumber("remaining_percent")
            ?: optionalNumber("remainingPercent")
            ?: remainingPercentFromAmounts()
            ?: if (optionalBoolean("unlimited") == true) 100.0 else null
            ?: return null
        val line = JSONObject().put("remaining_percent", remainingPercent)
        (optionalString("reset_date") ?: optionalString("resetDate") ?: resetDate)?.let { line.put("resetAt", it) }
        return line.toLine(key, label, source)
    }

    private fun JSONObject.remainingPercentFromAmounts(): Double? {
        val remaining = optionalNumber("remaining") ?: return null
        val entitlement = optionalNumber("entitlement")
            ?: optionalNumber("limit")
            ?: optionalNumber("quota")
            ?: return null
        if (entitlement <= 0.0) return null
        return (remaining / entitlement) * 100.0
    }

    private fun normalizeCursor(json: JSONObject, source: ProviderPayloadSource, fetchedAt: String): ProviderUsageSnapshot? {
        val reset = cursorReset(json)
        val lines = buildList {
            addAll(cursorLimitLines(json.optJSONArray("limits"), source, reset))
            cursorPlanUsageLine(
                json.optObject("planUsage")
                    ?: json.optObject("usage")?.optObject("planUsage")
                    ?: json.takeIf { it.has("totalPercentUsed") || it.has("totalSpend") },
                source,
                reset
            )?.let(::add)
            addAll(cursorRequestUsageLines(json.optObject("requestUsage") ?: json.optObject("usage") ?: json, source, reset))
            addAll(cursorIndividualUsageLines(json.optObject("individualUsage"), source, reset))
        }.let(::dedupeCursorLines)
        return snapshot(
            providerId = ProviderId.CURSOR,
            plan = cursorPlan(json),
            account = json.optionalString("email") ?: json.optionalString("account"),
            fetchedAt = fetchedAt,
            lines = lines
        )
    }

    private fun cursorLimitLines(limits: JSONArray?, source: ProviderPayloadSource, fallbackReset: String?): List<ProviderUsageLine> {
        if (limits == null) return emptyList()
        return buildList {
            for (index in 0 until limits.length()) {
                val item = limits.optJSONObject(index) ?: continue
                val label = item.optionalString("title") ?: item.optionalString("label") ?: continue
                val key = "cursor:${label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')}"
                val line = item.toCursorUsageLine(key, label, source, fallbackReset)
                if (line != null) add(line)
            }
        }
    }

    private fun cursorPlanUsageLine(usage: JSONObject?, source: ProviderPayloadSource, fallbackReset: String?): ProviderUsageLine? {
        if (usage == null) return null
        return usage.toCursorUsageLine("cursor:total", "Total usage", source, fallbackReset)
    }

    private fun cursorRequestUsageLines(root: JSONObject, source: ProviderPayloadSource, fallbackReset: String?): List<ProviderUsageLine> {
        val buckets = mutableListOf<JSONObject>()
        fun collect(value: JSONObject, depth: Int) {
            if (depth > 3) return
            if (value.hasAny("maxRequestUsage", "maxRequests", "requestLimit", "limit", "limitAmount") &&
                value.hasAny("numRequests", "numRequestsTotal", "requestUsage", "currentRequestUsage", "requests", "used", "usage")
            ) {
                buckets.add(value)
                return
            }
            val keys = value.keys()
            while (keys.hasNext()) {
                val child = value.opt(keys.next()) as? JSONObject ?: continue
                collect(child, depth + 1)
            }
        }
        collect(root, 0)
        if (buckets.isEmpty()) return emptyList()
        val used = buckets.sumOf { it.cursorNumber("numRequests", "numRequestsTotal", "requestUsage", "currentRequestUsage", "requests", "used", "usage") ?: 0.0 }
        val limit = buckets.sumOf { it.cursorNumber("maxRequestUsage", "maxRequests", "requestLimit", "limit", "limitAmount") ?: 0.0 }
        if (used < 0.0 || limit <= 0.0) return emptyList()
        return listOf(
            ProviderUsageLine(
                key = "cursor:requests",
                label = "Requests",
                remainingPercent = ((limit - used) / limit).toFloat().coerceIn(0f, 1f),
                resetsAt = fallbackReset,
                usedAmount = used,
                limitAmount = limit,
                remainingAmount = (limit - used).coerceAtLeast(0.0),
                unit = "requests",
                sourceLabel = source.label,
                confidence = source.confidence
            )
        )
    }

    private fun cursorIndividualUsageLines(individualUsage: JSONObject?, source: ProviderPayloadSource, fallbackReset: String?): List<ProviderUsageLine> {
        if (individualUsage == null) return emptyList()
        return listOfNotNull(
            individualUsage.optObject("plan")?.toCursorUsageLine("cursor:total", "Total usage", source, fallbackReset),
            individualUsage.optObject("onDemand")?.toCursorUsageLine("cursor:on_demand", "On-demand usage", source, fallbackReset)
        )
    }

    private fun JSONObject.toCursorUsageLine(
        key: String,
        label: String,
        source: ProviderPayloadSource,
        fallbackReset: String?
    ): ProviderUsageLine? {
        val usedPercent = cursorNumber("usedPercent", "used_percent", "totalPercentUsed", "total_percent_used", "utilization")
        val limit = cursorNumber("limit", "monthlyLimit", "totalLimit", "maxRequestUsage", "maxRequests", "requestLimit", "limitAmount")
        val remainingPercent = cursorNumber("remainingPercent", "remaining_percent")
        val remaining = cursorNumber("remaining", "totalRemaining", "balance")
        val used = cursorNumber("totalSpend", "totalUsage", "spend", "used", "usage")
        val isPercentBased = usedPercent != null || remainingPercent != null
        val remainingFraction = when {
            usedPercent != null -> (100.0 - percent(usedPercent)) / 100.0
            remainingPercent != null -> if (remainingPercent > 1.0) remainingPercent / 100.0 else remainingPercent
            limit != null && limit > 0.0 && remaining != null -> remaining / limit
            limit != null && limit > 0.0 && used != null -> (limit - used) / limit
            else -> return null
        }.toFloat().coerceIn(0f, 1f)
        if (!isPercentBased && limit != null && limit <= 0.0) return null
        val amountLimit = limit?.takeIf { it > 0.0 }
        return ProviderUsageLine(
            key = key,
            label = label,
            remainingPercent = remainingFraction,
            resetsAt = cursorReset(this) ?: fallbackReset,
            resetText = optionalString("resetText") ?: optionalString("reset_text"),
            usedAmount = used.takeIf { amountLimit != null },
            limitAmount = amountLimit,
            remainingAmount = remaining?.takeIf { amountLimit != null }
                ?: if (amountLimit != null && used != null) (amountLimit - used).coerceAtLeast(0.0) else null,
            unit = optionalString("unit") ?: "percent",
            sourceLabel = source.label,
            confidence = source.confidence
        )
    }

    private fun dedupeCursorLines(lines: List<ProviderUsageLine>): List<ProviderUsageLine> {
        val order = mapOf("Total usage" to 0, "Requests" to 1, "Auto usage" to 2, "API usage" to 3, "On-demand usage" to 4)
        return lines
            .filter { it.remainingPercent != null }
            .groupBy { it.label }
            .map { (_, values) -> values.minBy { it.remainingPercent ?: 1f } }
            .sortedWith(compareBy({ order[it.label] ?: 100 }, { it.label }))
    }

    private fun cursorPlan(json: JSONObject): String? {
        return json.optionalString("membershipType")
            ?: json.optionalString("plan")
            ?: json.optionalString("planName")
            ?: json.optionalString("planType")
            ?: json.optionalString("tier")
            ?: json.optObject("planInfo")?.optionalString("plan")
            ?: json.optObject("plan")?.optionalString("name")
            ?: json.optObject("individualUsage")?.optionalString("plan")
    }

    private fun cursorReset(json: JSONObject): String? {
        return json.optionalString("billingCycleEnd")
            ?: json.optionalString("resetAt")
            ?: json.optionalString("resetsAt")
            ?: json.optionalString("resetTime")
            ?: json.optionalNumber("billingCycleEnd")?.toLong()?.toString()
            ?: json.optionalNumber("resetAt")?.toLong()?.toString()
            ?: json.optionalNumber("resetsAt")?.toLong()?.toString()
    }

    private fun JSONObject.cursorNumber(vararg keys: String): Double? {
        for (key in keys) {
            optionalNumber(key)?.let { return it }
        }
        return null
    }

    private fun JSONObject.hasAny(vararg keys: String): Boolean = keys.any { has(it) && !isNull(it) }

    private fun snapshot(
        providerId: ProviderId,
        plan: String?,
        account: String?,
        fetchedAt: String,
        lines: List<ProviderUsageLine>
    ): ProviderUsageSnapshot? {
        if (lines.isEmpty()) return null
        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            planLabel = plan,
            account = account,
            updatedAt = fetchedAt,
            lines = lines
        )
    }

    private fun JSONObject.toLine(key: String, label: String, source: ProviderPayloadSource): ProviderUsageLine? {
        val used = usedPercent() ?: return null
        val remaining = (100 - used).coerceIn(0, 100)
        return ProviderUsageLine(
            key = key,
            label = label,
            remainingPercent = remaining / 100f,
            resetsAt = optionalString("resets_at") ?: optionalString("reset_at") ?: optionalString("resetAt") ?: optionalString("r"),
            resetText = optionalString("reset_text") ?: optionalString("resetText") ?: optionalString("t") ?: resetAfterText(),
            sourceLabel = source.label,
            confidence = source.confidence
        )
    }

    private fun JSONObject.toCopilotQuotaLine(quotaKey: String, lineKey: String, label: String, source: ProviderPayloadSource): ProviderUsageLine? {
        val remaining = optObject("remaining") ?: return null
        val remainingValue = remaining.optionalNumber(quotaKey) ?: return null
        val limitValue = optObject("limits")?.optionalNumber(quotaKey)
        val remainingPercent = remaining.optionalNumber("${quotaKey}Percentage")
            ?: if (limitValue != null && limitValue > 0.0) (remainingValue / limitValue) * 100.0 else return null
        val line = JSONObject().put("remaining_percent", remainingPercent)
        optionalString("resetDate")?.let { line.put("resetAt", it) }
        return line.toLine(lineKey, label, source)
    }

    private fun JSONObject.toCreditsLine(key: String, label: String, source: ProviderPayloadSource): ProviderUsageLine? {
        toLine(key, label, source)?.let { return it }
        val balance = optionalNumber("balance") ?: return null
        val limit = optionalNumber("limit") ?: optionalNumber("total") ?: optionalNumber("total_credits") ?: return null
        if (limit <= 0.0) return null
        return JSONObject()
            .put("remaining_percent", (balance / limit) * 100.0)
            .toLine(key, label, source)
    }

    private fun JSONObject.usedPercent(): Int? {
        optionalNumber("used_percent")?.let { return percent(it) }
        optionalNumber("usedPercent")?.let { return percent(it) }
        optionalNumber("usedPercentage")?.let { return percent(it) }
        optionalNumber("used_percentage")?.let { return percent(it) }
        optionalNumber("percent_used")?.let { return percent(it) }
        optionalNumber("totalPercentUsed")?.let { return percent(it) }
        optionalNumber("utilization")?.let { return percent(it) }
        optionalNumber("u")?.let { return percent(it) }
        optionalNumber("remaining_percent")?.let { return 100 - percent(it) }
        optionalNumber("remainingPercent")?.let { return 100 - percent(it) }
        optionalNumber("remainingFraction")?.let { return 100 - percent(it) }
        optionalNumber("remaining_fraction")?.let { return 100 - percent(it) }
        return null
    }

    private fun JSONObject.resetAfterText(): String? {
        val seconds = optionalNumber("reset_after_seconds")?.toLong() ?: return null
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "Resets in ${hours}h ${minutes}m"
            hours > 0 -> "Resets in ${hours}h"
            minutes > 0 -> "Resets in ${minutes}m"
            else -> "Resets soon"
        }
    }

    private fun percent(value: Double): Int {
        val normalized = if (value in 0.0..1.0) value * 100.0 else value
        return normalized.roundToInt().coerceIn(0, 100)
    }

    private fun JSONObject.optionalNumber(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.toDoubleOrNull()
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.optionalBoolean(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Boolean -> value
            else -> value?.toString()?.toBooleanStrictOrNull()
        }
    }

    private fun JSONObject.optObject(key: String): JSONObject? {
        return optJSONObject(key)
    }

    private fun codexPlan(value: String?): String? {
        return when (value?.trim()?.lowercase(Locale.US)) {
            "prolite" -> "Pro 5x"
            "pro" -> "Pro 20x"
            null, "" -> null
            else -> value
        }
    }

    private fun geminiPlan(value: String?): String? {
        return when (value?.trim()?.lowercase(Locale.US)?.replace(Regex("[^a-z0-9]+"), "")) {
            "standardtier", "paid" -> "Paid"
            "legacytier", "legacy", "geminilegacy" -> "Legacy"
            "freetier", "workspace" -> "Free"
            "geminipro", "googleaipro", "g1protier" -> "Gemini Pro"
            "geminiultra", "googleaiultra", "g1ultratier" -> "Gemini Ultra"
            "geminiplus", "googleaiplus" -> "Gemini Plus"
            "geminifree", "googleaifree" -> "Gemini Free"
            "geminiunknown", "unknown" -> null
            null, "" -> null
            else -> value
        }
    }

    private fun copilotPlan(value: String?): String? {
        return when (value?.trim()?.lowercase(Locale.US)?.replace(Regex("[^a-z0-9]+"), "")) {
            "copilotpro", "pro" -> "Copilot Pro"
            "copilotbusiness", "business" -> "Copilot Business"
            "copilotenterprise", "enterprise" -> "Copilot Enterprise"
            null, "" -> null
            else -> value
        }
    }

    private fun geminiLineLabel(value: String?): String? {
        val compact = value?.trim()?.lowercase(Locale.US)?.replace(Regex("[^a-z0-9]+"), "")
        return when {
            compact == "geminipro" || compact == "pro" -> "Gemini Pro"
            compact == "geminiflash" || compact == "flash" -> "Gemini Flash"
            compact == "geminideepresearch" || compact == "deepresearch" -> "Gemini Deep Research"
            compact.orEmpty().contains("gemini") && compact.orEmpty().contains("pro") -> "Gemini Pro"
            compact.orEmpty().contains("gemini") && compact.orEmpty().contains("flash") -> "Gemini Flash"
            compact.orEmpty().contains("deepresearch") -> "Gemini Deep Research"
            else -> null
        }
    }
}
