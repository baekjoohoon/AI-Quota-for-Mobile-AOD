package com.aiquota.mobile

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object FirebaseGatewayBootstrap {
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        installed = true
    }
}
