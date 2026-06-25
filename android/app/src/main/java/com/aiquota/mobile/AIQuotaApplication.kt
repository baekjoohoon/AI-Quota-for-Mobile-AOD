package com.aiquota.mobile

import android.app.Application
import com.aiquota.mobile.providers.GlmIsolatedWebViewProfile
import com.aiquota.mobile.update.AppUpdateCheckScheduler

class AIQuotaApplication : Application() {
    override fun onCreate() {
        val glmIsolatedProcess = GlmIsolatedWebViewProfile.configureIfNeeded(this)
        super.onCreate()
        if (glmIsolatedProcess) return
        FirebaseGatewayBootstrap.install()
        AppUpdateCheckScheduler.schedule(this)
    }
}
