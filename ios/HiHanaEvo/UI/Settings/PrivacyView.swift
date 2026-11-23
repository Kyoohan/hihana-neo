import SwiftUI

/*
 * 개인정보 보호 — 정보 화면에서 엽니다. 제품 소개 페이지처럼 큰 헤드라인과 차분한 카드, 색은 섹션마다 작은 포인트로만.
 * 적은 내용은 iOS 코드와 공개 문서, 실제 연결로 확인한 사실만 씁니다:
 * 계정 저장(Keychain, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly — 키체인 항목은 AES-256-GCM 으로 잠기고 그 열쇠는 Secure Enclave 가 지킴,
 * Apple 플랫폼 보안 문서), 새로 설치할 때 남은 키체인 지움(Keychain.wipeIfFreshInstall), 통신하는 서버([hosts]),
 * 광고·분석 SDK 없음(project.yml 에 외부 패키지 없음), 학사시스템 연결 TLS 1.3 · AES-256-GCM(2026년 11월 확인).
 * 코드·서버가 바뀌면 이 문구도 같이 고칩니다.
 */

// iOS 27 키트 Colors 의 시스템 색만.
private let pvYellow = Color.yellow
private let pvGreen = Color.green
private let pvOrange = Color.orange
private let pvBlue = Color.blue
private let pvPink = Color.pink
private let pvViolet = Color.purple

/// 앱이 스스로 접속하는 곳 — 이름, 쓰는 곳, 보내는 정보, 점 색.
private let hosts: [(name: String, use: String, sends: String, color: Color)] = [
    ("하나고 학사시스템", "로그인 · 시간표 · 신청 · 게시판", "아이디·비밀번호\nHTTPS", pvBlue),
    ("심야면학 사이트", "심야면학 로그인 · 신청", "이름·비밀번호\nHTTPS", pvViolet),
    ("AI 요약 서버", "Cloudflare · 게시판 글 요약 · 데일리 브리핑 · 급식 특식 · 날씨 · 클래스룸 중계", "글 제목·본문\n과목·일정 제목\n급식 메뉴 이름", pvPink),
    ("나이스 교육정보", "급식 칼로리·영양·원산지", "학교 코드·날짜", pvOrange),
    ("하나고 홈페이지", "급식 메뉴", "날짜", pvYellow),
]

/// 같은 AES-256 을 쓰는 곳 — 두 줄로 반대 방향으로 흘러갑니다.
private let aesUsers: [[(String, String)]] = [
    [("NSA", "최고 기밀 승인"), ("Apple", "iPhone 데이터 보호"), ("Android", "기기 파일 암호화"), ("Samsung Knox", "파일 암호화"),
     ("Google Cloud", "저장 데이터"), ("Amazon S3", "저장 데이터"), ("Dropbox", "저장 파일")],
    [("WhatsApp", "메시지 암호화"), ("Signal", "메시지 암호화"), ("Zoom", "회의 암호화"), ("1Password", "비밀번호 금고"),
     ("KB국민은행", "인터넷 뱅킹"), ("신한은행", "인터넷 뱅킹"), ("우리은행", "인터넷 뱅킹"), ("하나은행", "인터넷 뱅킹"), ("토스뱅크", "인터넷 뱅킹")],
]

