import SwiftUI

/// 버전 로고 — 14 부터는 둥근 판 위 실크 그라데이션과 흰 숫자(안드로이드 SilkVersionLogo). 그 전 버전은 차분한 판에 숫자.
struct VersionLogo: View {
    var major: Int = Int(appVersion.split(separator: ".").first ?? "14") ?? 14
    var size: CGFloat = 120

    var body: some View {
        Canvas { ctx, sz in
            let u = min(sz.width, sz.height) / 100
            ctx.translateBy(x: (sz.width - 100 * u) / 2, y: (sz.height - 100 * u) / 2)
            ctx.scaleBy(x: u, y: u)
            var plate = Path()
            plate.move(to: CGPoint(x: 50, y: 6))
            plate.addCurve(to: CGPoint(x: 94, y: 50), control1: CGPoint(x: 90, y: 6), control2: CGPoint(x: 94, y: 10))
            plate.addCurve(to: CGPoint(x: 50, y: 94), control1: CGPoint(x: 94, y: 90), control2: CGPoint(x: 90, y: 94))
            plate.addCurve(to: CGPoint(x: 6, y: 50), control1: CGPoint(x: 10, y: 94), control2: CGPoint(x: 6, y: 90))
            plate.addCurve(to: CGPoint(x: 50, y: 6), control1: CGPoint(x: 6, y: 10), control2: CGPoint(x: 10, y: 6))
            plate.closeSubpath()
            let ink = Color(argb: 0xFF14233D)
            ctx.fill(plate.offsetBy(dx: 0, dy: 0.55), with: .color(ink.opacity(0.07)))
            if major >= 14 {
                let p = SilkPalette.palette(for: major)
                var cool = Path()
                cool.move(to: CGPoint(x: -10, y: -12)); cool.addLine(to: CGPoint(x: 82, y: -12))
                cool.addCurve(to: CGPoint(x: 42, y: 23), control1: CGPoint(x: 76, y: 6), control2: CGPoint(x: 55, y: 11))
                cool.addCurve(to: CGPoint(x: 40, y: 69), control1: CGPoint(x: 24, y: 39), control2: CGPoint(x: 25, y: 57))
                cool.addCurve(to: CGPoint(x: 34, y: 114), control1: CGPoint(x: 58, y: 84), control2: CGPoint(x: 53, y: 105))
                cool.addLine(to: CGPoint(x: -10, y: 114)); cool.closeSubpath()
                var warm = Path()
                warm.move(to: CGPoint(x: 94, y: -12))
                warm.addCurve(to: CGPoint(x: 68, y: 33), control1: CGPoint(x: 98, y: 10), control2: CGPoint(x: 83, y: 20))
                warm.addCurve(to: CGPoint(x: 70, y: 82), control1: CGPoint(x: 49, y: 50), control2: CGPoint(x: 52, y: 70))
                warm.addCurve(to: CGPoint(x: 87, y: 112), control1: CGPoint(x: 84, y: 92), control2: CGPoint(x: 90, y: 103))
                warm.addLine(to: CGPoint(x: 112, y: 112)); warm.addLine(to: CGPoint(x: 112, y: -12)); warm.closeSubpath()
                var clipped = ctx
                clipped.clip(to: plate)
                clipped.fill(plate, with: .linearGradient(Gradient(stops: [.init(color: p.purple, location: 0), .init(color: p.indigo, location: 0.48),
                                                                           .init(color: p.purple, location: 0.82), .init(color: p.blue, location: 1)]),
                                                         startPoint: CGPoint(x: 32, y: 5), endPoint: CGPoint(x: 65, y: 104)))
                clipped.fill(cool, with: .linearGradient(Gradient(stops: [.init(color: p.mint, location: 0), .init(color: p.cyan, location: 0.36),
                                                                          .init(color: p.blue, location: 0.72), .init(color: p.indigo, location: 1)]),
                                                        startPoint: CGPoint(x: 12, y: 8), endPoint: CGPoint(x: 42, y: 100)))
                clipped.fill(warm, with: .linearGradient(Gradient(stops: [.init(color: p.gold, location: 0), .init(color: p.coral, location: 0.42),
                                                                          .init(color: p.pink, location: 1)]),
                                                        startPoint: CGPoint(x: 92, y: 9), endPoint: CGPoint(x: 68, y: 100)))
                clipped.fill(plate, with: .linearGradient(Gradient(stops: [.init(color: .clear, location: 0.32), .init(color: .white.opacity(0.12), location: 0.52), .init(color: .clear, location: 0.72)]),
                                                         startPoint: CGPoint(x: 8, y: 25), endPoint: CGPoint(x: 88, y: 80)))
                clipped.stroke(plate, with: .linearGradient(Gradient(colors: [.white.opacity(0.24), .white.opacity(0.02)]), startPoint: CGPoint(x: 15, y: 6), endPoint: CGPoint(x: 85, y: 94)), lineWidth: 0.6)
            } else {
                ctx.fill(plate, with: .linearGradient(Gradient(colors: [Color(argb: 0xFF5C6B8A), Color(argb: 0xFF2E3A55)]), startPoint: CGPoint(x: 20, y: 6), endPoint: CGPoint(x: 80, y: 94)))
            }
            ctx.stroke(plate, with: .color(ink.opacity(0.12)), lineWidth: 0.6)
            let text = ctx.resolve(Text("\(major)").font(.system(size: major >= 10 ? 46 : 54, weight: .heavy, design: .rounded)).foregroundStyle(.white))
            ctx.draw(text, at: CGPoint(x: 50, y: 50.5))
        }
        .frame(width: size, height: size)
    }
}

