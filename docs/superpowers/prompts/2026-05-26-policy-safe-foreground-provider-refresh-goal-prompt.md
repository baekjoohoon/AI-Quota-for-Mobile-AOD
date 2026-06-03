# Goal Prompt: Policy Safe Foreground Provider Refresh

Use this prompt to start a goal-driven implementation session.

```text
Objective:
Implement the policy-safe foreground provider refresh work for AI Quota Android.

Workspace:
D:\Vibe Project\AI Usage for Mobile

Primary spec:
D:\Vibe Project\AI Usage for Mobile\docs\superpowers\specs\2026-05-26-policy-safe-foreground-provider-refresh-spec.md

Primary plan:
D:\Vibe Project\AI Usage for Mobile\docs\superpowers\plans\2026-05-26-policy-safe-foreground-provider-refresh.md

Required documentation outputs:
1. D:\Vibe Project\AI Usage for Mobile\docs\qa\policy-safe-foreground-provider-refresh-progress-2026-05-26.md
2. D:\Vibe Project\AI Usage for Mobile\docs\qa\policy-safe-foreground-provider-refresh-troubleshooting-2026-05-26.md
3. D:\Vibe Project\AI Usage for Mobile\docs\qa\policy-safe-foreground-provider-refresh-limitations-2026-05-26.md
4. D:\Vibe Project\AI Usage for Mobile\docs\store\foreground-service-declaration.md

Implementation rules:
- Do not use MCP tools unless I explicitly ask for them.
- Do not revert unrelated worktree changes.
- Keep passive widget update cache-only.
- Do not start ProviderBackgroundRefreshService from passive APPWIDGET_UPDATE.
- Manual widget refresh may start ProviderBackgroundRefreshService because it is a user action.
- Use dataSync foreground service type by default.
- Do not add FOREGROUND_SERVICE_SPECIAL_USE unless separately approved.
- Do not add exact alarm permission.
- Do not add BOOT_COMPLETED startup for dataSync FGS.
- Do not re-add OAuth client_secret to the APK.
- Keep provider diagnostics redacted.
- Preserve trust-first provider state: CONNECTED only after trusted usage row save success.
- Timeout/no payload/selector mismatch/API shape change must not automatically become INTERACTIVE_AUTH_REQUIRED.

Required work:
1. Read the spec and plan.
2. Create the three required QA docs before code changes.
3. Add/adjust RED tests for:
   - passive widget update does not start foreground service,
   - manual widget refresh remains user-action start and clears spinner on start failure,
   - live monitoring requires user approval and notification permission,
   - ProviderBackgroundRefreshService uses dataSync and typed startForeground,
   - service heartbeat is written without sensitive values,
   - notification has a user stop action,
   - 90-second copy is used in English and Korean,
   - OAuth client_secret is not embedded.
4. Implement the minimal code needed to pass the tests.
5. Update progress and troubleshooting docs after every meaningful change or failed test.
6. Update limitations and Play declaration docs before final verification.
7. Run:
   .\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest
   .\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug
   git diff --check
8. Install the debug APK on the running emulator and verify:
   - passive widget update does not trigger ForegroundServiceStartNotAllowedException,
   - live monitoring produces roughly 90-second cycleStart logs while notification/service is active,
   - notification stop action stops the loop,
   - widget manual refresh still requests immediate collection.

Completion criteria:
- All required docs exist and are updated.
- Unit tests and debug build pass.
- git diff --check passes.
- Emulator verification is completed or any blocker is documented with evidence.
- Final report includes changed files, verification commands, emulator result, and known limitations.
```
