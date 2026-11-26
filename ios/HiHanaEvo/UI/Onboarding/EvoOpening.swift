import SwiftUI
import UIKit

/*
 * HiHana Evo — 오프닝 애니메이션 (안드로이드 EvoOpening.kt 의 수식·상수를 그대로).
 *
 * 연출: 로고의 세 갈래(민트 · 인디고 · 코랄)가 화면 밖에서 가늘고 긴 실크 리본으로 흩날려 들어와, 자리에 닿는 부분부터
 *       로고 조각의 형태로 감싸지며 완성된다. 리본은 카메라 바로 앞을 스치듯 크게 나타났다가 멀어지며 제자리로 간다(원근).
 *       완성 후 아주 작은 숨 한 번, 대각선 하이라이트 한 번, 글자 등장.
 *
 * 구현: 각 갈래에 재료 좌표 (u 길이, v 폭)를 붙이고, 섹션 u 는 출발 시각부터 감쇠 스프링으로 접근 경로를 따라 날아온다.
 *       X(u, v, t) = O(t) + (1 − α)·비행모양 + α·P(u, v). 면을 u 띠 세그먼트로 나눠 경로를 매 프레임 워프하고, 그라데이션은
 *       세그먼트별 아핀 맞춤으로 따라가게 한다. 다 멈춘 뒤에는 정확히 원본 벡터다.
 */

private let twoPi = Float.pi * 2

// 생성된 상수 — 안드로이드와 같은 숫자(lottie/source/gen_kotlin_consts.py, 시안 A). 손으로 고치지 말 것.
private let TOTAL: Float = 7.0
private let TIME_SCALE: Float = 1.8
private let LOGO_CX: Float = 204, LOGO_CY: Float = 217
private let OVERLAP: Float = 0.004
private let LAND_MARKS: [Float] = [3.3, 3.783333, 3.466667]
private let TEXT_T0: Float = 5.2
private let LIGHT_X: Float = -0.451129, LIGHT_Y: Float = -0.55138, LIGHT_Z: Float = 0.701757
private let HALF_X: Float = -0.244533, HALF_Y: Float = -0.298874, HALF_Z: Float = 0.922431
private let K_DIFF: Float = 0.45, K_SPEC: Float = 0.5, SPEC_P: Float = 8.0, DIFF_REF: Float = 0.55
private let BREATH_T0: Float = 3.85, BREATH_T1: Float = 4.85, BREATH_AMP: Float = 0.022
private let HL_T0: Float = 4.0, HL_T1: Float = 5.2, HL_PEAK: Float = 0.3
private let HL_DIR_X: Float = 0.545, HL_DIR_Y: Float = 0.838, HL_SPAN: Float = 290.0, HL_WIDTH: Float = 70.0

// 화면·글자 타이밍
private let FADE: Float = 0.6
private let TEXT_STAGGER: Float = 0.35
private let TEXT_DUR: Float = 1.1
/// 글자를 읽을 정지 구간 — 리본 움직임은 TOTAL 안에서 끝나므로 늘어나는 건 정지 장면뿐.
private let TEXT_HOLD: Float = 2.2
let EVO_END: Float = TOTAL + TEXT_HOLD
private let T_STILL: Float = EVO_END - FADE - 0.2
/// 첫 장으로 잇기 — 마지막 0.75초 동안 어두운 바탕이 걷히고 첫 장이 같은 자리의 로고·글자로 이어받습니다.
let EVO_HANDOFF_DUR: Float = 0.75
let EVO_HANDOFF_T0: Float = EVO_END - EVO_HANDOFF_DUR

/// 갈래 순서: 0 = 왼쪽(민트), 1 = 가운데(인디고), 2 = 오른쪽(코랄). (길이축 L, 단면 방향 S)
private let RIBBON_FRAMES: [[Float]] = [[0, 1, 1, 0], [179, 46, 1, -0.69], [0, 1, 1, 0]]
private let SEGMENTS = [20, 16, 20]

private struct RibbonCfg {
    var order: Int
    var uc: Float = 0.5
    var t0: Float, kappa: Float
    var fx: Float, fy: Float, h0x: Float, h0y: Float
    var dinX: Float, dinY: Float, h1: Float
    var wf: Float
    var flutter: [Float] = []
    var twist: [Float]
    var omega: Float = 6.2, zeta: Float = 0.87
    var sW0: Float = 1.10, sW1: Float = 1.45
    var sa0: Float = 0.44, sa1: Float = 0.94
    var sf0: Float = 0.26, sf1: Float = 0.72
    var st0: Float = 0.28, st1: Float = 0.56
    var kp: Float = 0.24
    var taper: Float = 0.22
    var eps: Float = 0.2
    var spread: Float = 0
    var twistLag: Float = 0.25
    var drape: Float = 0.30
    var v0: Float = 0.5
    var persp: Float = 1
    var windAmp: Float = 0, windLong: Float = 0, windFlap: Float = 0, windRipple: Float = 0, windShear: Float = 0
    var waves: [Float] = [], gust: [Float] = []
    var windBoost: Float = 0
    var wide: Float = 0

    var p1x: Float { fx + h0x }
    var p1y: Float { fy + h0y }
    var p2x: Float { let n = (dinX * dinX + dinY * dinY).squareRoot(); return -dinX / n * h1 }
    var p2y: Float { let n = (dinX * dinX + dinY * dinY).squareRoot(); return -dinY / n * h1 }
    var landTime: Float { (t0 + kappa + sW1) * TIME_SCALE }
}

private let CHOREO: [RibbonCfg] = [
    RibbonCfg(order: 0, t0: -0.1, kappa: 0.62, fx: 77.99, fy: 197.24, h0x: 85.15, h0y: -11.81, dinX: -0.616, dinY: -0.7877, h1: 153.4, wf: 46,
              twist: [1.9, 0.8, 0.35, 0.4, 0.5, 1.9, 0.8, 2.0],
              omega: 4.8, zeta: 0.9, sW0: 1.55, sW1: 1.95, sa0: 0.88, sa1: 1.3, sf0: 0.45, sf1: 1.05, st0: 0.55, st1: 0.95, kp: 0.24, taper: 0.22,
              eps: 0.2, spread: 0, twistLag: 0.25, drape: 0.3, v0: 0, persp: 8,
              windAmp: 46, windLong: 0.35, windFlap: 0.9, windRipple: 0.22, windShear: 0.2,
              waves: [1.0, 1.15, 0.95, 0.0, 0.55, 2.1, 1.65, 1.9, 0.25, 3.6, 2.8, 4.1], gust: [0.3, 0.31, 1.1, 0.2, 0.53, 2.3],
              windBoost: 1.2, wide: 2.6),
    RibbonCfg(order: 0, t0: 0.08, kappa: 0.4, fx: 114.81, fy: 178.47, h0x: -194.78, h0y: -91.63, dinX: -0.8495, dinY: -0.5277, h1: 241.84, wf: 48,
              twist: [2.2, 0.9, 0.5, 0.0, 0.6, 2.0, 1.0, 0.4],
              omega: 4.8, zeta: 0.9, sW0: 1.55, sW1: 1.95, sa0: 0.88, sa1: 1.3, sf0: 0.45, sf1: 1.05, st0: 0.55, st1: 0.95, kp: 0.24, taper: 0.22,
              eps: 0.2, spread: 0, twistLag: 0.25, drape: 0.3, v0: 0, persp: 8,
              windAmp: 38, windLong: 0.35, windFlap: 0.8, windRipple: 0.2, windShear: 0.18,
              waves: [1.0, 1.2, 0.9, 1.0, 0.55, 2.0, 1.6, 2.6, 0.25, 3.7, 2.7, 0.3], gust: [0.3, 0.33, 0.6, 0.2, 0.49, 1.7],
              windBoost: 1.2, wide: 3.0),
    RibbonCfg(order: 1, t0: 0.0, kappa: 0.62, fx: -45.5, fy: -179.35, h0x: -227.58, h0y: 203.92, dinX: 0.4365, dinY: 0.8997, h1: 72.68, wf: 46,
              twist: [1.9, 0.8, -0.35, 2.1, 0.5, 1.9, -0.8, 0.7],
              omega: 4.8, zeta: 0.9, sW0: 1.55, sW1: 1.95, sa0: 0.88, sa1: 1.3, sf0: 0.45, sf1: 1.05, st0: 0.55, st1: 0.95, kp: 0.24, taper: 0.22,
              eps: 0.2, spread: 0, twistLag: 0.25, drape: 0.3, v0: 1, persp: 8,
              windAmp: 46, windLong: 0.35, windFlap: 0.9, windRipple: 0.22, windShear: 0.2,
              waves: [1.0, 1.1, 1.0, 2.0, 0.55, 2.2, 1.7, 0.4, 0.25, 3.5, 2.9, 3.0], gust: [0.3, 0.29, 2.0, 0.2, 0.57, 0.4],
              windBoost: 1.2, wide: 3.0),
]

