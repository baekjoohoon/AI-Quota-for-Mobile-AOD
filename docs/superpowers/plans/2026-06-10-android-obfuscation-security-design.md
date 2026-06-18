# Android Stable Obfuscation Security Design Plan

> For agentic workers: use `omo:start-work` or `omo:ulw-loop` to execute this plan task-by-task. Do not edit production code before the RED test for that task is captured.

Date: 2026-06-10

## TL;DR

> **Summary**: Tighten release R8 rules for stable name obfuscation while keeping shrink and optimize disabled. Preserve Android/runtime entry points and JavaScript bridge method names, then prove the release AAB still contains the required manifest surface while internal provider/helper names are obfuscated.
> **Deliverables**:
> - Short spec at `docs/superpowers/specs/2026-06-10-android-obfuscation-security-design.md`
> - Conservative update to `android/app/proguard-rules.pro`
> - Static tests for R8 guardrails, JavaScript bridge preservation, and no accidental JS asset minification
> - Sensitive diagnostic/redaction regression coverage
> - Release AAB, manifest, and `mapping.txt` evidence
> **Effort**: Medium
> **Parallel**: YES - 2 implementation waves after the spec/test foundation
> **Critical Path**: Task 1 -> Task 2 -> Task 3 -> Task 5 -> Task 6

## Context

### Original Request

Plan the implementation for the user's "stability-first obfuscation" design:

- Keep `-dontshrink` and `-dontoptimize`, or change them only after separate high-risk validation.
- Reduce release class-name exposure without enabling shrink/optimize.
- Narrow broad `-keep class` rules for Activity/Service/Receiver entry points so internal members/helpers can still be obfuscated.
- Explicitly preserve JavaScript bridge methods such as `postUsagePayload`, `fetchCopilotJson`, and `fetchCopilotJsonWithAuthorization`.
- Keep Android Keystore + AES/GCM token storage through `SecureStringStore`.
- Add tests that tokens and sensitive values do not appear in logs/diagnostics.
- Exclude `gemini_collector.js` and `antigravity_collector.js` string/asset obfuscation from this phase.
- Verify release AAB generation, manifest entry points, JS bridge methods, Firebase/AppCheck-sensitive paths, provider collectors, dashboard, and widget regressions.

### Repository Findings

- `android/app/build.gradle.kts:117-135` already has `release { isMinifyEnabled = true; isShrinkResources = false; proguardFiles(...) }`.
- `android/app/proguard-rules.pro:8-22` currently keeps `-dontshrink`, `-dontoptimize`, broad app entrypoint classes, `ForegroundRefreshHealthWorker` constructor, and all `@JavascriptInterface` methods.
- `android/app/src/main/AndroidManifest.xml:7-125` contains runtime entry points that must remain resolvable by Android: application, activities, services, receivers, and widget providers.
- JavaScript bridge methods are defined in:
  - `android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt:394-474`
  - `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:564-636`
  - `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt:315-381`
- JavaScript bridge call sites are in `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt`, including `AIQuotaCollectorBridge.postUsagePayload`, `postCollectorError`, `fetchCopilotJson`, `fetchCopilotJsonWithAuthorization`, and `fetchCursorJson`.
- Sensitive storage is already handled by `android/app/src/main/java/com/aiquota/mobile/providers/SecureStringStore.kt` using Android Keystore and AES/GCM.
- Existing relevant tests include provider collector/normalizer tests, Firebase gateway tests, dashboard reorder tests, widget tests, `test/mobile-source.test.js`, and `android/app/src/test/java/com/aiquota/mobile/support/BugReportEmailComposerTest.kt`.
- This checkout has no Gradle wrapper and `gradle` is not currently on PATH in Git Bash or PowerShell. Release AAB verification requires a Gradle CLI/Android Studio Gradle environment gate before implementation can be marked complete.

### Metis Review

Metis subagent was unavailable in this harness. Self-review gap analysis:

- **Critical risk**: Broad entrypoint keep rules can be narrowed too far and break Android class loading. Mitigation: first add tests that enumerate manifest/debug entrypoints and required WorkManager constructors, then only narrow rules against that list.
- **Critical risk**: `@JavascriptInterface` member names are string-called from JS. Mitigation: preserve annotated methods and add a release mapping check that these exact method names are not obfuscated.
- **Critical risk**: Release AAB verification can be blocked by missing Gradle CLI. Mitigation: make environment preflight Task 2 acceptance criteria; do not claim implementation complete without `:app:bundleRelease` evidence.
- **Scope creep risk**: JS asset minification/encoding and shrink/optimize are tempting security hardening steps. Mitigation: explicitly forbid them in all tasks and tests.

## Work Objectives

### Core Objective

Implement stable release obfuscation hardening that reduces internal name exposure without changing runtime behavior or enabling shrink/optimize.

### Definition of Done

All of the following must be true:

- `docs/superpowers/specs/2026-06-10-android-obfuscation-security-design.md` exists and states the exact in/out scope.
- A RED test is captured before each production/config change.
- `android/app/proguard-rules.pro` keeps `-dontshrink` and `-dontoptimize`.
- Broad Activity/Service/Receiver keep rules are replaced by narrower entrypoint/constructor rules that allow internal names/members to be obfuscated where safe.
- JavaScript bridge method names remain preserved in release mapping.
- `gemini_collector.js` and `antigravity_collector.js` are not minified, encoded, moved, renamed, or otherwise transformed in this phase.
- Sensitive diagnostics tests prove token/cookie/authorization/code values are redacted or represented only as booleans/status.
- Release AAB is generated.
- Manifest entry points and widget/AppCheck/Firebase-sensitive paths are verified against the release artifact.
- `mapping.txt` evidence shows at least one internal provider/helper class is obfuscated and required bridge methods remain unobfuscated.

### Must NOT Have

- Do not enable shrink.
- Do not enable optimize.
- Do not enable resource shrinking.
- Do not obfuscate, minify, encode, rename, or bundle-transform provider asset JS in this phase.
- Do not remove Android/Firebase/WorkManager/Glance/OAuth keep attributes without a RED test proving it is safe.
- Do not log raw tokens, cookies, authorization headers, OAuth codes, account emails, or raw provider HTML.
- Do not reclassify provider stale/unavailable usage as auth failure without explicit auth evidence.

## Verification Strategy

> ZERO HUMAN INTERVENTION - all verification is agent-executed after environment preflight passes.

- Test decision: TDD for each config/production change.
- Main static test entry: `node test/mobile-source.test.js`
- Android unit test entry after Gradle preflight: `gradle -p android :app:testReleaseUnitTest`
- Release artifact entry after Gradle preflight: `gradle -p android :app:bundleRelease`
- Evidence directory: `.omo/ulw-loop/evidence/android-obfuscation-20260610/`
- Manual QA channel for CLI/data-shaped criteria: tmux transcript with exact command, stdout/stderr, and exit code.
- Manual QA channel for release artifact criteria: tmux transcript plus copied manifest/mapping snippets.

## Execution Strategy

### Parallel Execution Waves

- Wave 1: Task 1 and Task 2 in sequence only. These establish spec, environment, baseline RED tests, and artifact commands.
- Wave 2: Task 3 and Task 4 can run in parallel after Task 2.
- Wave 3: Task 5 depends on Task 3 and Task 4.
- Wave 4: Task 6 is final verification after all implementation tasks.

### Dependency Matrix

| Task | Blocks | Blocked By |
| --- | --- | --- |
| 1. Spec and scope lock | 2, 3, 4, 5, 6 | none |
| 2. Test and environment foundation | 3, 4, 5, 6 | 1 |
| 3. R8 rule narrowing | 5, 6 | 1, 2 |
| 4. Sensitive diagnostic guardrails | 5, 6 | 1, 2 |
| 5. Release AAB and mapping verification | 6 | 3, 4 |
| 6. Final regression and handoff | none | 5 |

