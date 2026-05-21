# Store APK Provider Reverse Engineering impl Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Analyze the published store APK provider flows end-to-end, then produce enough evidence to recreate each supported provider login, authentication, usage collection, and normalization path in this app without hardcoded usage values.

**Architecture:** Treat the store APK as the behavioral source of truth, not the current app. Use static analysis to map provider classes, Remote Config script keys, fallback assets, WebView services, JavaScript bridges, DataStore names, and normalizers; use runtime analysis to capture the actual decrypted/injected collector scripts and same-origin provider responses. Convert captured evidence into provider-specific implementation handoff docs before touching production provider code.

**Tech Stack:** Android APK/JADX/APKTool, ADB emulator, Android WebView, Firebase Remote Config observation, Frida or equivalent runtime hook tooling, Kotlin/JUnit4, Markdown evidence docs under `docs/qa`, sanitized captured scripts under `.tmp/store-apk-analysis`.

## 현재 실행 상태 (2026-05-21)

아래 checkbox plan은 최초 실행 절차다. 현재 authoritative 상태는 `docs/qa/store-apk-context-handoff-2026-05-20.md`와 `docs/qa/store-apk-provider-evidence-2026-05-20.md`에 있다.

| 계획 영역 | 현재 상태 | 근거 | 다음 조건 |
| --- | --- | --- | --- |
| Task 1-4 정적 workspace/map/decrypt/runbook | 완료 | scratch rebuild, JADX/apktool, provider map, Remote Config/fallback/decrypt path, runtime runbook 작성 | 없음 |
| Task 5 runtime collector script capture | 차단 | attached APK는 base-only이고, `u.sage` 미설치, base APK에 native `lib/<abi>/libs.so` 없음, manifest가 `base__abi,base__density` 요구 | full Store split set, same-version installed `u.sage`, 또는 equivalent native decrypt evidence |
| Task 6 provider evidence document | 정적/encrypted 단계 완료, runtime section 차단 | provider section과 readiness matrix 존재; 모든 provider `Ready=No` | runtime/decrypt evidence |
| Task 7 parity gap tracker | 정적/current-app gap 단계 완료 | Copilot/provider-scope/auth/service/widget/storage/normalizer/manifest gap 기록 | runtime evidence 확보 시 row 추가 가능 |
| Task 8 context handoff | 완료, 계속 갱신 | source APK, scratch, key, blocker, next action 기록 | next action 변경 |
| Task 9 implementation handoff gate | guard 완료, implementation plan 차단 | readiness checklist에서 gate 통과 provider 없음 | 최소 1개 provider가 readiness 전 항목 충족 |

Production provider code 변경은 여전히 금지다. Runtime/decrypt evidence 없이 provider-specific implementation plan을 작성하지 않는다.

---

## Source Inputs

- Store APK: `C:/Users/datell1357/Downloads/AI Usage_1.260505.7.apk`
- Existing static analysis: `docs/qa/sage-apk-usage-collection-analysis-2026-05-17.md`
- Current app provider code:
  - `android/app/src/main/java/com/aiusage/mobile/providers/WebLoginActivity.kt`
  - `android/app/src/main/java/com/aiusage/mobile/providers/ProviderWebCollectorScripts.kt`
  - `android/app/src/main/java/com/aiusage/mobile/providers/ProviderUsageNormalizer.kt`
  - `android/app/src/main/java/com/aiusage/mobile/providers/ProviderRefreshPlan.kt`
  - `android/app/src/main/java/com/aiusage/mobile/ui/BackgroundProviderWebCollector.kt`

## Scope

Analyze every provider supported by the store APK.

Current known provider/script-key candidates from prior analysis:

| Provider | Store APK script key | Fallback asset | Known class clue |
| --- | --- | --- | --- |
| Claude | `a260503` | `assets/a` | `o50` |
| Copilot | `b260503` | `assets/b` | `yh0` |
| Antigravity | `c260503` | `assets/c` | unknown app support status in current app |
| Gemini | `d260503` | `assets/d` | `jg1` |
| Codex | `e260503` | `assets/e` | `h70` |

