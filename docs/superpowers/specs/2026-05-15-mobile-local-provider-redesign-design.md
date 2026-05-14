# Mobile Local Provider Redesign Design

Approved by the user on 2026-05-15.

## Summary

AI Usage will move from a Windows-synced Android companion app to a local-first Android app for tracking personal subscription usage limits. The app will support Claude, Codex, Gemini, GitHub Copilot, and Cursor as provider connections. Existing Windows/Firebase sync code will be hidden first and removed later only after the local provider model is complete and verified.

The first screen will be a unified dashboard. It will show one card per provider, allow providers to be hidden, and allow cards to be reordered by long press. The same order and visibility state will drive the unified dashboard widget. Each provider will also have a detail screen reachable from the navigation bar and a provider-specific widget. Widget work in this phase includes two explicit widget families: one unified dashboard widget and one individual provider widget family.

## Goals

- Make local provider tracking the default app experience.
- Show Claude, Codex, Gemini, GitHub Copilot, and Cursor on the unified dashboard.
- Show a login/connect action on each disconnected provider card.
- Allow users to hide providers they do not use.
- Allow long-press drag reordering of provider cards.
- Persist provider order and hidden state locally.
- Reuse provider order and hidden state in the unified dashboard widget.
- Add provider detail screens with connection state, login flow, refresh state, and detailed usage limits.
- Add a unified dashboard widget that adapts from 2x2 through 3x4.
- Add provider-specific widgets that adapt from 2x1 through 3x3.
- Keep widget, notification, and preview caches free of provider cookies, tokens, raw HTML, raw logs, local credential paths, and other sensitive session material.

## Non-Goals

- Do not delete the existing Windows/Firebase sync code in this phase.
- Do not add a backend service for provider scraping or account sessions.
- Do not store provider API keys or raw provider credentials in widget or notification caches.
- Do not add paid plans, subscriptions, or new ad placements as part of this redesign.
- Do not rely on provider API usage dashboards for the main experience, because the product goal is subscription limit tracking rather than API usage tracking.

## Architecture

The app will use a local-first provider architecture with a shared snapshot model.

```text
Provider Web Login / Provider Page
        |
        v
ProviderConnector
        |
        v
LocalUsageRepository
        |
        +--> UnifiedDashboard
        +--> ProviderDetailScreen
        +--> WidgetSnapshotCache
        +--> Notification renderer
```

Each provider owns only its login/session/extraction logic. The dashboard, notification, and widgets do not know provider-specific page structures. They consume normalized usage snapshots.

The existing Windows/Firebase sync implementation will be moved behind a legacy feature boundary named `LegacyWindowsSync`. It can remain compiled if that is the lowest-risk path, but it must no longer be the default first-run experience.

## Core Components

### ProviderId

`ProviderId` is a stable enum-like identifier:

- `claude`
- `codex`
- `gemini`
- `copilot`
- `cursor`

The id is used for local preferences, snapshot storage, widget configuration, navigation routes, and tests.

### ProviderConnector

Each provider implements a common connector interface:

```text
providerId
displayName
connectionState()
openLogin()
refreshUsage()
disconnect()
```

Connectors must use user-authorized local sessions only. They must not bypass login, MFA, rate limits, access controls, or provider restrictions. If a provider page changes or does not expose usable subscription limit data, the connector returns a typed unavailable state instead of inventing data.

### ProviderSessionStore

Provider session material stays in local app-private storage. The design allows WebView-managed cookies or an encrypted local session store depending on the provider flow. Session material must never be copied into widget cache JSON, notification extras, logs, crash metadata, screenshots, or Firebase documents.

### LocalUsageRepository

`LocalUsageRepository` stores normalized display data per provider:

- provider id
- connection state
- last successful refresh time
- refresh error, if any
- provider plan label, if visible to the user
- usage limit lines
- optional detail lines

The repository is the single source of truth for the dashboard and provider screens.