## TODOs

- [ ] 1. Create the Short Spec

  **What to do**:
  - Create `docs/superpowers/specs/2026-06-10-android-obfuscation-security-design.md`.
  - Include objective, in scope, out of scope, current R8 state, required keep surfaces, test strategy, release verification strategy, and explicit rollback criteria.
  - Reference this plan from the spec.

  **Must NOT do**:
  - Do not edit `android/app/proguard-rules.pro`.
  - Do not edit Kotlin/JS source.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 2, 3, 4, 5, 6 | Blocked By: none

  **References**:
  - Pattern: `docs/superpowers/specs/2026-05-29-antigravity-firebase-token-gateway-spec.md` - existing spec style and context-protection format.
  - Plan: `docs/superpowers/plans/2026-06-10-android-obfuscation-security-design.md` - this execution plan.

  **Acceptance Criteria**:
  - [ ] `test -f docs/superpowers/specs/2026-06-10-android-obfuscation-security-design.md`
  - [ ] Spec contains exact strings: `-dontshrink`, `-dontoptimize`, `AIQuotaCollectorBridge`, `mapping.txt`, `release AAB`, `out of scope`.

  **QA Scenarios**:
  ```text
  Scenario: Spec exists and names the fixed scope
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-spec "grep -E -- '-dontshrink|-dontoptimize|AIQuotaCollectorBridge|mapping.txt|release AAB|out of scope' docs/superpowers/specs/2026-06-10-android-obfuscation-security-design.md; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-spec -S -200
      tmux kill-session -t ulw-qa-obfuscation-spec
    Expected: EXIT:0 and every required phrase appears.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-1-spec.txt

  Scenario: Spec has not executed implementation
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-spec-scope "git diff --name-only -- android/app/proguard-rules.pro android/app/src/main android/app/src/test test/mobile-source.test.js; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-spec-scope -S -200
      tmux kill-session -t ulw-qa-obfuscation-spec-scope
    Expected: Before Task 2 starts, no production/test implementation files are changed by Task 1.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-1-no-implementation.txt
  ```

  **Commit**: YES | Message: `docs(android): plan stable release obfuscation` | Files: `docs/superpowers/specs/2026-06-10-android-obfuscation-security-design.md`, `docs/superpowers/plans/2026-06-10-android-obfuscation-security-design.md`

