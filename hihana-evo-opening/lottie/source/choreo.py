"""연출 설정 — 최종: 시안 A "Weave". (러프 B "Breeze" 는 비교 후 폐기) 좌표는 로고 단위, 오프셋은 각 섹션의 최종 위치 기준.

화면(1080×2340)을 로고 단위로 보면 x ∈ [-236, 644], y ∈ [-546, 1361]
(세로 중앙 1920px 안전 영역 y ∈ [-375, 1190]).
"""
import math

PI = math.pi

# 섹션 공통 시간 상수 (s = 섹션 경과 시간)
COMMON = dict(omega=4.8, zeta=0.90,          # 스프링: 지나침 ≈ 0.15% (경로 길이 기준)
              s_w0=1.55, s_w1=1.95,          # 잔차를 0으로 접는 창 → 유한 시간에 정확히 정지
              sa0=0.88, sa1=1.30,            # 형태 가중치 α: 제자리 몇십 px 안에서부터 로고 단면으로
              sf0=0.45, sf1=1.05,            # 흩날림 감쇠
              st0=0.55, st1=0.95,            # 비틀림 감쇠 (섹션마다 twist_lag 만큼 늦춰 풀림이 번진다)
              twist_lag=0.25,
              drape=0.30,                    # 바깥 가장자리부터 안쪽으로 내려앉기
              kp=0.24, taper=0.22)


def rib(slow=1.0, **kw):
    """slow > 1 이면 섹션 시간 상수를 모두 늘린다 (같은 모양, 더 느린 접근)."""
    d = dict(COMMON)
    for k in ("s_w0", "s_w1", "sa0", "sa1", "sf0", "sf1", "st0", "st1", "twist_lag"):
        d[k] = d[k] * slow
    d["omega"] = d["omega"] / slow
    d.update(kw)
    return d


def screen_path(ribbon, waypoints, persp, power=2.0, rs=(0.3, 0.6)):
    """화면 경유점 → 평면 접근 경로 (F, h0, din, h1).

    원근 화면 위치 = 중심 + ps(r)·(평면 위치 − 중심),  ps(r) = 1 + (persp − 1)(1 − r)^2.
    갈래 가운데 단면이 r = 0, rs[0], rs[1] 에서 화면 오프셋(로고 중심 기준, 로고 단위) waypoints 를 지나도록
    3차 베지어 B(r) (B(1) = 0) 의 조절점을 푼다."""
    import geom
    import numpy as np
    g, surf = geom.load()
    P = geom.RibbonParam(ribbon, [x for x in surf.values() if x["ribbon"] == ribbon])
    LC = np.array([204.0, 217.0])
    target = P.P(np.array(0.5), np.array(0.5))
    ps = lambda r: 1 + (persp - 1) * (1 - r) ** power
    O = [LC + np.asarray(a, float) / ps(r) - target for r, a in zip((0.0,) + tuple(rs), waypoints)]
    P0 = O[0]
    # B(r) = (1-r)^3 P0 + 3(1-r)^2 r P1 + 3(1-r) r^2 P2   (P3 = 0)
    M = np.array([[3 * (1 - r) ** 2 * r, 3 * (1 - r) * r * r] for r in rs])
    rhs = np.array([O[k + 1] - (1 - r) ** 3 * P0 for k, r in enumerate(rs)])
    P1, P2 = np.linalg.solve(M, rhs)
    h1 = float(np.linalg.norm(P2))
    return dict(F=tuple(np.round(P0, 2)), h0=tuple(np.round(P1 - P0, 2)),
                din=tuple(np.round(-P2 / h1, 4)), h1=round(h1, 2), persp=persp)


# ---------------------------------------------------------------------------
# 시안 A — "Weave (엮기)"
#   세 갈래가 거의 동시에 카메라(눈) 바로 앞을 스치듯 크게 지나가고, 멀어지며 작아져 각자 자리로 간다.
#   민트: 왼쪽 아래에서 위 끝부터 아래로 감싸며 내려앉는다. 코랄: 오른쪽 위에서 (180° 대칭) 아래 끝부터 위로.
#   인디고: 오른쪽 아래에서 조금 더 느리게 돌아와 코랄 앞·뒷면 사이로 꿰어, 왼쪽 끝부터 마지막에 둘을 잇는다.
#   경유점: 화면 오프셋 (로고 중심 기준, 로고 단위; 화면은 x ±440, y −763 … +1144)
# ---------------------------------------------------------------------------
PERSP = 8.0
ROUGH_A = dict(
    name="A_weave",
    title="A — Weave",
    duration=6.5,
    K={"left": 20, "center": 16, "right": 20},
    ribbons=dict(
        left=rib(order="head", T0=-0.10, kappa=0.62, v0=0.0,
                 **screen_path("left", [(-760, 1500), (-250, 720), (-170, 230)], PERSP),
                 wf=46, flutter=[],
                 wind=dict(amp=46, long=0.35, flap=0.9, ripple=0.22, shear=0.20,
                           waves=[(1.0, 1.15, 0.95, 0.0), (0.55, 2.1, 1.65, 1.9), (0.25, 3.6, 2.8, 4.1)],
                           gust=[(0.30, 0.31, 1.1), (0.20, 0.53, 2.3)]),
                 twist=[(1.9, 0.8, 0.35, 0.4), (0.5, 1.9, 0.8, 2.0)]),
        right=rib(order="tail", T0=0.0, kappa=0.62, v0=1.0,
                  **screen_path("right", [(760, -1350), (250, -520), (170, -120)], PERSP),
                  wf=46, flutter=[],
                  wind=dict(amp=46, long=0.35, flap=0.9, ripple=0.22, shear=0.20,
                            waves=[(1.0, 1.1, 1.0, 2.0), (0.55, 2.2, 1.7, 0.4), (0.25, 3.5, 2.9, 3.0)],
                            gust=[(0.30, 0.29, 2.0), (0.20, 0.57, 0.4)]),
                  twist=[(1.9, 0.8, -0.35, 2.1), (0.5, 1.9, -0.8, 0.7)]),
        center=rib(slow=1.3, order="head", T0=0.10, kappa=0.55, v0=0.0,
                   **screen_path("center", [(900, 1450), (330, 760), (220, 260)], PERSP),
                   wf=48, sf0=0.45, sf1=1.05, flutter=[],
                   wind=dict(amp=38, long=0.35, flap=0.8, ripple=0.20, shear=0.18,
                             waves=[(1.0, 1.2, 0.9, 1.0), (0.55, 2.0, 1.6, 2.6), (0.25, 3.7, 2.7, 0.3)],
                             gust=[(0.30, 0.33, 0.6), (0.20, 0.49, 1.7)]),
                   twist=[(2.2, 0.9, 0.5, 0.0), (0.6, 2.0, 1.0, 0.4)]),
    ),
    breath=dict(t0=2.85, t1=3.45, amp=0.022),
    highlight=dict(t0=3.05, t1=3.95, dir=(0.545, 0.838), span=290, width=70, peak=0.30),
)

VARIANTS = {"A": ROUGH_A}   # 러프 B (Breeze) 는 시안 비교 후 폐기
