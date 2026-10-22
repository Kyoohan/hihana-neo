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
            bad = (err > tol) & ~dont_care[i + 1:j] if not path_check else (err > tol)
            if path_check:
                for q in np.where(bad & dont_care[i + 1:j])[0]:
                    lo, hi = screen_bbox(interp[q].reshape(-1, 4, 2))
                    if offscreen(lo, hi):
                        bad[q] = False
            if bad.any():
                break
            best = j
        keys.append(best)
        i = best
    return keys


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
            cubs, gs, ge, ov_s, ov_e, ov_g, op = [], [], [], [], [], [], []
            for f in fs:
                sg = frames_cache[f]["layers"]
                seg = [x for (lid, segs) in sg if lid == sid for x in segs][j]
                cubs.append(seg["cubics"])
                gs.append(seg["grad"][0])
                ge.append(seg["grad"][1])
                a0, a1 = seg["shade"]
                p0, p1 = seg["shade_pts"]
                if np.linalg.norm(p1 - p0) < 0.05:
                    p1 = p0 + np.array([0.05, 0])
                ov_s.append(p0)
                ov_e.append(p1)
                ov_g.append([a0, a1])
                if sid == "04_center_fold":
                    x = float(np.clip((seg["alpha"] - 0.85) / 0.15, 0, 1))
                    op.append(100 * x * x * (3 - 2 * x))
                else:
                    op.append(100.0)
            cubs = np.array(cubs)
            nfk = len(fs)
            dc = np.array([offscreen(*screen_bbox(c)) for c in cubs])
            ov_g = np.array(ov_g)
            # 속성마다 따로 단순화 (느린 속성은 키가 적다)
            kp = simplify_keys(cubs.reshape(nfk, -1), tol_path, dc, path_check=True)
            kg = simplify_keys(np.hstack([gs, ge]), 1.5, dc)
            shade_off = dc | (ov_g.max(axis=1) < 0.004)
            ka = simplify_keys(ov_g, 0.012, dc)
            ks = simplify_keys(np.hstack([ov_s, ov_e]), 2.0, shade_off)
            k_sh = sorted(set(ka) | set(ks))
            ko = simplify_keys(np.array(op)[:, None], 0.8, dc)
            F_ = lambda ks_: [fs[q] for q in ks_]
            items = [{"ty": "sh", "nm": "path", "ks": animated_shape(F_(kp), [shape_of(cubs[q], nd) for q in kp])}]
            o = animated(F_(ko), [round(op[q], 1) for q in ko]) if sid == "04_center_fold" else None
            if ov_g.max() > 0.003:
                # 접힘(04)의 음영도 접힘과 같은 불투명도 → 접힘이 숨은 동안 음영만 보이지 않게
                items.append(gfill("shade",
                                   animated(F_(k_sh), [[0, 0, 0, 0, 1, 0, 0, 0, 0, round(ov_g[q][0], 3), 1, round(ov_g[q][1], 3)] for q in k_sh]),
                                   animated(F_(k_sh), [rnd(ov_s[q], nd) for q in k_sh]),
                                   animated(F_(k_sh), [rnd(ov_e[q], nd) for q in k_sh]), o=o, np_=2))
            items.append(gfill("fill", static(grad_colors(s["stops"])),
                               animated(F_(kg), [rnd(gs[q], nd) for q in kg]),
                               animated(F_(kg), [rnd(ge[q], nd) for q in kg]),
                               o=o, np_=len(s["stops"])))
            kk = kp
            items.append(TR)
            nkeys += len(kk)
            groups.append({"ty": "gr", "nm": "seg %02d" % rg.k, "it": items})
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
