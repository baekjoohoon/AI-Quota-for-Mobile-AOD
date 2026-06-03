import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();

test("mobile app contains iOS app, iOS widget, Android app, local cache, and Android Glance widget", () => {
  const requiredPaths = [
    "ios/AIQuotaMobile/AIQuotaMobileApp.swift",
    "ios/AIQuotaMobile/SnapshotCache.swift",
    "ios/AIQuotaWidget/AIQuotaWidget.swift",
    "android/app/src/main/java/com/aiquota/mobile/MainActivity.kt",
    "android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt",
    "android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt"
  ];

  for (const path of requiredPaths) {
    assert.equal(existsSync(join(root, path)), true, `${path} should exist`);
  }
});

test("mobile app documents widget cache behavior and keeps OAuth client secrets off the client", () => {
  const iosCache = readFileSync(join(root, "ios/AIQuotaMobile/SnapshotCache.swift"), "utf8");
  const androidLocalRepo = readFileSync(
    join(root, "android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt"),
    "utf8"
  );
  const widget = readFileSync(
    join(root, "android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt"),
    "utf8"
  );
  const antigravityGateway = readFileSync(
    join(root, "android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt"),
    "utf8"
  );
  const geminiGateway = readFileSync(
    join(root, "android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliFirebaseGateway.kt"),
    "utf8"
  );

  assert.match(iosCache, /App Group cache/);
  assert.match(androidLocalRepo, /exportDisplayOnlyCache/);
  assert.match(widget, /local cache|cache/);
  assert.match(antigravityGateway, /FirebaseFunctions/);
  assert.match(geminiGateway, /FirebaseFunctions/);
  assert.doesNotMatch(
    `${iosCache}\n${androidLocalRepo}\n${widget}\n${antigravityGateway}\n${geminiGateway}`,
    /apiKey|client_secret|clientSecret|GOOGLE_OAUTH_CLIENT_SECRET/
  );
});