/// 버전별 실크 색(안드로이드 VersionLogo.kt 의 silkPalettes 와 같은 값) — 새 버전은 한 줄을 더하고, 미정인 버전은 Evo 색.
struct SilkPalette {
    let mint, cyan, blue, indigo, purple, gold, coral, pink: Color

    static let evo = SilkPalette(mint: Color(argb: 0xFF38EAA8), cyan: Color(argb: 0xFF25ACEA), blue: Color(argb: 0xFF0873F9), indigo: Color(argb: 0xFF2616D7),
                                 purple: Color(argb: 0xFF4925FB), gold: Color(argb: 0xFFFFE095), coral: Color(argb: 0xFFFF8B83), pink: Color(argb: 0xFFF24C9E))
    // 15 · iOS 출시와 외출·외박 승인 알림 — 새벽 하늘(하늘색·남색)에서 복숭앗빛 아침으로.
    static let dawn = SilkPalette(mint: Color(argb: 0xFF8FE3FF), cyan: Color(argb: 0xFF4FB3FF), blue: Color(argb: 0xFF3D7BFF), indigo: Color(argb: 0xFF1E2A8C),
                                  purple: Color(argb: 0xFF3A2E9E), gold: Color(argb: 0xFFFFD9A8), coral: Color(argb: 0xFFFF9E8A), pink: Color(argb: 0xFFE86AA6))

    static func palette(for major: Int) -> SilkPalette { major == 15 ? dawn : evo }
}