- [ ] 2. Add RED Static Tests and Environment Preflight

  **What to do**:
  - Add tests to `test/mobile-source.test.js` before changing `proguard-rules.pro`.
  - Tests must fail against the current broad keep rules.
  - Required test IDs:
    - `release R8 keeps shrink and optimize disabled while narrowing Android entrypoint keep rules`
    - `release R8 preserves JavaScript bridge method names used by collector scripts`
    - `release obfuscation phase does not transform provider asset JavaScript`
  - The first test must assert:
    - `-dontshrink` exists.
    - `-dontoptimize` exists.
    - No broad rules remain for `com.aiquota.mobile.** extends android.app.Activity`, `android.app.Service`, `android.content.BroadcastReceiver`, `android.appwidget.AppWidgetProvider`, or `androidx.glance.appwidget.GlanceAppWidgetReceiver`.
    - Manifest entrypoint classes are represented by explicit keep rules or targeted constructors.
  - The bridge test must assert that `@android.webkit.JavascriptInterface <methods>;` remains and that collector scripts still reference `AIQuotaCollectorBridge.postUsagePayload`, `postCollectorError`, `fetchCopilotJson`, `fetchCopilotJsonWithAuthorization`, and `fetchCursorJson`.
  - The asset JS test must assert `android/app/src/main/assets/gemini_collector.js` and `android/app/src/main/assets/antigravity_collector.js` are not added to any minification/encoding pipeline in Gradle or custom scripts.
  - Add a preflight note in the spec that this checkout currently has no `gradlew` and no `gradle` on PATH; implementation must install/provide Gradle CLI before Task 5.

  **Must NOT do**:
  - Do not make tests pass by weakening assertions.
  - Do not touch `proguard-rules.pro` in this task.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 3, 4, 5, 6 | Blocked By: 1

  **References**:
  - Static test file: `test/mobile-source.test.js`
  - Current rules: `android/app/proguard-rules.pro:8-22`
  - Release config: `android/app/build.gradle.kts:117-135`
  - Manifest entries: `android/app/src/main/AndroidManifest.xml:7-125`
  - JS bridge call sites: `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt`

  **Acceptance Criteria**:
  - [ ] RED captured: `node --test test/mobile-source.test.js --test-name-pattern "release R8 keeps shrink and optimize disabled while narrowing Android entrypoint keep rules"` fails before `proguard-rules.pro` changes.
  - [ ] RED captured: `node --test test/mobile-source.test.js --test-name-pattern "release R8 preserves JavaScript bridge method names used by collector scripts"` passes or fails only for a real missing guard, not because the test is malformed.
  - [ ] RED captured or baseline pass captured for asset JS guard: `node --test test/mobile-source.test.js --test-name-pattern "release obfuscation phase does not transform provider asset JavaScript"`.
  - [ ] `gradle -p android tasks --all` preflight output is captured; if command is missing, evidence records the exact `command not found` blocker and Task 5 is not attempted.

  **QA Scenarios**:
  ```text
  Scenario: Broad R8 keep rules are caught before implementation
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-red "node --test test/mobile-source.test.js --test-name-pattern 'release R8 keeps shrink and optimize disabled while narrowing Android entrypoint keep rules'; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-red -S -300
      tmux kill-session -t ulw-qa-obfuscation-red
    Expected: EXIT is nonzero before Task 3, with an assertion naming the broad keep rule.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-2-r8-red.txt

  Scenario: Build environment is explicitly known
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-gradle "gradle -p android tasks --all; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-gradle -S -300
      tmux kill-session -t ulw-qa-obfuscation-gradle
    Expected: EXIT:0 with Gradle tasks, or exact environment blocker captured. If blocked, stop before release AAB verification.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-2-gradle-preflight.txt
  ```

  **Commit**: YES | Message: `test(android): lock stable obfuscation guardrails` | Files: `test/mobile-source.test.js`, spec progress update if added

