import SwiftUI
import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins
import Observation

/// 사용자가 고른 배경 사진 한 장 — 긴 변 1600px 로 줄여 JPEG 로 둡니다. 설정의 배경이 '사진'일 때만 그립니다.
@Observable
final class PageBackground {
    static let shared = PageBackground()

    private static var file: URL { SharedContainer.directory.appendingPathComponent("page_background.jpg") }
    private static var stash: URL { SharedContainer.directory.appendingPathComponent("page_background.jpg.kept") }

    /// 밝기·채도를 적용한 사진 — 배경이 '사진'이 아니어도 지우지 않고 둡니다(다시 고르면 바로).
    private(set) var storedPhoto: UIImage?
    private var storedBlurred: UIImage?
    /// 화면에 그리는 사진 — 배경이 '사진'일 때만.
    var photo: UIImage? { Prefs.shared.backgroundStyle == .photo ? storedPhoto : nil }
    /// 크게 흐린 작은 사본(긴 변 96px) — 사진 위 글자색 판단과 불투명 서리 페이지용. 배경이 '사진'일 때만.
    var blurred: UIImage? { Prefs.shared.backgroundStyle == .photo ? storedBlurred : nil }
    /// 바뀔 때마다 1씩.
    private(set) var version = 0

    private init() { reload() }

    /// 고른 사진이 있는지(지금 배경으로 쓰는지와 별개).
    var hasPhoto: Bool { storedPhoto != nil }

    func reload() {
        let raw = (try? Data(contentsOf: Self.file)).flatMap(UIImage.init(data:))
        let adjusted = raw.map { Self.adjust($0, brightness: Prefs.shared.backgroundBrightness, saturation: Prefs.shared.backgroundSaturation) }
        storedPhoto = adjusted
        storedBlurred = adjusted.map { Self.blurredCopy($0) }
        version += 1
    }

    /// 자른 사진을 저장 — 긴 변 1600px.
    func save(_ image: UIImage) -> Bool {
        let scale = min(1, 1600 / max(image.size.width * image.scale, image.size.height * image.scale))
        let target = CGSize(width: image.size.width * image.scale * scale, height: image.size.height * image.scale * scale)
        let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1
        let scaled = UIGraphicsImageRenderer(size: target, format: fmt).image { _ in image.draw(in: CGRect(origin: .zero, size: target)) }
        guard let data = scaled.jpegData(compressionQuality: 0.88), (try? data.write(to: Self.file, options: .atomic)) != nil else { return false }
        reload()
        return true
    }

    func clear() { try? FileManager.default.removeItem(at: Self.file); reload() }

    /// 사진을 지우지 않고 옆에 치워 둡니다 — 업데이트 투어에서 '새 기본 배경'을 골랐다가 마음을 바꿔도 되돌릴 수 있게.
    func stashPhoto() {
        try? FileManager.default.removeItem(at: Self.stash)
        try? FileManager.default.moveItem(at: Self.file, to: Self.stash)
        reload()
    }

    /// 치워 둔 사진을 다시 배경으로 — 없으면 false.
    @discardableResult
    func restorePhoto() -> Bool {
        guard FileManager.default.fileExists(atPath: Self.stash.path) else { return false }
        try? FileManager.default.removeItem(at: Self.file)
        let ok = (try? FileManager.default.moveItem(at: Self.stash, to: Self.file)) != nil
        reload()
        return ok
    }

    // MARK: 이미지 처리

    private static let ci = CIContext(options: [.useSoftwareRenderer: false])

    /// 밝기(-0.4~0.4)·채도(0~1.6) — 안드로이드 색 행렬과 같은 뜻(밝기는 더하는 양).
    static func adjust(_ image: UIImage, brightness: Double, saturation: Double) -> UIImage {
        guard brightness != 0 || saturation != 1, let input = CIImage(image: image) else { return image }
        let f = CIFilter.colorControls()
        f.inputImage = input
        f.brightness = Float(brightness)
        f.saturation = Float(saturation)
        f.contrast = 1
        guard let out = f.outputImage, let cg = ci.createCGImage(out, from: input.extent) else { return image }
        return UIImage(cgImage: cg, scale: image.scale, orientation: image.imageOrientation)
    }

