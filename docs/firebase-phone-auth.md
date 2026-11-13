Firebase Google 계정 · SMS 전화번호 인증 통합 기록

1. 작업 기준

   - 경로: `/Users/daeseong/Projects/safepath_front`
   - 원격: `https://github.com/SafePath7/safepath_frontend.git`
   - 브랜치: `feature/login-phone`
   - HEAD: `3db50c4ef2887f87f5d5b1188d2a6cdf01e95cca`
   - 작업 시작 시 `.idea/misc.xml`에 JDK 설정 변경이 있었으며 그대로 보존했다.
   - Android Studio의 프로젝트 경로·브랜치도 위 값과 일치함을 확인했다.
   - commit, push, merge, 브랜치 변경, 기기 설치, 실제 SMS 발송은 수행하지 않았다.

2. 기존 구조와 확인한 계약

   - 휴대폰 앱은 `:app`, Wear OS 앱은 `:wear`. 앱 namespace/applicationId는 `com.example.safepath_test1`이다.
   - Google 로그인은 `auth/GoogleAuthManager.kt`의 Credential Manager → Google ID token → GoogleAuthProvider credential → Firebase Google 로그인 흐름이다. 이 파일과 로그인 동작은 변경하지 않았다.
   - `SafePathApp.kt`는 Firebase 로그인 상태와 `safe_path_profile.onboarded_uid`를 비교해 회원가입 화면/메인 화면을 선택한다. Firebase `isNewUser`나 서버 회원 조회 응답으로 신규 회원을 판단하지 않는다.
   - 기존 SignupScreen은 이름과 전화번호 길이를 확인한 뒤 `user_name`, `user_phone`, `onboarded_uid`를 기기에 저장했다. SMS 본인 인증은 없었다.
   - Spring Boot 회원 등록/회원 조회 API 호출은 없다. 저장소의 `backend/`는 Node.js 기반 Firebase 친구 API다. 기존 친구 API의 Firebase ID token 전달과 호출 흐름을 유지했다.
   - `google-services.json`의 Firebase 프로젝트는 `sidepj-ba5ab`, Android 패키지는 앱과 일치한다. 기존 backend 프로젝트 설정, Gradle 생성 project_id, Google OAuth web client ID도 일치함을 확인했다. Console의 실제 운영 설정은 직접 조회하지 않았으며 사용자가 완료했다고 제공한 설정을 전제로 한다. 설정 파일을 교체하지 않았다.
   - Firebase BoM 34.19.0, firebase-auth, Credential Manager, lifecycle-viewmodel-compose, lifecycle-runtime-compose, JUnit 4.13.2가 이미 있어 Gradle 의존성 추가/변경은 없다.
   - 이전 구현은 `/Users/daeseong/Projects/spft`에서 발견했다. 상태/오류 분리와 계정 연결 구조를 참고하고 현재 프로젝트의 전화번호 규칙, 재발송 제한, 서버 확인, 토큰 갱신, 생명주기 및 테스트를 보강했다.

