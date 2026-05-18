package com.aiusage.mobile.providers

import android.content.Context
import com.aiusage.mobile.local.ProviderId

object ProviderCollectorAssets {
    fun scriptFor(context: Context, providerId: ProviderId): String {
        return when (providerId) {
            ProviderId.GEMINI -> readAsset(context, GEMINI_COLLECTOR_ASSET)
            else -> ""
        }
    }

    private fun readAsset(context: Context, fileName: String): String {
        return runCatching {
            context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        }.getOrDefault("")
    }

    private const val GEMINI_COLLECTOR_ASSET = "gemini_collector.js"
}
