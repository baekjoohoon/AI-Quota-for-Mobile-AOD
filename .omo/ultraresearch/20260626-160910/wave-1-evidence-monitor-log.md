# Wave 1 - Monitor Log Evidence

## Findings
- The 120-minute monitor stopped at 40 minutes, before the 60-minute APK reinstall threshold.
- Other providers were connected and fresh enough; GLM failed because `shared_prefs/usage_data_glm.xml` could not be read.
- Logcat shows successful early GLM payloads and `webSessionCookieSaved provider=glm saved=true cookieCount=8`, followed on the next cycles by `glmWebSessionDirectRefresh payload=false diagnostic=glm_web_cookie_missing`.
- Logcat also shows repeated external `adb dumpsys package com.google.android.gms` every roughly 10 seconds and later GLM WebView timeouts, skipped frames, process attach/start timeouts.

## Sources
- `.omo/ulw-loop/session-monitor-20260626-120m/evidence/session-monitor-20260626-151411-failure.txt`
- `.omo/ulw-loop/session-monitor-20260626-120m/evidence/session-monitor-20260626-151411-logcat.txt`

## EXPAND
- LEAD: external adb dump source -- WHY: it worsens emulator pressure but is not the primary GLM design bug -- ANGLE: identify host process only if recurring after app-side GLM direct path is fixed.
