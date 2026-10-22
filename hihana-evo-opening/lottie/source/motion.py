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

    def __init__(self, name, param, cfg, nu=257, time_scale=1.0):
        self.name, self.p, self.c = name, param, cfg
        self.ts = time_scale             # 실제 시각 = 운동 시간 × ts (ts > 1 이면 그만큼 느리게)
        self.ug = np.linspace(0, 1, nu)
        self.Sm = param.spine_smooth(self.ug)
        F = np.array(cfg["F"], float)
        din = np.array(cfg["din"], float)
        din /= np.linalg.norm(din)
        self.bz = (F, F + np.array(cfg["h0"], float), -din * cfg["h1"], np.zeros(2))
        u = self.ug
        tp = cfg.get("taper", 0.16)
        self.wf = cfg["wf"] * np.clip(np.minimum(u, 1 - u) / tp, 0.0, 1.0) ** 0.75
        self.wf = np.maximum(self.wf, cfg["wf"] * 0.02)

    # 착지 순서 w(u) ∈ [0, 1]
    def order(self, u):
        o = self.c["order"]
        if o == "head":
            return u
        if o == "tail":
            return 1 - u
        # 가운데부터: 매끈한 |d| (꼭짓점에서 리본이 V 로 꺾이지 않게)
        uc = self.c.get("uc", 0.5)
        e = self.c.get("eps", 0.2)
        d = (u - uc) / max(uc, 1 - uc)
        return (np.sqrt(d * d + e * e) - e) / (math.sqrt(1 + e * e) - e)

    def section_time(self, u, t):
        """t 는 운동 시간."""
        c = self.c
        return t - (c["T0"] + c["kappa"] * self.order(u))

    def land_time(self):
        """마지막 섹션까지 완전히 멈추는 실제 시각 (= 스프링 창 끝)."""
        c = self.c
        return (c["T0"] + c["kappa"] + c["s_w1"]) * self.ts

    def fields(self, t):
        """t 는 실제 시각(초). 아래 수식은 모두 운동 시간 t / ts 로 계산한다."""
        t = t / self.ts
        c, u = self.c, self.ug
        w = self.order(u)
        s = self.section_time(u, t)
        r = spring(s, c["omega"], c["zeta"], c["s_w0"], c["s_w1"])
        O = cubic(*self.bz, r)
        g = c.get("spread", 0.0)
        if g:
            # 가운데부터 착지하는 리본: 양 끝을 길이 방향으로 벌려 접힌 V 대신 열린 호로 들어오게 (r→1 이면 0)
            mr = 1 - r
            O = O + ((mr ** 3 + 3 * mr * mr * r) * g * (u - c.get("uc", 0.5)))[:, None] * (self.p.L * self.p.length)
        alpha = smootherstep(c["sa0"], c["sa1"], s)
        env_f = 1 - smootherstep(c["sf0"], c["sf1"], s)
        # 비틀림 풀림 시점을 섹션마다 늦춰(먼저 착지하는 쪽일수록 늦게) 풀림이 리본을 따라 거꾸로 번지게
        lag = c.get("twist_lag", 0.0) * (1 - w)
        env_t = 1 - smootherstep(c["st0"] + lag, c["st1"] + lag, s)
        C = self.Sm + O
        T0 = np.gradient(C, u, axis=0)
        T0 /= np.maximum(np.linalg.norm(T0, axis=1, keepdims=True), 1e-9)
        N0 = np.stack([T0[:, 1], -T0[:, 0]], axis=1)
        th = np.zeros_like(u)
        for A, k, f, ph in c["twist"]:
            th += A * np.sin(2 * math.pi * (k * u - f * t) + ph)
        th = th * env_t
        # ── 바람 ──────────────────────────────────────────────────────────────
        # 앞(먼저 닿는) 끝에서 자유로운 뒤 끝으로 흘러가는 물결 3개 (위상 k·d − f·t), 뒤 끝일수록 크게,
        # 돌풍 g(t) 로 세기가 출렁인다. 옆 흔들림 + 길이 방향 출렁임 + 면 기울기(폭 마디) + 가장자리 잔물결.
        wd = c.get("wind")
        fl = np.zeros_like(u)
        flt = np.zeros_like(u)
        rip = np.zeros_like(u)
        shr = np.zeros_like(u)
        if wd:
            d = w
            gust = 1.0
            for a, f, ph in wd["gust"]:
                gust += a * math.sin(2 * math.pi * f * t + ph)
            # 카메라 가까이(막 등장)일수록 더 크게 펄럭인다: × (1 + boost·(1 − r)²)
            base = env_f * gust * (0.2 + 0.8 * d ** 0.8) * (1 + wd.get("boost", 0.0) * (1 - r) ** 2)
            W = np.zeros_like(u); W2 = np.zeros_like(u); W3 = np.zeros_like(u); R = np.zeros_like(u); Rs = np.zeros_like(u)
            for a, k, f, ph in wd["waves"]:
                q = 2 * math.pi * (k * d - f * t) + ph
                W += a * np.sin(q)
                W2 += a * np.sin(q + 1.3)
                W3 += a * np.cos(q)
                q2 = 2 * math.pi * (1.7 * k * d - 1.3 * f * t) + ph + 0.7
                R += a * np.sin(q2)
                Rs += a * np.cos(q2)
            fl = wd["amp"] * base * W
            flt = wd["long"] * wd["amp"] * base * W2
            th = th + wd["flap"] * base * W3
            rip = wd["ripple"] * base * R
            shr = wd["shear"] * base * Rs
        for A, k, f, ph in c.get("flutter", []):
            fl += env_f * A * np.sin(2 * math.pi * (k * u - f * t) + ph)
        # 단면 방향은 바람에 휜 실제 중심선에서 다시 잰다
        C2 = C + fl[:, None] * N0 + flt[:, None] * T0
        T = np.gradient(C2, u, axis=0)
        T /= np.maximum(np.linalg.norm(T, axis=1, keepdims=True), 1e-9)
        N = np.stack([T[:, 1], -T[:, 0]], axis=1)
        # 원근: 카메라(눈) 바로 앞에서 출발해 로고 평면으로 멀어진다. 배율 ps = 1 + (S0 − 1)·(1 − r)²
        # (출발 S0 배 → 착지 r = 1 에서 정확히 1). 로고 중심을 소실점으로 위치·폭·흩날림이 함께 줄어든다.
        S0 = c.get("persp", 1.0)
        ps = 1 + (S0 - 1) * (1 - r) ** 2
        if S0 > 1:
            # 출발 전·직후에는 카메라 뒤(배율 21배)에 있다가 0.18초 동안 화면 안으로 스쳐 들어온다 → 멈춘 큰 조각이 보이지 않게
            ps = ps * (1 + 20 * (1 - smootherstep(0.0, 0.18, s)))
        lit = lighting(th, N, alpha)
        # 막 등장할 때 천이 더 넓다: 폭 × (1 + wide·(1 − r)^1.5)  (착지하면 1)
        wm = 1 + c.get("wide", 0.0) * np.clip(1 - r, 0, None) ** 1.5
        return dict(s=s, r=r, O=O, alpha=alpha, T=T, N=N, T0=T0, fl=fl, flt=flt, rip=rip, shr=shr,
                    th=th, env_f=env_f, ps=ps, lit=lit, wm=wm)

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
        lam = self.c.get("drape", 0.0)
        if lam:
            # 바깥 가장자리(v0)부터 안쪽으로 내려앉는다: α 를 v 에 따라 조금씩 늦춘다 (끝 단면 근처는 약하게)
            c = self.c
            v0 = c.get("v0", 0.5)
            dv = np.abs(np.clip(v, 0, 1) - v0) / max(v0, 1 - v0)
            le = lam * smootherstep(0.05, 0.18, np.minimum(u, 1 - u))
            s = ip(F["s"])
            al = smootherstep(c["sa0"] + le * (dv - 0.5), c["sa1"] + le * (dv - 0.5), s)[..., None]
        else:
            al = ip(F["alpha"])[..., None]
        T0 = np.stack([ip(F["T0"][:, 0]), ip(F["T0"][:, 1])], -1)
        T0 /= np.maximum(np.linalg.norm(T0, axis=-1, keepdims=True), 1e-9)
        N0 = np.stack([T0[..., 1], -T0[..., 0]], -1)
        fl = ip(F["fl"])[..., None]
        flt = ip(F["flt"])[..., None]
        rip = ip(F["rip"])[..., None]
        shr = ip(F["shr"])[..., None]
        th = ip(F["th"])[..., None]
        wf = ip(self.wf)[..., None] * ip(F["wm"])[..., None]
        Sm = self.p.spine_smooth(u)
        k = self.c.get("kp", 0.22)
        # 끝 단면이 아주 짧은 곳은 v 가 [0,1] 밖으로 크게 벗어나므로 비행 단면에서는 잘라 쓴다
        vc = np.clip(v, -0.05, 1.05)[..., None]
        flight = (Sm + fl * N0 + flt * T0
                  + (vc - 0.5) * wf * ((1 + rip) * (np.cos(th) * N + k * np.sin(th) * T) + shr * T))
        Xf = O + (1 - al) * flight + al * self.p.P(u, v)
        ps = ip(F["ps"])[..., None]
        return LOGO_CENTER + ps * (Xf - LOGO_CENTER)


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
        # 이웃 세그먼트와 표본을 반씩 겹쳐 맞춘다 → 이음선에서 색이 이어진다
        du = 0.5 * (self.u1 - self.u0)
        uu, vv = np.meshgrid(np.linspace(max(self.u0 - du, 0.0), min(self.u1 + du, 1.0), 5), np.linspace(0.0, 1.0, 4))
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
        # 비행 중 끝 단면처럼 크게 눌린 곳은 보정 핸들이 현보다 길어져 '부리'가 생긴다 →
        # 현 길이의 0.4배로 제한하되 착지(α→1)할수록 제한을 풀어 원본 핸들로 돌아간다
        al = float(np.interp(0.5 * (self.u0 + self.u1), ribbon.ug, F["alpha"]))
        ch = np.linalg.norm(A1 - A, axis=1, keepdims=True)
        for h in (ho, hi):
            ln = np.linalg.norm(h, axis=1, keepdims=True)
            sc = np.minimum(1.0, 0.4 * ch / np.maximum(ln, 1e-9))
            h *= al + (1 - al) * sc
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
        # 빛: 세그먼트 양 끝 단면의 명암 → 검정(그늘)·흰색(빛) 덮개 램프
        ip = lambda arr, x: float(np.interp(x, ribbon.ug, arr))
        um = 0.5 * (self.u0 + self.u1)
        sh, li = [], []
        for uu in (self.u0, self.u1):
            th_, lt = ip(F["th"], uu), ip(F["lit"], uu)
            sh.append(min(0.45, shade_of(th_) + max(0.0, -lt)))
            li.append(min(0.5, max(0.0, lt)))
        # 램프 축: 두 단면선(세그먼트 경계)의 평균 방향에 수직 → 등명암선이 경계선과 나란해 이웃 세그먼트와 이어진다
        e = ribbon.X(F, np.array([self.u0, self.u0, self.u1, self.u1, self.u0, self.u1]),
                     np.array([0.0, 1.0, 0.0, 1.0, 0.5, 0.5]))
        dcut = (e[1] - e[0]) + (e[3] - e[2])
        nrm = np.linalg.norm(dcut)
        p0 = e[4]
        if nrm > 1e-6:
            ax = np.array([dcut[1], -dcut[0]]) / nrm
            p1 = p0 + ax * float(np.dot(e[5] - p0, ax))
        else:
            p1 = e[5]
        al = ip(F["alpha"], um)
        return dict(sid=self.sid, k=self.k, cubics=cub, grad=(gs, ge), aff=M, grad_logo=(self.start, self.end),
                    stops=self.stops,
                    shade=tuple(sh), light=tuple(li), shade_pts=(p0, p1), alpha=al)