private struct SurfaceDef {
    let id: String
    let ribbon: Int
    let d: String
    let sx: Float, sy: Float, ex: Float, ey: Float
    let stops: [Float]
    let colors: [UInt32]
}

/// 부록 벡터(레이어 순서 뒤 → 앞).
private let SURFACE_DEFS: [SurfaceDef] = [
    SurfaceDef(id: "01_left_tail", ribbon: 0,
               d: "M0 298 C5 268 14 250 40 225 C49 237 63 243 84 249 L138 262.904192 L138 312 C138 340 122 355 100 370 L27 409 C10 421 0 412 0 396 Z",
               sx: 45, sy: 229, ex: 90, ey: 417, stops: [0, 0.48, 1], colors: [0xFF2850F5, 0xFF526FFF, 0xFF829CFF]),
    SurfaceDef(id: "02_right_back", ribbon: 2,
               d: "M387 13 C400 5 408 12 408 28 L408 141 C407 175 389 197 364 215 L266 284 L266 125 C266 100 277 82 296 69 Z",
               sx: 385, sy: 13, ex: 300, ey: 219, stops: [0, 0.25, 0.6, 1], colors: [0xFFFFE095, 0xFFFFCB88, 0xFFFF8B83, 0xFFF24C9E]),
    SurfaceDef(id: "03_center_bridge", ribbon: 1,
               d: "M150 147 L329 193 C343 197 355 204 364 215 C338 239 296 264 251 292 L84 249 C63 243 49 237 40 225 C69 200 108 173 150 147 Z",
               sx: 100, sy: 206, ex: 300, ey: 240, stops: [0, 0.5, 1], colors: [0xFF2616D7, 0xFF4925FB, 0xFF3C14C3]),
    SurfaceDef(id: "04_center_fold", ribbon: 1,
               d: "M266 176.810056 L329 193 C343 197 355 204 364 215 C338 239 296 264 266 282.666667 Z",
               sx: 266, sy: 180, ex: 351, ey: 250, stops: [0, 0.65, 1], colors: [0xFF652BB4, 0xFF45199D, 0xFF37108F]),
    SurfaceDef(id: "05_left_front", ribbon: 0,
               d: "M164 0 C186 0 198 14 198 40 L198 66 C198 104 180 127 150 147 C110 173 69 200 40 225 C14 250 5 268 0 298 L0 157 C0 116 21 87 52 70 L143 10 C151 5 158 0 164 0 Z",
               sx: 110, sy: 10, ex: 101, ey: 278, stops: [0, 0.32, 0.58, 0.8, 1], colors: [0xFF38EAA8, 0xFF27D9B8, 0xFF25ACEA, 0xFF0873F9, 0xFF4766FF]),
    SurfaceDef(id: "06_right_front", ribbon: 2,
               d: "M408 141 C407 175 389 197 364 215 C339 239 296 265 251 292 L235 302 C215 318 202 338 202 362 L202 399 C202 424 218 440 242 430 C264 419 314 386 357 357 C389 337 408 308 408 271 Z",
               sx: 406, sy: 186, ex: 217, ey: 415, stops: [0, 0.35, 0.7, 1], colors: [0xFFFFCF92, 0xFFFFA48E, 0xFFFF5A97, 0xFFDF39A8]),
]

private let SILHOUETTE = "M164 0 C186 0 198 14 198 40 L198 66 C198 104 180 127 150 147 L266 176.810056 L266 125 C266 100 277 82 296 69 L387 13 C400 5 408 12 408 28 L408 271 C408 308 389 337 357 357 C314 386 264 419 242 430 C218 440 202 424 202 399 L202 362 C202 338 215 318 235 302 L251 292 L138 262.904192 L138 312 C138 340 122 355 100 370 L27 409 C10 421 0 412 0 396 L0 157 C0 116 21 87 52 70 L143 10 C151 5 158 0 164 0 Z"

// MARK: - 1. 로고 기하

/// 3차 베지어 조각 하나: x0 y0 x1 y1 x2 y2 x3 y3. 직선도 3차로.
private typealias Cub = [Float]

private func lineCub(_ x0: Float, _ y0: Float, _ x3: Float, _ y3: Float) -> Cub {
    [x0, y0, x0 + (x3 - x0) / 3, y0 + (y3 - y0) / 3, x0 + 2 * (x3 - x0) / 3, y0 + 2 * (y3 - y0) / 3, x3, y3]
}

private func bezX(_ c: Cub, _ t: Float) -> Float { let m = 1 - t; return m * m * m * c[0] + 3 * m * m * t * c[2] + 3 * m * t * t * c[4] + t * t * t * c[6] }
private func bezY(_ c: Cub, _ t: Float) -> Float { let m = 1 - t; return m * m * m * c[1] + 3 * m * m * t * c[3] + 3 * m * t * t * c[5] + t * t * t * c[7] }

private func splitCub(_ c: Cub, _ t: Float) -> (Cub, Cub) {
    func lerp(_ a: Float, _ b: Float) -> Float { a + (b - a) * t }
    let x01 = lerp(c[0], c[2]), y01 = lerp(c[1], c[3]), x12 = lerp(c[2], c[4]), y12 = lerp(c[3], c[5]), x23 = lerp(c[4], c[6]), y23 = lerp(c[5], c[7])
    let x012 = lerp(x01, x12), y012 = lerp(y01, y12), x123 = lerp(x12, x23), y123 = lerp(y12, y23)
    let x = lerp(x012, x123), y = lerp(y012, y123)
    return ([c[0], c[1], x01, y01, x012, y012, x, y], [x, y, x123, y123, x23, y23, c[6], c[7]])
}

/// M/L/C/Z 절대 좌표 경로 → 닫힌 3차 조각 목록.
private func parsePath(_ d: String) -> [Cub] {
    let re = try! NSRegularExpression(pattern: #"[MLCZ]|-?\d*\.?\d+(?:e-?\d+)?"#)
    let ns = d as NSString
    let tok = re.matches(in: d, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range) }
    var out: [Cub] = []
    var i = 0
    var cx: Float = 0, cy: Float = 0, sx: Float = 0, sy: Float = 0
    func num() -> Float { defer { i += 1 }; return Float(tok[i])! }
    while i < tok.count {
        let t = tok[i]; i += 1
        switch t {
        case "M": cx = num(); cy = num(); sx = cx; sy = cy
        case "L": let x = num(), y = num(); out.append(lineCub(cx, cy, x, y)); cx = x; cy = y
        case "C":
            let c: Cub = [cx, cy, num(), num(), num(), num(), num(), num()]
            out.append(c); cx = c[6]; cy = c[7]
        case "Z":
            if abs(cx - sx) > 1e-6 || abs(cy - sy) > 1e-6 { out.append(lineCub(cx, cy, sx, sy)) }
            cx = sx; cy = sy
        default: break
        }
    }
    return out
}

private func interp(_ x: Float, _ xs: [Float], _ ys: [Float]) -> Float {
    let n = xs.count
    if x <= xs[0] { return ys[0] }
    if x >= xs[n - 1] { return ys[n - 1] }
    let f = (x - xs[0]) / (xs[n - 1] - xs[0]) * Float(n - 1)
    let k = min(Int(f), n - 2)
    let w = f - Float(k)
    return ys[k] + (ys[k + 1] - ys[k]) * w
}