    /// 긴 변 96px 로 줄인 뒤 크게 흐린 사본 — 늘려 그리면 반경 수십 pt 의 흐림처럼 보입니다.
    static func blurredCopy(_ image: UIImage) -> UIImage {
        let scale = 96 / max(image.size.width, image.size.height)
        let size = CGSize(width: max(1, (image.size.width * scale).rounded()), height: max(1, (image.size.height * scale).rounded()))
        let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1
        let small = UIGraphicsImageRenderer(size: size, format: fmt).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        guard let input = CIImage(image: small) else { return small }
        let blur = CIFilter.gaussianBlur()
        blur.inputImage = input.clampedToExtent()
        blur.radius = 4.5
        guard let out = blur.outputImage?.cropped(to: input.extent), let cg = ci.createCGImage(out, from: input.extent) else { return small }
        return UIImage(cgImage: cg)
    }

    /// 창 좌표의 사각형 아래에 보이는 흐린 배경의 평균 밝기(0~1) — cover 배치로 좌표를 옮겨 12점을 읽습니다.
    static func luminance(under rect: CGRect, window: CGSize, in image: UIImage) -> CGFloat {
        guard let cg = image.cgImage, window.width > 0, window.height > 0, rect.width > 0, rect.height > 0 else { return 0.5 }
        let w = CGFloat(cg.width), h = CGFloat(cg.height)
        let scale = max(window.width / w, window.height / h)
        let offX = (window.width - w * scale) / 2, offY = (window.height - h * scale) / 2
        guard let pixels = PixelReader(cg) else { return 0.5 }
        var sum: CGFloat = 0
        for iy in 0..<3 { for ix in 0..<4 {
            let x = Int(((rect.minX + rect.width * (CGFloat(ix) + 0.5) / 4 - offX) / scale).clamped(0, w - 1))
            let y = Int(((rect.minY + rect.height * (CGFloat(iy) + 0.5) / 3 - offY) / scale).clamped(0, h - 1))
            sum += pixels.luminance(x, y)
        } }
        return sum / 12
    }

    /// 화면 위쪽(22%)에 보이는 사진의 평균 밝기 — 사진 위에 바로 놓이는 글자를 흰색/검정으로.
    static func onPageColor(window: CGSize, image: UIImage) -> Color {
        let lum = luminance(under: CGRect(x: 0, y: 0, width: window.width, height: window.height * 0.22), window: window, in: image)
        return lum * 255 < 150 ? .white : Color(argb: 0xFF111114)
    }
}

/// 비트맵 한 장을 읽는 작은 도우미.
private struct PixelReader {
    let data: [UInt8]
    let width: Int

    init?(_ cg: CGImage) {
        width = cg.width
        var buf = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        guard let ctx = CGContext(data: &buf, width: cg.width, height: cg.height, bitsPerComponent: 8, bytesPerRow: cg.width * 4,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        data = buf
    }

    func luminance(_ x: Int, _ y: Int) -> CGFloat {
        let i = (y * width + x) * 4
        return (0.2126 * CGFloat(data[i]) + 0.7152 * CGFloat(data[i + 1]) + 0.0722 * CGFloat(data[i + 2])) / 255
    }
}

extension Comparable {
    func clamped(_ lo: Self, _ hi: Self) -> Self { min(max(self, lo), hi) }
}

// MARK: - 그리기

/// 사진이 없을 때의 기본 배경 — 로고의 세 색(민트·인디고·코랄)을 아주 옅게 퍼뜨린 그라데이션과 미세한 그레인.
struct DefaultBackground: View {
    let dark: Bool

