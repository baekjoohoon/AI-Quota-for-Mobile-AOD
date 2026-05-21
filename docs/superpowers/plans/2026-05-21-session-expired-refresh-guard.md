# Session Expired Refresh Guard impl Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Superseded: 사용자 UX 기준을 반영해 이 계획은 `D:/Vibe Project/AI Usage for Mobile/docs/superpowers/plans/2026-05-21-provider-session-continuity-refresh.md`로 대체한다. `SESSION_EXPIRED`는 기본 UX가 아니라 silent session renewal이 실패했을 때의 마지막 fallback 상태로만 사용한다.

**Goal:** Provider hidden refresh가 세션 만료 뒤 `/login` 또는 인증 페이지로 리다이렉트될 때, 이전 사용량은 보존하되 자동 refresh 루프에서 제외하고 사용자가 다시 로그인하도록 유도한다.

**Architecture:** 현재 앱의 hidden WebView collector는 Store APK처럼 인앱 세션을 사용하지만, 세션 만료를 일반 실패로만 저장한다. 일반 실패는 `failKeepingPrevious()`를 통해 `STALE` 상태가 되고, `ForegroundRefreshPolicy`가 `STALE`을 자동 refresh 대상에 포함해서 `/login` 조회가 반복된다. 해결은 collector URL을 더 바꾸는 것이 아니라, 세션 만료를 명시 상태와 명시 실패 종류로 분리하는 것이다.

**Tech Stack:** Kotlin, Android WebView, JUnit, existing local repository snapshot codec.

## Current Evidence

현재 Store APK 분석 기준으로 확정된 점:

- Store APK는 provider별 인앱 WebView 세션과 암호화된 JavaScript collector를 사용한다.
- Store APK의 live collector plaintext는 아직 확보되지 않았다. base APK만으로는 required split과 native `libs.so`가 없어 `CryptoUtils.r()` native key source를 복원할 수 없다.
- Store APK 지원 provider는 Claude, Copilot, Antigravity, Gemini, Codex다. Cursor는 현재 static-negative다.
- Store APK parity 구현은 decrypted collector/runtime evidence가 더 필요하다.

현재 앱 코드 기준으로 확인된 점:

- `ProviderRefreshPlan`의 hidden refresh 시작 URL은 이미 로그인 URL이 아니다.
- Claude/Codex/Copilot/Cursor hidden collector는 `BackgroundProviderWebCollector`에서 1dp WebView로 실행된다.
- `ProviderWebCollectorScripts.isRefreshLoginPage()`는 provider별 로그인 또는 인증 페이지를 감지한다.
- 로그인 페이지가 감지되면 현재는 문자열 에러로 `failKeepingPrevious()`가 호출된다.
- 이전 사용량이 있으면 `failKeepingPrevious()`가 snapshot을 `STALE`로 저장한다.
- `ForegroundRefreshPolicy.connectedProviders()`는 `STALE`과 non-empty lines를 자동 refresh 대상으로 포함한다.
- 그래서 세션 만료 뒤에도 provider가 계속 자동 refresh 대상이 되고, WebView가 다시 `/login` 또는 인증 페이지를 조회한다.

## Non-Goals

- Store APK의 암호화된 collector source를 추측 구현하지 않는다.
- hardcoded usage value, preview/sample row, fake fallback을 추가하지 않는다.
- Cursor를 Store APK 지원 provider로 승격하지 않는다.
- Copilot을 Firebase Auth 또는 Custom Tab 기반 parity로 바꾸지 않는다.
- 이번 계획은 세션 만료 refresh 루프 차단 계획이다. Store APK collector parity 계획은 runtime evidence 확보 뒤 별도 작성한다.

## Desired Behavior

세션이 살아 있는 일시 실패:

- 이전 사용량을 보존한다.
- 상태는 기존처럼 `STALE` 또는 `ERROR`를 사용한다.
- 자동 refresh 재시도 대상에 남길 수 있다.

세션 만료 또는 로그인 페이지 리다이렉트:

- 이전 사용량 lines는 보존한다.
- 상태는 `SESSION_EXPIRED`로 저장한다.
- 자동 refresh 대상에서 제외한다.
- dashboard/detail의 primary action은 다시 로그인 또는 연결로 보인다.
- 사용자가 수동 refresh를 누르면 hidden collector를 다시 돌리지 않고 login flow로 보낸다.

## Implementation Tasks

