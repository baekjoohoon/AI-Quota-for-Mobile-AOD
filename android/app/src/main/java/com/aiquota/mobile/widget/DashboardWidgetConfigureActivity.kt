package com.aiquota.mobile.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.aiquota.mobile.R
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderPreferencesCodec
import com.aiquota.mobile.local.ProviderPreferencesRepository
import com.aiquota.mobile.local.ThemePreferencesRepository
import com.aiquota.mobile.localization.withAppLanguageForDeviceLanguage
import com.aiquota.mobile.providers.UsageSurfaceRefresher
import com.aiquota.mobile.ui.appLayoutMetrics
import com.aiquota.mobile.ui.dashboard.ProviderCardOrder
import com.aiquota.mobile.ui.provider.providerIconRes
import kotlin.math.abs

private const val WIDGET_CONFIGURE_VISIBILITY_BUTTON_SIZE_DP = 28
private const val WIDGET_CONFIGURE_VISIBILITY_BUTTON_TEXT_SIZE_SP = 16f

class DashboardWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var preferencesRepository: ProviderPreferencesRepository
    private lateinit var visibleSectionTitle: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var hiddenSectionTitle: TextView
    private lateinit var hiddenListContainer: LinearLayout
    private var providerOrder: List<ProviderId> = emptyList()
    private var previewProviderOrder: List<ProviderId> = emptyList()
    private var hiddenProviders: Set<ProviderId> = emptySet()
    private var draggedRow: View? = null
    private var draggedProviderId: ProviderId? = null
    private val rowTranslationAnimators = mutableMapOf<View, ValueAnimator>()
    private val rowTranslationTargets = mutableMapOf<View, Float>()

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

        preferencesRepository = ProviderPreferencesRepository(this)
        providerOrder = ProviderCardOrder.normalizedOrder(
            preferencesRepository.dashboardWidgetProviderOrder(appWidgetId)
        )
        hiddenProviders = preferencesRepository.dashboardWidgetHiddenProviders(appWidgetId)
        previewProviderOrder = visibleProviderOrder()
        showProviderOrder()
    }

    private fun showProviderOrder() {
        val style = widgetConfigureStyle(ThemePreferencesRepository(this).currentTheme())
        applyWidgetConfigureWindow(style)
        val layoutMetrics = appLayoutMetrics(
            screenWidthDp = resources.configuration.screenWidthDp,
            screenHeightDp = resources.configuration.screenHeightDp
        )
        title = getString(R.string.widget_configure_order_title)

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
                text = getString(R.string.widget_configure_order_title)
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
        root.addView(
            TextView(this).apply {
                text = getString(R.string.provider_drag_hint)
                applyWidgetConfigureText(style, textSizeSp = 13f, muted = true)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
            }
        )
        visibleSectionTitle = TextView(this).apply {
            text = getString(R.string.widget_configure_visible_title)
            applyWidgetConfigureText(style, textSizeSp = 13f, bold = true, muted = true)
        }
        root.addView(
            visibleSectionTitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = layoutMetrics.cardSpacingDp.dp()
            }
        )

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(
            listContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
            }
        )
        hiddenSectionTitle = TextView(this).apply {
            text = getString(R.string.widget_configure_hidden_title)
            applyWidgetConfigureText(style, textSizeSp = 13f, bold = true, muted = true)
        }
        root.addView(
            hiddenSectionTitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = layoutMetrics.cardSpacingDp.dp()
            }
        )
        hiddenListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(
            hiddenListContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
            }
        )
        renderProviderRows(style)

        root.addView(
            TextView(this).apply {
                text = getString(R.string.widget_configure_done)
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
                applyWidgetConfigureText(style, textSizeSp = 15f, bold = true)
                setTextColor(style.primaryButtonTextColor)
                background = context.widgetConfigureRoundedBackground(
                    fillColor = style.accentColor,
                    strokeColor = style.accentColor,
                    cornerRadiusDp = style.buttonCornerRadiusDp
                )
                setOnClickListener { finishConfigured() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = layoutMetrics.cardSpacingDp.dp()
            }
        )
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

    private fun renderProviderRows(style: WidgetConfigureStyle) {
        cancelProviderRowAnimations()
        listContainer.removeAllViews()
        hiddenListContainer.removeAllViews()
        previewProviderOrder = previewProviderOrder.filterNot { it in hiddenProviders }
        previewProviderOrder.distinct().forEach { providerId ->
            listContainer.addView(
                providerOrderRow(providerId, style),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dp()
                }
            )
        }
        val hiddenProviderOrder = hiddenProviderOrder()
        val hiddenVisibility = if (hiddenProviderOrder.isEmpty()) View.GONE else View.VISIBLE
        hiddenSectionTitle.visibility = hiddenVisibility
        hiddenListContainer.visibility = hiddenVisibility
        hiddenProviderOrder.forEach { providerId ->
            hiddenListContainer.addView(
                hiddenProviderRow(providerId, style),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dp()
                }
            )
        }
    }

    private fun cancelProviderRowAnimations() {
        if (!::listContainer.isInitialized) return
        rowTranslationAnimators.values.forEach { it.cancel() }
        rowTranslationAnimators.clear()
        rowTranslationTargets.clear()
        for (index in 0 until listContainer.childCount) {
            val row = listContainer.getChildAt(index) ?: continue
            row.animate().cancel()
            row.clearAnimation()
            row.translationY = 0f
            row.alpha = 1f
            row.scaleX = 1f
            row.scaleY = 1f
            row.elevation = 0f
        }
    }

    private fun providerOrderRow(providerId: ProviderId, style: WidgetConfigureStyle): LinearLayout {
        return LinearLayout(this).apply {
            tag = providerId
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            applyWidgetConfigureRowBackground(style)
            setPadding(14.dp(), 12.dp(), 10.dp(), 12.dp())

            addView(
                ImageView(this@DashboardWidgetConfigureActivity).apply {
                    setImageResource(providerIconRes(providerId))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = providerId.displayName
                },
                LinearLayout.LayoutParams(30.dp(), 30.dp())
            )

            addView(
                TextView(this@DashboardWidgetConfigureActivity).apply {
                    text = providerId.displayName
                    applyWidgetConfigureText(style, textSizeSp = 16f)
                    gravity = Gravity.CENTER_VERTICAL
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 12.dp()
                }
            )

            addView(
                providerVisibilityButton(
                    symbol = "-",
                    contentDescription = getString(R.string.widget_configure_remove_provider, providerId.displayName),
                    style = style,
                    onClick = { hideProviderFromWidget(providerId) }
                ),
                LinearLayout.LayoutParams(
                    WIDGET_CONFIGURE_VISIBILITY_BUTTON_SIZE_DP.dp(),
                    WIDGET_CONFIGURE_VISIBILITY_BUTTON_SIZE_DP.dp()
                ).apply {
                    marginStart = 8.dp()
                }
            )

            addView(
                ImageView(this@DashboardWidgetConfigureActivity).apply {
                    setImageResource(R.drawable.ic_drag_handle)
                    imageTintList = ColorStateList.valueOf(style.mutedTextColor)
                    scaleType = ImageView.ScaleType.CENTER
                    contentDescription = getString(R.string.provider_reorder_handle)
                    setPadding(6.dp(), 6.dp(), 6.dp(), 6.dp())
                    setOnTouchListener(providerDragTouchListener(providerId))
                },
                LinearLayout.LayoutParams(44.dp(), 44.dp())
            )
        }
    }

    private fun hiddenProviderRow(providerId: ProviderId, style: WidgetConfigureStyle): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            applyWidgetConfigureRowBackground(style)
            setPadding(14.dp(), 12.dp(), 10.dp(), 12.dp())

            addView(
                ImageView(this@DashboardWidgetConfigureActivity).apply {
                    setImageResource(providerIconRes(providerId))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    alpha = 0.72f
                    contentDescription = providerId.displayName
                },
                LinearLayout.LayoutParams(30.dp(), 30.dp())
            )

            addView(
                TextView(this@DashboardWidgetConfigureActivity).apply {
                    text = providerId.displayName
                    applyWidgetConfigureText(style, textSizeSp = 16f, muted = true)
                    gravity = Gravity.CENTER_VERTICAL
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 12.dp()
                }
            )

            addView(
                providerVisibilityButton(
                    symbol = "+",
                    contentDescription = getString(R.string.widget_configure_add_provider, providerId.displayName),
                    style = style,
                    onClick = { restoreProviderToWidget(providerId) }
                ),
                LinearLayout.LayoutParams(
                    WIDGET_CONFIGURE_VISIBILITY_BUTTON_SIZE_DP.dp(),
                    WIDGET_CONFIGURE_VISIBILITY_BUTTON_SIZE_DP.dp()
                ).apply {
                    marginStart = 8.dp()
                }
            )
        }
    }

    private fun providerVisibilityButton(
        symbol: String,
        contentDescription: String,
        style: WidgetConfigureStyle,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = symbol
            this.contentDescription = contentDescription
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(0, 0, 0, 1.dp())
            applyWidgetConfigureText(
                style = style,
                textSizeSp = WIDGET_CONFIGURE_VISIBILITY_BUTTON_TEXT_SIZE_SP,
                bold = true
            )
            setTextColor(style.accentColor)
            background = context.widgetConfigureRoundedBackground(
                fillColor = style.backgroundColor,
                strokeColor = style.borderColor,
                cornerRadiusDp = style.buttonCornerRadiusDp
            )
            setOnClickListener { onClick() }
        }
    }

    private fun providerDragTouchListener(providerId: ProviderId): View.OnTouchListener {
        val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
        var startRawY = 0f
        var dragging = false
        var pendingLongPress: Runnable? = null

        return View.OnTouchListener { handle, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = event.rawY
                    dragging = false
                    pendingLongPress = Runnable {
                        dragging = true
                        startDraggingRow(providerId)
                        listContainer.parent?.requestDisallowInterceptTouchEvent(true)
                    }.also {
                        handle.postDelayed(it, longPressTimeoutMs)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        handleDragMove(providerId, event.rawY, startRawY)
                        true
                    } else {
                        true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    pendingLongPress?.let { handle.removeCallbacks(it) }
                    if (dragging) {
                        finishDraggingRow(commitDrop = true)
                    }
                    dragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    pendingLongPress?.let { handle.removeCallbacks(it) }
                    if (dragging) {
                        cancelDraggingRow()
                    }
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun startDraggingRow(providerId: ProviderId) {
        draggedProviderId = providerId
        previewProviderOrder = providerRowOrder().ifEmpty { providerOrder }.distinct()
        draggedRow = findProviderRow(providerId)?.apply {
            alpha = 0.82f
            scaleX = 1.02f
            scaleY = 1.02f
            elevation = 8.dp().toFloat()
        }
    }

    private fun finishDraggingRow(commitDrop: Boolean) {
        draggedRow?.apply {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            elevation = 0f
        }
        draggedRow = null
        draggedProviderId = null
        if (commitDrop && previewProviderOrder != visibleProviderOrder()) {
            providerOrder = orderWithVisibleProviders(previewProviderOrder)
            preferencesRepository.saveDashboardWidgetProviderOrder(appWidgetId, providerOrder)
            previewProviderOrder = visibleProviderOrder()
            postRenderProviderRows(widgetConfigureStyle(ThemePreferencesRepository(this).currentTheme()))
            refreshConfiguredWidgets()
        } else {
            resetProviderRowTranslations()
        }
        listContainer.parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun cancelDraggingRow() {
        draggedRow?.apply {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            elevation = 0f
        }
        draggedRow = null
        draggedProviderId = null
        previewProviderOrder = visibleProviderOrder()
        postRenderProviderRows(widgetConfigureStyle(ThemePreferencesRepository(this).currentTheme()))
        listContainer.parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun handleDragMove(providerId: ProviderId, rawY: Float, startRawY: Float) {
        val targetIndex = dragTargetIndex(rawY)
        if (targetIndex < 0) return

        val draggedTranslationY = rawY - startRawY
        val currentIndex = previewProviderOrder.indexOf(providerId)
        if (currentIndex < 0) {
            applyProviderRowTranslations(providerId, draggedTranslationY)
            return
        }
        if (targetIndex != currentIndex) {
            previewProviderOrder = ProviderCardOrder.previewVisibleOrder(
                visibleOrder = previewProviderOrder,
                draggedProvider = providerId,
                targetVisibleIndex = targetIndex
            )
        }
        applyProviderRowTranslations(providerId, draggedTranslationY)
    }

    private fun dragTargetIndex(rawY: Float): Int {
        if (!::listContainer.isInitialized || listContainer.childCount == 0) return -1
        val containerLocation = IntArray(2)
        listContainer.getLocationOnScreen(containerLocation)
        val localY = rawY - containerLocation[1]
        for (index in 0 until listContainer.childCount) {
            val child = listContainer.getChildAt(index)
            if (localY < child.top + child.height / 2f) return index
        }
        return listContainer.childCount - 1
    }

    private fun findProviderRow(providerId: ProviderId): View? {
        for (index in 0 until listContainer.childCount) {
            val child = listContainer.getChildAt(index)
            if (child.tag == providerId) return child
        }
        return null
    }

    private fun providerRowOrder(): List<ProviderId> {
        return buildList {
            for (index in 0 until listContainer.childCount) {
                val providerId = listContainer.getChildAt(index).tag as? ProviderId
                if (providerId != null) add(providerId)
            }
        }
    }

    private fun applyProviderRowTranslations(draggedProvider: ProviderId, draggedTranslationY: Float) {
        val rowStep = providerRowStepPx()
        for (index in 0 until listContainer.childCount) {
            val row = listContainer.getChildAt(index) ?: continue
            val providerId = row.tag as? ProviderId ?: continue
            if (providerId == draggedProvider) {
                row.translationY = draggedTranslationY
                continue
            }
            val previewIndex = previewProviderOrder.indexOf(providerId)
            if (previewIndex < 0) continue
            val targetTranslation = (previewIndex - index) * rowStep
            animateProviderRowTranslation(row, targetTranslation)
        }
    }

    private fun animateProviderRowTranslation(row: View, targetTranslation: Float) {
        val existingTarget = rowTranslationTargets[row]
        if (existingTarget != null && abs(existingTarget - targetTranslation) < 0.5f) return

        rowTranslationAnimators.remove(row)?.cancel()
        rowTranslationTargets[row] = targetTranslation

        val startTranslation = row.translationY
        if (abs(startTranslation - targetTranslation) < 0.5f) {
            row.translationY = targetTranslation
            rowTranslationTargets.remove(row)
            return
        }

        val animator = ValueAnimator.ofFloat(startTranslation, targetTranslation).apply {
            duration = 120L
            interpolator = DecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                row.translationY = valueAnimator.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (rowTranslationAnimators[row] == animation) {
                        row.translationY = targetTranslation
                        rowTranslationAnimators.remove(row)
                        rowTranslationTargets.remove(row)
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (rowTranslationAnimators[row] == animation) {
                        rowTranslationAnimators.remove(row)
                        rowTranslationTargets.remove(row)
                    }
                }
            })
        }
        rowTranslationAnimators[row] = animator
        animator.start()
    }

    private fun resetProviderRowTranslations() {
        rowTranslationAnimators.values.forEach { it.cancel() }
        rowTranslationAnimators.clear()
        rowTranslationTargets.clear()
        for (index in 0 until listContainer.childCount) {
            val row = listContainer.getChildAt(index) ?: continue
            row.animate().cancel()
            row.translationY = 0f
        }
    }

    private fun providerRowStepPx(): Float {
        val first = firstProviderRow() ?: return 0f
        val bottomMargin = (first.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        return (first.height + bottomMargin).toFloat()
    }

    private fun firstProviderRow(): View? {
        for (index in 0 until listContainer.childCount) {
            val row = listContainer.getChildAt(index) ?: continue
            if (row.tag is ProviderId) {
                return row
            }
        }
        return null
    }

    private fun postRenderProviderRows(style: WidgetConfigureStyle) {
        listContainer.post {
            if (!isFinishing && !isDestroyed) {
                renderProviderRows(style)
            }
        }
    }

    private fun hideProviderFromWidget(providerId: ProviderId) {
        if (providerId in hiddenProviders) return
        hiddenProviders = hiddenProviders + providerId
        preferencesRepository.setDashboardWidgetProviderHidden(appWidgetId, providerId, true)
        previewProviderOrder = visibleProviderOrder()
        renderProviderRows(widgetConfigureStyle(ThemePreferencesRepository(this).currentTheme()))
        refreshConfiguredWidgets()
    }

    private fun restoreProviderToWidget(providerId: ProviderId) {
        if (providerId !in hiddenProviders) return
        hiddenProviders = hiddenProviders - providerId
        preferencesRepository.setDashboardWidgetProviderHidden(appWidgetId, providerId, false)
        previewProviderOrder = visibleProviderOrder()
        renderProviderRows(widgetConfigureStyle(ThemePreferencesRepository(this).currentTheme()))
        refreshConfiguredWidgets()
    }

    private fun visibleProviderOrder(): List<ProviderId> {
        return ProviderPreferencesCodec.visibleProviders(providerOrder, hiddenProviders)
    }

    private fun hiddenProviderOrder(): List<ProviderId> {
        return ProviderCardOrder.normalizedOrder(providerOrder).filter { it in hiddenProviders }
    }

    private fun orderWithVisibleProviders(visibleOrder: List<ProviderId>): List<ProviderId> {
        val reorderedVisible = ArrayDeque(visibleOrder.distinct())
        return ProviderCardOrder.normalizedOrder(providerOrder).mapNotNull { providerId ->
            if (providerId in hiddenProviders) {
                providerId
            } else {
                reorderedVisible.removeFirstOrNull()
            }
        }
    }

    private fun refreshConfiguredWidgets() {
        UsageSurfaceRefresher.refreshWidgetSurfaces(applicationContext)
    }

    private fun finishConfigured() {
        preferencesRepository.saveDashboardWidgetProviderOrder(appWidgetId, providerOrder)
        preferencesRepository.saveDashboardWidgetHiddenProviders(appWidgetId, hiddenProviders)
        refreshConfiguredWidgets()
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
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
        return widgetConfigureDp(this@DashboardWidgetConfigureActivity)
    }
}