# 조명: 왼쪽 위 앞쪽에서 오는 빛 (화면 x 오른쪽, y 아래, z 보는 사람 쪽)
LIGHT = np.array([-0.45, -0.55, 0.70]) / np.linalg.norm([-0.45, -0.55, 0.70])
HALF = (LIGHT + np.array([0, 0, 1.0])) / np.linalg.norm(LIGHT + np.array([0, 0, 1.0]))
K_DIFF, K_SPEC, SPEC_P = 0.45, 0.50, 8
DIFF_REF = 0.55                       # 명암 기준 (n·L 의 대략 평균) → 비행 중 평균 밝기가 로고 색과 비슷하게


def lighting(th, N, alpha):
    """단면 u 의 명암 (+ 흰 빛 / − 그늘).

    천 면의 법선 n = cosθ·ẑ − sinθ·N (θ = 단면의 회전, N = 화면 안 법선), 보이는 면 n_vis = sign(cosθ)·n.
    명암 = K_DIFF·(n_vis·L − 0.55) + K_SPEC·(max(0, n_vis·H)^p − H_z^p)   (L 조명, H 반사 반각 벡터)
    → 휘는 곳·물결의 마루에 부드러운 하이라이트, 비스듬히 돌아선 곳은 그늘. (1 − α) 로 착지하면 0."""
    c, s = np.cos(th), np.sin(th)
    sg = np.where(c >= 0, 1.0, -1.0)
    ndl = sg * (c * LIGHT[2] - s * (N[:, 0] * LIGHT[0] + N[:, 1] * LIGHT[1]))
    ndh = sg * (c * HALF[2] - s * (N[:, 0] * HALF[0] + N[:, 1] * HALF[1]))
    lit = K_DIFF * (ndl - DIFF_REF) + K_SPEC * (np.maximum(ndh, 0) ** SPEC_P - HALF[2] ** SPEC_P)
    return lit * (1 - alpha)