### 1. Add failing state-model tests

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/local/ProviderUsageSnapshotTest.kt`.
- [ ] Add a test proving session-expired snapshots keep previous usage lines but require reconnect.

Expected test shape:

```kotlin
@Test
fun sessionExpiredKeepsPreviousUsageButRequiresReconnect() {
    val previous = ProviderUsageSnapshot.connected(
        providerId = ProviderId.CLAUDE,
        lines = listOf(
            ProviderUsageLine(
                label = "Plan",
                used = 10,
                limit = 100,
                resetsAt = null,
                detail = null,
            ),
        ),
        message = "Usage updated",
    )

    val snapshot = ProviderUsageSnapshot.sessionExpiredKeepingPrevious(
        providerId = ProviderId.CLAUDE,
        previous = previous,
        message = "Claude login session expired. Sign in again.",
    )

    assertEquals(ProviderConnectionState.SESSION_EXPIRED, snapshot.connectionState)
    assertEquals(previous.lines, snapshot.lines)
    assertEquals(ProviderConnectionAction.CONNECT, snapshot.primaryConnectionAction())
    assertTrue(snapshot.shouldShowDashboardConnectAction())
}
```

- [ ] Run expected-red test:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderUsageSnapshotTest"
```

### 2. Add failing auto-refresh policy test

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/sync/ForegroundRefreshPolicyTest.kt`.
- [ ] Keep the existing behavior that `STALE` may be refreshed.
- [ ] Add a new test proving `SESSION_EXPIRED` is not refreshed, even if it has previous lines.

Expected test shape:

```kotlin
@Test
fun sessionExpiredProviderIsNotAutoRefreshed() {
    val connected = snapshot(
        providerId = ProviderId.CLAUDE,
        state = ProviderConnectionState.CONNECTED,
        hasLines = true,
    )
    val expired = snapshot(
        providerId = ProviderId.COPILOT,
        state = ProviderConnectionState.SESSION_EXPIRED,
        hasLines = true,
    )

    val providers = ForegroundRefreshPolicy.connectedProviders(
        snapshots = listOf(connected, expired),
        nowMillis = 1_000L,
    )

    assertEquals(listOf(ProviderId.CLAUDE), providers)
}
```

- [ ] Run expected-red test:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.sync.ForegroundRefreshPolicyTest"
```

### 3. Add typed refresh failure model

- [ ] Add `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderRefreshFailure.kt`.

Implementation:

```kotlin
package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId

enum class ProviderRefreshFailureKind {
    SESSION_EXPIRED,
    PAGE_LOAD_FAILED,
    HTTP_ERROR,
    TIMEOUT,
    COLLECTOR_ERROR,
    NO_TRUSTED_PAYLOAD,
}

data class ProviderRefreshFailure(
    val kind: ProviderRefreshFailureKind,
    val message: String,
) {
    companion object {
        fun sessionExpired(providerId: ProviderId): ProviderRefreshFailure =
            ProviderRefreshFailure(
                kind = ProviderRefreshFailureKind.SESSION_EXPIRED,
                message = "${providerId.displayName} login session expired. Sign in again.",
            )

        fun pageLoadFailed(message: String): ProviderRefreshFailure =
            ProviderRefreshFailure(ProviderRefreshFailureKind.PAGE_LOAD_FAILED, message)

        fun httpError(message: String): ProviderRefreshFailure =
            ProviderRefreshFailure(ProviderRefreshFailureKind.HTTP_ERROR, message)

        fun timeout(providerId: ProviderId): ProviderRefreshFailure =
            ProviderRefreshFailure(
                kind = ProviderRefreshFailureKind.TIMEOUT,
                message = "${providerId.displayName} usage collection timed out.",
            )

        fun collectorError(message: String): ProviderRefreshFailure =
            ProviderRefreshFailure(ProviderRefreshFailureKind.COLLECTOR_ERROR, message)

        fun noTrustedPayload(providerId: ProviderId): ProviderRefreshFailure =
            ProviderRefreshFailure(
                kind = ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD,
                message = "${providerId.displayName} did not return trusted usage data.",
            )
    }
}
```

Why:

- 문자열 message matching으로 session 만료를 구분하지 않는다.
- collector 실패와 login 만료를 persistence layer에서 다르게 처리할 수 있다.
- 나중에 Store APK collector evidence가 들어와도 실패 종류를 확장하기 쉽다.