- [ ] 3. Narrow R8 Rules Conservatively

  **What to do**:
  - Modify `android/app/proguard-rules.pro` only after Task 2 RED evidence exists.
  - Keep these lines:
    - `-dontshrink`
    - `-dontoptimize`
    - `-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod`
    - `-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }`
  - Replace broad rules such as `-keep class com.aiquota.mobile.** extends android.app.Activity` with explicit rules for manifest/debug entrypoint classes:
    - `com.aiquota.mobile.AIQuotaApplication`
    - `com.aiquota.mobile.MainActivity`
    - `com.aiquota.mobile.providers.WebLoginActivity`
    - `com.aiquota.mobile.providers.GeminiCliLoopbackOAuthActivity`
    - `com.aiquota.mobile.providers.AntigravityLoopbackOAuthActivity`
    - `com.aiquota.mobile.providers.GoogleAuthorizationActivity`
    - `net.openid.appauth.RedirectUriReceiverActivity`
    - `com.aiquota.mobile.widget.ProviderWidgetConfigureActivity`
    - `com.aiquota.mobile.widget.DashboardWidgetConfigureActivity`
    - `com.aiquota.mobile.providers.ProviderUsageCollectionService`
    - `com.aiquota.mobile.providers.ProviderBackgroundRefreshService`
    - `com.aiquota.mobile.widget.WidgetManualRefreshReceiver`
    - `com.aiquota.mobile.providers.ProviderRefreshReceiver`
    - `com.aiquota.mobile.widget.AIQuotaUnifiedGlanceWidgetReceiver`
    - `com.aiquota.mobile.widget.AIQuotaCircularWidgetProvider`
    - `com.aiquota.mobile.widget.ProviderUsageWidgetProvider`
    - debug only: `com.aiquota.mobile.debug.LiveRefreshIssueDebugReceiver`
  - Preserve required no-arg constructors for Android-created classes with `{ public <init>(); }`.
  - Keep `ForegroundRefreshHealthWorker` constructor exactly as current: `{ public <init>(android.content.Context, androidx.work.WorkerParameters); }`.
  - Do not add `allowshrinking`; shrink is intentionally disabled.
  - Use `allowobfuscation` only where Android does not require the class binary name. For manifest/runtime entrypoint classes, do not allow class-name obfuscation.

  **Must NOT do**:
  - Do not add broad `com.aiquota.mobile.**` keep rules.
  - Do not remove `@JavascriptInterface` member preservation.
  - Do not enable shrink/optimize/resource shrinking.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 5, 6 | Blocked By: 1, 2

  **References**:
  - R8 rules: `android/app/proguard-rules.pro`
  - Manifest: `android/app/src/main/AndroidManifest.xml:7-125`
  - Debug manifest: `android/app/src/debug/AndroidManifest.xml:3-9`
  - WorkManager: `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshHealthWorker.kt`
  - Glance receivers: `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt`, `android/app/src/main/java/com/aiquota/mobile/widget/ProviderUsageGlanceWidget.kt`

  **Acceptance Criteria**:
  - [ ] GREEN captured: `node --test test/mobile-source.test.js --test-name-pattern "release R8 keeps shrink and optimize disabled while narrowing Android entrypoint keep rules"`
  - [ ] GREEN captured: `node --test test/mobile-source.test.js --test-name-pattern "release R8 preserves JavaScript bridge method names used by collector scripts"`
  - [ ] GREEN captured: `node --test test/mobile-source.test.js --test-name-pattern "release obfuscation phase does not transform provider asset JavaScript"`
  - [ ] `git diff -- android/app/proguard-rules.pro` shows no shrink/optimize/resource-shrink enablement.

  **QA Scenarios**:
  ```text
  Scenario: R8 rules are narrow and stable
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-r8-green "node --test test/mobile-source.test.js --test-name-pattern 'release R8 keeps shrink and optimize disabled while narrowing Android entrypoint keep rules'; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-r8-green -S -300
      tmux kill-session -t ulw-qa-obfuscation-r8-green
    Expected: EXIT:0 and assertion output confirms no broad keep rule remains.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-3-r8-green.txt

  Scenario: JavaScript bridge member names remain protected
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-bridge "node --test test/mobile-source.test.js --test-name-pattern 'release R8 preserves JavaScript bridge method names used by collector scripts'; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-bridge -S -300
      tmux kill-session -t ulw-qa-obfuscation-bridge
    Expected: EXIT:0 and required bridge method names are asserted.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-3-bridge-green.txt
  ```

  **Commit**: YES | Message: `build(android): narrow release obfuscation keeps` | Files: `android/app/proguard-rules.pro`, `test/mobile-source.test.js`

