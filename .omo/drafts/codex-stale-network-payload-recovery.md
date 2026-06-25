---
slug: codex-stale-network-payload-recovery
status: awaiting-approval
intent: clear
pending-action: write .omo/plans/codex-stale-network-payload-recovery.md
approach: Codex collector only: scope retained network rows to the current collection, prefer fresh visible DOM when it contains complete usage limits, and keep provider storage/normalizer/session behavior unchanged.
---

# Draft: codex-stale-network-payload-recovery

## Components (topology ledger)
<!-- Lock the SHAPE before depth. One row per top-level component that can succeed or fail independently. -->
<!-- id | outcome (one line) | status: active|deferred | evidence path -->
codex-collector-freshness | Codex manual/non-scheduled reset is reflected instead of stale retained network rows | active | android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:1000
codex-regression-guard | Existing Codex navigation/session/DOM fallback behavior remains intact | active | android/app/src/test/java/com/aiquota/mobile/providers/ProviderWebCollectorScriptsTest.kt:2296
provider-isolation | No non-Codex provider source or normalizer behavior is changed | active | android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:1751

## Open assumptions (announced defaults)
<!-- Record any default you adopt instead of asking, so the user can veto it at the gate. -->
<!-- assumption | adopted default | rationale | reversible? -->
freshness-boundary | Treat each Codex collector start/page collection as the freshness boundary for captured network rows | Prevents hidden WebView memory from surviving across manual reset events; local to Codex JS | yes
fallback-priority | If visible DOM has complete primary/weekly usage, allow it to beat retained network rows; otherwise use current network/state rows | Preserves network/state collection when DOM is incomplete, but fixes the reset recovery mismatch | yes
scope | Do not modify ProviderUsageNormalizer, repository merge, session cleaner, or other provider collectors | The bug is row selection before normalization, not storage or generic percent handling | yes

## Findings (cited - path:lines)
- `ProviderWebCollectorScripts.kt:1010` initializes `window.__AIQuotaCodexNetworkRows` with `|| []`, preserving prior rows in the hidden WebView.
- `ProviderWebCollectorScripts.kt:1372` appends matching Codex responses to the retained network row array and caps the array at 80, but does not mark a per-refresh generation.
- `ProviderWebCollectorScripts.kt:1751` concatenates retained network rows before stored state rows and scans oldest-to-newest.
- `ProviderWebCollectorScripts.kt:1824` returns `extractCodexUsageFromRows(accountId) || extractCodexVisibleDomUsage(accountId)`, so any old row payload prevents fresh DOM from being considered.
- `ProviderWebCollectorScriptsTest.kt:2296` and `ProviderWebCollectorScriptsTest.kt:2314` currently assert the stale-prone priority behavior and must be changed to assert the corrected behavior.
- `ProviderWebCollectorScriptsTest.kt:3098` already protects a related Codex DOM-over-internal-used-percent path and must stay green.

## Decisions (with rationale)
- Fix at the Codex collector input-selection layer, not in `ProviderUsageNormalizer` or repository merge. Rationale: runtime evidence shows refresh succeeds and saves structured payload; the stale value is selected before normalization.
- Keep network capture for Codex because it is still needed when the ChatGPT page does not expose complete visible DOM.
- Add freshness metadata or a scoped row array for Codex rows so stale rows from a previous collection cannot win over current page evidence.
- Replace the current stale-row-positive test with a RED test that proves old retained network rows do not override current visible DOM after a manual/non-scheduled reset.

## Scope IN
- `ProviderWebCollectorScripts.kt` Codex-only JS collector changes.
- `ProviderWebCollectorScriptsTest.kt` Codex-only tests for stale network rows, visible DOM priority, and existing navigation/session behavior.
- Targeted unit/runtime tests and one emulator/hidden-WebView refresh evidence pass if implementation is approved.

## Scope OUT (Must NOT have)
- No changes to Claude, Gemini, Copilot, Cursor, Antigravity, GLM, or OpenCode collector logic.
- No changes to shared repository merge, snapshot codec, widget parser, session cleaner, or provider order.
- No fake usage, hardcoded values, forced update timestamps, or best-effort pass-through.
- No removal of Codex network capture or fallback DOM parsing.

## Open questions
None blocking. Recommended path is scoped and reversible.

## Approval gate
status: awaiting-approval
<!-- When exploration is exhausted and unknowns are answered, set status: awaiting-approval. -->
<!-- That durable record is the loop guard: on a later turn read it and resume at the gate instead of re-running exploration. -->