### ProviderPreferencesRepository

`ProviderPreferencesRepository` stores:

- provider order
- hidden provider ids
- provider widget selections
- legacy Windows sync hidden/default state

Provider order defaults to Claude, Codex, Gemini, GitHub Copilot, Cursor. Hidden providers are excluded from the unified dashboard widget and unified notification summary.

## Usage Snapshot Model

The normalized model will stay close to the existing `SnapshotProviderUsage` and `UsageLine` shapes so the current widget and notification rendering code can be reused.

Each provider snapshot contains:

- `providerId`
- `displayName`
- `status`: disconnected, connecting, connected, refreshing, unavailable, error
- `planLabel`
- `updatedAt`
- `lines`

Each usage line contains:

- `label`
- `remainingPercent`
- `remainingText`
- `resetText`
- `detailText`
- `severity`

`remainingPercent` is nullable because some providers may expose text-only limits. Widgets must render text-only rows gracefully.

## App UX

### First Launch

The app opens to the unified dashboard. No Windows PC setup is shown as the primary path.

When no provider is connected, the dashboard still shows all five provider cards. Each card shows:

- provider name
- connection state
- `Login` or `Connect` action
- hide action in an overflow menu

### Unified Dashboard

The dashboard shows visible provider cards in saved order. A long press starts drag reorder. Reordering persists immediately and updates:

- dashboard card order
- unified dashboard widget order
- notification provider order

Hidden providers are available from settings or a dashboard manage-providers sheet.

### Provider Detail Screens

The navigation bar exposes:

- Home
- Claude
- Codex
- Gemini
- Copilot
- Cursor

Each provider detail screen shows:

- connection state
- login/connect action when disconnected
- disconnect action when connected
- refresh action
- last updated time
- detailed limit lines
- unavailable/error message when extraction is not currently possible

### Legacy Windows Sync

Existing Windows sync surfaces are hidden from the main first-run and dashboard flow. If retained in settings, it is labeled as legacy and is not required for local provider tracking.

## Provider Login And Refresh Flow

Each provider uses a local login flow launched from its provider card or detail screen. The implementation can use a WebView-backed auth surface when provider session cookies are required for local page inspection.

Refresh runs locally:

1. Check provider connection state.
2. Open or reuse the local provider session.
3. Read only the pages or structured responses available to the signed-in user.
4. Extract subscription usage limit display data.
5. Normalize to `ProviderUsageSnapshot`.
6. Store normalized snapshot in `LocalUsageRepository`.
7. Export display-only snapshot data to widget and notification caches.

The connector layer must include host allowlists per provider. Those allowlists must be verified during implementation against the provider's current official login and account pages before enabling each connector.

## Widgets

This phase implements both widget families. The unified widget and the individual provider widget are separate user-facing widget choices, even if they share rendering helpers internally.

### Unified Dashboard Widget

The unified dashboard widget supports 2x2 through 3x4 sizes. It displays visible providers in the saved dashboard order.

Adaptive behavior:

- 2x2: compact summary rows, limited detail.
- 3x2: wider rows with gauge and reset text.
- 3x3: more provider rows and clearer spacing.
- 3x4: expanded rows with additional detail where available.

The widget must recalculate row height, gauge width, font size bucket, and visible row count from the widget size. It must not use fixed gauge dimensions that look correct on phones but undersized on tablets.

### Provider-Specific Widget

The provider widget supports 2x1 through 3x3 sizes. During widget configuration, the user selects one provider.

Adaptive behavior:

- 2x1: primary limit and reset text.
- 2x2: two to three limit lines.
- 3x2: wider gauges and detail text.
- 3x3: detailed provider card with more lines and refresh state.

Provider widgets keep working when other providers are hidden from the unified dashboard. If the selected provider is disconnected, the widget shows a safe disconnected state and opens the provider detail screen on tap.

## Widget And App Tap Behavior

