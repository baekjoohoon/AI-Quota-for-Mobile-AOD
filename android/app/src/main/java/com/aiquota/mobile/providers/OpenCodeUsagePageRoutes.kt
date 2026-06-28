package com.aiquota.mobile.providers

import java.net.URI
import java.util.Locale

object OpenCodeUsagePageRoutes {
    fun canonicalGoUsageUrlFrom(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase(Locale.US)
        if (host != "opencode.ai" && host != "www.opencode.ai") return null
        val segments = uri.path.orEmpty()
            .split("/")
            .filter { it.isNotBlank() }
        val workspaceIndex = segments.indexOf("workspace")
        if (workspaceIndex < 0 || workspaceIndex + 1 >= segments.size) return null
        val workspaceId = segments[workspaceIndex + 1]
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: "https"
        return "$scheme://${uri.host}/workspace/$workspaceId/go"
            .takeIf { segments.getOrNull(workspaceIndex + 2) == "go" }
            ?: goUsageUrlFrom(url)
    }

    fun goUsageUrlFrom(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase(Locale.US)
        if (host != "opencode.ai" && host != "www.opencode.ai") return null
        val segments = uri.path.orEmpty()
            .split("/")
            .filter { it.isNotBlank() }
        val workspaceIndex = segments.indexOf("workspace")
        if (workspaceIndex < 0 || workspaceIndex + 1 >= segments.size) return null
        if (segments.getOrNull(workspaceIndex + 2) == "go") return null
        val workspaceId = segments[workspaceIndex + 1]
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: "https"
        return "$scheme://${uri.host}/workspace/$workspaceId/go"
    }
}
