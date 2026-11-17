# 백엔드 연결 코드

- `AccountApi.kt`: 로그인과 회원 API 및 오류 메시지
- `BackendApi.kt`: 보호자, 귀가 세션, 알림, 위험 기록과 첨부 API
- `AuthenticatedBackend.kt`: Firebase 토큰과 HTTP 전송 설정
- `SafePathMessagingService.kt`: FCM 토큰 등록과 알림 수신

기존 호출부와 Android 매니페스트의 호환을 위해 Kotlin 패키지 이름은 `com.example.safepath_test1.api`로 유지한다. 화면과 ViewModel은 각 기능 폴더에 두며 별도 친구 서버와 지도·공공데이터 API는 이 백엔드와 구분한다.

전화번호 변경은 서버에서 아직 503으로 차단한다. 위치 WebSocket은 서버가 채널 생성·발견과 세션 연결을 완성한 뒤 연동해야 하므로 자동 접속하거나 위치 공유가 작동하는 것처럼 표시하지 않는다. 관리자 기능은 사용자 토큰의 표시용 관리자 클레임과 별개로 백엔드가 권한을 최종 확인한다.
