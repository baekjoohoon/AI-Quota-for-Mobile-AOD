package com.aiquota.mobile.support

import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderUsageSnapshot
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val BUG_REPORT_RECIPIENT = "minicode@minicode.co.kr"

enum class BugReportCategory(val emailLabel: String) {
    LOGIN("Login"),
    USAGE_COLLECTION("Usage collection"),
    WIDGET("Widget"),
    LIVE_REFRESH("Live refresh"),
    OTHER("Other")
}

data class BugReportRequest(
    val category: BugReportCategory,
    val providerId: ProviderId?,
    val description: String,
    val includeDiagnostics: Boolean
)

data class BugReportDiagnostics(
    val appVersionName: String,
    val versionCode: Int,
    val buildType: String,
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val localeTag: String,
    val liveMonitoringEnabled: Boolean,
    val canPostNotifications: Boolean,
    val notificationEnabled: Boolean,
    val liveRefreshState: String,
    val snapshots: List<ProviderUsageSnapshot>
)

data class BugReportEmail(
    val recipient: String,
    val subject: String,
    val body: String
)

object BugReportEmailComposer {
    fun compose(
        request: BugReportRequest,
        diagnostics: BugReportDiagnostics
    ): BugReportEmail {
        val providerLabel = request.providerId?.displayName ?: "All providers"
        val subject = "[AI Quota] Bug report - ${request.category.emailLabel} - $providerLabel"
        val body = buildString {
            appendLine("Issue type: ${request.category.emailLabel}")
            appendLine("Provider: $providerLabel")
            appendLine()
            appendLine("Description")
            appendLine(request.description.ifBlank { "(No description provided)" })
            if (request.includeDiagnostics) {
                appendLine()
                appendLine("Diagnostics")
                appendLine("App version: ${diagnostics.appVersionName} (${diagnostics.versionCode})")
                appendLine("Build type: ${diagnostics.buildType}")
                appendLine("Android: ${diagnostics.androidRelease} / SDK ${diagnostics.sdkInt}")
                appendLine("Device: ${diagnostics.manufacturer} ${diagnostics.model}")
                appendLine("Locale: ${diagnostics.localeTag}")
                appendLine(
                    "Live refresh: enabled=${diagnostics.liveMonitoringEnabled}, " +
                        "state=${diagnostics.liveRefreshState}, " +
                        "notificationsEnabled=${diagnostics.notificationEnabled}, " +
                        "notificationsAllowed=${diagnostics.canPostNotifications}"
                )
                appendLine("Providers")
                diagnostics.snapshots.forEach { snapshot ->
                    appendLine(snapshot.safeDiagnosticLine())
                }
            }
            appendLine()
            appendLine("Sensitive values such as account names, tokens, cookies, and authorization headers are not included automatically.")
        }
        return BugReportEmail(
            recipient = BUG_REPORT_RECIPIENT,
            subject = subject,
            body = body
        )
    }

    fun mailtoUri(email: BugReportEmail): String {
        return "mailto:${email.recipient}" +
            "?subject=${email.subject.urlEncode()}" +
            "&body=${email.body.urlEncode()}"
    }

    private fun ProviderUsageSnapshot.safeDiagnosticLine(): String {
        val plan = planLabel?.takeIf { it.isNotBlank() } ?: "unknown"
        val message = message
            ?.takeIf { it.isNotBlank() }
            ?.let(::sanitizeDiagnosticText)
            ?.take(180)
            ?.let { " / message=$it" }
            .orEmpty()
        return "${providerId.displayName}: $connectionState / $refreshState / plan=$plan / lines=${lines.size}$message"
    }

    private fun sanitizeDiagnosticText(value: String): String {
        return value
            .replace(EMAIL_REGEX, "[redacted-email]")
            .replace(SECRET_HINT_REGEX) { match ->
                "${match.groupValues[1]}=[redacted]"
            }
    }

    private val EMAIL_REGEX = Regex(
        "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val SECRET_HINT_REGEX = Regex(
        "(?i)\\b(token|cookie|authorization|bearer|refresh_token|access_token|id_token|sid)\\b\\s*[:=]?\\s*[^\\s,;]+"
    )

    private fun String.urlEncode(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
    }
}
