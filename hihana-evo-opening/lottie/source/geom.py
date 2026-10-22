"""로고 기하: 경로 파싱, 갈래(리본)별 재료 좌표 (u, v), 세그먼트 분할.

핵심 아이디어
-------------
각 갈래(왼쪽·가운데·오른쪽)를 '띠'로 보고 재료 좌표 (u, v) 를 붙인다.
  - u: 띠의 길이 방향 (0 → 1)
  - v: 띠의 폭 방향 (0 → 1)
로고 좌표 p 는 사선 좌표계 p = a·L + b·S 로 분해한다.
  - L: 띠의 길이축 (왼쪽·오른쪽 = 세로, 가운데 = 다리 방향)
  - S: 단면(슬라이스) 방향 — 로고의 끝 컷과 같은 사선이라 형태가 잡히는 경계선이 디자인 선과 나란하다.
u = (a - a0)/(a1 - a0),  v = (b - bl(u)) / (br(u) - bl(u))
P(u, v) = a(u)·L + (bl + v·(br - bl))·S  는  p → (u, v) → P 가 정확히 항등이 되도록 만든 역함수다.
따라서 '착지(형태 완성)' 상태에서 워프는 정확히 항등이고, 마지막 프레임은 원본 벡터와 같다.
"""
import json
import os
import re

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
GEOMETRY = os.path.join(HERE, "assets", "geometry.json")

LAYER_ORDER = ["01_left_tail", "02_right_back", "03_center_bridge",
               "04_center_fold", "05_left_front", "06_right_front"]
RIBBON_OF = {"01_left_tail": "left", "05_left_front": "left",
             "02_right_back": "right", "06_right_front": "right",
             "03_center_bridge": "center", "04_center_fold": "center"}

# 띠 좌표계 (길이축 L, 단면 방향 S). cross(L, S) < 0 이 되도록 방향을 맞춘다
# (비행 중 단면 방향 N = (T.y, -T.x) 와 같은 손잡이 → 착지할 때 뒤집히지 않는다).
# 단면이 띠 '중간'의 외곽선과 거의 나란하면 (u, v) 가 급변해 비행 중 구멍이 생긴다.
#   왼쪽·오른쪽: 사선 접힘선(기울기 ≈ -0.7)과 겹치지 않도록 수평 단면.
#   가운데 다리: 양 끝 컷과 나란한 사선 단면 (나란한 외곽선이 끝에만 있다).
RIBBON_FRAME = {
    "left":   ((0.0, 1.0), (1.0, 0.0)),
    "right":  ((0.0, 1.0), (1.0, 0.0)),
    "center": ((179.0, 46.0), (1.0, -0.69)),
}


def _unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def parse_path(d):
    """M/L/C/Z 절대 좌표 SVG 경로 → 닫힌 3차 베지어 조각 리스트 [(4,2) ndarray]."""
    toks = re.findall(r"[MLCZ]|-?\d*\.?\d+(?:e-?\d+)?", d)
    i, segs, cur, start = 0, [], None, None

    def num():
        nonlocal i
        x, y = float(toks[i]), float(toks[i + 1])
        i += 2
        return np.array([x, y])

    while i < len(toks):
        c = toks[i]
        i += 1
        if c == "M":
            cur = num()
            start = cur.copy()
        elif c == "L":
            p = num()
            segs.append(line_cubic(cur, p))
            cur = p
        elif c == "C":
            p1, p2, p3 = num(), num(), num()
            segs.append(np.array([cur, p1, p2, p3]))
            cur = p3
        elif c == "Z":
            if np.linalg.norm(cur - start) > 1e-9:
                segs.append(line_cubic(cur, start))
            cur = start
    return segs


def line_cubic(p0, p3):
    p0, p3 = np.asarray(p0, float), np.asarray(p3, float)
    return np.array([p0, p0 + (p3 - p0) / 3, p0 + 2 * (p3 - p0) / 3, p3])


def bez(c, t):
    t = np.asarray(t, float)[..., None]
    mt = 1 - t
    return mt ** 3 * c[0] + 3 * mt * mt * t * c[1] + 3 * mt * t * t * c[2] + t ** 3 * c[3]


def split_bez(c, t):
    """de Casteljau 분할 → (앞, 뒤)."""
    p01 = c[0] + (c[1] - c[0]) * t
    p12 = c[1] + (c[2] - c[1]) * t
    p23 = c[2] + (c[3] - c[2]) * t
    p012 = p01 + (p12 - p01) * t
    p123 = p12 + (p23 - p12) * t
    p = p012 + (p123 - p012) * t
    return np.array([c[0], p01, p012, p]), np.array([p, p123, p23, c[3]])


def is_line(c):
    d = c[3] - c[0]
    n = np.linalg.norm(d)
    if n < 1e-9:
        return True
    nn = np.array([-d[1], d[0]]) / n
    return abs(np.dot(c[1] - c[0], nn)) < 1e-6 and abs(np.dot(c[2] - c[0], nn)) < 1e-6