/// 개인정보 보호 — 건강 앱의 '건강 데이터 보호하기'처럼 큰 기호·굵은 제목·설명과 '더 알아보기', 아래에 지키는 방법과 보내는 곳.
/// 긴 설명(최고 기밀, 열쇠 수, 직접 눌러 보기…)은 '더 알아보기' 안에.
struct PrivacyView: View {
    @Environment(\.palette) private var palette
    @State private var showingMore = false
    /// '이 iPhone과' / '이 iPad와' — 받침에 맞춘 조사까지.
    private var device: String { isPad ? "iPad와" : "iPhone과" }

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 14) {
                    Image(systemName: "lock.shield.fill")
                        .font(.system(size: 84))
                        .foregroundStyle(palette.primary.gradient)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                        .accessibilityHidden(true)
                    Text("비밀번호는 이 \(device) 학교만 압니다").font(.title2.bold())
                    Text("학사시스템 비밀번호는 이 기기의 키체인에 암호화되어 저장되고, 학교 서버로만 보내집니다. 광고나 분석 도구는 들어 있지 않습니다.")
                        .foregroundStyle(.secondary)
                    Text("앱을 지우고 다시 설치하면 남아 있던 계정 정보도 지웁니다.").foregroundStyle(.secondary)
                    Button { showingMore = true } label: {
                        Label("더 알아보기…", systemImage: "info.circle.fill").foregroundStyle(.tint)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.vertical, 8)
                .listRowBackground(Color.clear)
            }
            Section("지키는 방법") {
                fact("key.fill", .orange, "저장", "AES-256 · 키체인")
                fact("lock.fill", .green, "보낼 때", "HTTPS · TLS 1.3")
                fact("cpu.fill", .indigo, "열쇠", "Secure Enclave")
            }
            Section {
                ForEach(hosts, id: \.name) { h in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(h.name)
                        Text(h.use).font(.footnote).foregroundStyle(.secondary)
                    }
                    .materialRow()
                }
            } header: { Text("접속하는 곳") } footer: {
                Text("앱이 스스로 접속하는 곳은 이 \(hosts.count)곳뿐입니다. 무엇을 보내는지는 '더 알아보기'에 있습니다.")
            }
        }
        .navigationTitle("개인정보 보호")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $showingMore) { PrivacyDetailView() }
    }

    private func fact(_ symbol: String, _ color: Color, _ title: String, _ value: String) -> some View {
        LabeledContent { Text(value) } label: { Label { Text(title) } icon: { IconTile(symbol, color) } }.materialRow()
    }
}

/// 개인정보 보호 › 더 알아보기 — 제품 소개 페이지처럼 큰 헤드라인과 차분한 카드.
struct PrivacyDetailView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 56) {
                Reveal { Hero() }
                Reveal { TopSecret() }
                Reveal { EnclaveSection() }
                Reveal { KeySpace() }
                Reveal { EncryptionDemo() }
                Reveal { HTTPSSection() }
                Reveal { FlowSection() }
                Reveal { HostsSection() }
                Reveal { CannotSection() }
                Reveal { EraseSection() }
            }
            .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 56)
        }
        .background { FrostedPageBackdrop() }
        .navigationTitle("더 알아보기")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// 처음 보일 때 아래에서 살짝 떠오르며.
private struct Reveal<C: View>: View {
    @ViewBuilder var content: C
    @State private var shown = false
    var body: some View {
        content.opacity(shown ? 1 : 0).offset(y: shown ? 0 : 24)
            .onAppear { withAnimation(.spring(response: 0.7, dampingFraction: 0.9)) { shown = true } }
    }
}

private struct Head: View {
    var eyebrow = ""
    var color: Color? = nil
    let title: String
    var lead: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if !eyebrow.isEmpty { Text(eyebrow).font(.subheadline.bold()).foregroundStyle(color ?? .secondary) }
            Text(title).font(.system(size: 26, weight: .heavy)).fixedSize(horizontal: false, vertical: true)
            if let lead { Text(lead).foregroundStyle(.secondary).padding(.top, 2) }
        }
    }
}

private struct Plain<C: View>: View {
    var padding: CGFloat = 20
    @ViewBuilder var content: C
    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

private struct Fine: View {
    let text: String
    var body: some View { Text(text).font(.footnote).foregroundStyle(.secondary).padding(.top, 12) }
}

// MARK: - 머리

private struct Hero: View {
    private let specs: [(String, String, String, Color)] = [
        ("AES-256", "저장할 때 암호화", "key.fill", .orange),
        ("HTTPS", "보낼 때 암호화", "lock.shield.fill", .green),
        ("TLS 1.3", "최신 보안 연결", "network.badge.shield.half.filled", .pink),
        ("Secure Enclave", "열쇠는 칩 속 금고에", "cpu.fill", .indigo),
    ]
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("개인정보 보호").font(.subheadline.bold()).foregroundStyle(.secondary)
            Text("비밀번호는\n이 iPhone과 학교만 압니다.").font(.system(size: 34, weight: .heavy))
            Text("HiHana Evo는 학교 계정으로 로그인하는 앱입니다. 그래서 계정 정보를 어떻게 지키는지, 어디로 무엇을 보내는지 숨김없이 보여 드립니다.")
                .foregroundStyle(.secondary)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(specs, id: \.0) { name, caption, symbol, color in
                    VStack(alignment: .leading, spacing: 10) {
                        Image(systemName: symbol).font(.title2).foregroundStyle(.white)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(name).font(.title3.weight(.black)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.7)
                            Text(caption).font(.caption).foregroundStyle(.white.opacity(0.9)).lineLimit(1)
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, minHeight: 112, alignment: .topLeading)
                    .background(color, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                }
            }
            .padding(.top, 8)
        }
    }
}

