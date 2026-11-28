# HiHana Evo에 기여하기

HiHana Evo는 하나고 학생이 만든 비공식 앱입니다. 코드는 **Pull Request(PR)** 로 받습니다. 저장소 관리자([@Kyoohan](https://github.com/Kyoohan))가 검토해서 합치고, 합친 변경은 다음 릴리스에 들어갑니다.

## 진행 순서

1. 이 저장소를 자기 계정으로 **Fork** 합니다.
2. 포크의 `main` 에서 작업 브랜치를 만듭니다. 이름 예: `fix/live-weekend`, `feat/meal-filter`.
3. 고치고, 아래 [빌드와 테스트](#빌드와-테스트)를 통과시킵니다.
4. `Kyoohan/hihana-neo` 의 `main` 으로 PR을 엽니다. 쓸 내용은 [PR에 적을 것](#pr에-적을-것)에 있습니다.
5. 검토 의견이 오면 같은 브랜치에 커밋을 더 올립니다. PR은 새로 열지 않아도 됩니다.

- 큰 기능이나 화면 구조를 바꾸는 일은 코드를 쓰기 전에 **이슈**로 먼저 이야기해 주세요. 방향이 안 맞아 작업이 버려지는 일을 막기 위해서입니다.
- PR 하나에는 주제 하나만 담아 주세요. 버그 수정과 리팩터링이 섞이면 검토가 오래 걸립니다.
- 이 저장소의 `main` 은 릴리스마다 `HiHana Evo 15.3` 같은 커밋 하나로 갱신됩니다. 그사이 바뀐 내용과 PR이 충돌하면 최신 `main` 위로 rebase를 부탁할 수 있습니다.

## 빌드와 테스트

비밀 키 없이 빌드됩니다. 2026-11-28에 새로 clone해서 안드로이드 디버그 빌드, 안드로이드 단위 테스트, iOS 시뮬레이터 빌드, iOS 단위 테스트(70개)가 모두 통과하는 것을 확인했습니다.

### 안드로이드 (`android/`)

- Android Studio 최신판(AGP 9.4, Gradle 9.7, compileSdk 37)으로 `android/` 폴더를 엽니다. JDK는 Android Studio에 들어 있는 것을 쓰면 됩니다.
- `local.properties` 는 Android Studio가 `sdk.dir` 을 넣어 만들어 줍니다. 커밋하지 않습니다.
  - `NEIS_KEY` 는 넣지 않아도 됩니다. 없으면 급식의 칼로리·영양·원산지만 비어 보입니다. 필요하면 [NEIS 교육정보 개방 포털](https://open.neis.go.kr)에서 개인 키를 받아 `local.properties` 에 `NEIS_KEY=…` 로 넣으세요.

```bash
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 단위 테스트
```

- 디버그 빌드는 **HiHana Evo (dev)** 라는 이름과 설치 ID `com.skh.hihanaevo.dev` 로 깔립니다. 정식 앱과 나란히 설치되고, 로그인·설정도 따로입니다.
- 디버그 빌드는 출시 빌드와 속도를 맞추려고 디버거를 붙일 수 없게 해 두었습니다. 디버거가 필요하면 `./gradlew installDebug -Pdebuggable` 처럼 `-Pdebuggable` 을 붙이세요.

### iOS·iPadOS (`ios/`)

- Mac, Xcode 27(2026-11-28 Xcode 27.2로 확인, 그 아래 버전은 확인하지 않음), [XcodeGen](https://github.com/yonaskolb/XcodeGen)이 필요합니다. 앱의 최소 지원 버전은 iOS 18입니다.

```bash
brew install xcodegen
cd ios
xcodegen generate              # project.yml → HiHanaEvo.xcodeproj (생성물은 커밋하지 않음)
open HiHanaEvo.xcodeproj
```

- 시뮬레이터에서는 개발자 계정(Team) 없이 돌아갑니다. 실행 인자 `-demo` 를 주면 로그인 없이 샘플 시간표로 볼 수 있습니다. 다른 인자는 [ios/README.md](ios/README.md)에 있습니다.
- 실제 아이폰에 올리려면 자기 Apple ID로 Team과 번들 ID를 바꿔야 합니다. 이 변경(`project.yml` 의 `DEVELOPMENT_TEAM`, `bundleIdPrefix`)은 **PR에 넣지 마세요**.
- 테스트는 아래 명령으로 돌립니다. 시뮬레이터 이름은 자기 Mac에 있는 것으로 바꾸세요.

```bash
xcodebuild test -scheme HiHanaEvo -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:HiHanaEvoTests CODE_SIGNING_ALLOWED=NO
```

## 실제 계정으로 시험할 때 — 꼭 지켜 주세요

이 앱은 **진짜 하이하나 포털**에 접속합니다. 개발 빌드도 마찬가지입니다.

- **신청·취소·수정, 댓글 쓰기·지우기는 시험 삼아 누르지 마세요.** 면학실·도서관·외출 신청과 댓글이 실제로 학교 시스템에 들어갑니다. 이 부분을 고쳤다면 PR에 "실제로 눌러 보지 않음"이라고 적어 주세요. 관리자가 확인합니다.
- 하이하나는 **비밀번호를 5번 틀리면 계정을 잠급니다.** 로그인 코드를 고칠 때 특히 조심하세요.
- 스크린샷, 로그, 테스트 데이터, 커밋에 **학번·이름·좌석 실명·사진 같은 개인정보를 넣지 마세요.** 스크린샷은 가리거나 `-demo` 화면을 쓰세요.
- AI 요약·브리핑·날씨는 관리자가 무료 한도로 운영하는 공용 서버(`summary-worker`)를 씁니다. 같은 요청을 반복해서 보내는 코드나 시험은 피해 주세요. `summary-worker/`·`classroom-bridge/` 를 고치는 PR은 환영하지만, **배포는 관리자가 합니다.**

## 코드 규칙

- **두 플랫폼은 같은 규칙을 따로 구현합니다.** 일과 계산은 `android/shared/…/Timetable.kt` ↔ `ios/HiHanaEvo/Core/Schedule.swift`, 실시간 일정은 `android/app/…/LiveDay.kt` ↔ `ios/HiHanaEvo/Core/LiveSegments.swift` 입니다. 한쪽만 고쳤다면 PR에 그렇다고 적어 주세요. 다른 쪽은 관리자가 맞춥니다.
- 일과·급식·실시간 일정 같은 계산을 바꾸면 테스트를 같이 넣어 주세요. 안드로이드는 `android/app/src/test/`, iOS는 `ios/Tests/` 에 넣습니다.
- 주석과 화면 문구는 한국어로 씁니다. 주석에는 "무엇을"보다 **왜** 그렇게 했는지를 적어 주세요.
- 화면 동작은 실제로 많이 쓰는 앱(삼성 One UI, iOS 기본 앱)을 기준으로 맞춥니다. 아이콘은 직접 그린 얇은 외곽선 아이콘을 씁니다. 다른 앱의 아이콘·이미지를 복사해 넣지 마세요.
- 다음은 관리자가 릴리스할 때 정하니 **건드리지 마세요**: 버전 번호(`versionCode`·`versionName`, `MARKETING_VERSION`), 변경 사항 목록(`Changelog`), `ios/source.json`, `.github/workflows/`.
- 비밀 값은 커밋하지 않습니다: `local.properties`, 키스토어, API 키, 토큰.
- 새 라이브러리를 넣을 때는 PR에 이유를 적어 주세요.

## PR에 적을 것

- **무엇을 왜** 바꿨는지 (고친 버그라면 어떤 상황에서 어떻게 틀렸는지)
- **어디서 확인했는지**: 기기·OS 버전, 에뮬레이터·시뮬레이터, 스크린샷(개인정보 가림)
- **테스트 결과**: 위 명령을 돌렸는지, 새로 넣은 테스트
- **한쪽 플랫폼만** 고쳤는지

## 그 밖에

- 저장소에는 아직 라이선스가 없습니다. PR을 보내면 그 변경을 이 앱에 넣어 배포하는 데 동의한 것으로 봅니다.
- 하나고·하이하나와 공식 관계가 없는 앱입니다. 짧은 간격으로 포털을 계속 조회하는 것처럼 학교 시스템에 무리를 주는 기능은 받지 않습니다.