def load():
    g = json.load(open(GEOMETRY))
    surf = {}
    for s in g["surfaces"]:
        surf[s["id"]] = dict(
            id=s["id"], ribbon=s["ribbon"], d=s["d"], cubics=parse_path(s["d"]),
            start=np.array(s["start"], float), end=np.array(s["end"], float),
            stops=[(float(o), c) for o, c in s["stops"]])
    return g, surf


class RibbonParam:
    """갈래 하나의 재료 좌표 (u, v) ↔ 로고 좌표 변환."""

    def __init__(self, name, surfaces, n=1025):
        self.name = name
        L, S = (_unit(v) for v in RIBBON_FRAME[name])
        self.L, self.S = L, S
        self.M = np.column_stack([L, S])
        self.Minv = np.linalg.inv(self.M)
        # 조밀한 외곽 폴리라인
        polys = []
        for s in surfaces:
            pts = np.concatenate([bez(c, np.linspace(0, 1, 48, endpoint=False)) for c in s["cubics"]])
            polys.append(np.vstack([pts, pts[:1]]))
        ab_all = [p @ self.Minv.T for p in polys]
        a_all = np.concatenate([ab[:, 0] for ab in ab_all])
        self.a0, self.a1 = float(a_all.min()), float(a_all.max())
        self.ug = np.linspace(0.0, 1.0, n)
        ag = self.a0 + self.ug * (self.a1 - self.a0)
        bl = np.full(n, np.nan)
        br = np.full(n, np.nan)
        for ab in ab_all:
            a, b = ab[:, 0], ab[:, 1]
            a_p, a_q, b_p, b_q = a[:-1], a[1:], b[:-1], b[1:]
            for k, lev in enumerate(ag):
                m = (a_p - lev) * (a_q - lev) <= 0
                m &= np.abs(a_q - a_p) > 1e-12
                if not m.any():
                    continue
                tt = (lev - a_p[m]) / (a_q[m] - a_p[m])
                bb = b_p[m] + tt * (b_q[m] - b_p[m])
                lo, hi = bb.min(), bb.max()
                bl[k] = lo if np.isnan(bl[k]) else min(bl[k], lo)
                br[k] = hi if np.isnan(br[k]) else max(br[k], hi)
        # 양 끝(단면이 점으로 줄어드는 곳)은 이웃 값으로 채우고 최소 폭을 보장
        good = ~np.isnan(bl)
        bl = np.interp(self.ug, self.ug[good], bl[good])
        br = np.interp(self.ug, self.ug[good], br[good])
        mid = 0.5 * (bl + br)
        half = np.maximum(0.5 * (br - bl), 0.6)
        self.bl, self.br = mid - half, mid + half
        # 비행용 매끈한 척추: b 중앙값을 직선으로 근사 (꺾임 없는 리본 중심선 — 비행 모양은 경로가 정한다)
        sel = (self.ug > 0.04) & (self.ug < 0.96)
        coef = np.polyfit(self.ug[sel], mid[sel], 1)
        self.bm_smooth = np.polyval(coef, self.ug)
        self.length = self.a1 - self.a0

    # --- 변환 ---
    def uv(self, xy):
        xy = np.asarray(xy, float)
        ab = xy @ self.Minv.T
        u = (ab[..., 0] - self.a0) / (self.a1 - self.a0)
        bl = np.interp(u, self.ug, self.bl)
        br = np.interp(u, self.ug, self.br)
        v = (ab[..., 1] - bl) / (br - bl)
        return u, v

    def P(self, u, v):
        u = np.asarray(u, float)
        v = np.asarray(v, float)
        a = self.a0 + u * (self.a1 - self.a0)
        bl = np.interp(u, self.ug, self.bl)
        br = np.interp(u, self.ug, self.br)
        b = bl + v * (br - bl)
        return a[..., None] * self.L + b[..., None] * self.S

    def width(self, u):
        """착지 단면 폭 (로고 단위, S 방향)."""
        return np.interp(u, self.ug, self.br - self.bl)

    def spine_smooth(self, u):
        u = np.asarray(u, float)
        a = self.a0 + u * (self.a1 - self.a0)
        b = np.interp(u, self.ug, self.bm_smooth)
        return a[..., None] * self.L + b[..., None] * self.S

    def u_of_xy_linear(self, xy):
        """u 는 로고 좌표의 1차 함수 → (계수 gx, gy, c): u = gx*x + gy*y + c."""
        g = self.Minv[0] / (self.a1 - self.a0)
        return g[0], g[1], -self.a0 / (self.a1 - self.a0)


# ---------------------------------------------------------------------------
# 세그먼트: 면을 u 띠로 자른 조각 (조각마다 그라데이션·음영을 따로 매핑)
# ---------------------------------------------------------------------------