Cursor is not assumed supported by the store APK until static scan confirms a provider config, script key, login URL, service, or DataStore.

## Non-Negotiables

- Do not use hardcoded preview/sample usage values as real provider data.
- Do not log raw tokens, cookies, refresh tokens, OAuth codes, raw credentials, or full provider HTML.
- Do not commit captured private account payloads.
- Do not modify unrelated provider code while analyzing one provider.
- Do not mark a provider as implemented unless a real usage snapshot path is proven.
- Preserve the distinction between login success, authenticated session detection, collector success, and normalized usage snapshot success.

---

## Files To Create

- `docs/qa/store-apk-provider-map-2026-05-20.md`
  - Provider inventory, login URLs, services, script providers, script keys, fallback assets, DataStore names, and JavaScript bridge names.

- `docs/qa/store-apk-runtime-capture-runbook-2026-05-20.md`
  - Exact emulator setup, install steps, log filters, Frida hook strategy, manual login checklist, and evidence capture commands.

- `docs/qa/store-apk-provider-evidence-2026-05-20.md`
  - Provider-by-provider evidence table for login, auth completion, script injection, endpoint calls, payload keys, normalized rows, and gaps.

- `docs/qa/store-apk-parity-gap-tracker-2026-05-20.md`
  - Current app vs store APK differences, ordered by provider and implementation priority.

- `docs/qa/store-apk-context-handoff-2026-05-20.md`
  - Compact handoff for future agents after context compaction: source APK path, artifact paths, commands, known classes, known keys, next step, and risk boundaries.

- `.tmp/store-apk-analysis/README.md`
  - Local-only scratch directory rules and artifact index. Do not store secrets in this README.

## Files To Modify Later, After Analysis

Do not modify these during analysis tasks unless the evidence docs prove the required behavior:

- `android/app/src/main/java/com/aiusage/mobile/providers/ProviderWebCollectorScripts.kt`
- `android/app/src/main/java/com/aiusage/mobile/providers/WebLoginActivity.kt`
- `android/app/src/main/java/com/aiusage/mobile/providers/ProviderUsageNormalizer.kt`
- `android/app/src/test/java/com/aiusage/mobile/providers/ProviderWebCollectorScriptsTest.kt`
- `android/app/src/test/java/com/aiusage/mobile/providers/ProviderUsageNormalizerTest.kt`

---

## Task 1: Rebuild Static APK Workspace

**Files:**
- Create: `.tmp/store-apk-analysis/README.md`
- Create: `docs/qa/store-apk-provider-map-2026-05-20.md`

- [ ] **Step 1: Create local scratch directory**

Run:

```powershell
New-Item -ItemType Directory -Force -Path ".tmp/store-apk-analysis" | Out-Null
```

Expected: `.tmp/store-apk-analysis` exists.

- [ ] **Step 2: Copy source APK into scratch directory**

Run:

```powershell
Copy-Item -LiteralPath "C:/Users/datell1357/Downloads/AI Usage_1.260505.7.apk" -Destination ".tmp/store-apk-analysis/store.apk" -Force
Get-FileHash ".tmp/store-apk-analysis/store.apk" -Algorithm SHA256
```

Expected: SHA-256 hash printed and recorded in `docs/qa/store-apk-provider-map-2026-05-20.md`.

- [ ] **Step 3: Extract APK resources/classes**

Run:

```powershell
Expand-Archive -LiteralPath ".tmp/store-apk-analysis/store.apk" -DestinationPath ".tmp/store-apk-analysis/unzip" -Force
```

Expected: `.tmp/store-apk-analysis/unzip/AndroidManifest.xml`, dex files, and assets exist.

- [ ] **Step 4: Decompile with JADX**

Run using the locally available JADX command or existing JADX path:

```powershell
jadx -d ".tmp/store-apk-analysis/jadx" ".tmp/store-apk-analysis/store.apk"
```

Expected: `.tmp/store-apk-analysis/jadx/sources` exists.

If `jadx` is unavailable, use the existing decompiled directory from prior analysis if present:

```powershell
Get-ChildItem ".tmp" -Directory | Where-Object { $_.Name -match "jadx|sage|store" }
```

Expected: a readable source directory is selected and recorded in the provider map.

- [ ] **Step 5: Write scratch README**

Create `.tmp/store-apk-analysis/README.md` with:

```markdown
# Store APK Analysis Scratch

Source APK: C:/Users/datell1357/Downloads/AI Usage_1.260505.7.apk
Local copy: .tmp/store-apk-analysis/store.apk

Rules:
- Do not commit this directory.
- Do not store raw cookies, tokens, OAuth codes, or account identifiers.
- Sanitized scripts may be referenced by hash and provider name.
- Provider findings must be copied into docs/qa before implementation.
```

- [ ] **Step 6: Initialize provider map doc**

Create `docs/qa/store-apk-provider-map-2026-05-20.md` with sections:

```markdown
# Store APK Provider Map

Date: 2026-05-20
Source APK: C:/Users/datell1357/Downloads/AI Usage_1.260505.7.apk
Local copy: .tmp/store-apk-analysis/store.apk
SHA-256: <recorded hash>

## Provider Inventory

| Provider | Login URL | Auth completion signal | Usage service | Script provider | Remote key | Fallback asset | DataStore | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## Class Evidence

## Resource Evidence

## Gaps Requiring Runtime Capture
```

Replace `<recorded hash>` with the actual hash from Step 2.

---

## Task 2: Static Provider Inventory

**Files:**
- Modify: `docs/qa/store-apk-provider-map-2026-05-20.md`

- [ ] **Step 1: Search provider labels and login URLs**

Run:

```powershell
rg -n "Claude|Copilot|Gemini|Codex|Cursor|Antigravity|claude.ai|github.com|gemini.google.com|chatgpt.com|cursor.com" ".tmp/store-apk-analysis/jadx"
```

Expected: provider-related classes and string constants appear.

- [ ] **Step 2: Search service names**

Run:

```powershell
rg -n "UsageService|Usage Monitor|usage_channel|start URL|about:blank" ".tmp/store-apk-analysis/jadx"
```

Expected: service classes for each supported provider are identified.

- [ ] **Step 3: Search script keys and fallback assets**

Run:

```powershell
rg -n "260503|Remote Config|script_data|assets/[a-e]|CryptoUtils|decrypt|AES|CBC" ".tmp/store-apk-analysis/jadx"
```

Expected: script provider classes and base script provider are identified.

- [ ] **Step 4: Search DataStore names**

Run:

```powershell
rg -n "account_data|usage_data|script_data|DataStore|Preferences" ".tmp/store-apk-analysis/jadx"
```

Expected: account, usage, and script storage names are mapped per provider.

- [ ] **Step 5: Fill provider inventory table**

For each supported provider, record:

```markdown
| Copilot | https://github.com/login | WebView session + collector payload | <class> | yh0 | b260503 | assets/b | script_data_copilot / usage_data_copilot / account_data_copilot | Needs runtime script capture for inline suggestions |
```

Expected: every provider row has at least one file/class evidence reference.

- [ ] **Step 6: Identify unsupported or ambiguous providers**

Record any provider found in strings but not supported by service/script/DataStore evidence under:

```markdown
## Unsupported Or Ambiguous Strings

| String | Evidence | Decision |
| --- | --- | --- |
```

Expected: Cursor and Android framework `Cursor` strings are not misclassified as provider support unless provider-specific evidence exists.

---

## Task 3: Script Provider And Decryption Path Map

**Files:**
- Modify: `docs/qa/store-apk-provider-map-2026-05-20.md`

- [ ] **Step 1: Trace base script provider**

Run:

```powershell
rg -n "class wr|CryptoUtils|FirebaseRemoteConfig|getString|fetchAndActivate|decode|Cipher|getInstance" ".tmp/store-apk-analysis/jadx/sources"
```

Expected: base class that fetches/decrypts remote or fallback scripts is identified.

- [ ] **Step 2: Trace provider script provider constructors**

Run:

