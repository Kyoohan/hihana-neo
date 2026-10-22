"""모션 엔진: 시간 t 에서 각 갈래 재료점 (u, v) 의 화면 위치 X(u, v, t) 와
세그먼트별 그리기 목록(경로·그라데이션·음영)을 만든다.

수식 요약 (단위: 로고 좌표)
---------------------------
섹션 u 의 출발 시각  ts(u) = T0 + κ·w(u)      (w: 착지 순서 — 머리부터 / 가운데부터)
섹션 경과 시간       s = t - ts(u)
스프링 진행도        r(s) = 1 - e^(-ζωs)(cos ωd s + ζ/√(1-ζ²) sin ωd s)   (ζ≈0.87 → 지나침 0.4%)
접근 오프셋          O = B(r)   (B: 화면 밖 F 에서 0 으로 오는 3차 베지어, r>1 이면 살짝 지나침)
형태 가중치          α = smootherstep(sa0, sa1, s)          (자리에 닿는 섹션부터 0 → 1)
비행 모양            F = Sm(u) + flutter·N + (v-½)·wf(u)·(cosθ·N + k·sinθ·T)
최종 위치            X = O + (1-α)·F + α·P(u, v)
착지 후 O = 0, α = 1 → X = P(u, v) = 원본 로고 좌표 (정확히 항등).
"""
import math

import numpy as np

import geom

FPS = 60
ARTBOARD = (1080, 2340)
LOGO_CENTER = np.array([204.0, 217.0])
SCREEN_CENTER = np.array([540.0, 936.0])
UNIT = 540.0 / 440.0          # 로고 높이 440 → 짧은 변 1080 의 50% = 540px


