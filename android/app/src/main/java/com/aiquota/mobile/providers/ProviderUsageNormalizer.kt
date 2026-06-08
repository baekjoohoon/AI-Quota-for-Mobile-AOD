package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageLine
import com.aiquota.mobile.local.ProviderUsageSnapshot
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

enum class ProviderPayloadSource(val label: String, val confidence: Float) {
    PROVIDER_API("provider-api", 0.99f),
    NETWORK_RESPONSE("webview-network", 0.96f),
    APP_STATE("app-state", 0.92f),
    VISIBLE_DOM("visible-dom", 0.94f),
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
            ProviderId.ANTIGRAVITY -> normalizeAntigravity(json, source, fetchedAt)
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
        return snapshot(ProviderId.CLAUDE, claudePlan(json), json.optionalString("account"), fetchedAt, lines)
    }

    private fun claudePlan(json: JSONObject): String? {
        claudePlanString(json)?.let { return it }
        return listOf(
            "subscription",
            "subscription_details",
            "subscriptionDetails",
            "organization",
            "billing",
            "plan_info",
            "planInfo"
        ).firstNotNullOfOrNull { key ->
            json.optObject(key)?.let(::claudePlan)
        }
    }

    private fun claudePlanString(json: JSONObject): String? {
        return listOf(
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
        ).firstNotNullOfOrNull { key ->
            json.optionalScalarString(key)
        }
    }

    private fun normalizeCodex(json: JSONObject, source: ProviderPayloadSource, fetchedAt: String): ProviderUsageSnapshot? {
        val codexSource = if (json.optionalString("source") == "visible-dom") ProviderPayloadSource.VISIBLE_DOM else source
        val preserveVisibleLabel = codexSource == ProviderPayloadSource.VISIBLE_DOM
        val usage = json.optObject("usage") ?: json
        val limits = usage.optObject("rate_limits") ?: usage.optObject("rate_limit") ?: usage
        val compactRows = usage.optJSONArray("x") ?: json.optJSONArray("x")
        val compactLines = codexCompactLines(compactRows, codexSource)
        val lines = compactLines.ifEmpty {
            val primaryWindow = limits.optObject("primary_window")
            val secondaryWindow = limits.optObject("secondary_window")
            val sparkPrimaryWindow = limits.optObject("spark_primary_window")
                ?.withCodexResetFallback(primaryWindow)
            val sparkSecondaryWindow = limits.optObject("spark_secondary_window")
                ?.withCodexResetFallback(secondaryWindow)
            listOfNotNull(
                primaryWindow?.toLine(
                    "codex:primary_window",
                    "Codex Session",
                    codexSource,
                    preferRemainingPercent = true,
                    preservePayloadLabel = preserveVisibleLabel
                ),
                secondaryWindow?.toLine(
                    "codex:secondary_window",
                    "Codex Weekly",
                    codexSource,
                    preferRemainingPercent = true,
                    preservePayloadLabel = preserveVisibleLabel
                ),
                (limits.optObject("code_review") ?: usage.optObject("code_review_rate_limit") ?: json.optObject("code_review_rate_limit"))?.toLine(
                    "codex:code_review",
                    "Codex Code Review",
                    codexSource,
                    preferRemainingPercent = true,
                    preservePayloadLabel = preserveVisibleLabel
                ),
                sparkPrimaryWindow?.toLine(
                    "codex:spark_primary_window",
                    "Codex Spark 5h",
                    codexSource,
                    preferRemainingPercent = true,
                    preservePayloadLabel = preserveVisibleLabel
                ),
                sparkSecondaryWindow?.toLine(
                    "codex:spark_secondary_window",
                    "Codex Spark Weekly",
                    codexSource,
                    preferRemainingPercent = true,
                    preservePayloadLabel = preserveVisibleLabel
                ),
                (limits.optObject("spark") ?: usage.optObject("spark_rate_limit") ?: usage.optObject("spark_rate_limits") ?: json.optObject("spark_rate_limit") ?: json.optObject("spark_rate_limits"))?.toLine(
                    "codex:spark",
                    "Codex Spark",
                    codexSource,
                    preferRemainingPercent = true,
                    preservePayloadLabel = preserveVisibleLabel
                ),
                (limits.optObject("credits") ?: usage.optObject("credits") ?: json.optObject("credits"))?.toCreditsLine("codex:credits", "Codex Credits", codexSource)
            )
        }
        return snapshot(
            providerId = ProviderId.CODEX,
            plan = codexPlan(json.optionalString("plan_type") ?: json.optionalString("plan")),
            account = json.optionalString("account") ?: json.optionalString("email"),
            fetchedAt = fetchedAt,
            lines = lines
        )
    }

    private fun codexCompactLines(limits: JSONArray?, source: ProviderPayloadSource): List<ProviderUsageLine> {
        if (limits == null) return emptyList()
        return buildList {
            for (index in 0 until limits.length()) {
                val item = limits.optJSONObject(index) ?: continue
                val rawLabel = item.optionalString("l") ?: item.optionalString("label") ?: continue
                val label = codexCompactLineLabel(rawLabel) ?: continue
                val key = "codex:${label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')}"
                item.toLine(key, label, source, preferRemainingPercent = true)?.let(::add)
            }
        }
    }

    private fun codexCompactLineLabel(rawLabel: String): String? {
        val label = rawLabel.trim()
        val normalized = label.lowercase(Locale.US)
        val compact = normalized.replace(Regex("[^a-z0-9가-힣]+"), "")
        return when {
            compact == "5시간세션" ||
                compact == "codex5시간세션" ||
                compact == "codex세션" ||
                normalized == "codex session" ||
                normalized == "codex 5h session" ||
                normalized == "5h session" ||
                normalized == "5-hour session" -> "Codex Session"
            compact == "주간세션" ||
                compact == "codex주간세션" ||
                normalized == "codex weekly" ||
                normalized == "codex weekly session" ||
                normalized == "weekly session" -> "Codex Weekly"
            label.contains("codex", ignoreCase = true) -> label
            else -> null
        }
    }

    private fun normalizeGemini(json: JSONObject, source: ProviderPayloadSource, fetchedAt: String): ProviderUsageSnapshot? {
        val account = json.optObject("account")
        val usage = json.optObject("usage") ?: json
        val plan = geminiPlan(account?.optionalString("p") ?: json.optionalString("plan"))
        val limits = usage.optJSONArray("x")
        val lines = buildList {
            if (limits != null) {
                for (index in 0 until limits.length()) {
                    val item = limits.optJSONObject(index) ?: continue
                    val label = geminiLineLabel(item.optionalString("l")) ?: continue
                    item.toLine("gemini:${geminiLineKey(label)}", label, source)?.let(::add)
                }
            }
        }.ifEmpty {
            geminiCodeAssistLines(json.optJSONArray("limits") ?: usage.optJSONArray("limits") ?: usage.optJSONArray("quotaBuckets"), source)
        }.filterNot { isUnavailableFreeGeminiProLine(plan, it) }
        return snapshot(
            providerId = ProviderId.GEMINI,
            plan = plan,
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
                key = "gemini:${geminiLineKey(label)}",
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
        val order = GEMINI_MODEL_LABELS.mapIndexed { index, (_, label) -> label to index }.toMap() +
            mapOf("Gemini Deep Research" to 100)
        return selected.values.sortedWith(compareBy({ order[it.label] ?: 1000 }, { it.label }))
    }

    private fun normalizeCopilot(json: JSONObject, source: ProviderPayloadSource, fetchedAt: String): ProviderUsageSnapshot? {
        val quotas = json.optObject("quotas") ?: json.optObject("usage") ?: json
        val resetDate = quotas.optionalString("quota_reset_date")
            ?: json.optionalString("quota_reset_date")
            ?: quotas.optionalString("resetDate")
            ?: json.optionalString("resetDate")
        val snapshotLines = copilotQuotaSnapshotLines(
            snapshots = quotas.optObject("quota_snapshots")
                ?: quotas.optObject("quotaSnapshots")
                ?: json.optObject("quota_snapshots")
                ?: json.optObject("quotaSnapshots"),
            resetDate = resetDate,
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
        val premiumBillingLines = listOfNotNull(
            copilotPremiumBillingLine(
                billing = quotas.optObject("premium_billing")
                    ?: quotas.optObject("premiumBilling")
                    ?: json.optObject("premium_billing")
                    ?: json.optObject("premiumBilling"),
                resetDate = resetDate,
                source = source
            )
        )
        val legacyNestedLines = listOfNotNull(
            quotas.toCopilotQuotaLine("chat", "copilot:chat", "Chat", source),
            quotas.toCopilotQuotaLine("completions", "copilot:completions", "Inline suggestions", source),
            quotas.toCopilotQuotaLine("premiumInteractions", "copilot:premium_requests", "Premium requests", source)
        )
        val explicitLines = listOfNotNull(
            quotas.optObject("chat")?.withResetDate(resetDate)?.toLine("copilot:chat", "Chat", source),
            quotas.optObject("completions")?.withResetDate(resetDate)?.toLine("copilot:completions", "Inline suggestions", source),
            quotas.optObject("premium_requests")?.toLine("copilot:premium_requests", "Premium requests", source),
            quotas.optObject("premiumRequests")?.toLine("copilot:premium_requests", "Premium requests", source)
        )
        val lines = mergeCopilotLines(premiumBillingLines, snapshotLines, limitedLines, legacyNestedLines, explicitLines)
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
            snapshots.optObject("completions")?.toCopilotSnapshotLine("copilot:completions", "Inline suggestions", resetDate, source),
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
            copilotAmountLine(remaining, limits, "completions", "copilot:completions", "Inline suggestions", resetDate, source)
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

    private fun copilotPremiumBillingLine(
        billing: JSONObject?,
        resetDate: String?,
        source: ProviderPayloadSource
    ): ProviderUsageLine? {
        if (billing == null) return null
        val used = billing.optionalNumber("discountQuantity")
            ?: billing.optionalNumber("includedPremiumRequestsConsumed")
            ?: billing.optionalNumber("used")
            ?: billing.optionalNumber("usage")
            ?: return null
        val entitlement = billing.optionalNumber("filteredUserPremiumRequestEntitlement")
            ?: billing.optionalNumber("userPremiumRequestEntitlement")
            ?: billing.optionalNumber("entitlement")
            ?: billing.optionalNumber("limit")
            ?: 0.0
        val remaining = (entitlement - used).coerceAtLeast(0.0)
        val remainingPercent = if (entitlement > 0.0) (remaining / entitlement).toFloat().coerceIn(0f, 1f) else null
        return ProviderUsageLine(
            key = "copilot:premium_requests",
            label = "Premium requests",
            remainingPercent = remainingPercent,
            remainingText = remainingPercent?.let { "${(it.coerceIn(0f, 1f) * 100f).roundToInt()}% left" }
                ?: "${formatAmount(used)} used",
            resetsAt = billing.optionalString("resetAt")
                ?: billing.optionalString("resetsAt")
                ?: billing.optionalString("resetDate")
                ?: resetDate,
            detailText = if (entitlement > 0.0) {
                "${formatAmount(used)} / ${formatAmount(entitlement)} included"
            } else {
                "${formatAmount(used)} included used"
            },
            usedAmount = used,
            limitAmount = entitlement,
            remainingAmount = if (entitlement > 0.0) remaining else null,
            unit = "requests",
            category = "premium_requests",
            sourceLabel = source.label,
            confidence = source.confidence
        )
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
        val remainingFraction = optionalNumber("remaining_fraction")
            ?: optionalNumber("remainingFraction")
        val remainingValue = remainingPercent
            ?: remainingFraction?.let { percent(it).toDouble() }
            ?: remainingPercentFromAmounts()
            ?: if (optionalBoolean("unlimited") == true) 100.0 else null
            ?: return null
        val line = JSONObject().put("remaining_percent", remainingValue)
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
        val planUsage = json.optObject("planUsage")
            ?: json.optObject("usage")?.optObject("planUsage")
            ?: json.takeIf { it.has("totalPercentUsed") || it.has("totalSpend") }
        val lines = buildList {
            addAll(cursorLimitLines(json.optJSONArray("limits"), source, reset))
            cursorPlanUsageLine(planUsage, source, reset)?.let(::add)
            addAll(cursorPlanUsageBreakdownLines(planUsage, source, reset))
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

    private fun normalizeAntigravity(json: JSONObject, source: ProviderPayloadSource, fetchedAt: String): ProviderUsageSnapshot? {
        val usage = json.optObject("usage") ?: json
        val reset = antigravityReset(json) ?: antigravityReset(usage)
        val compactLines = antigravityCompactLines(
            usage.optJSONArray("x")
                ?: usage.optJSONArray("limits")
                ?: usage.optJSONArray("windows")
                ?: usage.optJSONArray("quotas")
                ?: json.optJSONArray("x")
                ?: json.optJSONArray("limits"),
            source,
            reset
        )
        val explicitLines = listOfNotNull(
            (usage.optObject("session") ?: usage.optObject("current") ?: usage.optObject("quota"))?.withResetDate(reset)
                ?.toLine("antigravity:session", "Antigravity Session", source),
            (usage.optObject("weekly") ?: usage.optObject("week"))?.withResetDate(reset)
                ?.toLine("antigravity:weekly", "Antigravity Weekly", source),
            usage.optObject("credits")?.withResetDate(reset)
                ?.toCreditsLine("antigravity:credits", "Antigravity Credits", source)
        )
        val modelLines = antigravityModelLines(
            json.opt("models") ?: usage.opt("models"),
            source,
            reset
        )
        val lines = (compactLines + explicitLines + modelLines + antigravityCollectedLines(json, source, reset))
            .filter { it.remainingPercent != null }
            .distinctBy { it.key }
            .let(::sortAntigravityLines)
        return snapshot(
            providerId = ProviderId.ANTIGRAVITY,
            plan = antigravityPlan(json, usage),
            account = json.optionalString("account") ?: json.optionalString("email"),
            fetchedAt = fetchedAt,
            lines = lines
        )
    }

    private fun antigravityCompactLines(limits: JSONArray?, source: ProviderPayloadSource, fallbackReset: String?): List<ProviderUsageLine> {
        if (limits == null) return emptyList()
        return buildList {
            for (index in 0 until limits.length()) {
                val item = limits.optJSONObject(index) ?: continue
                val rawLabel = item.optionalString("l")
                    ?: item.optionalString("label")
                    ?: item.optionalString("title")
                    ?: item.optionalString("name")
                    ?: continue
                val label = antigravityDisplayLabel(rawLabel)
                val key = "antigravity:${label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').removePrefix("antigravity_")}"
                antigravityLineFromObject(item.withResetDate(fallbackReset), key, label, source, fallbackReset)?.let(::add)
            }
        }
    }

    private fun antigravityReset(json: JSONObject): String? {
        return json.optionalString("resetAt")
            ?: json.optionalString("resetsAt")
            ?: json.optionalString("reset_at")
            ?: json.optionalString("resets_at")
            ?: json.optionalString("resetTime")
            ?: json.optionalString("reset_time")
            ?: json.optionalString("quota_reset_date")
    }

    private fun antigravityCollectedLines(root: JSONObject, source: ProviderPayloadSource, fallbackReset: String?): List<ProviderUsageLine> {
        val lines = mutableListOf<ProviderUsageLine>()
        fun scan(value: Any?, depth: Int) {
            if (value == null || depth > 6) return
            when (value) {
                is JSONObject -> {
                    val label = antigravityLineLabel(value)
                    if (label != null) {
                        val key = "antigravity:${label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').removePrefix("antigravity_")}"
                        antigravityLineFromObject(value, key, label, source, fallbackReset)?.let(lines::add)
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        scan(value.opt(keys.next()), depth + 1)
                    }
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        scan(value.opt(index), depth + 1)
                    }
                }
            }
        }
        scan(root, 0)
        return lines.distinctBy { it.key }
    }

    private fun antigravityModelLines(models: Any?, source: ProviderPayloadSource, fallbackReset: String?): List<ProviderUsageLine> {
        if (models == null || models == JSONObject.NULL) return emptyList()
        val lines = mutableListOf<ProviderUsageLine>()
        fun addModel(modelId: String, model: JSONObject?) {
            if (model == null || model.optBoolean("isInternal", false)) return
            val label = model.optionalString("displayName")
                ?: model.optionalString("display_name")
                ?: model.optionalString("title")
                ?: model.optionalString("name")
                ?: model.optionalString("model")
                ?: modelId
            if (label.isBlank()) return
            val quota = (model.optObject("quotaInfo") ?: model.optObject("quota") ?: model).withAntigravityResetAlias(fallbackReset)
            val key = "antigravity:${label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').removePrefix("antigravity_")}"
            antigravityLineFromObject(quota, key, antigravityDisplayLabel(label), source, fallbackReset)
                ?.let(lines::add)
        }
        when (models) {
            is JSONObject -> {
                val keys = models.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    addModel(key, models.optJSONObject(key))
                }
            }
            is JSONArray -> {
                for (index in 0 until models.length()) {
                    addModel(index.toString(), models.optJSONObject(index))
                }
            }
        }
        return lines.distinctBy { it.key }
    }

    private fun sortAntigravityLines(lines: List<ProviderUsageLine>): List<ProviderUsageLine> {
        return lines
            .mapIndexed { index, line -> IndexedValue(index, line) }
            .sortedWith(
                compareBy<IndexedValue<ProviderUsageLine>> { antigravityModelPriority(it.value.label) }
                    .thenBy { it.index }
            )
            .map { it.value }
    }

    private fun antigravityModelPriority(label: String): Int {
        val key = label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
        return AntigravityModelOrder.indexOf(key).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    private fun antigravityLineFromObject(
        json: JSONObject,
        key: String,
        label: String,
        source: ProviderPayloadSource,
        fallbackReset: String?
    ): ProviderUsageLine? {
        json.toLine(key, label, source)?.let { return it }
        val limit = json.antigravityNumber("limit", "total", "quota", "max", "limitAmount", "maxRequests")
        val used = json.antigravityNumber("used", "usage", "current", "consumed", "usedAmount", "requestUsage")
        val remaining = json.antigravityNumber("remaining", "available", "balance", "remainingAmount")
        val usedPercent = json.antigravityNumber("used_percent", "usedPercent", "percent_used", "usedPercentage", "utilization")
        val remainingPercent = json.antigravityNumber("remaining_percent", "remainingPercent", "remainingPercentage", "remaining_fraction", "remainingFraction")
        val remainingFraction = when {
            usedPercent != null -> (100.0 - percent(usedPercent)) / 100.0
            remainingPercent != null -> if (remainingPercent > 1.0) remainingPercent / 100.0 else remainingPercent
            limit != null && limit > 0.0 && remaining != null -> remaining / limit
            limit != null && limit > 0.0 && used != null -> (limit - used) / limit
            else -> return null
        }.toFloat().coerceIn(0f, 1f)
        val amountLimit = limit?.takeIf { it > 0.0 }
        return ProviderUsageLine(
            key = key,
            label = label,
            remainingPercent = remainingFraction,
            resetsAt = antigravityReset(json) ?: fallbackReset,
            resetText = json.optionalString("resetText") ?: json.optionalString("reset_text") ?: json.optionalString("t"),
            usedAmount = used?.takeIf { amountLimit != null },
            limitAmount = amountLimit,
            remainingAmount = remaining?.takeIf { amountLimit != null }
                ?: if (amountLimit != null && used != null) (amountLimit - used).coerceAtLeast(0.0) else null,
            unit = json.optionalString("unit") ?: json.optionalString("units"),
            sourceLabel = source.label,
            confidence = source.confidence
        )
    }

    private fun antigravityLineLabel(json: JSONObject): String? {
        val direct = json.optionalScalarString("l")
            ?: json.optionalScalarString("label")
            ?: json.optionalScalarString("title")
            ?: json.optionalScalarString("name")
            ?: json.optionalScalarString("displayName")
            ?: json.optionalScalarString("display_name")
            ?: json.optionalScalarString("metricName")
            ?: json.optionalScalarString("quotaName")
        val nested = json.optObject("metric")?.let(::antigravityLineLabel)
            ?: json.optObject("quota")?.let(::antigravityLineLabel)
            ?: json.optObject("usage")?.let(::antigravityLineLabel)
        return (direct ?: nested)?.let(::antigravityDisplayLabel)
    }

    private fun JSONObject.antigravityNumber(vararg keys: String): Double? {
        for (key in keys) {
            optionalNumber(key)?.let { return it }
        }
        return null
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

    private fun cursorPlanUsageBreakdownLines(usage: JSONObject?, source: ProviderPayloadSource, fallbackReset: String?): List<ProviderUsageLine> {
        if (usage == null) return emptyList()
        return buildList {
            cursorPercentBreakdownLine(
                usage,
                arrayOf("autoPercentUsed", "auto_percent_used", "autoUsagePercent", "auto_usage_percent"),
                "cursor:auto",
                "Auto usage",
                source,
                fallbackReset
            )?.let(::add)
            cursorPercentBreakdownLine(
                usage,
                arrayOf("apiPercentUsed", "api_percent_used", "apiUsagePercent", "api_usage_percent"),
                "cursor:api",
                "API usage",
                source,
                fallbackReset
            )?.let(::add)
            addAll(cursorBreakdownLines(usage.opt("breakdown"), source, fallbackReset))
            addAll(cursorBreakdownLines(usage.opt("usageBreakdown"), source, fallbackReset))
            listOf("onDemand", "onDemandUsage", "spendLimitUsage").forEach { key ->
                usage.optObject(key)
                    ?.toCursorUsageLine("cursor:on_demand", "On-demand usage", source, fallbackReset)
                    ?.let(::add)
            }
        }
    }

    private fun cursorPercentBreakdownLine(
        usage: JSONObject,
        keys: Array<String>,
        key: String,
        label: String,
        source: ProviderPayloadSource,
        fallbackReset: String?
    ): ProviderUsageLine? {
        val usedPercent = usage.cursorNumber(*keys) ?: return null
        return JSONObject()
            .put("used_percent", usedPercent)
            .withResetDate(cursorReset(usage) ?: fallbackReset)
            .toCursorUsageLine(key, label, source, fallbackReset)
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
                val key = keys.next()
                if (key in CursorNonRequestUsageContainers) continue
                val child = value.opt(key) as? JSONObject ?: continue
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
        val plan = individualUsage.optObject("plan")
        return buildList {
            plan?.toCursorUsageLine("cursor:total", "Total usage", source, fallbackReset)?.let(::add)
            addAll(cursorPlanUsageBreakdownLines(plan, source, fallbackReset))
            individualUsage.optObject("onDemand")
                ?.toCursorUsageLine("cursor:on_demand", "On-demand usage", source, fallbackReset)
                ?.let(::add)
        }
    }

    private data class CursorBreakdownLineIdentity(val key: String, val label: String)

    private fun cursorBreakdownLines(breakdown: Any?, source: ProviderPayloadSource, fallbackReset: String?): List<ProviderUsageLine> {
        if (breakdown == null || breakdown == JSONObject.NULL) return emptyList()
        val lines = mutableListOf<ProviderUsageLine>()
        fun addBreakdown(rawLabel: String?, rawValue: Any?) {
            if (rawValue == null || rawValue == JSONObject.NULL) return
            val item = when (rawValue) {
                is JSONObject -> rawValue
                is Number -> JSONObject().put("used_percent", rawValue.toDouble())
                else -> return
            }
            val identity = cursorBreakdownIdentity(rawLabel, item) ?: return
            item.toCursorUsageLine(identity.key, identity.label, source, fallbackReset)
                ?.let(lines::add)
        }
        when (breakdown) {
            is JSONObject -> {
                val keys = breakdown.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    addBreakdown(key, breakdown.opt(key))
                }
            }
            is JSONArray -> {
                for (index in 0 until breakdown.length()) {
                    val item = breakdown.opt(index)
                    if (item is JSONObject) {
                        val label = item.optionalScalarString("label")
                            ?: item.optionalScalarString("name")
                            ?: item.optionalScalarString("title")
                            ?: item.optionalScalarString("type")
                            ?: item.optionalScalarString("category")
                        addBreakdown(label, item)
                    }
                }
            }
        }
        return lines
    }

    private fun cursorBreakdownIdentity(rawLabel: String?, item: JSONObject): CursorBreakdownLineIdentity? {
        val label = rawLabel
            ?: item.optionalScalarString("label")
            ?: item.optionalScalarString("name")
            ?: item.optionalScalarString("title")
            ?: item.optionalScalarString("type")
            ?: item.optionalScalarString("category")
            ?: return null
        val compact = label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
        return when {
            compact.contains("auto") -> CursorBreakdownLineIdentity("cursor:auto", "Auto usage")
            compact == "api" || compact.contains("apiusage") || compact.contains("api") -> CursorBreakdownLineIdentity("cursor:api", "API usage")
            compact.contains("ondemand") || compact.contains("payasyougo") || compact.contains("usagebased") -> {
                CursorBreakdownLineIdentity("cursor:on_demand", "On-demand usage")
            }
            else -> null
        }
    }

    private fun JSONObject.toCursorUsageLine(
        key: String,
        label: String,
        source: ProviderPayloadSource,
        fallbackReset: String?
    ): ProviderUsageLine? {
        val usedPercent = cursorNumber("usedPercent", "used_percent", "totalPercentUsed", "total_percent_used", "utilization")
        val limit = cursorNumber("limit", "monthlyLimit", "totalLimit", "maxRequestUsage", "maxRequests", "requestLimit", "limitAmount", "individualLimit", "onDemandLimit")
        val remainingPercent = cursorNumber("remainingPercent", "remaining_percent", "remainingFraction", "remaining_fraction")
        val remaining = cursorNumber("remaining", "totalRemaining", "balance", "individualRemaining", "onDemandRemaining")
        val used = cursorNumber("totalSpend", "totalUsage", "spend", "used", "usage", "individualUsed", "individualUsage", "onDemandUsed", "onDemandUsage")
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

    private val CursorNonRequestUsageContainers = setOf(
        "planUsage",
        "individualUsage",
        "breakdown",
        "usageBreakdown",
        "onDemand",
        "onDemandUsage",
        "spendLimitUsage"
    )

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

    private fun JSONObject.toLine(
        key: String,
        label: String,
        source: ProviderPayloadSource,
        preferRemainingPercent: Boolean = false,
        preservePayloadLabel: Boolean = false
    ): ProviderUsageLine? {
        val used = if (preferRemainingPercent) {
            remainingBasedUsedPercent() ?: usedBasedPercent()
        } else {
            usedPercent()
        } ?: return null
        val remaining = (100 - used).coerceIn(0, 100)
        val effectiveLabel = if (preservePayloadLabel) optionalString("label") ?: optionalString("l") ?: label else label
        return ProviderUsageLine(
            key = key,
            label = effectiveLabel,
            remainingPercent = remaining / 100f,
            resetsAt = optionalString("resets_at") ?: optionalString("reset_at") ?: optionalString("resetAt") ?: optionalString("r"),
            resetText = optionalString("reset_text") ?: optionalString("resetText") ?: optionalString("t") ?: resetAfterText(),
            sourceLabel = source.label,
            confidence = source.confidence
        )
    }

    private fun JSONObject.withResetDate(resetDate: String?): JSONObject {
        if (resetDate.isNullOrBlank() || has("resetAt") || has("reset_at") || has("resets_at") || has("r")) return this
        return JSONObject(toString()).put("resetAt", resetDate)
    }

    private fun JSONObject.withCodexResetFallback(fallback: JSONObject?): JSONObject {
        if (hasAny("reset_text", "resetText", "t", "resets_at", "reset_at", "resetAt", "r")) return this
        if (fallback == null) return this
        val resetText = fallback.optionalString("reset_text")
            ?: fallback.optionalString("resetText")
            ?: fallback.optionalString("t")
            ?: fallback.resetAfterText()
        val resetAt = fallback.optionalString("resets_at")
            ?: fallback.optionalString("reset_at")
            ?: fallback.optionalString("resetAt")
            ?: fallback.optionalString("r")
        if (resetText.isNullOrBlank() && resetAt.isNullOrBlank()) return this
        val copy = JSONObject(toString())
        if (!resetText.isNullOrBlank()) copy.put("reset_text", resetText)
        if (!resetAt.isNullOrBlank()) copy.put("resets_at", resetAt)
        return copy
    }

    private fun JSONObject.withAntigravityResetAlias(fallbackReset: String?): JSONObject {
        val reset = antigravityReset(this) ?: fallbackReset
        if (reset.isNullOrBlank() || has("resetAt") || has("reset_at") || has("resets_at") || has("r")) return this
        return JSONObject(toString()).put("resetAt", reset)
    }

    private fun JSONObject.toCopilotQuotaLine(quotaKey: String, lineKey: String, label: String, source: ProviderPayloadSource): ProviderUsageLine? {
        val remaining = optObject("remaining") ?: return null
        val remainingValue = remaining.optionalNumber(quotaKey) ?: return null
        val limitValue = optObject("limits")?.optionalNumber(quotaKey)
        if (quotaKey == "premiumInteractions" && limitValue != null && limitValue <= 0.0 && remainingValue <= 0.0) return null
        val remainingPercent = remaining.optionalNumber("${quotaKey}Percentage")
            ?: if (limitValue != null && limitValue > 0.0) (remainingValue / limitValue) * 100.0 else return null
        val line = JSONObject().put("remaining_percent", remainingPercent)
        optionalString("resetDate")?.let { line.put("resetAt", it) }
        return line.toLine(lineKey, label, source)
    }

    private fun JSONObject.toCreditsLine(key: String, label: String, source: ProviderPayloadSource): ProviderUsageLine? {
        toLine(key, label, source)?.let { return it }
        val balance = optionalNumber("balance") ?: optionalNumber("remaining") ?: return null
        val limit = optionalNumber("limit") ?: optionalNumber("total") ?: optionalNumber("total_credits")
        if (limit == null) {
            if (source != ProviderPayloadSource.VISIBLE_DOM) return null
            return ProviderUsageLine(
                key = key,
                label = label,
                remainingText = "${formatNumber(balance)} credits left",
                remainingAmount = balance,
                unit = "credits",
                sourceLabel = source.label,
                confidence = source.confidence
            )
        }
        if (limit <= 0.0) return null
        return JSONObject()
            .put("remaining_percent", (balance / limit) * 100.0)
            .toLine(key, label, source)
    }

    private fun JSONObject.usedPercent(): Int? {
        return usedBasedPercent() ?: remainingBasedUsedPercent()
    }

    private fun JSONObject.usedBasedPercent(): Int? {
        optionalNumber("used_percent")?.let { return percent(it) }
        optionalNumber("usedPercent")?.let { return percent(it) }
        optionalNumber("usedPercentage")?.let { return percent(it) }
        optionalNumber("used_percentage")?.let { return percent(it) }
        optionalNumber("percent_used")?.let { return percent(it) }
        optionalNumber("totalPercentUsed")?.let { return percent(it) }
        optionalNumber("utilization")?.let { return percent(it) }
        optionalNumber("u")?.let { return percent(it) }
        return null
    }

    private fun JSONObject.remainingBasedUsedPercent(): Int? {
        optionalNumber("remaining_percent")?.let { return 100 - percentScale(it) }
        optionalNumber("remainingPercent")?.let { return 100 - percentScale(it) }
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

    private fun percentScale(value: Double): Int {
        return value.roundToInt().coerceIn(0, 100)
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }

    private fun JSONObject.optionalNumber(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.toDoubleOrNull()
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.optionalScalarString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is String -> value
            is Number -> value.toString()
            else -> null
        }?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun formatAmount(value: Double): String {
        val rounded = value.roundToInt()
        return if (kotlin.math.abs(value - rounded) < 0.001) {
            rounded.toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
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
            "geminipro", "googleaipro", "g1protier" -> "Pro"
            "geminiultra", "googleaiultra", "g1ultratier" -> "Ultra"
            "geminiplus", "googleaiplus" -> "Plus"
            "geminifree", "googleaifree" -> "Free"
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

    private fun antigravityPlan(json: JSONObject, usage: JSONObject): String? {
        return sequenceOf(
            json.optionalScalarString("plan"),
            usage.optionalScalarString("plan"),
            json.optionalScalarString("planName"),
            usage.optionalScalarString("planName"),
            json.optionalScalarString("plan_name"),
            usage.optionalScalarString("plan_name"),
            json.optionalScalarString("tier"),
            usage.optionalScalarString("tier"),
            json.optionalScalarString("subscriptionName"),
            usage.optionalScalarString("subscriptionName"),
            json.optObject("subscription")?.let(::antigravityPlanFromObject),
            usage.optObject("subscription")?.let(::antigravityPlanFromObject),
            json.optObject("plan")?.let(::antigravityPlanFromObject),
            usage.optObject("plan")?.let(::antigravityPlanFromObject),
            json.optObject("planInfo")?.let(::antigravityPlanFromObject),
            usage.optObject("planInfo")?.let(::antigravityPlanFromObject),
            json.optObject("tier")?.let(::antigravityPlanFromObject),
            usage.optObject("tier")?.let(::antigravityPlanFromObject)
        ).mapNotNull(::antigravityPlanLabel).firstOrNull()
    }

    private fun antigravityPlanFromObject(json: JSONObject): String? {
        return json.optionalScalarString("displayName")
            ?: json.optionalScalarString("display_name")
            ?: json.optionalScalarString("name")
            ?: json.optionalScalarString("label")
            ?: json.optionalScalarString("plan")
            ?: json.optionalScalarString("tier")
            ?: json.optionalScalarString("id")
    }

    private fun antigravityPlanLabel(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        val compact = trimmed.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
        if (compact == "antigravityunknown" || compact == "googleaiunknown" || compact == "unknown") return null
        return antigravityDisplayLabel(trimmed)
    }

    private fun antigravityDisplayLabel(value: String): String {
        val trimmed = value.trim()
        if (!trimmed.contains("_") && trimmed.any { it.isLowerCase() }) return trimmed
        return trimmed
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                when (token.lowercase(Locale.US)) {
                    "ai" -> "AI"
                    "api" -> "API"
                    "id" -> "ID"
                    "ui" -> "UI"
                    else -> token.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
                }
            }
            .ifBlank { trimmed }
    }

    private val AntigravityModelOrder = listOf(
        "gemini35flashhigh",
        "gemini35flashmedium",
        "gemini35flashlow",
        "gemini31prohigh",
        "gemini31prolow",
        "gemini3flash",
        "gemini31flashimage",
        "gemini31flashlite",
        "claudesonnet46thinking",
        "claudeopus46thinking",
        "gptoss120bmedium"
    )

    private fun geminiLineLabel(value: String?): String? {
        val raw = value?.trim()?.lowercase(Locale.US) ?: return null
        val compact = raw.replace(Regex("[^a-z0-9]+"), "")
        GEMINI_MODEL_LABELS.firstOrNull { (modelId, label) ->
            raw == modelId || raw == label || compact == compactGeminiLabel(modelId) || compact == compactGeminiLabel(label)
        }?.let { return it.second }
        return when {
            compact == "geminideepresearch" || compact == "deepresearch" -> "Gemini Deep Research"
            compact.contains("deepresearch") -> "Gemini Deep Research"
            raw.startsWith("gemini-") -> raw
                .removePrefix("gemini-")
                .replace("-preview", "-pre")
                .replace('-', ' ')
            else -> null
        }
    }

    private val GEMINI_MODEL_LABELS = listOf(
        "gemini-3.1-pro-preview" to "3.1 pro-pre",
        "gemini-3.1-flash-lite-preview" to "3.1 flash-lite-pre",
        "gemini-3.1-flash-lite" to "3.1 flash-lite",
        "gemini-3-pro-preview" to "3 pro-pre",
        "gemini-3-flash-preview" to "3 flash-pre",
        "gemini-2.5-pro" to "2.5 pro",
        "gemini-2.5-pro-preview" to "2.5 pro-pre",
        "gemini-2.5-flash-lite" to "2.5 flash-lite",
        "gemini-2.5-flash" to "2.5 flash"
    )

    private fun compactGeminiLabel(value: String): String {
        return value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
    }

    private fun geminiLineKey(label: String): String {
        return label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
    }

    private fun isUnavailableFreeGeminiProLine(plan: String?, line: ProviderUsageLine): Boolean {
        if (plan != "Free") return false
        if (!compactGeminiLabel(line.label).contains("pro")) return false
        if ((line.remainingPercent ?: return false) > 0f) return false
        return isEpochReset(line.resetsAt)
    }

    private fun isEpochReset(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.startsWith("1970-01-01")) return true
        return trimmed.toLongOrNull() == 0L
    }
}