// MARK: - AES-256

private struct TopSecret: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("TOP SECRET 승인").font(.subheadline.weight(.heavy)).kerning(1.5).foregroundStyle(pvYellow)
            Head(title: "미국 정부가\n최고 기밀에 쓰는 암호화.",
                 lead: "비밀번호를 잠그는 AES-256은 미국 국가안보시스템위원회(CNSSP-15)가 최고 기밀(TOP SECRET) 정보를 보호하도록 승인한 방식입니다.")
            Text("같은 AES-256을 쓰는 곳").font(.caption).foregroundStyle(.secondary)
            Belt(items: aesUsers[0], duration: 38, reverse: false)
            Belt(items: aesUsers[1], duration: 44, reverse: true)
            Text("2026년 9월 기준").font(.footnote).foregroundStyle(.secondary)
        }
    }
}

/// 이름 칩이 한 방향으로 끝없이 흐르는 띠.
private struct Belt: View {
    let items: [(String, String)]
    let duration: Double
    let reverse: Bool
    @State private var width: CGFloat = 1

    var body: some View {
        // 띠는 겹쳐 그려 화면 폭을 넓히지 않습니다.
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .overlay(alignment: .leading) {
                TimelineView(.animation) { t in
                    let phase = CGFloat(t.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: duration) / duration)
                    HStack(spacing: 10) { row; row }
                        .offset(x: (reverse ? phase - 1 : -phase) * width)
                }
            }
            .clipped()
        .mask(LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .black, location: 0.08), .init(color: .black, location: 0.92), .init(color: .clear, location: 1)],
                             startPoint: .leading, endPoint: .trailing))
    }

    private var row: some View {
        HStack(spacing: 10) {
            ForEach(items, id: \.0) { name, use in
                VStack(alignment: .leading, spacing: 1) {
                    Text(name).font(.subheadline.weight(.heavy)).lineLimit(1)
                    Text(use).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(.background.secondary, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
        }
        .fixedSize()
        .onGeometryChange(for: CGFloat.self) { $0.size.width + 10 } action: { width = $0 }
    }
}

// MARK: - Secure Enclave

private struct EnclaveSection: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Head(eyebrow: "Secure Enclave", color: pvBlue, title: "열쇠는\n금고 안의 금고에.",
                 lead: "Secure Enclave는 iPhone의 칩 안에 따로 떼어 놓은 보안 영역입니다. 자체 프로세서와 운영체제로 움직이고, iOS 쪽에서는 열쇠를 꺼내 볼 수 없습니다.")
            EnclaveChip()
            Fact(symbol: "lock.iphone", color: pvBlue, title: "앱이 뚫려도",
                 body: "계정 정보는 iOS 키체인에 넣습니다. 키체인 항목은 AES-256-GCM으로 잠기고, 그 열쇠는 Secure Enclave가 지킵니다. 앱이나 iOS가 공격받아도 열쇠 자체를 꺼낼 수는 없습니다.")
            Fact(symbol: "iphone.gen3", color: pvViolet, title: "이 iPhone에 묶인 열쇠",
                 body: "'이 기기에서만'으로 저장해 백업이나 기기 이전으로 다른 기기에 옮겨 가지 않습니다. iCloud 키체인으로도 동기화되지 않습니다.")
            Fact(symbol: "faceid", color: pvYellow, title: "Face ID와 같은 금고",
                 body: "Face ID·Touch ID 정보도 같은 Secure Enclave가 지킵니다.")
        }
    }
}

