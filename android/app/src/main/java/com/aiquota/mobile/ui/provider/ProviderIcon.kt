package com.aiquota.mobile.ui.provider

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.aiquota.mobile.R
import com.aiquota.mobile.local.ProviderId

@DrawableRes
fun providerIconRes(providerId: ProviderId): Int {
    return when (providerId) {
        ProviderId.CLAUDE -> R.drawable.ic_provider_claude
        ProviderId.CODEX -> R.drawable.ic_provider_codex
        ProviderId.GLM -> R.drawable.ic_provider_glm
        ProviderId.OPENCODE -> R.drawable.ic_provider_opencode
        ProviderId.GEMINI -> R.drawable.ic_provider_gemini
        ProviderId.COPILOT -> R.drawable.ic_provider_copilot
        ProviderId.ANTIGRAVITY -> R.drawable.ic_provider_antigravity
        ProviderId.CURSOR -> R.drawable.ic_provider_cursor
    }
}

@DrawableRes
fun providerIconRes(providerId: String): Int {
    return when (providerId.trim().lowercase()) {
        "claude" -> R.drawable.ic_provider_claude
        "codex" -> R.drawable.ic_provider_codex
        "glm", "z-ai", "zai", "z.ai" -> R.drawable.ic_provider_glm
        "opencode", "open-code", "open code" -> R.drawable.ic_provider_opencode
        "openai" -> R.drawable.ic_provider_openai
        "gemini" -> R.drawable.ic_provider_gemini
        "copilot", "github-copilot", "github_copilot" -> R.drawable.ic_provider_copilot
        "antigravity" -> R.drawable.ic_provider_antigravity
        "cursor" -> R.drawable.ic_provider_cursor
        else -> R.drawable.ic_provider_unknown
    }
}

@Composable
fun ProviderIconImage(
    providerId: ProviderId,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(providerIconRes(providerId)),
            contentDescription = providerId.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(providerIconVisualScale(providerId))
        )
    }
}

internal fun providerIconVisualScale(providerId: ProviderId): Float {
    return when (providerId) {
        ProviderId.CLAUDE -> 0.85f
        ProviderId.CODEX -> 0.85f
        ProviderId.GLM -> 0.86f
        ProviderId.OPENCODE -> 0.76f
        ProviderId.GEMINI -> 0.92f
        ProviderId.COPILOT -> 0.64f
        ProviderId.ANTIGRAVITY -> 0.78f
        ProviderId.CURSOR -> 0.73f
    }
}