private func cubPath(_ cubs: [Cub]) -> Path {
    var p = Path()
    guard let first = cubs.first else { return p }
    p.move(to: CGPoint(x: CGFloat(first[0]), y: CGFloat(first[1])))
    for c in cubs { p.addCurve(to: CGPoint(x: CGFloat(c[6]), y: CGFloat(c[7])), control1: CGPoint(x: CGFloat(c[2]), y: CGFloat(c[3])), control2: CGPoint(x: CGFloat(c[4]), y: CGFloat(c[5]))) }
    p.closeSubpath()
    return p
}

/// 갈래 하나의 재료 좌표 — 로고 좌표를 사선 좌표 p = a·L + b·S 로 나눠 u = (a − a0)/(a1 − a0), v = (b − bl(u)) / (br(u) − bl(u)).
private final class RibbonParam {
    let Lx: Float, Ly: Float, Sx: Float, Sy: Float
    private let i00: Float, i01: Float, i10: Float, i11: Float
    let a0: Float, a1: Float
    let ug: [Float]
    var bl: [Float], br: [Float]
    var bmSmooth: [Float]

    init(_ lx: Float, _ ly: Float, _ sx: Float, _ sy: Float, surfaces: [[Cub]], n: Int = 1025) {
        let ln = (lx * lx + ly * ly).squareRoot(), sn = (sx * sx + sy * sy).squareRoot()
        Lx = lx / ln; Ly = ly / ln; Sx = sx / sn; Sy = sy / sn
        let det = Lx * Sy - Sx * Ly
        i00 = Sy / det; i01 = -Sx / det; i10 = -Ly / det; i11 = Lx / det
        ug = (0..<n).map { Float($0) / Float(n - 1) }
        let (i00, i01, i10, i11) = (self.i00, self.i01, self.i10, self.i11)
        let polys: [([Float], [Float])] = surfaces.map { cubs in
            let m = cubs.count * 48
            var a = [Float](repeating: 0, count: m + 1), b = [Float](repeating: 0, count: m + 1)
            var k = 0
            for c in cubs { for j in 0..<48 { let t = Float(j) / 48; let x = bezX(c, t), y = bezY(c, t); a[k] = i00 * x + i01 * y; b[k] = i10 * x + i11 * y; k += 1 } }
            a[m] = a[0]; b[m] = b[0]
            return (a, b)
        }
        var amin = Float.greatestFiniteMagnitude, amax = -Float.greatestFiniteMagnitude
        for (a, _) in polys { for v in a { amin = min(amin, v); amax = max(amax, v) } }
        a0 = amin; a1 = amax
        var bl = [Float](repeating: .nan, count: n), br = [Float](repeating: .nan, count: n)
        for k in 0..<n {
            let lev = amin + ug[k] * (amax - amin)
            for (a, b) in polys {
                for j in 0..<(a.count - 1) {
                    let ap = a[j], aq = a[j + 1]
                    if (ap - lev) * (aq - lev) <= 0 && abs(aq - ap) > 1e-12 {
                        let bb = b[j] + (lev - ap) / (aq - ap) * (b[j + 1] - b[j])
                        bl[k] = bl[k].isNaN ? bb : min(bl[k], bb)
                        br[k] = br[k].isNaN ? bb : max(br[k], bb)
                    }
                }
            }
        }
        func fillNaN(_ arr: inout [Float], _ ug: [Float]) {
            let good = arr.indices.filter { !arr[$0].isNaN }
            let gx = good.map { ug[$0] }, gy = good.map { arr[$0] }
            for k in arr.indices where arr[k].isNaN {
                let x = ug[k]
                if x <= gx[0] { arr[k] = gy[0]; continue }
                if x >= gx.last! { arr[k] = gy.last!; continue }
                var j = 1
                while gx[j] < x { j += 1 }
                let w = (x - gx[j - 1]) / (gx[j] - gx[j - 1])
                arr[k] = gy[j - 1] + (gy[j] - gy[j - 1]) * w
            }
        }
        fillNaN(&bl, ug); fillNaN(&br, ug)
        for k in 0..<n {   // 끝 단면은 점으로 줄어든다 → 최소 반폭 0.6
            let mid = 0.5 * (bl[k] + br[k]), half = max(0.5 * (br[k] - bl[k]), 0.6)
            bl[k] = mid - half; br[k] = mid + half
        }
        self.bl = bl; self.br = br
        // 비행용 매끈한 척추: 단면 중앙 b 를 u 에 대한 직선으로 근사 (0.04 < u < 0.96)
        var s0 = 0.0, s1 = 0.0, s2 = 0.0, t0 = 0.0, t1 = 0.0
        for k in 0..<n where ug[k] > 0.04 && ug[k] < 0.96 {
            let u = Double(ug[k]), m = 0.5 * Double(bl[k] + br[k])
            s0 += 1; s1 += u; s2 += u * u; t0 += m; t1 += u * m
        }
        let slope = (s0 * t1 - s1 * t0) / (s0 * s2 - s1 * s1), icpt = (t0 - slope * s1) / s0
        bmSmooth = ug.map { Float(icpt + slope * Double($0)) }
    }

    var gx: Float { i00 / (a1 - a0) }
    var gy: Float { i01 / (a1 - a0) }
    var g0: Float { -a0 / (a1 - a0) }

    func u(_ x: Float, _ y: Float) -> Float { ((i00 * x + i01 * y) - a0) / (a1 - a0) }
    func v(_ x: Float, _ y: Float, _ u: Float) -> Float {
        let b = i10 * x + i11 * y
        let l = interp(u, ug, bl), r = interp(u, ug, br)
        return (b - l) / (r - l)
    }
    func px(_ u: Float, _ v: Float) -> Float { let a = a0 + u * (a1 - a0); let l = interp(u, ug, bl), r = interp(u, ug, br); return a * Lx + (l + v * (r - l)) * Sx }
    func py(_ u: Float, _ v: Float) -> Float { let a = a0 + u * (a1 - a0); let l = interp(u, ug, bl), r = interp(u, ug, br); return a * Ly + (l + v * (r - l)) * Sy }
    func smX(_ u: Float) -> Float { (a0 + u * (a1 - a0)) * Lx + interp(u, ug, bmSmooth) * Sx }
    func smY(_ u: Float) -> Float { (a0 + u * (a1 - a0)) * Ly + interp(u, ug, bmSmooth) * Sy }
}

/// 베지어 c 위에서 u(p(t)) = level 인 t 들.
private func uRoots(_ c: Cub, _ gx: Float, _ gy: Float, _ g0: Float, _ level: Float) -> [Float] {
    func f(_ t: Float) -> Float { bezX(c, t) * gx + bezY(c, t) * gy + g0 - level }
    var out: [Float] = []
    let steps = 64
    var t0: Float = 0, f0 = f(0)
    for k in 1...steps {
        let t1 = Float(k) / Float(steps), f1 = f(t1)
        if (f0 <= 0 && f1 > 0) || (f0 >= 0 && f1 < 0) {
            var lo = t0, hi = t1, flo = f0
            for _ in 0..<40 {
                let mid = 0.5 * (lo + hi), fm = f(mid)
                if flo * fm <= 0 { hi = mid } else { lo = mid; flo = fm }
            }
            let r = 0.5 * (lo + hi)
            if r > 1e-6 && r < 1 - 1e-6 { out.append(r) }
        }
        t0 = t1; f0 = f1
    }
    return out
}

/// 외곽 베지어를 u 경계(levels)와 최대 길이로 잘게 나눈다.
private func subdivide(_ cubs: [Cub], _ p: RibbonParam, _ levels: [Float], maxLen: Float = 24) -> [Cub] {
    let gx = p.gx, gy = p.gy, g0 = p.g0
    var out: [Cub] = []
    for c in cubs {
        var seen = Set<Int>()
        let ts = levels.filter { $0 > 0 && $0 < 1 }.flatMap { uRoots(c, gx, gy, g0, $0) }.filter { seen.insert(Int(($0 * 1e6).rounded())).inserted }.sorted()
        var pieces: [Cub] = []
        var rest = c, tPrev: Float = 0
        for t in ts {
            let (a, b) = splitCub(rest, (t - tPrev) / (1 - tPrev))
            pieces.append(a); rest = b; tPrev = t
        }
        pieces.append(rest)
        for pc in pieces {
            var len: Float = 0, lx = pc[0], ly = pc[1]
            for j in 1...8 { let x = bezX(pc, Float(j) / 8), y = bezY(pc, Float(j) / 8); len += hypot(x - lx, y - ly); lx = x; ly = y }
            var r = pc
            let n = max(1, Int((len / maxLen).rounded(.up)))
            if n >= 2 { for k in stride(from: n, through: 2, by: -1) { let (a, b) = splitCub(r, 1 / Float(k)); out.append(a); r = b } }
            out.append(r)
        }
    }
    return out.filter { hypot($0[6] - $0[0], $0[7] - $0[1]) > 1e-6 || hypot($0[2] - $0[0], $0[3] - $0[1]) > 1e-6 }
}

