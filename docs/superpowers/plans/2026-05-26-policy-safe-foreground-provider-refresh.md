# Policy Safe Foreground Provider Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AI Quota's provider usage collection run about every 90 seconds while the user-approved pinned notification foreground service is active, without starting collection from passive widget updates or adding Play/security-sensitive shortcuts.

**Architecture:** Keep the current `ProviderBackgroundRefreshService` as the single persistent refresh loop and keep passive widgets cache-only. Strengthen the user-visible opt-in, 90-second copy, service heartbeat/restart detection, safe start sources, and redacted diagnostics so Play review can understand why the foreground service exists and how the user controls it.

**Tech Stack:** Android Kotlin, Compose, Glance widgets, `dataSync` foreground service, WebView/native provider collectors, JUnit source/unit tests, Gradle, adb/logcat QA.

---

## Baseline Decisions

- Use `dataSync`, not `specialUse`, unless Android/Play review later proves `dataSync` cannot cover provider quota fetches.
- Do not start `ProviderBackgroundRefreshService` from passive `APPWIDGET_UPDATE`.
- Widget manual refresh may start the service because it is a user action.
- App foreground/login/permission/settings paths may start the service after notification permission or explicit live monitoring consent.
- `ProviderBackgroundRefreshService` owns real collection and runs `ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS = 90_000L`.
- The notification copy must say roughly 90 seconds, not 1 minute.
- Do not add `BOOT_COMPLETED` foreground-service startup for `dataSync` while targeting Android 15. Android documents that Android 15+ apps cannot launch a `dataSync` FGS from `BOOT_COMPLETED`.
- Do not add exact alarm permission for this feature. If "alarm" appears in user-facing copy, it means the visible notification, not `AlarmManager`.
- Do not add remote arbitrary collector scripts. Provider collectors remain local and diagnostics remain redacted.
- This plan does not solve the current Gemini/Antigravity OAuth token exchange blocker by itself. Those providers still need a secret-free mobile OAuth path or backend token broker before they can be reliable in store builds.

## File Structure

- Modify: `android/app/src/test/java/com/aiquota/mobile/widget/WidgetManualRefreshActionTest.kt`
  - Keep the passive widget cache-only contract and add failure-safe expectations for post-configure refresh.
- Modify: `android/app/src/test/java/com/aiquota/mobile/sync/ForegroundRefreshControllerTest.kt`
  - Cover persisted live monitoring opt-in, start/stop actions, and restart suppression.
- Modify: `android/app/src/test/java/com/aiquota/mobile/sync/ForegroundRefreshPolicyTest.kt`
  - Cover notification permission, user opt-in, connected provider requirement, and auth-required stop behavior.
- Create: `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt`
  - Source-level tests for `dataSync`, no `specialUse`, 90-second cadence, typed `startForeground`, heartbeat updates, and redacted logs.
- Modify: `android/app/src/main/res/values/strings.xml`
  - Replace 1-minute wording with 90-second pinned live monitoring wording.
- Modify: `android/app/src/main/res/values-ko/strings.xml`
  - Same Korean copy update.
- Modify: `android/app/src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt`
  - Add a stop action to the pinned notification and make notification copy match live monitoring.
- Modify: `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshController.kt`
  - Persist user approval and expose safe start/stop/sync helpers.
- Modify: `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshPolicy.kt`
  - Require user live monitoring opt-in plus notification ability and refreshable providers.
