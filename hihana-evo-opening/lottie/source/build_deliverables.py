"""고른 시안으로 납품물 전체를 다시 만든다.

  python3 build_deliverables.py A  [--harness DIR]

만드는 것 (hihana-evo-opening/ 아래)
  lottie/opening.json               Lottie 최종본
  compose/EvoOpening.kt             Compose 최종본 (상수는 gen_kotlin_consts.py 가 생성)
  preview/lottie.mp4                lottie-android 6.x 로 재생한 프레임 → H.264 1080×2340 60fps, 배경 #0B0B1A
  preview/compose.mp4               EvoOpening 을 Robolectric(안드로이드 Skia, JVM)에서 프레임 단위로 녹화
  preview/keyframes/*.png           핵심 장면 8장 (lottie-android 렌더)
  lottie/source/build_report.json   타임라인·마커·검증 수치 (README 표의 근거)

검증 하네스(Gradle 안드로이드 프로젝트, lottie-web)는 verify/ 의 설명대로 준비한다.
"""
import argparse
import json
import math
import os
import re
import shutil
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))           # hihana-evo-opening/
sys.path.insert(0, HERE)

import choreo  # noqa: E402
import export_lottie  # noqa: E402
import gen_kotlin_consts  # noqa: E402
import motion  # noqa: E402
from motion import FPS, ARTBOARD, UNIT, LOGO_CENTER, SCREEN_CENTER  # noqa: E402