/// 'HiHana Evo 정보' — 설정 › 일반 › 정보처럼: 맨 위 아이콘·이름·설명 카드, 이름·버전·빌드 값 줄, 아래 목록.
/// 업데이트는 SideStore 로 — 새 버전이면 '업데이트'(SideStore 가 받아 설치), 'SideStore에 소스 추가'로 SideStore 안에서도 알림.
struct AboutView: View {
    @State private var updater = AppUpdater.shared
    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 10) {
                    Image("AppLogo").resizable().frame(width: 60, height: 60)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .accessibilityHidden(true)
                    Text("HiHana Evo").font(.title2.bold())
                    Text("하나고 시간표·급식·게시판·신청을 한곳에서 보는 학생용 앱입니다.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                .padding(.vertical, 8)
                .materialRow()
            }
            Section {
                LabeledContent("이름", value: "HiHana Evo").materialRow()
                LabeledContent("버전", value: appVersion).materialRow()
                LabeledContent("빌드", value: (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String) ?? "").materialRow()
            }
            Section {
                if let u = updater.available {
                    Button("\(u.version) 업데이트") { AppUpdater.install(u) }.fontWeight(.semibold).materialRow()
                } else {
                    Button(updater.checking ? "확인 중…" : "업데이트 확인") { Task { await updater.check(force: true) } }
                        .disabled(updater.checking).materialRow()
                }
                Button("SideStore에 HiHana 소스 추가") { UIApplication.shared.open(AppUpdater.addSourceLink) }
                    .disabled(!AppUpdater.hasSideStore).materialRow()
            } header: { Text("업데이트") } footer: {
                if let u = updater.available {
                    Text(u.notes.isEmpty ? "누르면 SideStore가 새 버전을 받아 설치합니다. 앱 데이터는 그대로 남습니다." : u.notes)
                } else if let e = updater.lastError {
                    Text(e)
                } else {
                    Text("SideStore에 소스를 한 번 추가해 두면 새 버전이 나왔을 때 SideStore에서도 알려 줍니다.")
                }
            }
            Section {
                RouteLink("변경 사항", value: Route.changelog)
                RouteLink("개인정보 보호", value: Route.privacy)
                RouteLink("오픈소스 라이선스", value: Route.licenses)
                RouteLink("법적 고지", value: Route.legal)
            }
        }
    }
}

/// 변경 사항 — 큰 버전별로 묶고 묶음 머리에 그 버전의 로고. 13 이전은 안드로이드 앱(HiHana Neo)의 기록입니다.
struct ChangelogView: View {
    var body: some View {
        let groups = Dictionary(grouping: changelog, by: { String($0.version.split(separator: ".").first ?? "") })
        let order = changelog.map { String($0.version.split(separator: ".").first ?? "") }.reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }
        List {
            ForEach(order, id: \.self) { major in
                Section {
                    ForEach(groups[major] ?? [], id: \.version) { entry in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(entry.version == appVersion ? "\(entry.version) (설치됨)" : entry.version).font(.headline)
                            ForEach(Array(entry.items.enumerated()), id: \.offset) { _, line in
                                HStack(alignment: .top, spacing: 8) {
                                    Text("·").foregroundStyle(.secondary)
                                    Text(line).font(.subheadline).fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                        .padding(.vertical, 4)
                        .materialRow()
                    }
                } header: {
                    HStack(spacing: 12) {
                        VersionLogo(major: Int(major) ?? 0, size: 52)
                        VStack(alignment: .leading, spacing: 0) {
                            Text((Int(major) ?? 0) >= 14 ? "HiHana Evo" : "HiHana Neo · 안드로이드").font(.caption)
                            Text(major).font(.title2.weight(.heavy)).foregroundStyle(.primary)
                        }
                    }
                    .textCase(nil)
                    .padding(.vertical, 6)
                }
            }
        }
    }
}

/// 오픈소스 라이선스 — 아이폰판은 바깥 라이브러리 없이 Apple 기본 프레임워크만 씁니다.
struct LicensesView: View {
    private let libs: [(String, String, String)] = [
        ("SwiftUI · Foundation · WebKit · WidgetKit · ActivityKit", "Apple 플랫폼 기본 프레임워크", "https://developer.apple.com/documentation"),
        ("SF Symbols", "Apple — 앱 안 아이콘", "https://developer.apple.com/sf-symbols/"),
    ]

    var body: some View {
        List {
            Section {
                ForEach(libs, id: \.0) { lib in
                    if let u = URL(string: lib.2) {
                        Link(destination: u) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(lib.0).foregroundStyle(Color.primary)
                                Text(lib.1).font(.footnote).foregroundStyle(Color.secondary)
                            }
                        }
                        .materialRow()
                    }
                }
            } footer: { Text("외부 라이브러리를 쓰지 않습니다.") }
        }
    }
}

