# Wave 1: Runtime Emulator

## Findings

- Current GLM snapshot is API Key mode output: GLM line source is `provider-api`.
- Saved API Key snapshot contains reset times for 5-hour, weekly, and monthly lines.
- Current isolated WebView storage does not contain the known `z-ai-open-platform-token-production` key.
- Cookie-only direct call to `https://api.z.ai/api/monitor/usage/quota/limit` previously returned missing header auth, and token-cookie variants returned expired or incorrect token.
- GLM isolated WebView DevTools socket existed but did not respond to `/json`; raw in-memory rows could not be extracted.

## Evidence

- `shared_prefs/usage_data_glm.xml`: `source=provider-api`, `glm:tokens.resetsAt=2026-06-24T04:30:26.949Z`.
- `shared_prefs/ai_quota_glm_api_key.xml`: API key entry exists.
- `grep` over `app_webview_glm_webview/Default` found no known Web OAuth localStorage token key.

## EXPAND

- LEAD: Switch to Web OAuth and inspect page localStorage key names through a controlled debug dump. WHY: needed for live proof. ANGLE: add a redacted diagnostic or use DevTools when responsive.