All widgets must open the app when tapped. A unified widget opens the home dashboard. A provider widget opens that provider's detail screen. This must work whether the user is connected, disconnected, or in an error state.

## Notification Behavior

The existing pinned notification can continue to show a compact usage summary. Its provider order and visibility follow `ProviderPreferencesRepository`. It must read display-only snapshots from the same cache path used by widgets.

## Security And Privacy

The app stores provider session data locally only. It must not send provider session material to Firebase or any custom backend.

The following data must not be written to widget cache, notification cache, logs, or screenshots:

- provider cookies
- access tokens
- refresh tokens
- raw HTML
- raw provider API responses that include account data beyond display limits
- local credential paths
- local files
- raw logs

Widget and notification caches may store only display-safe values:

- provider id
- provider display name
- remaining percentage
- remaining/reset text
- generic connection status
- last updated timestamp

## Testing Strategy

### Source Tests

Add tests for:

- provider id list and default order
- hide/show preference persistence
- reorder persistence
- hidden providers excluded from unified widget payload
- provider widget selection persistence
- widget cache serializer excluding sensitive fields
- widget tap routing for signed-in, disconnected, and error states

### Android Unit Tests

Add tests for:

- normalized snapshot conversion
- widget layout spec selection across 2x1, 2x2, 3x2, 3x3, and 3x4
- provider status rendering states
- repository behavior when a connector returns unavailable/error

### Build Verification

Run:

- `npm test`
- Android debug unit tests
- Android debug build
- Android release bundle build

### Emulator QA

Verify on both phone and tablet emulators:

- first launch opens unified dashboard
- disconnected provider cards show login/connect actions
- provider navigation opens all five provider screens
- hide/show changes update dashboard and unified widget
- long-press reorder updates dashboard and unified widget order
- unified widget scales correctly from 2x2 through 3x4
- provider widget scales correctly from 2x1 through 3x3
- widget tap opens the correct app screen
- no widget layout clipping on tablet

## Migration Plan

1. Introduce local provider domain models and repositories without deleting Windows sync.
2. Add local-first dashboard and navigation.
3. Hide legacy Windows sync from first-run and primary dashboard paths.
4. Add connector scaffolds and provider detail screens.
5. Add local snapshot storage and display-only widget cache export.
6. Expand unified widget layout and add provider-specific widget.
7. Add tests and emulator QA.
8. Later, after the local provider model is stable, remove unused Windows sync code in a separate cleanup phase.

## Risks

- Provider web pages can change without notice, breaking extraction.
- Some providers may not expose subscription usage limits in a stable machine-readable form.
- WebView login flows can be sensitive to cookies, MFA, device trust, and provider security rules.
- Widget size behavior varies by launcher and device density, especially on tablets.

The design mitigates these risks by isolating provider extraction in connectors, representing unavailable/error states explicitly, keeping display logic provider-agnostic, and testing widget size buckets on phone and tablet.

## Acceptance Criteria

- The app defaults to a local unified dashboard rather than Windows sync setup.
- Claude, Codex, Gemini, GitHub Copilot, and Cursor appear as provider cards.
- Disconnected provider cards expose login/connect actions.
- Each provider has a detail screen reachable from navigation.
- Providers can be hidden and restored.
- Provider cards can be reordered by long press.
- Provider order and hidden state persist after app restart.
- Unified widget reflects provider order and hidden state.
- Provider-specific widgets can target individual providers.
- Unified widget supports 2x2 through 3x4 without clipping on phone or tablet.
- Provider-specific widget supports 2x1 through 3x3 without clipping on phone or tablet.
- Widget taps open the app route appropriate to the widget.
- Widget and notification caches contain display-only data and no provider session material.
- Existing Windows sync code is hidden from the primary flow but not deleted in this phase.
- Automated tests and emulator QA evidence cover the dashboard, provider screens, preferences, widget routing, and widget sizing.
