import AVFoundation
import SwiftUI
import UIKit

/*
 * 첫 실행 오프닝 영상(캠퍼스 위를 나는 리본이 로고가 되는 장면) — 앱 용량을 늘리지 않으려고 앱에 넣지 않고, 요약 서버에서
 * 스트리밍으로 재생합니다. 기기에는 저장하지 않습니다. 개인 정보는 보내지 않습니다. 안드로이드 OpeningVideo.kt 와 같은 동작입니다.
 *
 * - 나오는 때: 첫 실행 투어 맨 앞, 정보 화면 로고 다섯 번(이스터에그).
 * - 시작될 때까지 로딩 표시를 띄우고, startSeconds 안에 시작되지 않으면(인터넷 없음·느림·데이터 절약 모드) 예전 코드 애니메이션으로
 *   대신합니다(2026-11-25 사용자 요청).
 * - 재생 중 stallSeconds 넘게 멈추면 영상을 끊고 앱이 그리는 로고로 바로 넘어갑니다.
 */
enum OpeningVideo {
    /// 영상 파일 이름 — 영상을 바꾸면 이름(버전)도 바꾸고, 서버(summary-worker/assets/opening)에도 같은 이름으로 올립니다.
    static let name = "opening-v1.mp4"
    static var url: URL {
        #if DEBUG
        // (개발용) 서버를 바꾸기 전에 로컬 워커(wrangler dev)로 확인할 때: 실행 인자 -openingBase http://localhost:8799
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-openingBase"), i + 1 < args.count, let u = URL(string: "\(args[i + 1])/opening/\(name)") { return u }
        #endif
        return URL(string: "https://hihana-summary.kyoohan0711ultra.workers.dev/opening/\(name)")!
    }

    /// 영상 마지막 프레임의 로고 테두리(영상 화면 비율 0..1, 위가 0) — Blender 카메라로 잰 값(2026-11-24, 마지막 프레임).
    static let logoCX: CGFloat = 0.4992, logoCY: CGFloat = 0.4978, logoW: CGFloat = 0.3795, logoH: CGFloat = 0.2294
    /// 재생이 이 안에 시작되지 않으면 예전 코드 애니메이션(정한 값 — 기다리는 동안은 오프닝 바탕 + 로딩 표시).
    static let startSeconds: Double = 3.0
    /// 재생 중 이만큼 넘게 멈추면 영상을 끊고 로고로 넘어감(정한 값).
    static let stallSeconds: Double = 1.5

    /// 예전 시험 빌드가 받아 둔 영상 파일을 지웁니다(지금은 저장하지 않음).
    static func removeOldDownloads() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("opening", isDirectory: true)
        if FileManager.default.fileExists(atPath: dir.path) { try? FileManager.default.removeItem(at: dir) }
    }

    /// 화면(size)에 꽉 차게(가운데 기준) 그린 영상 속 로고의 테두리(화면 pt).
    static func logoRect(size: CGSize, video: CGSize) -> CGRect {
        let s = max(size.width / video.width, size.height / video.height)
        let ox = (size.width - video.width * s) / 2, oy = (size.height - video.height * s) / 2
        let w = logoW * video.width * s, h = logoH * video.height * s
        return CGRect(x: ox + logoCX * video.width * s - w / 2, y: oy + logoCY * video.height * s - h / 2, width: w, height: h)
    }
}

/// 영상을 화면에 꽉 차게(가운데 기준, 남는 쪽은 잘림) 소리 없이 스트리밍 재생. 끝나면 마지막 프레임이 남습니다.
struct OpeningVideoLayer: UIViewRepresentable {
    let url: URL
    let onReady: (CGSize) -> Void
    /// 실제로 화면이 움직이기 시작한 때(버퍼가 차서 재생 시작).
    let onStart: () -> Void
    let onEnd: () -> Void
    let onError: () -> Void

    final class PlayerView: UIView {
        override static var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
        var observers: [NSObjectProtocol] = []
        var statusObs: NSKeyValueObservation?
        var rateObs: NSKeyValueObservation?
        var started = false
        var stallCheck: DispatchWorkItem?
        deinit { observers.forEach(NotificationCenter.default.removeObserver); stallCheck?.cancel() }
    }

    func makeUIView(context: Context) -> PlayerView {
        let v = PlayerView()
        v.backgroundColor = .clear
        // 데이터 절약 모드(저데이터 모드)에서는 받지 않음 → 곧바로 실패해 코드 애니메이션으로.
        let asset = AVURLAsset(url: url, options: [AVURLAssetAllowsConstrainedNetworkAccessKey: false])
        let item = AVPlayerItem(asset: asset)
        item.preferredForwardBufferDuration = 4
        let player = AVPlayer(playerItem: item)
        player.isMuted = true
        player.actionAtItemEnd = .pause
        // 소리 없는 영상이라 다른 앱 음악을 끊지 않게.
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        v.playerLayer.player = player
        v.playerLayer.videoGravity = .resizeAspectFill
        v.observers.append(NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { _ in onEnd() })
        v.observers.append(NotificationCenter.default.addObserver(forName: .AVPlayerItemFailedToPlayToEndTime, object: item, queue: .main) { _ in onError() })
        v.statusObs = item.observe(\.status, options: [.new]) { it, _ in
            DispatchQueue.main.async {
                switch it.status {
                case .readyToPlay:
                    onReady(it.presentationSize)
                    player.play()
                case .failed: onError()
                default: break
                }
            }
        }
        // 재생 시작·멈춤 — 시작 전에 멈춘 것은 EvoOpening 의 시작 시간 한도가, 시작 뒤에 멈춘 것은 여기서 stallSeconds 로 봅니다.
        v.rateObs = player.observe(\.timeControlStatus, options: [.new]) { [weak v] p, _ in
            DispatchQueue.main.async {
                guard let v else { return }
                switch p.timeControlStatus {
                case .playing:
                    v.stallCheck?.cancel()
                    if !v.started { v.started = true; onStart() }
                case .waitingToPlayAtSpecifiedRate where v.started:
                    v.stallCheck?.cancel()
                    let w = DispatchWorkItem { if p.timeControlStatus != .playing { onEnd() } }
                    v.stallCheck = w
                    DispatchQueue.main.asyncAfter(deadline: .now() + OpeningVideo.stallSeconds, execute: w)
                default: break
                }
            }
        }
        return v
    }

    func updateUIView(_ uiView: PlayerView, context: Context) {}

    static func dismantleUIView(_ uiView: PlayerView, coordinator: ()) {
        uiView.stallCheck?.cancel()
        uiView.playerLayer.player?.pause()
        uiView.playerLayer.player?.replaceCurrentItem(with: nil)
        uiView.playerLayer.player = nil
    }
}