- Create: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshStateRepository.kt`
  - Store service heartbeat, last start source, and last failure kind without sensitive provider data.
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt`
  - Use typed `dataSync` `startForeground`, update heartbeat, handle stop action, and keep failures redacted.
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshReceiver.kt`
  - Keep manual widget refresh service starts but make failures clear widget spinner and redraw cache.
- Modify: `android/app/src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureRefreshRequester.kt`
  - Treat delayed post-configure refresh as best-effort and clear spinner on FGS start failure.
- Modify: `android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt`
  - Start/sync the loop only from foreground UI when policy allows it.
- Modify: `android/app/src/main/java/com/aiquota/mobile/ui/settings/SettingsPanel.kt`
  - Show clear 90-second pinned live monitoring control and notification permission requirement.
- Modify: `android/app/src/main/java/com/aiquota/mobile/MainActivity.kt`
  - Keep notification permission flow, but start live monitoring only after user-visible approval state is true.
- Create: `docs/store/foreground-service-declaration.md`
  - Play Console declaration draft, review video checklist, and QA evidence checklist.

---

### Task 1: Lock The Foreground Refresh Contract With Tests

**Files:**
- Modify: `android/app/src/test/java/com/aiquota/mobile/widget/WidgetManualRefreshActionTest.kt`
- Modify: `android/app/src/test/java/com/aiquota/mobile/sync/ForegroundRefreshControllerTest.kt`
- Modify: `android/app/src/test/java/com/aiquota/mobile/sync/ForegroundRefreshPolicyTest.kt`
- Create: `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt`

- [ ] **Step 1: Add source-level service policy tests**

Create `ProviderBackgroundRefreshServicePolicyTest.kt`:

```kotlin
package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderBackgroundRefreshServicePolicyTest {
    @Test
    fun providerRefreshServiceUsesDataSyncForegroundServiceOnly() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertFalse(manifest.contains("FOREGROUND_SERVICE_SPECIAL_USE"))
        assertFalse(manifest.contains("specialUse"))
        assertTrue(service.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
    }

    @Test
    fun providerRefreshServiceKeepsNinetySecondCadenceAndHeartbeat() {
        val plan = File("src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(plan.contains("AUTO_REFRESH_INTERVAL_MILLIS = 90_000L"))
        assertTrue(service.contains("ProviderBackgroundRefreshStateRepository"))
        assertTrue(service.contains("recordHeartbeat"))
        assertTrue(service.contains("recordStopped"))
    }

    @Test
    fun providerRefreshServiceLogsStayRedacted() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertFalse(service.contains("CookieManager.getInstance().getCookie(url).toString()"))
        assertFalse(service.contains("observedCodexAccessToken\")"))
        assertFalse(service.contains("rawPayload)"))
        assertTrue(service.contains("provider=${'$'}{job.providerId.storageId}"))
    }
}
```

- [ ] **Step 2: Extend passive widget test expectations**

In `WidgetManualRefreshActionTest`, add:

```kotlin
@Test
fun delayedProviderWidgetConfigureRefreshClearsSpinnerWhenForegroundStartFails() {
    val requesterSource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureRefreshRequester.kt").readText()

    assertTrue(requesterSource.contains("runCatching"))
    assertTrue(requesterSource.contains("ContextCompat.startForegroundService"))
    assertTrue(requesterSource.contains("WidgetRefreshFeedback.clearWidgetRefresh"))
    assertTrue(requesterSource.contains("UsageSurfaceRefresher.refresh"))
}
```

- [ ] **Step 3: Extend controller tests for persisted approval**

In `ForegroundRefreshControllerTest`, add a fake preference-backed constructor and this test target:

```kotlin
@Test
fun controllerPersistsLiveMonitoringApprovalSeparatelyFromRuntimeStartState() {
    val starter = RecordingStarter()
    val preferences = InMemoryForegroundRefreshPreferences()
    val controller = ForegroundRefreshController(starter, preferences)

    assertEquals(false, controller.liveMonitoringEnabled())

    controller.setLiveMonitoringEnabled(true)
    controller.startPreciseRefresh()
    controller.startPreciseRefresh()

    assertEquals(true, controller.liveMonitoringEnabled())
    assertEquals(listOf(ProviderBackgroundRefreshService.ACTION_START), starter.actions)

    controller.setLiveMonitoringEnabled(false)

    assertEquals(false, controller.liveMonitoringEnabled())
    assertEquals(
        listOf(
            ProviderBackgroundRefreshService.ACTION_START,
            ProviderBackgroundRefreshService.ACTION_STOP
        ),
        starter.actions
    )
}
```

- [ ] **Step 4: Extend refresh policy tests**

In `ForegroundRefreshPolicyTest`, update `shouldRunForegroundLoop` calls to include `liveMonitoringEnabled` and add:

```kotlin
@Test
fun foregroundLoopRequiresLiveMonitoringApproval() {
    val snapshots = listOf(snapshot(ProviderId.CLAUDE, ProviderConnectionState.CONNECTED))

    assertEquals(
        false,
        ForegroundRefreshPolicy.shouldRunForegroundLoop(
            snapshots = snapshots,
            liveMonitoringEnabled = false,
            canPostNotifications = true
        )
    )
    assertEquals(
        true,
        ForegroundRefreshPolicy.shouldRunForegroundLoop(
            snapshots = snapshots,
            liveMonitoringEnabled = true,
            canPostNotifications = true
        )
    )
}
```

- [ ] **Step 5: Run focused tests and confirm they fail**

Run:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests "com.aiquota.mobile.providers.ProviderBackgroundRefreshServicePolicyTest" --tests "com.aiquota.mobile.widget.WidgetManualRefreshActionTest" --tests "com.aiquota.mobile.sync.ForegroundRefreshControllerTest" --tests "com.aiquota.mobile.sync.ForegroundRefreshPolicyTest"
```

Expected: FAIL because the controller persistence, service heartbeat, typed `startForeground`, and delayed refresh failure handling do not exist yet.

---