def smootherstep(e0, e1, x):
    t = np.clip((np.asarray(x, float) - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * t * (t * (t * 6 - 15) + 10)


def spring(s, omega, zeta, s_w0, s_w1):
    """0→1 감쇠 스프링 (정지 상태에서 출발). s_w0~s_w1 에서 잔차를 부드럽게 0 으로 접어 유한 시간에 정확히 멈춘다."""
    s = np.maximum(np.asarray(s, float), 0.0)
    wd = omega * math.sqrt(1 - zeta * zeta)
    e = np.exp(-zeta * omega * s)
    r = 1 - e * (np.cos(wd * s) + zeta / math.sqrt(1 - zeta * zeta) * np.sin(wd * s))
    return 1 - (1 - r) * (1 - smootherstep(s_w0, s_w1, s))


def cubic(p0, p1, p2, p3, r):
    r = np.asarray(r, float)[..., None]
    mr = 1 - r
    return mr ** 3 * p0 + 3 * mr * mr * r * p1 + 3 * mr * r * r * p2 + r ** 3 * p3


class Ribbon:
    """갈래 하나의 운동."""

    def __init__(self, name, param, cfg, nu=257):
        self.name, self.p, self.c = name, param, cfg
        self.ug = np.linspace(0, 1, nu)
        self.Sm = param.spine_smooth(self.ug)
        F = np.array(cfg["F"], float)
        din = np.array(cfg["din"], float)
        din /= np.linalg.norm(din)
        self.bz = (F, F + np.array(cfg["h0"], float), -din * cfg["h1"], np.zeros(2))
        u = self.ug
        tp = cfg.get("taper", 0.16)
        self.wf = cfg["wf"] * np.clip(np.minimum(u, 1 - u) / tp, 0.0, 1.0) ** 0.75
        self.wf = np.maximum(self.wf, cfg["wf"] * 0.08)

    # 착지 순서 w(u) ∈ [0, 1]
    def order(self, u):
        o = self.c["order"]
        if o == "head":
            return u
        if o == "tail":
            return 1 - u
        uc = self.c.get("uc", 0.5)
        return np.abs(u - uc) / max(uc, 1 - uc)

    def section_time(self, u, t):
        c = self.c
        return t - (c["T0"] + c["kappa"] * self.order(u))

    def land_time(self):
        """마지막 섹션까지 완전히 멈추는 시각 (= 스프링 창 끝)."""
        c = self.c
        return c["T0"] + c["kappa"] + c["s_w1"]

    def fields(self, t):
        c, u = self.c, self.ug
        s = self.section_time(u, t)
        r = spring(s, c["omega"], c["zeta"], c["s_w0"], c["s_w1"])
        O = cubic(*self.bz, r)
        alpha = smootherstep(c["sa0"], c["sa1"], s)
        env_f = 1 - smootherstep(c["sf0"], c["sf1"], s)
        env_t = 1 - smootherstep(c["st0"], c["st1"], s)
        C = self.Sm + O
        T = np.gradient(C, u, axis=0)
        T /= np.maximum(np.linalg.norm(T, axis=1, keepdims=True), 1e-9)
        N = np.stack([T[:, 1], -T[:, 0]], axis=1)
        fl = np.zeros_like(u)
        for A, k, f, ph in c["flutter"]:
            fl += A * np.sin(2 * math.pi * (k * u - f * t) + ph)
        fl *= env_f
        th = np.zeros_like(u)
        for A, k, f, ph in c["twist"]:
            th += A * np.sin(2 * math.pi * (k * u - f * t) + ph)
        th = th * env_t
        return dict(s=s, r=r, O=O, alpha=alpha, T=T, N=N, fl=fl, th=th, env_f=env_f)

    def X(self, F, u, v):
        """임의의 재료점 (u, v) 위치 (로고 단위)."""
        ug = self.ug
        u = np.asarray(u, float)
        v = np.asarray(v, float)
        ip = lambda arr: np.interp(u, ug, arr)
        O = np.stack([ip(F["O"][:, 0]), ip(F["O"][:, 1])], -1)
        T = np.stack([ip(F["T"][:, 0]), ip(F["T"][:, 1])], -1)
        T /= np.maximum(np.linalg.norm(T, axis=-1, keepdims=True), 1e-9)
        N = np.stack([T[..., 1], -T[..., 0]], -1)
        al = ip(F["alpha"])[..., None]
        fl = ip(F["fl"])[..., None]
        th = ip(F["th"])[..., None]
        wf = ip(self.wf)[..., None]
        Sm = self.p.spine_smooth(u)
        k = self.c.get("kp", 0.22)
        # 끝 단면이 아주 짧은 곳은 v 가 [0,1] 밖으로 크게 벗어나므로 비행 단면에서는 잘라 쓴다
        vc = np.clip(v, -0.05, 1.05)[..., None]
        flight = Sm + fl * N + (vc - 0.5) * wf * (np.cos(th) * N + k * np.sin(th) * T)
        return O + (1 - al) * flight + al * self.p.P(u, v)


# ---------------------------------------------------------------------------
# 세그먼트 준비 (한 번만) + 프레임별 그리기 목록
# ---------------------------------------------------------------------------

DT = 0.02  # 핸들 보정용 곡선 매개변수 간격


class SegmentRig:
    def __init__(self, surface, ribbon, seg):
        self.sid = surface["id"]
        self.k = seg["k"]
        self.u0, self.u1 = seg["u0"], seg["u1"]
        pc = np.array(seg["pieces"])                    # (n, 4, 2)
        self.pieces = pc
        self.anch = pc[:, 0]                             # 각 조각의 시작점 (닫힌 루프)
        self.hout = pc[:, 1] - pc[:, 0]
        self.hin = pc[:, 2] - pc[:, 3]                   # 다음 앵커 기준
        p = ribbon.p
        A = self.anch
        # 핸들 보정용: 각 조각의 시작 직후 / 끝 직전 점 (곡선을 따라가는 유한차분 → 경계 꺾임에 둔감)
        qa = np.array([geom.bez(c, DT) for c in pc])
        qb = np.array([geom.bez(c, 1 - DT) for c in pc])
        self.pieces_qa, self.pieces_qb = qa, qb
        allp = np.concatenate([A, qa, qb])
        self.uv = p.uv(allp)
        n = len(A)
        self.n = n
        # 그라데이션 맞춤용 표본점
        uu, vv = np.meshgrid(np.linspace(self.u0, self.u1, 5), np.linspace(0.0, 1.0, 4))
        self.fit_uv = (uu.ravel(), vv.ravel())
        self.fit_P = p.P(*self.fit_uv)
        self.start = surface["start"]
        self.end = surface["end"]
        self.stops = surface["stops"]

    def frame(self, ribbon, F):
        u, v = self.uv
        X = ribbon.X(F, u, v)
        n = self.n
        A = X[:n]
        # 변위 D = X - p 를 곡선 방향으로 미분해 원래 핸들에 더한다 (착지 후 D = 0 → 원본 핸들 그대로)
        D0 = A - self.anch
        Da = X[n:2 * n] - self.pieces_qa
        Db = X[2 * n:3 * n] - self.pieces_qb
        nxt = np.roll(np.arange(n), -1)
        ho = self.hout + (Da - D0) / (3 * DT)
        hi = self.hin - (D0[nxt] - Db) / (3 * DT)
        A1 = A[nxt]
        cub = np.stack([A, A + ho, A1 + hi, A1], axis=1)
        # 그라데이션: 최소제곱 아핀 맞춤 → 시작·끝점 매핑
        Xf = ribbon.X(F, *self.fit_uv)
        G = np.column_stack([self.fit_P, np.ones(len(self.fit_P))])
        M, *_ = np.linalg.lstsq(G, Xf, rcond=None)     # (3, 2)
        Mlin = M[:2]
        U, Sv, Vt = np.linalg.svd(Mlin)
        Sv = np.maximum(Sv, Sv.max() * 0.2)
        Mlin = (U * Sv) @ Vt
        M = np.vstack([Mlin, M[2]])
        gs = np.append(self.start, 1) @ M
        ge = np.append(self.end, 1) @ M
        # 비틀림 음영: 세그먼트 양 끝 단면의 cosθ → 검정 덮개 불투명도
        th0 = np.interp(self.u0, ribbon.ug, F["th"])
        th1 = np.interp(self.u1, ribbon.ug, F["th"])
        c0 = ribbon.X(F, np.array([self.u0, self.u1]), np.array([0.5, 0.5]))
        al = np.interp(0.5 * (self.u0 + self.u1), ribbon.ug, F["alpha"])
        return dict(sid=self.sid, k=self.k, cubics=cub, grad=(gs, ge), stops=self.stops,
                    shade=(shade_of(th0), shade_of(th1)), shade_pts=(c0[0], c0[1]), alpha=al)


def shade_of(theta):
    """비틀림 각 → 검정 덮개 불투명도. 앞면 0, 옆면 0.16, 뒷면 0.42."""
    c = math.cos(theta)
    if c >= 0:
        return 0.16 * (1 - c) ** 1.4
    return 0.16 + 0.26 * (-c) ** 0.8


class Scene:
    def __init__(self, cfg, K=None, overlap=0.004):
        self.cfg = cfg
        self.g, self.surf = geom.load()
        self.ribbons = {}
        self.params = {}
        for r in ("left", "center", "right"):
            ss = [s for s in self.surf.values() if s["ribbon"] == r]
            self.params[r] = geom.RibbonParam(r, ss)
            self.ribbons[r] = Ribbon(r, self.params[r], cfg["ribbons"][r])
        K = K or cfg.get("K", {"left": 12, "center": 10, "right": 12})
        self.rigs = []
        for sid in geom.LAYER_ORDER:
            s = self.surf[sid]
            r = s["ribbon"]
            cuts = np.linspace(0, 1, K[r] + 1)
            segs = geom.segment_surface(s, self.params[r], cuts, overlap)
            self.rigs.append((sid, r, [SegmentRig(s, self.ribbons[r], sg) for sg in segs]))
        self.silhouette = geom.parse_path(self.g["silhouette"])

    def frame(self, t):
        fields = {r: rb.fields(t) for r, rb in self.ribbons.items()}
        layers = []
        for sid, r, rigs in self.rigs:
            layers.append((sid, [rg.frame(self.ribbons[r], fields[r]) for rg in rigs]))
        return dict(t=t, layers=layers, breath=self.breath(t), hl=self.highlight(t))

    # 완성 후: 아주 작은 숨쉬기 + 대각선 하이라이트
    def breath(self, t):
        b = self.cfg["breath"]
        x = np.clip((t - b["t0"]) / (b["t1"] - b["t0"]), 0, 1)
        return 1 + b["amp"] * math.sin(math.pi * x) ** 2

    def highlight(self, t):
        h = self.cfg["highlight"]
        x = (t - h["t0"]) / (h["t1"] - h["t0"])
        if x <= 0 or x >= 1:
            return None
        x = float(smootherstep(0, 1, x))
        d = np.array(h["dir"], float)
        d /= np.linalg.norm(d)
        # 띠 중심이 로고 바깥(-) 에서 바깥(+) 으로 지나간다
        span = h["span"]
        c = LOGO_CENTER + d * (-span + 2 * span * x)
        w = h["width"]
        return dict(s=c - d * w, e=c + d * w, peak=h["peak"])

    def land_times(self):
        return {r: rb.land_time() for r, rb in self.ribbons.items()}