```powershell
rg -n "a260503|b260503|c260503|d260503|e260503" ".tmp/store-apk-analysis/jadx/sources"
```

Expected: constructor signatures show provider-specific key, fallback asset, and provider tag.

- [ ] **Step 3: Trace JavaScript bridge names**

Run:

```powershell
rg -n "addJavascriptInterface|evaluateJavascript|loadUrl\\(\"javascript|SAGE1|SAGE10|SAGE11|SAGE12" ".tmp/store-apk-analysis/jadx/sources"
```

Expected: bridge names and receiving methods are mapped.

- [ ] **Step 4: Trace payload parser**

Run:

```powershell
rg -n "\"s\"|\"d\"|\"x\"|\"u\"|\"r\"|\"m\"|percent|reset|usage" ".tmp/store-apk-analysis/jadx/sources"
```

Expected: compact payload fields and parser classes are identified.

- [ ] **Step 5: Record decryption limits**

Add this decision to the provider map:

```markdown
## Script Decryption Findings

- Remote/fallback payload format:
  1. Base64 outer string.
  2. First 16 decoded bytes are AES IV.
  3. Remaining bytes are AES/CBC/PKCS5Padding ciphertext.
  4. AES key is returned by native method `CryptoUtils.r()`.
- Static decryption status:
  - If native key material is not recoverable statically, runtime hook capture is required.
```

Expected: future agents know why static analysis alone cannot recover collector JS.

---

## Task 4: Runtime Capture Runbook

**Files:**
- Create: `docs/qa/store-apk-runtime-capture-runbook-2026-05-20.md`

- [ ] **Step 1: Create runbook header**

Create:

```markdown
# Store APK Runtime Capture Runbook

Date: 2026-05-20
Goal: Capture actual decrypted provider collector scripts and usage payload behavior from the store APK.

## Device Setup
```

- [ ] **Step 2: Record emulator setup commands**

Add:

```powershell
& "C:/Users/datell1357/AppData/Local/Android/Sdk/platform-tools/adb.exe" devices
& "C:/Users/datell1357/AppData/Local/Android/Sdk/platform-tools/adb.exe" -s <device> install -r ".tmp/store-apk-analysis/store.apk"
& "C:/Users/datell1357/AppData/Local/Android/Sdk/platform-tools/adb.exe" -s <device> shell monkey -p u.sage 1
```

Expected: store APK launches on the selected emulator.

- [ ] **Step 3: Record log filters**

Add:

```powershell
& "C:/Users/datell1357/AppData/Local/Android/Sdk/platform-tools/adb.exe" -s <device> logcat -c
& "C:/Users/datell1357/AppData/Local/Android/Sdk/platform-tools/adb.exe" -s <device> logcat -v time | Select-String -Pattern "sage|SAGE|WebView|evaluateJavascript|RemoteConfig|Copilot|Claude|Gemini|Codex|usage|quota"
```

Expected: provider service and bridge logs are visible if the APK logs them.

- [ ] **Step 4: Define hook targets**

Add hook target table:

| Target | Purpose | Expected Evidence |
| --- | --- | --- |
| `android.webkit.WebView.evaluateJavascript` | Capture injected JS collector text | provider script hash and sanitized script |
| `android.webkit.WebView.loadUrl` where URL starts `javascript:` | Capture injected JS fallback | provider script hash and sanitized script |
| `android.webkit.WebView.addJavascriptInterface` | Capture bridge names and object classes | `SAGE*` bridge map |
| Base script provider return method | Capture decrypted script before WebView injection | raw provider collector script |
| Payload bridge receiver | Capture compact `s/d/x/u/r/t/m` payload shape | normalized usage evidence |

- [ ] **Step 5: Define capture redaction rules**

Add:

```markdown
## Redaction Rules

- Replace emails with `<email>`.
- Replace usernames with `<username>` when not needed for endpoint shape.
- Replace tokens, cookies, OAuth codes, auth headers, and session ids with `<secret>`.
- Keep endpoint path, HTTP status, JSON key names, numeric quota values, reset field names, and provider labels.
- Store raw captures only in local `.tmp/store-apk-analysis/captures`.
- Copy only sanitized snippets into `docs/qa`.
```

