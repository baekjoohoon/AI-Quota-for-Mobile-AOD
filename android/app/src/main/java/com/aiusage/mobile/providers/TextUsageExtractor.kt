package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageLine
import com.aiusage.mobile.local.ProviderUsageSnapshot
import com.aiusage.mobile.local.UsageSeverity
import org.json.JSONObject
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

object TextUsageExtractor {
    fun extract(providerId: ProviderId, visibleText: String): ProviderUsageSnapshot {
        val structuredSnapshot = extractStructuredUsage(providerId, visibleText)
        if (structuredSnapshot != null) return structuredSnapshot

        val providerApiSnapshot = extractProviderApiResponse(providerId, visibleText)
        if (providerApiSnapshot != null) return providerApiSnapshot

        val normalized = normalize(visibleText)
        val percentSnapshot = extractPercentUsage(providerId, normalized)
        if (percentSnapshot != null) return percentSnapshot

        val quotaSnapshot = extractUsedQuota(providerId, normalized)
        if (quotaSnapshot != null) return quotaSnapshot

        val countSnapshot = extractRemainingCount(providerId, normalized)
        if (countSnapshot != null) return countSnapshot

        val planSnapshot = extractTrustedPlanEvidence(providerId, normalized)
        if (planSnapshot != null) return planSnapshot

        return ProviderUsageSnapshot.unavailable(
            providerId = providerId,
            message = "No visible usage limit found for ${providerId.displayName}."
        )
    }

    private fun extractProviderApiResponse(
        providerId: ProviderId,
        visibleText: String
    ): ProviderUsageSnapshot? {
        val trimmed = visibleText.trim()
        if (!trimmed.startsWith("{")) return null
        val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        return when (providerId) {
            ProviderId.CLAUDE -> extractClaudeUsageApiResponse(json)
            ProviderId.COPILOT -> extractCopilotEntitlementResponse(json)
            else -> null
        }
    }

    private fun extractClaudeUsageApiResponse(json: JSONObject): ProviderUsageSnapshot? {
        val lines = listOf("five_hour", "seven_day", "seven_day_omelette")
            .mapNotNull { key ->
                val item = json.optJSONObject(key) ?: return@mapNotNull null
                val utilization = item.optNumber("utilization") ?: return@mapNotNull null
                val usedPercent = utilization.coerceIn(0.0, 100.0)
                val remainingRatio = ((100.0 - usedPercent) / 100.0).coerceIn(0.0, 1.0).toFloat()
                ProviderUsageLine(
                    label = key.toDisplayLabel(),
                    remainingPercent = remainingRatio,
                    remainingText = "${formatPercent(remainingRatio.toDouble() * 100.0)}% left",
                    resetText = null,
                    detailText = "${formatPercent(usedPercent)}% used",
                    severity = severityForStructured(remainingRatio),
                    category = "usage_window",
                    windowText = when (key) {
                        "five_hour" -> "5 hours"
                        "seven_day", "seven_day_omelette" -> "7 days"
                        else -> null
                    },
                    resetsAt = item.optNullableString("resets_at"),
                    sourceLabel = "/api/organizations/:id/usage",
                    confidence = 0.98f
                )
            }
        if (lines.isEmpty()) return null
        return ProviderUsageSnapshot(
            providerId = ProviderId.CLAUDE,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            lines = lines
        )
    }

