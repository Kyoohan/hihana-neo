"""엔진 → Lottie JSON (Bodymovin 5.x, lottie-android 6.x 호환).

구조
----
레이어(앞 → 뒤 = JSON 순서): highlight, 06, 05, 04, 03, 02, 01, logo_root(null)
  - logo_root: 로고 단위 → 화면 (앵커 (204,217), 위치 (540,936), 배율 122.73%) + 숨쉬기 배율 키.
  - 면마다 레이어 2개:
      <id>        : 움직이는 구간. 세그먼트마다 그룹 [경로(베이크), 음영 램프(gf, 검정+불투명도), 바탕 그라데이션(gf)].
      <id>_final  : 정지 이후. 부록 벡터 그대로의 경로 + 원래 그라데이션 (모양·색 동일 → 교체가 보이지 않음).
  - 04_center_fold 세그먼트는 바탕 그라데이션 불투명도를 착지 가중치 α 로 올린다 (코랄이 다리 뒤로 들어간 다음).
익스프레션·이펙트 없음. 경로/그라데이션 키프레임 + 레이어 부모 + 그라데이션 필만 쓴다.
"""
import argparse
import json
import math
import os

import numpy as np

import choreo
import geom
import motion
from motion import FPS, ARTBOARD, LOGO_CENTER, SCREEN_CENTER, UNIT

LIN_O = {"x": 0, "y": 0}
LIN_I = {"x": 1, "y": 1}


def rnd(a, nd=1):
    a = np.round(np.asarray(a, float), nd)
    a = np.where(a == 0, 0.0, a)            # -0.0 제거
    out = a.tolist()
    return out


def num(x, nd=1):
    v = round(float(x), nd)
    return 0 if v == 0 else (int(v) if v == int(v) else v)


def shape_of(cub, nd=1):
    """(n,4,2) 3차 조각 → Lottie 경로 {v, i, o, c}."""
    v = cub[:, 0]
    o = cub[:, 1] - cub[:, 0]
    i = np.roll(cub[:, 2] - cub[:, 3], 1, axis=0)   # 정점 k 의 in-핸들 = 이전 조각의 (P2 - P3)
    return {"i": rnd(i, nd), "o": rnd(o, nd), "v": rnd(v, nd), "c": True}


def static(k):
    return {"a": 0, "k": k}


def animated(frames, values, hold_last=True):
    """선형 보간 키프레임. 값이 바뀌지 않는 구간은 줄인다."""
    keys = []
    n = len(frames)
    for j, (f, val) in enumerate(zip(frames, values)):
        # 앞뒤와 같은 값이면 건너뛰기 (첫·끝 키는 유지)
        if 0 < j < n - 1 and val == values[j - 1] and val == values[j + 1]:
            continue
        kf = {"t": int(f), "s": val if isinstance(val, list) and not isinstance(val, dict) else [val]}
        if j < n - 1:
            kf["o"] = LIN_O
            kf["i"] = LIN_I
        keys.append(kf)
    if len(keys) == 1:
        v = keys[0]["s"]
        return {"a": 0, "k": v[0] if len(v) == 1 and not isinstance(v[0], (list, dict)) else v}
    return {"a": 1, "k": keys}


def animated_shape(frames, shapes):
    keys = []
    n = len(frames)
    for j, (f, s) in enumerate(zip(frames, shapes)):
        if 0 < j < n - 1 and s == shapes[j - 1] and s == shapes[j + 1]:
            continue
        kf = {"t": int(f), "s": [s]}
        if j < n - 1:
            kf["o"] = LIN_O
            kf["i"] = LIN_I
        keys.append(kf)
    if len(keys) == 1:
        return {"a": 0, "k": keys[0]["s"][0]}
    return {"a": 1, "k": keys}


def hex_rgb(h):
    h = h.lstrip("#")
    return [int(h[0:2], 16) / 255, int(h[2:4], 16) / 255, int(h[4:6], 16) / 255]


def grad_colors(stops):
    out = []
    for o, c in stops:
        out += [o] + hex_rgb(c)      # 반올림하지 않는다 (lottie-android 는 (int)(v·255) 로 잘라 1단계 어두워짐)
    return out


def screen_bbox(cub):
    """로고 단위 3차 조각 → 화면 px 경계 상자 (조절점 포함 = 곡선을 감싼다)."""
    pts = cub.reshape(-1, 2)
    px = (pts - LOGO_CENTER) * UNIT + SCREEN_CENTER
    return px.min(0), px.max(0)