private struct EnclaveChip: View {
    @State private var pulse = false
    var body: some View {
        let teal = Color(argb: 0xFF2FB8A6), amber = Color(argb: 0xFFF5B53D)
        VStack(spacing: 10) {
            VStack(spacing: 8) {
                Text("SECURE ENCLAVE").font(.system(size: 15, weight: .bold, design: .monospaced)).kerning(3).foregroundStyle(teal)
                Label("AES-256 열쇠", systemImage: "key.fill").font(.caption.bold()).foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 6).background(teal.opacity(pulse ? 0.9 : 0.55), in: Capsule())
            }
            .frame(maxWidth: .infinity).padding(.vertical, 16)
            .background(teal.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(teal.opacity(0.5), lineWidth: 1))
            HStack(spacing: 18) {
                Label("잠가 줘 · 풀어 줘 (부탁만)", systemImage: "arrow.up").foregroundStyle(amber)
                Label("결과만 돌아옴", systemImage: "arrow.down").foregroundStyle(teal)
            }
            .font(.caption2)
            VStack(spacing: 8) {
                Label("HiHana Evo · 열쇠 없음", systemImage: "app.fill").font(.caption.bold()).foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 6).background(amber.opacity(0.75), in: Capsule())
                Text("iOS").font(.system(size: 15, weight: .bold, design: .monospaced)).kerning(3).foregroundStyle(amber)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 16)
            .background(amber.opacity(0.10), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .padding(14)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .onAppear { withAnimation(.easeInOut(duration: 1.6).repeatForever(autoreverses: true)) { pulse = true } }
    }
}

private struct Fact: View {
    let symbol: String
    let color: Color
    let title: String
    let body_: String
    init(symbol: String, color: Color, title: String, body: String) { self.symbol = symbol; self.color = color; self.title = title; self.body_ = body }
    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            TintedCircleIcon(symbol: symbol, color: color, size: 38)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.headline)
                Text(body_).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }
}

private struct KeySpace: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Head(eyebrow: "AES-256", color: pvYellow, title: "추측으로는\n열 수 없습니다.")
            Plain {
                HStack(alignment: .top, spacing: 0) {
                    Text("2").font(.system(size: 64, weight: .heavy))
                    Text("256").font(.system(size: 30, weight: .heavy)).padding(.top, 4)
                }
                Text("115,792,089,237,316,195,423,570,985,008,687,907,853,269,984,665,640,564,039,457,584,007,913,129,639,936 가지 열쇠")
                    .font(.system(.caption, design: .monospaced)).foregroundStyle(.secondary)
            }
            HStack(alignment: .top, spacing: 10) {
                stat("3.7×10⁵¹년", "1초에 100경 번 시도하는 컴퓨터로 모든 열쇠를 시도하는 시간")
                stat("10⁴¹배", "지금까지 흐른 우주의 나이(약 138억 년)보다 긴 정도")
            }
        }
    }
    private func stat(_ big: String, _ small: String) -> some View {
        Plain(padding: 16) {
            Text(big).font(.title2.weight(.heavy))
            Text(small).font(.caption).foregroundStyle(.secondary).padding(.top, 4)
        }
    }
}

/// 직접 눌러 보는 암호화 — 글자가 뒤섞이며 저장되는 모습으로.
private struct EncryptionDemo: View {
    @State private var encrypted = false
    @State private var shown = "hana2026!"
    private let cipher = "kQ8vZ3xN1pRw+7aJcL0eYf2Hs9TgUb6Mn4Dq5Ki="

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Head(eyebrow: "직접 눌러 보세요", title: "저장되는 모습.", lead: "비밀번호는 그대로 저장되지 않습니다. 이 iPhone 안에 실제로 남는 형태를 확인해 보세요.")
            Plain {
                Text(encrypted ? "이 iPhone에 저장된 값 (AES-256)" : "입력한 비밀번호").font(.caption).foregroundStyle(.secondary)
                Text(shown).font(.system(size: encrypted ? 15 : 24, weight: .semibold, design: .monospaced)).lineLimit(2)
                    .foregroundStyle(encrypted ? pvGreen : .primary).padding(.top, 8).contentTransition(.numericText())
            }
            Button(encrypted ? "원래대로" : "암호화하기") { toggle() }.buttonStyle(.borderedProminent).controlSize(.large)
        }
    }

    private func toggle() {
        encrypted.toggle()
        let target = encrypted ? cipher : "hana2026!"
        let pool = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=")
        Task {
            for step in 0...12 {
                let keep = target.count * step / 12
                shown = String(target.prefix(keep)) + String((0..<max(0, target.count - keep)).map { _ in pool.randomElement()! })
                try? await Task.sleep(for: .milliseconds(40))
            }
            shown = target
        }
    }
}

