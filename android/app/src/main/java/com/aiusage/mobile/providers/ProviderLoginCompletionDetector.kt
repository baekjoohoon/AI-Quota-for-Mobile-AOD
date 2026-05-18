package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.json.JSONObject
import java.net.URI
import java.util.Locale

object ProviderLoginCompletionDetector {
    fun isLoginComplete(providerId: ProviderId, url: String?, visibleText: String): Boolean {
        val candidateUrl = url?.trim().orEmpty()
        if (!ProviderHostAllowlist.isAllowed(providerId, candidateUrl)) return false

        structuredCompletion(providerId, candidateUrl, visibleText)?.let { isComplete ->
            return isComplete
        }

        val normalizedText = normalize(visibleText)
        if (normalizedText.length < MIN_VISIBLE_TEXT_LENGTH) return false
        if (isLoginForm(candidateUrl, normalizedText)) return false

        return when (providerId) {
            ProviderId.CLAUDE -> isClaudeComplete(candidateUrl, normalizedText)
            ProviderId.CODEX -> isCodexComplete(candidateUrl, normalizedText)
            ProviderId.GEMINI -> isGeminiComplete(candidateUrl, normalizedText)
            ProviderId.COPILOT -> isCopilotComplete(candidateUrl, normalizedText)
            ProviderId.CURSOR -> isCursorComplete(candidateUrl, normalizedText)
        }
    }

