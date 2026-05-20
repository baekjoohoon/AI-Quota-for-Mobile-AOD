package com.aiusage.mobile.providers

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.aiusage.mobile.local.LocalUsageRepository
import com.aiusage.mobile.local.ProviderPreferencesRepository
import com.aiusage.mobile.notification.UsageLimitNotificationController
import com.aiusage.mobile.widget.AIUsageCircularWidgetProvider
import com.aiusage.mobile.widget.AIUsageUnifiedGlanceWidget
import com.aiusage.mobile.widget.ProviderUsageGlanceWidget
import com.aiusage.mobile.widget.WidgetSnapshotCache
import java.time.Instant
import kotlinx.coroutines.runBlocking

object UsageSurfaceRefresher {
    fun refresh(context: Context, repository: LocalUsageRepository) {
        val appContext = context.applicationContext
        val preferences = ProviderPreferencesRepository(appContext)
        val updatedAt = Instant.now().toString()
        val displayJson = repository.exportDisplayOnlyCache(
            order = preferences.providerOrder(),
            hidden = preferences.hiddenProviders(),
            updatedAt = updatedAt
        )
        val cache = WidgetSnapshotCache(appContext)
        cache.write(repository.exportDisplayJson(), updatedAt = updatedAt)
        cache.writeLocalDisplaySnapshot(displayJson, updatedAt)
        UsageLimitNotificationController.update(appContext, displayJson)
        AIUsageCircularWidgetProvider.updateAll(appContext)
        runCatching {
            runBlocking {
                AIUsageUnifiedGlanceWidget().updateAll(appContext)
                ProviderUsageGlanceWidget().updateAll(appContext)
            }
        }
    }
}