// MARK: - 연결

private struct HTTPSSection: View {
    @State private var secure = true
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Head(eyebrow: "HTTPS · TLS 1.3", color: pvGreen, title: "학교 와이파이에서도\n안전하게.",
                 lead: "로그인할 때 비밀번호는 암호화된 연결로 학사시스템에 곧장 갑니다. 같은 와이파이의 누군가가 엿봐도 알아볼 수 없습니다.")
            Picker("", selection: $secure) {
                Text("HTTPS (지금)").tag(true)
                Text("암호화가 없다면").tag(false)
            }
            .pickerStyle(.segmented)
            Plain {
                HStack {
                    Label("내 iPhone", systemImage: "iphone")
                    Spacer()
                    Image(systemName: secure ? "lock.fill" : "lock.open.fill").foregroundStyle(secure ? pvGreen : pvOrange)
                    Spacer()
                    Label("학사시스템", systemImage: "building.columns")
                }
                .font(.caption)
                Text("같은 와이파이의 누군가에게 보이는 것").font(.caption).foregroundStyle(.secondary).padding(.top, 14)
                Text(secure ? "17 03 03 01 2a e9 4c 8b f0 3d 91 7a c2 5e 08 b6 d4 13 …" : "mem_id=hana2026&mem_pwd=hana2026!")
                    .font(.system(.subheadline, design: .monospaced)).foregroundStyle(secure ? pvGreen : pvOrange).padding(.top, 4)
                HStack(spacing: 6) {
                    ForEach(["TLS 1.3", "AES-256-GCM", "인증서 확인"], id: \.self) { tag in
                        Text(tag).font(.caption2.bold()).padding(.horizontal, 8).padding(.vertical, 4).background(pvGreen.opacity(0.14), in: Capsule()).foregroundStyle(pvGreen)
                    }
                }
                .opacity(secure ? 1 : 0.3)
                .padding(.top, 12)
            }
            .animation(.spring(response: 0.35, dampingFraction: 0.9), value: secure)
            Fine(text: "학사시스템과의 연결은 TLS 1.3 · AES-256-GCM으로 암호화됩니다(2026년 11월 확인). 인증서로 진짜 hi.hana.hs.kr인지도 확인합니다.")
        }
    }
}

private struct FlowSection: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Head(eyebrow: "개인정보가 가는 길", color: pvOrange, title: "비밀번호는\n한 길로만 갑니다.")
            Plain {
                node("key.fill", "입력한 아이디·비밀번호", "로그인 화면에서", .secondary)
                wire()
                node("iphone", "이 iPhone", "AES-256으로 잠가 키체인에 · 열쇠는 Secure Enclave 안", pvBlue)
                wire()
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 6) {
                        tag("HTTPS · TLS 1.3\n아이디·비밀번호", pvGreen)
                        node("building.columns", "학사시스템", "hi.hana.hs.kr", pvGreen)
                    }
                    VStack(alignment: .leading, spacing: 6) {
                        tag("게시판 글\n제목·본문만", pvPink)
                        node("cloud.fill", "AI 요약 서버", "Cloudflare", pvPink)
                        Label("아이디·비밀번호 전송 없음", systemImage: "xmark.circle.fill").font(.caption2.bold()).foregroundStyle(pvPink)
                    }
                }
            }
            Fine(text: "AI 요약 서버(Cloudflare)에는 게시판 글, 데일리 브리핑에 쓸 과목·일정 제목, 급식 메뉴 이름만 갑니다. 날씨도 이 서버에서 받으며, 학교(하나고)와 서울 중 어느 곳의 날씨인지만 알립니다. 계정 정보를 다른 곳으로 보내는 코드는 앱 어디에도 없습니다.")
        }
    }
    private func node(_ symbol: String, _ title: String, _ caption: String, _ tint: Color) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: symbol).foregroundStyle(tint).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.bold())
                Text(caption).font(.caption).foregroundStyle(.secondary)
            }
        }
    }
    private func wire() -> some View { Rectangle().fill(.quaternary).frame(width: 2, height: 18).padding(.leading, 11).padding(.vertical, 4) }
    private func tag(_ text: String, _ color: Color) -> some View {
        Text(text).font(.caption2.bold()).foregroundStyle(color).padding(.horizontal, 8).padding(.vertical, 4).background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct HostsSection: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Head(eyebrow: "앱이 접속하는 곳", title: "다섯 곳, 그게 전부.")
            Plain(padding: 0) {
                ForEach(Array(hosts.enumerated()), id: \.offset) { i, h in
                    if i > 0 { Divider().padding(.leading, 40) }
                    HStack(alignment: .top, spacing: 12) {
                        Circle().fill(h.color).frame(width: 10, height: 10).padding(.top, 5)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(h.name).font(.subheadline.bold())
                            Text(h.use).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 8)
                        Text(h.sends).font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.trailing)
                    }
                    .padding(.horizontal, 18).padding(.vertical, 14)
                }
            }
            Fine(text: "게시글을 열면 글에 들어 있는 사진·첨부파일은 그 글이 가리키는 곳(대개 학사시스템)에서 받습니다. 클래스룸을 연결할 때는 학교 Google 계정의 허용 화면을 앱 안 인증 창으로 엽니다.")
        }
    }
}

