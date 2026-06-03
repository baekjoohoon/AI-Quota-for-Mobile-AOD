package com.aiquota.mobile

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object FirebaseGatewayBootstrap {
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
        installed = true
    }
}
