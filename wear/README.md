# SafePath 워치 (Wear OS)

SafePath의 워치 앱과, 폰 앱에서 워치와 통신하는 부분을 설명한다.
워치는 폰이 보내는 길안내를 보여 주고, SOS·위험상황·센서·음성을 폰으로 보낸다.
워치는 인터넷을 직접 쓰지 않고 모든 통신은 폰을 거친다.

## 구성

| 위치 | 내용 |
| --- | --- |
| `wear/` | 워치 앱 모듈 |
| `wear-contract/` | 폰과 워치가 함께 쓰는 Data Layer 통신 계약(경로·키 상수)과 그 테스트 |
| `app/.../wear/` | 폰 앱에서 워치와 주고받는 코드 |

워치 모듈(`wear/src/main/.../wear/`) 패키지:

| 패키지 | 하는 일 |
| --- | --- |
| `comm` | 폰이 보낸 값 받기(`WatchDataReceiver`)와 해석(`StatusDecoder`, `MapImageLoader`) |
| `geo` | 거리·방향 계산, 경로 위 진행 위치(`RoutePath`) |
| `launch` | 앱이 꺼져 있을 때 앱 열기: 폰의 "워치 앱 열기"(`WatchLaunchListenerService`), 위험 확인 질문(`WatchRiskCheckListenerService`), 공용 열기(`WatchAppOpener`) |
| `presentation` | 화면(`MainScreen`, `GuidancePage`, `SafetyScreens`), 화면 상태(`WearAppState`), 부작용(`WearEffects`) |
| `sensors` | 센서·마이크 수집과 폰으로 보내기(`Watch*Sender`, `DataLayerSender`) |
| `state` | 화면과 떨어진 순수 상태 규칙(`AlertFlow`, `WatchState`) |

## 폰 ↔ 워치 통신

Wearable Data Layer의 **DataClient**만 쓴다. MessageClient는 실기기 테스트에서 실패했다.
DataClient는 폰과 워치의 **applicationId가 같아야** 동작한다(`com.example.safepath_test1`).

경로와 키는 반드시 `wear-contract`의 상수(`DataLayerPaths`, `*Keys`)를 쓴다.
문자열을 직접 적지 않는다. 예외로 매니페스트의 `pathPrefix`는 XML이라 문자열로 적고, 계약과 같아야 한다.

| 경로 | 방향 | 보내는 쪽 | 받는 쪽 | 내용 |
| --- | --- | --- | --- | --- |
| `STATUS` | 폰 → 워치 | `WearMessenger` | `WatchDataReceiver` | 길안내 상태, 경로, 현재 위치, 회전 안내 |
| `MAP` | 폰 → 워치 | `WearMapImageSender` | `WatchDataReceiver` | 지도 이미지와 투영 정보 |
| `RELEASE` | 폰 → 워치 | `WearMessenger.sendRelease` | `WatchDataReceiver` | 위험상황 해제 |
| `SOS_ACK` | 폰 → 워치 | `WearMessenger.sendSosAck` | `WatchDataReceiver` | SOS를 받았다는 답장 |
| `LAUNCH_WATCH` | 폰 → 워치 | `WatchLauncher` | `WatchLaunchListenerService` | 워치 앱 열기 |
| `RISK_CHECK` | 폰 → 워치 | `WearMessenger.sendRiskCheck` | `WatchDataReceiver`, `WatchRiskCheckListenerService` | 위험 확인 질문(eventId, riskType, requestedAt) |
| `SOS` | 워치 → 폰 | `WatchSosSender` | `WearSosRelayService` | SOS |
| `VOICE` | 워치 → 폰 | `WatchVoiceSender` | `WearVoiceRelayService` | 1초 음성 조각(AAC) |
| `MOTION` | 워치 → 폰 | `WatchMotionSender` | `WearDataRelayService` | 가속도·자이로 1초 묶음(20Hz, 배열) |
| `RISK_ANSWER` | 워치 → 폰 | `WatchRiskAnswerSender` | `WearRiskRelayService` | 확인 질문의 답(SAFE / HELP / NO_RESPONSE) |

DataClient는 내용이 같은 DataItem을 다시 보내지 않으므로, 보낼 때마다 시각 값(`updatedAt`, `triggeredAt` 등)을 담는다.

움직임은 워치가 20Hz로 읽어 1초씩 묶어 보낸다(`MotionBatcher`, 초당 한 번 전송).
폰은 최근 움직임을 `recentMotion`에 쌓아 두고, AI 분석 요청 때 음성 3초 창과 같은 구간을 꺼내 쓴다.
시각은 모두 Unix epoch 밀리초라 음성의 `capturedAt`과 바로 비교할 수 있다.
"보냈다" 로그는 워치나 폰의 Data Layer에 넣었다는 뜻이지 상대에게 도착했다는 뜻이 아니다.

## 위험상황 흐름

```
평소(None) ──폰의 RISK_CHECK / 방패 길게 누르기(테스트)──▶ 확인 질문(CheckIn, 30초)
   │                                    "괜찮아요" ◀─┤ (SAFE → 평소)
   │                                                  │ "도움 요청"(HELP) 또는 시간 초과(NO_RESPONSE)
   └────────────────────SOS 버튼───────────────────▶ 위험상황(Alerted)
                                                       │ 폰의 RELEASE
                                                       ▼
                                                     평소(None)
```

### 위험 확인 질문(RISK_CHECK)

폰이 `RISK_CHECK`를 보내면 워치는 확인 질문을 띄우고, 답을 `RISK_ANSWER`로 돌려준다.
HELP와 NO_RESPONSE일 때는 지금처럼 SOS도 함께 보낸다. 방패 길게 누르기로 띄운 질문은 폰에 답하지 않는다.

