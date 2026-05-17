package com.aiusage.mobile.ui.provider

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.aiusage.mobile.R
import com.aiusage.mobile.local.ProviderId

@DrawableRes
fun providerIconRes(providerId: ProviderId): Int {
    return when (providerId) {
        ProviderId.CLAUDE -> R.drawable.ic_provider_claude
        ProviderId.CODEX -> R.drawable.ic_provider_openai
        ProviderId.GEMINI -> R.drawable.ic_provider_gemini
        ProviderId.COPILOT -> R.drawable.ic_provider_copilot
        ProviderId.CURSOR -> R.drawable.ic_provider_cursor
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
        ProviderId.GEMINI -> 0.92f
        ProviderId.COPILOT -> 0.64f
        ProviderId.CURSOR -> 0.73f
    }
}