3. 파일별 변경

   | 파일 | 변경 |
   | --- | --- |
   | `app/src/main/java/com/example/safepath_test1/ui/login/LoginPage.kt` | Google 로그인 UI/동작을 유지하고 기존 SignupScreen을 별도 파일로 이동. 사용하지 않는 import 정리. |
   | `app/src/main/java/com/example/safepath_test1/ui/login/SignupScreen.kt` | 기존 이름/권한 안내/시작하기 배치를 유지하면서 SMS 발송, 6자리 입력, 확인, 재발송, 대기시간, 진행/결과 표시 추가. 인증 전 시작하기 비활성화. Activity 복귀 시 콜백 재연결. |
   | `app/src/main/java/com/example/safepath_test1/ui/login/SignupViewModel.kt` | 계정 확인/발송/코드 입력/연결/성공/실패 상태와 화면 상태 관리. 번호 변경·계정 변경·이탈 시 무효화. 서버 확인된 기존 연결 복원 및 최종 완료 가드. |
   | `app/src/main/java/com/example/safepath_test1/ui/login/SignupPhoneBackend.kt` | Firebase SDK 어댑터, Google 사용자 확인, 네 가지 SMS 콜백, 서버 reload, linkWithCredential, 재인증/번호 갱신, 강제 ID token 갱신, 민감정보 없는 오류 분류. |
   | `app/src/main/java/com/example/safepath_test1/ui/login/PhoneAccountLinker.kt` | 최초 연결·기존 연결 처리와 UID/번호 검증을 하나의 테스트 가능한 경로로 통합. 전화번호로 sign-in하는 기능은 제공하지 않음. |
   | `app/src/main/java/com/example/safepath_test1/auth/PhoneAuthManager.kt` | verificationId/ForceResendingToken/credential의 메모리 관리, 세션 세대 번호, 60초 제한, 자동 인증 타임아웃, Activity 콜백 복귀 및 watchdog. |
   | `app/src/main/java/com/example/safepath_test1/auth/PhoneNumber.kt` | 기존 친구 번호 정규화 함수를 재사용하고 입력을 엄격히 검증. 국내 010, +82, 0082를 +8210… E.164로 변환. |
   | `app/src/test/java/com/example/safepath_test1/auth/PhoneAuthManagerTest.kt` | SMS 세션/콜백/재발송/번호·계정 변경/타임아웃 테스트 15개. |
   | `app/src/test/java/com/example/safepath_test1/ui/login/PhoneAccountLinkerTest.kt` | 계정 연결/UID/중복 연결/번호 교체/충돌/토큰 갱신 실패 테스트 8개. |
   | `app/src/test/java/com/example/safepath_test1/ui/login/SignupViewModelTest.kt` | 가입 완료 가드, 수동·자동 인증 공통 흐름, 복원/오류/경합 테스트 21개. |
   | `docs/firebase-phone-auth.md` | 분석 결과, 구현 범위, 검증 결과, 기기 테스트 및 후속 연동 안내. |

4. 실행 흐름

   1. 기존 Google 로그인으로 Firebase 사용자를 인증한다.
   2. 기존 `onboarded_uid`가 현재 UID와 같으면 기존대로 메인 화면으로 진입한다. 기존 회원에게 이번 변경으로 SMS를 강제하지 않는다.
   3. 회원가입 화면에서는 `reload()`와 `getIdToken(true)`로 계정의 현재 연결 상태를 확인한다. Firebase 서버에서 연결된 번호가 확인되면 SMS를 반복하지 않는다. 캐시의 번호나 로컬 verified 플래그만으로 성공 처리하지 않는다.
   4. 연결된 번호가 없으면 국내 010 번호를 입력한다. 기존 친구 계약에 맞춰 현재 구현은 한국 휴대전화만 지원한다. 기타 국가 번호는 후속 범위다.
   5. 발송 버튼에서 E.164 번호와 현재 Google UID를 고정한 세션으로 `verifyPhoneNumber`를 호출한다.
   6. `onCodeSent`는 verificationId와 재발송 token을 메모리에 보관하고 코드 입력을 허용한다. 발송 자체는 인증 성공이 아니다.
   7. 수동 입력은 6자리 ASCII 숫자를 확인해 credential을 만든다. 자동 인증의 `onVerificationCompleted`도 동일한 연결 경로로 전달된다.
   8. 연결 및 서버 재확인·토큰 갱신이 모두 성공한 뒤 VERIFIED가 된다. 이름이 있어야 시작하기 버튼을 사용할 수 있다.
   9. 시작하기에서 UID와 입력 번호/연결 번호를 다시 비교한 뒤 기존 로컬 프로필 저장과 메인 화면 이동을 수행한다. 서버 회원 등록을 수행한 것은 아니다.

