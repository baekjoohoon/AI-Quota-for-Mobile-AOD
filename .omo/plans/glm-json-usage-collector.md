# glm-json-usage-collector - Work Plan

## TL;DR (For humans)
**What you'll get:** GLM Web OAuth refresh will try the small quota JSON response first, then fall back to the current DOM-based collector only if that direct JSON path fails. The user-visible GLM rows stay the same: 5시간 한도, 주간 한도, 월간 한도.

**Why this approach:** The logged-in usage page already calls a small quota endpoint that contains all required limits and reset times. Reading that JSON first avoids repeatedly scanning storage, scripts, window state, and visible DOM during every refresh.

**What it will NOT do:** It will not change other providers, clear GLM sessions, fake usage values, or update timestamps without a trusted payload.

**Effort:** Short
**Risk:** Medium - z.ai is an external SPA/API surface, so the endpoint must remain fallback-protected.
**Decisions to sanity-check:** Keep cooldown out of this change; first reduce GLM collection cost at the source.

Your next move: approve execution with `$omo:ulw-loop` or ask for a review pass first. Full execution detail follows below.

---

> TL;DR (machine): Short GLM-only collector refactor; medium external-endpoint risk; deliver quota JSON-first GLM Web OAuth collection with DOM fallback preserved.

## Scope
### Must have
- GLM Web OAuth collector must first fetch `https://api.z.ai/api/monitor/usage/quota/limit` with the existing authenticated WebView session.
- The direct JSON path must produce normalized 5-hour, weekly, and monthly usage lines through existing GLM normalization.
- Existing GLM DOM/network fallback must remain available only after the direct JSON path fails or returns no trusted limits.
- GLM network hook must prefilter URL candidates before reading response bodies.
- GLM payload success/failure must continue to destroy the GLM isolated WebView/process as currently designed.
- GLM timeout/failure must keep previous snapshot and must not mark the provider disconnected unless the existing auth classifier has explicit auth evidence.

### Must NOT have (guardrails, anti-slop, scope boundaries)
- Do not modify any non-GLM provider collector, refresh, session, normalizer, widget, or notification logic.
- Do not alter GLM API Key flow.
- Do not hardcode usage percentages, reset times, timestamps, or plan names.
- Do not remove visible DOM fallback unless a focused test proves the JSON-first path fully supersedes it and the user approves.
- Do not add cooldown in this plan.

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- Test decision: tests-after, using existing Android unit/instrumentation test structure where available plus emulator smoke logs.
- Evidence: `.omo/evidence/glm-json-usage-collector/`
- Required evidence files:
  - `task-1-direct-json-unit.txt`
  - `task-2-network-prefilter-unit.txt`
  - `task-3-debug-build-install.txt`
  - `task-4-glm-refresh-smoke.txt`
  - `task-5-regression-monitor.txt`

## Execution strategy
### Parallel execution waves
> Target 5-8 todos per wave. Fewer than 3 (except the final) means you under-split.
- Wave 1: GLM collector refactor and focused unit coverage.
- Wave 2: Build/install and emulator smoke verification.
- Wave 3: Short repeated refresh observation to confirm GLM no longer causes queue-wide stale state.

### Dependency matrix
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| 1 | none | 2, 3, 4 | none |
| 2 | 1 | 3, 4 | none |
| 3 | 1, 2 | 4, 5 | none |
| 4 | 3 | 5 | none |
| 5 | 4 | final verification | none |

## Todos
> Implementation + Test = ONE todo. Never separate.
<!-- APPEND TASK BATCHES BELOW THIS LINE WITH edit/apply_patch - never rewrite the headers above. -->
- [ ] 1. Add GLM quota JSON-first collection path
  What to do / Must NOT do: In `ProviderWebCollectorScripts.glm()`, add a first-pass direct fetch to `https://api.z.ai/api/monitor/usage/quota/limit` using existing `fetch` with `credentials: "include"` or the collector helper. If the response contains `data.limits`, post it immediately as the GLM payload. Preserve current fallback functions for no-payload and no-subscription cases. Do not touch other provider script builders.
  Parallelization: Wave 1 | Blocked by: none | Blocks: 2, 3, 4
  References (executor has NO interview context - be exhaustive): `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:525`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:2295`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:2581`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:59`
  Acceptance criteria (agent-executable): A focused unit or script test proves a GLM quota JSON payload with `data.limits` normalizes to exactly the three GLM keys `glm:tokens`, `glm:weekly_tokens`, `glm:mcp`.
  QA scenarios (name the exact tool + invocation): Happy: run focused Android test or a JVM normalizer test and save output to `.omo/evidence/glm-json-usage-collector/task-1-direct-json-unit.txt`. Failure: simulate JSON fetch returning no `limits`; assert fallback path still reaches existing no-trusted-payload behavior or DOM fallback without posting fake data, save same evidence file.
  Commit: Y | `fix(provider): GLM 사용량 수집을 JSON 우선 방식으로 변경`

- [ ] 2. Prefilter GLM network hook before reading response bodies
  What to do / Must NOT do: Change the GLM `fetch`/XHR hook so it checks the URL before cloning/reading response text. Only read candidates matching `api.z.ai/api/monitor/usage/quota/limit`, `/api/monitor/usage`, `/api/biz/subscription/list`, quota, usage, plan, or subscription. Do not read analytics, HTML documents, JS chunks, images, or unrelated app calls.
  Parallelization: Wave 1 | Blocked by: 1 | Blocks: 3, 4
  References (executor has NO interview context - be exhaustive): `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:2443`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:2457`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:2477`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:316`
  Acceptance criteria (agent-executable): A focused test or static assertion proves unrelated URLs do not cause `response.clone().text()`/XHR response body reads, while quota endpoint URLs do.
  QA scenarios (name the exact tool + invocation): Happy: run the focused test and save to `.omo/evidence/glm-json-usage-collector/task-2-network-prefilter-unit.txt`. Failure: feed an analytics or HTML URL and assert it is ignored before body parse, save output to same evidence file.
  Commit: Y | `fix(provider): GLM 네트워크 응답 필터링 경량화`

