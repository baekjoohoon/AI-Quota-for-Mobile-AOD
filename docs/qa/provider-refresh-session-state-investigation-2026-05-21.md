# Provider Refresh Session State Investigation - 2026-05-21

## Scope

자동/수동 refresh 중 로그인 WebView 세션이 풀리거나, refresh 실패 후 만료된 usage row가 이전 snapshot에 남는 문제가 다른 provider에도 있는지 확인했다.

## Runtime Findings

- Claude
  - Hidden refresh가 `claude.ai/login` 또는 403 main-frame 응답으로 끝나는 케이스가 확인됐다.
  - 기존 Claude Session row는 reset 만료 후에도 이전 remaining percent와 섞여 표시될 수 있었다.
  - 조치: `claude:session` reset이 현재 시각보다 과거면 snapshot read 단계에서 제거한다.

- Codex
  - Hidden refresh는 `chatgpt.com/`까지 도달하지만 collector 내부 `/backend-api/wham/usage`가 401로 실패했다.
  - 기존 `codex:primary_window` reset 값 `1779298708`은 2026-05-20T17:38:28Z로 이미 만료였지만 snapshot에 남아 있었다.
  - 조치: `codex:primary_window` reset이 현재 시각보다 과거면 snapshot read 단계에서 제거한다.

- Copilot
  - Hidden refresh가 `github.com/login`으로 떨어지는 로그가 확인됐다.
  - 이는 inline usage 파싱 실패와 별개로, 현재 WebView 세션이 background refresh에서 인증된 GitHub 상태가 아니라는 신호다.
  - 조치: background refresh에서 GitHub login/two-factor/session URL을 만나면 30초 timeout을 기다리지 않고 세션 만료로 실패 처리한다.

- Cursor
  - Hidden refresh가 `github.com/login`, `authenticator.cursor.sh`로 이동하는 흐름이 확인됐다.
  - 조치: WorkOS/Cursor auth host와 Cursor의 GitHub login 경유도 세션 만료로 조기 감지한다.

- Gemini
  - 현재 refresh source는 `provider-api`이며 WebView hidden collector가 아니다.
  - 이번 WebView 세션 손실 문제와 같은 계열은 아니다.

## Code Changes

- `LocalUsageRepository`
  - volatile session window row 제거 로직 추가.
  - 현재 대상: `claude:session`, `codex:primary_window`.

- `ProviderWebCollectorScripts`
  - `isRefreshLoginPage(providerId, url)` 추가.
  - provider별 로그인/auth/2FA URL을 refresh 실패 신호로 분류.

- `BackgroundProviderWebCollector`
  - hidden refresh가 로그인/auth 페이지에 도달하면 즉시 `login session expired`로 실패 처리하고 다음 queued provider로 이동.

## Verification

- Unit test:
  - `LocalUsageRepositoryTest`
  - `ProviderWebCollectorScriptsTest`

- Build:
  - `:app:assembleDebug`

- Emulator:
  - debug APK 설치 성공.
  - UI hierarchy 기준 Codex는 expired `Codex Session` row가 제거되고 `Codex Weekly`만 표시됨.
  - logcat 기준 Copilot/Cursor login page 도달 시 timeout 없이 다음 provider로 넘어가는 흐름 확인.

## Remaining Work

- Copilot inline completions는 아직 실제 가변 payload 확보가 검증되지 않았다.
- Claude 5-hour session은 만료 stale row 제거는 되었지만, fresh payload 수집은 여전히 Claude WebView 세션/API 접근 안정화가 필요하다.
