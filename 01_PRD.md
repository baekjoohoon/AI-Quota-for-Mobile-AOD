# AI Quota Mobile Sync PRD

Created: 2026-04-29  
Updated: 2026-04-29

## Product summary

AI Quota Mobile Sync shows the latest AI quota snapshot from one or more Windows devices on Android. The free-plan MVP uses Firebase Auth and Firestore only. There is no pairing code. A user signs in on Android and Windows with the same Firebase account, and devices appear automatically under that user's Firestore path.

## Target user

- A user who already tracks Codex, Claude, Gemini, or GitHub Copilot usage on Windows
- A user who wants to check usage from Android without logging in to each provider on mobile
- A user who may own multiple PCs and needs to distinguish them by device name

## Core jobs

- Sign in once on Android with Google
- See remaining AI quota limits first, matching the Windows app
- Open device management from settings
- Rename a device to something meaningful like `Home PC` or `Work Laptop`
- Surface stale, offline, revoked, and provider-error states clearly

## MVP scope

- Android app with Google sign-in
- Firestore device list under `/users/{uid}/devices`
- Latest snapshot under `/users/{uid}/devices/{deviceId}/snapshots/latest`
- Device rename from Android settings
- Android widget backed by local cache only with remaining limit text
- Multi-device support with newest device selected by default

## Out of scope for this phase

- iOS implementation
- Pairing codes
- Cloud Functions upload broker
- Secret Manager
- Apple sign-in
- Push notifications

## User flow

1. The user signs in on Android with Google.
2. The app reads `/users/{uid}/devices`.
3. The app selects the most recent device by `lastSeenAt`.
4. The home screen reads `/snapshots/latest` and renders active provider limits.
5. The user opens settings to switch devices or rename the selected device.
6. The app hides inactive placeholder providers from the home screen.
7. The widget shows cached display data only.

## Success criteria

- Android login succeeds with Google.
- Active provider limits appear for the signed-in user.
- Device renaming updates Firestore and is reflected in the UI.
- The selected device snapshot renders remaining limits without provider credentials stored on mobile.
- The widget updates from cache and never fetches credentials directly.