- [ ] 3. Build and install debug APK
  What to do / Must NOT do: Build debug APK and install on emulator. Do not modify signing, Firebase, other provider code, or version metadata unless build/install explicitly requires it.
  Parallelization: Wave 2 | Blocked by: 1, 2 | Blocks: 4, 5
  References (executor has NO interview context - be exhaustive): `android/app/build.gradle*`, existing project Gradle wrapper under `android/`, previous debug install workflow.
  Acceptance criteria (agent-executable): `:app:assembleDebug` exits 0 and `adb install -r` exits 0.
  QA scenarios (name the exact tool + invocation): Happy: run `.\gradlew :app:assembleDebug` from `android` then `adb install -r app\build\outputs\apk\debug\app-debug.apk`; save console output to `.omo/evidence/glm-json-usage-collector/task-3-debug-build-install.txt`. Failure: if install fails, capture exact adb error and do not continue to smoke QA.
  Commit: N | covered by Todo 1/2 commit if code changes are staged together.

- [ ] 4. Verify GLM Web OAuth refresh uses JSON-first path
  What to do / Must NOT do: With existing GLM Web OAuth session, trigger GLM refresh and inspect logs for direct quota JSON success. Confirm GLM lines update from trusted payload and that broad DOM fallback is not the normal successful path. Do not re-login or clear sessions unless the user explicitly says so.
  Parallelization: Wave 2 | Blocked by: 3 | Blocks: 5
  References (executor has NO interview context - be exhaustive): `android/app/src/main/java/com/aiquota/mobile/providers/GlmIsolatedWebSessionService.kt:247`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:482`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:91`
  Acceptance criteria (agent-executable): Logs show GLM payload success from quota JSON path, GLM snapshot remains `CONNECTED`, and lines are ordered 5시간 한도, 주간 한도, 월간 한도.
  QA scenarios (name the exact tool + invocation): Happy: trigger refresh via app UI or existing refresh command path, collect `adb logcat` GLM/provider lines, and save to `.omo/evidence/glm-json-usage-collector/task-4-glm-refresh-smoke.txt`. Failure: block network or simulate endpoint failure if practical; confirm fallback does not post fake usage and previous snapshot is kept.
  Commit: N | QA only.

- [ ] 5. Run short regression monitor for queue health
  What to do / Must NOT do: Observe repeated provider refresh for at least 30 minutes. The goal is not to prove 120 minutes; it is to catch immediate regression where GLM still blocks the queue. Do not add cooldown, fake timestamps, or skip other providers to pass.
  Parallelization: Wave 3 | Blocked by: 4 | Blocks: final verification
  References (executor has NO interview context - be exhaustive): prior monitor evidence `.omo/ulw-loop/evidence/session-monitor-20260626-073338/final.json`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt:25`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:336`
  Acceptance criteria (agent-executable): During the observation window, GLM refresh completes without ANR, and Claude/Codex/Gemini latest successful snapshot times continue to advance when their refresh cycles run.
  QA scenarios (name the exact tool + invocation): Happy: run the existing monitor script/check method and save output to `.omo/evidence/glm-json-usage-collector/task-5-regression-monitor.txt`. Failure: if any provider becomes stale due to timeout, stop and report the first failing provider, first timeout log, and GLM log immediately before it.
  Commit: N | QA only.

## Final verification wave
> Runs in parallel after ALL todos. ALL must APPROVE. Surface results and wait for the user's explicit okay before declaring complete.
- [ ] F1. Plan compliance audit: verify only GLM collector-related files changed, no unrelated provider/session/widget files changed.
- [ ] F2. Code quality review: inspect GLM JSON-first path for fake data, broad scans on happy path, and missing fallback.
- [ ] F3. Real manual QA: emulator GLM refresh succeeds and UI/widget/notification GLM gauge still displays the same labels and percentages from a real payload.
- [ ] F4. Scope fidelity: confirm no cooldown or session-clearing behavior was added under this plan.

## Commit strategy
- Prefer one commit if implementation changes are limited to GLM collector code and focused tests:
  `fix(provider): GLM 사용량 수집을 JSON 우선 방식으로 변경`
- If tests are substantial and separate, split:
  `fix(provider): GLM 사용량 수집을 JSON 우선 방식으로 변경`
  `test(provider): GLM JSON 수집 경로 검증 추가`
- Stage only files changed for this task. Do not use `git add -A`.

## Success criteria
- GLM Web OAuth refresh can collect 5시간 한도, 주간 한도, 월간 한도 from `api.z.ai/api/monitor/usage/quota/limit`.
- GLM normalizer preserves existing labels, order, percentages, and reset time handling.
- GLM happy path no longer performs broad `document.scripts`, `localStorage/sessionStorage`, `window` recursive, or full visible DOM scans before posting a trusted payload.
- Endpoint failure falls back to existing behavior without fake values or timestamp-only updates.
- Debug APK builds and installs.
- Emulator smoke QA confirms GLM refresh succeeds and does not immediately cause other providers to stale.
- No non-GLM provider code or behavior is changed.
