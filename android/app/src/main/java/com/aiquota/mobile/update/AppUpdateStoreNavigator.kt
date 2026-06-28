package com.aiquota.mobile.update

import android.content.Context
import android.content.Intent
import android.net.Uri

object AppUpdateStoreNavigator {
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun open(context: Context) {
        runCatching {
            context.startActivity(storeIntent(context))
        }
    }

    fun storeIntent(context: Context): Intent {
        val packageName = context.packageName
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
            setPackage(PLAY_STORE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (marketIntent.resolveActivity(context.packageManager) != null) {
            return marketIntent
        }
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