def _u_roots(c, gx, gy, g0, level):
    """베지어 c 위에서 u(p(t)) = level 인 t (0<t<1). 64칸 부호 변화 + 이분법 40회
    (Compose 구현과 같은 알고리즘이라 두 쪽의 조각 구조가 같다)."""
    def f(t):
        p = bez(c, t)
        return p[..., 0] * gx + p[..., 1] * gy + g0 - level
    ts = np.linspace(0, 1, 65)
    fs = f(ts)
    out = []
    for k in range(64):
        t0, t1, f0, f1 = ts[k], ts[k + 1], fs[k], fs[k + 1]
        # 반열린 구간 [t0, t1): 표본점이 정확히 0 이어도 한 번만, float/double 어느 쪽이든 같은 근
        if (f0 <= 0 < f1) or (f0 >= 0 > f1):
            lo, hi, flo = t0, t1, f0
            for _ in range(40):
                mid = 0.5 * (lo + hi)
                fm = f(mid)
                if flo * fm <= 0:
                    hi = mid
                else:
                    lo, flo = mid, fm
            r = 0.5 * (lo + hi)
            if 1e-6 < r < 1 - 1e-6:
                out.append(r)
    return out


def subdivide(cubics, param, levels, max_len=36.0):
    """외곽 베지어를 u 경계(levels)와 최대 길이로 잘게 나눈다 (원래 곡선과 정확히 같은 모양)."""
    gx, gy, g0 = param.u_of_xy_linear(None)
    levels = [lv for lv in levels if 0 < lv < 1]      # 띠 양 끝(u=0,1)은 외곽선이 접할 뿐이라 나누지 않는다
    out = []
    for c in cubics:
        ts = set()
        for lev in levels:
            for r in _u_roots(c, gx, gy, g0, lev):
                ts.add(round(r, 12))
        ts = sorted(ts)
        pieces = []
        rest, t_prev = c, 0.0
        for t in ts:
            tl = (t - t_prev) / (1 - t_prev)
            a, rest = split_bez(rest, tl)
            pieces.append(a)
            t_prev = t
        pieces.append(rest)
        for p in pieces:
            # 길이 제한
            ln = np.sum(np.linalg.norm(np.diff(bez(p, np.linspace(0, 1, 9)), axis=0), axis=1))
            n = max(1, int(np.ceil(ln / max_len)))
            r = p
            for k in range(n, 1, -1):
                a, r = split_bez(r, 1.0 / k)
                out.append(a)
            out.append(r)
    return [p for p in out if np.linalg.norm(p[3] - p[0]) > 1e-7 or np.linalg.norm(p[1] - p[0]) > 1e-7]


def _clip_half(pieces, param, level, keep_below):
    """u <= level (keep_below) 또는 u >= level 반평면으로 자르기 (Sutherland–Hodgman).
    조각은 미리 level 에서 나뉘어 있으므로 각 조각은 한쪽에만 있다. 밖으로 나간 구간은
    출구점→입구점 직선(같은 단면선 위)으로 대체한다."""
    gx, gy, g0 = param.u_of_xy_linear(None)

    def inside(p):
        m = bez(p, 0.5)
        u = m[0] * gx + m[1] * gy + g0
        return u <= level + 1e-9 if keep_below else u >= level - 1e-9

    flags = [inside(p) for p in pieces]
    if all(flags):
        return list(pieces)
    if not any(flags):
        return []
    n = len(pieces)
    k0 = flags.index(True)
    # 안쪽 조각에서 시작하도록 회전
    order = [(k0 + i) % n for i in range(n)]
    out = []
    exit_pt = None
    for idx in order:
        p = pieces[idx]
        if flags[idx]:
            if exit_pt is not None:
                if np.linalg.norm(p[0] - exit_pt) > 1e-7:
                    out.append(line_cubic(exit_pt, p[0]))
                exit_pt = None
            out.append(p)
        else:
            if exit_pt is None:
                exit_pt = p[0]
    if exit_pt is not None and np.linalg.norm(out[0][0] - exit_pt) > 1e-7:
        out.append(line_cubic(exit_pt, out[0][0]))
    return out


def segment_surface(surface, param, cuts, overlap, max_len=36.0):
    """면을 u 띠 [cut_k - overlap, cut_{k+1}] 로 나눈 세그먼트 리스트.
    세그먼트마다 자기 경계에서만 외곽선을 나눠 정점 수를 줄인다. 빈 띠는 건너뛴다."""
    segs = []
    for k in range(len(cuts) - 1):
        lo = cuts[k] - (overlap if k > 0 else 0.0)
        hi = cuts[k + 1]
        lo_c = lo if k > 0 else -1.0
        hi_c = hi if k < len(cuts) - 2 else 2.0
        fine = subdivide(surface["cubics"], param, [lo, hi], max_len)
        p = _clip_half(fine, param, hi_c, True)
        p = _clip_half(p, param, lo_c, False) if p else []
        if not p:
            continue
        area = polygon_area(np.concatenate([bez(c, np.linspace(0, 1, 8, endpoint=False)) for c in p]))
        if abs(area) < 0.5:
            continue
        segs.append(dict(k=k, u0=cuts[k], u1=cuts[k + 1], pieces=p))
    return segs


def polygon_area(pts):
    x, y = pts[:, 0], pts[:, 1]
    return 0.5 * np.sum(x * np.roll(y, -1) - np.roll(x, -1) * y)


def flatten(pieces, n=12):
    return np.concatenate([bez(c, np.linspace(0, 1, n, endpoint=False)) for c in pieces])