### 4. Add explicit session-expired snapshot state

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/ProviderModels.kt`.
- [ ] Add `SESSION_EXPIRED` to `ProviderConnectionState`.
- [ ] Update `primaryConnectionAction()` so `SESSION_EXPIRED` returns `ProviderConnectionAction.CONNECT`.
- [ ] Add `ProviderUsageSnapshot.sessionExpiredKeepingPrevious()`.

Implementation intent:

```kotlin
enum class ProviderConnectionState {
    DISCONNECTED,
    NOT_CONNECTED,
    CONNECTING,
    CONNECTED,
    COLLECTING,
    STALE,
    SESSION_EXPIRED,
    UNAVAILABLE,
    ERROR,
}
```

```kotlin
fun sessionExpiredKeepingPrevious(
    providerId: ProviderId,
    previous: ProviderUsageSnapshot?,
    message: String,
): ProviderUsageSnapshot {
    val now = Instant.now().toString()
    return previous?.copy(
        connectionState = ProviderConnectionState.SESSION_EXPIRED,
        refreshState = ProviderRefreshState.IDLE,
        updatedAt = now,
        message = message,
    ) ?: disconnected(providerId).copy(
        connectionState = ProviderConnectionState.SESSION_EXPIRED,
        refreshState = ProviderRefreshState.IDLE,
        updatedAt = now,
        message = message,
    )
}
```

Primary action rule:

```kotlin
ProviderConnectionState.SESSION_EXPIRED -> ProviderConnectionAction.CONNECT
```

### 5. Persist session-expired state in the repository

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt`.
- [ ] Add a repository method for session expiry.

Implementation intent:

```kotlin
fun markSessionExpired(providerId: ProviderId, message: String) {
    updateSnapshot(providerId) { previous ->
        ProviderUsageSnapshot.sessionExpiredKeepingPrevious(
            providerId = providerId,
            previous = previous,
            message = message,
        )
    }
}
```

Do not replace `failKeepingPrevious()`:

- `failKeepingPrevious()` remains for network errors, collector parse errors, timeout, and no trusted payload.
- `markSessionExpired()` is only for confirmed login/auth page redirects.

### 6. Route hidden collector login-page detection to typed session expiry

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/BackgroundProviderWebCollector.kt`.
- [ ] Change `onError` callback type from string message to `ProviderRefreshFailure`.

Constructor intent:

```kotlin
private val onError: (QueuedProviderRefreshJob, ProviderRefreshFailure) -> Unit,
```

Login-page handling:

```kotlin
if (ProviderWebCollectorScripts.isRefreshLoginPage(providerId, url)) {
    finishWithErrorOnce(
        job = job,
        failure = ProviderRefreshFailure.sessionExpired(providerId),
    )
    return
}
```

Other failures:

- page load failure -> `ProviderRefreshFailure.pageLoadFailed(message)`
- HTTP error -> `ProviderRefreshFailure.httpError(message)`
- timeout -> `ProviderRefreshFailure.timeout(providerId)`
- collector JavaScript error -> `ProviderRefreshFailure.collectorError(message)`
- page finished with no trusted payload -> `ProviderRefreshFailure.noTrustedPayload(providerId)`

Do not inject collector scripts when `ProviderWebCollectorScripts.shouldRunCollector()` is false.

### 7. Store typed failure result in app shell

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/AIUsageAppShell.kt`.
- [ ] In the hidden collector `onError` lambda, branch on failure kind.

Implementation intent:

```kotlin
when (failure.kind) {
    ProviderRefreshFailureKind.SESSION_EXPIRED -> {
        localUsageRepository.markSessionExpired(
            providerId = queuedJob.job.providerId,
            message = failure.message,
        )
    }
    else -> {
        localUsageRepository.failKeepingPrevious(
            providerId = queuedJob.job.providerId,
            message = failure.message,
        )
    }
}
finishProviderRefreshSurfaces()
```

### 8. Exclude session-expired providers from automatic refresh

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/sync/ForegroundRefreshPolicy.kt`.
- [ ] Keep `CONNECTED`, `COLLECTING`, and `STALE` behavior unchanged.
- [ ] Do not include `SESSION_EXPIRED`.

Implementation intent:

```kotlin
private val REFRESHABLE_STATES = setOf(
    ProviderConnectionState.CONNECTED,
    ProviderConnectionState.COLLECTING,
    ProviderConnectionState.STALE,
)
```

Why keep `STALE`:

- `STALE` can still mean transient network or collector failure with a valid session.
- `SESSION_EXPIRED` is the only state that proves hidden collector reached login/auth UI.

### 9. Guard manual refresh for expired sessions

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/AIUsageAppShell.kt`.
- [ ] If the selected provider is `SESSION_EXPIRED`, route manual refresh to the existing connect/login flow instead of enqueueing hidden collector.

Implementation intent:

```kotlin
if (snapshot.connectionState == ProviderConnectionState.SESSION_EXPIRED) {
    connectProvider(providerId)
    return
}
```

Placement:

- Put this guard at the start of the existing provider refresh action path.
- Keep background automatic refresh exclusion in `ForegroundRefreshPolicy`; this manual guard is for user-initiated refresh buttons.