/// u ≤ level (keepBelow) 또는 u ≥ level 반평면으로 자르기.
private func clipHalf(_ pieces: [Cub], _ p: RibbonParam, _ level: Float, _ keepBelow: Bool) -> [Cub] {
    let inside = pieces.map { pc -> Bool in
        let u = p.u(bezX(pc, 0.5), bezY(pc, 0.5))
        return keepBelow ? u <= level + 1e-6 : u >= level - 1e-6
    }
    if inside.allSatisfy({ $0 }) { return pieces }
    if !inside.contains(true) { return [] }
    let n = pieces.count
    let k0 = inside.firstIndex(of: true)!
    var out: [Cub] = []
    var exitX = Float.nan, exitY = Float.nan
    for s in 0..<n {
        let idx = (k0 + s) % n
        let pc = pieces[idx]
        if inside[idx] {
            if !exitX.isNaN {
                if hypot(pc[0] - exitX, pc[1] - exitY) > 1e-6 { out.append(lineCub(exitX, exitY, pc[0], pc[1])) }
                exitX = .nan
            }
            out.append(pc)
        } else if exitX.isNaN { exitX = pc[0]; exitY = pc[1] }
    }
    if !exitX.isNaN && hypot(out[0][0] - exitX, out[0][1] - exitY) > 1e-6 { out.append(lineCub(exitX, exitY, out[0][0], out[0][1])) }
    return out
}

private struct SegmentGeom { let k: Int; let u0: Float; let u1: Float; let pieces: [Cub] }

private func segmentSurface(_ cubs: [Cub], _ p: RibbonParam, _ cuts: [Float], _ overlap: Float) -> [SegmentGeom] {
    var out: [SegmentGeom] = []
    for k in 0..<(cuts.count - 1) {
        let lo = k > 0 ? cuts[k] - overlap : cuts[k]
        let hi = cuts[k + 1]
        let loC: Float = k > 0 ? lo : -1
        let hiC: Float = k < cuts.count - 2 ? hi : 2
        let fine = subdivide(cubs, p, [lo, hi])
        var pc = clipHalf(fine, p, hiC, true)
        if !pc.isEmpty { pc = clipHalf(pc, p, loC, false) }
        if pc.isEmpty { continue }
        var area: Float = 0
        for c in pc { for j in 0..<8 { let t0 = Float(j) / 8, t1 = Float(j + 1) / 8; area += bezX(c, t0) * bezY(c, t1) - bezX(c, t1) * bezY(c, t0) } }
        if abs(area) * 0.5 < 0.5 { continue }
        out.append(SegmentGeom(k: k, u0: cuts[k], u1: cuts[k + 1], pieces: pc))
    }
    return out
}

// MARK: - 2. 운동

private func smootherstep(_ e0: Float, _ e1: Float, _ x: Float) -> Float {
    let t = min(max((x - e0) / (e1 - e0), 0), 1)
    return t * t * t * (t * (t * 6 - 15) + 10)
}

/// 0 → 1 감쇠 스프링(정지에서 출발). s_w0~s_w1 에서 잔차를 부드럽게 0 으로 접어 유한 시간에 정확히 멈춘다.
private func spring(_ s: Float, _ c: RibbonCfg) -> Double {
    let ss = Double(max(s, 0))
    let z = Double(c.zeta), w = Double(c.omega)
    let wd = w * (1 - z * z).squareRoot()
    let e = exp(-z * w * ss)
    let r = 1 - e * (cos(wd * ss) + z / (1 - z * z).squareRoot() * sin(wd * ss))
    return 1 - (1 - r) * (1 - Double(smootherstep(c.sW0, c.sW1, Float(ss))))
}

/// 매 프레임 u 격자 위에서 계산하는 갈래 상태.
private final class Ribbon {
    let p: RibbonParam
    let c: RibbonCfg
    let nu: Int
    let ug: [Float]
    let smx: [Float], smy: [Float], wf: [Float]
    var ox: [Float], oy: [Float], sec: [Float], ps: [Float], alpha: [Float]
    var tx: [Float], ty: [Float], t0x: [Float], t0y: [Float]
    var fl: [Float], flt: [Float], rip: [Float], shr: [Float], th: [Float], wm: [Float], lit: [Float]
    private var cx: [Float], cy: [Float]

    init(_ p: RibbonParam, _ c: RibbonCfg, nu: Int = 257) {
        self.p = p; self.c = c; self.nu = nu
        ug = (0..<nu).map { Float($0) / Float(nu - 1) }
        smx = ug.map { p.smX($0) }
        smy = ug.map { p.smY($0) }
        wf = ug.map { u in max(c.wf * pow(min(max(min(u, 1 - u) / c.taper, 0), 1), 0.75), c.wf * 0.02) }
        let z = [Float](repeating: 0, count: nu)
        ox = z; oy = z; sec = z; ps = z; alpha = z; tx = z; ty = z; t0x = z; t0y = z
        fl = z; flt = z; rip = z; shr = z; th = z; wm = z; lit = z; cx = z; cy = z
    }

    private func order(_ u: Float) -> Float {
        switch c.order {
        case 0: return u
        case 1: return 1 - u
        default:
            let d = (u - c.uc) / max(c.uc, 1 - c.uc)
            return ((d * d + c.eps * c.eps).squareRoot() - c.eps) / ((1 + c.eps * c.eps).squareRoot() - c.eps)
        }
    }

    private func tangents(_ x: [Float], _ y: [Float], _ outX: inout [Float], _ outY: inout [Float]) {
        for k in 0..<nu {
            let k0 = max(k - 1, 0), k1 = min(k + 1, nu - 1)
            let dx = x[k1] - x[k0], dy = y[k1] - y[k0]
            let n = max((dx * dx + dy * dy).squareRoot(), 1e-9)
            outX[k] = dx / n; outY[k] = dy / n
        }
    }

