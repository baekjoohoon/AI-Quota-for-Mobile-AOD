package com.aiquota.mobile

import android.app.Application

class AIQuotaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseGatewayBootstrap.install()
    }
}