private struct CannotSection: View {
    private let promises: [(String, String, Color, String)] = [
        ("비밀번호를 볼 수 없습니다", "비밀번호는 학사시스템에만 곧장 갑니다. 개발자에게 전달될 길이 없습니다.", pvViolet, "eye.slash.fill"),
        ("요약 서버엔 꼭 필요한 글만", "AI 요약 서버(Cloudflare)는 게시판 글의 제목·본문·이미지 주소, 데일리 브리핑에 쓸 과목·일정 제목, 급식 메뉴 이름만 받습니다. 요약을 끄면 게시판 글도 보내지 않습니다. 날씨는 학교와 서울 중 어느 곳인지만 알리고 받습니다.", pvPink, "cloud.fill"),
        ("원격 조작 기능 없음", "서버에서 명령이나 코드를 받아 실행하지 않습니다. 앱이 하는 일을 바꾸려면 새 버전을 내야 합니다.", pvBlue, "arrow.left.arrow.right"),
        ("사용 기록 수집 없음", "광고·사용 분석·오류 수집 도구를 넣지 않았습니다. 무엇을 봤는지 같은 기록이 어디로도 가지 않습니다.", pvOrange, "chart.bar.xaxis"),
    ]
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Head(eyebrow: "개발자가 할 수 없는 것", title: "만든 사람도\n못 봅니다.")
            ForEach(promises, id: \.0) { title, body, color, symbol in
                Plain { Fact(symbol: symbol, color: color, title: title, body: body) }
            }
        }
    }
}

private struct EraseSection: View {
    private let steps = [
        ("계정 연결 해제", "설정 → 계정·학년 → 학사시스템 연동에서 연결 해제를 누르면 바로 지워집니다."),
        ("앱 삭제", "이 기기에 저장된 정보가 함께 지워집니다. iOS 키체인에 남는 계정 정보는 다시 설치해 처음 열 때 앱이 지우며, 백업으로 다른 기기에 옮겨 가지 않습니다."),
        ("AI 요약 서버 기록", "요약은 게시판 글 내용만 담고 180일 뒤 지워집니다. IP 주소는 요청 횟수 제한을 위해 1시간만 셉니다."),
    ]
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Head(eyebrow: "지우는 방법", title: "언제든 지울 수 있습니다.")
            Plain {
                ForEach(Array(steps.enumerated()), id: \.offset) { i, s in
                    if i > 0 { Divider().padding(.vertical, 12) }
                    HStack(alignment: .top, spacing: 0) {
                        Text("0\(i + 1)").font(.subheadline.weight(.heavy)).foregroundStyle(.tertiary).frame(width: 30, alignment: .leading)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(s.0).font(.subheadline.bold())
                            Text(s.1).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }
}
