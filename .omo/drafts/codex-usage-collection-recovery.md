---
slug: codex-usage-collection-recovery
status: awaiting-approval
intent: clear
pending-action: write .omo/plans/codex-usage-collection-recovery.md
approach: Restore Codex collection to a trustable fresh-data flow by removing the stale visible-DOM success path as the primary completion condition, restoring production-safe navigation/session behavior where appropriate, and adding tests plus emulator/manual QA that prove stored usage changes only after a trusted fresh collection.
---

# Draft: codex-usage-collection-recovery

## Components (topology ledger)
<!-- Lock the SHAPE before depth. One row per top-level component that can succeed or fail independently. -->
<!-- id | outcome (one line) | status: active|deferred | evidence path -->
collector | Codex WebView collector only completes with trusted current usage data, not stale DOM | active | android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt
refresh | Background refresh waits/fails correctly without poisoning other providers or inventing freshness | active | android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt
normalize-store | Normalizer and scoped store preserve source/updatedAt truth without fake values | active | android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt; android/app/src/main/java/com/aiquota/mobile/providers/ProviderScopedStateRepository.kt
qa | Tests and emulator/CDP proof distinguish stale visible DOM from fresh Codex usage | active | android/app/src/test/java/com/aiquota/mobile/providers/ProviderWebCollectorScriptsTest.kt

## Open assumptions (announced defaults)
<!-- Record any default you adopt instead of asking, so the user can veto it at the gate. -->
<!-- assumption | adopted default | rationale | reversible? -->
Codex scope | Only Codex provider code/tests may change unless a shared helper is strictly required | User explicitly wants existing providers preserved | yes
Data source | Prefer a trusted network/state payload over visible DOM; visible DOM may be fallback only when freshness can be proven | Current failure is stale visible DOM accepted as success | yes
Production parity | Revert current Codex divergences from production 21 unless a divergence fixes the known reset-time requirement or is necessary for fresh data proof | User asked to preserve production behavior and existing improvements | yes
No fake freshness | Do not update timestamps, force success, or write placeholder usage unless a new trusted payload is stored | Existing issue is updatedAt masking stale data | no

## Findings (cited - path:lines)
- Current Codex collector resets `window.__AIQuotaCodexNetworkRows` at startup, while production 21 preserved existing rows. Current source: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:1010`.
- Current row extractor reads only `window.__AIQuotaCodexNetworkRows`, so when runtime logs show `rows=0`, it cannot use page storage/script text as production 21 did. Current source: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:1745`.
- Current scan path returns `extractCodexUsageFromRows(accountId) || extractCodexVisibleDomUsage(accountId)`, so empty network rows fall through to visible DOM success. Current source: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:1803`.
- Current navigation adds `hasCodexNavigationAuth` and `missing_navigation_auth` behavior that production 21 did not have. Current source: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:1962`.
- Background refresh injects Codex once per route and completes immediately on any accepted payload. Current source: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:544`; payload completion: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:841`.
- Normalizer stores visible DOM as `ProviderPayloadSource.VISIBLE_DOM` and prefers remaining percent; it does not transform 99 into another value. Current source: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:495`.
- Script metadata still reports Codex `e260503` even when collector logic differs between production 21 and current installed APK. Current source: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderScriptProviders.kt:110`.

## Decisions (with rationale)
- Treat session as valid when `session/me/account` are 200; do not classify this problem as auth expiry without login redirect, 401/403, or auth-required evidence.
- Fix collection trust first: prevent stale visible DOM from being considered a successful refresh when no network/state evidence was captured.
- Keep the previously requested weekly time-only reset display improvement, but isolate it from usage freshness logic.
- Add RED-first unit/characterization coverage for stale DOM accepted as success, then implement the minimal Codex-only correction.

## Scope IN
- Codex WebView collector script.
- Codex collector tests and, if necessary, narrow background refresh behavior for Codex only.
- Script metadata version bump if collector JS changes.
- Emulator/CDP verification for Codex stored snapshot vs hidden WebView/raw evidence.

## Scope OUT (Must NOT have)
- No changes to Claude, Gemini, Copilot, Antigravity, Cursor, GLM, or OpenCode collection logic unless only shared test scaffolding is touched.
- No fake usage, hardcoded percentages, timestamp-only refresh, or best-effort success masking.
- No provider order changes.
- No session clearing behavior changes unless directly required for Codex and separately proven.

## Open questions
None. Adopted defaults above are reversible except the no-fake-freshness guardrail.

## Approval gate
status: awaiting-approval
pending action: write decision-complete work plan to .omo/plans/codex-usage-collection-recovery.md
approval needed: user confirms this approach before implementation planning continues
<!-- When exploration is exhausted and unknowns are answered, set status: awaiting-approval. -->
<!-- That durable record is the loop guard: on a later turn read it and resume at the gate instead of re-running exploration. -->