Expected: runtime evidence is useful without exposing credentials.

- [ ] **Step 6: Define provider manual login checklist**

Add one checklist per provider:

```markdown
## Provider Manual Capture Checklist

### Copilot

- [ ] Clear store APK app data when testing first-run login.
- [ ] Tap Copilot connect in store APK.
- [ ] Complete GitHub login and app/2FA verification manually.
- [ ] Wait for app return or usage screen update.
- [ ] Capture injected script hash.
- [ ] Capture bridge payload keys.
- [ ] Capture endpoint paths and HTTP status only.
- [ ] Record whether rows include Chat, Inline suggestions, Premium requests.
```

Repeat for Claude, Gemini, Codex, and Antigravity if present in static inventory.

---

## Task 5: Runtime Collector Script Capture

**Files:**
- Modify: `docs/qa/store-apk-runtime-capture-runbook-2026-05-20.md`
- Modify: `docs/qa/store-apk-provider-evidence-2026-05-20.md`
- Create local-only sanitized script files under `.tmp/store-apk-analysis/captures`

- [ ] **Step 1: Install and launch store APK**

Run:

```powershell
& "C:/Users/datell1357/AppData/Local/Android/Sdk/platform-tools/adb.exe" -s <device> install -r ".tmp/store-apk-analysis/store.apk"
& "C:/Users/datell1357/AppData/Local/Android/Sdk/platform-tools/adb.exe" -s <device> shell monkey -p u.sage 1
```

Expected: store APK starts.

- [ ] **Step 2: Attach runtime hook**

Use Frida or equivalent hook tooling to attach to package `u.sage`.

Hook behavior:

```javascript
Java.perform(function () {
  var WebView = Java.use("android.webkit.WebView");
  WebView.evaluateJavascript.overload("java.lang.String", "android.webkit.ValueCallback").implementation = function (script, callback) {
    if (script && script.length > 200) {
      send({ type: "evaluateJavascript", length: script.length, preview: script.substring(0, 200) });
    }
    return this.evaluateJavascript(script, callback);
  };
});
```

Expected: script lengths and previews appear when provider collectors are injected.

- [ ] **Step 3: Capture script hash per provider**

For each provider, record:

```markdown
| Provider | Script key | Captured via | Length | SHA-256 | Contains endpoint clues | Notes |
| --- | --- | --- | ---: | --- | --- | --- |
```

Expected: every supported provider has a script hash or a documented reason capture failed.

- [ ] **Step 4: Capture bridge payload shape**

Hook payload receiver or JavaScript bridge methods and record sanitized payload:

```json
{
  "s": "s",
  "d": {
    "x": [
      {
        "l": "Chat messages",
        "u": 12,
        "r": 1780000000000,
        "t": "Resets Jun 15"
      }
    ]
  },
  "m": null
}
```

Expected: payload schema is confirmed per provider.

- [ ] **Step 5: Capture endpoint path inventory**

Record endpoint paths without auth headers:

```markdown
| Provider | Origin | Path | Method | Status | Response keys | Source |
| --- | --- | --- | --- | ---: | --- | --- |
```

Expected: endpoint paths used by the collector are known.

---

## Task 6: Provider Evidence Document

**Files:**
- Create: `docs/qa/store-apk-provider-evidence-2026-05-20.md`

- [ ] **Step 1: Create evidence doc**

Create:

```markdown
# Store APK Provider Evidence

Date: 2026-05-20
Source APK: C:/Users/datell1357/Downloads/AI Usage_1.260505.7.apk

## Evidence Quality Levels

- Static: class/resource/string evidence only.
- Runtime script: decrypted/injected collector script was captured.
- Runtime endpoint: provider endpoint path/status/keys were captured.
- Runtime snapshot: compact payload rows were captured and normalized.

## Provider Summary

| Provider | Login Flow | Auth Completion | Usage Collection | Evidence Level | Implementation Readiness |
| --- | --- | --- | --- | --- | --- |
```

