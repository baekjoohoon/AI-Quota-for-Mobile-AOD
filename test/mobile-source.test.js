import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();

function source(path) {
  return readFileSync(join(root, path), "utf8");
}

function assertPathsExist(paths) {
  for (const path of paths) {
    assert.equal(existsSync(join(root, path)), true, `${path} should exist`);
  }
}

test("iOS app has a real sync API client and typed UI state model", () => {
  const requiredPaths = [
    "ios/AIQuotaMobile/AIQuotaAPIClient.swift",
    "ios/AIQuotaMobile/PairingCodeViewModel.swift",
    "ios/AIQuotaMobile/SnapshotStatus.swift"
  ];

  assertPathsExist(requiredPaths);

  const api = source("ios/AIQuotaMobile/AIQuotaAPIClient.swift");
  const pairing = source("ios/AIQuotaMobile/PairingCodeViewModel.swift");
  const status = source("ios/AIQuotaMobile/SnapshotStatus.swift");

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

test("Android live monitoring foreground service uses specialUse usage monitor", () => {
  const manifest = source("android/app/src/main/AndroidManifest.xml");
  const refreshService = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt");

  assert.match(manifest, /android\.permission\.FOREGROUND_SERVICE_SPECIAL_USE/);
  assert.doesNotMatch(manifest, /android\.permission\.FOREGROUND_SERVICE_DATA_SYNC/);
  assert.match(manifest, /android:foregroundServiceType="specialUse"/);
  assert.match(manifest, /android\.app\.PROPERTY_SPECIAL_USE_FGS_SUBTYPE/);
  assert.match(manifest, /android:value="usage_monitor"/);
  assert.match(refreshService, /ServiceInfo\.FOREGROUND_SERVICE_TYPE_SPECIAL_USE/);
  assert.doesNotMatch(refreshService, /ServiceInfo\.FOREGROUND_SERVICE_TYPE_DATA_SYNC/);
});

test("Android local-first shell keeps provider snapshots in local display cache", () => {
  const requiredPaths = [
    "android/app/src/main/java/com/aiquota/mobile/MainActivity.kt",
    "android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt",
    "android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt",
    "android/app/src/main/java/com/aiquota/mobile/local/WidgetCacheSanitizer.kt",
    "android/app/src/main/java/com/aiquota/mobile/widget/WidgetSnapshotCache.kt",
    "android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt",
    "android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt",
    "android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshController.kt",
    "android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshPolicy.kt",
    "android/app/src/main/res/xml/ai_quota_widget_large.xml",
    "android/app/src/main/res/xml/ai_quota_widget_provider.xml"
  ];

  assertPathsExist(requiredPaths);

  const main = source("android/app/src/main/java/com/aiquota/mobile/MainActivity.kt");
  const appShell = source("android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt");
  const localRepo = source("android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt");
  const sanitizer = source("android/app/src/main/java/com/aiquota/mobile/local/WidgetCacheSanitizer.kt");
  const widgetCache = source("android/app/src/main/java/com/aiquota/mobile/widget/WidgetSnapshotCache.kt");
  const widget = source("android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt");
  const refreshService = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt");
  const foregroundController = source("android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshController.kt");
  const foregroundPolicy = source("android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshPolicy.kt");
  const manifest = source("android/app/src/main/AndroidManifest.xml");

  assert.match(main, /AIQuotaAppShell\(context = this\)/);
  assert.match(main, /requestNotificationPermissionOnFirstLaunch/);
  assert.match(main, /ForegroundRefreshController/);
  assert.match(appShell, /LocalUsageRepository\(appContext\)/);
  assert.match(appShell, /ProviderConnectorRegistry\.default\(appContext\)/);
  assert.match(localRepo, /getSharedPreferences\(PREFERENCES_NAME/);
  assert.match(localRepo, /fun readSnapshots\(\): List<ProviderUsageSnapshot>/);
  assert.match(localRepo, /fun saveSnapshot\(snapshot: ProviderUsageSnapshot\)/);
  assert.match(localRepo, /fun exportDisplayOnlyCache/);
  assert.match(localRepo, /WidgetCacheSanitizer\.toDisplayOnlyJson/);
  assert.match(localRepo, /fun removeProviderSnapshot\(providerId: ProviderId\)/);
  assert.match(localRepo, /GOOGLE_STALE_REFRESH_TIMEOUT/);
  assert.match(sanitizer, /sanitizeDisplayOnlyJson/);
  assert.match(widgetCache, /fun writeLocalDisplaySnapshot/);
  assert.match(widgetCache, /WidgetCacheSanitizer\.sanitizeDisplayOnlyJson/);
  assert.match(widgetCache, /readLocalDisplaySnapshot\(\)\.ifBlank/);
  assert.match(widget, /WidgetSnapshotCache\(context\)\.read\(\)/);
  assert.match(refreshService, /class ProviderBackgroundRefreshService : Service\(\)/);
  assert.match(refreshService, /startForeground/);
  assert.match(refreshService, /ProviderRefreshPlan\.nextAutoRefreshDelayMillis/);
  assert.match(foregroundController, /ContextCompat\.startForegroundService/);
  assert.match(foregroundPolicy, /ProviderConnectionState\.CONNECTED/);
  assert.match(foregroundPolicy, /ProviderConnectionState\.UNAVAILABLE/);
  assert.match(manifest, /android\.permission\.FOREGROUND_SERVICE_SPECIAL_USE/);
  assert.doesNotMatch(manifest, /android\.permission\.FOREGROUND_SERVICE_DATA_SYNC/);
  assert.match(manifest, /android:foregroundServiceType="specialUse"/);
  assert.match(manifest, /android\.app\.PROPERTY_SPECIAL_USE_FGS_SUBTYPE/);
  assert.match(manifest, /android:value="usage_monitor"/);
  assert.match(manifest, /ProviderBackgroundRefreshService/);
  assert.match(manifest, /AIQuotaUnifiedGlanceWidgetReceiver/);
  assert.match(manifest, /ProviderUsageGlanceWidgetReceiver/);
});

test("Android web collectors remain for visible-session providers", () => {
  const webLogin = source("android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt");
  const backgroundRefreshService = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt");
  const collectionService = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt");
  const scriptProviders = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderScriptProviders.kt");
  const definitions = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt");

  assert.match(webLogin, /settings\.javaScriptEnabled = true/);
  assert.match(webLogin, /addJavascriptInterface\(UsageBridge\(\), BRIDGE_NAME\)/);
  assert.match(webLogin, /ProviderWebCollectorScripts\.build/);
  assert.match(webLogin, /finishSuccessfulLogin\(rawPayload\)/);
  assert.match(collectionService, /class ProviderUsageCollectionService : Service\(\)/);
  assert.match(collectionService, /ProviderUsageNormalizer\.normalize\(providerId, effectivePayload, source\)/);
  assert.match(backgroundRefreshService, /ProviderWebCollectorScripts\.build/);
  assert.match(scriptProviders, /class GeminiScriptProvider/);
  assert.match(scriptProviders, /class AntigravityScriptProvider/);
  assert.match(definitions, /providerId = ProviderId\.COPILOT,[\s\S]*?collectionKind = ProviderCollectionKind\.WEBVIEW_COLLECTOR/);
});

test("Google native OAuth routes through Firebase token exchange without client secret", () => {
  const gradle = source("android/app/build.gradle.kts");
  const main = source("android/app/src/main/java/com/aiquota/mobile/MainActivity.kt");
  const debugBootstrap = source("android/app/src/debug/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt");
  const releaseBootstrap = source("android/app/src/release/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt");
  const geminiGateway = source("android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliFirebaseGateway.kt");
  const antigravityGateway = source("android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt");
  const geminiActivity = source("android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt");
  const antigravityActivity = source("android/app/src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt");
  const geminiRepository = source("android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt");
  const antigravityRepository = source("android/app/src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt");
  const definitions = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt");
  const refreshPlan = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt");
  const appShell = source("android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt");
  const refreshService = source("android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt");

  const geminiBlock = definitions
    .split("providerId = ProviderId.GEMINI,")[1]
    .split("ProviderDefinition(")[0];
  const antigravityBlock = definitions
    .split("providerId = ProviderId.ANTIGRAVITY,")[1]
    .split("ProviderDefinition(")[0];

  assert.match(gradle, /com\.google\.gms\.google-services/);
  assert.match(gradle, /com\.google\.firebase:firebase-auth/);
  assert.match(gradle, /com\.google\.firebase:firebase-functions/);
  assert.match(gradle, /com\.google\.firebase:firebase-appcheck-playintegrity/);
  assert.match(gradle, /com\.google\.firebase:firebase-appcheck-debug/);
  assert.match(main, /FirebaseGatewayBootstrap\.install\(\)/);
  assert.match(debugBootstrap, /DebugAppCheckProviderFactory/);
  assert.match(releaseBootstrap, /PlayIntegrityAppCheckProviderFactory/);
  assert.match(gradle, /681255809395-oo8ft2oprdrnp9e3aqf6av3hmdib135j\.apps\.googleusercontent\.com/);
  assert.match(gradle, /1071006060591-tmhssin2h21lcre235vtolojh4g403ep\.apps\.googleusercontent\.com/);
  assert.match(geminiGateway, /FirebaseAuth/);
  assert.match(geminiGateway, /getHttpsCallable\("startGeminiCliOAuth"\)/);
  assert.match(geminiGateway, /getHttpsCallable\("completeGeminiCliOAuth"\)/);
  assert.match(geminiGateway, /getHttpsCallable\("refreshGeminiCliAccessToken"\)/);
  assert.match(antigravityGateway, /FirebaseAuth/);
  assert.match(antigravityGateway, /getHttpsCallable\("startAntigravityOAuth"\)/);
  assert.match(antigravityGateway, /getHttpsCallable\("completeAntigravityOAuth"\)/);
  assert.match(antigravityGateway, /getHttpsCallable\("refreshAntigravityAccessToken"\)/);
  assert.match(geminiActivity, /WebView/);
  assert.match(geminiActivity, /GeminiCliFirebaseGateway\(applicationContext\)\.startOAuth\(\)/);
  assert.match(geminiActivity, /completeOAuth\(url\)/);
  assert.match(geminiActivity, /fetchUsagePayloadFromGatewayTokenResult/);
  assert.match(antigravityActivity, /WebView/);
  assert.match(antigravityActivity, /AntigravityFirebaseGateway\(applicationContext\)\.startOAuth\(\)/);
  assert.match(antigravityActivity, /completeOAuth\(url\)/);
  assert.match(antigravityActivity, /fetchUsagePayloadFromGatewayTokenResult/);
  assert.match(geminiRepository, /GeminiCliFirebaseGateway\(appContext\)\.refreshAccessToken\(refreshToken\)/);
  assert.match(antigravityRepository, /AntigravityFirebaseGateway\(appContext\)\.refreshAccessToken\(refreshToken\)/);
  assert.match(geminiBlock, /authStoreKind = ProviderAuthStoreKind\.NATIVE_TOKEN/);
  assert.match(geminiBlock, /collectionKind = ProviderCollectionKind\.NATIVE_API/);
  assert.match(antigravityBlock, /authStoreKind = ProviderAuthStoreKind\.NATIVE_TOKEN/);
  assert.match(antigravityBlock, /collectionKind = ProviderCollectionKind\.NATIVE_API/);
  assert.match(refreshPlan, /ProviderCollectionKind\.NATIVE_API -> ProviderRefreshMode\.NATIVE_API/);
  assert.match(appShell, /GeminiCliLoopbackOAuthActivity\.createIntent/);
  assert.match(appShell, /AntigravityLoopbackOAuthActivity\.createIntent/);
  assert.match(appShell, /GeminiCliOAuthRepository\(appContext\)\.fetchUsagePayloadFromStoredCredential\(\)/);
  assert.match(appShell, /AntigravityOAuthRepository\(appContext\)\.fetchUsagePayloadFromStoredCredential\(\)/);
  assert.match(refreshService, /GeminiCliOAuthRepository\(applicationContext\)\.fetchUsagePayloadFromStoredCredential\(\)/);
  assert.match(refreshService, /AntigravityOAuthRepository\(applicationContext\)\.fetchUsagePayloadFromStoredCredential\(\)/);
  assert.doesNotMatch(
    `${geminiGateway}\n${antigravityGateway}\n${geminiActivity}\n${antigravityActivity}\n${geminiRepository}\n${antigravityRepository}\n${gradle}`,
    /GEMINI_CLI_GOOGLE_OAUTH_CLIENT_SECRET|ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET|client_secret|clientSecret/
  );
});

test("Antigravity backend Functions expose Secret Manager and AES-GCM-backed gateway only", () => {
  const index = source("functions/src/index.js");
  const gateway = source("functions/src/antigravityGateway.js");
  const core = source("functions/src/core.js");
  const functionsPackage = source("functions/package.json");
  const firebase = source("firebase.json");
  const rules = source("firestore.rules");

  assert.match(index, /defineSecret\("ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_ID"\)/);
  assert.match(index, /defineSecret\("ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET"\)/);
  assert.match(index, /defineSecret\("ANTIGRAVITY_GOOGLE_OAUTH_REDIRECT_URI"\)/);
  assert.match(index, /defineSecret\("ANTIGRAVITY_TOKEN_MASTER_KEY"\)/);
  assert.match(index, /startAntigravityOAuth/);
  assert.match(index, /completeAntigravityOAuth/);
  assert.match(index, /refreshAntigravityAccessToken/);
  assert.match(index, /collectAntigravityUsage/);
  assert.match(index, /disconnectAntigravity/);
  assert.match(gateway, /encryptRefreshToken/);
  assert.match(gateway, /decryptRefreshToken/);
  assert.match(gateway, /createCipheriv\("aes-256-gcm"/);
  assert.match(gateway, /tokenEncryptionProvider/);
  assert.match(gateway, /BACKEND_API_FORBIDDEN/);
  assert.match(gateway, /access_type", "offline"/);
  assert.match(gateway, /prompt", "consent"/);
  assert.match(core, /assertNoPlaintextProviderSecret/);
  assert.doesNotMatch(functionsPackage, /@google-cloud\/kms/);
  assert.match(firebase, /"functions"/);
  assert.match(rules, /providerSecrets/);
  assert.match(rules, /antigravityOAuthStates/);
});

test("Gemini CLI OAuth token exchange uses Firebase Functions secrets", () => {
  const index = source("functions/src/index.js");
  const gateway = source("functions/src/geminiCliGateway.js");
  const androidGateway = source("android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliFirebaseGateway.kt");
  const activity = source("android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt");
  const appShell = source("android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt");
  const rules = source("firestore.rules");

  assert.match(index, /defineSecret\("GEMINI_CLI_GOOGLE_OAUTH_CLIENT_ID"\)/);
  assert.match(index, /defineSecret\("GEMINI_CLI_GOOGLE_OAUTH_CLIENT_SECRET"\)/);
  assert.match(index, /defineSecret\("GEMINI_CLI_GOOGLE_OAUTH_REDIRECT_URI"\)/);
  assert.match(index, /startGeminiCliOAuth/);
  assert.match(index, /completeGeminiCliOAuth/);
  assert.match(index, /refreshGeminiCliAccessToken/);
  assert.match(gateway, /client_secret: oauthClientSecret/);
  assert.match(gateway, /refreshGeminiCliAccessToken/);
  assert.match(gateway, /geminiCliOAuthStates/);
  assert.match(androidGateway, /getHttpsCallable\("startGeminiCliOAuth"\)/);
  assert.match(androidGateway, /getHttpsCallable\("completeGeminiCliOAuth"\)/);
  assert.match(androidGateway, /getHttpsCallable\("refreshGeminiCliAccessToken"\)/);
  assert.match(activity, /GeminiCliFirebaseGateway/);
  assert.match(activity, /completeOAuth\(url\)/);
  assert.match(appShell, /providerId == ProviderId\.GEMINI/);
  assert.doesNotMatch(`${androidGateway}\n${activity}`, /GEMINI_CLI_GOOGLE_OAUTH_CLIENT_SECRET|client_secret/);
  assert.match(rules, /geminiCliOAuthStates/);
});

test("iOS main UI exposes pre-production mobile flow up to snapshot display", () => {
  const content = source("ios/AIQuotaMobile/ContentView.swift");
  const store = source("ios/AIQuotaMobile/SnapshotStore.swift");

  assert.match(content, /Continue with Google/);
  assert.match(content, /Continue with GitHub/);
  assert.match(content, /No PC linked/);
  assert.match(content, /Generate PC Link Code/);
  assert.match(content, /Save sample snapshot/);
  assert.match(store, /Codex/);
  assert.match(store, /Claude/);
});