- [ ] 4. Strengthen Sensitive Diagnostic Redaction Tests

  **What to do**:
  - Add or extend tests before implementation changes in the smallest applicable file:
    - Prefer `android/app/src/test/java/com/aiquota/mobile/support/BugReportEmailComposerTest.kt` for bug-report diagnostic redaction.
    - Use focused provider tests only if a provider-specific diagnostic currently emits raw sensitive values.
  - Add test IDs:
    - `bug report diagnostics redact token cookie authorization bearer oauth code and email`
    - `provider diagnostics expose token presence only as booleans or typed statuses`
  - RED condition must be a concrete sensitive leak or missing regression guard. If current code already passes, record a characterization GREEN and do not change production code for that assertion.
  - If production code change is required, change only the redaction/sanitization function involved.

  **Must NOT do**:
  - Do not hide useful non-sensitive diagnostics such as provider id, typed error code, HTTP status, timeout type, selector/API-shape failure, or payload accepted/rejected.
  - Do not replace all diagnostics with generic messages.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 5, 6 | Blocked By: 1, 2

  **References**:
  - Secure store: `android/app/src/main/java/com/aiquota/mobile/providers/SecureStringStore.kt`
  - Bug report composer: `android/app/src/main/java/com/aiquota/mobile/support/BugReportEmailComposer.kt`
  - Existing redaction test: `android/app/src/test/java/com/aiquota/mobile/support/BugReportEmailComposerTest.kt`
  - Provider diagnostics: `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt`, `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt`, `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt`, `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt`

  **Acceptance Criteria**:
  - [ ] RED or characterization evidence captured for `BugReportEmailComposerTest`.
  - [ ] GREEN captured after any required production change.
  - [ ] `rg -n "(access_token=|refresh_token=|id_token=|Authorization:|Bearer [A-Za-z0-9]|code=[^r])" android/app/src/main/java android/app/src/test/java` yields no unredacted logging/diagnostic emission.
  - [ ] Tests still allow safe status messages such as `refreshToken=true`, `access_token=true`, or typed diagnostics, not raw values.

  **QA Scenarios**:
  ```text
  Scenario: Bug report redaction removes sensitive values
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-redaction "gradle -p android :app:testDebugUnitTest --tests 'com.aiquota.mobile.support.BugReportEmailComposerTest'; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-redaction -S -400
      tmux kill-session -t ulw-qa-obfuscation-redaction
    Expected: EXIT:0 after Gradle preflight, and test output includes BugReportEmailComposerTest passing.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-4-redaction-green.txt

  Scenario: Static sensitive string scan has no raw log pattern
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-secret-scan "rg -n '(access_token=|refresh_token=|id_token=|Authorization:|Bearer [A-Za-z0-9]|code=[^r])' android/app/src/main/java android/app/src/test/java; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-secret-scan -S -300
      tmux kill-session -t ulw-qa-obfuscation-secret-scan
    Expected: EXIT:1, meaning no raw sensitive emission pattern was found. Safe boolean/status strings must be reviewed if present.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-4-sensitive-scan.txt
  ```

  **Commit**: YES | Message: `test(android): guard sensitive diagnostics` | Files: relevant test file and minimal production redaction file only if needed

- [ ] 5. Generate Release AAB and Inspect Manifest plus Mapping

  **What to do**:
  - Run only after Gradle CLI preflight passes.
  - Build release AAB with `gradle -p android :app:bundleRelease`.
  - Capture release mapping path, expected under `android/app/build/outputs/mapping/release/mapping.txt`.
  - Inspect mapping to prove:
    - Required JS bridge method names remain visible/unobfuscated: `postUsagePayload`, `postCollectorError`, `fetchCopilotJson`, `fetchCopilotJsonWithAuthorization`, `fetchCursorJson`.
    - At least one internal provider/helper class not listed as a runtime entry point is obfuscated.
    - Manifest/runtime entrypoint classes remain resolvable.
  - Inspect AAB contents with `jar tf android/app/build/outputs/bundle/release/app-release.aab`.
  - Dump manifest with available Android build tools. Preferred:
    - `bundletool dump manifest --bundle android/app/build/outputs/bundle/release/app-release.aab --module base`
    - If `bundletool` is unavailable, use Android Studio/SDK artifact inspection and record the exact command/path used.
  - Save manifest and mapping snippets to evidence.

  **Must NOT do**:
  - Do not claim success from unit tests alone.
  - Do not skip mapping inspection.
  - Do not skip release artifact inspection.

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: 6 | Blocked By: 3, 4 and Gradle preflight

  **References**:
  - Release config: `android/app/build.gradle.kts:117-135`
  - Mapping path: `android/app/build/outputs/mapping/release/mapping.txt`
  - AAB path: `android/app/build/outputs/bundle/release/app-release.aab`
  - Required entrypoints: `android/app/src/main/AndroidManifest.xml:7-125`

  **Acceptance Criteria**:
  - [ ] `gradle -p android :app:bundleRelease` exits 0.
  - [ ] `test -f android/app/build/outputs/bundle/release/app-release.aab`
  - [ ] `test -f android/app/build/outputs/mapping/release/mapping.txt`
  - [ ] Manifest dump contains all release manifest entrypoint class names.
  - [ ] Mapping evidence proves bridge methods are kept and at least one internal non-entrypoint provider/helper name is obfuscated.

  **QA Scenarios**:
  ```text
  Scenario: Release AAB is produced
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-aab "gradle -p android :app:bundleRelease; test -f android/app/build/outputs/bundle/release/app-release.aab; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-aab -S -500
      tmux kill-session -t ulw-qa-obfuscation-aab
    Expected: EXIT:0 and Gradle reports :app:bundleRelease success.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-5-release-aab.txt

  Scenario: Manifest entrypoints and mapping are safe
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-artifact "jar tf android/app/build/outputs/bundle/release/app-release.aab | sed -n '1,80p'; grep -E 'postUsagePayload|postCollectorError|fetchCopilotJson|fetchCopilotJsonWithAuthorization|fetchCursorJson' android/app/build/outputs/mapping/release/mapping.txt; grep -E 'ProviderUsageNormalizer|ProviderWebCollectorScripts|CopilotNativeUsageFetcher' android/app/build/outputs/mapping/release/mapping.txt | sed -n '1,40p'; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-artifact -S -500
      tmux kill-session -t ulw-qa-obfuscation-artifact
    Expected: EXIT:0; bridge methods are present; at least one internal helper/provider class maps to a short obfuscated name.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-5-manifest-mapping.txt
  ```

  **Commit**: YES | Message: `build(android): verify stable release obfuscation` | Files: source/config/test changes only; do not commit build outputs