    func update(_ tReal: Float) {
        let t = tReal / TIME_SCALE
        var gust: Float = 1
        var gi = 0
        while gi < c.gust.count { gust += c.gust[gi] * sin(twoPi * c.gust[gi + 1] * t + c.gust[gi + 2]); gi += 3 }
        for k in 0..<nu {
            let u = ug[k]
            let w = order(u)
            let s = t - (c.t0 + c.kappa * w)
            sec[k] = s
            let rd = spring(s, c), md = 1 - rd
            let r = Float(rd), m = Float(md)
            ox[k] = Float(md * md * md * Double(c.fx) + 3 * md * md * rd * Double(c.p1x) + 3 * md * rd * rd * Double(c.p2x))
            oy[k] = Float(md * md * md * Double(c.fy) + 3 * md * md * rd * Double(c.p1y) + 3 * md * rd * rd * Double(c.p2y))
            if c.spread != 0 {
                let g = (m * m * m + 3 * m * m * r) * c.spread * (u - c.uc) * (p.a1 - p.a0)
                ox[k] += g * p.Lx; oy[k] += g * p.Ly
            }
            // 원근: 카메라 바로 앞에서 출발해 로고 평면으로 멀어진다(출발 직후 0.18초는 카메라 뒤에서 스쳐 들어옴).
            var pss = 1 + Double(c.persp - 1) * md * md
            if c.persp > 1 { pss *= 1 + 20 * Double(1 - smootherstep(0, 0.18, s)) }
            ps[k] = Float(pss)
            alpha[k] = smootherstep(c.sa0, c.sa1, s)
            let ef = 1 - smootherstep(c.sf0, c.sf1, s)
            let lag = c.twistLag * (1 - w)
            let envT = 1 - smootherstep(c.st0 + lag, c.st1 + lag, s)
            var a: Float = 0
            var i = 0
            while i < c.twist.count { a += c.twist[i] * sin(twoPi * (c.twist[i + 1] * u - c.twist[i + 2] * t) + c.twist[i + 3]); i += 4 }
            th[k] = a * envT
            var wv: Float = 0, wv2: Float = 0, wv3: Float = 0, rr: Float = 0, rs: Float = 0
            i = 0
            while i < c.waves.count {
                let amp = c.waves[i], kk = c.waves[i + 1], ff = c.waves[i + 2], ph = c.waves[i + 3]
                let q = twoPi * (kk * w - ff * t) + ph
                wv += amp * sin(q); wv2 += amp * sin(q + 1.3); wv3 += amp * cos(q)
                let q2 = twoPi * (1.7 * kk * w - 1.3 * ff * t) + ph + 0.7
                rr += amp * sin(q2); rs += amp * cos(q2)
                i += 4
            }
            let base = ef * gust * (0.2 + 0.8 * pow(w, 0.8)) * (1 + c.windBoost * m * m)
            wm[k] = 1 + c.wide * pow(max(m, 0), 1.5)
            fl[k] = c.windAmp * base * wv
            flt[k] = c.windLong * c.windAmp * base * wv2
            th[k] += c.windFlap * base * wv3
            rip[k] = c.windRipple * base * rr
            shr[k] = c.windShear * base * rs
            i = 0
            while i < c.flutter.count { fl[k] += ef * c.flutter[i] * sin(twoPi * (c.flutter[i + 1] * u - c.flutter[i + 2] * t) + c.flutter[i + 3]); i += 4 }
            cx[k] = smx[k] + ox[k]; cy[k] = smy[k] + oy[k]
        }
        tangents(cx, cy, &t0x, &t0y)
        for k in 0..<nu {
            let nX = t0y[k], nY = -t0x[k]
            cx[k] += fl[k] * nX + flt[k] * t0x[k]; cy[k] += fl[k] * nY + flt[k] * t0y[k]
        }
        tangents(cx, cy, &tx, &ty)
        for k in 0..<nu {
            let ct = cos(th[k]), st = sin(th[k]), sg: Float = ct >= 0 ? 1 : -1
            let nX = ty[k], nY = -tx[k]
            let ndl = sg * (ct * LIGHT_Z - st * (nX * LIGHT_X + nY * LIGHT_Y))
            let ndh = sg * (ct * HALF_Z - st * (nX * HALF_X + nY * HALF_Y))
            lit[k] = (K_DIFF * (ndl - DIFF_REF) + K_SPEC * (pow(max(ndh, 0), SPEC_P) - pow(HALF_Z, SPEC_P))) * (1 - alpha[k])
        }
    }

    /// 재료점 (u, v) 의 화면(로고 단위) 위치.
    func x(_ u: Float, _ v: Float) -> (Float, Float) {
        let f = min(max((u - ug[0]) / (ug[nu - 1] - ug[0]) * Float(nu - 1), 0), Float(nu - 1))
        let k = min(Int(f), nu - 2), w = f - Float(k)
        func ip(_ a: [Float]) -> Float { a[k] + (a[k + 1] - a[k]) * w }
        var tX = ip(tx), tY = ip(ty)
        let tn = max((tX * tX + tY * tY).squareRoot(), 1e-9); tX /= tn; tY /= tn
        let nX = tY, nY = -tX
        var t0X = ip(t0x), t0Y = ip(t0y)
        let t0n = max((t0X * t0X + t0Y * t0Y).squareRoot(), 1e-9); t0X /= t0n; t0Y /= t0n
        let n0X = t0Y, n0Y = -t0X
        let al: Float
        if c.drape != 0 {
            // 바깥 가장자리(v0)부터 안쪽으로 내려앉는다.
            let dv = abs(min(max(v, 0), 1) - c.v0) / max(c.v0, 1 - c.v0)
            let le = c.drape * smootherstep(0.05, 0.18, min(u, 1 - u))
            al = smootherstep(c.sa0 + le * (dv - 0.5), c.sa1 + le * (dv - 0.5), ip(sec))
        } else { al = ip(alpha) }
        let flv = ip(fl), fltv = ip(flt), ripv = ip(rip), shrv = ip(shr)
        let thv = ip(th), wfv = ip(wf) * ip(wm)
        let vc = min(max(v, -0.05), 1.05) - 0.5
        let ct = cos(thv) * (1 + ripv), st = c.kp * sin(thv) * (1 + ripv) + shrv
        let fx = p.smX(u) + flv * n0X + fltv * t0X + vc * wfv * (ct * nX + st * tX)
        let fy = p.smY(u) + flv * n0Y + fltv * t0Y + vc * wfv * (ct * nY + st * tY)
        let xf = ip(ox) + (1 - al) * fx + al * p.px(u, v)
        let yf = ip(oy) + (1 - al) * fy + al * p.py(u, v)
        let s = ip(ps)
        return (LOGO_CX + s * (xf - LOGO_CX), LOGO_CY + s * (yf - LOGO_CY))
    }
}

// MARK: - 3. 세그먼트

private let DT: Float = 0.02

private final class Surface {
    let def: SurfaceDef
    let cubics: [Cub]
    let finalPath: Path
    let gradient: Gradient
    init(_ d: SurfaceDef) {
        def = d
        cubics = parsePath(d.d)
        finalPath = cubPath(cubics)
        gradient = Gradient(stops: zip(d.stops, d.colors).map { Gradient.Stop(color: Color(argb: $1), location: CGFloat($0)) })
    }
}

private final class SegmentRig {
    let surface: Surface
    let rb: Ribbon
    let u0: Float, u1: Float
    let n: Int
    private let pc: [Cub]
    private var pu: [Float], pv: [Float], ref: [Float], xs: [Float]
    private var fu = [Float](repeating: 0, count: 20), fv = [Float](repeating: 0, count: 20)
    private var fpx = [Float](repeating: 0, count: 20), fpy = [Float](repeating: 0, count: 20)
    private var gInv = [Double](repeating: 0, count: 9)

    var path = Path()
    var gradTransform = CGAffineTransform.identity
    var shadeTransform = CGAffineTransform.identity
    var lightTransform = CGAffineTransform.identity
    var shadeA0: Float = 0, shadeA1: Float = 0, lightA0: Float = 0, lightA1: Float = 0, landed: Float = 0

    init(_ surface: Surface, _ rb: Ribbon, _ g: SegmentGeom) {
        self.surface = surface; self.rb = rb
        u0 = g.u0; u1 = g.u1; n = g.pieces.count; pc = g.pieces
        pu = [Float](repeating: 0, count: 3 * n); pv = pu
        ref = [Float](repeating: 0, count: 6 * n); xs = ref
        let p = rb.p
        for i in 0..<n {
            let c = pc[i]
            let pts: [Float] = [c[0], c[1], bezX(c, DT), bezY(c, DT), bezX(c, 1 - DT), bezY(c, 1 - DT)]
            for j in 0..<3 {
                let x = pts[2 * j], y = pts[2 * j + 1]
                let q = j * n + i
                ref[2 * q] = x; ref[2 * q + 1] = y
                pu[q] = p.u(x, y); pv[q] = p.v(x, y, pu[q])
            }
        }
        var a = [Double](repeating: 0, count: 9)
        // 이웃 세그먼트와 표본을 반씩 겹쳐 맞춘다 → 이음선에서 색이 이어진다
        let du = 0.5 * (u1 - u0)
        let fa = max(u0 - du, 0), fb = min(u1 + du, 1)
        var q = 0
        for vi in 0..<4 {
            for ui in 0..<5 {
                fu[q] = fa + (fb - fa) * Float(ui) / 4; fv[q] = Float(vi) / 3
                fpx[q] = p.px(fu[q], fv[q]); fpy[q] = p.py(fu[q], fv[q])
                let g = [Double(fpx[q]), Double(fpy[q]), 1]
                for r in 0..<3 { for cc in 0..<3 { a[3 * r + cc] += g[r] * g[cc] } }
                q += 1
            }
        }
        invert3(a, &gInv)
    }