### Task 2: Replace 1-Minute Copy With 90-Second Pinned Live Monitoring Copy

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-ko/strings.xml`
- Modify: `android/app/src/test/java/com/aiquota/mobile/notification/NotificationPermissionLaunchTest.kt`

- [ ] **Step 1: Update English strings**

Replace the existing precise refresh strings with:

```xml
<string name="settings_precise_refresh">90-second live widget monitoring</string>
<string name="settings_precise_refresh_description">Keeps a silent pinned notification active and refreshes connected provider usage about every 90 seconds.</string>
<string name="precise_refresh_prompt_title">Keep widget data updated every 90 seconds?</string>
<string name="precise_refresh_prompt_body">AI Quota can keep a silent pinned notification active so connected provider usage is collected about every 90 seconds. If this is off, widgets only redraw cached data and Android may delay background refresh.</string>
<string name="precise_refresh_enable">Enable live monitoring</string>
<string name="precise_refresh_standard">Use manual and cached refresh</string>
<string name="notification_stop_live_refresh">Stop live refresh</string>
```

- [ ] **Step 2: Update Korean strings**

Replace the Korean precise refresh strings with:

```xml
<string name="settings_precise_refresh">90초 라이브 위젯 모니터링</string>
<string name="settings_precise_refresh_description">조용한 상단 고정 알림을 유지하고 연결된 provider 사용량을 약 90초마다 갱신합니다.</string>
<string name="precise_refresh_prompt_title">위젯 데이터를 90초마다 갱신할까요?</string>
<string name="precise_refresh_prompt_body">AI Quota는 조용한 상단 고정 알림을 유지해 연결된 provider 사용량을 약 90초마다 수집할 수 있습니다. 끄면 위젯은 캐시만 다시 그리고 Android가 백그라운드 갱신을 지연할 수 있습니다.</string>
<string name="precise_refresh_enable">라이브 모니터링 켜기</string>
<string name="precise_refresh_standard">수동 및 캐시 갱신 사용</string>
<string name="notification_stop_live_refresh">라이브 갱신 중지</string>
```

- [ ] **Step 3: Add string assertions**

Add to `NotificationPermissionLaunchTest`:

```kotlin
@Test
fun liveMonitoringCopyMentionsNinetySecondsAndPinnedNotification() {
    val english = File("src/main/res/values/strings.xml").readText()
    val korean = File("src/main/res/values-ko/strings.xml").readText()

    assertTrue(english.contains("90-second live widget monitoring"))
    assertTrue(english.contains("about every 90 seconds"))
    assertTrue(english.contains("silent pinned notification"))
    assertTrue(korean.contains("90초 라이브 위젯 모니터링"))
    assertTrue(korean.contains("상단 고정 알림"))
    assertTrue(korean.contains("약 90초마다"))
}
```

- [ ] **Step 4: Run focused notification tests**

Run:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests "com.aiquota.mobile.notification.NotificationPermissionLaunchTest"
```

Expected: PASS after string updates.

---

### Task 3: Persist User Approval In ForegroundRefreshController

**Files:**
- Modify: `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshController.kt`
- Modify: `android/app/src/test/java/com/aiquota/mobile/sync/ForegroundRefreshControllerTest.kt`

- [ ] **Step 1: Add preference abstraction for tests**

Add inside `ForegroundRefreshController.kt`:

```kotlin
internal interface ForegroundRefreshPreferences {
    fun liveMonitoringEnabled(): Boolean
    fun setLiveMonitoringEnabled(enabled: Boolean)
}
```

- [ ] **Step 2: Add Android implementation**

Add:

```kotlin
private class AndroidForegroundRefreshPreferences(
    context: Context
) : ForegroundRefreshPreferences {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun liveMonitoringEnabled(): Boolean {
        return preferences.getBoolean(KEY_LIVE_MONITORING_ENABLED, false)
    }

    override fun setLiveMonitoringEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_LIVE_MONITORING_ENABLED, enabled).apply()
    }
}
```

Add constants:

```kotlin
private const val PREFS = "ai_quota_foreground_refresh"
private const val KEY_LIVE_MONITORING_ENABLED = "live_monitoring_enabled"
```

- [ ] **Step 3: Extend controller API**

Change the constructor to keep preferences:

```kotlin
class ForegroundRefreshController {
    private val serviceStarter: ServiceStarter
    private val preferences: ForegroundRefreshPreferences
    private var preciseRefreshRequested = false

    constructor(context: Context) {
        val appContext = context.applicationContext
        serviceStarter = AndroidServiceStarter(appContext)
        preferences = AndroidForegroundRefreshPreferences(appContext)
    }

    internal constructor(
        serviceStarter: ServiceStarter,
        preferences: ForegroundRefreshPreferences
    ) {
        this.serviceStarter = serviceStarter
        this.preferences = preferences
    }

    fun liveMonitoringEnabled(): Boolean = preferences.liveMonitoringEnabled()

    fun setLiveMonitoringEnabled(enabled: Boolean) {
        preferences.setLiveMonitoringEnabled(enabled)
        if (enabled) {
            startPreciseRefresh()
        } else {
            stopPreciseRefresh()
        }
    }
}
```

- [ ] **Step 4: Update existing tests**

Update existing `ForegroundRefreshController(starter)` calls to:

```kotlin
ForegroundRefreshController(starter, InMemoryForegroundRefreshPreferences())
```

Add fake preferences:

```kotlin
private class InMemoryForegroundRefreshPreferences : ForegroundRefreshController.ForegroundRefreshPreferences {
    private var enabled = false

    override fun liveMonitoringEnabled(): Boolean = enabled

    override fun setLiveMonitoringEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
```

- [ ] **Step 5: Run focused controller tests**

Run:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests "com.aiquota.mobile.sync.ForegroundRefreshControllerTest"
```

Expected: PASS.

---

### Task 4: Require Live Monitoring Approval In ForegroundRefreshPolicy And UI

**Files:**
- Modify: `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshPolicy.kt`
- Modify: `android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt`
- Modify: `android/app/src/main/java/com/aiquota/mobile/ui/settings/SettingsPanel.kt`
- Modify: `android/app/src/main/java/com/aiquota/mobile/MainActivity.kt`
- Modify: `android/app/src/test/java/com/aiquota/mobile/sync/ForegroundRefreshPolicyTest.kt`

- [ ] **Step 1: Update policy signature**

Change:

```kotlin
fun shouldRunForegroundLoop(
    snapshots: List<ProviderUsageSnapshot>,
    notificationEnabled: Boolean,
    canPostNotifications: Boolean
): Boolean
```

to:

```kotlin
fun shouldRunForegroundLoop(
    snapshots: List<ProviderUsageSnapshot>,
    liveMonitoringEnabled: Boolean,
    canPostNotifications: Boolean
): Boolean {
    return liveMonitoringEnabled &&
        canPostNotifications &&
        connectedProviders(snapshots).isNotEmpty()
}
```

- [ ] **Step 2: Add UI state**

In `AIQuotaAppShell`, after `notificationEnabled`, add:

```kotlin
var liveMonitoringEnabled by remember {
    mutableStateOf(foregroundRefreshController.liveMonitoringEnabled())
}
```

- [ ] **Step 3: Update settings toggle handler**

Replace the service-driving part of `setNotificationEnabled` with:

```kotlin
fun setLiveMonitoringEnabled(enabled: Boolean) {
    if (!enabled) {
        foregroundRefreshController.setLiveMonitoringEnabled(false)
        liveMonitoringEnabled = false
        notificationEnabled = UsageLimitNotificationController.isEnabled(appContext) &&
            UsageLimitNotificationController.canPostNotifications(launchContext)
        return
    }

    canPostNotifications = UsageLimitNotificationController.canPostNotifications(launchContext)
    if (!canPostNotifications) {
        UsageLimitNotificationController.setEnabled(appContext, true)
        openNotificationSettings()
        return
    }

    UsageLimitNotificationController.setEnabled(appContext, true)
    UsageLimitNotificationController.updateFromCache(appContext)
    foregroundRefreshController.setLiveMonitoringEnabled(true)
    liveMonitoringEnabled = true
    notificationEnabled = true
}
```

Keep `setNotificationEnabled` only if the existing status-bar-gauge toggle remains. If it remains, make it call `setLiveMonitoringEnabled(enabled)` so there is one source of truth.

- [ ] **Step 4: Update `LaunchedEffect` service sync**

Change the policy call to:

```kotlin
ForegroundRefreshPolicy.shouldRunForegroundLoop(
    snapshots = snapshots,
    liveMonitoringEnabled = liveMonitoringEnabled,
    canPostNotifications = canPostNotifications
)
```

- [ ] **Step 5: Pass live monitoring state to settings**

Update `SettingsPanel` call in `AIQuotaAppShell`:

```kotlin
SettingsPanel(
    notificationEnabled = liveMonitoringEnabled,
    canPostNotifications = canPostNotifications,
    onNotificationEnabledChanged = ::setLiveMonitoringEnabled,
    onOpenNotificationSettings = ::openNotificationSettings,
    hiddenProviders = hiddenProviders,
    onShowProvider = { providerId -> setProviderHidden(providerId, false) },
    currentTheme = currentTheme,
    onThemeSelected = ::applyTheme
)
```

- [ ] **Step 6: MainActivity permission result should enable the user-approved state only after grant**

In `onRequestPermissionsResult`, after notification grant:

```kotlin
UsageLimitNotificationController.setEnabled(this, true)
UsageLimitNotificationController.updateFromCache(this)
ForegroundRefreshController(this).setLiveMonitoringEnabled(true)
```

If this feels too aggressive during implementation, keep this behavior only when the permission request was opened from the live monitoring prompt or settings toggle.

- [ ] **Step 7: Run policy and UI source tests**

Run:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests "com.aiquota.mobile.sync.ForegroundRefreshPolicyTest" --tests "com.aiquota.mobile.ui.AIQuotaAppShellAuthRoutingTest"
```

Expected: PASS.

---

### Task 5: Add Service Heartbeat And Typed dataSync Foreground Start

**Files:**
- Create: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshStateRepository.kt`
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt`
- Modify: `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt`