def offscreen(lo, hi, margin=24):
    return hi[0] < -margin or lo[0] > ARTBOARD[0] + margin or hi[1] < -margin or lo[1] > ARTBOARD[1] + margin


def simplify_keys(track, tol, dont_care, path_check=False):
    """시간축 선형 보간 단순화 (트랙 하나): 허용 오차 안에서 가장 먼 다음 키를 고른다.
    dont_care 프레임(화면 밖)은 오차를 따지지 않는다. path_check 면 보간된 경로도 화면 밖이어야 허용."""
    nf = len(track)
    keys = [0]
    i = 0
    while i < nf - 1:
        best = i + 1
        for j in range(i + 2, nf):
            w = (np.arange(i + 1, j) - i) / (j - i)
            interp = track[i] + (track[j] - track[i]) * w[:, None]
            err = np.abs(interp - track[i + 1:j]).max(axis=1)
            tl = tol[i + 1:j] if np.ndim(tol) else tol
            bad = (err > tl) & ~dont_care[i + 1:j] if not path_check else (err > tl)
            if path_check:
                for q in np.where(bad & dont_care[i + 1:j])[0]:
                    lo, hi = screen_bbox(path_check(interp[q], i + 1 + q) if callable(path_check) else interp[q].reshape(-1, 4, 2))
                    if offscreen(lo, hi):
                        bad[q] = False
            if bad.any():
                break
            best = j
        keys.append(best)
        i = best
    return keys


def decompose_affines(Ms):
    """세그먼트 아핀 열 (각 M: 3×2, X = [x y 1]·M) → 연속적인 (위치, φ, 배율 s1·s2, θ).

    A = R(φ)·diag(s1, s2)·R(θ)  (2×2 닫힌 꼴 SVD, s2 는 부호 있음 = 뒤집힘).  φ+θ, φ−θ 를 프레임마다
    이전 값에 이어 붙여(unwrap) 각도가 튀지 않게 하고, 등방(φ−θ 가 정의되지 않음)일 땐 이전 값을 쓴다."""
    out = []
    ps = pd = None
    for M in Ms:
        a, b, c, d = M[0, 0], M[1, 0], M[0, 1], M[1, 1]      # A = [[a b],[c d]]
        E, F, G, H = (a + d) / 2, (a - d) / 2, (c + b) / 2, (c - b) / 2
        Q, R = math.hypot(E, H), math.hypot(F, G)
        s1, s2 = Q + R, Q - R
        sm = math.atan2(H, E) if Q > 1e-9 else (ps if ps is not None else 0.0)
        df = math.atan2(G, F) if R > 1e-6 * max(Q, 1e-9) else (pd if pd is not None else 0.0)
        if ps is not None:
            sm += 2 * math.pi * round((ps - sm) / (2 * math.pi))
            df += 2 * math.pi * round((pd - df) / (2 * math.pi))
        ps, pd = sm, df
        phi, th = (sm + df) / 2, (sm - df) / 2
        out.append((M[2].copy(), phi, s1, s2, th))
    return out


def affine_of(t, phi, s1, s2, th):
    cp, sp, ct, st = math.cos(phi), math.sin(phi), math.cos(th), math.sin(th)
    A = np.array([[cp, -sp], [sp, cp]]) @ np.diag([s1, s2]) @ np.array([[ct, -st], [st, ct]])
    return A, np.asarray(t)


TR = {"ty": "tr", "p": static([0, 0]), "a": static([0, 0]), "s": static([100, 100]),
      "r": static(0), "o": static(100), "sk": static(0), "sa": static(0), "nm": "Transform"}


def layer_base(ind, name, ip, op, parent=1):
    return {"ddd": 0, "ind": ind, "ty": 4, "nm": name, "parent": parent, "sr": 1,
            "ks": {"o": static(100), "r": static(0), "p": static([0, 0, 0]),
                   "a": static([0, 0, 0]), "s": static([100, 100, 100])},
            "ao": 0, "shapes": [], "ip": ip, "op": op, "st": 0, "bm": 0}


def gfill(name, stops_k, s, e, o=None, np_=None):
    g = {"ty": "gf", "nm": name, "o": o or static(100), "r": 1, "bm": 0,
         "g": {"p": np_, "k": stops_k}, "s": s, "e": e, "t": 1}
    return g