    func update() {
        for q in 0..<(3 * n) { let (x, y) = rb.x(pu[q], pv[q]); xs[2 * q] = x; xs[2 * q + 1] = y }
        // 앵커는 그대로, 핸들은 원래 핸들 + 변위의 곡선 방향 미분(착지 후 변위 0 → 원본 곡선). 크게 눌린 곳은 현의 0.4배로 제한.
        var path = Path()
        let al = interp(0.5 * (u0 + u1), rb.ug, rb.alpha)
        for i in 0..<n {
            let nx = (i + 1) % n
            let ax = xs[2 * i], ay = xs[2 * i + 1], bx = xs[2 * nx], by = xs[2 * nx + 1]
            let d0x = ax - ref[2 * i], d0y = ay - ref[2 * i + 1]
            let qa = n + i, qb = 2 * n + i
            let dax = xs[2 * qa] - ref[2 * qa], day = xs[2 * qa + 1] - ref[2 * qa + 1]
            let dbx = xs[2 * qb] - ref[2 * qb], dby = xs[2 * qb + 1] - ref[2 * qb + 1]
            let d1x = bx - ref[2 * nx], d1y = by - ref[2 * nx + 1]
            let c = pc[i]
            var hox = (c[2] - c[0]) + (dax - d0x) / (3 * DT), hoy = (c[3] - c[1]) + (day - d0y) / (3 * DT)
            var hix = (c[4] - c[6]) - (d1x - dbx) / (3 * DT), hiy = (c[5] - c[7]) - (d1y - dby) / (3 * DT)
            let chord = hypot(bx - ax, by - ay)
            let so = al + (1 - al) * min(1, 0.4 * chord / max(hypot(hox, hoy), 1e-9))
            let si = al + (1 - al) * min(1, 0.4 * chord / max(hypot(hix, hiy), 1e-9))
            hox *= so; hoy *= so; hix *= si; hiy *= si
            if i == 0 { path.move(to: CGPoint(x: CGFloat(ax), y: CGFloat(ay))) }
            path.addCurve(to: CGPoint(x: CGFloat(bx), y: CGFloat(by)),
                          control1: CGPoint(x: CGFloat(ax + hox), y: CGFloat(ay + hoy)),
                          control2: CGPoint(x: CGFloat(bx + hix), y: CGFloat(by + hiy)))
        }
        path.closeSubpath()
        self.path = path
        // 그라데이션: 표본점 P → X 최소제곱 아핀 맞춤, 너무 납작하면 특이값을 0.2배 이상으로.
        var bxv = [Double](repeating: 0, count: 3), byv = bxv
        for q in 0..<20 {
            let (x, y) = rb.x(fu[q], fv[q])
            let g0 = Double(fpx[q]), g1 = Double(fpy[q])
            bxv[0] += g0 * Double(x); bxv[1] += g1 * Double(x); bxv[2] += Double(x)
            byv[0] += g0 * Double(y); byv[1] += g1 * Double(y); byv[2] += Double(y)
        }
        var m = [Double](repeating: 0, count: 6)
        for r in 0..<3 {
            m[2 * r] = gInv[3 * r] * bxv[0] + gInv[3 * r + 1] * bxv[1] + gInv[3 * r + 2] * bxv[2]
            m[2 * r + 1] = gInv[3 * r] * byv[0] + gInv[3 * r + 1] * byv[1] + gInv[3 * r + 2] * byv[2]
        }
        clampSingular(&m, 0.2)
        let d = surface.def
        // 단위 선분 (0,0)→(1,0) → 로고 그라데이션 축 → 맞춘 아핀.
        let aff = CGAffineTransform(a: CGFloat(m[0]), b: CGFloat(m[1]), c: CGFloat(m[2]), d: CGFloat(m[3]), tx: CGFloat(m[4]), ty: CGFloat(m[5]))
        gradTransform = unitSegment(d.sx, d.sy, d.ex, d.ey).concatenating(aff)
        // 빛: 세그먼트 양 끝 단면의 명암 → 검정(그늘)·흰색(빛) 덮개 램프. 뒷면은 그늘을 더한다.
        let th0 = interp(u0, rb.ug, rb.th), th1 = interp(u1, rb.ug, rb.th)
        let l0 = interp(u0, rb.ug, rb.lit), l1 = interp(u1, rb.ug, rb.lit)
        shadeA0 = min(0.45, shadeOf(th0) + max(0, -l0)); shadeA1 = min(0.45, shadeOf(th1) + max(0, -l1))
        lightA0 = min(0.5, max(0, l0)); lightA1 = min(0.5, max(0, l1))
        landed = interp(0.5 * (u0 + u1), rb.ug, rb.alpha)
        if max(max(shadeA0, shadeA1), max(lightA0, lightA1)) > 0.003 {
            let e0 = rb.x(u0, 0), e1 = rb.x(u0, 1), e2 = rb.x(u1, 0), e3 = rb.x(u1, 1), e4 = rb.x(u0, 0.5), e5 = rb.x(u1, 0.5)
            let dx = (e1.0 - e0.0) + (e3.0 - e2.0), dy = (e1.1 - e0.1) + (e3.1 - e2.1)
            let nrm = hypot(dx, dy)
            let p0x = e4.0, p0y = e4.1
            var p1x: Float, p1y: Float
            if nrm > 1e-6 {
                let axX = dy / nrm, axY = -dx / nrm
                let dd = (e5.0 - p0x) * axX + (e5.1 - p0y) * axY
                p1x = p0x + axX * dd; p1y = p0y + axY * dd
            } else { p1x = e5.0; p1y = e5.1 }
            if hypot(p1x - p0x, p1y - p0y) < 0.05 { p1x = p0x + 0.05; p1y = p0y }
            shadeTransform = ramp(p0x, p0y, p1x, p1y, shadeA0, shadeA1)
            lightTransform = ramp(p0x, p0y, p1x, p1y, lightA0, lightA1)
        }
    }
}

/// 비틀림 각 → 검정 덮개 불투명도(뒷면 그늘). 앞면 0, 옆면 0.16, 뒷면 0.28.
private func shadeOf(_ theta: Float) -> Float {
    let c = cos(theta)
    return c >= 0 ? 0.16 * pow(1 - c, 1.4) : 0.16 + 0.12 * pow(-c, 0.8)
}

/// 단위 선분 (0,0)→(1,0) 을 (x0,y0)→(x1,y1) 로 보내는 닮음 변환.
private func unitSegment(_ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float) -> CGAffineTransform {
    let dx = CGFloat(x1 - x0), dy = CGFloat(y1 - y0)
    return CGAffineTransform(a: dx, b: dy, c: -dy, d: dx, tx: CGFloat(x0), ty: CGFloat(y0))
}

/// 램프 (0,0)→(1,0) 의 알파 0→1 을 축 p0→p1 위에서 a0→a1 로 선형 변하게 놓는 변환.
private func ramp(_ p0x: Float, _ p0y: Float, _ p1x: Float, _ p1y: Float, _ a0: Float, _ a1: Float) -> CGAffineTransform {
    var da = a1 - a0
    if abs(da) < 1e-3 { da = da >= 0 ? 1e-3 : -1e-3 }
    let q0 = -a0 / da, q1 = (1 - a0) / da
    return unitSegment(p0x + (p1x - p0x) * q0, p0y + (p1y - p0y) * q0, p0x + (p1x - p0x) * q1, p0y + (p1y - p0y) * q1)
}

private func invert3(_ a: [Double], _ out: inout [Double]) {
    let c00 = a[4] * a[8] - a[5] * a[7], c01 = a[5] * a[6] - a[3] * a[8], c02 = a[3] * a[7] - a[4] * a[6]
    let det = a[0] * c00 + a[1] * c01 + a[2] * c02
    out[0] = c00 / det; out[1] = (a[2] * a[7] - a[1] * a[8]) / det; out[2] = (a[1] * a[5] - a[2] * a[4]) / det
    out[3] = c01 / det; out[4] = (a[0] * a[8] - a[2] * a[6]) / det; out[5] = (a[2] * a[3] - a[0] * a[5]) / det
    out[6] = c02 / det; out[7] = (a[1] * a[6] - a[0] * a[7]) / det; out[8] = (a[0] * a[4] - a[1] * a[3]) / det
}

