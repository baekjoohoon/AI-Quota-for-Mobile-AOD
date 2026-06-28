package com.aiquota.mobile.sync

import android.content.Context
import androidx.core.content.ContextCompat
import com.aiquota.mobile.providers.ProviderBackgroundRefreshService

class ForegroundRefreshController {
    private val serviceStarter: ServiceStarter
    private val preferences: ForegroundRefreshPreferences
    private val healthScheduler: HealthScheduler
    private var preciseRefreshRequested = false

    constructor(context: Context) {
        val appContext = context.applicationContext
        serviceStarter = AndroidServiceStarter(appContext)
        preferences = AndroidForegroundRefreshPreferences(appContext)
        healthScheduler = AndroidHealthScheduler(appContext)
    }

    internal constructor(serviceStarter: ServiceStarter) {
        this.serviceStarter = serviceStarter
        this.preferences = InMemoryForegroundRefreshPreferences()
        this.healthScheduler = NoOpHealthScheduler
    }

    internal constructor(
        serviceStarter: ServiceStarter,
        preferences: ForegroundRefreshPreferences
    ) {
        this.serviceStarter = serviceStarter
        this.preferences = preferences
        this.healthScheduler = NoOpHealthScheduler
    }

    internal constructor(
        serviceStarter: ServiceStarter,
        preferences: ForegroundRefreshPreferences,
        healthScheduler: HealthScheduler
    ) {
        this.serviceStarter = serviceStarter
        this.preferences = preferences
        this.healthScheduler = healthScheduler
    }

    fun liveMonitoringEnabled(): Boolean {
        return preferences.liveMonitoringEnabled()
    }

    fun setLiveMonitoringEnabled(enabled: Boolean) {
        preferences.setLiveMonitoringEnabled(enabled)
        if (enabled) {
            startPreciseRefresh()
        } else {
            stopPreciseRefresh()
        }
    }

    fun startPreciseRefresh() {
        if (preciseRefreshRequested) return
        preciseRefreshRequested = true
        healthScheduler.schedule()
        serviceStarter.start(ProviderBackgroundRefreshService.ACTION_START)
    }

    fun stopPreciseRefresh() {
        healthScheduler.cancel()
        if (!preciseRefreshRequested) return
        preciseRefreshRequested = false
        serviceStarter.start(ProviderBackgroundRefreshService.ACTION_STOP)
    }

    internal interface ServiceStarter {
        fun start(action: String)
    }

    internal interface ForegroundRefreshPreferences {
        fun liveMonitoringEnabled(): Boolean
        fun setLiveMonitoringEnabled(enabled: Boolean)
    }

    internal interface HealthScheduler {
        fun schedule()
        fun cancel()
    }

    private class AndroidServiceStarter(
        private val context: Context
    ) : ServiceStarter {
        override fun start(action: String) {
            ProviderBackgroundRefreshService.createControlIntents(context, action).forEach { intent ->
                if (action == ProviderBackgroundRefreshService.ACTION_START) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }

    private class AndroidHealthScheduler(
        private val context: Context
    ) : HealthScheduler {
        override fun schedule() {
            ForegroundRefreshHealthScheduler.schedule(context)
        }

        override fun cancel() {
            ForegroundRefreshHealthScheduler.cancel(context)
        }
    }

    private object NoOpHealthScheduler : HealthScheduler {
        override fun schedule() = Unit
        override fun cancel() = Unit
    }

    private class AndroidForegroundRefreshPreferences(
        context: Context
    ) : ForegroundRefreshPreferences {
        private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        override fun liveMonitoringEnabled(): Boolean {
            return preferences.getBoolean(KEY_LIVE_MONITORING_ENABLED, false)
        }

        override fun setLiveMonitoringEnabled(enabled: Boolean) {
            preferences.edit()
                .putBoolean(KEY_LIVE_MONITORING_ENABLED, enabled)
                .apply()
        }
    }

    private class InMemoryForegroundRefreshPreferences : ForegroundRefreshPreferences {
        private var enabled = false

        override fun liveMonitoringEnabled(): Boolean {
            return enabled
        }

        override fun setLiveMonitoringEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    companion object {
        private const val PREFS = "ai_quota_foreground_refresh"
        private const val KEY_LIVE_MONITORING_ENABLED = "live_monitoring_enabled"
    }
}
