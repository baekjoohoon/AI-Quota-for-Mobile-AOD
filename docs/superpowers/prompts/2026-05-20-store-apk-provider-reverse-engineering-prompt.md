# Store APK Provider Reverse Engineering Prompt

Use this prompt to resume or start the full store APK provider analysis after context compaction.

```text
You are working in `D:\Vibe Project\AI Usage for Mobile`.

Use only the essential MCP/tooling requested by the user: Lean Ctx, CodeGraph, Agentmemory, and Context Mode. Use shell/adb through lean-ctx `ctx_shell` where possible. Do not use screenshots unless they are strictly necessary.

Use Korean for user-facing updates and final responses.

Objective:
Analyze the published store APK so the current Android app can reproduce each supported provider's login, authentication, usage collection, and normalization behavior without hardcoded or fake fallback usage values.

Source APK:
`C:/Users/datell1357/Downloads/AI Usage_1.260505.7.apk`

Canonical documents:
- Spec: `docs/superpowers/specs/2026-05-20-store-apk-provider-reverse-engineering-spec.md`
- Plan: `docs/superpowers/plans/2026-05-20-store-apk-provider-reverse-engineering.md`
- Existing prior analysis: `docs/qa/sage-apk-usage-collection-analysis-2026-05-17.md`

Before doing any code changes:
1. Read the spec and plan.
2. Create or update these docs:
   - `docs/qa/store-apk-context-handoff-2026-05-20.md`
   - `docs/qa/store-apk-analysis-journal-2026-05-20.md`
   - `docs/qa/store-apk-decision-log-2026-05-20.md`
   - `docs/qa/store-apk-provider-map-2026-05-20.md`
   - `docs/qa/store-apk-runtime-capture-runbook-2026-05-20.md`
   - `docs/qa/store-apk-capture-index-2026-05-20.md`
   - `docs/qa/store-apk-provider-evidence-2026-05-20.md`
   - `docs/qa/store-apk-parity-gap-tracker-2026-05-20.md`
3. Update `store-apk-analysis-journal` after every meaningful scan, hook, failure, or conclusion.
4. Update `store-apk-context-handoff` whenever the next action changes.

Known facts from prior analysis:
- Store APK package: `u.sage`.
- Store APK uses in-app Android WebView sessions and provider-specific injected JavaScript collectors.
- Remote Config script keys:
  - Claude: `a260503`
  - Copilot: `b260503`
  - Antigravity: `c260503`
  - Gemini: `d260503`
  - Codex: `e260503`
- Fallback assets:
  - Claude: `assets/a`
  - Copilot: `assets/b`
  - Antigravity: `assets/c`
  - Gemini: `assets/d`
  - Codex: `assets/e`
- Compact usage payload rows use fields like `l`, `u`, `s`, `r`, and `t`.
- Hardcoded preview/sample values in the store APK are not live usage data.
- Cursor must not be treated as store APK supported unless provider-specific evidence proves it.

Important current product issue:
Copilot must match store APK UX. Firebase Auth GitHub OAuth opens Chrome/Custom Tab, so it is not store APK parity. The store APK uses in-app WebView login and a `b260503` collector. The missing piece is the actual collector logic that returns inline suggestions/completions usage.

Execution order:
1. Rebuild `.tmp/store-apk-analysis` from the source APK.
2. Decompile or locate existing JADX output.
3. Fill `store-apk-provider-map` with static provider inventory.
4. Trace Remote Config/fallback script/decryption path.
5. Build runtime capture runbook and hook targets.
6. Capture Copilot collector first.
7. Capture Codex, Gemini, Claude, and Antigravity if supported.
8. Decide Cursor support status.
9. Fill provider evidence and parity gap tracker.
10. Only after evidence is sufficient, write a provider-specific implementation plan. Do not directly modify provider code during analysis.

Required redaction:
- Never write raw tokens, cookies, OAuth codes, refresh tokens, auth headers, account ids, or full provider HTML into docs.
- Redact emails as `<email>`.
- Redact usernames as `<username>` unless the username itself is required to prove a URL shape.
- Keep endpoint paths, status codes, JSON key names, quota numeric shapes, reset field names, and row labels.

Provider readiness gate:
A provider is implementation-ready only when all are true:
- login flow confirmed,
- auth completion signal confirmed,
- collector trigger confirmed,
- usage source confirmed,
- compact payload keys confirmed,
- normalized row labels confirmed,
- reset mapping confirmed,
- failure behavior understood,
- sanitized test payload can be created.

Start now:
Open the spec and plan, then create the missing docs skeletons. Record the first journal entry as "Analysis kickoff".
```