/// 2×2 선형부의 특이값을 최대값의 ratio 배 이상으로(부호 유지).
private func clampSingular(_ m: inout [Double], _ ratio: Double) {
    let a = m[0], b = m[1], c = m[2], d = m[3]
    let e = (a + d) / 2, f = (a - d) / 2, g = (c + b) / 2, h = (c - b) / 2
    let q = (e * e + h * h).squareRoot(), r = (f * f + g * g).squareRoot()
    let s1 = q + r
    var s2 = q - r
    if abs(s2) >= ratio * s1 { return }
    s2 = s2 >= 0 ? ratio * s1 : -ratio * s1
    let a1 = atan2(g, f), a2 = atan2(h, e)
    let th = (a2 - a1) / 2, ph = (a2 + a1) / 2
    let cp = cos(ph), sp = sin(ph), ct = cos(th), st = sin(th)
    m[0] = cp * s1 * ct - sp * s2 * st;  m[1] = -cp * s1 * st - sp * s2 * ct
    m[2] = sp * s1 * ct + cp * s2 * st;  m[3] = -sp * s1 * st + cp * s2 * ct
}

// MARK: - 4. 장면

final class EvoScene: @unchecked Sendable {
    private let surfaces: [Surface]
    private let ribbons: [Ribbon]
    private let rigs: [[SegmentRig]]
    private let silhouette: Path
    private let landTimes: [Float]

    /// 완성된 로고의 테두리(로고 단위).
    static let finalPaths: [(Path, Gradient, CGPoint, CGPoint)] = SURFACE_DEFS.map { d in
        let s = Surface(d)
        return (s.finalPath, s.gradient, CGPoint(x: CGFloat(d.sx), y: CGFloat(d.sy)), CGPoint(x: CGFloat(d.ex), y: CGFloat(d.ey)))
    }

    init() {
        surfaces = SURFACE_DEFS.map(Surface.init)
        let params = (0..<3).map { r in
            let f = RIBBON_FRAMES[r]
            return RibbonParam(f[0], f[1], f[2], f[3], surfaces: SURFACE_DEFS.indices.filter { SURFACE_DEFS[$0].ribbon == r }.map { parsePath(SURFACE_DEFS[$0].d) })
        }
        let ribbons = (0..<3).map { Ribbon(params[$0], CHOREO[$0]) }
        self.ribbons = ribbons
        rigs = surfaces.map { s in
            let k = SEGMENTS[s.def.ribbon]
            let cuts = (0...k).map { Float($0) / Float(k) }
            return segmentSurface(s.cubics, params[s.def.ribbon], cuts, OVERLAP).map { SegmentRig(s, ribbons[s.def.ribbon], $0) }
        }
        silhouette = cubPath(parsePath(SILHOUETTE))
        landTimes = CHOREO.map(\.landTime)
    }

    /// t 초의 로고를 (w × h) 화면에. 로고 높이 440 = 짧은 변의 50%, 중심 = (가로 중앙, 세로 40%).
    func draw(_ ctx: inout GraphicsContext, t: Float, size: CGSize) {
        let unit = 0.5 * min(size.width, size.height) / 440
        let b = CGFloat(breath(t))
        var c = ctx
        c.translateBy(x: size.width / 2, y: 0.4 * size.height)
        c.scaleBy(x: unit * b, y: unit * b)
        c.translateBy(x: -CGFloat(LOGO_CX), y: -CGFloat(LOGO_CY))
        for r in 0..<3 where t < landTimes[r] { ribbons[r].update(t) }
        for (i, s) in surfaces.enumerated() {
            if t >= landTimes[s.def.ribbon] {
                // 정지 후: 부록 벡터 그대로.
                c.fill(s.finalPath, with: .linearGradient(s.gradient, startPoint: CGPoint(x: CGFloat(s.def.sx), y: CGFloat(s.def.sy)),
                                                          endPoint: CGPoint(x: CGFloat(s.def.ex), y: CGFloat(s.def.ey))))
                continue
            }
            for sg in rigs[i] {
                sg.update()
                var op: Double = 1
                if s.def.id == "04_center_fold" {
                    // 접힘은 다리가 제자리에 닿는 만큼만 보인다.
                    let x = Double(min(max((sg.landed - 0.85) / 0.15, 0), 1))
                    op = x * x * (3 - 2 * x)
                    if op <= 0.001 { continue }
                }
                fillMapped(&c, sg.path, s.gradient, sg.gradTransform, opacity: op)
                if max(sg.shadeA0, sg.shadeA1) > 0.003 {
                    fillMapped(&c, sg.path, Gradient(colors: [.black.opacity(0), .black]), sg.shadeTransform, opacity: 1)
                }
                if max(sg.lightA0, sg.lightA1) > 0.003 {
                    fillMapped(&c, sg.path, Gradient(colors: [.white.opacity(0), .white]), sg.lightTransform, opacity: 1)
                }
            }
        }
        if let axis = highlight(t) {
            let peak = Color.white.opacity(Double(HL_PEAK))
            c.fill(silhouette, with: .linearGradient(Gradient(stops: [.init(color: .white.opacity(0), location: 0), .init(color: peak, location: 0.5), .init(color: .white.opacity(0), location: 1)]),
                                                    startPoint: axis.0, endPoint: axis.1))
        }
    }

    /// 단위 선분 그라데이션을 [m] 으로 옮겨 [path] 안에 — 비틀린 면에서는 등색선도 함께 기웁니다.
    private func fillMapped(_ ctx: inout GraphicsContext, _ path: Path, _ g: Gradient, _ m: CGAffineTransform, opacity: Double) {
        var l = ctx
        l.opacity = opacity
        l.clip(to: path)
        l.concatenate(m)
        l.fill(Path(CGRect(x: -40, y: -4000, width: 81, height: 8000)), with: .linearGradient(g, startPoint: .zero, endPoint: CGPoint(x: 1, y: 0)))
    }

    /// 완성 로고(정지 장면)를 그 자리에 — 첫 장이 같은 자리에 겹쳐 그립니다.
    static func drawFinal(_ ctx: inout GraphicsContext, size: CGSize, scale: CGFloat = 1) {
        let unit = 0.5 * min(size.width, size.height) / 440
        var c = ctx
        let bottom = 0.4 * size.height + CGFloat(440 - LOGO_CY) * unit
        c.translateBy(x: size.width / 2, y: bottom)
        c.scaleBy(x: scale, y: scale)
        c.translateBy(x: -size.width / 2, y: -bottom)
        c.translateBy(x: size.width / 2, y: 0.4 * size.height)
        c.scaleBy(x: unit, y: unit)
        c.translateBy(x: -CGFloat(LOGO_CX), y: -CGFloat(LOGO_CY))
        for (p, g, s, e) in finalPaths { c.fill(p, with: .linearGradient(g, startPoint: s, endPoint: e)) }
    }
}

/// 완성 후 한 번의 숨: 1 + A·sin²(πx).
private func breath(_ t: Float) -> Float {
    let x = min(max((t - BREATH_T0) / (BREATH_T1 - BREATH_T0), 0), 1)
    let s = sin(Float.pi * x)
    return 1 + BREATH_AMP * s * s
}

/// 대각선 하이라이트 띠의 그라데이션 축(로고 바깥 → 반대편 바깥). 구간 밖이면 nil.
private func highlight(_ t: Float) -> (CGPoint, CGPoint)? {
    let x0 = (t - HL_T0) / (HL_T1 - HL_T0)
    if x0 <= 0 || x0 >= 1 { return nil }
    let x = smootherstep(0, 1, x0)
    let n = hypot(HL_DIR_X, HL_DIR_Y), dx = HL_DIR_X / n, dy = HL_DIR_Y / n
    let cx = LOGO_CX + dx * (-HL_SPAN + 2 * HL_SPAN * x), cy = LOGO_CY + dy * (-HL_SPAN + 2 * HL_SPAN * x)
    return (CGPoint(x: CGFloat(cx - dx * HL_WIDTH), y: CGFloat(cy - dy * HL_WIDTH)), CGPoint(x: CGFloat(cx + dx * HL_WIDTH), y: CGFloat(cy + dy * HL_WIDTH)))
}