def shade_of(theta):
    """비틀림 각 → 검정 덮개 불투명도. 앞면 0, 옆면 0.16, 뒷면 0.28 ('조금 어둡게')."""
    c = math.cos(theta)
    if c >= 0:
        return 0.16 * (1 - c) ** 1.4
    return 0.16 + 0.12 * (-c) ** 0.8


class Scene:
    def __init__(self, cfg, K=None, overlap=0.004):
        self.cfg = cfg
        self.g, self.surf = geom.load()
        self.ribbons = {}
        self.params = {}
        for r in ("left", "center", "right"):
            ss = [s for s in self.surf.values() if s["ribbon"] == r]
            self.params[r] = geom.RibbonParam(r, ss)
            self.ribbons[r] = Ribbon(r, self.params[r], cfg["ribbons"][r], time_scale=cfg.get("time_scale", 1.0))
        K = K or cfg["K"]
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
        """완전 정지(원본 벡터로 교체 가능) 시각."""
        return {r: rb.land_time() for r, rb in self.ribbons.items()}

    def land_marks(self, tol_px=1.0):
        """눈에 '멈춘' 시각 = 모든 섹션이 제자리에서 tol_px 안에 들어오고 형태가 완성된 첫 시각 (마커·햅틱)."""
        out = {}
        uu = np.linspace(0, 1, 129)
        for r, rb in self.ribbons.items():
            t = rb.c["T0"] * rb.ts
            t_end = rb.land_time()
            last_bad = t
            while t <= t_end:
                F = rb.fields(t)
                P = rb.p.P(uu, np.full_like(uu, 0.5))
                X = rb.X(F, uu, np.full_like(uu, 0.5))
                Xe = rb.X(F, np.concatenate([uu, uu]), np.concatenate([np.zeros_like(uu), np.ones_like(uu)]))
                Pe = rb.p.P(np.concatenate([uu, uu]), np.concatenate([np.zeros_like(uu), np.ones_like(uu)]))
                err = max(np.abs(X - P).max(), np.abs(Xe - Pe).max()) * UNIT
                if err >= tol_px:
                    last_bad = t
                t += 1.0 / 240
            out[r] = last_bad + 1.0 / 240
        return out
