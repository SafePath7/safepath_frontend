# 회원 API 로컬 연결

백엔드 `safepath-audit-20261113-back-develop`의 `README.md`에 따라 PostgreSQL, Firebase Admin 서비스 계정 및 전화번호 암호화 키를 준비하고 `local` 프로필로 서버를 실행한다. 백엔드 기본 포트는 8080이다. 서버 설정과 비밀 값은 Git에 올리지 않는다.

에뮬레이터 디버그 빌드는 별도 설정이 없으면 `http://10.0.2.2:8080`에 연결한다. USB 연결된 실제 휴대폰은 Android SDK의 `adb reverse tcp:8080 tcp:8080`을 실행하고 프론트엔드 `local.properties`에 아래 값을 추가한 뒤 앱을 다시 빌드 및 설치한다.

```properties
SAFEPATH_API_BASE_URL=http://127.0.0.1:8080
```

USB 대신 같은 로컬 네트워크를 사용하는 경우에는 `http://PC의_사설_IP:8080`으로 설정하고 서버와 방화벽이 휴대폰 연결을 허용해야 한다. 앱 재설치 전에 서버와 `adb reverse`가 실행 중인지 확인한다. Android 버전 및 기기 설정에 따라 로컬 네트워크 접근 권한도 필요할 수 있다. 디버그에서 HTTP는 localhost 및 사설 IPv4만 허용하며, 릴리스 빌드에는 HTTPS 주소가 필요하다. 이 값에는 `/api`를 붙이지 않는다.

회원 상태 조회는 Firebase ID 토큰과 Spring 서버 연결이 필요하다. 서버가 꺼져 있으면 로그인 이후 재시도 화면이 표시된다. 전화번호 변경은 백엔드에서 일회성 SMS 증명 검증을 지원하지 않아 현재 503을 반환하므로 앱에서 변경을 제공하지 않는다. 회원 탈퇴는 서버의 회원정보 삭제이며 Firebase 계정과 Firestore의 친구 정보 삭제는 포함하지 않는다.
