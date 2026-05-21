# Claude Session Reset Investigation - 2026-05-21

## Context

- User reported `Claude Session` showing `91% left` with reset text `Starts when a message is sent`.
- This is invalid because a non-full session quota and an expired/reset-waiting timer cannot be treated as one coherent live quota payload.

## Root Cause

- The app preserved the previous Claude snapshot when a background refresh failed.
- That previous snapshot contained:
  - `Claude Session` remaining percent from an older collector run.
  - `Claude Session` `resetsAt` that had already passed.
- `UsageResetText` converted the expired timestamp into `Starts when a message is sent`.
- Result: old usage percent and reset-start state were mixed into one row.

## Runtime Evidence

- `logcat` showed Claude background collector reaching `https://claude.ai/login`.
- Because the hidden WebView was not authenticated, the collector skipped injection and did not receive a fresh `/usage` payload.
- Native HTTP fallback was tested and rejected:
  - `/api/organizations`: 403
  - `/api/organizations/me`: 403
- Therefore Claude collection must remain WebView-session based.

## Fix Applied

- `LocalUsageRepository.readSnapshots()` now removes expired provider-specific volatile rows.
- Current scoped rule:
  - Provider: Claude
  - Row: `claude:session`
  - Condition: `resetsAt <= now`
  - Result: remove the stale session row until a fresh provider payload is collected.
- `UsageResetText` keeps suppressing `Starts when a message is sent` when a line still carries non-full remaining percent.
- `WebLoginActivity.finishSuccessfulLogin()` now calls `CookieManager.flush()` before closing, so a future successful Claude login has a better chance of surviving into background refresh.

## Verification

- Unit tests:
  - `LocalUsageRepositoryTest`
  - `UsageResetTextTest`
  - `ProviderRefreshPlanTest`
  - `ProviderWebCollectorScriptsTest`
  - `CopilotNativeUsageFetcherTest`
- Result: PASS.
- `:app:assembleDebug`: PASS.
- Installed to `emulator-5554`: PASS.
- UI after install no longer shows `Claude Session 91%` with `Starts when a message is sent`.
- UI shows `Claude Weekly` and `Claude Design` only while the current Claude WebView session is not authenticated.

## Remaining Work

- This fix prevents incoherent stale display.
- It does not fake or reconstruct the 5-hour session quota.
- Fresh 5-hour session collection still requires the Claude WebView session to be authenticated and the in-page collector to receive a real `five_hour` or equivalent usage payload.
