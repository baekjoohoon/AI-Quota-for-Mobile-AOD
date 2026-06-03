# Policy Safe Foreground Provider Refresh Limitations

Date: 2026-05-26

## Android Foreground Service Boundary

AI Quota uses a user-approved pinned notification and a `dataSync` foreground service for live provider refresh. This does not bypass Android background execution limits. If the user disables notifications, disables live monitoring, or force-stops the app, continuous background collection is not guaranteed.

## Passive Widget Updates

Passive `APPWIDGET_UPDATE` redraws local display-only cache. It does not start `ProviderBackgroundRefreshService` and does not run provider collectors. This prevents Android background foreground-service start rejection from killing the app process.

## Manual Widget Refresh

Manual widget refresh is a user action and may request immediate collection through `ProviderBackgroundRefreshService`. If Android rejects the foreground-service start, widget spinner feedback is cleared and cached surfaces are redrawn.

Direct shell verification of this path is limited because the receiver/service are intentionally non-exported. Real validation should use a launcher widget click or UI automation against a widget host.

## Notification Permission

If Android notification permission is denied, the live monitoring loop is not started. Widgets can still display cached data and users can open the app or use manual refresh paths where Android allows them.

## User Stop Action

The pinned notification includes a stop action. When the user stops live refresh, `live_monitoring_enabled=false` is persisted and the loop stops. The app may restart the loop only after the user enables live monitoring again and permission/policy conditions are satisfied.

## Force Stop And Reboot

If the user force-stops the app, Android prevents the app from restarting itself until the user explicitly opens or interacts with it again. This implementation does not add `BOOT_COMPLETED` startup for the `dataSync` foreground service and does not add exact alarm permission.

## OEM Battery Policy

Some OEM battery policies may stop long-running foreground services. On app foreground entry, AI Quota checks stale heartbeat state and can restart the service when live monitoring is enabled, notification permission is available, and at least one trusted provider snapshot is refreshable.

## Provider Sessions

This work reduces false `INTERACTIVE_AUTH_REQUIRED` states by preserving the distinction between auth failures and collector/payload failures. It does not make expired provider sessions valid. Clear login redirect, main-frame 401/403, explicit collector auth-required, or confirmed OAuth/token refresh failure still become `INTERACTIVE_AUTH_REQUIRED`.

## Gemini And Antigravity OAuth

The APK must not embed OAuth `client_secret`. This work keeps that invariant.

Runtime evidence showed the Android Google Identity access token path is not enough for Gemini/Antigravity quota collection because Code Assist returned HTTP 403. The default route therefore uses the WebView OAuth/session path and tries WebView Google session cookies before token-based fallbacks.

This means long-running Gemini/Antigravity refresh depends on the WebView Google session remaining valid. If Google expires that WebView session, requires re-authentication, blocks embedded WebView OAuth, or changes Code Assist request requirements, the provider remains retry-pending/stale until the user opens the app and completes login again.

For local debug testing, the APK is still signed with the user's local debug keystore so Android Google Identity fallback remains registered, but that fallback is not the primary collection path. Release builds still require release/upload signing SHA-1 registration if Google Identity fallback remains shipped.

## Emulator Verification Limits

The verified emulator had no real provider login session. Claude correctly returned HTTP 403 and stopped as auth-required. The 90-second loop was verified with a seeded Gemini trusted snapshot, where missing OAuth payload is treated as `NO_TRUSTED_PAYLOAD` and previous trusted usage remains stale instead of auth-required.
