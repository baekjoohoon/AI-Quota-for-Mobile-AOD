---
slug: glm-json-usage-collector
status: plan-written
intent: clear
pending-action: write .omo/plans/glm-json-usage-collector.md
approach: Replace GLM Web OAuth broad DOM/script/storage scanning with a quota JSON-first collector that directly fetches api.z.ai/api/monitor/usage/quota/limit inside the authenticated WebView, keeping DOM/network scan paths as fallback only.
---

# Draft: glm-json-usage-collector

## Components (topology ledger)
<!-- Lock the SHAPE before depth. One row per top-level component that can succeed or fail independently. -->
<!-- id | outcome (one line) | status: active|deferred | evidence path -->
GLM collector | Direct quota JSON fetch produces the same 5H/weekly/monthly usage lines without broad DOM scan | active | in-app browser CDP capture, ProviderWebCollectorScripts.kt
GLM WebView lifecycle | Payload success/failure still tears down isolated WebView and does not leak into other providers | active | GlmIsolatedWebSessionService.kt
GLM normalization | Existing GLM label/order/reset parsing continues to accept quota/limit JSON shape | active | ProviderUsageNormalizer.kt
Regression QA | GLM refresh no longer causes provider queue timeout/stale cascade under repeated refresh | active | long-session monitor evidence

## Open assumptions (announced defaults)
<!-- Record any default you adopt instead of asking, so the user can veto it at the gate. -->
<!-- assumption | adopted default | rationale | reversible? -->
Endpoint choice | Use https://api.z.ai/api/monitor/usage/quota/limit first | Live logged-in usage page calls it and the response contains data.limits with all required quota rows | yes
Fallback behavior | Keep existing DOM/network fallback but run it only after direct JSON fetch fails | Preserves current behavior when endpoint changes or auth cookie is insufficient | yes
Scope limit | Touch GLM collector/lifecycle tests only | User repeatedly forbids unrelated provider changes | yes
Cooldown | Do not add cooldown in this plan | User asked for JSON extraction plan; JSON-first path should reduce root cost before adding queue policy | yes

## Findings (cited - path:lines)
ProviderWebCollectorScripts.kt:525-535 has a shared fetchJson helper that can fetch with credentials and parse JSON.
ProviderWebCollectorScripts.kt:537-559 currently exposes broad text/rows helpers that read document text, storage, and script text.
ProviderWebCollectorScripts.kt:2443-2474 currently hooks fetch and reads response.clone().text() before GLM URL/body relevance is fully minimized.
ProviderWebCollectorScripts.kt:2492-2586 currently combines network rows, page state, and visible text in every GLM collect attempt.
ProviderWebCollectorScripts.kt:2595-2597 retries GLM collection up to 8 times at 1500ms, which can keep the WebView alive when payload is not found.
ProviderWebCollectorScripts.kt:316-327 already recognizes api.z.ai quota/usage/subscription resources.
ProviderUsageNormalizer.kt:59-78 normalizes GLM JSON from data.limits.
ProviderUsageNormalizer.kt:91-98 sorts GLM lines as 5H, weekly, monthly.
ProviderUsageNormalizer.kt:105-119 maps TOKENS_LIMIT unit/number to 5H/weekly and TIME_LIMIT to monthly.
ProviderUsageNormalizer.kt:152-157 reads nextResetTime.
GlmIsolatedWebSessionService.kt:247-260 currently injects collector from onLoadResource/onPageFinished.
Live in-app browser CDP capture on 2026-06-26 showed https://api.z.ai/api/monitor/usage/quota/limit returns 200 application/json, body length about 526 bytes, with data.limits containing TIME_LIMIT and two TOKENS_LIMIT rows.

## Decisions (with rationale)
Use quota JSON as the primary source. It contains all currently displayed GLM usage rows and reset timestamps in a small response.
Keep visible DOM as fallback only. This prevents data loss if z.ai changes the endpoint while avoiding the normal-case DOM/script/storage scan cost.
Prefilter GLM network hook by URL before cloning response bodies. Avoid reading analytics, HTML, script, or unrelated Fetch responses.
Do not alter ProviderUsageNormalizer percent semantics. It already handles the GLM JSON shape and preserves the existing 5H/weekly/monthly order.
Do not change other provider refresh/session code. This plan is GLM-only.

## Scope IN
ProviderWebCollectorScripts.kt GLM collector changes.
GlmIsolatedWebSessionService.kt changes only if needed to reduce duplicate GLM collector injection/resource triggers.
Focused tests for GLM collector script/normalizer behavior if existing test structure supports it.
Debug APK installation and GLM Web OAuth refresh smoke test after implementation.
Long refresh observation focused on GLM not causing queue-wide stale state.

## Scope OUT (Must NOT have)
No changes to Claude, Codex, OpenCode, Gemini, Copilot, Antigravity, or Cursor collector logic.
No GLM session clearing changes.
No fake usage, hardcoded quota values, or artificial updatedAt changes.
No removal of existing fallback behavior unless a test proves it is unreachable and harmful.
No API key flow changes.

## Open questions
None blocking. If implementation proves quota/limit requires headers unavailable to WebView fetch, fallback remains and the worker must report that as a failed assumption instead of changing scope.

## Approval gate
status: approved-by-user-request
<!-- When exploration is exhausted and unknowns are answered, set status: awaiting-approval. -->
<!-- That durable record is the loop guard: on a later turn read it and resume at the gate instead of re-running exploration. -->