- [ ] **Step 1: Create heartbeat repository**

Create:

```kotlin
package com.aiquota.mobile.providers

import android.content.Context

class ProviderBackgroundRefreshStateRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordStarted(source: String, nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putBoolean(KEY_RUNNING, true)
            .putString(KEY_SOURCE, source)
            .putLong(KEY_STARTED_AT, nowMillis)
            .putLong(KEY_HEARTBEAT_AT, nowMillis)
            .remove(KEY_LAST_FAILURE)
            .apply()
    }

    fun recordHeartbeat(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putBoolean(KEY_RUNNING, true)
            .putLong(KEY_HEARTBEAT_AT, nowMillis)
            .apply()
    }

    fun recordFailure(kind: String, nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putString(KEY_LAST_FAILURE, kind)
            .putLong(KEY_HEARTBEAT_AT, nowMillis)
            .apply()
    }

    fun recordStopped(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putBoolean(KEY_RUNNING, false)
            .putLong(KEY_STOPPED_AT, nowMillis)
            .apply()
    }

    fun isHeartbeatStale(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val heartbeat = preferences.getLong(KEY_HEARTBEAT_AT, 0L)
        if (heartbeat <= 0L) return true
        return nowMillis - heartbeat > STALE_HEARTBEAT_MILLIS
    }

    companion object {
        private const val PREFS = "ai_quota_background_refresh_state"
        private const val KEY_RUNNING = "running"
        private const val KEY_SOURCE = "source"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_HEARTBEAT_AT = "heartbeat_at"
        private const val KEY_STOPPED_AT = "stopped_at"
        private const val KEY_LAST_FAILURE = "last_failure"
        const val STALE_HEARTBEAT_MILLIS = 180_000L
    }
}
```

- [ ] **Step 2: Use repository in service**

In `ProviderBackgroundRefreshService`, add:

```kotlin
private lateinit var refreshStateRepository: ProviderBackgroundRefreshStateRepository
```

Initialize in `onCreate`:

```kotlin
refreshStateRepository = ProviderBackgroundRefreshStateRepository(applicationContext)
```

In `startRefreshLoop()` after `startForegroundNotification()` succeeds:

```kotlin
refreshStateRepository.recordStarted(source = "loop")
```

At the beginning of `runRefreshCycle()`:

```kotlin
refreshStateRepository.recordHeartbeat()
```

In `stopRefreshLoop()`:

```kotlin
refreshStateRepository.recordStopped()
```

When a `ServiceRefreshOutcome.Failure` is processed:

```kotlin
refreshStateRepository.recordFailure(outcome.failure.kind.name)
```

- [ ] **Step 3: Use typed dataSync foreground start**

Replace `startForeground(...)` with:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(
        UsageLimitNotificationController.NOTIFICATION_ID,
        UsageLimitNotificationController.foregroundNotification(this),
        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    )
} else {
    startForeground(
        UsageLimitNotificationController.NOTIFICATION_ID,
        UsageLimitNotificationController.foregroundNotification(this)
    )
}
```

- [ ] **Step 4: Run service policy tests**

Run:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests "com.aiquota.mobile.providers.ProviderBackgroundRefreshServicePolicyTest"
```

Expected: PASS.

---

### Task 6: Add User Stop Control To The Pinned Notification