- [ ] 6. Run Final Regression Suite and Handoff

  **What to do**:
  - Run static tests:
    - `npm test`
  - Run focused Android tests after Gradle preflight:
    - `gradle -p android :app:testDebugUnitTest --tests 'com.aiquota.mobile.providers.ProviderUsageNormalizerTest'`
    - `gradle -p android :app:testDebugUnitTest --tests 'com.aiquota.mobile.providers.ProviderWebCollectorScriptsTest'`
    - `gradle -p android :app:testDebugUnitTest --tests 'com.aiquota.mobile.providers.GeminiCliFirebaseGatewayTest'`
    - `gradle -p android :app:testDebugUnitTest --tests 'com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest'`
    - `gradle -p android :app:testDebugUnitTest --tests 'com.aiquota.mobile.ui.dashboard.UnifiedDashboardDragReorderTest'`
    - `gradle -p android :app:testDebugUnitTest --tests 'com.aiquota.mobile.widget.*'`
  - Re-run release build:
    - `gradle -p android :app:bundleRelease`
  - Record exact artifact paths and final `git status --short --branch`.
  - Create a final progress note under `.omo/ulw-loop/evidence/android-obfuscation-20260610/final-summary.md`.

  **Must NOT do**:
  - Do not commit `.omo/ulw-loop/evidence/**` unless the user explicitly wants evidence committed.
  - Do not commit release build outputs.
  - Do not claim Mac/iOS or Play Console validation; this is Android release artifact validation only.

  **Parallelization**: Can Parallel: NO | Wave 4 | Blocks: none | Blocked By: 5

  **References**:
  - Static tests: `package.json`
  - Provider tests: `android/app/src/test/java/com/aiquota/mobile/providers`
  - Dashboard test: `android/app/src/test/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardDragReorderTest.kt`
  - Widget tests: `android/app/src/test/java/com/aiquota/mobile/widget`

  **Acceptance Criteria**:
  - [ ] `npm test` exits 0.
  - [ ] Focused Android tests exit 0.
  - [ ] `gradle -p android :app:bundleRelease` exits 0.
  - [ ] Evidence files exist for RED, GREEN, release AAB, manifest/mapping, final regression, and cleanup.
  - [ ] `tmux ls` does not list any `ulw-qa-obfuscation-*` leftover sessions.
  - [ ] Final `git status --short --branch` is captured and distinguishes implementation changes from pre-existing unrelated working tree changes.

  **QA Scenarios**:
  ```text
  Scenario: Static and Android regressions pass
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-final-tests "npm test; gradle -p android :app:testDebugUnitTest --tests 'com.aiquota.mobile.providers.ProviderUsageNormalizerTest' --tests 'com.aiquota.mobile.providers.ProviderWebCollectorScriptsTest' --tests 'com.aiquota.mobile.providers.GeminiCliFirebaseGatewayTest' --tests 'com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest' --tests 'com.aiquota.mobile.ui.dashboard.UnifiedDashboardDragReorderTest'; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-final-tests -S -800
      tmux kill-session -t ulw-qa-obfuscation-final-tests
    Expected: EXIT:0 and all named suites pass.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-6-final-tests.txt

  Scenario: QA sessions and artifacts are cleaned up
    Tool: tmux
    Steps:
      tmux new-session -d -s ulw-qa-obfuscation-cleanup "tmux ls 2>/dev/null | grep 'ulw-qa-obfuscation-' || true; git status --short --branch; echo EXIT:$?"
      tmux capture-pane -pt ulw-qa-obfuscation-cleanup -S -300
      tmux kill-session -t ulw-qa-obfuscation-cleanup
    Expected: EXIT:0; no `ulw-qa-obfuscation-*` sessions remain in the captured output.
    Evidence: .omo/ulw-loop/evidence/android-obfuscation-20260610/task-6-cleanup-status.txt
  ```

  **Commit**: YES | Message: `chore(android): complete obfuscation verification` | Files: final docs/evidence summary only if evidence is intended to be tracked

