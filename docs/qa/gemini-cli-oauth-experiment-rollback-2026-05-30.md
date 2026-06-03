# Gemini CLI OAuth Experiment Rollback - 2026-05-30

This document records the pre-experiment state before testing a Gemini CLI / Code Assist style OAuth flow with a separate Google Cloud project/client.

## Scope

- Goal: test whether our own Google OAuth client can reproduce the Store APK style Gemini CLI / Code Assist usage flow.
- Existing production-ish behavior must remain recoverable.
- Do not store OAuth client secrets, refresh tokens, access tokens, callback codes, cookies, or private provider payloads in this document.
- Session rule: do not use lean-ctx for this work unless the user explicitly changes that rule.

## Pre-Experiment Snapshot

Recorded at: 2026-05-30 16:58 KST

Current Firebase project:

- `ai-usage-for-mobile`

Current deployed Functions that may depend on OAuth/secret state:

- `startAntigravityOAuth`
- `antigravityOAuthCallback`
- `collectAntigravityUsage`
- `disconnectAntigravity`
- `createPairingCode`
- `consumePairingCode`
- `uploadLatestSnapshot`

Current Antigravity gateway OAuth config:

- `ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_ID`: `248131081430-n60schpofi2r66htik2bmr1e4ao1t9df.apps.googleusercontent.com`
- `ANTIGRAVITY_GOOGLE_OAUTH_REDIRECT_URI`: `https://us-central1-ai-usage-for-mobile.cloudfunctions.net/antigravityOAuthCallback`
- `ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET`: version `1`, `ENABLED` at backup time. Secret value intentionally not recorded.
- `ANTIGRAVITY_TOKEN_MASTER_KEY`: version `1`, `ENABLED` at backup time. Secret value intentionally not recorded.

Current Android local OAuth config:

- `android/local.properties` has `aiquota.googleAndroidOAuthClientId=248131081430-auhc3tbm85e59sfd6epef5foimranu82.apps.googleusercontent.com`
- `android/app/google-services.json` includes Android OAuth clients for package `com.aiquota.mobile`:
  - `248131081430-auhc3tbm85e59sfd6epef5foimranu82.apps.googleusercontent.com`
  - `248131081430-cv98alpp8au3ka4f9i333drbu3ajhoh2.apps.googleusercontent.com`

## Local File Backups

Backup directory:

```powershell
D:\Vibe Project\AI Usage for Mobile\.tmp\backups\2026-05-30-gemini-cli-oauth-experiment-preflight
```

Files copied before any Gemini OAuth experiment changes:

- `android/local.properties`
- `android/app/google-services.json`
- `android/app/build.gradle.kts`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/GoogleAuthorizationActivity.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/GoogleAppAuthUsageAuthorizer.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/GoogleIdentityUsageAuthorizer.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt`
- `android/app/src/main/assets/gemini_collector.js`
- `functions/src/index.js`
- `functions/src/antigravityGateway.js`
- `functions/package.json`
- `firebase.json`
- `firestore.rules`

## Secret Rollback

Firebase CLI can read a specific secret version with `KEY@version`.

If an experiment overwrites one of the existing Antigravity secrets, restore version `1` like this:

```powershell
firebase functions:secrets:access ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_ID@1 --project ai-usage-for-mobile | firebase functions:secrets:set ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_ID --project ai-usage-for-mobile --data-file -
firebase functions:secrets:access ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET@1 --project ai-usage-for-mobile | firebase functions:secrets:set ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET --project ai-usage-for-mobile --data-file -
firebase functions:secrets:access ANTIGRAVITY_GOOGLE_OAUTH_REDIRECT_URI@1 --project ai-usage-for-mobile | firebase functions:secrets:set ANTIGRAVITY_GOOGLE_OAUTH_REDIRECT_URI --project ai-usage-for-mobile --data-file -
firebase functions:secrets:access ANTIGRAVITY_TOKEN_MASTER_KEY@1 --project ai-usage-for-mobile | firebase functions:secrets:set ANTIGRAVITY_TOKEN_MASTER_KEY --project ai-usage-for-mobile --data-file -
```

Then redeploy Functions:

```powershell
firebase deploy --only functions --project ai-usage-for-mobile
```

## Local File Rollback

Restore backed-up files:

```powershell
$backupRoot = 'D:\Vibe Project\AI Usage for Mobile\.tmp\backups\2026-05-30-gemini-cli-oauth-experiment-preflight'
$repoRoot = 'D:\Vibe Project\AI Usage for Mobile'
Get-ChildItem -LiteralPath $backupRoot -Recurse -File | ForEach-Object {
  $relative = $_.FullName.Substring($backupRoot.Length + 1)
  $target = Join-Path $repoRoot $relative
  New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
  Copy-Item -LiteralPath $_.FullName -Destination $target -Force
}
```

After restoring, rebuild/reinstall the Android app if the experiment changed Android config:

```powershell
cd 'D:\Vibe Project\AI Usage for Mobile\android'
.\gradlew.bat :app:assembleDebug
```

## Preferred Experiment Guardrails

- Do not overwrite the current Antigravity gateway secrets unless explicitly needed.
- Prefer a separate test Google Cloud project and a separate OAuth client.
- Prefer separate test config names/files for Gemini CLI probing rather than reusing Antigravity secret names.
- Treat `403 PERMISSION_DENIED`, `CONSUMER_INVALID`, or `SERVICE_DISABLED` as a backend authorization result, not a login failure.
- Keep diagnostics redacted: no tokens, cookies, auth headers, OAuth codes, emails, usernames, or full provider HTML in logs/docs.

## Current Rollback Status

- Local file backups: complete.
- Secret values: not copied to disk; current version `1` remains enabled and recoverable through Firebase CLI.
- Experiment changes: applied after this preflight section; see below.

## Experiment Resources Created

Created in Google Cloud Console using the secondary browser account:

- Test project display name: `My First Project`
- Test project ID: `project-2ecfb94e-45de-483f-bb7`
- Test project number: `957428666864`
- OAuth app name: `AI Quota Test`
- OAuth user type: external, testing
- Test user: added in Console; do not record the email address in repo docs.
- OAuth client type: desktop app
- OAuth client name shown by Console: `데스크톱 클라이언트 1`
- OAuth client ID: `957428666864-tqnqupcmmh0jk9jv5f1b42fgjduvfuk3.apps.googleusercontent.com`

No client secret was written into repo docs. The Android experiment uses PKCE and the loopback redirect URI below:

```text
http://127.0.0.1:46417/oauth2callback
```

## Experiment Code Changes

The experiment is intentionally gated by a local property so the existing Antigravity gateway and existing Android Google OAuth client are not overwritten.

Added local property:

```properties
aiquota.geminiCliOAuthClientId=957428666864-tqnqupcmmh0jk9jv5f1b42fgjduvfuk3.apps.googleusercontent.com
```

Changed behavior:

- `android/app/build.gradle.kts` exposes `BuildConfig.GEMINI_CLI_OAUTH_CLIENT_ID`.
- `GeminiCliOAuthRepository` can build a Gemini CLI / Code Assist style Google OAuth URL with:
  - `client_id`
  - `redirect_uri=http://127.0.0.1:46417/oauth2callback`
  - `response_type=code`
  - `scope=cloud-platform userinfo.email userinfo.profile`
  - `access_type=offline`
  - `prompt=consent`
  - PKCE `code_challenge`
- `AIQuotaAppShell` routes Gemini connect to `GeminiCliLoopbackOAuthActivity` when `aiquota.geminiCliOAuthClientId` is present.
- `GeminiCliLoopbackOAuthActivity` starts a localhost callback listener, launches Google OAuth in the external browser, accepts the loopback callback, exchanges the code, stores returned tokens locally, then probes Gemini Code Assist quota via the existing `GeminiCliOAuthRepository` Code Assist calls.
- `WebLoginActivity` is no longer used to load the Gemini CLI OAuth URL because Google OAuth can show an embedded-user-agent access block inside Android WebView.
- The loopback callback page returns immediately in Chrome. The app defers token exchange and Code Assist calls until AI Quota returns to the foreground because the emulator can block app network while Chrome is the focused foreground app.

## Current Test Notes

- The APK installed on the emulator now opens Google OAuth in Chrome instead of Android WebView for the Gemini CLI OAuth experiment.
- Because the OAuth app is still in `Testing`, every Google account used on the emulator/device must be added under Google Auth Platform > Audience > Test users before Google login can proceed.
- Do not record test-user email addresses in this document.
- 2026-05-30 test result: Google OAuth reaches the localhost callback and returns to AI Quota successfully. Token exchange then fails with HTTP 400 `invalid_request` because the current Desktop OAuth client requires `client_secret` even with PKCE. The secret was not added to the APK.

## 2026-05-30 Cloud Project Reset

- Old temporary project `project-2ecfb94e-45de-483f-bb7` was shut down in Google Cloud Console. Console reported it is scheduled for deletion after `2026-06-29`.
- New Google Cloud project was created under the secondary browser account:
  - Project display name: `AI Quota`
  - Project ID: `com-aiquota-mobile`
  - Project number: `550123003638`
- Requested ID `com.aiquota.mobile` could not be used as a Google Cloud project ID because dots are not allowed. `com-aiquota-mobile` was used as the closest Play Console style equivalent.
- This project is now the intended production Firebase/GCP project for the mobile app.

## 2026-05-30 Firebase Secret Token Exchange Implementation