    var body: some View {
        GeometryReader { g in
            let r = max(g.size.width, g.size.height)
            ZStack {
                LinearGradient(colors: dark ? [Color(argb: 0xFF0E0F1C), Color(argb: 0xFF0B0C14), Color(argb: 0xFF110D16)]
                                            : [Color(argb: 0xFFF4F4FB), Color(argb: 0xFFF1F2F8), Color(argb: 0xFFF8F1F2)],
                               startPoint: .top, endPoint: .bottom)
                glow(dark ? Color(argb: 0xFF17504A).opacity(0.55) : Color(argb: 0xFFA6EBD7).opacity(0.60), 0.05, 0.04, r * 0.62)
                glow(dark ? Color(argb: 0xFF2A2A7C).opacity(0.60) : Color(argb: 0xFFBCC4FF).opacity(0.60), 0.95, 0.18, r * 0.66)
                glow(dark ? Color(argb: 0xFF5A2244).opacity(0.45) : Color(argb: 0xFFFFCDBE).opacity(0.55), 0.55, 1.0, r * 0.70)
                if let grain = UIImage(named: "page_grain_tile") {
                    Rectangle().fill(ImagePaint(image: Image(uiImage: grain), scale: 1 / UIScreen.main.scale)).opacity(dark ? 0.07 : 0.05)
                }
            }
        }
    }

    private func glow(_ c: Color, _ x: CGFloat, _ y: CGFloat, _ radius: CGFloat) -> some View {
        RadialGradient(colors: [c, c.opacity(0)], center: UnitPoint(x: x, y: y), startRadius: 0, endRadius: radius)
    }
}

/// 단색 배경 — 건강 앱 '요약'처럼 화면 위쪽만 그 색으로 물들고 아래로 갈수록 기본 바탕.
struct TopTintBackground: View {
    let color: Color
    let dark: Bool

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .top) {
                Color(argb: dark ? 0xFF000000 : 0xFFF2F2F7)
                LinearGradient(stops: [.init(color: color.opacity(dark ? 0.42 : 0.30), location: 0),
                                       .init(color: color.opacity(dark ? 0.16 : 0.10), location: 0.45),
                                       .init(color: color.opacity(0), location: 1)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: g.size.height * 0.45)
            }
        }
    }
}

/// 페이지 배경 — 설정의 배경 방식대로: 그라데이션(기본), 단색(위쪽만), 사진(막 없이 원래 색 그대로, 밝기·채도만).
struct PageBackdrop: View {
    @Environment(\.palette) private var palette
    var background = PageBackground.shared
    private var prefs = Prefs.shared

    var body: some View {
        Group {
            if let photo = background.photo {
                GeometryReader { g in
                    Image(uiImage: photo).resizable().scaledToFill().frame(width: g.size.width, height: g.size.height).clipped()
                }
            } else if prefs.backgroundStyle == .solid {
                TopTintBackground(color: prefs.backgroundSolidARGB == 0 ? palette.primary : Color(argb: prefs.backgroundSolidARGB), dark: palette.dark)
            } else {
                DefaultBackground(dark: palette.dark)
            }
        }
        .ignoresSafeArea()
    }
}

/// 불투명 서리 페이지 — 사진을 흐린 사본으로 깔고 테마 면 색을 짙게. 정보·개인정보 보호처럼 글이 긴 화면용.
struct FrostedPageBackdrop: View {
    @Environment(\.palette) private var palette
    var background = PageBackground.shared

    var body: some View {
        GeometryReader { g in
            ZStack {
                if let blurred = background.blurred {
                    Image(uiImage: blurred).resizable().scaledToFill().frame(width: g.size.width, height: g.size.height).clipped()
                    Rectangle().fill(palette.dark ? palette.surface.mix(palette.onSurface, 0.06).opacity(0.64) : palette.surface.opacity(0.80))
                } else if Prefs.shared.backgroundStyle == .solid {
                    TopTintBackground(color: Prefs.shared.backgroundSolidARGB == 0 ? palette.primary : Color(argb: Prefs.shared.backgroundSolidARGB), dark: palette.dark)
                } else {
                    DefaultBackground(dark: palette.dark)
                }
            }
        }
        .ignoresSafeArea()
    }
}

/// 창 크기 — 서리 카드가 흐린 배경을 창 좌표로 옮겨 그릴 때 씁니다.
private struct WindowSizeKey: EnvironmentKey { static let defaultValue: CGSize = UIScreen.main.bounds.size }
extension EnvironmentValues {
    var windowSize: CGSize {
        get { self[WindowSizeKey.self] }
        set { self[WindowSizeKey.self] = newValue }
    }
}