5. Firebase 연결과 예외 처리

   - Google provider가 있는 현재 사용자의 `linkWithCredential(phoneCredential)`을 사용한다. PhoneAuthCredential로 `signInWithCredential()`을 호출하지 않는다.
   - 최초 연결 응답, reload 결과, 현재 사용자 UID와 전화번호가 모두 요청 시 값과 같아야 한다. `getIdToken(true)`가 실패해도 완료 처리하지 않는다.
   - `ERROR_PROVIDER_ALREADY_LINKED`이면 먼저 서버 상태를 조회한다. 동일 번호는 `reauthenticate(credential)`로 새 코드를 검증한다. 다른 번호는 `updatePhoneNumber(credential)`로 현재 계정의 번호를 교체한다. 어느 경우도 다른 계정으로 로그인하거나 계정을 병합하지 않는다.
   - 다른 계정에 연결된 번호는 충돌 메시지를 표시하고 완료를 차단한다. 잘못된 코드, 만료, 네트워크, 요청 과다, 최근 로그인 필요, 계정 변경도 분류한다.
   - 번호 수정/계정 변경/이탈/재발송 시 이전 콜백을 무효화한다. 서버가 이미 접수한 SMS나 계정 연결 요청 자체는 취소할 수 없지만 늦은 응답으로 화면의 인증/가입 완료를 승인하지 않는다.
   - 이미 진행 중인 발송/연결 동안 중복 버튼 동작을 차단한다. 입력 번호를 변경해도 현재 ViewModel의 60초 대기시간은 유지한다. 프로세스 종료 후에는 메모리 세션과 대기시간이 사라지며 Firebase 서버의 발송 제한은 계속 적용된다.
   - 자동 검색 타임아웃은 SMS 코드 만료와 구분한다. 수동 입력을 계속 허용하며 실제 만료는 Firebase 오류 응답으로 처리한다.
   - Activity onStop으로 분리된 콜백은 원래 자동 검색 시간 안에서만 force token 없이 재연결한다. 시간이 지난 뒤 화면 복귀가 자동으로 새 SMS를 발송하지 않도록 한다.
   - SMS 코드, credential, verificationId, resend token, ID token을 로그 또는 앱 영구 저장소에 기록하지 않는다. Firebase SDK 자체의 기존 로그인 세션 관리는 유지한다. 프로세스 종료로 미완료 세션을 잃으면 새 요청이 필요하다.

6. 검증 결과

   ```bash
   ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
   ```

   - 최종 결과: BUILD SUCCESSFUL.
   - 전체 앱 테스트 71개 통과, 실패 0, 오류 0. 기존 27개와 신규 인증 테스트 44개다.
   - 테스트는 가짜 SMS gateway, 가짜 계정 연산, 가짜 backend 및 제어 가능한 시계를 사용한다. 실제 Firebase/SMS 서버를 호출하지 않는다. Firebase SDK 어댑터의 네트워크 동작과 실제 기기 UI는 이 JVM 테스트만으로 검증되지 않는다.
   - `git diff --check` 통과.
   - HTML 결과: `app/build/reports/tests/testDebugUnitTest/index.html`.
   - Gradle wrapper가 사용자 홈의 캐시/잠금 파일에 접근해야 하므로 샌드박스 밖 실행 권한을 사용했다. 프로젝트 Gradle/JDK 설정은 변경하지 않았다.