- [ ] **Step 2: Add Copilot evidence section**

Use this exact section layout:

```markdown
## Copilot

### Login

### Authentication Completion

### Collector Script

### Usage Sources

### Normalized Rows

### Current App Gap

### Implementation Requirement
```

Expected: Copilot explains why WebView session alone produced Chat but not Inline suggestions.

- [ ] **Step 3: Add sections for Claude, Gemini, Codex, Antigravity, and Cursor decision**

Use the same section layout for each provider.

For Cursor, use:

```markdown
## Cursor Decision

Store APK support status: <supported / not supported>
Evidence:
- <class/string/resource evidence>
Decision:
- <whether current app should derive Cursor behavior from store APK or Windows plugin>
```

Expected: future work does not re-litigate Cursor support.

---

## Task 7: Parity Gap Tracker

**Files:**
- Create: `docs/qa/store-apk-parity-gap-tracker-2026-05-20.md`

- [ ] **Step 1: Create tracker**

Create:

```markdown
# Store APK Parity Gap Tracker

Date: 2026-05-20

## Priority Rules

1. Real usage data collection beats visual connected state.
2. Store APK login UX parity beats Firebase/Auth workaround UX unless token access is proven impossible.
3. Provider-specific collector parity beats generic DOM scraping.
4. No fixed fallback usage values.

## Gaps

| Priority | Provider | Store APK Behavior | Current App Behavior | Evidence | Required Change | Verification |
| ---: | --- | --- | --- | --- | --- | --- |
```

- [ ] **Step 2: Add known Copilot gap**

Add:

```markdown
| 1 | Copilot | In-app WebView login and collector returns Chat plus Inline suggestions where available | Current Firebase OAuth can obtain token but opens Chrome/Custom Tab; prior WebView path returned Chat only | `b260503` script key and runtime capture needed | Recover or recreate collector path for completions without fixed values | Fresh login shows Chat and Inline suggestions from real payload |
```

- [ ] **Step 3: Add provider rows after runtime evidence**

For each provider, add one row per missing behavior:

- login start URL mismatch
- auth completion heuristic mismatch
- collector endpoint mismatch
- missing quota bucket
- reset time mismatch
- wrong background refresh lifecycle

Expected: every implementation task later traces to a documented parity gap.

---

## Task 8: Context Handoff Document

**Files:**
- Create: `docs/qa/store-apk-context-handoff-2026-05-20.md`

- [ ] **Step 1: Create handoff header**

Create:

```markdown
# Store APK Analysis Context Handoff

Date: 2026-05-20

## Current Objective

Analyze the store APK provider login/auth/usage collection mechanisms and use that evidence to implement provider parity in the Android app.
```

- [ ] **Step 2: Add canonical paths**

Add:

```markdown
## Canonical Paths

- Store APK: `C:/Users/datell1357/Downloads/AI Usage_1.260505.7.apk`
- Scratch root: `.tmp/store-apk-analysis`
- Existing static analysis: `docs/qa/sage-apk-usage-collection-analysis-2026-05-17.md`
- Provider map: `docs/qa/store-apk-provider-map-2026-05-20.md`
- Runtime runbook: `docs/qa/store-apk-runtime-capture-runbook-2026-05-20.md`
- Provider evidence: `docs/qa/store-apk-provider-evidence-2026-05-20.md`
- Parity gaps: `docs/qa/store-apk-parity-gap-tracker-2026-05-20.md`
```

- [ ] **Step 3: Add known facts**

Add:

```markdown
## Known Facts

- Store APK package is `u.sage`.
- Store APK uses WebView sessions and provider-specific injected JavaScript collectors.
- Remote Config script keys observed in prior static analysis:
  - Claude: `a260503`
  - Copilot: `b260503`
  - Antigravity: `c260503`
  - Gemini: `d260503`
  - Codex: `e260503`
- Compact payload rows use fields like `l`, `u`, `s`, `r`, and `t`.
- Hardcoded sample values in the store APK are preview data and must not be treated as live usage.
```

- [ ] **Step 4: Add next-action pointer**

Add:

```markdown
## Next Action

Start with Task 1 in `docs/superpowers/plans/2026-05-20-store-apk-provider-reverse-engineering.md`, then complete Task 2 before any implementation changes.
```

Expected: after context compaction, a future agent can resume without asking the user to repeat the APK path or current goal.

---

## Task 9: Implementation Handoff Gate

**Files:**
- Modify: `docs/qa/store-apk-provider-evidence-2026-05-20.md`
- Modify: `docs/qa/store-apk-parity-gap-tracker-2026-05-20.md`

- [ ] **Step 1: Define readiness checklist**

Add to provider evidence doc:

```markdown
## Implementation Readiness Checklist

For a provider to move from analysis to implementation:

- [ ] Login URL and in-app WebView behavior confirmed.
- [ ] Auth completion signal confirmed.
- [ ] Collector trigger point confirmed.
- [ ] Usage source endpoint/state/DOM path confirmed.
- [ ] Normalized row labels confirmed.
- [ ] Reset timestamp/text mapping confirmed.
- [ ] Failure state behavior confirmed.
- [ ] Test payload captured or synthesized from real key shape without private values.
```

- [ ] **Step 2: Mark provider readiness**

Add:

```markdown
| Provider | Ready To Implement | Missing Evidence |
| --- | --- | --- |
```

Expected: implementation starts only for providers with enough evidence.

- [ ] **Step 3: Create implementation task list after evidence**

Only after the readiness checklist is satisfied, add provider-specific implementation tasks to a separate plan:

```text
docs/superpowers/plans/YYYY-MM-DD-store-apk-provider-parity-implementation.md
```

Expected: analysis and production code changes stay separated.

---

## Context-Compaction Protection Docs

Prepare these documents before implementation:

1. `docs/qa/store-apk-context-handoff-2026-05-20.md`
   - Most important compaction guard.
   - Contains current objective, canonical paths, known keys, source APK, and next action.

2. `docs/qa/store-apk-provider-map-2026-05-20.md`
   - Prevents redoing static analysis.
   - Keeps provider class/script/DataStore mapping in one place.

3. `docs/qa/store-apk-provider-evidence-2026-05-20.md`
   - Prevents “connected means fixed” confusion.
   - Separates login, auth, collector, endpoint, payload, and normalized rows.

4. `docs/qa/store-apk-parity-gap-tracker-2026-05-20.md`
   - Prevents accidental broad rewrites.
   - Every code change must point to one gap row.

5. `docs/qa/store-apk-runtime-capture-runbook-2026-05-20.md`
   - Prevents tool/command loss after compaction.
   - Records exactly how runtime evidence is captured.

6. Provider-specific implementation plans later:
   - `docs/superpowers/plans/YYYY-MM-DD-copilot-store-apk-parity.md`
   - `docs/superpowers/plans/YYYY-MM-DD-codex-store-apk-parity.md`
   - `docs/superpowers/plans/YYYY-MM-DD-gemini-store-apk-parity.md`
   - `docs/superpowers/plans/YYYY-MM-DD-claude-store-apk-parity.md`

Each provider plan must include:

- exact store APK evidence references,
- exact current app files to modify,
- failing tests first,
- real payload key examples with private values removed,
- emulator verification steps.

---

## Verification Commands For This Plan

After creating or updating the docs, run:

```powershell
Test-Path "docs/superpowers/plans/2026-05-20-store-apk-provider-reverse-engineering.md"
Test-Path "docs/qa/sage-apk-usage-collection-analysis-2026-05-17.md"
```

Expected:

```text
True
True
```

Before implementing provider parity, run:

```powershell
git diff -- docs/qa docs/superpowers/plans
```

Expected: only analysis/planning docs changed.

---

## Self-Review

- Spec coverage: This plan covers provider login, authentication completion, usage collection source discovery, collector script recovery, normalization evidence, and context-compaction protection.
- Placeholder scan: No implementation step depends on fixed fallback usage values or unspecified provider behavior.
- Type consistency: Provider readiness, parity gaps, and runtime evidence use the same provider names and evidence levels across all planned docs.