    private fun structuredCompletion(
        providerId: ProviderId,
        url: String,
        value: String
    ): Boolean? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("{") || !trimmed.contains("\"c\"")) return null
        val response = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val provider = response.optNullableString("provider")
        if (provider != null && !provider.equals(providerId.storageId, ignoreCase = true)) {
            return false
        }
        val completion = response.optJSONObject("c") ?: return null
        if (completion.optBoolean("login", false)) return false
        val data = response.optJSONObject("d") ?: response.optJSONObject("data")
        val limits = data?.optJSONArray("x") ?: data?.optJSONArray("limits")
        val hasUsageData = data?.optNullableString("p") != null ||
            data?.optNullableString("plan") != null ||
            data?.optNullableString("planLabel") != null ||
            (limits != null && limits.length() > 0)
        val authenticatedApp = completion.optBoolean("authenticatedApp", false)
        if (completion.optInt("textLength", 0) < MIN_VISIBLE_TEXT_LENGTH && !authenticatedApp && !hasUsageData) {
            return false
        }
        if (!completion.optBoolean("providerPage", false) && !authenticatedApp && !hasUsageData) return false
        if (!authenticatedApp && !hasUsageData) return false

        val normalizedValue = normalize(value)
        return when (providerId) {
            ProviderId.CLAUDE -> hostEndsWith(url, "claude.ai") &&
                !isClaudeLogoutUrl(url) &&
                (!isClaudeLoginUrl(url) || authenticatedApp) &&
                (hasUsageData || hasClaudeAuthenticatedMarker(normalizedValue) || authenticatedApp)
            ProviderId.CODEX -> (hostEndsWith(url, "chatgpt.com") ||
                hostEndsWith(url, "chat.openai.com"))
            ProviderId.GEMINI -> hostEndsWith(url, "gemini.google.com")
            ProviderId.COPILOT -> hostEndsWith(url, "github.com") || hostEndsWith(url, "githubcopilot.com")
            ProviderId.CURSOR -> hostEndsWith(url, "cursor.com") || hostEndsWith(url, "cursor.sh")
        }
    }

    private fun isClaudeComplete(url: String, text: String): Boolean {
        return hostEndsWith(url, "claude.ai") && hasClaudeAuthenticatedMarker(text)
    }

    private fun isClaudeLoginUrl(url: String): Boolean {
        val path = runCatching { URI(url).path?.lowercase(Locale.US).orEmpty() }.getOrDefault("")
        return path == "/login" ||
            path.startsWith("/login/")
    }

    private fun isClaudeLogoutUrl(url: String): Boolean {
        val path = runCatching { URI(url).path?.lowercase(Locale.US).orEmpty() }.getOrDefault("")
        return path == "/logout" ||
            path.startsWith("/logout/")
    }

    private fun hasClaudeAuthenticatedMarker(text: String): Boolean {
        if (containsAny(text, claudeLoggedOutPrompts)) return false
        return containsAny(text, claudeAuthenticatedPrompts)
    }

    private fun isCodexComplete(url: String, text: String): Boolean {
        return (
            hostEndsWith(url, "chatgpt.com") ||
                hostEndsWith(url, "chat.openai.com")
            ) &&
            containsAny(text, codexAuthenticatedPrompts)
    }

    private fun isGeminiComplete(url: String, text: String): Boolean {
        return hostEndsWith(url, "gemini.google.com") &&
            containsAny(text, geminiAuthenticatedPrompts)
    }

    private fun isCopilotComplete(url: String, text: String): Boolean {
        return (hostEndsWith(url, "github.com") || hostEndsWith(url, "githubcopilot.com")) &&
            containsAny(text, copilotAuthenticatedPrompts)
    }

    private fun isCursorComplete(url: String, text: String): Boolean {
        if (containsAny(text, cursorLoggedOutPrompts)) return false
        return (hostEndsWith(url, "cursor.com") || hostEndsWith(url, "cursor.sh")) &&
            containsAny(text, cursorAuthenticatedPrompts)
    }

    private fun normalize(value: String): String {
        return value
            .replace('\u00A0', ' ')
            .replace(WHITESPACE, " ")
            .trim()
            .lowercase(Locale.US)
    }

    private fun containsAny(text: String, needles: List<String>): Boolean {
        return needles.any { needle -> text.contains(needle.lowercase(Locale.US)) }
    }

    private fun isLoginForm(url: String, text: String): Boolean {
        val path = runCatching { URI(url).path?.lowercase(Locale.US).orEmpty() }.getOrDefault("")
        val loginPath = listOf("/login", "/signin", "/session", "/authorize").any { path.contains(it) }
        val hasCredentialField = containsAny(text, credentialFieldPrompts)
        val hasExplicitLoginAction = containsAny(text, explicitLoginPrompts)

        if (loginPath && (hasCredentialField || hasExplicitLoginAction)) return true
        if (hasCredentialField && hasExplicitLoginAction) return true
        if (containsAny(text, verificationPrompts) && hasExplicitLoginAction) return true
        return false
    }

    private fun hostEndsWith(url: String, expectedHost: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase(Locale.US) }.getOrNull() ?: return false
        return host == expectedHost || host.endsWith(".$expectedHost")
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).trim().takeIf { it.isNotBlank() }
    }

    private val explicitLoginPrompts = listOf(
        "log in to",
        "login to",
        "sign in to",
        "sign up for",
        "continue with google",
        "continue with apple",
        "\ub85c\uadf8\uc778 \ub610\ub294 \ud68c\uc6d0\uac00\uc785",
        "google \uacc4\uc815\uc73c\ub85c \uacc4\uc18d",
        "apple \uacc4\uc815\uc73c\ub85c \uacc4\uc18d"
    )

    private val claudeAuthenticatedPrompts = listOf(
        "new chat",
        "recent chats",
        "message claude",
        "projects",
        "artifacts",
        "claude\uc640 \ud568\uaed8 \ucee4\ud53c \ud55c\uc794",
        "\ub2ec\ube5b \uc544\ub798 \ub300\ud654\ud560\uae4c\uc694",
        "\uc624\ub298 \uc5b4\ub5a4 \ub3c4\uc6c0\uc744 \ub4dc\ub9b4\uae4c\uc694",
        "sonnet",
        "\uc0c8 \ucc44\ud305",
        "\ucd5c\uadfc \ucc44\ud305",
        "claude\uc5d0\uac8c \uba54\uc2dc\uc9c0"
    )

    private val claudeLoggedOutPrompts = listOf(
        "try claude",
        "sign in",
        "log in",
        "login",
        "pricing",
        "continue with google",
        "continue with apple",
        "\ub85c\uadf8\uc778 \ub610\ub294 \ud68c\uc6d0\uac00\uc785",
        "google \uacc4\uc815\uc73c\ub85c \uacc4\uc18d",
        "apple \uacc4\uc815\uc73c\ub85c \uacc4\uc18d"
    )

    private val codexAuthenticatedPrompts = listOf(
        "usage",
        "limits",
        "settings",
        "message chatgpt",
        "new chat",
        "library",
        "projects",
        "temporary chat",
        "\uc0c8 \ucc44\ud305",
        "\ub77c\uc774\ube0c\ub7ec\ub9ac",
        "\ud504\ub85c\uc81d\ud2b8",
        "\uc784\uc2dc \ucc44\ud305",
        "chatgpt\uc5d0 \uba54\uc2dc\uc9c0",
        "chatgpt\uc5d0\uac8c \uba54\uc2dc\uc9c0"
    )

    private val geminiAuthenticatedPrompts = listOf(
        "ask gemini",
        "chat with gemini",
        "recent chats",
        "gemini",
        "gemini\uc640\uc758 \ub300\ud654",
        "\ub2d8, \uc548\ub155\ud558\uc138\uc694",
        "\uacc4\ud68d, \ud559\uc2b5, \uc544\uc774\ub514\uc5b4",
        "\uc0c8 \ucc44\ud305",
        "\ucd5c\uadfc \ucc44\ud305",
        "gemini\uc5d0\uac8c \ubb3c\uc5b4"
    )

    private val copilotAuthenticatedPrompts = listOf(
        "copilot",
        "github copilot",
        "settings",
        "billing",
        "\uc124\uc815",
        "\uccad\uad6c"
    )

    private val cursorAuthenticatedPrompts = listOf(
        "dashboard",
        "usage",
        "settings",
        "account",
        "workspace",
        "team",
        "billing"
    )

    private val cursorLoggedOutPrompts = listOf(
        "welcome to cursor",
        "sign in",
        "sign up",
        "continue with google",
        "continue with github",
        "continue with apple",
        "don't have an account?",
        "\ub85c\uadf8\uc778",
        "\ud68c\uc6d0\uac00\uc785",
        "google\ub85c \uacc4\uc18d",
        "github\ub85c \uacc4\uc18d",
        "apple\ub85c \uacc4\uc18d"
    )

    private val credentialFieldPrompts = listOf(
        "username or email",
        "username or email address",
        "email address",
        "password",
        "\uc774\uba54\uc77c \uc8fc\uc18c",
        "\uc774\uba54\uc77c \ub610\ub294 \uc804\ud654 \ubc88\ud638",
        "\ube44\ubc00\ubc88\ud638"
    )

    private val verificationPrompts = listOf(
        "2-step verification",
        "two-factor authentication",
        "verification code",
        "\uc778\uc99d \ucf54\ub4dc"
    )

    private val WHITESPACE = Regex("\\s+")
    private const val MIN_VISIBLE_TEXT_LENGTH = 16
}
