import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();

function source(path) {
  return readFileSync(join(root, path), "utf8");
}

test("iOS app has a real sync API client and typed UI state model", () => {
  const requiredPaths = [
    "ios/AIUsageMobile/AIUsageAPIClient.swift",
    "ios/AIUsageMobile/PairingCodeViewModel.swift",
    "ios/AIUsageMobile/SnapshotStatus.swift"
  ];

  for (const path of requiredPaths) {
    assert.equal(existsSync(join(root, path)), true, `${path} should exist`);
  }

  const api = source("ios/AIUsageMobile/AIUsageAPIClient.swift");
  const pairing = source("ios/AIUsageMobile/PairingCodeViewModel.swift");
  const status = source("ios/AIUsageMobile/SnapshotStatus.swift");

  assert.match(api, /func createPairingCode\(idToken:/);
  assert.match(api, /func fetchLatestSnapshot\(uid: String, deviceId: String, idToken: String\)/);
  assert.match(api, /URLSession\.shared\.data/);
  assert.match(pairing, /enum PairingCodeState/);
  assert.match(pairing, /Expires in/);
  assert.match(status, /case fresh/);
  assert.match(status, /case stale/);
  assert.match(status, /case offline/);
  assert.match(status, /15 \* 60/);
  assert.match(status, /24 \* 60 \* 60/);
});

test("Android app has device-list snapshot sync and non-placeholder widget cache", () => {
  const requiredPaths = [
    "android/app/src/main/java/com/aiusage/mobile/sync/Models.kt",
    "android/app/src/main/java/com/aiusage/mobile/sync/SnapshotStatus.kt"
  ];

  for (const path of requiredPaths) {
    assert.equal(existsSync(join(root, path)), true, `${path} should exist`);
  }

  const models = source("android/app/src/main/java/com/aiusage/mobile/sync/Models.kt");
  const status = source("android/app/src/main/java/com/aiusage/mobile/sync/SnapshotStatus.kt");
  const repo = source("android/app/src/main/java/com/aiusage/mobile/sync/SnapshotRepository.kt");
  const worker = source("android/app/src/main/java/com/aiusage/mobile/sync/SnapshotSyncWorker.kt");
  const cache = source("android/app/src/main/java/com/aiusage/mobile/widget/WidgetSnapshotCache.kt");
  const notificationController = source("android/app/src/main/java/com/aiusage/mobile/notification/UsageLimitNotificationController.kt");
  const manifest = source("android/app/src/main/AndroidManifest.xml");
  const foregroundController = source("android/app/src/main/java/com/aiusage/mobile/sync/ForegroundRefreshController.kt");
  const foregroundService = source("android/app/src/main/java/com/aiusage/mobile/sync/ForegroundRefreshService.kt");
  const compactNotificationLayout = source("android/app/src/main/res/layout/notification_usage_compact.xml");
  const widget = source("android/app/src/main/java/com/aiusage/mobile/widget/AIUsageGlanceWidget.kt");
  const widgetLayout = source("android/app/src/main/java/com/aiusage/mobile/widget/WidgetGaugeLayout.kt");
  const widgetParser = source("android/app/src/main/java/com/aiusage/mobile/widget/WidgetGaugeParser.kt");
  const widgetXml = source("android/app/src/main/res/xml/ai_usage_widget.xml");
  const largeWidgetXml = source("android/app/src/main/res/xml/ai_usage_widget_large.xml");
  const colors = source("android/app/src/main/res/values/colors.xml");
  const nightColors = source("android/app/src/main/res/values-night/colors.xml");

  assert.match(models, /data class SnapshotRefreshResult/);
  assert.match(models, /data class SnapshotDevice/);
  assert.match(models, /data class SnapshotProviderUsage/);
  assert.match(models, /data class SnapshotUsageLimitLine/);
  assert.match(models, /remainingText/);
  assert.match(models, /remainingRatio/);
  assert.match(status, /Fresh/);
  assert.match(status, /Stale/);
  assert.match(status, /Offline/);
  assert.match(status, /15 \* 60/);
  assert.match(status, /24 \* 60 \* 60/);
  assert.match(repo, /FirebaseFirestore/);
  assert.match(repo, /collection\("users"\)/);
  assert.match(repo, /collection\("devices"\)/);
  assert.match(repo, /collection\("snapshots"\)/);
  assert.match(repo, /resolveSnapshotStatus/);
  assert.match(repo, /saveForWidget/);
  assert.match(repo, /updateDeviceName/);
  assert.match(repo, /listDevices/);
  assert.match(repo, /rememberSignedInUser/);
  assert.match(repo, /scheduleWidgetRefresh/);
  assert.match(repo, /5, TimeUnit\.MINUTES/);
  assert.match(repo, /ExistingWorkPolicy\.REPLACE/);
  assert.doesNotMatch(repo, /ExistingWorkPolicy\.APPEND_OR_REPLACE/);
  assert.match(worker, /refreshLatestSnapshot/);
  assert.match(worker, /inputData\.getString\("uid"\)/);
  assert.match(worker, /storedUid/);
  assert.match(worker, /scheduleWidgetRefresh/);
  assert.doesNotMatch(worker, /write\("\{}"\)/);
  assert.match(cache, /updatedAt/);
  assert.match(cache, /status/);
  assert.doesNotMatch(cache, /"\{}"/);
  assert.match(notificationController, /getBoolean\(KEY_ENABLED,\s*true\)/);
  assert.match(notificationController, /setCustomContentView\(compactRemoteViews/);
  assert.match(notificationController, /foregroundNotification/);
  assert.match(repo, /DecimalFormat\("0\.#"/);
  assert.match(widgetParser, /DecimalFormat\("0\.#"/);
  assert.doesNotMatch(repo, /else number\.toString\(\)/);
  assert.doesNotMatch(widgetParser, /else value\.toString\(\)/);
  assert.match(manifest, /android\.permission\.FOREGROUND_SERVICE/);
  assert.match(manifest, /android\.permission\.FOREGROUND_SERVICE_DATA_SYNC/);
  assert.match(manifest, /ForegroundRefreshService/);
  assert.match(manifest, /android:foregroundServiceType="dataSync"/);
  assert.match(manifest, /AIUsageLargeGlanceWidgetReceiver/);
  assert.match(manifest, /@xml\/ai_usage_widget_large/);
  assert.equal(existsSync(join(root, "android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")), false);
  assert.equal(existsSync(join(root, "android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")), false);
  assert.match(widgetXml, /android:targetCellWidth="1"/);
  assert.match(widgetXml, /android:targetCellHeight="1"/);
  assert.match(largeWidgetXml, /android:targetCellWidth="3"/);
  assert.match(largeWidgetXml, /android:targetCellHeight="2"/);
  assert.match(widget, /widgetBackgroundColor/);
  assert.match(widget, /\.background\(widgetBackgroundColor\(\)\)/);
  assert.match(widget, /cornerRadius\(if \(isCompact\) 20\.dp else 24\.dp\)/);
  assert.match(widget, /ColorProvider\(R\.color\.widget_background\)/);
  assert.match(widget, /ColorProvider\(R\.color\.widget_caption\)/);
  assert.match(colors, /name="widget_background">#B3FFFFFF/);
  assert.match(colors, /name="widget_caption">#475569/);
  assert.match(nightColors, /name="widget_background">#B30F172A/);
  assert.match(nightColors, /name="widget_caption">#CBD5E1/);
  assert.match(widgetLayout, /gaugeWidthDp = 41/);
  assert.match(widgetLayout, /gaugeWidthDp = 42/);
  assert.match(widgetLayout, /EXPANDED_GAUGE_WIDTH_DP = 194/);
  assert.match(widget, /EXPANDED_CAPTION_REMAINING_WIDTH_DP = 66/);
  assert.match(widget, /EXPANDED_CAPTION_SPACER_WIDTH_DP = 8/);
  assert.match(widget, /Row\(\s*modifier = GlanceModifier\.width\(layoutSpec\.gaugeWidthDp\.dp\)/);
  assert.match(widget, /layoutSpec\.gaugeWidthDp - EXPANDED_CAPTION_REMAINING_WIDTH_DP - EXPANDED_CAPTION_SPACER_WIDTH_DP/);
  assert.doesNotMatch(widgetLayout, /gaugeWidthDp = 51/);
  assert.doesNotMatch(widgetLayout, /gaugeWidthDp = 53/);
  assert.doesNotMatch(widgetLayout, /gaugeWidthDp = 220/);
  assert.match(widget, /AIUsageLargeGlanceWidget/);
  assert.match(foregroundController, /KEY_PRECISE_REFRESH_ENABLED/);
  assert.match(foregroundController, /KEY_PRECISE_REFRESH_PROMPT_SEEN/);
  assert.match(foregroundController, /startForegroundService/);
  assert.match(foregroundController, /stopService/);
  assert.match(foregroundService, /class ForegroundRefreshService/);
  assert.match(foregroundService, /startForeground/);
  assert.match(foregroundService, /delay\(60_000\)/);
  assert.match(foregroundService, /refreshLatestSnapshot/);
  assert.match(compactNotificationLayout, /notification_compact_summary/);
  assert.match(compactNotificationLayout, /android:maxLines="2"/);
  assert.doesNotMatch(compactNotificationLayout, /notification_compact_title/);
  assert.doesNotMatch(compactNotificationLayout, /notification_compact_icon/);
  assert.match(compactNotificationLayout, /android:textSize="14sp"/);
});

test("Android main UI uses Firebase auth with device list, rename flow, and snapshot refresh", () => {
  const main = source("android/app/src/main/java/com/aiusage/mobile/MainActivity.kt");
  const manifest = source("android/app/src/main/AndroidManifest.xml");
  const styles = source("android/app/src/main/res/values/styles.xml");
  const strings = source("android/app/src/main/res/values/strings.xml");
  const koreanStrings = source("android/app/src/main/res/values-ko/strings.xml");
  const gradle = source("android/app/build.gradle.kts");
  const accountDeletion = source("docs/account-deletion.md");
  const privacyPolicy = source("docs/privacy-policy.md");

  assert.match(manifest, /android:theme="@style\/Theme\.AIUsage"/);
  assert.match(manifest, /uses-permission android:name="android\.permission\.INTERNET"/);
  assert.match(styles, /windowActionBar">false/);
  assert.match(styles, /windowNoTitle">true/);
  assert.match(main, /Continue with Google/);
  assert.match(main, /LoginScreen/);
  assert.match(main, /painterResource\(R\.mipmap\.ic_launcher_foreground\)/);
  assert.match(main, /painterResource\(R\.drawable\.ic_google_g\)/);
  assert.match(main, /GoogleButtonSurface/);
  assert.match(main, /contentDescription = "AI Usage icon"/);
  assert.match(main, /contentDescription = "Google"/);
  assert.match(main, /FirebaseAuth/);
  assert.match(main, /GoogleSignInOptions/);
  assert.match(main, /GoogleAuthProvider/);
  assert.match(main, /auth\.currentUser/);
  assert.match(main, /refreshLatestSnapshot/);
  assert.match(main, /deviceList/);
  assert.match(main, /LimitDashboard/);
  assert.match(main, /ProviderLimitCard/);
  assert.match(main, /ProviderLimitLine/);
  assert.match(main, /AppHeader/);
  assert.match(main, /AppHeader\(showSettings = showSettings, onToggleSettings = onToggleSettings\)/);
  assert.doesNotMatch(main, /AppHeader\([^)]*snapshotResult/);
  assert.doesNotMatch(main, /AppHeader\([^)]*deviceList/);
  assert.match(main, /HeaderTopOffset/);
  assert.match(main, /WindowsAppBackground/);
  assert.match(main, /BrandPurple/);
  assert.match(main, /PlanPill/);
  assert.match(main, /BorderStroke\(1\.dp, DividerColor\)/);
  assert.match(main, /SettingsPanel/);
  assert.match(main, /stringResource\(R\.string\.settings_rename_selected_device\)/);
  assert.match(main, /stringResource\(R\.string\.settings_save_device_name\)/);
  assert.match(main, /stringResource\(R\.string\.settings_selected_device\)/);
  assert.match(main, /stringResource\(R\.string\.settings_connected_devices\)/);
  assert.match(main, /Usage Limits/);
  assert.match(main, /stringResource\(R\.string\.settings_refreshing_snapshot\)/);
  assert.match(main, /stringResource\(R\.string\.settings_snapshot_status\)/);
  assert.match(main, /stringResource\(R\.string\.settings_no_pc_linked\)/);
  assert.match(main, /remainingRatio/);
  assert.match(main, /delay\(60_000\)/);
  assert.match(main, /isActive/);
  assert.match(main, /rememberSignedInUser/);
  assert.match(main, /scheduleWidgetRefresh/);
  assert.match(main, /Signing in\.\.\./);
  assert.match(main, /stringResource\(R\.string\.settings_sign_out\)/);
  assert.match(strings, /name="settings_connected_devices">Connected devices/);
  assert.match(koreanStrings, /name="settings_connected_devices">연결된 장치/);
  assert.match(main, /ForegroundRefreshController/);
  assert.match(main, /preciseRefreshEnabled/);
  assert.match(main, /preciseRefreshPromptSeen/);
  assert.match(main, /settings_precise_refresh/);
  assert.match(main, /precise_refresh_prompt_title/);
  assert.match(main, /stopPreciseRefresh/);
  assert.match(main, /Intent\.ACTION_SENDTO/);
  assert.match(main, /settings_request_account_deletion/);
  assert.match(strings, /name="settings_precise_refresh">1-minute pinned refresh/);
  assert.match(strings, /name="precise_refresh_prompt_title">Keep widgets closer to real time\?/);
  assert.match(strings, /name="support_email">datell1357@naver\.com/);
  assert.match(strings, /name="settings_request_account_deletion">Request account deletion/);
  assert.match(koreanStrings, /name="settings_precise_refresh">1분 고정 갱신/);
  assert.match(koreanStrings, /name="precise_refresh_prompt_title">위젯을 더 실시간에 가깝게 유지할까요\?/);
  assert.match(koreanStrings, /name="settings_request_account_deletion">계정 삭제 요청/);
  assert.match(accountDeletion, /datell1357@naver\.com/);
  assert.match(accountDeletion, /Request account deletion/);
  assert.match(privacyPolicy, /datell1357@naver\.com/);
  assert.match(main, /providers\.forEach/);
  assert.doesNotMatch(main, /Text\(\s*"Windows PC"/);
  assert.doesNotMatch(main, /Continue with GitHub/);
  assert.doesNotMatch(main, /OAuthProvider\.newBuilder\("github\.com"\)/);
  assert.doesNotMatch(main, /signedIn = true/);
  assert.doesNotMatch(main, /482 193/);
  assert.doesNotMatch(main, /Generate PC Link Code/);
  assert.doesNotMatch(main, /Latest Snapshot/);
  assert.doesNotMatch(main, /Save sample snapshot/);
  assert.match(gradle, /play-services-auth/);
  assert.match(gradle, /lifecycle-runtime-ktx/);
  assert.match(gradle, /kotlinx-coroutines-play-services/);
  assert.match(gradle, /buildConfig = true/);
});

test("iOS main UI exposes pre-production mobile flow up to snapshot display", () => {
  const content = source("ios/AIUsageMobile/ContentView.swift");
  const store = source("ios/AIUsageMobile/SnapshotStore.swift");

  assert.match(content, /Continue with Google/);
  assert.match(content, /Continue with GitHub/);
  assert.match(content, /No PC linked/);
  assert.match(content, /Generate PC Link Code/);
  assert.match(content, /Save sample snapshot/);
  assert.match(store, /Codex/);
  assert.match(store, /Claude/);
});
