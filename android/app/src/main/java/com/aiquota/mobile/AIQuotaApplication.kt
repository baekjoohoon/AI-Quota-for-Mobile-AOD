package com.aiquota.mobile

import android.app.Application
import com.aiquota.mobile.update.AppUpdateCheckScheduler

class AIQuotaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseGatewayBootstrap.install()
        AppUpdateCheckScheduler.schedule(this)
    }
}
