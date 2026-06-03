package com.aiquota.mobile.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.aiquota.mobile.R
import com.aiquota.mobile.localization.withAppLanguageForDeviceLanguage
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderPreferencesRepository
import com.aiquota.mobile.local.ThemePreferencesRepository
import com.aiquota.mobile.ui.appLayoutMetrics
import com.aiquota.mobile.ui.provider.providerIconRes

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

        preselectedProviderFromIntent()?.let { providerId ->
            finishWithProvider(providerId)
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

    private fun preselectedProviderFromIntent(): ProviderId? {
        val options = intent?.extras?.getBundle(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS)
        return ProviderId.fromStorageId(intent?.getStringExtra(PinnedWidgetRequester.EXTRA_PINNED_PROVIDER_ID))
            ?: ProviderId.fromStorageId(options?.getString(PinnedWidgetRequester.EXTRA_PINNED_PROVIDER_ID))
    }

    private fun showProviderSelection(providers: List<ProviderId>) {
        val style = widgetConfigureStyle(ThemePreferencesRepository(this).currentTheme())
        applyWidgetConfigureWindow(style)
        val layoutMetrics = appLayoutMetrics(
            screenWidthDp = resources.configuration.screenWidthDp,
            screenHeightDp = resources.configuration.screenHeightDp
        )
        title = getString(R.string.widget_label_provider)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(style.backgroundColor)
            setPadding(
                layoutMetrics.contentHorizontalPaddingDp.dp(),
                layoutMetrics.contentVerticalPaddingDp.dp() + widgetConfigureStatusBarInsetPx(),
                layoutMetrics.contentHorizontalPaddingDp.dp(),
                layoutMetrics.contentVerticalPaddingDp.dp()
            )
        }
        root.addView(
            TextView(this).apply {
                text = getString(R.string.widget_label_provider)
                applyWidgetConfigureText(
                    style = style,
                    textSizeSp = if (resources.configuration.screenWidthDp >= 600) 20f else 18f,
                    bold = true
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        providers.forEach { providerId ->
            root.addView(
                providerSelectionRow(providerId, style),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = layoutMetrics.cardSpacingDp.dp()
                }
            )
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(style.backgroundColor)
                isFillViewport = true
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        )
    }

    private fun providerSelectionRow(providerId: ProviderId, style: WidgetConfigureStyle): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            applyWidgetConfigureRowBackground(style)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            setOnClickListener { finishWithProvider(providerId) }

            addView(
                ImageView(this@ProviderWidgetConfigureActivity).apply {
                    setImageResource(providerIconRes(providerId))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = providerId.displayName
                },
                LinearLayout.LayoutParams(32.dp(), 32.dp())
            )

            addView(
                TextView(this@ProviderWidgetConfigureActivity).apply {
                    text = providerId.displayName
                    applyWidgetConfigureText(style, textSizeSp = 16f)
                    gravity = Gravity.CENTER_VERTICAL
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 12.dp()
                }
            )

            addView(
                TextView(this@ProviderWidgetConfigureActivity).apply {
                    text = ">"
                    applyWidgetConfigureText(style, textSizeSp = 16f, muted = true)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(24.dp(), 24.dp())
            )
        }
    }

    private fun finishWithProvider(providerId: ProviderId) {
        if (isFinishingWithSelection) return
        isFinishingWithSelection = true

        ProviderPreferencesRepository(this).saveProviderWidgetSelection(appWidgetId, providerId)
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        ProviderWidgetImmediateRenderer.render(this, appWidgetId, providerId)
        setResult(Activity.RESULT_OK, resultValue)
        ProviderWidgetPostConfigureUpdater.schedule(applicationContext, appWidgetId, providerId)
        ProviderWidgetConfigureRefreshRequester.schedule(applicationContext, appWidgetId, providerId)
        returnHomeIfLaunchedFromPinCallback()
        finish()
    }

    private fun returnHomeIfLaunchedFromPinCallback() {
        if (intent?.action == null) {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    private fun Int.dp(): Int {
        return widgetConfigureDp(this@ProviderWidgetConfigureActivity)
    }
}
