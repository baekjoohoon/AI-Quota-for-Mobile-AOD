package com.aiusage.mobile.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.aiusage.mobile.R
import com.aiusage.mobile.localization.withAppLanguageForDeviceLanguage
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesRepository
import com.aiusage.mobile.ui.appLayoutMetrics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ProviderWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var isFinishingWithSelection = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLanguageForDeviceLanguage())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val providers = ProviderPreferencesRepository(this).providerOrder()
            .ifEmpty { ProviderId.defaultOrder() }

        if (providers.size == 1) {
            finishWithProvider(providers.first())
        } else {
            showProviderSelection(providers)
        }
    }

    private fun showProviderSelection(providers: List<ProviderId>) {
        val layoutMetrics = appLayoutMetrics(
            screenWidthDp = resources.configuration.screenWidthDp,
            screenHeightDp = resources.configuration.screenHeightDp
        )
        title = getString(R.string.widget_label_provider)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                layoutMetrics.contentHorizontalPaddingDp.dp(),
                layoutMetrics.contentVerticalPaddingDp.dp(),
                layoutMetrics.contentHorizontalPaddingDp.dp(),
                layoutMetrics.contentVerticalPaddingDp.dp()
            )
        }
        root.addView(
            TextView(this).apply {
                text = getString(R.string.widget_label_provider)
                textSize = if (resources.configuration.screenWidthDp >= 600) 20f else 18f
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        providers.forEach { providerId ->
            root.addView(
                Button(this).apply {
                    text = providerId.displayName
                    setOnClickListener { finishWithProvider(providerId) }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = layoutMetrics.cardSpacingDp.dp()
                }
            )
        }
        setContentView(root)
    }

    private fun finishWithProvider(providerId: ProviderId) {
        if (isFinishingWithSelection) return
        isFinishingWithSelection = true

        ProviderPreferencesRepository(this).saveProviderWidgetSelection(appWidgetId, providerId)
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)

        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@ProviderWidgetConfigureActivity)
                    .getGlanceIdBy(appWidgetId)
                ProviderUsageGlanceWidget().update(this@ProviderWidgetConfigureActivity, glanceId)
            }
            finish()
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).roundToInt()
    }
}
