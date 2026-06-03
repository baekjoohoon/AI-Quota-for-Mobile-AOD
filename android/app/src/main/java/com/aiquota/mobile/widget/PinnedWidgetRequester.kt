package com.aiquota.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.aiquota.mobile.local.ProviderId

enum class DashboardPinnedWidgetType {
    DASHBOARD,
    BATTERY
}

enum class WidgetPinRequestStatus {
    REQUESTED,
    UNSUPPORTED,
    FAILED
}

object PinnedWidgetRequester {
    const val EXTRA_PINNED_PROVIDER_ID = "com.aiquota.mobile.extra.PINNED_PROVIDER_ID"

    fun requestProviderWidget(context: Context, providerId: ProviderId): WidgetPinRequestStatus {
        val appContext = context.applicationContext
        val extras = Bundle().apply {
            putString(EXTRA_PINNED_PROVIDER_ID, providerId.storageId)
        }
        return requestPin(
            context = appContext,
            provider = ComponentName(appContext, ProviderUsageWidgetProvider::class.java),
            extras = extras,
            successCallback = configureActivityCallback(
                context = appContext,
                requestCode = PROVIDER_WIDGET_PIN_REQUEST_CODE_BASE + providerId.ordinal,
                intent = Intent(appContext, ProviderWidgetConfigureActivity::class.java).apply {
                    putExtra(EXTRA_PINNED_PROVIDER_ID, providerId.storageId)
                }
            )
        )
    }

    fun requestDashboardWidget(context: Context, type: DashboardPinnedWidgetType): WidgetPinRequestStatus {
        val appContext = context.applicationContext
        val providerClass = when (type) {
            DashboardPinnedWidgetType.DASHBOARD -> AIQuotaUnifiedGlanceWidgetReceiver::class.java
            DashboardPinnedWidgetType.BATTERY -> AIQuotaCircularWidgetProvider::class.java
        }
        return requestPin(
            context = appContext,
            provider = ComponentName(appContext, providerClass),
            extras = null,
            successCallback = configureActivityCallback(
                context = appContext,
                requestCode = DASHBOARD_WIDGET_PIN_REQUEST_CODE_BASE + type.ordinal,
                intent = Intent(appContext, DashboardWidgetConfigureActivity::class.java)
            )
        )
    }

    private fun requestPin(
        context: Context,
        provider: ComponentName,
        extras: Bundle?,
        successCallback: PendingIntent?
    ): WidgetPinRequestStatus {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (!appWidgetManager.isRequestPinAppWidgetSupported) {
            return WidgetPinRequestStatus.UNSUPPORTED
        }
        return if (appWidgetManager.requestPinAppWidget(provider, extras, successCallback)) {
            WidgetPinRequestStatus.REQUESTED
        } else {
            WidgetPinRequestStatus.FAILED
        }
    }

    private fun configureActivityCallback(
        context: Context,
        requestCode: Int,
        intent: Intent
    ): PendingIntent {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableCallbackFlag()
        )
    }

    private fun mutableCallbackFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
    }

    private const val PROVIDER_WIDGET_PIN_REQUEST_CODE_BASE = 70_000
    private const val DASHBOARD_WIDGET_PIN_REQUEST_CODE_BASE = 71_000
}
