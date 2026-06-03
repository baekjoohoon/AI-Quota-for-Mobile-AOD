package com.aiquota.mobile.widget

import android.content.Context
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.providers.UsageSurfaceRefresher

object WidgetAutoRefreshStarter {
    fun requestBackgroundRefresh(context: Context) {
        val appContext = context.applicationContext
        UsageSurfaceRefresher.refresh(appContext, LocalUsageRepository(appContext))
    }
}