The Gemini CLI loopback experiment was changed so the APK does not exchange the OAuth code directly with `oauth2.googleapis.com` and does not need a Google OAuth client secret.

New Functions:

- `startGeminiCliOAuth`
  - Callable Function.
  - Requires Firebase Auth.
  - Reads `GEMINI_CLI_GOOGLE_OAUTH_CLIENT_ID` and `GEMINI_CLI_GOOGLE_OAUTH_REDIRECT_URI` from Firebase Secret Manager.
  - Generates `state`, PKCE verifier, and authorization URL.
  - Stores only the short-lived state/verifier record in Firestore at `geminiCliOAuthStates/{stateHash}`.
- `completeGeminiCliOAuth`
  - Callable Function.
  - Requires Firebase Auth.
  - Reads `GEMINI_CLI_GOOGLE_OAUTH_CLIENT_ID`, `GEMINI_CLI_GOOGLE_OAUTH_CLIENT_SECRET`, and `GEMINI_CLI_GOOGLE_OAUTH_REDIRECT_URI` from Firebase Secret Manager.
  - Accepts the loopback callback URL from the Android app, validates state, exchanges the code with Google's token endpoint, and returns the token response to the app without returning or logging the client secret.

Android changes:

- `GeminiCliLoopbackOAuthActivity` now calls `GeminiCliFirebaseGateway.startOAuth()` before opening Chrome.
- After the localhost callback is received, it calls `GeminiCliFirebaseGateway.completeOAuth(callbackUrl)` and passes the returned token response into the existing `GeminiCliOAuthRepository` usage collector.
- Gemini connect is now routed to `GeminiCliLoopbackOAuthActivity` without requiring `aiquota.geminiCliOAuthClientId` in `android/local.properties`.
- The old local direct token exchange path remains in `GeminiCliOAuthRepository` for rollback compatibility, but the active UI route no longer uses it.

Production project config:

- `.firebaserc` now points default Firebase CLI operations to `com-aiquota-mobile`.
- `android/app/google-services.json` now points package `com.aiquota.mobile` at Firebase project `com-aiquota-mobile`.
- Firestore rules deny client reads/writes to `geminiCliOAuthStates/{stateHash}`.
- Firebase Android app registered:
  - App nickname: `AI Quota Android`
  - Package name: `com.aiquota.mobile`
  - App ID: `1:550123003638:android:b77771790177d817eb56d7`
- Firebase Authentication anonymous sign-in is enabled.
- Cloud Firestore default database is created in `asia-northeast3`.
- Secret Manager API is enabled.
- Gemini CLI Desktop OAuth client is created in Google Auth Platform. Its client ID is stored in Secret Manager; the client secret value is intentionally not stored in repo docs.
- OAuth app remains in testing mode. Test users must be added in Google Auth Platform > Audience before they can complete Google login. Do not record test-user email addresses in this document.

Production secrets created:

```text
GEMINI_CLI_GOOGLE_OAUTH_CLIENT_ID
GEMINI_CLI_GOOGLE_OAUTH_CLIENT_SECRET
GEMINI_CLI_GOOGLE_OAUTH_REDIRECT_URI=http://127.0.0.1:46417/oauth2callback
```

Required production deployment steps:

1. Switch local Firebase CLI auth to the browser console account, or grant the current CLI account sufficient IAM on `com-aiquota-mobile`.
2. Deploy Firestore rules and Functions.
3. Rebuild and reinstall the APK after deployment, then test Gemini login on the emulator/device.

Production deployment status:

- Local Firebase CLI is now using the same account as the Google Cloud Console session.
- Firestore rules were deployed to `com-aiquota-mobile`.
- `startGeminiCliOAuth` and `completeGeminiCliOAuth` were deployed as Node.js 22, v2 callable Functions in `us-central1`.
- `firebase functions:list --project com-aiquota-mobile` shows both callable Functions deployed.
- The deploy granted the runtime service account Secret Manager accessor permissions for the Gemini CLI OAuth secrets.
- The old local direct OAuth experiment property `aiquota.geminiCliOAuthClientId` has been removed from the current `android/local.properties`; the active Android flow now relies on Firebase Functions for the OAuth URL and token exchange.
- The Cloud Build/runtime service account `550123003638-compute@developer.gserviceaccount.com` needed these IAM roles before Functions deployment succeeded:
  - Logs Writer
  - Storage Object Viewer
  - Artifact Registry Writer
- The same runtime service account also needs Firestore write access for `geminiCliOAuthStates/{stateHash}`. `Cloud Datastore User` was granted after `startGeminiCliOAuth` logged Firestore `PERMISSION_DENIED`.
- Both v2 callable Functions must allow unauthenticated Cloud Run invocation so Firebase callable requests can reach the Functions framework. Authentication is still enforced inside the callable handler with Firebase Auth. Cloud Run security was changed to public access for:
  - `startgeminiclioauth`
  - `completegeminiclioauth`
