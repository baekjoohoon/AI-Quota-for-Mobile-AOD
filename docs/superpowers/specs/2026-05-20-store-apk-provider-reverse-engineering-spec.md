# Store APK Provider Reverse Engineering Spec

Date: 2026-05-20

## Objective

Reverse-engineer the published store APK provider behavior so this app can reproduce provider login, authentication completion, usage collection, normalization, dashboard display, refresh, and widget behavior without hardcoded quota values or fake fallback rows.

Source APK:

`C:/Users/datell1357/Downloads/AI Usage_1.260505.7.apk`

Primary implementation plan:

`docs/superpowers/plans/2026-05-20-store-apk-provider-reverse-engineering.md`

Existing prior analysis:

`docs/qa/sage-apk-usage-collection-analysis-2026-05-17.md`

## Background

The current app can show connected states and some provider rows, but multiple providers still diverge from the store APK behavior. Copilot is the clearest example:

- Store APK uses in-app WebView login.
- Store APK uses a provider-specific script provider and remote/fallback collector script.
- Store APK appears to collect both chat messages and inline suggestions where available.
- Current Firebase GitHub OAuth workaround opens Chrome/Custom Tab and is not store APK UX parity.
- Current WebView-only path has captured chat usage but not inline suggestions.

The store APK collector logic is expected to live in encrypted/remote JavaScript collectors, not in plain Kotlin/Java classes. Therefore static APK analysis is necessary but not sufficient; runtime capture is required.

## Scope

Analyze all providers supported by the store APK.

Known candidates from prior static analysis:

| Provider | Remote key | Fallback asset | Known purpose |
| --- | --- | --- | --- |
| Claude | `a260503` | `assets/a` | Claude WebView session collector |
| Copilot | `b260503` | `assets/b` | GitHub Copilot WebView session collector |
| Antigravity | `c260503` | `assets/c` | Store APK candidate provider |
| Gemini | `d260503` | `assets/d` | Gemini WebView session collector |
| Codex | `e260503` | `assets/e` | ChatGPT/Codex WebView session collector |

Cursor must be treated as unsupported until store APK evidence proves otherwise. Android framework `Cursor` strings are not provider evidence.

## Non-Goals

- Do not implement provider parity before evidence is captured.
- Do not add hardcoded usage rows.
- Do not use sample/preview values from the store APK as live values.
- Do not change unrelated provider code while analyzing one provider.
- Do not commit raw scripts or payloads containing tokens, cookies, email addresses, OAuth codes, account ids, or session ids.
- Do not treat login success as usage collection success.

## Required Deliverables

Create and maintain these analysis documents:

1. `docs/qa/store-apk-context-handoff-2026-05-20.md`
   - Compact resume document for context compaction.
   - Must include source APK path, scratch path, known script keys, current next action, and analysis status.

2. `docs/qa/store-apk-analysis-journal-2026-05-20.md`
   - Chronological work log.
   - Must be updated after every meaningful scan, runtime hook, failure, or conclusion.

3. `docs/qa/store-apk-decision-log-2026-05-20.md`
   - Durable decisions and why they were made.
   - Must include rejected paths such as Firebase Auth when they conflict with store APK parity.

4. `docs/qa/store-apk-provider-map-2026-05-20.md`
   - Static provider inventory.
   - Must map provider, login URL, usage service, script provider, remote key, fallback asset, DataStore, and evidence references.

5. `docs/qa/store-apk-runtime-capture-runbook-2026-05-20.md`
   - Exact runtime capture commands and hook targets.
   - Must explain redaction rules.

6. `docs/qa/store-apk-capture-index-2026-05-20.md`
   - Index of captured artifacts.
   - Must include provider, capture method, sanitized path, local raw path, SHA-256, and conclusion supported.

7. `docs/qa/store-apk-provider-evidence-2026-05-20.md`
   - Provider-by-provider evidence.
   - Must separate login, auth completion, collector injection, endpoint/source, compact payload, normalized rows, and current app gap.

8. `docs/qa/store-apk-parity-gap-tracker-2026-05-20.md`
   - Current app vs store APK delta.
   - Every future code change must point to one row in this tracker.

## Required Scratch Structure

Use local scratch only for extracted APK and runtime captures:

```text
.tmp/store-apk-analysis/
  store.apk
  unzip/
  jadx/
  captures/
    raw/
    sanitized/
  README.md
```

Scratch directory rules:

- Do not commit `.tmp/store-apk-analysis`.
- Raw captures may exist locally only.
- Sanitized conclusions must be copied into `docs/qa`.
- Do not store secrets in docs.

## Analysis Method

### Phase 1: Static Provider Map

For each provider, identify:

- login start URL,
- allowed/auth hosts,
- WebView service/activity,
- usage service,
- script provider class,
- Remote Config key,
- fallback asset,
- script DataStore,
- account DataStore,
- usage DataStore,
- JavaScript bridge names,
- compact payload parser,
- normalizer row labels.

Minimum commands:

```powershell
rg -n "Claude|Copilot|Gemini|Codex|Cursor|Antigravity|claude.ai|github.com|gemini.google.com|chatgpt.com|cursor.com" ".tmp/store-apk-analysis/jadx"
rg -n "UsageService|Usage Monitor|usage_channel|about:blank" ".tmp/store-apk-analysis/jadx"
rg -n "a260503|b260503|c260503|d260503|e260503|script_data|account_data|usage_data" ".tmp/store-apk-analysis/jadx"
rg -n "addJavascriptInterface|evaluateJavascript|loadUrl\\(\"javascript|SAGE1|SAGE10|SAGE11|SAGE12" ".tmp/store-apk-analysis/jadx"
```

### Phase 2: Script Fetch/Decrypt Path

Trace:

- Firebase Remote Config fetch,
- fallback asset read,
- base64 decode,
- AES IV/ciphertext split,
- native key source,
- decrypted script return method,
- WebView injection method.

If native key is unavailable statically, move to runtime hook capture.

### Phase 3: Runtime Script Capture

Install and run the store APK.

Capture:

- `WebView.evaluateJavascript(...)`,
- `WebView.loadUrl("javascript:...")`,
- `WebView.addJavascriptInterface(...)`,
- base script provider decrypted return method if identifiable,
- bridge receiver payloads.

Each capture must record:

- provider,
- timestamp,
- method,
- script/payload length,
- SHA-256 hash,
- sanitized snippet,
- endpoint/path clues,
- conclusion supported.

### Phase 4: Provider Runtime Evidence

For each provider, manually test:

1. clean app state,
2. tap provider connect,
3. complete login/2FA/auth manually,
4. observe return-to-app condition,
5. observe collector injection,
6. capture endpoint/source keys,
7. capture compact payload rows,
8. compare with current app.

Provider status must not be marked ready if only login/session detection is proven.

### Phase 5: Parity Implementation Gate

A provider can move to implementation only when all are true:

- login flow confirmed,
- auth completion signal confirmed,
- collector trigger confirmed,
- usage source confirmed,
- payload row keys confirmed,
- reset timestamp/text mapping confirmed,
- failure state behavior understood,
- sanitized test payload can be written.

## Evidence Quality Levels

Use these labels in docs:

- `static`: class/resource/string evidence only.
- `runtime-script`: decrypted or injected collector script captured.
- `runtime-endpoint`: endpoint path/status/response keys captured.
- `runtime-payload`: compact bridge payload captured.
- `runtime-snapshot`: normalized real usage rows verified in store APK.
- `implementation-ready`: enough evidence exists to write tests and code.

## Failure Taxonomy

Use these exact failure names:

- `login_not_started`
- `auth_redirect_blocked`
- `oauth_custom_tab_mismatch`
- `session_detected_only`
- `collector_not_injected`
- `collector_payload_empty`
- `script_not_decrypted`
- `endpoint_unauthorized`
- `quota_bucket_missing`
- `normalizer_dropped_line`
- `snapshot_not_saved`
- `sample_value_misclassified`

## Documentation Rules

Every analysis journal entry must use this format:

```markdown
## YYYY-MM-DD HH:mm - <short title>

Action:
- <what was done>

Result:
- <what was found>

Evidence:
- Command: `<command>`
- File/Class: `<path or class>`
- Output summary: <short summary>

Decision/Next:
- <decision or next action>
```

Every decision log entry must use this format:

```markdown
## Decision: <title>

Date: YYYY-MM-DD

Context:
- <why this decision came up>

Decision:
- <what is decided>

Reason:
- <evidence-backed reason>

Impact:
- <what this changes>

Revisit When:
- <condition to revisit>
```

## Success Criteria

The analysis phase is complete when:

- every store APK provider has a row in `store-apk-provider-map`,
- every provider has a section in `store-apk-provider-evidence`,
- every captured script/payload has an entry in `store-apk-capture-index`,
- every current app mismatch has a row in `store-apk-parity-gap-tracker`,
- `store-apk-context-handoff` identifies the next implementation plan,
- no future agent needs the user to repeat APK path, provider scope, or known script keys.

The implementation phase must not begin until the analysis phase satisfies the provider readiness gate for at least one provider.

## Preferred Execution Order

1. Create documentation skeletons.
2. Rebuild static APK workspace.
3. Complete static provider inventory.
4. Complete script/decrypt path analysis.
5. Prepare runtime capture tooling.
6. Capture Copilot first because it currently blocks inline suggestions.
7. Capture Codex/Gemini/Claude next.
8. Decide Antigravity and Cursor support status.
9. Write provider-specific implementation plans.

