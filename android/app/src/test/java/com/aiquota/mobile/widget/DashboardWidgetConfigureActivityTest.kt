package com.aiquota.mobile.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardWidgetConfigureActivityTest {
    @Test
    fun dashboardAndCircularWidgetsExposeProviderOrderConfiguration() {
        assertEquals(
            "com.aiquota.mobile.widget.DashboardWidgetConfigureActivity",
            appWidgetConfigureActivity("src/main/res/xml/ai_quota_widget_circular.xml")
        )
        assertEquals(
            "com.aiquota.mobile.widget.DashboardWidgetConfigureActivity",
            appWidgetConfigureActivity("src/main/res/xml/ai_quota_widget_large.xml")
        )
    }

    @Test
    fun dashboardAndCircularWidgetsCanBeReconfiguredFromLauncherSettings() {
        listOf(
            "src/main/res/xml/ai_quota_widget_circular.xml",
            "src/main/res/xml/ai_quota_widget_large.xml"
        ).forEach { path ->
            val features = appWidgetFeatures(path)
            assertTrue("$path should expose launcher reconfiguration", features.contains("reconfigurable"))
            assertTrue("$path should keep initial placement optional", features.contains("configuration_optional"))
        }
    }

    @Test
    fun providerOrderConfigurationPersistsPerWidgetOrderAndRefreshesSurfacesOnly() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val source = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt")

        assertTrue(manifest.contains(".widget.DashboardWidgetConfigureActivity"))
        assertTrue(source.exists())
        val text = source.readText()
        assertTrue(text.contains("dashboardWidgetProviderOrder(appWidgetId)"))
        assertTrue(text.contains("dashboardWidgetHiddenProviders(appWidgetId)"))
        assertTrue(text.contains("saveDashboardWidgetProviderOrder(appWidgetId, providerOrder)"))
        assertTrue(text.contains("UsageSurfaceRefresher.refreshWidgetSurfaces"))
        assertTrue(!text.contains("UsageSurfaceRefresher.refresh("))
        assertTrue(!text.contains("LocalUsageRepository"))
        assertTrue(text.contains("ProviderCardOrder.normalizedOrder"))
    }

    @Test
    fun providerOrderConfigurationUsesAsyncApplyAndRedrawsWithoutRewritingCache() {
        val repositorySource = File("src/main/java/com/aiquota/mobile/local/ProviderPreferencesRepository.kt").readText()
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val saveProviderOrderBody = repositorySource.substringAfter("fun saveDashboardWidgetProviderOrder").substringBefore("fun saveDashboardWidgetHiddenProviders")
        val refreshWidgetsBody = activitySource.substringAfter("private fun refreshConfiguredWidgets").substringBefore("private fun finishConfigured")

        assertTrue(
            "Widget order persistence should not block UI; reconfiguration should redraw widgets without rewriting the shared snapshot cache.",
            saveProviderOrderBody.contains(".apply()") &&
                !saveProviderOrderBody.contains(".commit()") &&
                refreshWidgetsBody.contains("refreshWidgetSurfaces") &&
                !refreshWidgetsBody.contains("UsageSurfaceRefresher.refresh(")
        )
    }

    @Test
    fun providerOrderConfigurationCanExcludeAndRestoreWidgetProviders() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val strings = File("src/main/res/values/strings.xml").readText()
        val koStrings = File("src/main/res/values-ko/strings.xml").readText()

        assertTrue(activitySource.contains("hiddenProviders"))
        assertTrue(activitySource.contains("hiddenListContainer"))
        assertTrue(activitySource.contains("hideProviderFromWidget(providerId)"))
        assertTrue(activitySource.contains("restoreProviderToWidget(providerId)"))
        assertTrue(activitySource.contains("R.string.widget_configure_visible_title"))
        assertTrue(activitySource.contains("R.string.widget_configure_hidden_title"))
        assertTrue(activitySource.contains("R.string.widget_configure_remove_provider"))
        assertTrue(activitySource.contains("R.string.widget_configure_add_provider"))
        assertTrue(strings.contains("widget_configure_visible_title"))
        assertTrue(strings.contains("widget_configure_hidden_title"))
        assertTrue(koStrings.contains("widget_configure_visible_title"))
        assertTrue(koStrings.contains("widget_configure_hidden_title"))
    }

    @Test
    fun dashboardAndCircularWidgetsRenderWithTheirOwnWidgetConfiguration() {
        val dashboardSource = File("src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt").readText()
        val circularSource = File("src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt").readText()

        listOf(dashboardSource, circularSource).forEach { source ->
            assertTrue(source.contains("dashboardWidgetPayload("))
            assertTrue(source.contains("dashboardWidgetProviderOrder(appWidgetId)"))
            assertTrue(source.contains("dashboardWidgetHiddenProviders(appWidgetId)"))
        }
    }

    @Test
    fun providerOrderConfigurationPlacesVisibilityControlsAtTrailingEdge() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val visibleRow = activitySource.substringAfter("private fun providerOrderRow").substringBefore("private fun hiddenProviderRow")
        val hiddenRow = activitySource.substringAfter("private fun hiddenProviderRow").substringBefore("private fun providerVisibilityButton")

        assertTrue(
            "Visible rows should show provider identity before the remove button.",
            visibleRow.indexOf("providerIconRes(providerId)") < visibleRow.indexOf("providerVisibilityButton(")
        )
        assertTrue(
            "Visible rows should place the remove button immediately before the drag handle.",
            visibleRow.indexOf("providerVisibilityButton(") < visibleRow.indexOf("R.drawable.ic_drag_handle")
        )
        assertTrue(
            "Hidden rows should show provider identity before the add button.",
            hiddenRow.indexOf("providerIconRes(providerId)") < hiddenRow.indexOf("providerVisibilityButton(")
        )
        assertTrue(
            "Visibility controls should be smaller than the drag handle.",
            activitySource.contains("WIDGET_CONFIGURE_VISIBILITY_BUTTON_SIZE_DP = 28") &&
                activitySource.contains("WIDGET_CONFIGURE_VISIBILITY_BUTTON_TEXT_SIZE_SP = 16f")
        )
    }

    @Test
    fun providerOrderConfigurationCommitsOnlyWhenDragDrops() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val dragMoveBody = activitySource.substringAfter("fun handleDragMove").substringBefore("private fun dragTargetIndex")
        val dropBody = activitySource.substringAfter("fun finishDraggingRow").substringBefore("private fun cancelDraggingRow")

        assertTrue(
            "Dragging should update only the visual preview order and must not persist provider order before drop.",
            !dragMoveBody.contains("saveDashboardWidgetProviderOrder") &&
                !dragMoveBody.contains("refreshConfiguredWidgets")
        )
        assertTrue(
            "Provider order must be committed only when the user drops the dragged row.",
            dropBody.contains("commitDrop: Boolean") &&
                dropBody.contains("if (commitDrop") &&
                dropBody.contains("saveDashboardWidgetProviderOrder(appWidgetId, providerOrder)") &&
                dropBody.contains("refreshConfiguredWidgets")
        )
    }

    @Test
    fun providerOrderConfigurationRefreshesWidgetsAgainWhenDoneIsPressed() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val finishBody = activitySource.substringAfter("fun finishConfigured").substringBefore("private fun Int.dp")
        val refreshBody = activitySource.substringAfter("private fun refreshConfiguredWidgets").substringBefore("private fun finishConfigured")

        assertTrue(
            "Done should refresh widgets with the configured order and hidden providers before closing the configuration screen.",
            finishBody.contains("saveDashboardWidgetProviderOrder(appWidgetId, providerOrder)") &&
                finishBody.contains("saveDashboardWidgetHiddenProviders(appWidgetId, hiddenProviders)") &&
                finishBody.contains("refreshConfiguredWidgets()") &&
                refreshBody.contains("refreshWidgetSurfaces")
        )
    }

    @Test
    fun dashboardWidgetConfigurationReturnsHomeOnlyForInAppPinCallback() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val finishBody = activitySource.substringAfter("fun finishConfigured").substringBefore("private fun Int.dp")

        assertTrue(finishBody.contains("returnHomeIfLaunchedFromPinCallback()"))
        assertTrue(activitySource.contains("private fun returnHomeIfLaunchedFromPinCallback()"))
        assertTrue(activitySource.contains("intent?.action == null"))
        assertTrue(activitySource.contains("Intent(Intent.ACTION_MAIN)"))
        assertTrue(activitySource.contains("Intent.CATEGORY_HOME"))
        assertTrue(activitySource.contains("Intent.FLAG_ACTIVITY_NEW_TASK"))
    }

    @Test
    fun providerOrderConfigurationUsesThemedDragRowsWithProviderIcons() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()

        assertTrue(
            "Provider order configuration should use the app theme instead of unstyled platform defaults.",
            activitySource.contains("widgetConfigureStyle") &&
                activitySource.contains("ThemePreferencesRepository")
        )
        assertTrue(
            "Provider order rows should include the provider icon next to the provider name.",
            activitySource.contains("providerIconRes(providerId)")
        )
        assertTrue(
            "Provider order should be changed by a long-press drag handle, not move up/down buttons.",
            activitySource.contains("provider_drag_hint") &&
                activitySource.contains("setOnTouchListener") &&
                activitySource.contains("ViewConfiguration.getLongPressTimeout") &&
                !activitySource.contains("provider_move_up") &&
                !activitySource.contains("provider_move_down")
        )
    }

    @Test
    fun dashboardGridUsesPreviewOrderAndAnimatedDragOverlay() {
        val dashboardSource = File("src/main/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardScreen.kt").readText()

        assertTrue(
            "Dashboard grid should render a temporary preview order while dragging and commit only on drag end.",
            dashboardSource.contains("previewProviders") &&
                dashboardSource.contains("previewDragTargetIndex") &&
                dashboardSource.contains("ProviderCardOrder.previewVisibleOrder") &&
                dashboardSource.contains("onDragEnd") &&
                dashboardSource.contains("onReorderProvider(providerId, finalTargetIndex)")
        )
        assertTrue(
            "Dashboard grid cards should animate layout movement and make the dragged card look like a floating overlay.",
            dashboardSource.contains("LazyVerticalGrid") &&
                dashboardSource.contains(".animateItem(") &&
                dashboardSource.contains("DashboardDragOverlay(") &&
                dashboardSource.contains("isPlaceholder = providerId == draggedProvider") &&
                dashboardSource.contains("forceDraggingVisual")
        )
    }

    @Test
    fun dashboardGridDragAutoScrollsNearViewportEdges() {
        val dashboardSource = File("src/main/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardScreen.kt").readText()

        assertTrue(
            "Dashboard provider drag should scroll only when the dragged overlay approaches the viewport edges.",
            dashboardSource.contains("scrollBy(delta)") &&
                dashboardSource.contains("onAutoScroll") &&
                dashboardSource.contains("dashboardAutoScrollDelta")
        )
    }

    @Test
    fun providerOrderDragDoesNotRebuildRowsDuringActiveTouch() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val startBody = activitySource.substringAfter("fun startDraggingRow").substringBefore("private fun finishDraggingRow")
        val moveBody = activitySource.substringAfter("fun handleDragMove").substringBefore("private fun dragTargetIndex")
        val translationBody = activitySource.substringAfter("fun applyProviderRowTranslations").substringBefore("private fun resetProviderRowTranslations")

        assertTrue(
            "Provider order drag must not rebuild row views while an active touch target is being dragged.",
            !startBody.contains("reconcileProviderRowsIfNeeded") &&
                !moveBody.contains("renderProviderRows") &&
                !translationBody.contains("reconcileProviderRowsIfNeeded") &&
                !translationBody.contains("renderProviderRows")
        )
    }

    @Test
    fun providerOrderDragDoesNotDetachTheTouchedRowDuringMove() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val moveBody = activitySource.substringAfter("fun handleDragMove").substringBefore("private fun dragTargetIndex")
        val translationBody = activitySource.substringAfter("fun applyProviderRowTranslations").substringBefore("private fun resetProviderRowTranslations")

        assertTrue(
            "Provider order drag should not remove/add the touched row during ACTION_MOVE because detaching the active touch target causes jank and crashes.",
            moveBody.contains("applyProviderRowTranslations") &&
                !moveBody.contains("moveProviderRowView") &&
                !translationBody.contains("reconcileProviderRowsIfNeeded")
        )
    }

    @Test
    fun providerOrderDragHandlesTransientMissingRowsWithoutCrash() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val translationBody = activitySource.substringAfter("fun applyProviderRowTranslations").substringBefore("private fun resetProviderRowTranslations")
        val stepBody = activitySource.substringAfter("fun providerRowStepPx").substringBefore("private fun refreshConfiguredWidgets")

        assertTrue(
            "Provider order drag should tolerate a transient empty or stale child list instead of crashing on getChildAt(0).",
            translationBody.contains("listContainer.getChildAt(index) ?: continue") &&
                stepBody.contains("?: return 0f") &&
                !stepBody.contains("getChildAt(0)")
        )
    }

    @Test
    fun providerOrderRowsCancelAnimationsBeforeRebuild() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val renderBody = activitySource.substringAfter("fun renderProviderRows").substringBefore("private fun providerOrderRow")

        assertTrue(
            "Provider order rows must cancel active ViewPropertyAnimators before removeAllViews to avoid draw-time crashes.",
            activitySource.contains("fun cancelProviderRowAnimations") &&
                renderBody.contains("cancelProviderRowAnimations()") &&
                renderBody.indexOf("cancelProviderRowAnimations()") < renderBody.indexOf("listContainer.removeAllViews()")
        )
    }

    @Test
    fun providerOrderDragSmoothlyAnimatesNonDraggedRowsWithoutViewPropertyAnimator() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val translationBody = activitySource.substringAfter("fun applyProviderRowTranslations").substringBefore("private fun resetProviderRowTranslations")

        assertTrue(
            "Provider order drag should animate displaced rows smoothly without using ViewPropertyAnimator during move.",
            activitySource.contains("ValueAnimator") &&
                activitySource.contains("animateProviderRowTranslation") &&
                translationBody.contains("animateProviderRowTranslation(row, targetTranslation)") &&
                !translationBody.contains("row.animate().translationY") &&
                !translationBody.contains(".setDuration(120L)")
        )
    }

    @Test
    fun providerOrderDragKeepsDraggedRowFollowingPointerEvenWhenPreviewIndexDoesNotChange() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val moveBody = activitySource.substringAfter("fun handleDragMove").substringBefore("private fun dragTargetIndex")

        assertTrue(
            "The dragged row must keep following the pointer on every move, even before the target index changes.",
            moveBody.contains("val draggedTranslationY = rawY - startRawY") &&
                !moveBody.contains("targetIndex == currentIndex) return") &&
                moveBody.contains("if (targetIndex != currentIndex)") &&
                moveBody.contains("applyProviderRowTranslations(providerId, draggedTranslationY)")
        )
    }

    @Test
    fun providerOrderRowsCancelCustomTranslationAnimatorsBeforeRebuild() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val cancelBody = activitySource.substringAfter("fun cancelProviderRowAnimations").substringBefore("private fun providerOrderRow")

        assertTrue(
            "Provider order row rebuild must cancel custom translation animators before rows are removed.",
            activitySource.contains("rowTranslationAnimators") &&
                cancelBody.contains("rowTranslationAnimators.values.forEach") &&
                cancelBody.contains("rowTranslationAnimators.clear()") &&
                cancelBody.contains("rowTranslationTargets.clear()")
        )
    }

    @Test
    fun providerOrderDropRebuildsRowsAfterCurrentInputDispatch() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val finishBody = activitySource.substringAfter("fun finishDraggingRow").substringBefore("private fun cancelDraggingRow")

        assertTrue(
            "Provider order drop should post row rebuilding so active touch dispatch and draw traversal can finish first.",
            activitySource.contains("fun postRenderProviderRows") &&
                finishBody.contains("postRenderProviderRows")
        )
    }

    @Test
    fun providerOrderDragDoesNotCancelLongPressOnSmallPreDragMovement() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readText()
        val actionMoveBody = activitySource.substringAfter("MotionEvent.ACTION_MOVE ->").substringBefore("MotionEvent.ACTION_UP")

        assertTrue(
            "The reorder handle should still enter drag after long press even if the finger shifts slightly before the timeout.",
            !actionMoveBody.contains("removeCallbacks") &&
                !actionMoveBody.contains("touchSlop")
        )
    }

    @Test
    fun dashboardDragUsesDerivedOffsetInsteadOfMutatingOffsetOnLayoutChanges() {
        val dashboardSource = File("src/main/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardScreen.kt").readText()
        val positionedBody = dashboardSource.substringAfter(".onGloballyPositioned {").substringBefore(".zIndex")

        assertTrue(
            "Dashboard drag offset should be derived from the drag origin and current base slot instead of mutating offset during layout callbacks.",
            dashboardSource.contains("dragOriginBounds") &&
                dashboardSource.contains("onDragOverlayChanged(dragOriginBounds, dragOffsetX, dragOffsetY)") &&
                !positionedBody.contains("dragOffsetX +=") &&
                !positionedBody.contains("dragOffsetY +=")
        )
    }

    @Test
    fun providerOrderConfigurationCentersContentVerticallyWhenViewportHasExtraSpace() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/DashboardWidgetConfigureActivity.kt").readNormalizedText()
        val rootBlock = activitySource.substringAfter("val root = LinearLayout(this).apply").substringBefore("root.addView(")
        val scrollBlock = activitySource.substringAfter("ScrollView(this).apply").substringAfter("addView(")

        assertTrue(
            "Provider order content should be vertically centered when the screen is taller than the configured rows.",
            rootBlock.contains("gravity = Gravity.CENTER_VERTICAL")
        )
        assertTrue(
            "Provider order root should fill the ScrollView viewport so vertical centering has real space to use.",
            activitySource.contains("isFillViewport = true") &&
                scrollBlock.contains("ViewGroup.LayoutParams.MATCH_PARENT,\n                        ViewGroup.LayoutParams.MATCH_PARENT")
        )
    }

    @Test
    fun widgetDisplayCacheUsesAsyncApplyToAvoidBlockingSurfaceRefresh() {
        val cacheSource = File("src/main/java/com/aiquota/mobile/widget/WidgetSnapshotCache.kt").readText()
        val body = cacheSource.substringAfter("fun writeLocalDisplaySnapshot").substringBefore("fun read")

        assertTrue(
            "Display cache should avoid synchronous disk writes on surface refresh.",
            body.contains(".apply()") && !body.contains(".commit()")
        )
    }

    private fun appWidgetConfigureActivity(path: String): String {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(path))
        return document.documentElement.getAttribute("android:configure")
    }

    private fun appWidgetFeatures(path: String): String {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(path))
        return document.documentElement.getAttribute("android:widgetFeatures")
    }
}

private fun File.readNormalizedText(): String = readText().replace("\r\n", "\n")