- Antigravity placeholder secrets were created only to satisfy Firebase source analysis during targeted deploys:
  - `ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_ID`
  - `ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET`
  - `ANTIGRAVITY_GOOGLE_OAUTH_REDIRECT_URI`
  - `ANTIGRAVITY_TOKEN_MASTER_KEY`
- These Antigravity placeholders use the non-production value `UNCONFIGURED_DO_NOT_DEPLOY`; Antigravity Functions must not be deployed or used until real Antigravity config is restored.

Verification so far:

- `npm.cmd test` passed after the Firebase secret token exchange implementation.
- `.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --no-daemon --max-workers=1` passed.
- `.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug --no-daemon --max-workers=1` passed with the new `google-services.json`.
- `adb install -r android\app\build\outputs\apk\debug\app-debug.apk` succeeded on emulator `emulator-5554`.
- `firebase deploy --only "functions:startGeminiCliOAuth,functions:completeGeminiCliOAuth" --project com-aiquota-mobile --force` completed successfully.
- After removing the old local direct OAuth client ID property, `.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug --no-daemon --max-workers=1` passed again.
- The rebuilt APK was installed with `adb install -r` and launched on emulator `emulator-5554`; `com.aiquota.mobile/.MainActivity` became the focused app and no `FATAL EXCEPTION` appeared in the recent logcat window.
- A Gemini login retry initially failed before opening Chrome because Cloud Run rejected the callable request. After enabling public Cloud Run invocation for both callable services, the request reached the function.
- A second retry reached the function but failed while writing OAuth state to Firestore. After granting `Cloud Datastore User` to the runtime service account and waiting for IAM propagation, tapping Gemini connect opened Chrome at Google's OAuth account chooser for the production Gemini CLI OAuth client.
- Chrome on the emulator can restore old `127.0.0.1:46417/oauth2callback` tabs or probe loopback URLs before the active OAuth consent completes. `GeminiCliLoopbackOAuthActivity` now keeps listening through non-matching loopback requests and accepts only callbacks whose `state` matches the current Firebase-issued OAuth state.
- A host-browser loopback verification was run with host `127.0.0.1:46417` forwarded to the emulator. The OAuth callback reached the app, Firebase token exchange succeeded, and the APK did not contain or log the OAuth client secret.
- After token exchange, Gemini Code Assist collection reached `loadCodeAssist` and failed with HTTP `403 PERMISSION_DENIED`, reason `SERVICE_DISABLED`. This confirms the current blocker is private Code Assist API access for project `com-aiquota-mobile`, not Google sign-in, test-user setup, Firebase callable routing, or client-secret handling.
- The emulator Chrome final consent page can remain stuck on the scope-selection screen even when the scope checkbox appears selected. The reliable diagnostic path is to separate consent completion from collection: verify callback/token exchange first, then treat `403 SERVICE_DISABLED` from `loadCodeAssist` as the backend authorization result.

## Client Secret Decision Point

The current experiment code intentionally does not hardcode a Gemini OAuth client secret. To continue this exact Desktop OAuth client test, choose one of these paths:

- Local-only quick test: add the Desktop OAuth client secret via `android/local.properties` and `BuildConfig`, then include it in the token exchange. This is fast but the secret is extractable from the APK.
- Production-shaped test: move token exchange to Firebase Functions and store the Desktop OAuth client secret in Secret Manager. The app sends the short-lived code and PKCE verifier to the backend; the APK never contains the secret.
- Alternative client test: try an Android OAuth client flow with no client secret, but this is no longer the Store APK / Gemini CLI loopback shape and may still be rejected by the private Code Assist API.

## Experiment Rollback

To disable only this Gemini CLI OAuth experiment without touching other current work:

1. Remove this line from `android/local.properties`:

```properties
aiquota.geminiCliOAuthClientId=957428666864-tqnqupcmmh0jk9jv5f1b42fgjduvfuk3.apps.googleusercontent.com
```

2. Rebuild/reinstall the app.

To fully restore pre-experiment source/config files, run the local file rollback command above.

To remove the Console-side experiment resources, delete the desktop OAuth client from:

```text
https://console.cloud.google.com/auth/clients?project=project-2ecfb94e-45de-483f-bb7
```

That old project is now already shut down, so the old OAuth client should be treated as disposable and unavailable for future testing.

Optionally delete the whole test project if it is no longer needed:

```text
https://console.cloud.google.com/cloud-resource-manager?project=project-2ecfb94e-45de-483f-bb7
```

For any new Console-side Gemini CLI OAuth experiment, use the new project:

```text
https://console.cloud.google.com/home/dashboard?project=com-aiquota-mobile
```