// MARK: - 5. 화면

/// 오프닝 부제.
let evoTagline = "Neo를 넘어 Evo로"
private let evoBackground = LinearGradient(colors: [Color(argb: 0xFF090A1C), Color(argb: 0xFF11102B), Color(argb: 0xFF0A0A18)], startPoint: .top, endPoint: .bottom)
let evoTitleGradient = LinearGradient(colors: [Color(argb: 0xFF38EAA8), Color(argb: 0xFF8C6BFF), Color(argb: 0xFFFF5A97)], startPoint: .leading, endPoint: .trailing)

/// 오프닝 끝의 글자 두 줄 — 로고 아래 끝 + 28pt. 오프닝과 첫 장이 같이 씁니다.
struct EvoTitle: View {
    var title: Color = .white.opacity(0.86)
    var tagline: Color = .white.opacity(0.66)
    var titleReveal: Double = 1
    var taglineReveal: Double = 1
    /// 영상 위에서 밝은 건물 위에서도 읽히게 옅은 그림자.
    var shadow: Bool = false
    /// 로고 아래 가장자리(화면 pt) — 영상 오프닝은 영상 속 로고 바로 아래에. 없으면 코드 오프닝의 로고 자리.
    var logoBottom: ((CGSize) -> CGFloat)? = nil

    var body: some View {
        GeometryReader { g in
            let unit = 0.5 * min(g.size.width, g.size.height) / 440
            VStack(spacing: 10) {
                (Text("HiHana ").font(.system(size: 38, weight: .light)).foregroundStyle(title)
                 + Text("Evo").font(.system(size: 38, weight: .semibold)).foregroundStyle(evoTitleGradient))
                    .kerning(-0.5)
                    .opacity(titleReveal).offset(y: (1 - titleReveal) * 16)
                Text(evoTagline).font(.system(size: 18, weight: .semibold)).foregroundStyle(tagline).kerning(-0.2)
                    .opacity(taglineReveal).offset(y: (1 - taglineReveal) * 16)
            }
            .frame(maxWidth: .infinity)
            .shadow(color: .black.opacity(shadow ? 0.45 : 0), radius: 7, y: 2)
            .offset(y: min((logoBottom?(g.size) ?? 0.4 * g.size.height + CGFloat(440 - LOGO_CY) * unit) + 28, g.size.height - 110))
        }
    }
}

/// 오프닝 입구 — 영상(OpeningVideo)을 스트리밍합니다. OpeningVideo.startSeconds 안에 재생이 시작되지 않으면 예전 코드 애니메이션.
struct EvoOpeningAuto: View {
    let onFinish: () -> Void

    var body: some View { EvoOpening(onFinish: onFinish, video: OpeningVideo.url) }
}

/// 첫 실행 투어 맨 앞의 오프닝. 0.6초 뒤부터 탭하면 건너뜁니다. 끝에서 바탕이 걷히고 투어 첫 장(같은 자리의 로고·글자)이 이어받습니다.
struct EvoOpening: View {
    let onFinish: () -> Void
    /// 오프닝 영상(OpeningVideo) 주소 — 있으면 스트리밍해 보고, 영상이 끝난 뒤 그 속 로고 자리에서 이어받습니다.
    var video: URL? = nil
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// 영상: -1 아직 모름, 0 재생 중, 1 끝남(앱 로고로 이어받음), 2 영상 없음·실패(처음부터 코드 애니메이션).
    @State private var vPhase = -1
    @State private var vSize: CGSize = .zero
    @State private var vStarted = false
    @State private var scene: EvoScene?
    @State private var start: Date?
    @State private var finished = false
    @State private var lastT: Float = 0
    private let shownAt = Date()
    private let haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        TimelineView(.animation(minimumInterval: nil, paused: scene == nil || finished || vPhase == 0)) { tl in
            let t = currentTime(tl.date)
            let back = Double(1 - smootherstep(0.05, 0.8, handoff(t)))
            ZStack {
                evoBackground.ignoresSafeArea().opacity(back)
                if let video, vPhase == 0 || vPhase == 1 {
                    OpeningVideoLayer(url: video, onReady: { vSize = $0 }, onStart: { vStarted = true }, onEnd: { videoEnded() }, onError: { if vStarted { videoEnded() } else { videoFailed() } })
                        .ignoresSafeArea()
                        .opacity(back)
                }
                Canvas { ctx, size in
                    // 영상 오프닝은 영상 마지막 장면의 로고를 그대로 둡니다(앱이 로고를 겹쳐 그리면 모양·자리가 조금씩 달라 두 개로 보였음).
                    guard let scene, handoff(t) <= 0, vPhase == 2 else { return }
                    scene.draw(&ctx, t: t, size: size)
                }
                .ignoresSafeArea()
                if video != nil, vPhase == 0, !vStarted {
                    // 영상이 시작되기 전 — 로딩 표시.
                    ProgressView().tint(.white.opacity(0.8)).controlSize(.large)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                EvoTitle(titleReveal: reveal(t, TEXT_T0), taglineReveal: reveal(t, TEXT_T0 + TEXT_STAGGER), shadow: vPhase == 1,
                         logoBottom: vPhase == 1 && vSize.width > 0 ? { OpeningVideo.logoRect(size: $0, video: vSize).maxY } : nil)
                    .ignoresSafeArea()
                    .opacity(handoff(t) > 0 || vPhase == 0 ? 0 : 1)
            }
            .onChange(of: t) { old, new in
                if vPhase == 2 { for m in LAND_MARKS where old < m && new >= m { haptic.impactOccurred(intensity: 0.6) } }
                if new >= EVO_END { finish() }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if Date().timeIntervalSince(shownAt) >= 0.6 { finish() } }
        .task {
            // 경로 파싱·분할·표 계산은 한 번만, 메인 스레드 밖에서.
            let s = await Task.detached(priority: .userInitiated) { EvoScene() }.value
            scene = s
            haptic.prepare()
            if reduceMotion {
                vPhase = 2
                start = Date().addingTimeInterval(-TimeInterval(T_STILL))
                try? await Task.sleep(for: .seconds(1.4))
                finish()
            } else if video != nil {
                vPhase = 0                                  // 영상이 끝나면 videoEnded() 가 시계를 켭니다
                // 정한 시간 안에 화면이 움직이기 시작하지 않으면(인터넷 없음·느림) 예전 코드 애니메이션으로.
                try? await Task.sleep(for: .seconds(OpeningVideo.startSeconds))
                if !vStarted { videoFailed() }
            } else {
                vPhase = 2
                start = Date()
            }
        }
    }

    /// 영상이 끝난 순간 — 글자가 0.2초 뒤 떠오르도록 시계를 맞춥니다(로고는 영상 마지막 장면 그대로).
    /// 재생 전 실패·시간 초과 — 처음부터 예전 코드 애니메이션(2026-11-25 사용자 요청: 안내 문구 대신).
    private func videoFailed() {
        guard vPhase == 0, !vStarted else { return }
        vPhase = 2
        start = Date()
    }

    private func videoEnded() {
        guard vPhase == 0 else { return }
        vPhase = 1
        start = Date().addingTimeInterval(-TimeInterval(TEXT_T0 - 0.2))
    }

    private func currentTime(_ now: Date) -> Float {
        guard let start else { return 0 }
        if reduceMotion { return T_STILL }
        return min(Float(now.timeIntervalSince(start)), EVO_END)
    }

    private func handoff(_ t: Float) -> Float {
        let x = min(max((t - EVO_HANDOFF_T0) / EVO_HANDOFF_DUR, 0), 1)
        return x * x * (3 - 2 * x)
    }

    private func reveal(_ t: Float, _ t0: Float) -> Double {
        let x = min(max((t - t0) / TEXT_DUR, 0), 1)
        return Double(1 - (1 - x) * (1 - x) * (1 - x))
    }

    private func finish() {
        guard !finished else { return }
        finished = true
        onFinish()
    }
}
