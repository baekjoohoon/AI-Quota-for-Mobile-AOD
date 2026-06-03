# AI Quota 계정 및 데이터 삭제 안내

최종 업데이트: 2026년 6월 1일

AI Quota는 별도의 username/password 계정을 생성하지 않습니다. provider 연결과 quota snapshot은 Android 기기 안에서 처리되며, Gemini와 Antigravity 같은 지원되는 Google OAuth provider의 경우 token exchange, token refresh, quota collection을 위해 Firebase Functions를 사용할 수 있습니다.

## 앱 또는 기기 설정에서 삭제할 수 있는 데이터

- provider 연결 상태
- provider별 표시용 quota snapshot
- 위젯 및 상태 표시줄 알림 표시 cache
- provider별 WebView 세션 데이터 또는 OAuth 기반 로컬 세션 상태
- 지원되는 OAuth provider를 위해 생성된 server-side provider secret record
- 적용 가능한 임시 OAuth state record

## 기기에서 직접 삭제하는 방법

- AI Quota 앱에서 provider 연결 해제를 실행합니다.
- Android 설정에서 AI Quota 앱 데이터를 삭제합니다.
- AI Quota 앱을 제거합니다.

지원되는 OAuth provider의 연결을 해제하면 로컬 credential이 삭제되고, 지원되는 경우 Firebase backend의 provider-scoped server record 삭제를 요청합니다.

## 개발자에게 삭제 요청하기

추가 삭제 요청이 필요한 경우 `datell1357@naver.com` 으로 이메일을 보내주세요. 제목은 `AI Quota data deletion request` 로 작성하면 됩니다.

관련 Firebase 인증 record를 찾기 위해 필요한 경우 Google 이메일 주소 또는 provider 계정 식별 정보를 요청할 수 있습니다. 삭제 요청은 수동으로 확인되며, 보안, 사기 방지, 법적 또는 운영상 필요한 보관 사유가 없는 한 합리적인 기간 안에 처리됩니다.

## provider 계정 데이터

Claude, Codex, Gemini, GitHub Copilot, Antigravity, Cursor 등 provider가 보관하는 계정, 결제, 구독 데이터는 AI Quota에서 삭제할 수 없습니다. 해당 정보는 각 provider의 계정 설정 또는 개인정보 처리 절차를 이용해야 합니다.
