# Gemini and Antigravity Backendless WebView-First Direction

Date: 2026-05-28

## Decision

Gemini and Antigravity should be implemented as backendless WebView-first collectors.

Do not add a backend for user token exchange, user cookies, raw provider payloads, or private `cloudcode-pa` proxying. A backend may be considered later only for non-user-data script distribution, such as remote collector versions, kill switches, or feature flags.

## Why

Public Google APIs do not expose the personal quota payload the app needs.

Direct `cloudcode-pa.googleapis.com/v1internal:*` calls are private/internal and currently fail for this project from both OAuth access-token paths and self-service Cloud Shell enablement attempts.

The previous client-secret-dependent path was not a pure WebView extractor. It effectively depended on Desktop OAuth token exchange plus private API access. If a client secret is required for collection, the collector is still native/private-API gated and not WebView-first.

## Target Shape

Gemini:

- login and session surface: `https://gemini.google.com/app`
- primary collector: WebView page/network observation
- script boundary: `GeminiScriptProvider` plus `gemini_collector.js`
- trusted state: only after normalized usage lines are collected

Antigravity:

- login and session surface: `https://antigravity.google/`
- primary collector: WebView page/network observation
- script boundary: `AntigravityScriptProvider` plus `antigravity_collector.js`
- trusted state: only after normalized usage lines are collected

## Implementation Rules

1. Route Gemini and Antigravity connect actions through `WebLoginActivity`, not `GoogleAuthorizationActivity`.
2. Classify both providers as `WEBVIEW_COLLECTOR`.
3. Store provider state through WebView/session collector state, not as a native token requirement.
4. Do not treat OAuth token exchange success as `CONNECTED`.
5. Do not start native Google OAuth/private API bridge collection when no raw WebView payload exists.
6. In injected JS, first inspect real page state, DOM text, local storage, and page-origin fetch/XHR responses.
7. Synthetic `cloudcode-pa` calls must not be the login or refresh success gate. If retained, keep them diagnostic only.
8. Save `CONNECTED` only for trusted normalized usage payloads. Otherwise keep previous snapshot and mark pending/stale according to existing trust-first rules.

## Backend Position

Backend is not required for the first implementation.

Allowed later:

- remote collector script versioning
- encrypted or signed script delivery
- kill switch and experiment flags

Not allowed:

- sending Google access tokens to backend
- sending Google cookies to backend
- server-side Desktop OAuth client secret exchange for mobile users
- backend proxying of private quota APIs as the primary collector
