# Wave 1: Codebase GLM Web OAuth

## Findings

- `ProviderWebCollectorScripts.glm()` accepts reset metadata from hidden objects through `nextResetTime`, `next_reset_time`, `resetTime`, `resetAt`, `reset_at`, `resetsAt`, or `resets_at`.
- The GLM Web collector hooks both `fetch` and `XMLHttpRequest` and stores matching rows in `window.__AIQuotaGlmNetworkRows`.
- Matching rows include `/api/monitor/usage`, `/api/biz/subscription`, `TOKENS_LIMIT`, and `TIME_LIMIT`.
- The visible text fallback creates percent-only limits and does not add a reset field.
- `ProviderUsageNormalizer` persists reset only from `nextResetTime` or `next_reset_time`.
- `GlmRuntimeRefreshJobs.resolve()` forces GLM Web OAuth refresh to `https://z.ai/manage-apikey/coding-plan/personal/usage`.

## Evidence

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:2334`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:2356`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:2409`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:2428`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:2497`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:152`
- `android/app/src/main/java/com/aiquota/mobile/providers/GlmUsagePageRoutes.kt:14`

## EXPAND

none - codebase path is clear.