KEYFRAMES = [  # (이름, 프레임 결정 함수) — 타임라인에서 계산
    ("00_start", lambda m: m["first_visible"]["left"] + 10),             # 민트 머리가 화면에 들어옴
    ("01_ribbons_fly", lambda m: m["first_visible"]["right"] + 22),      # 민트·코랄 실크 리본 비행 (비틀림)
    ("02_mint_forms", lambda m: m["form_start"]["left"] + 24),           # 민트가 위 끝부터 형태를 잡음
    ("03_coral_forms", lambda m: m["form_start"]["right"] + 30),         # 코랄이 아래 끝부터, 인디고 진입
    ("04_indigo_weaves", lambda m: m["first_visible"]["center"] + 45),   # 인디고가 코랄 앞·뒷면 사이로
    ("05_indigo_lands", lambda m: m["land"]["center"] - 30),             # 인디고가 왼쪽 끝부터 다리로
    ("06_breath_highlight", lambda m: (m["highlight"][0] + m["highlight"][1]) // 2),
    ("07_final", lambda m: m["frames"] - 1),
]


def run(cmd, cwd=None, env=None):
    print("+", " ".join(cmd) if isinstance(cmd, list) else cmd, flush=True)
    r = subprocess.run(cmd, cwd=cwd, env=env, shell=isinstance(cmd, str), capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout[-4000:], r.stderr[-4000:])
        raise SystemExit("failed: %s" % cmd)
    return r.stdout


def timeline(variant):
    cfg = choreo.VARIANTS[variant]
    sc = motion.Scene(cfg)
    nfr = int(round(cfg["duration"] * FPS))
    marks = sc.land_marks()
    full = sc.land_times()
    first_vis, form_start = {}, {}
    uu = np.linspace(0, 1, 65)
    for r, rb in sc.ribbons.items():
        fv = fs = None
        for f in range(nfr):
            F = rb.fields(f / FPS)
            X = rb.X(F, np.concatenate([uu, uu]), np.concatenate([np.zeros_like(uu), np.ones_like(uu)]))
            px = (X - LOGO_CENTER) * UNIT + SCREEN_CENTER
            on = ((px[:, 0] > 0) & (px[:, 0] < ARTBOARD[0]) & (px[:, 1] > 0) & (px[:, 1] < ARTBOARD[1])).any()
            if fv is None and on:
                fv = f
            if fs is None and F["alpha"].max() > 0.02:
                fs = f
            if fv is not None and fs is not None:
                break
        first_vis[r], form_start[r] = fv, fs
    b, h = cfg["breath"], cfg["highlight"]
    complete = max(int(math.ceil(h["t1"] * FPS)), int(math.ceil(b["t1"] * FPS)))
    return dict(variant=variant, title=cfg["title"], frames=nfr, fps=FPS,
                first_visible=first_vis, form_start=form_start,
                land={r: int(math.ceil(marks[r] * FPS - 1e-6)) for r in marks},
                rest={r: int(math.ceil(full[r] * FPS - 1e-6)) for r in full},
                breath=(int(math.floor(b["t0"] * FPS)), int(math.ceil(b["t1"] * FPS)), b["amp"]),
                highlight=(int(math.floor(h["t0"] * FPS)), int(math.ceil(h["t1"] * FPS))),
                complete=complete, hold_s=(nfr - complete) / FPS)


def write_keyframes(tl, frames_dir):
    kdir = os.path.join(ROOT, "preview", "keyframes")
    os.makedirs(kdir, exist_ok=True)
    for fn in os.listdir(kdir):
        os.remove(os.path.join(kdir, fn))
    kf = []
    for name, fnc in KEYFRAMES:
        f = int(max(0, min(tl["frames"] - 1, fnc(tl))))
        dst = os.path.join(kdir, "%s_f%03d.png" % (name, f))
        shutil.copy(os.path.join(frames_dir, "frame_%05d.png" % f), dst)
        kf.append(os.path.basename(dst))
    return kf


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("variant", choices=list(choreo.VARIANTS))
    ap.add_argument("--harness", default=os.environ.get("EVO_HARNESS", ""))
    ap.add_argument("--keyframes-only", action="store_true")
    a = ap.parse_args()
    v = a.variant
    H = a.harness
    report = dict(timeline=timeline(v))
    if a.keyframes_only:
        old = json.load(open(os.path.join(HERE, "build_report.json")))
        old["keyframes"] = write_keyframes(report["timeline"], os.path.join(H, "out", "final_lottie_android"))
        json.dump(old, open(os.path.join(HERE, "build_report.json"), "w"), ensure_ascii=False, indent=1)
        print(old["keyframes"])
        return

    # 1) Lottie
    os.makedirs(os.path.join(ROOT, "lottie"), exist_ok=True)
    lj = os.path.join(ROOT, "lottie", "opening.json")
    report["lottie"] = export_lottie.export(v, lj, tol_path=0.3)

    # 2) Compose: 생성 상수 블록 갱신 → 하네스에 복사 (검증 테스트가 접근하도록 private class → internal)
    kt = os.path.join(ROOT, "compose", "EvoOpening.kt")
    gen_kotlin_consts.splice(v, kt)
    src = open(kt, encoding="utf-8").read()
    src = re.sub(r"^private class (EvoScene|SegmentRig|Ribbon|Surface|RibbonParam|RibbonCfg|SegmentGeom)\b",
                 r"internal class \1", src, flags=re.M)
    open(os.path.join(H, "android", "app", "src", "main", "java", "com", "skh", "hihanaevo", "EvoOpening.kt"),
         "w", encoding="utf-8").write(src)

    adir = os.path.join(H, "android")
    # 3) Kotlin ↔ Python 수치 일치
    ref = os.path.join(H, "ref_%s.json" % v)
    import dump_reference
    dump_reference.dump(v, ref)
    out = run(["./gradlew", ":app:testDebugUnitTest", "--tests", "*EvoParity*", "-Pevo.reference=" + ref], cwd=adir)
    report["parity"] = [ln.strip() for ln in out.splitlines() if "worst" in ln]

    # 4) lottie-android 프레임 → lottie.mp4 + 키프레임
    la = os.path.join(H, "out", "final_lottie_android")
    shutil.rmtree(la, ignore_errors=True)
    out = run(["./gradlew", ":app:testDebugUnitTest", "--tests", "*LottieRender*", "-PlottieJson=" + lj,
               "-PlottieOut=" + la, "-PlottieBg=#0B0B1A"], cwd=adir)
    report["lottie_android_log"] = [ln.strip() for ln in out.splitlines() if "[lottie" in ln]
    os.makedirs(os.path.join(ROOT, "preview", "keyframes"), exist_ok=True)
    run(["bash", os.path.join(H, "frames_to_mp4.sh"), la, os.path.join(ROOT, "preview", "lottie.mp4"), "60", "16"])
    report["keyframes"] = write_keyframes(report["timeline"], la)

    # 5) lottie-web 와 비교 (같은 JSON 을 브라우저 엔진으로)
    lw = os.path.join(H, "out", "final_lottie_web")
    shutil.rmtree(lw, ignore_errors=True)
    run(["node", "render_lottie_web.mjs", lj, lw, "--bg", "#0B0B1A"], cwd=os.path.join(H, "web"))
    from PIL import Image
    diffs = []
    for f in range(0, report["timeline"]["frames"], 3):
        x = np.asarray(Image.open(os.path.join(la, "frame_%05d.png" % f)).convert("RGB"), dtype=np.int16)
        y = np.asarray(Image.open(os.path.join(lw, "frame_%05d.png" % f)).convert("RGB"), dtype=np.int16)
        d = np.abs(x - y).max(axis=2)
        diffs.append((float(d.mean()), int((d > 40).sum())))
    report["android_vs_web"] = dict(mean_absdiff_max=max(d[0] for d in diffs), px_over40_max=max(d[1] for d in diffs))

    # 6) Compose 녹화 → compose.mp4
    cf = os.path.join(H, "out", "final_compose")
    shutil.rmtree(cf, ignore_errors=True)
    out = run(["./gradlew", ":app:testDebugUnitTest", "--tests", "*RecordFrames*", "-PframesDir=" + cf], cwd=adir)
    report["compose_log"] = [ln.strip() for ln in out.splitlines() if "[record]" in ln]
    run(["bash", os.path.join(H, "frames_to_mp4.sh"), cf, os.path.join(ROOT, "preview", "compose.mp4"), "60", "16"])

    json.dump(report, open(os.path.join(HERE, "build_report.json"), "w"), ensure_ascii=False, indent=1)
    print(json.dumps(report, ensure_ascii=False, indent=1)[:4000])


if __name__ == "__main__":
    main()
