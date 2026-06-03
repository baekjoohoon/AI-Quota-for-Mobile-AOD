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

test("only the live background refresh service declares dataSync foreground service", () => {
  const manifest = source("android/app/src/main/AndroidManifest.xml");
  const providerUsageCollectionService = manifest.match(
    /<service[\s\S]*?android:name="\.providers\.ProviderUsageCollectionService"[\s\S]*?\/>/
  )?.[0] ?? "";
  const backgroundRefreshService = manifest.match(
    /<service[\s\S]*?android:name="\.providers\.ProviderBackgroundRefreshService"[\s\S]*?\/>/
  )?.[0] ?? "";

  assert.match(manifest, /android\.permission\.FOREGROUND_SERVICE_DATA_SYNC/);
  assert.doesNotMatch(providerUsageCollectionService, /foregroundServiceType/);
  assert.match(backgroundRefreshService, /android:foregroundServiceType="dataSync"/);
});

test("widget configure screens use edge-to-edge instead of deprecated system bar color APIs", () => {
  const style = source("android/app/src/main/java/com/aiquota/mobile/widget/WidgetConfigureStyle.kt");

  assert.match(style, /enableEdgeToEdge/);
  assert.doesNotMatch(style, /statusBarColor/);
  assert.doesNotMatch(style, /navigationBarColor/);
  assert.doesNotMatch(style, /setStatusBarColor/);
  assert.doesNotMatch(style, /setNavigationBarColor/);
});
