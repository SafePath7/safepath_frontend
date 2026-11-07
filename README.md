<div align="center">

> **개발 단계의 프로토타입입니다.** SOS는 사용자가 공유 앱과 수신자를 직접 선택하는 방식이며, 자동 신고나 보호자 자동 알림은 구현되어 있지 않습니다. 안전도는 제공된 시설 데이터에 기반한 참고 정보입니다.

## 주요 기능

- **경로 탐색과 비교** — Kakao 장소 검색과 Mapbox 도보 경로를 사용해 안전·최단·추천 경로를 비교합니다.
- **안전시설 분석** — 앱에 포함된 CCTV와 가로등 CSV를 이용해 경로 주변 시설과 현재 위치 주변 안전도를 표시합니다.
- **로그인과 친구 연결** — Firebase Google 로그인 또는 데모 진입을 지원합니다. 카카오톡으로 친구 요청 링크를 공유하고, 수락된 친구를 보호자 선택에 사용할 수 있습니다.
- **위치 및 SOS 공유** — 현재 위치 또는 긴급 문구를 Android 공유 시트로 전달합니다.
- **Wear OS 연동** — 휴대폰의 경로 상태를 시계에 표시하고, 시계의 가속도·자이로 센서 데이터를 휴대폰으로 전달합니다. 수신 데이터는 현재 로그로 기록하며 AI 분석 서버와 연결되어 있지 않습니다.

## 화면 미리보기

|                 홈                 |                경로 탐색                |                   친구                   |
| :---------------------------------: | :-------------------------------------: | :--------------------------------------: |
| ![홈 화면](docs/ui-review/home.png) | ![경로 화면](docs/ui-review/routes.png) | ![친구 화면](docs/ui-review/friends.png) |

> 이미지는 프로젝트에 포함된 UI 리뷰 자료입니다. 실제 실행 화면은 설정과 데이터에 따라 다를 수 있습니다.

## 기술 스택

| 영역             | 기술                                                                 |
| ---------------- | -------------------------------------------------------------------- |
| Android          | Kotlin, Jetpack Compose, Material 3, ViewModel                       |
| 지도와 경로      | Kakao Maps SDK v2, Kakao Local REST API, Mapbox Directions API       |
| 인증과 통신      | Firebase Authentication, Credential Manager, Google ID, Retrofit     |
| Wear OS          | Wear Compose, Google Play services Wearable Data Layer               |
| 친구 요청 백엔드 | Node.js 22, Express, Firebase Functions, Firestore, Firebase Hosting |
| 빌드             | Gradle Kotlin DSL, Gradle 9.5.0, Android Gradle Plugin 9.3.2         |

휴대폰 앱은 Android API 24 이상, Wear 앱은 API 30 이상을 대상으로 합니다. 두 모듈의 compile / target SDK는 37, Java 바이트코드 타깃은 11입니다. Gradle 실행에는 JDK 17을 권장합니다.

## 시작하기

### 1. 프로젝트 열기

```bash
git clone https://github.com/SafePath7/safepath_frontend.git
cd safepath_frontend
```

Android Studio에서 프로젝트 루트를 열고 Gradle을 동기화합니다. 저장소가 비공개인 경우 접근 권한이 필요합니다.

### 2. API 설정

프로젝트 루트의 `local.properties`에 다음 값을 추가합니다. Android Studio가 생성한 SDK 경로 등 기존 설정은 유지하세요. 이 파일은 Git에서 제외됩니다.

```properties
# Mapbox Directions API
MAPBOX_ACCESS_TOKEN=pk.your_mapbox_public_access_token

# Kakao Local REST API 및 Kakao Maps SDK
KAKAO_REST_API_KEY=your_kakao_rest_api_key
KAKAO_NATIVE_APP_KEY=your_kakao_native_app_key

# 친구 요청 API를 사용할 때 설정
FRIENDS_API_BASE_URL=https://your-firebase-hosting-domain/api
```

각 값은 동일한 이름의 Gradle 속성으로도 전달할 수 있습니다. 지도와 장소 검색에는 Kakao 키가, 도보 경로 조회에는 Mapbox 토큰이 필요합니다. 친구 요청 서버 설정은 [백엔드 안내](backend/README.md)를 참고하세요.

### 3. Google 로그인 설정

1. Firebase 프로젝트에 Android 패키지 `com.example.safepath_test1`을 등록하고 Authentication의 Google 로그인 제공자를 활성화합니다.
2. 앱 서명키에 맞는 SHA-1 / SHA-256 지문과 OAuth 설정을 등록합니다.
3. Firebase에서 내려받은 설정 파일을 `app/src/google-services.json`에 둡니다. 저장소에 포함된 설정과 다른 Firebase 프로젝트를 사용할 경우 해당 프로젝트의 파일로 교체하세요.

### 4. 빌드와 실행

```bash
# macOS / Linux
./gradlew :app:assembleDebug :wear:assembleDebug
```

```powershell
# Windows
.\gradlew.bat :app:assembleDebug :wear:assembleDebug
```

Android Studio에서 `app` 또는 `wear` 실행 구성을 선택해 설치할 수도 있습니다. Wear 연동을 확인하려면 같은 application ID의 두 앱을 페어링된 휴대폰과 시계에 설치하고 Google Play services를 사용할 수 있어야 합니다.

## 프로젝트 구조

```text
safepath_frontend/
├── app/                         # Android 휴대폰 앱
│   └── src/main/
│       ├── assets/              # CCTV · 가로등 CSV
│       └── java/                # 로그인, 지도, 경로, 친구, UI, Wear 연동
├── wear/                        # Wear OS 앱과 모션 센서 처리
├── backend/                     # 친구 요청 API, Firestore 규칙, Hosting 페이지
├── docs/ui-review/              # UI 캡처와 검토 기록
├── gradle/                      # Gradle Wrapper와 버전 카탈로그
└── settings.gradle.kts          # app · wear 모듈 구성
```

## 테스트

```bash
# Android 및 Wear 단위 테스트
./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest

# 연결된 Android 기기 또는 에뮬레이터에서 실행
./gradlew :app:connectedDebugAndroidTest

# 친구 요청 백엔드 테스트
cd backend/functions
npm ci
npm test
```

Windows에서는 `./gradlew` 대신 `.\gradlew.bat`을 사용하세요. 실제 카카오톡 공유와 링크 수락은 기기와 로그인 계정을 이용한 별도 검증이 필요합니다.

## 동작 범위와 데이터

- 위치 권한은 현재 위치 표시와 출발지 설정에 사용됩니다. 위치 공유는 사용자가 Android 공유 시트에서 직접 실행합니다.
- 보호자 및 일부 설정은 기기에 저장됩니다. 친구 요청 기능은 설정된 Firebase 백엔드를 사용합니다.
- 안전시설 정보는 `app/src/main/assets`의 CSV를 사용합니다. 데이터의 범위와 최신성에 따라 실제 환경과 차이가 날 수 있습니다.
- 워치 센서 데이터는 Wearable Data Layer의 최신 상태로 전달되며, 모든 샘플의 손실 없는 전송을 보장하지 않습니다.

백엔드 배포와 친구 요청 흐름에 관한 자세한 내용은 [backend/README.md](backend/README.md)를 확인하세요.
