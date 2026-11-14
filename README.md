<div align="center">

# 🛡️ SafePath

### 당신의 귀갓길에, 한 겹의 안심을 더하다

**안전시설을 살펴보고, 나에게 맞는 귀갓길을 선택하세요.**

출발지부터 도착까지, SafePath가 함께합니다.

![Android](https://img.shields.io/badge/Android-API%2024%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)
![Status](https://img.shields.io/badge/Status-Prototype-1B4965)

</div>

---

> **SafePath는 개발 중인 프로토타입입니다.** 시설 기반 지표는 실제 범죄 위험이나 안전을 보장하지 않습니다. SOS는 Android 공유 화면에서 사용자가 직접 앱과 수신자를 선택하는 방식입니다.

## ✨ SafePath가 함께하는 순간

낯선 길을 걸을 때, 얼마나 걸리는지뿐 아니라 **주변에 어떤 안전시설이 있는지**도 알고 싶을 때가 있습니다. SafePath는 지도와 공공시설 데이터를 바탕으로 보행 경로를 비교하고, 귀가 준비부터 위치 공유까지 연결합니다.

```text
   장소 검색             경로 비교              출발 준비             귀가
 📍 목적지 선택   →   🗺️ 세 가지 경로   →   🔐 인증·보호자 선택   →   🏠 이동
```

## 📱 화면 미리보기

| 홈 지도 | 경로 비교 | 출발 준비 |
| :---: | :---: | :---: |
| ![SafePath 홈 지도](docs/ui-review/home.png) | ![SafePath 경로 비교](docs/ui-review/routes.png) | ![SafePath 출발 준비](docs/ui-review/departure.png) |

<sub>저장소에 포함된 UI 리뷰 이미지입니다. 실제 화면은 앱 버전, 설정 및 데이터에 따라 다를 수 있습니다.</sub>

## 🧭 주요 기능

| 기능 | 설명 |
| --- | --- |
| **홈 지도** | 현재 위치와 주변 안전시설을 지도에서 확인합니다. |
| **목적지 검색** | Kakao 장소 검색으로 출발지와 목적지를 선택합니다. |
| **세 가지 경로** | 시설 우선·가장 빠른·추천 경로의 시간과 거리를 비교합니다. |
| **시설 기반 평가** | 경로 주변의 CCTV, 가로등, 경찰서, 비상벨, 소방서 자료를 이용합니다. |
| **출발 준비와 인증** | 보호자를 선택하고, 사용 가능한 기기에서는 생체인증을 사용합니다. 생체인증을 사용할 수 없으면 비밀번호를 사용합니다. |
| **위치와 SOS 공유** | Android 공유 화면에서 현재 위치 또는 긴급 문구를 직접 공유합니다. |
| **친구와 보호자** | 친구 요청 링크 공유 및 보호자 선택을 지원합니다. |
| **Wear OS 연동** | 휴대폰의 경로 상태를 시계에 전달하고, 시계의 센서 데이터를 휴대폰으로 전달합니다. |

## 🗺️ 경로는 어떻게 선택하나요?

Mapbox 보행 경로와 안전시설 위치를 바탕으로 후보를 비교합니다.

| 경로 | 선택 기준 |
| --- | --- |
| ⚡ **가장 빠른 경로** | 후보 중 예상 이동 시간이 가장 짧은 경로 |
| 🛡️ **시설 우선 경로** | 허용 우회 범위 안에서 시설 기반 점수가 가장 높은 경로 |
| ✨ **추천 경로** | 시설 점수와 시간 효율을 함께 반영한 경로 |

시설 우선 경로의 초기 우회 제한은 **가장 빠른 경로 대비 20% 이내이면서 추가 5분 이내**입니다. 추천 경로는 시설 점수 65%, 시간 효율 35%를 사용하며, 점수가 비슷한 별도 후보가 있으면 다른 경로를 우선합니다. 실제 후보가 부족하거나 기준에 맞는 대안이 없으면 선택 결과가 같을 수 있습니다.

시설 점수는 CCTV 25%, 가로등 35%, 경찰서 15%, 비상벨 20%, 소방서 5%의 **검증 전 임시 가중치**를 사용합니다. 경로 주변 분포와 시설까지의 직선거리 등을 반영하지만, CCTV 촬영 방향·조도·시설 운영 여부·실제 보행 접근성은 확인하지 못합니다. 일부 자료를 불러오지 못하면 부분 평가 또는 평가 불가로 표시될 수 있습니다.

## 📍 안전시설 데이터

| 시설 | 현재 데이터 | 지역 정보 |
| --- | --- | --- |
| CCTV | 앱에 포함된 CSV | 제공 범위와 완전성 확인 필요 |
| 가로등 | 대구공공시설관리공단 공공데이터 API | 대구 자료 |
| 경찰서·파출소 | 앱에 포함된 CSV | 검색·확인된 목록, 전국 전체 여부 확인 필요 |
| 비상벨 | 행정안전부 공공데이터 API | 지자체별 제공 범위 확인 필요 |
| 소방서 | 생활안전지도 API | 제공 범위 확인 필요 |

지도에 표시된 시설 위치를 평가 자료로 사용합니다. 실제 시설 위치와 데이터의 최신성은 제공 기관의 자료에 따라 달라질 수 있습니다.

## 🛠️ 기술 스택

| 영역 | 기술 |
| --- | --- |
| Android | Kotlin · Jetpack Compose · Material 3 · ViewModel |
| 지도와 경로 | Kakao Maps SDK · Kakao Local REST API · Mapbox Directions API |
| 인증과 통신 | Firebase Authentication · Credential Manager · Retrofit |
| 시설 데이터 | 공공데이터포털 API · 생활안전지도 API · CSV |
| Wear OS | Wear Compose · Google Play services Wearable Data Layer |
| 빌드 | Gradle Kotlin DSL · Gradle Wrapper |

휴대폰 앱은 **Android API 24 이상**을 대상으로 합니다. 생체인증은 기기의 지원 여부와 등록 상태에 따라 사용할 수 있습니다.

## 🚀 시작하기

### 1. 저장소 열기

```bash
git clone https://github.com/SafePath7/safepath_frontend.git
cd safepath_frontend
```

Android Studio에서 프로젝트 루트를 열고 Gradle을 동기화합니다. Android SDK와 JDK 17 이상을 준비하세요.

### 2. 로컬 설정

프로젝트 루트의 `local.properties`에 필요한 값을 설정합니다. Android Studio가 생성한 `sdk.dir`은 유지하세요.

```properties
# 지도 · 장소 검색 · 도보 경로
KAKAO_NATIVE_APP_KEY=your_kakao_native_app_key
KAKAO_REST_API_KEY=your_kakao_rest_api_key
MAPBOX_ACCESS_TOKEN=your_mapbox_access_token

# 가로등 · 비상벨 / 소방서
PUBLIC_DATA_API_KEY=your_public_data_api_key
SAFEMAP_API_KEY=your_safemap_api_key

# 사용 환경에 따라 설정
FRIENDS_API_BASE_URL=https://your-server.example/api
SAFEPATH_API_BASE_URL=https://your-server.example
```

`local.properties`는 Git에 올리지 않습니다. **앱에서 사용하는 키는 빌드 결과물에 포함될 수 있으므로**, 배포 전 서비스별 키 제한과 안전한 운영 방식을 확인하세요.

### 3. Firebase 설정

Google 로그인을 사용하려면 Firebase 프로젝트에 Android 패키지 `com.example.safepath_test1`을 등록하고 Google 로그인 및 서명키 지문을 설정합니다. 해당 Firebase 프로젝트의 `google-services.json`을 `app/src/google-services.json`에 배치하세요.

### 4. Android 앱 빌드

```powershell
# Windows
.\gradlew.bat :app:assembleDebug
```

```bash
# macOS / Linux
./gradlew :app:assembleDebug
```

Android Studio의 `app` 실행 구성을 이용해 기기 또는 에뮬레이터에 설치할 수 있습니다.

## 🧪 테스트

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

실제 API 응답, 기기 권한, 생체인증, 위치 공유와 지도 화면은 별도의 기기 검증이 필요합니다. Windows에서 프로젝트 경로에 한글이 포함된 경우 Gradle 경로 경고가 나타날 수 있습니다.

## 🗂️ 프로젝트 구조

```text
safepath_frontend/
├── app/                    # Android 휴대폰 앱
│   └── src/main/
│       ├── assets/         # 시설 CSV와 지도 마커
│       └── java/           # 지도 · 경로 · 인증 · 화면 · 연동
├── wear/                   # Wear OS 앱
├── backend/                # 친구 요청 관련 서버 구성
├── docs/ui-review/         # UI 리뷰 이미지
└── gradle/                 # Gradle Wrapper와 버전 카탈로그
```

## 📌 현재 범위

- 안전시설 점수는 시설 위치에 기반한 참고 지표입니다. 실제 안전이나 긴급 대응을 보장하지 않습니다.
- 귀가 세션과 일부 연동은 현재 로컬 테스트 구성을 포함합니다. 서버 환경에 따라 동작 범위가 달라집니다.
- SOS는 사용자가 공유 대상을 선택하는 방식이며, 자동 신고 또는 보호자 자동 알림으로 설명하지 않습니다.
- 워치 센서 데이터의 AI 분석 서버 연동은 현재 구현 범위에 포함되지 않습니다.

---

<div align="center">

**SafePath** · 더 안심할 수 있는 귀갓길을 향해

</div>
