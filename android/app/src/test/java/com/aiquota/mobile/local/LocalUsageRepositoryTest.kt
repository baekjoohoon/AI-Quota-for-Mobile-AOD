package com.aiquota.mobile.local

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalUsageRepositoryTest {
    @Test
    fun readSnapshotsPersistsRecoveredStaleState() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()
        val method = source.substringAfter("fun readSnapshots()")
            .substringBefore("private fun clearStaleRefreshing")

        assertEquals(true, method.contains("if (cleaned != stored)"))
        assertEquals(true, method.contains("saveSnapshots(cleaned)"))
    }

    @Test
    fun saveSnapshotsSkipsNoOpWritesToAvoidPreferenceListenerLoops() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()
        val method = source.substringAfter("fun saveSnapshots(")
            .substringBefore("fun markConnecting")

        assertEquals(true, method.contains("val encoded = ProviderSnapshotCodec.encode(ordered)"))
        assertEquals(true, method.contains("if (preferences.getString(KEY_SNAPSHOTS, \"\") == encoded) return"))
        assertEquals(true, method.contains(".putString(KEY_SNAPSHOTS, encoded)"))
    }

    @Test
    fun transientLoginAndGoogleRefreshStatesAreNotExpiredTooEarly() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()
        val staleRefreshPolicy = source.substringAfter("val staleTimeout = when")
            .substringBefore("if (Duration.between")

        assertEquals(true, source.contains("Duration.ofMinutes(15)"))
        assertEquals(true, source.contains("GOOGLE_STALE_REFRESH_TIMEOUT: Duration = Duration.ofSeconds(90)"))
        assertEquals(true, source.contains("OPENCODE_STALE_REFRESH_TIMEOUT: Duration = Duration.ofSeconds(30)"))
        assertEquals(true, staleRefreshPolicy.contains("ProviderId.OPENCODE"))
        assertEquals(true, staleRefreshPolicy.contains("ProviderId.GEMINI"))
        assertEquals(true, staleRefreshPolicy.contains("ProviderId.ANTIGRAVITY"))
    }

    @Test
    fun googleRecoverableFailuresAreReadBackAsRetryPending() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()
        val readSnapshots = source.substringAfter("fun readSnapshots()")
            .substringBefore("private fun clearStaleRefreshing")
        val failKeepingPrevious = source.substringAfter("fun failKeepingPrevious")
            .substringBefore("fun markInteractiveAuthRequired")

        assertEquals(true, readSnapshots.contains(".map(::recoverGoogleRecoverableUsageFailure)"))
        assertEquals(true, readSnapshots.contains(".map(::normalizeGoogleUsagePendingMessage)"))
        assertEquals(true, source.contains("private fun recoverGoogleRecoverableUsageFailure"))
        assertEquals(true, source.contains("private fun normalizeGoogleUsagePendingMessage"))
        assertEquals(true, source.contains("LEGACY_GOOGLE_USAGE_PENDING_MESSAGE"))
        assertEquals(true, source.contains("Provider session reached, but trusted usage payload was not available yet."))
        assertEquals(true, source.contains("snapshot.message == GOOGLE_USAGE_PENDING_MESSAGE"))
        assertEquals(true, source.contains("return snapshot"))
        assertEquals(true, source.contains("private fun ProviderId.isGoogleProvider"))
        assertEquals(true, source.contains("private fun String.isRecoverableGoogleUsageFailureMessage"))
        assertEquals(true, failKeepingPrevious.contains("if (providerId.isGoogleProvider() && message.isRecoverableGoogleUsageFailureMessage())"))
        assertEquals(true, failKeepingPrevious.contains("markGoogleUsagePending"))
    }

    @Test
    fun geminiExplicitLoginRequiredDoesNotStayConnectedBecauseOfPreviousUsage() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()
        val failKeepingPrevious = source.substringAfter("fun failKeepingPrevious")
            .substringBefore("fun markInteractiveAuthRequired")
        val authMessageClassifier = source.substringAfter("private fun String.isGeminiInteractiveAuthRequiredMessage")
            .substringBefore("private const val GOOGLE_USAGE_PENDING_MESSAGE")

        assertEquals(true, failKeepingPrevious.contains("providerId == ProviderId.GEMINI"))
        assertEquals(true, failKeepingPrevious.contains("message.isGeminiInteractiveAuthRequiredMessage()"))
        assertEquals(true, failKeepingPrevious.contains("ProviderUsageSnapshot.interactiveAuthRequiredKeepingPrevious"))
        assertEquals(true, failKeepingPrevious.indexOf("message.isGeminiInteractiveAuthRequiredMessage()") < failKeepingPrevious.indexOf("message.isRecoverableGoogleUsageFailureMessage()"))
        assertEquals(true, authMessageClassifier.contains("gemini login is required."))
        assertEquals(true, authMessageClassifier.contains("background refresh reached a provider login page."))
    }

    @Test
    fun googleCollectingWithoutTrustedUsageIsRecoveredToPendingIdleState() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.GEMINI,
            connectionState = ProviderConnectionState.COLLECTING,
            refreshState = ProviderRefreshState.REFRESHING,
            message = "Collecting usage"
        )

        val recovered = recoverGoogleCollectingWithoutTrustedUsage(snapshot)

        assertEquals(ProviderConnectionState.UNAVAILABLE, recovered.connectionState)
        assertEquals(ProviderRefreshState.IDLE, recovered.refreshState)
        assertEquals("Provider session reached, but trusted usage payload was not available yet.", recovered.message)
    }

    @Test
    fun glmNoSubscriptionFailureIsRecoveredToConnectedPlanlessState() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.GLM,
            connectionState = ProviderConnectionState.ERROR,
            refreshState = ProviderRefreshState.IDLE,
            planLabel = "Plan 없음",
            message = "Background refresh timed out.",
            lines = emptyList()
        )

        val recovered = recoverGlmNoSubscriptionPlan(snapshot)

        assertEquals(ProviderConnectionState.CONNECTED, recovered.connectionState)
        assertEquals(ProviderRefreshState.IDLE, recovered.refreshState)
        assertEquals("Plan 없음", recovered.planLabel)
        assertEquals("You don't have any subscription", recovered.message)
        assertEquals(emptyList<ProviderUsageLine>(), recovered.lines)
    }

    @Test
    fun connectingLoginStatePreservesPreviousTrustedUsageAndCanBeCancelled() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()
        val markConnecting = source.substringAfter("fun markConnecting")
            .substringBefore("fun markLoginCancelled")
        val markLoginCancelled = source.substringAfter("fun markLoginCancelled")
            .substringBefore("fun markCollecting")
        val webLoginActivity = java.io.File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val appShell = java.io.File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val connectProvider = appShell.substringAfter("fun connectProvider")
            .substringBefore("fun disconnectProvider")

        assertEquals(true, markConnecting.contains("val current = readSnapshots().firstOrNull"))
        assertEquals(true, markConnecting.contains("current?.copy("))
        assertEquals(true, markConnecting.contains("connectionState = ProviderConnectionState.CONNECTING"))
        assertEquals(true, markConnecting.contains("refreshState = ProviderRefreshState.REFRESHING"))
        assertEquals(true, markConnecting.contains("updatedAt = snapshotUpdatedAtForStatusTransition(current, now)"))
        assertEquals(true, markConnecting.contains("statusUpdatedAt = now"))
        assertEquals(true, markLoginCancelled.contains("providerConnectionStateAfterPreviousUsageFailure"))
        assertEquals(true, markLoginCancelled.contains("hasPreviousUsage = current.lines.isNotEmpty()"))
        assertEquals(true, markLoginCancelled.contains("withoutPreviousUsage = ProviderConnectionState.DISCONNECTED"))
        assertEquals(true, markLoginCancelled.contains("updatedAt = snapshotUpdatedAtForStatusTransition(current, now)"))
        assertEquals(true, markLoginCancelled.contains("statusUpdatedAt = now"))
        assertEquals(true, connectProvider.contains("updatedAt = snapshotUpdatedAtForStatusTransition(currentSnapshot, now)"))
        assertEquals(true, connectProvider.contains("statusUpdatedAt = now"))
        assertEquals(true, webLoginActivity.contains("markLoginCancelled("))
        assertEquals(true, webLoginActivity.contains("Provider login was cancelled."))
    }

    @Test
    fun previousUsageFailureKeepsAllProvidersConnectedWhenUsageExists() {
        ProviderId.entries.forEach { providerId ->
            assertEquals(
                providerId.name,
                ProviderConnectionState.CONNECTED,
                providerConnectionStateAfterPreviousUsageFailure(
                    providerId = providerId,
                    hasPreviousUsage = true,
                    withoutPreviousUsage = ProviderConnectionState.DISCONNECTED
                )
            )
        }
    }

    @Test
    fun previousUsageFailureWithoutPriorLinesFallsBackToRequestedState() {
        assertEquals(
            ProviderConnectionState.DISCONNECTED,
            providerConnectionStateAfterPreviousUsageFailure(
                providerId = ProviderId.CODEX,
                hasPreviousUsage = false,
                withoutPreviousUsage = ProviderConnectionState.DISCONNECTED
            )
        )
    }

    @Test
    fun partialConnectedRefreshKeepsPreviousLinesUntilTheyRefresh() {
        val previous = ProviderUsageSnapshot(
            providerId = ProviderId.CODEX,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(
                ProviderUsageLine(
                    key = "codex:primary_window",
                    label = "Codex Session",
                    remainingPercent = 0.79f
                ),
                ProviderUsageLine(
                    key = "codex:secondary_window",
                    label = "Codex Weekly",
                    remainingPercent = 0.41f
                )
            )
        )
        val partialRefresh = previous.copy(
            lines = listOf(
                ProviderUsageLine(
                    key = "codex:secondary_window",
                    label = "Codex Weekly",
                    remainingPercent = 0.40f
                )
            )
        )

        val merged = mergeFreshSnapshotWithPreviousLines(partialRefresh, previous)

        assertEquals(listOf("Codex Session", "Codex Weekly"), merged.lines.map { it.label })
        assertEquals(0.79f, merged.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals(0.40f, merged.lines[1].remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun saveSnapshotDoesNotApplyCodexWeeklyResetRolloverGuard() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()
        val saveSnapshot = source.substringAfter("fun saveSnapshot(")
            .substringBefore("fun saveSnapshots(")

        assertFalse(saveSnapshot.contains("CodexWeeklyResetRolloverPolicy"))
        assertFalse(source.contains("codex_weekly_reset_rollover_active_reset_at"))
        assertFalse(source.contains("CodexWeeklyResetRolloverState"))
    }

    @Test
    fun stoppedBackgroundRefreshWithPreviousTrustedUsageIsRecoveredToConnected() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CLAUDE,
            connectionState = ProviderConnectionState.STALE,
            refreshState = ProviderRefreshState.IDLE,
            message = "Background refresh stopped.",
            lines = listOf(ProviderUsageLine(label = "Claude Weekly", remainingPercent = 0.88f))
        )

        val recovered = recoverStoppedBackgroundRefreshWithPreviousUsage(snapshot)

        assertEquals(ProviderConnectionState.CONNECTED, recovered.connectionState)
        assertEquals(ProviderRefreshState.IDLE, recovered.refreshState)
        assertEquals(null, recovered.message)
        assertEquals(snapshot.lines, recovered.lines)
    }

    @Test
    fun legacyGeminiCollapsedUsageLinesAreRemoved() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.GEMINI,
            connectionState = ProviderConnectionState.CONNECTED,
            planLabel = "Gemini Plus",
            lines = listOf(
                ProviderUsageLine(
                    key = "gemini:gemini_pro",
                    label = "Gemini Pro",
                    remainingPercent = 1.0f
                ),
                ProviderUsageLine(
                    key = "gemini:gemini_flash",
                    label = "Gemini Flash",
                    remainingPercent = 1.0f
                ),
                ProviderUsageLine(
                    key = "gemini:25_flash",
                    label = "2.5 flash",
                    remainingPercent = 1.0f
                )
            )
        )

        val recovered = normalizeGeminiLegacyPlanLabel(
            normalizeGeminiLegacyUsageLabels(snapshot)
        )

        assertEquals("gemini:25_flash", recovered.lines.single().key)
        assertEquals("2.5 flash", recovered.lines.single().label)
        assertEquals("Plus", recovered.planLabel)
    }

    @Test
    fun geminiUsageWindowLinesAreKeptWhenNoModelSpecificReplacementExists() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.GEMINI,
            connectionState = ProviderConnectionState.CONNECTED,
            planLabel = "Gemini Pro",
            lines = listOf(
                ProviderUsageLine(
                    key = "gemini:5_hour",
                    label = "5-hour limit",
                    remainingPercent = 1.0f
                ),
                ProviderUsageLine(
                    key = "gemini:weekly",
                    label = "Weekly limit",
                    remainingPercent = 1.0f,
                    resetText = "Resets in 5d 1h"
                )
            )
        )

        val recovered = normalizeGeminiLegacyUsageLabels(snapshot)

        assertEquals(listOf("5-hour limit", "Weekly limit"), recovered.lines.map { it.label })
        assertEquals(null, recovered.lines[0].resetText)
        assertEquals("Resets in 5d 1h", recovered.lines[1].resetText)
    }

    @Test
    fun legacyAuthRequiredWithPreviousUsageReturnsToConnected() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CURSOR,
            connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
            updatedAt = "2026-05-24T11:36:55Z",
            message = "Connection needs attention",
            lines = listOf(ProviderUsageLine(label = "Total usage", remainingPercent = 0.94f))
        )

        val recovered = recoverLegacyInteractiveAuthRequired(snapshot)

        assertEquals(ProviderConnectionState.CONNECTED, recovered.connectionState)
        assertEquals(snapshot.lines, recovered.lines)
    }

    @Test
    fun recentExplicitAuthRequiredWithPreviousUsageStaysAuthRequired() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CURSOR,
            connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
            updatedAt = "2026-05-26T05:31:00Z",
            message = "Background refresh reached a provider login page.",
            lines = listOf(ProviderUsageLine(label = "Total usage", remainingPercent = 0.94f))
        )

        val recovered = recoverLegacyInteractiveAuthRequired(snapshot)

        assertEquals(ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED, recovered.connectionState)
    }

    @Test
    fun sessionExpiredAuthRequiredIsRecoveredToDisconnected() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()
        val readSnapshots = source.substringAfter("fun readSnapshots()")
            .substringBefore("private fun clearStaleRefreshing")
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CURSOR,
            connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
            updatedAt = "2026-05-26T05:31:00Z",
            message = "Background refresh reached a provider login page.",
            lines = listOf(ProviderUsageLine(label = "Total usage", remainingPercent = 0.94f))
        )

        val recovered = recoverSessionExpiredInteractiveAuthRequired(snapshot)

        assertEquals(true, readSnapshots.contains(".map(::recoverSessionExpiredInteractiveAuthRequired)"))
        assertEquals(ProviderConnectionState.DISCONNECTED, recovered.connectionState)
        assertEquals(emptyList<ProviderUsageLine>(), recovered.lines)
        assertEquals("Background refresh reached a provider login page.", recovered.message)
    }

    @Test
    fun authRequiredWithoutPreviousUsageStaysAuthRequired() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CURSOR,
            connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
            updatedAt = "2026-05-24T11:36:55Z",
            message = "Connection needs attention",
            lines = emptyList()
        )

        val recovered = recoverLegacyInteractiveAuthRequired(snapshot)

        assertEquals(ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED, recovered.connectionState)
    }

    @Test
    fun expiredClaudeSessionLineIsKeptUntilFreshProviderPayloadArrives() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CLAUDE,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(
                ProviderUsageLine(
                    key = "claude:session",
                    label = "Claude Session",
                    remainingPercent = 0.91f,
                    resetsAt = "2026-05-20T17:10:01Z"
                ),
                ProviderUsageLine(
                    key = "claude:weekly",
                    label = "Claude Weekly",
                    remainingPercent = 0.86f,
                    resetsAt = "2026-05-25T20:00:00Z"
                )
            )
        )

        val cleaned = snapshot.withExpiredProviderSpecificLinesRemoved(
            now = Instant.parse("2026-05-20T21:24:16Z")
        )

        assertEquals(listOf("Claude Session", "Claude Weekly"), cleaned.lines.map { it.label })
        assertFalse(cleaned.message.orEmpty().contains("fresh provider update", ignoreCase = true))
    }

    @Test
    fun expiredCodexSessionLineIsKeptUntilFreshProviderPayloadArrives() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CODEX,
            connectionState = ProviderConnectionState.STALE,
            lines = listOf(
                ProviderUsageLine(
                    key = "codex:primary_window",
                    label = "Codex Session",
                    remainingPercent = 0.79f,
                    resetsAt = "1779298708"
                ),
                ProviderUsageLine(
                    key = "codex:secondary_window",
                    label = "Codex Weekly",
                    remainingPercent = 0.79f,
                    resetsAt = "1779831098"
                )
            )
        )

        val cleaned = snapshot.withExpiredProviderSpecificLinesRemoved(
            now = Instant.parse("2026-05-21T00:06:49Z")
        )

        assertEquals(ProviderConnectionState.STALE, cleaned.connectionState)
        assertEquals(listOf("Codex Session", "Codex Weekly"), cleaned.lines.map { it.label })
        assertFalse(cleaned.message.orEmpty().contains("fresh provider update", ignoreCase = true))
    }
}
