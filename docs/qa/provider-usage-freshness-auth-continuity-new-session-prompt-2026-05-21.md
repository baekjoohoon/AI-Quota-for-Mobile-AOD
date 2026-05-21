# 새 세션 시작 프롬프트

아래 프롬프트를 새 Codex 세션에 그대로 전달한다.

```text
사용하지 않는 MCP들을 호출하지마. 필요한 경우에만 호출해.
답변과 문서 작성은 반드시 한국어로 작성해.

작업 위치:
D:\Vibe Project\AI Usage for Mobile

목표:
Provider usage freshness/auth continuity를 구현해줘. 세션 유지 자체보다 더 중요한 것은 수집 데이터 신뢰성이야. refresh 실패 뒤 과거 usage를 최신값처럼 계속 보여주면 안 되고, stale/last-known 상태를 명확히 표시해야 해. reset이 지난 volatile row는 제거해야 해.

먼저 읽을 문서:
1. docs/superpowers/specs/2026-05-21-provider-usage-freshness-auth-continuity-spec.md
2. docs/superpowers/plans/2026-05-21-provider-usage-freshness-auth-continuity.md
3. docs/qa/provider-usage-freshness-auth-continuity-handoff-2026-05-21.md
4. docs/qa/provider-usage-freshness-auth-continuity-progress-2026-05-21.md
5. docs/qa/store-apk-context-handoff-2026-05-20.md

진행 기록:
작업 중 의미 있는 코드 변경, 테스트 실행, 실패, 트러블슈팅, 결정사항은 반드시 아래 문서에 기록해.
docs/qa/provider-usage-freshness-auth-continuity-progress-2026-05-21.md

핵심 요구사항:
1. 이전 usage를 표시할 수는 있지만 반드시 stale/last-known으로 표시해야 한다.
2. reset이 지난 volatile row는 현재 usage로 표시하지 않는다.
3. connected=true는 trusted usage row 저장 성공 후에만 허용한다.
4. login URL 도착, OAuth redirect, session cookie 감지만으로 connected 처리하지 않는다.
5. refresh 전 provider별 auth/session probe를 먼저 실행한다.
6. WebView 기반 provider는 login과 collector가 같은 app WebView profile을 써야 한다.
7. Copilot은 OAuth/token 기반 native API 수집 우선으로 전환한다.
8. Gemini는 AppAuth + Code Assist quota API 방향을 유지한다.
9. Cursor는 token/API 우선, 안 되면 same WebView profile fallback으로 둔다.
10. Store APK collector parity는 runtime/decrypt evidence 전까지 완료로 주장하지 않는다.

Provider별 방향:
- Claude: WebView session 기반 유지
- Codex: WebView session 기반 유지
- Copilot: OAuth/token 기반 native API 우선
- Gemini: AppAuth + Code Assist quota API
- Cursor: token/API 우선, WebView session fallback

Redaction:
문서나 답변에 raw token, raw cookie, OAuth secret/callback code, auth header value, account identifier, email, username, full provider HTML을 쓰지 마.
이메일은 <email>, 사용자명은 <username>로 써.

실행 방식:
docs/superpowers/plans/2026-05-21-provider-usage-freshness-auth-continuity.md의 Task 1부터 TDD로 진행해.
각 task마다 focused test를 먼저 실패시키고, 최소 구현으로 통과시킨 뒤 progress journal을 갱신해.

최종 검증:
.\gradlew.bat :app:testDebugUnitTest
git diff --check
```