def export(variant, out, step=1, nd=1, tol_path=0.25):
    cfg = choreo.VARIANTS[variant]
    sc = motion.Scene(cfg)
    dur = cfg["duration"]
    nfr = int(round(dur * FPS))
    lands = sc.land_times()                       # 완전 정지 → 원본 벡터 레이어로 교체
    land_fr = {r: int(math.ceil(lands[r] * FPS - 1e-6)) for r in lands}
    marks = sc.land_marks()                       # 눈에 멈춘 시각 → 마커
    mark_fr = {r: int(math.ceil(marks[r] * FPS - 1e-6)) for r in marks}

    # 섹션별 움직임 시작 프레임 (그 전엔 화면 밖 대기 위치에서 정지)
    def start_frame(rname):
        c = cfg["ribbons"][rname]
        return max(0, int(math.floor(c["T0"] * FPS)))

    # 키 시각 목록 (리본별): 시작 ~ 정지, step 간격 + 끝 프레임 포함
    key_frames = {}
    for r in sc.ribbons:
        f0, f1 = start_frame(r), land_fr[r]
        fs = list(range(f0, f1, step)) + [f1]
        key_frames[r] = fs
    all_frames = sorted(set(f for fs in key_frames.values() for f in fs))
    frames_cache = {f: sc.frame(f / FPS) for f in all_frames}

    layers = []
    ind = 2
    nkeys = 0
    # 하이라이트 (맨 앞)
    hl = cfg["highlight"]
    h0, h1 = int(math.floor(hl["t0"] * FPS)), int(math.ceil(hl["t1"] * FPS))
    hs, he = [], []
    hframes = list(range(h0, h1 + 1))
    for f in hframes:
        h = sc.highlight(f / FPS)
        if h is None:
            d = np.array(hl["dir"], float)
            d /= np.linalg.norm(d)
            x = -1 if f <= h0 else 1
            c = LOGO_CENTER + d * hl["span"] * x
            h = dict(s=c - d * hl["width"], e=c + d * hl["width"], peak=hl["peak"])
        hs.append(rnd(h["s"], 2))
        he.append(rnd(h["e"], 2))
    pk = hl["peak"]
    hl_layer = layer_base(ind, "highlight", h0, h1 + 1)
    ind += 1
    sil = np.array(sc.silhouette)
    hl_layer["shapes"] = [{"ty": "gr", "nm": "highlight", "it": [
        {"ty": "sh", "nm": "silhouette", "ks": static(shape_of(sil, 3))},
        gfill("sheen", static([0, 1, 1, 1, 0.5, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0.5, pk, 1, 0]),
              animated(hframes, hs), animated(hframes, he), np_=3),
        TR]}]
    layers.append(hl_layer)

    # 면 레이어 (앞 → 뒤)
    for sid, rname, rigs in reversed(sc.rigs):
        s = sc.surf[sid]
        fs = key_frames[rname]
        lf = land_fr[rname]
        # --- 움직이는 레이어 ---
        L = layer_base(ind, sid, 0, lf)
        ind += 1
        groups = []
        for j, rg in enumerate(rigs):
            # 세그먼트 = 움직이는 그룹 변환(위치·회전·배율·회전) + 로컬 좌표 경로 + 정적 그라데이션.
            # lottie-android 는 그라데이션 셰이더를 32ms 단위로 캐시하므로(60fps 에서 두 프레임마다 재사용),
            # 그라데이션 자체는 정적으로 두고 움직임은 캐시되지 않는 변환 행렬이 맡게 한다.
            cubs, Ms, ov_s, ov_e, ov_g, li_g, op = [], [], [], [], [], [], []
            for f in fs:
                sg = frames_cache[f]["layers"]
                seg = [x for (lid, segs) in sg if lid == sid for x in segs][j]
                cubs.append(seg["cubics"])
                Ms.append(seg["aff"])
                a0, a1 = seg["shade"]
                p0, p1 = seg["shade_pts"]
                if np.linalg.norm(p1 - p0) < 0.05:
                    p1 = p0 + np.array([0.05, 0])
                ov_s.append(p0)
                ov_e.append(p1)
                ov_g.append([a0, a1])
                li_g.append(list(seg["light"]))
                if sid == "04_center_fold":
                    x = float(np.clip((seg["alpha"] - 0.85) / 0.15, 0, 1))
                    op.append(100 * x * x * (3 - 2 * x))
                else:
                    op.append(100.0)
            cubs = np.array(cubs)
            nfk = len(fs)
            dc = np.array([offscreen(*screen_bbox(c)) for c in cubs])
            ov_g = np.array(ov_g)
            li_g = np.array(li_g)
            dec = decompose_affines(Ms)
            # 변환·로컬 경로를 따로 보간하면 화면 밖에서도 둘의 조합이 화면 안으로 튈 수 있다 → 화면 밖 생략을 쓰지 않는다
            dc_any = dc
            dc = np.zeros_like(dc)
            loc, ls, le, s1s, rads = [], [], [], [], []
            for q, (t_, phi, s1, s2, th) in enumerate(dec):
                A, tt = affine_of(t_, phi, s1, s2, th)
                Ai = np.linalg.inv(A)
                lc = (cubs[q].reshape(-1, 2) - tt) @ Ai.T
                loc.append(lc.reshape(-1, 4, 2))
                ls.append((ov_s[q] - tt) @ Ai.T)
                le.append((ov_e[q] - tt) @ Ai.T)
                s1s.append(max(abs(s1), abs(s2)))
                rads.append(float(np.abs(lc - lc.mean(0)).max()) + 1.0)
            loc = np.array(loc)
            s1s = np.array(s1s)
            rads = np.array(rads)
            # 단순화: 화면 오차 ≈ 배율 × 로컬 오차, 각도 오차 ≈ 반지름 × 배율 × Δ각
            tol_loc = tol_path / s1s
            path_scr = lambda flat, q: (flat.reshape(-1, 2) @ affine_of(*dec[q])[0].T + dec[q][0]).reshape(-1, 4, 2)
            kp = simplify_keys(loc.reshape(nfk, -1), tol_loc, dc, path_check=path_scr)
            kt = sorted(set(simplify_keys(np.array([d[0] for d in dec]), tol_path, dc))
                        | set(simplify_keys(np.array([[d[1], d[4]] for d in dec]), tol_path / (rads * s1s), dc))
                        | set(simplify_keys(np.array([[d[2], d[3]] for d in dec]), tol_path / rads, dc)))
            shade_off = dc_any | (np.maximum(ov_g.max(axis=1), li_g.max(axis=1)) < 0.004)
            ka = sorted(set(simplify_keys(ov_g, 0.012, dc_any)) | set(simplify_keys(li_g, 0.012, dc_any)))
            ks = simplify_keys(np.hstack([ls, le]), 2.0 / s1s, shade_off)
            k_sh = sorted(set(ka) | set(ks))
            ko = simplify_keys(np.array(op)[:, None], 0.8, dc)
            F_ = lambda ks_: [fs[q] for q in ks_]
            items = [{"ty": "sh", "nm": "path", "ks": animated_shape(F_(kp), [shape_of(loc[q], 2) for q in kp])}]
            o = animated(F_(ko), [round(op[q], 1) for q in ko]) if sid == "04_center_fold" else None
            if max(ov_g.max(), li_g.max()) > 0.003:
                # 명암 덮개: 로고 좌표에 고정된 정적 램프 4개(흰·검정 × 앞·뒤 끝) + 매 프레임 바뀌는 채움 불투명도.
                # 앞 끝 램프(1→0) 와 뒤 끝 램프(0→1) 를 불투명도 a0, a1 로 겹치면 a0 → a1 선형 명암이 된다.
                # 그라데이션 자체는 바뀌지 않으므로 lottie-android 의 그라데이션 캐시(32ms)에 걸리지 않는다.
                pr = sc.params[rname]
                c0 = pr.P(np.array(rg.u0), np.array(0.5))
                c1 = pr.P(np.array(rg.u1), np.array(0.5))
                ax = np.array([pr.S[1], -pr.S[0]])
                q0, q1 = c0, c0 + ax * float(np.dot(c1 - c0, ax))
                if np.linalg.norm(q1 - q0) < 0.05:
                    q1 = q0 + ax * 0.05
                opf = np.array(op) / 100.0
                for nm_, col, vals in (("light", [1, 1, 1], li_g), ("shade", [0, 0, 0], ov_g)):
                    for e, ramp in ((0, [0, 1, 1, 0]), (1, [0, 0, 1, 1])):
                        track = 100 * vals[:, e] * opf
                        if track.max() < 0.3:
                            continue
                        kk_ = simplify_keys(track[:, None], 1.2, dc_any)
                        items.append(gfill("%s%d" % (nm_, e),
                                           static([0] + col + [1] + col + ramp),
                                           static(rnd(q0, 3)), static(rnd(q1, 3)),
                                           o=animated(F_(kk_), [round(float(track[q]), 1) for q in kk_]), np_=2))
            # 바탕: 부록 그라데이션 그대로 (정적) — 로컬 좌표 ≈ 로고 좌표
            items.append(gfill("fill", static(grad_colors(s["stops"])), static(rnd(s["start"], 6)), static(rnd(s["end"], 6)),
                               o=o, np_=len(s["stops"])))
            tr_in = dict(TR)
            tr_in["r"] = animated(F_(kt), [round(math.degrees(dec[q][4]), 4) for q in kt])
            items.append(tr_in)
            tr_out = dict(TR)
            tr_out["p"] = animated(F_(kt), [rnd(dec[q][0], 2) for q in kt])
            tr_out["r"] = animated(F_(kt), [round(math.degrees(dec[q][1]), 4) for q in kt])
            tr_out["s"] = animated(F_(kt), [[round(100 * dec[q][2], 4), round(100 * dec[q][3], 4)] for q in kt])
            nkeys += len(kp)
            groups.append({"ty": "gr", "nm": "seg %02d" % rg.k, "it": [
                {"ty": "gr", "nm": "local", "it": items}, tr_out]})
        # JSON 순서 = 위가 앞. 엔진은 세그먼트를 k 순서로 덧그리므로 뒤집는다.
        L["shapes"] = list(reversed(groups))
        layers.append(L)
        # --- 정지 레이어 (원본 벡터) ---
        F = layer_base(ind, sid + "_final", lf, nfr + 1)
        ind += 1
        F["shapes"] = [{"ty": "gr", "nm": sid, "it": [
            {"ty": "sh", "nm": "path", "ks": static(shape_of(np.array(s["cubics"]), 6))},
            gfill("fill", static(grad_colors(s["stops"])), static(rnd(s["start"], 6)), static(rnd(s["end"], 6)),
                  np_=len(s["stops"])),
            TR]}]
        layers.append(F)

    # 루트 null: 로고 단위 → 화면, 숨쉬기
    b = cfg["breath"]
    b0, b1 = int(math.floor(b["t0"] * FPS)), int(math.ceil(b["t1"] * FPS))
    bfr = list(range(b0, b1 + 1))
    bval = [[round(100 * UNIT * sc.breath(f / FPS), 4)] * 3 for f in bfr]
    root = {"ddd": 0, "ind": 1, "ty": 3, "nm": "logo_root", "sr": 1,
            "ks": {"o": static(100), "r": static(0),
                   "p": static([float(SCREEN_CENTER[0]), float(SCREEN_CENTER[1]), 0]),
                   "a": static([float(LOGO_CENTER[0]), float(LOGO_CENTER[1]), 0]),
                   "s": animated(bfr, bval)},
            "ao": 0, "ip": 0, "op": nfr + 1, "st": 0, "bm": 0}
    layers.append(root)

    complete = max(h1, b1)
    markers = [
        {"tm": mark_fr["left"], "cm": "land_mint", "dr": 0},
        {"tm": mark_fr["right"], "cm": "land_coral", "dr": 0},
        {"tm": mark_fr["center"], "cm": "land_indigo", "dr": 0},
        {"tm": complete, "cm": "logo_complete", "dr": 0},
    ]
    doc = {"v": "5.7.4", "fr": FPS, "ip": 0, "op": nfr, "w": ARTBOARD[0], "h": ARTBOARD[1],
           "nm": "HiHana Evo opening", "ddd": 0, "assets": [], "layers": layers, "markers": markers}
    s = json.dumps(doc, separators=(",", ":"), ensure_ascii=False)
    with open(out, "w") as fh:
        fh.write(s)
    return dict(bytes=len(s), keys=nkeys, markers=markers, frames=nfr, land=land_fr, complete=complete)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--variant", default="A")
    ap.add_argument("--out", default="opening.json")
    ap.add_argument("--step", type=int, default=1)
    ap.add_argument("--tol", type=float, default=0.25)
    a = ap.parse_args()
    print(export(a.variant, a.out, step=a.step, tol_path=a.tol))
