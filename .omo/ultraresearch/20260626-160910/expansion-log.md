# Expansion Log

## Wave 1
- Opened axes: GLM refresh path, GLM process/storage, provider snapshot storage, monitor log evidence.
- New actionable leads:
  - GLM cookie transfer must cross the `:glm_webview` -> main process boundary explicitly.
  - Provider scoped snapshot writes need hardening against 0-byte / `.bak` window.
  - External recurring `dumpsys package com.google.android.gms` should be stopped or isolated during durability QA.

## Convergence
- Root-cause convergence reached for app-side GLM failure: direct cookie path never becomes reliable because the cookie is saved in the isolated process through SharedPreferences, while main refresh reads in another process.
- Remaining environmental lead: source of the recurring external `adb dumpsys` process.
