# Wave 1 - Provider Snapshot Storage

## Findings
- `ProviderScopedStateRepository.saveSnapshot()` writes provider `usage_data_*`, `account_data_*`, and `script_data_*` using `SharedPreferences.Editor.apply()`.
- The failed monitor observed `usage_data_glm.xml` as missing/0-byte while `usage_data_glm.xml.bak` still had the previous valid snapshot.
- This is consistent with asynchronous preference persistence under process/system pressure. It should not be used as the product truth without recovery or synchronous commit for critical provider snapshots.

## Sources
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderScopedStateRepository.kt:15-42`
- `.omo/ulw-loop/session-monitor-20260626-120m/evidence/session-monitor-20260626-151411-failure.txt`
- Android SharedPreferences: https://developer.android.com/reference/android/content/SharedPreferences

## EXPAND
- LEAD: harden provider scoped snapshot writes or monitor reads -- WHY: usage collection success can be hidden by a transient XML state -- ANGLE: commit critical provider scoped writes or recover from `.bak`.