**Files:**
- Modify: `android/app/src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt`
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt`
- Modify: `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt`

- [ ] **Step 1: Add stop action pending intent**

In `UsageLimitNotificationController`, add:

```kotlin
private fun stopLiveRefreshIntent(context: Context): PendingIntent {
    val intent = Intent(context, ProviderBackgroundRefreshService::class.java)
        .setAction(ProviderBackgroundRefreshService.ACTION_STOP)
    return PendingIntent.getService(
        context,
        1002,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
```

Add import:

```kotlin
import android.content.Intent
import com.aiquota.mobile.providers.ProviderBackgroundRefreshService
```

- [ ] **Step 2: Add action to notification builder**

In `buildNotification`, before `.build()`:

```kotlin
.addAction(
    R.drawable.ic_refresh,
    context.getString(R.string.notification_stop_live_refresh),
    stopLiveRefreshIntent(context)
)
```

- [ ] **Step 3: Make stop disable live monitoring preference**

In `ProviderBackgroundRefreshService` `ACTION_STOP` branch, before `stopSelf(startId)`:

```kotlin
com.aiquota.mobile.sync.ForegroundRefreshController(applicationContext)
    .setLiveMonitoringEnabled(false)
```

Guard against recursive stop calls by making `stopPreciseRefresh()` idempotent, as it already is.

- [ ] **Step 4: Add source assertion**

In `ProviderBackgroundRefreshServicePolicyTest`, add:

```kotlin
@Test
fun pinnedNotificationIncludesUserStopAction() {
    val notification = File("src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt").readText()
    val strings = File("src/main/res/values/strings.xml").readText()

    assertTrue(notification.contains("ACTION_STOP"))
    assertTrue(notification.contains("addAction"))
    assertTrue(strings.contains("notification_stop_live_refresh"))
}
```

- [ ] **Step 5: Run notification and service tests**

Run:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests "com.aiquota.mobile.providers.ProviderBackgroundRefreshServicePolicyTest" --tests "com.aiquota.mobile.notification.NotificationSixProvidersTest"
```

Expected: PASS.

---

### Task 7: Make Manual Widget And Post-Configure Starts Failure-Safe

**Files:**
- Modify: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshReceiver.kt`
- Modify: `android/app/src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureRefreshRequester.kt`
- Modify: `android/app/src/test/java/com/aiquota/mobile/widget/WidgetManualRefreshActionTest.kt`

- [ ] **Step 1: Wrap manual widget service start**

In `ProviderRefreshReceiver.onReceive`, replace direct `ContextCompat.startForegroundService(...)` with:

```kotlin
val appContext = context.applicationContext
val appWidgetId = intent.getIntExtra(
    WidgetRefreshActions.EXTRA_APP_WIDGET_ID,
    android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
)
val serviceIntent = Intent(appContext, ProviderBackgroundRefreshService::class.java)
    .setAction(ProviderBackgroundRefreshService.ACTION_REFRESH)
    .putExtra(WidgetRefreshActions.EXTRA_APP_WIDGET_ID, appWidgetId)
    .apply {
        intent.getStringExtra(WidgetRefreshActions.EXTRA_PROVIDER_ID)
            ?.let { putExtra(WidgetRefreshActions.EXTRA_PROVIDER_ID, it) }
    }

runCatching {
    ContextCompat.startForegroundService(appContext, serviceIntent)
}.onFailure {
    WidgetRefreshFeedback.clearWidgetRefresh(appContext, appWidgetId)
    UsageSurfaceRefresher.refresh(appContext, LocalUsageRepository(appContext))
}
```

Add imports:

```kotlin
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.widget.WidgetRefreshFeedback
```

- [ ] **Step 2: Wrap delayed post-configure service start**

In `ProviderWidgetConfigureRefreshRequester.schedule`, replace direct service start with:

```kotlin
runCatching {
    ContextCompat.startForegroundService(
        appContext,
        Intent(appContext, ProviderBackgroundRefreshService::class.java)
            .setAction(ProviderBackgroundRefreshService.ACTION_REFRESH)
            .putExtra(WidgetRefreshActions.EXTRA_APP_WIDGET_ID, appWidgetId)
            .putExtra(WidgetRefreshActions.EXTRA_PROVIDER_ID, providerId.storageId)
    )
}.onFailure {
    WidgetRefreshFeedback.clearWidgetRefresh(appContext, appWidgetId)
    UsageSurfaceRefresher.refresh(appContext, LocalUsageRepository(appContext))
}
```

- [ ] **Step 3: Run widget refresh tests**

Run:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests "com.aiquota.mobile.widget.WidgetManualRefreshActionTest" --tests "com.aiquota.mobile.widget.ProviderWidgetConfigureActivityTest"
```

Expected: PASS.

---

### Task 8: Sync Service State On App Foreground And Keep Passive Widgets Cache-Only

**Files:**
- Modify: `android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt`
- Modify: `android/app/src/main/java/com/aiquota/mobile/widget/WidgetAutoRefreshStarter.kt`
- Modify: `android/app/src/test/java/com/aiquota/mobile/widget/WidgetManualRefreshActionTest.kt`

- [ ] **Step 1: Restart only from foreground UI when heartbeat is stale**

In `AIQuotaAppShell`, create:

```kotlin
val refreshStateRepository = remember(appContext) {
    ProviderBackgroundRefreshStateRepository(appContext)
}
```

In the `Lifecycle.Event.ON_RESUME` observer, after `refreshSnapshots()`:

```kotlin
canPostNotifications = UsageLimitNotificationController.canPostNotifications(launchContext)
liveMonitoringEnabled = foregroundRefreshController.liveMonitoringEnabled()
if (
    ForegroundRefreshPolicy.shouldRunForegroundLoop(
        snapshots = localUsageRepository.readSnapshots(),
        liveMonitoringEnabled = liveMonitoringEnabled,
        canPostNotifications = canPostNotifications
    ) &&
    refreshStateRepository.isHeartbeatStale()
) {
    runCatching { foregroundRefreshController.startPreciseRefresh() }
}
```

- [ ] **Step 2: Keep passive widget starter unchanged**

`WidgetAutoRefreshStarter.requestBackgroundRefresh(context)` must remain:

```kotlin
val appContext = context.applicationContext
UsageSurfaceRefresher.refresh(appContext, LocalUsageRepository(appContext))
```

Do not add `ContextCompat.startForegroundService`, `ProviderBackgroundRefreshService.ACTION_REFRESH`, `AlarmManager`, or `WorkManager` to this object.

- [ ] **Step 3: Extend passive widget test**

In `WidgetManualRefreshActionTest.systemWidgetUpdatesRefreshCachedSurfacesWithoutStartingForegroundService`, add:

```kotlin
assertTrue(!starterSource.contains("AlarmManager"))
assertTrue(!starterSource.contains("WorkManager"))
```

- [ ] **Step 4: Run focused tests**

Run:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests "com.aiquota.mobile.widget.WidgetManualRefreshActionTest" --tests "com.aiquota.mobile.sync.ForegroundRefreshPolicyTest"
```

Expected: PASS.

---

### Task 9: Keep Security Guardrails Explicit

**Files:**
- Modify: `android/app/src/test/java/com/aiquota/mobile/providers/GeminiCliOAuthRepositoryTest.kt`
- Modify: `android/app/src/test/java/com/aiquota/mobile/providers/AntigravityOAuthRepositoryTest.kt`
- Modify: `android/app/src/test/java/com/aiquota/mobile/providers/ProviderCollectorErrorPolicyTest.kt`

- [ ] **Step 1: Keep OAuth client secret out of app code**

Ensure both OAuth tests keep:

```kotlin
assertFalse(refresh.containsKey("client_secret"))
assertFalse(exchange.containsKey("client_secret"))
```

- [ ] **Step 2: Add source guard against embedded secrets**

Add to each OAuth repository test:

```kotlin
@Test
fun repositorySourceDoesNotEmbedClientSecret() {
    val source = java.io.File("src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt").readText()

    assertFalse(source.contains("CLIENT_SECRET"))
    assertFalse(source.contains("client_secret\" to"))
}
```

Use `AntigravityOAuthRepository.kt` in the Antigravity test.

- [ ] **Step 3: Keep collector diagnostics redacted**

In `ProviderCollectorErrorPolicyTest`, add:

```kotlin
@Test
fun diagnosticsDoNotRequireRawTokenCookieOrHtml() {
    val service = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

    assertFalse(service.contains("Log.d(TAG, rawPayload"))
    assertFalse(service.contains("Log.e(TAG, rawPayload"))
    assertFalse(service.contains("Log.d(TAG, cookiesFor"))
    assertFalse(service.contains("Log.e(TAG, cookiesFor"))
}
```

- [ ] **Step 4: Run provider security tests**

Run:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests "com.aiquota.mobile.providers.GeminiCliOAuthRepositoryTest" --tests "com.aiquota.mobile.providers.AntigravityOAuthRepositoryTest" --tests "com.aiquota.mobile.providers.ProviderCollectorErrorPolicyTest"
```

Expected: PASS.

---

### Task 10: Create Play Console Foreground Service Declaration Draft

**Files:**
- Create: `docs/store/foreground-service-declaration.md`

- [ ] **Step 1: Create declaration draft**

Create:

```markdown
# AI Quota Foreground Service Declaration Draft

Date: 2026-05-26

## Foreground Service Type

`dataSync`

## App Functionality

AI Quota lets the user monitor connected AI provider quota usage from home-screen widgets and a pinned silent notification. When the user enables 90-second live widget monitoring and allows Android notifications, the app keeps a foreground service active to fetch the latest provider usage on device and refresh local widget/cache surfaces about every 90 seconds.

## User Action That Starts The Feature

- User grants notification permission or enables "90-second live widget monitoring" in settings.
- User connects at least one provider.
- User may also press a widget refresh button to request an immediate provider refresh.

## User Visible Disclosure And Control

- The service is represented by a pinned silent notification.
- The settings screen explains that connected provider usage is refreshed about every 90 seconds while the pinned notification is active.
- The notification includes a stop action.
- Turning off the setting stops the foreground service.

## Impact If Deferred Or Interrupted

If the service is deferred, stopped, or Android prevents it from starting, widgets can still redraw the last trusted local cache, but provider usage may become stale until the user opens the app or presses widget refresh.

## Why Passive Widget Update Does Not Start The Service

Passive `APPWIDGET_UPDATE` only redraws cached data. It does not start `startForegroundService()` because Android can reject background foreground-service starts and kill the process.

## Data Safety Notes

- Provider sessions and tokens stay local to the device.
- Logs do not include raw tokens, cookies, OAuth codes, account identifiers, emails, usernames, or full provider HTML.
- Disconnect clears provider credentials and provider-scoped WebView session data.

## Review Video Checklist

1. Open AI Quota.
2. Grant notification permission.
3. Connect one provider.
4. Enable 90-second live widget monitoring.
5. Show the pinned silent notification.
6. Press the notification stop action and show the service stops.
7. Add a widget and show passive widget redraw uses cache.
8. Press widget refresh and show user action triggers immediate refresh.
```

- [ ] **Step 2: Add policy source references**

Append:

```markdown
## Policy References Checked

- Google Play foreground service requirements: https://support.google.com/googleplay/android-developer/answer/13392821
- Android foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Android background foreground-service start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
```

- [ ] **Step 3: Review for unsupported claims**

Check that the doc does not claim:

- exact 90-second refresh after reboot,
- passive widget background collection,
- collection without notification permission,
- provider login/session bypass,
- server-side data processing that does not exist.

---

### Task 11: Full Verification

**Files:**
- No additional source edits expected.

- [ ] **Step 1: Run unit tests**

Run:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build debug APK**

Run:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Check whitespace**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 4: Install on emulator**

Run:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb -s emulator-5554 install -r android\app\build\outputs\apk\debug\app-debug.apk
& $adb -s emulator-5554 shell monkey -p com.aiquota.mobile 1
```

Expected: APK installs and app launches.

- [ ] **Step 5: Verify passive widget update does not start FGS**

Run:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s emulator-5554 logcat -c
& $adb -s emulator-5554 shell cmd appwidget update --user 0 com.aiquota.mobile/.widget.AIQuotaCircularWidgetProvider
Start-Sleep -Seconds 5
& $adb -s emulator-5554 logcat -d | Select-String -Pattern "AIQuotaBgRefreshService|ForegroundServiceStartNotAllowedException|cycleStart"
```

Expected: no new FGS start from passive update and no `ForegroundServiceStartNotAllowedException`.

- [ ] **Step 6: Verify live monitoring foreground loop**

Manual emulator steps:

1. Open app.
2. Allow notifications.
3. Enable 90-second live widget monitoring.
4. Connect at least one provider that can collect on the emulator.
5. Press Home.
6. Wait at least 190 seconds.

Log command:

```powershell
& $adb -s emulator-5554 logcat -d | Select-String -Pattern "AIQuotaBgRefreshService|cycleStart|failedToStartForeground|ForegroundServiceStartNotAllowedException"
```

Expected:

- `cycleStart` appears at roughly 90-second intervals while service is alive.
- no raw token/cookie/html appears.
- no foreground-service start restriction crash appears.

- [ ] **Step 7: Verify notification stop action**

Manual emulator steps:

1. Open notification shade.
2. Press "Stop live refresh" or Korean equivalent.
3. Wait 100 seconds.

Expected:

- foreground notification disappears,
- no additional `cycleStart` appears after stop,
- settings switch is off after reopening the app.

- [ ] **Step 8: Verify widget manual refresh**

Manual emulator steps:

1. Add unified or provider widget.
2. Press widget refresh button.

Expected:

- spinner starts only for the pressed widget,
- service receives `ACTION_REFRESH`,
- spinner clears after success or start failure,
- passive widget redraw still uses cache only.

---

## Acceptance Criteria

- Store-visible copy says "about every 90 seconds" and "pinned notification".
- `ProviderBackgroundRefreshService` remains `dataSync`, not `specialUse`.
- No passive widget update path calls `startForegroundService()`.
- Manual widget refresh remains a user-action path and handles FGS start failure gracefully.
- Foreground loop starts only when live monitoring is enabled, notifications can be posted, and at least one provider has trusted usage lines in a refreshable state.
- The pinned notification has a stop action.
- Service heartbeat lets the app foreground path detect and restart a dead/stale loop.
- Logs and docs do not include raw provider secrets, tokens, cookies, account IDs, emails, usernames, OAuth codes, or full HTML.
- Gemini/Antigravity are not made "working" by reintroducing `client_secret` into the APK.
- `:app:testDebugUnitTest`, `:app:assembleDebug`, and `git diff --check` pass.

## Risks And Non-Goals

- If the user force-stops the app, Android will not allow the app to restart itself until the user opens it again.
- After device reboot, this plan does not start a `dataSync` foreground service from `BOOT_COMPLETED` because target Android 15+ disallows that path for data sync FGS. The next user-visible app/widget action can restart the loop.
- Android may still stop a long-running service under extreme battery or OEM restrictions. The plan detects stale heartbeat on app foreground and manual refresh, but it does not bypass OS restrictions.
- This plan does not add exact alarm permission.
- This plan does not add a backend token broker.
- This plan does not complete the separate Google OAuth migration needed to make Gemini/Antigravity secret-free and reliable in release builds.

## Self-Review

- Spec coverage: covers 90-second live collection, user-approved pinned notification, passive widget cache-only behavior, manual widget refresh, dead service detection, Play declaration docs, and redacted diagnostics.
- Placeholder scan: no `TBD`, no fake data, no hidden remote script work.
- Type consistency: plan uses existing `ProviderBackgroundRefreshService`, `ProviderRefreshPlan`, `ForegroundRefreshController`, `ForegroundRefreshPolicy`, `UsageLimitNotificationController`, `WidgetAutoRefreshStarter`, and `UsageSurfaceRefresher` names from the current codebase.
- Policy realism: avoids `specialUse`, boot-time `dataSync` FGS startup, exact alarms, and passive widget FGS starts.
