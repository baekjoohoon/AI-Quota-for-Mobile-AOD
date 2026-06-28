package com.aiquota.mobile.support

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageLine
import com.aiquota.mobile.local.ProviderUsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportEmailComposerTest {
    @Test
    fun bugReportEmailIncludesSafeDiagnosticsOnly() {
        val email = BugReportEmailComposer.compose(
            request = BugReportRequest(
                category = BugReportCategory.USAGE_COLLECTION,
                providerId = ProviderId.GEMINI,
                description = "Gemini Free plan Pro bucket appears as 0%.",
                includeDiagnostics = true
            ),
            diagnostics = BugReportDiagnostics(
                appVersionName = "1.0.0",
                versionCode = 11,
                buildType = "debug",
                androidRelease = "15",
                sdkInt = 35,
                manufacturer = "Google",
                model = "Pixel 5",
                localeTag = "ko-KR",
                liveMonitoringEnabled = true,
                canPostNotifications = false,
                notificationEnabled = true,
                liveRefreshState = "STOPPED",
                snapshots = listOf(
                    ProviderUsageSnapshot(
                        providerId = ProviderId.GEMINI,
                        connectionState = ProviderConnectionState.ERROR,
                        refreshState = ProviderRefreshState.IDLE,
                        planLabel = "Free",
                        account = "private-user@example.com",
                        message = "token abc.def.ghi cookie sid=123 for private-user@example.com",
                        lines = listOf(
                            ProviderUsageLine(
                                label = "2.5 flash",
                                remainingPercent = 1f,
                                sourceLabel = "provider-api"
                            )
                        )
                    )
                )
            )
        )

        assertEquals(BUG_REPORT_RECIPIENT, email.recipient)
        assertTrue(email.subject.contains("Gemini"))
        assertTrue(email.body.contains("Gemini Free plan Pro bucket appears as 0%."))
        assertTrue(email.body.contains("Gemini: ERROR / IDLE / plan=Free / lines=1"))
        assertTrue(email.body.contains("notificationsAllowed=false"))
        assertFalse(email.body.contains("private-user@example.com"))
        assertFalse(email.body.contains("abc.def.ghi"))
        assertFalse(email.body.contains("sid=123"))
    }

    @Test
    fun bugReportDiagnosticsRedactTokenCookieAuthorizationBearerOauthCodeAndEmail() {
        val email = BugReportEmailComposer.compose(
            request = BugReportRequest(
                category = BugReportCategory.LOGIN,
                providerId = ProviderId.COPILOT,
                description = "Login callback exposed sensitive diagnostics.",
                includeDiagnostics = true
            ),
            diagnostics = BugReportDiagnostics(
                appVersionName = "1.0.0",
                versionCode = 15,
                buildType = "release",
                androidRelease = "15",
                sdkInt = 35,
                manufacturer = "Google",
                model = "Pixel 6a",
                localeTag = "ko-KR",
                liveMonitoringEnabled = false,
                canPostNotifications = true,
                notificationEnabled = false,
                liveRefreshState = "STOPPED",
                snapshots = listOf(
                    ProviderUsageSnapshot(
                        providerId = ProviderId.COPILOT,
                        connectionState = ProviderConnectionState.ERROR,
                        refreshState = ProviderRefreshState.IDLE,
                        planLabel = "Pro",
                        account = "private-user@example.com",
                        message = "authorization: Bearer ghp_secret access_token=abc refresh_token=def id_token=ghi code=oauth-secret cookie sid=123",
                        lines = emptyList()
                    )
                )
            )
        )

        assertTrue(email.body.contains("Copilot: ERROR / IDLE / plan=Pro / lines=0"))
        assertFalse(email.body.contains("private-user@example.com"))
        assertFalse(email.body.contains("ghp_secret"))
        assertFalse(email.body.contains("abc"))
        assertFalse(email.body.contains("def"))
        assertFalse(email.body.contains("ghi"))
        assertFalse(email.body.contains("oauth-secret"))
        assertFalse(email.body.contains("sid=123"))
    }

    @Test
    fun bugReportEmailCanOmitDiagnostics() {
        val email = BugReportEmailComposer.compose(
            request = BugReportRequest(
                category = BugReportCategory.WIDGET,
                providerId = null,
                description = "Widget layout is too small.",
                includeDiagnostics = false
            ),
            diagnostics = BugReportDiagnostics(
                appVersionName = "1.0.0",
                versionCode = 11,
                buildType = "release",
                androidRelease = "15",
                sdkInt = 35,
                manufacturer = "Samsung",
                model = "SM-S911N",
                localeTag = "ko-KR",
                liveMonitoringEnabled = false,
                canPostNotifications = true,
                notificationEnabled = false,
                liveRefreshState = "STOPPED",
                snapshots = emptyList()
            )
        )

        assertTrue(email.body.contains("Widget layout is too small."))
        assertFalse(email.body.contains("App version"))
        assertFalse(email.body.contains("SM-S911N"))
    }

    @Test
    fun bugReportMailtoUriCarriesSubjectAndBody() {
        val email = BugReportEmail(
            recipient = BUG_REPORT_RECIPIENT,
            subject = "[AI Quota] Bug report - Login - Gemini",
            body = "Issue type: Login\nProvider: Gemini\nDescription\nGoogle sign-in failed."
        )

        val uri = BugReportEmailComposer.mailtoUri(email)

        assertTrue(uri.startsWith("mailto:minicode@minicode.co.kr?"))
        assertTrue(uri.contains("subject=%5BAI%20Quota%5D%20Bug%20report%20-%20Login%20-%20Gemini"))
        assertTrue(uri.contains("body=Issue%20type%3A%20Login%0AProvider%3A%20Gemini"))
    }
}
