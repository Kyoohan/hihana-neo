# HiHana Evo — iOS (SwiftUI)

안드로이드 앱(`../android`)의 아이폰·아이패드판. SwiftUI, iOS 18 이상(iOS 26+ 에서 Liquid Glass).

## 만들고 실행하기

```bash
brew install xcodegen      # 한 번
cd ios
xcodegen generate          # project.yml → HiHanaEvo.xcodeproj
open HiHanaEvo.xcodeproj
```

시뮬레이터에서 계정 없이 훑어보려면 실행 인자를 줍니다(Scheme → Run → Arguments):

| 인자 | 뜻 |
| --- | --- |
| `-demo` | 샘플 시간표·면학 위치로 로그인 없이 |
| `-demoNow 14:20` | 오늘 그 시각인 것처럼 |
| `-axis 1` | 0 홈 · 1 타임라인 (아이폰) |
| `-route …` | 그 화면으로 바로 (`UI/Root/DebugRoutes.swift`) |
| `-q 수학` | 통합 검색에 검색어를 미리 |
| `-tour` | 처음 투어 |

테스트: `xcodebuild -scheme HiHanaEvo -destination 'platform=iOS Simulator,name=iPhone 18 Pro' -only-testing:HiHanaEvoTests test CODE_SIGNING_ALLOWED=NO`

## 구조

- `Core/` — 안드로이드 `shared/Timetable.kt` 와 같은 일과 계산(수업·면학·식사·쉬는 시간), 급식 규칙
- `Portal/` — 하이하나 포털(토큰 로그인, 면학 위치, 시간표는 숨은 웹뷰로 렌더한 표를 파싱), 공개 급식
- `Store/AppModel.swift` — 상태·캐시·로그인
- `UI/` — 홈, 타임라인, 전체·사이드바, 게시판·학사일정·클래스룸·신청·급식·알리미·날씨, 통합 검색, 설정, 투어
- `Widgets/` — 홈·잠금 화면 위젯, 실시간 현황(Live Activity)

## 안드로이드와 다른 점

앱 안 업데이트·개인화(아이콘 결)·알파 기능은 없습니다. 실시간 현황과 백그라운드 갱신은 iOS 제약상 앱을 열 때 주로 갱신됩니다.

## 배포 (TestFlight)

1. Apple Developer Program 가입(연 99달러, 만 19세 미만은 보호자 명의).
2. Xcode → Signing & Capabilities 에서 Team 지정(`project.yml` 의 `DEVELOPMENT_TEAM` 에 팀 ID 를 넣고 `xcodegen generate`).
3. 앱 아이콘(1024×1024)을 `HiHanaEvo/Resources/Assets.xcassets/AppIcon.appiconset` 에 — 디자이너 작업물로.
4. Product → Archive → Distribute App → App Store Connect → TestFlight 외부 테스트(공개 링크).

## 계정 안전

- 비밀번호는 키체인에만(`AfterFirstUnlockThisDeviceOnly`), 로그·캐시에 남기지 않습니다.
- 로그인이 한 번 실패하면 계정을 새로 넣을 때까지 자동 로그인을 멈춥니다 — 하이하나는 5회 실패 시 계정을 잠급니다.