### 10. Preserve snapshot codec compatibility

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/providers/ProviderSnapshotCodecTest.kt`.
- [ ] Add encode/decode coverage for `SESSION_EXPIRED` with previous lines.
- [ ] Confirm older snapshots without `SESSION_EXPIRED` still decode as before.

Expected test intent:

```kotlin
@Test
fun sessionExpiredSnapshotRoundTrips() {
    val snapshot = ProviderUsageSnapshot.sessionExpiredKeepingPrevious(
        providerId = ProviderId.COPILOT,
        previous = ProviderUsageSnapshot.connected(
            providerId = ProviderId.COPILOT,
            lines = listOf(
                ProviderUsageLine(
                    label = "Completions",
                    used = 12,
                    limit = 100,
                    resetsAt = null,
                    detail = null,
                ),
            ),
            message = "Usage updated",
        ),
        message = "Copilot login session expired. Sign in again.",
    )

    val encoded = ProviderSnapshotCodec.encode(listOf(snapshot))
    val decoded = ProviderSnapshotCodec.decode(encoded).single()

    assertEquals(ProviderConnectionState.SESSION_EXPIRED, decoded.connectionState)
    assertEquals(snapshot.lines, decoded.lines)
}
```

### 11. Confirm login-page detection coverage

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/providers/ProviderWebCollectorScriptsTest.kt` only if current tests do not already cover these cases.
- [ ] Cover these URL shapes:

```kotlin
ProviderWebCollectorScripts.isRefreshLoginPage(
    ProviderId.CLAUDE,
    "https://claude.ai/login",
)
ProviderWebCollectorScripts.isRefreshLoginPage(
    ProviderId.CODEX,
    "https://chatgpt.com/auth/login",
)
ProviderWebCollectorScripts.isRefreshLoginPage(
    ProviderId.COPILOT,
    "https://github.com/login",
)
ProviderWebCollectorScripts.isRefreshLoginPage(
    ProviderId.COPILOT,
    "https://github.com/session",
)
```

- [ ] Confirm `shouldRunCollector()` stays false for login/auth URLs.

### 12. Verification commands

- [ ] Run focused unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderUsageSnapshotTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.sync.ForegroundRefreshPolicyTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderWebCollectorScriptsTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderRefreshPlanTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderSnapshotCodecTest"
```

- [ ] Run full unit test suite:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected result:

- Session-expired provider with previous usage is not auto-refreshed.
- Session-expired provider still displays previous usage lines.
- Session-expired provider presents reconnect/connect as the primary action.
- Existing transient failure behavior still preserves previous usage as `STALE`.
- Hidden collector start URLs remain non-login URLs.

### 13. Runtime validation

- [ ] Use an emulator or real device with an existing provider session.
- [ ] Connect one provider and collect usage once.
- [ ] Force provider session expiry by clearing provider web auth state or signing out in the provider WebView.
- [ ] Trigger manual refresh.
- [ ] Confirm the app detects login/auth page and marks provider `SESSION_EXPIRED`.
- [ ] Wait longer than `ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS`.
- [ ] Confirm automatic foreground refresh does not repeatedly enqueue the expired provider.
- [ ] Tap reconnect/connect and confirm the login flow opens.
- [ ] Re-authenticate and confirm usage collection can run again.

Runtime docs must not record raw cookies, tokens, auth headers, account IDs, OAuth codes, full provider HTML, emails, or usernames.

### 14. Documentation updates after implementation

- [ ] Update `D:/Vibe Project/AI Usage for Mobile/docs/qa/store-apk-parity-gap-tracker-2026-05-20.md` with the current-app mitigation:
  - session-expired refresh loop blocked,
  - not a Store APK collector parity completion,
  - final collector parity still blocked by missing decrypted runtime evidence.
- [ ] Update `D:/Vibe Project/AI Usage for Mobile/docs/qa/store-apk-analysis-journal-2026-05-20.md` with test results and runtime validation summary.
- [ ] Update `D:/Vibe Project/AI Usage for Mobile/docs/qa/store-apk-context-handoff-2026-05-20.md` only if the next action changes.

## Risk Notes

- Adding a new persisted enum value is safe for current snapshots because older app versions will not be asked to decode future data in normal app flow. Current codec still needs a round-trip test.
- Keeping `STALE` auto-refreshable preserves behavior for transient failures.
- The main behavior change is that confirmed login/auth redirects stop silent retry and require reconnect.
- UI text may need Korean or existing English style alignment depending on current app copy. Do not add verbose explanatory UI text.

## Self-Review

- Spec coverage: this plan addresses the current refresh loop without pretending Store APK collector evidence is complete.
- Data integrity: no hardcoded provider usage values are introduced.
- Privacy: no tokens, cookies, auth headers, account identifiers, emails, usernames, or full provider HTML are written.
- Placeholder scan: no placeholder markers are required for implementation.
- Test coverage: state model, refresh policy, login-page detection, snapshot codec, and full unit suite are covered.
