package com.aiusage.mobile.providers

import android.content.Context
import android.webkit.WebSettings

object ProviderWebViewUserAgent {
    fun mobileChrome(context: Context): String {
        return WebSettings.getDefaultUserAgent(context)
            .replace("; wv", "")
            .replace("Version/4.0 ", "")
    }
}