## Final Verification Wave

- [ ] F1. Plan Compliance Audit
  - Verify every task has references, acceptance criteria, QA scenarios, and commit guidance.
  - Command: `grep -n "Acceptance Criteria\\|QA Scenarios\\|Commit" docs/superpowers/plans/2026-06-10-android-obfuscation-security-design.md`

- [ ] F2. Scope Fidelity Check
  - Verify no task enables shrink/optimize or JS asset obfuscation.
  - Command: `grep -n "Do not enable shrink\\|Do not enable optimize\\|Do not obfuscate, minify, encode" docs/superpowers/plans/2026-06-10-android-obfuscation-security-design.md`

- [ ] F3. Release Artifact Audit
  - Verify AAB, manifest dump, and mapping snippets are captured.
  - Command: `find .omo/ulw-loop/evidence/android-obfuscation-20260610 -maxdepth 1 -type f | sort`

- [ ] F4. Reviewer Gate
  - Because this is security-sensitive release hardening and touches multiple files, run a post-implementation reviewer pass before declaring done.
  - If reviewer tooling is unavailable, perform a documented self-review that checks the diff against this plan line-by-line and records all exceptions.

## Commit Strategy

Use separate commits:

1. `docs(android): plan stable release obfuscation`
2. `test(android): lock stable obfuscation guardrails`
3. `build(android): narrow release obfuscation keeps`
4. `test(android): guard sensitive diagnostics`
5. Optional final docs commit only if the user wants tracked verification notes.

Do not auto-commit without explicit user approval.

## Success Criteria

- Plan exists at `docs/superpowers/plans/2026-06-10-android-obfuscation-security-design.md`.
- Spec will exist at `docs/superpowers/specs/2026-06-10-android-obfuscation-security-design.md` after Task 1 execution.
- RED evidence exists before R8 rule changes.
- GREEN evidence exists after R8 rule changes.
- `-dontshrink` and `-dontoptimize` remain in release rules.
- Broad app-wide Activity/Service/Receiver keep rules are gone.
- JS bridge methods remain name-stable.
- Provider asset JS is untouched.
- Sensitive diagnostics do not emit raw tokens/cookies/auth headers/OAuth codes.
- Release AAB and mapping verification are complete.
- Existing provider, Firebase gateway, dashboard, and widget regressions pass.
