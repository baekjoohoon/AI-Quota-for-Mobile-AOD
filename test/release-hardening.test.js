import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

function source(path) {
  return readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
}

test("WebView login debugging is disabled for debug and release builds", () => {
  const policy = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderLoginWebViewPolicy.kt");
  const loginActivity = source("android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt");

  assert.match(policy, /webContentsDebuggingEnabled: Boolean = false/);
  assert.match(policy, /fun capabilities\(\s*isDebugBuild: Boolean = false\s*\)/);
  assert.match(policy, /webContentsDebuggingEnabled = false/);
  assert.doesNotMatch(policy, /BuildConfig\.DEBUG/);
  assert.doesNotMatch(policy, /webContentsDebuggingEnabled: Boolean = true/);
  assert.match(loginActivity, /if \(capabilities\.webContentsDebuggingEnabled\)/);
});

test("Gemini and Antigravity callable gateways enforce Firebase App Check", () => {
  const index = source("functions/src/index.js");

  assert.match(index, /createAntigravityGatewayHandlers\(\{[\s\S]*?enforceAppCheck: true/);
  assert.match(index, /createGeminiCliGatewayHandlers\(\{[\s\S]*?enforceAppCheck: true/);
  assert.doesNotMatch(index, /enforceAppCheck: false/);
});

test("WebViews with JavaScript collectors disable file access", () => {
  const loginPolicy = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderLoginWebViewPolicy.kt");
  const backgroundService = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt");
  const composeCollector = source("android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt");

  assert.match(loginPolicy, /allowFileAccess: Boolean = false/);
  assert.doesNotMatch(backgroundService, /settings\.allowFileAccess = true/);
  assert.match(backgroundService, /settings\.allowFileAccess = false/);
  assert.doesNotMatch(composeCollector, /settings\.allowFileAccess = true/);
  assert.match(composeCollector, /settings\.allowFileAccess = false/);
});

test("provider WebView console logs do not include raw page messages or source urls", () => {
  const loginActivity = source("android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt");
  const composeCollector = source("android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt");

  assert.doesNotMatch(loginActivity, /consoleMessage\.message\(\)/);
  assert.doesNotMatch(loginActivity, /consoleMessage\.sourceId\(\)/);
  assert.doesNotMatch(composeCollector, /consoleMessage\.message\(\)/);
  assert.doesNotMatch(composeCollector, /consoleMessage\.sourceId\(\)/);
});

test("only the live background refresh service declares specialUse usage monitor foreground service", () => {
  const manifest = source("android/app/src/main/AndroidManifest.xml");
  const providerUsageCollectionService = manifest.match(
    /<service[\s\S]*?android:name="\.providers\.ProviderUsageCollectionService"[\s\S]*?\/>/
  )?.[0] ?? "";
  const backgroundRefreshService = manifest.match(
    /<service[\s\S]*?android:name="\.providers\.ProviderBackgroundRefreshService"[\s\S]*?(?:<\/service>|\/>)/
  )?.[0] ?? "";

  assert.match(manifest, /android\.permission\.FOREGROUND_SERVICE_SPECIAL_USE/);
  assert.doesNotMatch(manifest, /android\.permission\.FOREGROUND_SERVICE_DATA_SYNC/);
  assert.doesNotMatch(providerUsageCollectionService, /foregroundServiceType/);
  assert.match(backgroundRefreshService, /android:foregroundServiceType="specialUse"/);
  assert.match(backgroundRefreshService, /android\.app\.PROPERTY_SPECIAL_USE_FGS_SUBTYPE/);
  assert.match(backgroundRefreshService, /android:value="usage_monitor"/);
});

test("foreground service Play declaration documents specialUse usage monitor", () => {
  const declaration = source("docs/store/foreground-service-declaration.md");

  assert.match(declaration, /specialUse/);
  assert.match(declaration, /usage_monitor/);
  assert.match(declaration, /PROPERTY_SPECIAL_USE_FGS_SUBTYPE/);
  assert.match(declaration, /고정 알림/);
  assert.match(declaration, /라이브 모니터링/);
  assert.match(declaration, /보장하지/);
  assert.doesNotMatch(declaration, /Foreground service type\s+`dataSync`/);
  assert.doesNotMatch(declaration, /알림에는 중지 action이 있습니다/);
});

test("live refresh in-app copy avoids exact refresh guarantees", () => {
  const strings = source("android/app/src/main/res/values/strings.xml");
  const koreanStrings = source("android/app/src/main/res/values-ko/strings.xml");

  assert.match(strings, /tries to refresh connected provider usage/);
  assert.match(strings, /Notification permission is required to use live monitoring/);
  assert.match(strings, /<string name="live_refresh_prompt_enable">Allow notifications<\/string>/);
  assert.doesNotMatch(strings, /60-second|every 60 seconds/);
  assert.match(koreanStrings, /갱신을 시도/);
  assert.match(koreanStrings, /라이브 모니터링을 이용하기 위해서는 알림 권한이 필요합니다\.\\n라이브 모니터링을 위해 알림 권한을 허용해주세요\./);
  assert.match(koreanStrings, /<string name="live_refresh_prompt_enable">알림 권한 허용<\/string>/);
  assert.doesNotMatch(koreanStrings, /60초마다|60초 라이브 위젯|상태 표시줄 알람/);
});

test("widget configure screens use edge-to-edge instead of deprecated system bar color APIs", () => {
  const style = source("android/app/src/main/java/com/aiquota/mobile/widget/WidgetConfigureStyle.kt");

  assert.match(style, /enableEdgeToEdge/);
  assert.doesNotMatch(style, /statusBarColor/);
  assert.doesNotMatch(style, /navigationBarColor/);
  assert.doesNotMatch(style, /setStatusBarColor/);
  assert.doesNotMatch(style, /setNavigationBarColor/);
});