/// 법적 고지 — 약관 형식(조·항·호). 제3조는 인공지능기본법 제31조의 사전 고지와 결과물 표시를 겸합니다.
struct LegalView: View {
    private let sections: [(String, [String])] = [
        ("제1조(서비스의 성격)", [
            "① HiHana Evo(이하 \"본 앱\"이라 합니다)는 하나고등학교 재학생의 편의를 위하여 개인이 제작·배포하는 비공식 애플리케이션으로서, 하나고등학교 및 하이하나 포털을 포함한 학교의 공식 정보시스템과 제휴하거나 그 승인을 받지 아니하였습니다.",
            "② \"하나고등학교\", \"하이하나\" 등의 명칭 및 이와 관련된 일체의 권리는 각 권리자에게 귀속됩니다.",
        ]),
        ("제2조(정보의 정확성)", [
            "① 본 앱이 제공하는 시간표, 급식, 학사일정, 게시물, 신청·예약 결과 등의 정보는 학교 정보시스템으로부터 수집하여 가공한 것으로서 원본과 상이하거나 반영이 지연될 수 있습니다. 이용자는 중요한 일정 및 신청·예약 결과를 반드시 학교의 공식 정보시스템을 통하여 확인하여야 합니다.",
            "② 본 앱이 학사일정에 따라 자동으로 조정하여 표시하는 일정(귀가 기간의 일정 표시를 포함합니다)은 학사일정의 등록 내용에 따르며, 실제 일정과 다를 수 있습니다.",
        ]),
        ("제3조(생성형 인공지능의 이용)", [
            "① 본 앱은 「인공지능 발전과 신뢰 기반 조성 등에 관한 기본법」 제31조에 따라 이 조의 사항을 고지합니다.",
            "② 본 앱의 게시물 요약 기능(게시판 목록의 한 줄 요약, 게시물 상세 화면의 요약, 새 게시물 알림의 요약 및 게시물에 적힌 일정을 뽑아 홈·학사일정에 표시하는 기능을 포함합니다), 데일리 브리핑 및 급식 특식 고르기(이하 \"AI 요약\"이라 합니다)는 생성형 인공지능을 이용하여 제공되며, AI 요약은 생성형 인공지능에 의하여 생성된 결과물입니다.",
            "③ AI 요약은 참고 목적으로만 제공되며, 본 앱은 그 정확성 및 완전성을 보증하지 아니합니다. 이용자는 중요한 사항(특히 AI 요약이 뽑아낸 일정의 날짜·시각)에 관하여 반드시 게시물 원문을 확인하여야 합니다.",
            "④ AI 요약은 Cloudflare, Inc.가 제공하는 Workers AI를 통하여 생성되며, 이를 위하여 게시물의 제목, 본문 및 본문에 포함된 이미지, 데일리 브리핑에 쓸 과목·일정 제목, 급식 메뉴 이름이 요약 서버로 전송됩니다. 이용자의 계정 정보, 학번 등 개인을 식별할 수 있는 정보는 전송되지 아니합니다.",
            "⑤ 생성된 AI 요약은 동일한 게시물을 열람하는 다른 이용자에게 제공하기 위하여 요약 서버에 최대 180일간 보관됩니다.",
            "⑥ 이용자는 설정의 게시판 메뉴에서 AI 요약과 AI 데일리 브리핑을 각각 사용하지 아니하도록 설정할 수 있으며, 이 경우 해당 내용은 요약 서버로 전송되지 아니합니다.",
            "⑦ 본 앱은 AI 요약 외의 기능에 생성형 인공지능을 이용하지 아니합니다.",
        ]),
        ("제4조(계정 정보 및 개인정보의 처리)", [
            "① 이용자가 입력한 하이하나 계정의 아이디 및 비밀번호는 이용자의 단말기 키체인에 암호화하여 저장되며, 로그인을 위하여 암호화된 통신(HTTPS)으로 하이하나 포털(hi.hana.hs.kr)에만 전송됩니다. 개발자는 이를 수집하거나 보관하지 아니합니다.",
            "② 제1항의 암호화에 사용되는 키는 iOS 의 보안 영역(Secure Enclave)이 보호하며, 본 앱 및 개발자는 이를 단말기 외부로 반출할 수 없습니다.",
            "③ 심야면학 서비스의 로그인 정보는 해당 서비스에만 전송되며, 단말기에는 로그인 상태의 유지에 필요한 정보만 암호화하여 저장됩니다.",
            "④ 제1항 및 제3항의 정보는 백업이나 기기 이전으로 다른 기기로 옮겨지지 아니합니다. 본 앱을 삭제한 뒤에도 키체인에 남는 정보는 본 앱을 다시 설치하여 처음 실행할 때 삭제됩니다.",
            "⑤ 본 앱은 광고 및 이용자의 행태를 추적·분석하는 도구를 포함하지 아니합니다.",
        ]),
        ("제4조의2(Google 클래스룸 연동)", [
            "① 이용자가 설정에서 클래스룸을 연결하면, 학교 Google 계정으로 실행되는 웹 앱(Apps Script)이 이용자 본인의 권한으로 수업 목록, 공지, 과제와 본인의 제출 상태, 수업 자료를 읽어 옵니다. 본 앱은 Google 계정의 비밀번호를 입력받거나 저장하지 아니합니다.",
            "② 읽어 온 내용은 단말기에서 만든 비밀키로 암호화되어 중계 서버를 거쳐 단말기로 전달되며, 중계 서버는 암호문만 보관하여 내용을 볼 수 없습니다. 클래스룸 정보는 단말기에만 저장됩니다.",
            "③ 본 앱은 클래스룸에 글을 쓰거나 과제를 제출하는 등 어떠한 변경도 하지 아니합니다.",
            "④ 이용자는 설정의 클래스룸 메뉴에서 언제든지 연결을 해제할 수 있으며, 이 경우 단말기에 저장된 클래스룸 정보와 중계 서버의 사본이 삭제됩니다.",
        ]),
        ("제5조(정보의 출처)", [
            "본 앱이 제공하는 정보의 출처는 다음 각 호와 같습니다.",
            "1. 시간표, 학사일정, 게시판, 신청 및 도서관: 하이하나 포털(hi.hana.hs.kr)",
            "1의2. 클래스룸 공지·과제·자료: Google 클래스룸(이용자가 연결한 경우에 한함)",
            "2. 급식 식단: 하나고등학교 홈페이지(www.hana.hs.kr)",
            "3. 급식의 열량, 영양성분 및 원산지: 나이스 교육정보 개방 포털(open.neis.go.kr)",
            "4. 심야면학 좌석 및 신청 현황: 심야면학 신청 사이트(midnight-study.vercel.app)",
            "5. 날씨: 기상청 단기예보(공공데이터포털)를 요약 서버를 거쳐 제공하고, 기상청 서비스 장애 시에는 Open-Meteo(open-meteo.com)의 예보로 대신하며, 이 과정에서 이용자의 위치 등 어떠한 정보도 전송되지 아니합니다",
        ]),
        ("제6조(책임의 제한)", [
            "① 본 앱은 무상으로 제공되며, 개발자는 본 앱이 중단 없이 또는 오류 없이 작동함을 보증하지 아니합니다.",
            "② 개발자는 이용자가 본 앱에 표시된 정보에 의존함으로써 발생한 신청 누락, 일정 착오 그 밖의 손해에 대하여, 개발자의 고의 또는 중대한 과실이 없는 한 책임을 지지 아니합니다.",
        ]),
    ]

    var body: some View {
        List {
            ForEach(sections, id: \.0) { title, items in
                Section(title) {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(Array(items.enumerated()), id: \.offset) { _, line in
                            let (marker, rest) = split(line)
                            HStack(alignment: .top, spacing: 6) {
                                if let marker { Text(marker).frame(minWidth: 18, alignment: .leading) }
                                Text(rest).fixedSize(horizontal: false, vertical: true)
                            }
                            .font(.subheadline)
                        }
                    }
                    .padding(.vertical, 4)
                    .materialRow()
                }
            }
        }
    }

    /// 항·호 번호(①, 1.)를 떼어 왼쪽 칸에.
    private func split(_ line: String) -> (String?, String) {
        if let f = line.unicodeScalars.first, (0x2460...0x2473).contains(f.value) { return (String(f), String(line.dropFirst()).trimmingCharacters(in: .whitespaces)) }
        if let r = line.range(of: #"^(\d+(의\d+)?\.)\s+"#, options: .regularExpression) {
            return (String(line[r]).trimmingCharacters(in: .whitespaces), String(line[r.upperBound...]))
        }
        return (nil, line)
    }
}
