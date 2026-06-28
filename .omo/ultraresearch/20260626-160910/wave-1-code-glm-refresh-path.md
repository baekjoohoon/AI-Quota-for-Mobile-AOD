# Wave 1 - GLM Refresh Path

## Findings
- `ProviderRefreshPlan` defines GLM as a `NATIVE_API` provider by definition, but `GlmRuntimeRefreshJobs.resolve()` switches GLM Web OAuth mode to `HIDDEN_WEB_COLLECTOR` and starts from `https://z.ai/manage-apikey/coding-plan/personal/usage`.
- `ProviderBackgroundRefreshService.collectWebProviderUsage()` first tries `GlmUsageRepository.fetchUsagePayloadFromWebSession()`. When that returns `glm_web_cookie_missing`, it starts `GlmIsolatedWebSession.collectUsage()`.
- In the 2026-06-26 15:14 monitor log, direct refresh repeatedly emitted `glm_web_cookie_missing`; therefore the heavy isolated WebView path remained the normal path.

## Sources
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt:89-108`
- `android/app/src/main/java/com/aiquota/mobile/providers/GlmUsagePageRoutes.kt:14-22`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:482-506`
- `.omo/ulw-loop/session-monitor-20260626-120m/evidence/session-monitor-20260626-151411-logcat.txt`

## EXPAND
- LEAD: why saved GLM cookies are not visible to the direct main-process fetch -- WHY: this keeps GLM on the expensive WebView fallback -- ANGLE: inspect GLM login/service process and storage.