- 확인 질문이 뜨면 진동(0.3초 세 번)하고, 질문이 떠 있는 동안 화면을 켜 둔다(`CheckInAlertEffect`). 테스트 질문도 같다.
- 평소 상태일 때만 띄운다. 확인 질문 중이거나 위험상황이면 새 질문은 무시한다.
- 질문 시각이 30초(+시계 오차 5초)보다 오래됐으면 띄우지 않는다. 연결이 끊겼다가 늦게 온 질문이나
  Data Layer에 남은 옛 질문이 다시 뜨지 않게 하기 위해서다. 남은 시간은 워치에 띄운 때부터 센다.
- 이미 띄우거나 답한 eventId는 다시 띄우지 않는다. 앱을 다시 켜도 워치가 쓴 `RISK_ANSWER`로 알아본다.
- 앱이 화면에 없으면 `WatchRiskCheckListenerService`가 앱을 열고(바로 열기 + "괜찮으신가요?" 알림),
  열린 앱이 이미 와 있는 질문을 읽어 띄운다. 앱이 보이면 다시 열지 않는다(`WatchAppVisibility`).
- 폰은 지금 답을 로그로만 남긴다(`WearRiskRelayService`). 백엔드 반영은 다음 작업이다.

디버그 빌드의 폰에서 adb로 질문을 보내 시험할 수 있다(`WearRiskCheckDebugReceiver`, 릴리스에는 없음).

```powershell
adb -s <폰 시리얼> shell am broadcast -n com.example.safepath_test1/.wear.WearRiskCheckDebugReceiver --es eventId test-1 --es riskType FALL
```

같은 eventId는 다시 뜨지 않으므로 시험할 때마다 바꾸거나 `--es eventId`를 빼서 새 ID를 쓰게 한다.

위험상황에서는 SOS가 폰까지 갔는지를 함께 보여 준다(`SosDelivery`).

| 상태 | 언제 | 화면 문구 |
| --- | --- | --- |
| `Sending` | SOS를 보낸 직후 | 보호자에게 알리는 중 |
| `Delivered` | 폰의 `SOS_ACK`가 이번 SOS의 시각과 같음 | 보호자에게 알렸어요 |
| `NotConnected` | 연결된 폰이 없거나 10초 안에 답장이 없음 | 아직 전달되지 않았어요 |

`NotConnected`여도 SOS는 워치에 남아 있다가 연결되면 넘어가고, 그때 답장이 오면 `Delivered`가 된다.
재연결 직후에는 Data Layer가 느려 답장과 해제가 수십 초 늦을 수 있다.

## 작업 규칙

- **이름**: 워치 모듈의 구성요소는 `Watch*`, 폰에서 Data Layer로 워치와 주고받는 클래스는 `Wear*`를 쓴다.
  폰에서 워치 기기 자체를 다루는 것(`WatchLauncher`, `WatchAlertState`)은 `Watch*`다.
  워치 앱의 뿌리인 `WearApp`, `WearAppState`, `WearEffects`는 Wear OS 앱 이름을 따른다.
- **공개 범위**: 기본은 `internal`이다. 안드로이드가 직접 만드는 Activity·Service만 `public`으로 둔다.
- **주석**: 한국어, "~한다" 서술체. 왜 그렇게 했는지를 적는다.
- **로그**: 메시지는 영어, TAG는 클래스 이름.
- **줄 길이**: 100자(`.editorconfig`).
- **상태와 부작용**: 상태 규칙은 `state`의 순수 함수로 두고 단위 테스트한다.
  통신·센서·타이머 같은 부작용은 `WearEffects`와 `WearApp`에서만 한다.
- **테스트 이름**: `대상_기대결과` 형식(예: `requestHelp_startsAsSending`).
- **커밋**: `type(scope): 한국어 설명`. type은 `feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`,
  scope는 워치면 `wear`, 폰이면 `phone`.

## 빌드와 테스트

```powershell
.\gradlew.bat :wear-contract:testDebugUnitTest :wear:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

실기기에서는 폰(`app`)을 먼저 설치한 뒤 워치(`wear`)를 설치한다.
두 앱의 applicationId가 같아서, `wear` 실행 설정을 폰에 실행하면 폰 앱이 덮어써진다.

## 알려진 제한과 남은 일

- 워치 SOS는 폰 알림까지만 가고 아직 서버(보호자 알림)로 가지 않는다.
- 폰의 해제 본인 확인(생체인증·PIN)은 세션 시작 흐름과 아직 연결되지 않았다.
- 위험상황 화면의 "휴대폰에서 해제"를 길게 누르면 해제되는 테스트용 동작이 남아 있다(제거 예정).
- 두 점 사이 거리 계산(haversine)이 폰(`WearMapFrame.kt`)과 워치(`geo/GeoMath.kt`)에 따로 있다.
- 폰의 "워치 앱 열기"(`LAUNCH_WATCH`)는 앱이 이미 열려 있어도 화면을 새로 만들어 지금 상태(위험상황 등)를 잃을 수 있다
  (`CLEAR_TOP`만 쓰고 `SINGLE_TOP`이 없음). 위험 확인 질문의 앱 열기는 `SINGLE_TOP`을 써서 이 문제가 없다.
- 위험 확인 질문의 답은 폰 로그까지만 가고 아직 백엔드에 반영되지 않는다. 워치가 연결돼 있지 않아 답이 없는 질문의 처리도 아직 없다.
- `riskType` 값 목록과 위험 이벤트 ID 형식이 정해지지 않았다. 지금은 둘 다 문자열로 그대로 전달만 한다.