    private fun extractCopilotEntitlementResponse(json: JSONObject): ProviderUsageSnapshot? {
        val quotas = json.optJSONObject("quotas") ?: return null
        val remaining = quotas.optJSONObject("remaining") ?: return null
        val limits = quotas.optJSONObject("limits") ?: JSONObject()
        val resetDate = quotas.optNullableString("resetDate")?.toResetInstantString()
        val lines = buildList {
            remaining.optNumber("chat")?.let { chatRemaining ->
                val chatLimit = limits.optFirstNumber("chat", "messages")
                val remainingPercent = remaining.optNumber("chatPercentage")
                    ?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() }
                add(
                    remainingCounterLine(
                        label = "Chat",
                        remaining = chatRemaining,
                        limit = chatLimit,
                        unit = "messages",
                        remainingPercent = remainingPercent,
                        resetsAt = resetDate,
                        category = "messages"
                    )
                )
            }
            remaining.optNumber("completions")?.let { completionsRemaining ->
                val completionsLimit = limits.optFirstNumber(
                    "completions",
                    "completion",
                    "codeCompletions",
                    "code_completions"
                ) ?: defaultCopilotCompletionsLimit(json, completionsRemaining)
                val remainingPercent = remaining.optNumber("completionsPercentage")
                    ?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() }
                add(
                    remainingCounterLine(
                        label = "Completions",
                        remaining = completionsRemaining,
                        limit = completionsLimit,
                        unit = "completions",
                        remainingPercent = remainingPercent,
                        resetsAt = resetDate,
                        category = "completions"
                    )
                )
            }
            val premiumLimit = limits.optNumber("premiumInteractions")
            val premiumRemaining = remaining.optNumber("premiumInteractions")
            if ((premiumLimit ?: 0.0) > 0.0 || (premiumRemaining ?: 0.0) > 0.0) {
                add(
                    amountLine(
                        label = "Premium requests",
                        limit = premiumLimit,
                        remaining = premiumRemaining,
                        remainingPercent = remaining.optNumber("premiumInteractionsPercentage")
                            ?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() },
                        unit = "premium requests",
                        resetsAt = resetDate,
                        category = "premium_requests"
                    )
                )
            }
        }
        if (lines.isEmpty()) return null
        return ProviderUsageSnapshot(
            providerId = ProviderId.COPILOT,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            planLabel = json.optNullableString("plan")?.toDisplayLabel(),
            lines = lines
        )
    }

    private fun remainingCounterLine(
        label: String,
        remaining: Double,
        limit: Double?,
        unit: String,
        remainingPercent: Float?,
        resetsAt: String?,
        category: String
    ): ProviderUsageLine {
        val used = if (limit != null && limit > 0.0) {
            (limit - remaining).coerceAtLeast(0.0)
        } else {
            null
        }
        val ratio = when {
            limit != null && limit > 0.0 -> (remaining / limit).coerceIn(0.0, 1.0).toFloat()
            remainingPercent != null -> remainingPercent
            else -> null
        }
        return ProviderUsageLine(
            label = label,
            remainingPercent = ratio,
            remainingText = if (limit != null && limit > 0.0) {
                remainingLimitText(remaining, limit, unit, ratio ?: 0f)
            } else {
                remainingOnlyText(remaining, unit)
            },
            resetText = null,
            detailText = if (limit != null && limit > 0.0 && used != null) {
                usedLimitText(used, limit, (used / limit) * 100.0)
            } else {
                null
            },
            severity = ratio?.let(::severityForStructured) ?: UsageSeverity.UNKNOWN,
            usedAmount = used,
            limitAmount = limit,
            remainingAmount = remaining,
            unit = unit,
            category = category,
            windowText = "monthly",
            resetsAt = resetsAt,
            sourceLabel = "/github-copilot/chat/entitlement",
            confidence = 0.98f
        )
    }

    private fun defaultCopilotCompletionsLimit(json: JSONObject, remaining: Double): Double? {
        val plan = json.optNullableString("plan").orEmpty().lowercase(Locale.US)
        val licenseType = json.optNullableString("licenseType").orEmpty().lowercase(Locale.US)
        return if (
            plan == "free" ||
            licenseType == "licensed_limited" ||
            remaining in 0.0..COPILOT_FREE_COMPLETIONS_LIMIT
        ) {
            COPILOT_FREE_COMPLETIONS_LIMIT
        } else {
            null
        }
    }

    private fun amountLine(
        label: String,
        limit: Double?,
        remaining: Double?,
        remainingPercent: Float?,
        unit: String,
        resetsAt: String?,
        category: String
    ): ProviderUsageLine {
        val used = if (limit != null && remaining != null) {
            (limit - remaining).coerceAtLeast(0.0)
        } else {
            null
        }
        val ratio = when {
            limit != null && limit > 0.0 && remaining != null -> {
                (remaining / limit).coerceIn(0.0, 1.0).toFloat()
            }
            remainingPercent != null -> remainingPercent
            else -> null
        }
        return ProviderUsageLine(
            label = label,
            remainingPercent = ratio,
            remainingText = if (limit != null && limit > 0.0 && remaining != null) {
                remainingLimitText(remaining, limit, unit, ratio ?: 0f)
            } else {
                remainingOnlyText((remaining ?: 0.0), unit)
            },
            detailText = if (limit != null && limit > 0.0 && used != null) {
                usedLimitText(used, limit, (used / limit) * 100.0)
            } else {
                null
            },
            severity = ratio?.let(::severityForStructured) ?: UsageSeverity.UNKNOWN,
            usedAmount = used,
            limitAmount = limit,
            remainingAmount = remaining,
            unit = unit,
            category = category,
            windowText = "monthly",
            resetsAt = resetsAt,
            sourceLabel = "/github-copilot/chat/entitlement",
            confidence = 0.98f
        )
    }

    private fun extractStructuredUsage(
        providerId: ProviderId,
        visibleText: String
    ): ProviderUsageSnapshot? {
        val trimmed = visibleText.trim()
        if (!trimmed.startsWith("{") || !trimmed.contains("\"s\"") || !trimmed.contains("\"d\"")) {
            return null
        }
        val response = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val status = response.optNullableString("s") ?: response.optNullableString("status")
        val provider = response.optNullableString("provider")
        if (provider != null && !provider.equals(providerId.storageId, ignoreCase = true)) {
            return ProviderUsageSnapshot.unavailable(
                providerId = providerId,
                message = "Usage payload belonged to $provider."
            )
        }
        if (!status.equals("s", ignoreCase = true) && !status.equals("success", ignoreCase = true)) {
            return ProviderUsageSnapshot.unavailable(
                providerId = providerId,
                message = response.optNullableString("m")
                    ?: response.optNullableString("errorMessage")
                    ?: "No usage limits found in local provider session."
            )
        }

        val data = response.optJSONObject("d") ?: response.optJSONObject("data")
        if (data == null) {
            return ProviderUsageSnapshot.unavailable(
                providerId = providerId,
                message = "Provider usage response did not include data."
            )
        }

        val plan = data.optNullableString("p")
            ?: data.optNullableString("plan")
            ?: data.optNullableString("planLabel")
        val limits = data.optJSONArray("x") ?: data.optJSONArray("limits")
        val rawLines = buildList {
            if (limits != null) {
                for (index in 0 until limits.length()) {
                    val limit = limits.optJSONObject(index) ?: continue
                    val explicitUtilization = limit.optNumber("u")
                        ?: limit.optNumber("utilization")
                    val limitAmount = limit.optNumber("limit") ?: limit.optNumber("limitAmount")
                    val explicitUsedAmount = limit.optNumber("used") ?: limit.optNumber("usedAmount")
                    val explicitRemainingAmount = limit.optNumber("remaining") ?: limit.optNumber("remainingAmount")
                    val remainingPercent = limit.optNumber("remainingPercent")
                    val remainingAmount = explicitRemainingAmount
                        ?: if (limitAmount != null && explicitUsedAmount != null) {
                            limitAmount - explicitUsedAmount
                        } else {
                            null
                        }
                    val usedAmount = explicitUsedAmount
                        ?: if (limitAmount != null && remainingAmount != null) {
                            limitAmount - remainingAmount
                        } else {
                            null
                        }
                    val usedPercent = explicitUtilization
                        ?: if (limitAmount != null && limitAmount > 0.0 && usedAmount != null) {
                            (usedAmount / limitAmount) * 100.0
                        } else {
                            remainingPercent?.let { (1.0 - it) * 100.0 }
                        }
                    val reset = limit.optNullableString("t")
                        ?: limit.optNullableString("resetText")
                    val unit = limit.optNullableString("unit")
                    val label = (limit.optNullableString("l")
                        ?: limit.optNullableString("title")
                        ?: unit
                        ?: "Usage").toDisplayLabel()
                    val startsAt = limit.optTimeString("startsAt", "s")
                    val resetsAt = limit.optTimeString("resetsAt", "r")
                    if (isCursorZeroLimitAsFull(providerId, limitAmount, remainingAmount, label, unit)) {
                        add(
                            ProviderUsageLine(
                                label = "Total usage",
                                remainingPercent = 1f,
                                remainingText = "100% left",
                                resetText = reset,
                                detailText = "0% used",
                                severity = UsageSeverity.NORMAL,
                                usedAmount = 0.0,
                                limitAmount = null,
                                remainingAmount = null,
                                unit = "percent",
                                category = "usage_window",
                                windowText = limit.optNullableString("window") ?: limit.optNullableString("windowText"),
                                startsAt = startsAt,
                                resetsAt = resetsAt,
                                sourceLabel = limit.optNullableString("source") ?: limit.optNullableString("sourceLabel"),
                                confidence = limit.optNumber("confidence")?.coerceIn(0.0, 1.0)?.toFloat()
                            )
                        )
                        continue
                    }
                    if (usedPercent == null && remainingAmount != null) {
                        add(
                            ProviderUsageLine(
                                label = label,
                                remainingPercent = null,
                                remainingText = remainingOnlyText(remainingAmount, unit),
                                resetText = reset,
                                detailText = null,
                                severity = UsageSeverity.UNKNOWN,
                                usedAmount = usedAmount,
                                limitAmount = limitAmount,
                                remainingAmount = remainingAmount,
                                unit = unit,
                                category = limit.optNullableString("category") ?: inferCategory(label, unit),
                                windowText = limit.optNullableString("window") ?: limit.optNullableString("windowText"),
                                startsAt = startsAt,
                                resetsAt = resetsAt,
                                sourceLabel = limit.optNullableString("source") ?: limit.optNullableString("sourceLabel"),
                                confidence = limit.optNumber("confidence")?.coerceIn(0.0, 1.0)?.toFloat()
                            )
                        )
                        continue
                    }
                    if (usedPercent == null) continue
                    val remainingRatio = when {
                        limitAmount != null && limitAmount > 0.0 && remainingAmount != null -> {
                            (remainingAmount / limitAmount).coerceIn(0.0, 1.0).toFloat()
                        }
                        remainingPercent != null -> remainingPercent.coerceIn(0.0, 1.0).toFloat()
                        else -> ((100.0 - usedPercent.coerceIn(0.0, 100.0)) / 100.0)
                            .coerceIn(0.0, 1.0)
                            .toFloat()
                    }
                    add(
                        ProviderUsageLine(
                            label = label,
                            remainingPercent = remainingRatio,
                            remainingText = remainingLimitText(remainingAmount, limitAmount, unit, remainingRatio),
                            resetText = reset,
                            detailText = usedLimitText(usedAmount, limitAmount, usedPercent),
                            severity = severityForStructured(remainingRatio),
                            usedAmount = usedAmount,
                            limitAmount = limitAmount,
                            remainingAmount = remainingAmount,
                            unit = unit,
                            category = limit.optNullableString("category") ?: inferCategory(label, unit),
                            windowText = limit.optNullableString("window") ?: limit.optNullableString("windowText"),
                            startsAt = startsAt,
                            resetsAt = resetsAt,
                            sourceLabel = limit.optNullableString("source") ?: limit.optNullableString("sourceLabel"),
                            confidence = limit.optNumber("confidence")?.coerceIn(0.0, 1.0)?.toFloat()
                        )
                    )
                }
            }
        }
        val lines = normalizeProviderLines(
            providerId = providerId,
            lines = rawLines.filterNot { it.isIgnoredExtractedLine(providerId) },
            plan = plan
        )

        val planLabel = structuredPlanLabel(providerId, plan, lines)

        if (lines.isEmpty()) {
            if (rawLines.isNotEmpty()) {
                return ProviderUsageSnapshot.unavailable(
                    providerId = providerId,
                    message = response.optNullableString("m")
                        ?: "No usage limits found in local provider session."
                )
            }
            return ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.CONNECTED,
                refreshState = ProviderRefreshState.IDLE,
                planLabel = planLabel,
                lines = emptyList(),
                message = response.optNullableString("m")
                    ?: "No usage limits found in local provider session."
            )
        }

        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            planLabel = planLabel,
            lines = lines
        )
    }

    private fun extractPercentUsage(
        providerId: ProviderId,
        normalized: NormalizedText
    ): ProviderUsageSnapshot? {
        val usageMatch = USAGE_PERCENT.find(normalized.fullText) ?: return null
        val percent = usageMatch.groupValues[1].toDoubleOrNull() ?: return null
        val ratio = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
        val remainingWord = usageMatch.groupValues[2].lowercase(Locale.US)
        val line = ProviderUsageLine(
            label = "Usage",
            remainingPercent = ratio,
            remainingText = "${formatPercent(percent)}% $remainingWord",
            resetText = resetText(normalized),
            severity = severityFor(ratio)
        )

        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            lines = listOf(line)
        )
    }

    private fun extractUsedQuota(
        providerId: ProviderId,
        normalized: NormalizedText
    ): ProviderUsageSnapshot? {
        val usageMatch = USED_OF_LIMIT_WITH_USED.find(normalized.fullText)
            ?: USED_OF_LIMIT.find(normalized.fullText)
            ?: return null
        val used = usageMatch.groupValues[1].toCountOrNull() ?: return null
        val limit = usageMatch.groupValues[2].toCountOrNull()?.takeIf { it > 0 } ?: return null
        val remaining = (limit - used).coerceAtLeast(0)
        val ratio = (remaining.toDouble() / limit.toDouble()).coerceIn(0.0, 1.0).toFloat()
        val unit = usageMatch.groupValues[3].trim().ifBlank { "requests" }
        val line = ProviderUsageLine(
            label = unit.toDisplayLabel(),
            remainingPercent = ratio,
            remainingText = "${formatCount(remaining)} of ${formatCount(limit)} left",
            resetText = resetText(normalized),
            detailText = "${formatCount(used)} used of ${formatCount(limit)}",
            severity = severityFor(ratio),
            usedAmount = used.toDouble(),
            limitAmount = limit.toDouble(),
            remainingAmount = remaining.toDouble(),
            unit = unit,
            category = inferCategory(unit, unit)
        )

        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            lines = listOf(line)
        )
    }

    private fun extractRemainingCount(
        providerId: ProviderId,
        normalized: NormalizedText
    ): ProviderUsageSnapshot? {
        val usageMatch = COUNT_REMAINING.find(normalized.fullText) ?: return null
        val remaining = usageMatch.groupValues[1].toCountOrNull() ?: return null
        val unit = usageMatch.groupValues[2].trim().ifBlank { "items" }
        val remainingWord = usageMatch.groupValues[3].lowercase(Locale.US)
        val line = ProviderUsageLine(
            label = unit.toDisplayLabel(),
            remainingPercent = null,
            remainingText = "${formatCount(remaining)} $unit $remainingWord",
            resetText = resetText(normalized),
            severity = UsageSeverity.UNKNOWN,
            remainingAmount = remaining.toDouble(),
            unit = unit,
            category = inferCategory(unit, unit)
        )

        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            lines = listOf(line)
        )
    }

    private fun extractTrustedPlanEvidence(
        providerId: ProviderId,
        normalized: NormalizedText
    ): ProviderUsageSnapshot? {
        if (!TRUSTED_PLAN_EVIDENCE.containsMatchIn(normalized.fullText)) return null
        val planMatch = PLAN_LABEL.find(normalized.fullText) ?: return null
        val rawPlan = planMatch.groupValues[1].trim()
        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            planLabel = rawPlan.toDisplayLabel(),
            lines = emptyList(),
            message = "No usage limits found in local provider session."
        )
    }

    private fun normalize(visibleText: String): NormalizedText {
        val lines = visibleText
            .replace('\u00A0', ' ')
            .lines()
            .map { line -> line.replace(WHITESPACE, " ").trim() }
            .filter { it.isNotBlank() }
        return NormalizedText(lines = lines, fullText = lines.joinToString(" "))
    }

    private fun resetText(normalized: NormalizedText): String? {
        val match = normalized.lines.firstNotNullOfOrNull { line -> RESET_PHRASE.find(line) }
            ?: RESET_PHRASE.find(normalized.fullText)
            ?: return null
        return match.value
            .replace(WHITESPACE, " ")
            .trim()
            .replaceFirstChar { char -> char.uppercase() }
    }

    private fun severityFor(ratio: Float): UsageSeverity {
        return when {
            ratio < DANGER_THRESHOLD -> UsageSeverity.DANGER
            ratio < WARNING_THRESHOLD -> UsageSeverity.WARNING
            else -> UsageSeverity.NORMAL
        }
    }

    private fun severityForStructured(ratio: Float): UsageSeverity {
        return when {
            ratio <= DANGER_THRESHOLD -> UsageSeverity.DANGER
            ratio <= WARNING_THRESHOLD -> UsageSeverity.WARNING
            else -> UsageSeverity.NORMAL
        }
    }

    private fun formatPercent(value: Double): String {
        return DecimalFormat("0.#", DecimalFormatSymbols(Locale.US)).apply {
            roundingMode = RoundingMode.HALF_UP
        }.format(value)
    }

    private fun remainingLimitText(
        remaining: Double?,
        limit: Double?,
        unit: String?,
        remainingRatio: Float
    ): String {
        if (remaining != null && limit != null && limit > 0.0) {
            val unitText = unit?.trim().orEmpty()
            val unitSuffix = if (unitText.isNotBlank()) " $unitText" else ""
            return "${formatAmount(remaining.coerceAtLeast(0.0))} of ${formatAmount(limit)}$unitSuffix left"
        }
        return "${formatPercent(remainingRatio.toDouble() * 100.0)}% left"
    }

    private fun remainingOnlyText(remaining: Double, unit: String?): String {
        val unitText = unit?.trim().orEmpty()
        val unitSuffix = if (unitText.isNotBlank()) " $unitText" else ""
        return "${formatAmount(remaining.coerceAtLeast(0.0))}$unitSuffix left"
    }

    private fun usedLimitText(
        used: Double?,
        limit: Double?,
        usedPercent: Double
    ): String {
        if (used != null && limit != null && limit > 0.0) {
            return "${formatAmount(used.coerceAtLeast(0.0))} used of ${formatAmount(limit)}"
        }
        return "${formatPercent(usedPercent.coerceIn(0.0, 100.0))}% used"
    }

    private fun inferCategory(label: String?, unit: String?): String? {
        val text = "${label.orEmpty()} ${unit.orEmpty()}".lowercase(Locale.US)
        return when {
            "premium request" in text -> "premium_requests"
            "fast request" in text -> "fast_requests"
            "included usage" in text || "usd" in text || "$" in text || "credit" in text -> "included_usage"
            "message" in text || "chat" in text -> "messages"
            "completion" in text -> "completions"
            "token" in text -> "tokens"
            "image" in text -> "images"
            "video" in text -> "videos"
            else -> null
        }
    }

    private fun isCursorZeroLimitAsFull(
        providerId: ProviderId,
        limitAmount: Double?,
        remainingAmount: Double?,
        label: String,
        unit: String?
    ): Boolean {
        if (providerId != ProviderId.CURSOR) return false
        if (limitAmount != 0.0 || remainingAmount != 0.0) return false
        val text = "$label ${unit.orEmpty()}".lowercase(Locale.US)
        return "included usage" in text ||
            "total usage" in text ||
            "planusage" in text ||
            "usd" in text
    }

    private fun ProviderUsageLine.isIgnoredExtractedLine(providerId: ProviderId): Boolean {
        val labelText = listOf(label, unit).joinToString(" ").lowercase(Locale.US)
        val allText = listOf(label, unit, sourceLabel).joinToString(" ").lowercase(Locale.US)
        if (Regex("""\b(sitemap|completed)\b""").containsMatchIn(labelText)) return true
        if (
            providerId == ProviderId.CLAUDE &&
            labelText.matches(Regex("""rate[_\s-]?limit|session|weekly""")) &&
            remainingPercent == null
        ) {
            return true
        }
        if (providerId == ProviderId.COPILOT) {
            if ("sitemap" in allText) return true
            if ("/features/copilot/plans" in allText) return true
            if (Regex("""\b[a-z0-9-]+\.(com|net|org|io|dev|ai)\b""").containsMatchIn(labelText)) return true
            if (
                !Regex(
                    """\b(copilot|premium\s+requests?|chat|messages?|code\s+completions?|completions?|usage|limit|quota|remaining|billing|entitlement)\b"""
                ).containsMatchIn(allText)
            ) {
                return true
            }
        }
        return false
    }

    private fun normalizeProviderLines(
        providerId: ProviderId,
        lines: List<ProviderUsageLine>,
        plan: String?
    ): List<ProviderUsageLine> {
        val providerNormalized = when (providerId) {
            ProviderId.CLAUDE -> normalizeClaudeRateLimitLabels(lines)
            ProviderId.GEMINI -> normalizeGeminiUsageLines(lines)
            ProviderId.CURSOR -> normalizeCursorUsageLines(lines)
            else -> lines
        }
        return providerNormalized
    }

    private fun normalizeGeminiUsageLines(lines: List<ProviderUsageLine>): List<ProviderUsageLine> {
        val order = mapOf(
            "pro" to 0,
            "flash" to 1,
            "deep research" to 2
        )
        return lines.sortedWith(
            compareBy<ProviderUsageLine> {
                order[it.label.lowercase(Locale.US)] ?: 100
            }.thenBy { it.label.lowercase(Locale.US) }
        )
    }

    private fun structuredPlanLabel(
        providerId: ProviderId,
        rawPlan: String?,
        lines: List<ProviderUsageLine>
    ): String? {
        rawPlan?.trim()?.takeIf { it.isNotBlank() }?.let { return it.toDisplayLabel() }
        return null
    }

    private fun normalizeClaudeRateLimitLabels(lines: List<ProviderUsageLine>): List<ProviderUsageLine> {
        val withoutGenericFragments = lines.filterNot { line ->
            val label = line.label.replace(' ', '_').lowercase(Locale.US)
            val source = line.sourceLabel.orEmpty().lowercase(Locale.US)
            val window = line.windowText.orEmpty().lowercase(Locale.US)
            line.remainingPercent == null &&
                line.limitAmount == null &&
                line.usedAmount == null &&
                (source == "/new" || source == "/" || source.isBlank()) &&
                (label in setOf("rate_limit", "session", "weekly") || window in setOf("session", "weekly"))
        }
        val apiUsageLines = lines.filter { line ->
            val label = line.label.replace(' ', '_').lowercase(Locale.US)
            val source = line.sourceLabel.orEmpty().lowercase(Locale.US)
            source.contains("/api/organizations/:id/usage") ||
                label in setOf("five_hour", "seven_day", "seven_day_omelette")
        }
        if (apiUsageLines.isNotEmpty()) {
            return apiUsageLines.map { line ->
                val label = line.label.replace(' ', '_').lowercase(Locale.US)
                line.copy(
                    category = line.category ?: "usage_window",
                    windowText = line.windowText ?: when (label) {
                        "five_hour" -> "5 hours"
                        "seven_day", "seven_day_omelette" -> "7 days"
                        else -> null
                    }
                )
            }
        }
        val rateLimitLines = withoutGenericFragments.filter { line ->
            line.label.equals("rate_limit", ignoreCase = true) ||
                line.label.equals("Rate_limit", ignoreCase = true)
        }
        if (rateLimitLines.size < 2) return withoutGenericFragments
        var index = 0
        return withoutGenericFragments.map { line ->
            if (!rateLimitLines.contains(line)) {
                line
            } else {
                val label = if (index == 0) "Session" else "Weekly"
                index += 1
                line.copy(
                    label = label,
                    category = line.category ?: "usage_window",
                    windowText = line.windowText ?: if (label == "Session") "session" else "weekly"
                )
            }
        }
    }

    private fun normalizeCursorUsageLines(lines: List<ProviderUsageLine>): List<ProviderUsageLine> {
        val normalizedLines = lines.map { line ->
            val normalized = line.label
                .replace('_', ' ')
                .replace('-', ' ')
                .replace(WHITESPACE, " ")
                .trim()
                .lowercase(Locale.US)
            val modelRequestLine = normalized.matches(Regex("""gpt\s*\d.*""")) ||
                normalized.matches(Regex("""claude\s*\d.*""")) ||
                normalized in setOf("request usage", "requests")
            val totalUsageLine = normalized in setOf(
                "included usage",
                "planusage",
                "plan usage",
                "total usage"
            ) || modelRequestLine
            if (!totalUsageLine) {
                line
            } else {
                line.copy(
                    label = "Total usage",
                    category = line.category ?: "usage_window",
                    windowText = line.windowText ?: "monthly"
                )
            }
        }
        val deduped = LinkedHashMap<String, ProviderUsageLine>()
        normalizedLines.forEach { line ->
            val key = if (line.label.equals("Total usage", ignoreCase = true)) {
                "cursor-total-usage"
            } else {
                listOf(line.label, line.category, line.windowText).joinToString("|").lowercase(Locale.US)
            }
            val existing = deduped[key]
            if (existing == null || line.isBetterCursorLineThan(existing)) {
                deduped[key] = line
            }
        }
        return deduped.values.toList()
    }

    private fun ProviderUsageLine.isBetterCursorLineThan(existing: ProviderUsageLine): Boolean {
        val score = cursorLineScore()
        val existingScore = existing.cursorLineScore()
        return when {
            score != existingScore -> score > existingScore
            (confidence ?: 0f) != (existing.confidence ?: 0f) -> (confidence ?: 0f) > (existing.confidence ?: 0f)
            else -> false
        }
    }

    private fun ProviderUsageLine.cursorLineScore(): Int {
        var score = 0
        if (remainingPercent != null) score += 8
        if (limitAmount != null && limitAmount > 0.0) score += 6
        if (!resetsAt.isNullOrBlank() || !resetText.isNullOrBlank()) score += 4
        if (remainingAmount != null) score += 1
        return score
    }

    private fun formatAmount(value: Double): String {
        return DecimalFormat("#,##0.###", DecimalFormatSymbols(Locale.US)).apply {
            roundingMode = RoundingMode.HALF_UP
        }.format(value)
    }

    private fun formatCount(value: Int): String {
        return DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(value)
    }

    private fun String.toResetInstantString(): String {
        runCatching { Instant.parse(this) }.getOrNull()?.let { return it.toString() }
        runCatching {
            return LocalDate.parse(this).atStartOfDay().toInstant(ZoneOffset.UTC).toString()
        }
        return this
    }

    private fun String.toCountOrNull(): Int? {
        return replace(",", "").toIntOrNull()
    }

    private fun String.toDisplayLabel(): String {
        val normalized = replace(WHITESPACE, " ").trim()
        return normalized.ifBlank { "Usage" }.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
        }
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).trim().takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optTimeString(primaryName: String, fallbackEpochName: String): String? {
        optNullableString(primaryName)?.let { return it }
        if (!has(fallbackEpochName) || isNull(fallbackEpochName)) return null
        return when (val value = opt(fallbackEpochName)) {
            is Number -> runCatching { Instant.ofEpochMilli(value.toLong()).toString() }.getOrNull()
            is String -> {
                val trimmed = value.trim()
                trimmed.toLongOrNull()
                    ?.let { epochMillis -> runCatching { Instant.ofEpochMilli(epochMillis).toString() }.getOrNull() }
                    ?: trimmed.takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    private fun JSONObject.optNumber(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun JSONObject.optFirstNumber(vararg names: String): Double? {
        names.forEach { name ->
            optNumber(name)?.let { return it }
        }
        return null
    }

    private data class NormalizedText(
        val lines: List<String>,
        val fullText: String
    )

    private val WHITESPACE = Regex("\\s+")
    private val USAGE_PERCENT = Regex("""\b([0-9]{1,3}(?:\.[0-9]+)?)\s*%\s*(left|remaining)\b""", RegexOption.IGNORE_CASE)
    private val USED_OF_LIMIT_WITH_USED = Regex(
        """\b([0-9][0-9,]*)\s*(?:/|of)\s*([0-9][0-9,]*)\s+([A-Za-z가-힣][A-Za-z가-힣\s-]{0,40}?)\s+(?:used|사용)\b""",
        RegexOption.IGNORE_CASE
    )
    private val USED_OF_LIMIT = Regex(
        """\b([0-9][0-9,]*)\s*(?:/|of)\s*([0-9][0-9,]*)(?:\s+([A-Za-z가-힣][A-Za-z가-힣\s-]{0,40}?))?\b""",
        RegexOption.IGNORE_CASE
    )
    private val COUNT_REMAINING = Regex(
        """\b([0-9][0-9,]*)\s+([A-Za-z가-힣][A-Za-z가-힣\s-]{0,40}?)\s+(left|remaining|남음|잔여)\b""",
        RegexOption.IGNORE_CASE
    )
    private val PLAN_LABEL = Regex(
        """\b(?:plan|subscription|구독|요금제)\b["':=\s-]*(free|plus|pro|max|team|business|enterprise|ultra|advanced)\b""",
        RegexOption.IGNORE_CASE
    )
    private val TRUSTED_PLAN_EVIDENCE = Regex(
        """\b(localStorage|sessionStorage|indexedDB|endpoint|billing|subscription|account|profile|settings|plan|tier)\b""",
        RegexOption.IGNORE_CASE
    )
    private val RESET_PHRASE = Regex("""\b(?:resets?|reset)\b(?:\s+[^.!?;|,]+)?""", RegexOption.IGNORE_CASE)
    private const val DANGER_THRESHOLD = 0.15f
    private const val WARNING_THRESHOLD = 0.35f
    private const val COPILOT_FREE_COMPLETIONS_LIMIT = 4000.0
}