7. Android 휴대폰 테스트 절차

   1. Android Studio에서 현재 브랜치의 `app`을 기기에 설치한다. 이 작업에서는 설치하거나 앱의 발송 버튼을 누르지 않았다.
   2. 기존 온보딩 기록이 없는 별도 Google 테스트 계정으로 로그인한다. Firebase Console Authentication에서 Google provider와 최초 UID를 확인한다.
   3. Console에 등록한 테스트 전화번호와 고정 6자리 코드를 사용한다. 이 방식은 실제 SMS 없이 검증할 수 있다. Firebase 설정을 교체하거나 앱 검증을 끄는 코드를 추가할 필요는 없다.
   4. 발송 전/발송 중/6자리 입력 전/계정 연결 중에 시작하기가 비활성화되는지 확인한다. 잘못된 코드는 오류를 표시하고 가입을 허용하지 않아야 한다.
   5. 올바른 코드로 완료한다. Console에서 동일 UID에 Google 및 Phone provider와 입력 번호가 연결되는지 확인한다. UID나 ID token을 앱 로그에 출력하지 않는다.
   6. 60초 내 재발송/연속 탭이 차단되는지, 60초 뒤 재발송 가능한지 확인한다. 번호를 수정하면 이전 성공과 입력 코드가 초기화되어야 한다.
   7. 화면 회전, SMS 앱 방문 후 복귀, 자동 검색 타임아웃 후 수동 입력을 확인한다. 프로세스를 종료한 뒤 재시작하면 미완료 코드 세션은 재사용하지 않아야 한다.
   8. 연결 성공 후 시작하기를 누르기 전에 앱을 종료한다. 다시 열면 서버에 연결된 번호를 확인해 불필요한 SMS를 요구하지 않아야 한다. 시작하기까지 완료한 계정은 기존 흐름대로 메인 화면에 진입해야 한다.
   9. Google 테스트 계정 A에 연결한 번호를 계정 B에서 연결해 충돌을 확인한다. 기존 A 계정으로 전환되거나 B의 UID가 변경되어서는 안 된다. 계정 변경 후에는 이전 코드로 완료할 수 없어야 한다.
   10. 같은 계정의 동일 번호 재인증 및 다른 번호 교체도 확인한다. 동일 번호라도 새 수동 인증을 요청했다면 틀린 코드로 통과해서는 안 된다.
   11. 자동 인증은 통신사/기기에 따라 지원 여부가 다르다. 자동 콜백 공통 로직은 단위 테스트로 확인했으나 실제 자동 검색/Play Integrity/reCAPTCHA 복귀는 별도 기기 검증이 필요하다. 실제 번호 발송은 사용자가 필요 시 별도로 수행한다.

8. 후속 백엔드 연동

   - Spring Boot 팀과 회원 조회/등록/수정 계약 및 완료 상태의 기준을 먼저 확정한다. 이번 변경은 새 API 주소나 요청 형식을 임의로 추가하지 않았다.
   - 회원 등록 시 강제 갱신한 Firebase ID token을 HTTPS 인증 헤더로 전달하고 서버에서 Firebase Admin SDK로 검증하도록 연동한다. 토큰/클라이언트의 verified 값을 DB의 본인 인증 사실로 그대로 신뢰하지 않는다.
   - 서버에서 UID와 Firebase의 검증된 전화번호를 대조하고 전화번호 중복 정책 및 가입 요청 멱등성을 적용한다. Google 로그인 provider만으로 전화번호 연결 여부를 판정하지 않는다.
   - 서버 회원 조회/등록 응답을 기준으로 `SafePathApp`의 신규/기존 회원 분기와 최종 완료 처리를 교체한다. 로컬 `onboarded_uid`는 화면 편의를 위한 상태만 맡아야 한다.
   - Firebase 연결 성공 후 회원 등록이 실패할 수 있으므로 두 단계를 구분하고, 재시도 시 이미 연결된 번호를 재사용하는 복구 흐름이 필요하다.
   - 기존 회원 중 전화번호가 없는 계정, 다른 기기 로그인, 전화번호 교체 시 서버 DB 갱신 및 기존 로컬 프로필 데이터의 이관 정책을 확정한다.
   - 이 백엔드 연동과 실기기 통합 검증은 미완료 범위다. 친구/보호자/지도/경로/SOS 및 backend 소스는 수정하지 않았다.

참고: [Firebase Android 전화번호 인증](https://firebase.google.com/docs/auth/android/phone-auth), [Firebase 계정 연결](https://firebase.google.com/docs/auth/android/account-linking).
