"""choreo.py / geometry.json → EvoOpening.kt 의 상수 블록 (두 구현이 같은 숫자를 쓰도록 생성)."""
import sys

import choreo
import geom
import motion

ORDER = {"head": 0, "tail": 1, "center": 2}
RIB = ["left", "center", "right"]


def f(x):
    s = ("%.6f" % float(x)).rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    if "." not in s:
        s += ".0"
    return s + "f"


def color(h):
    return "0xFF%s.toInt()" % h.lstrip("#").upper()


def gen(variant):
    cfg = choreo.VARIANTS[variant]
    g, surf = geom.load()
    out = []
    w = out.append
    w("// ── 생성된 상수 (lottie/source/gen_kotlin_consts.py, 시안 %s) — Lottie 와 같은 숫자 ──" % variant)
    w("private const val TOTAL = %s            // 전체 길이 (초)" % f(cfg["duration"]))
    w("private const val LOGO_CX = %s; private const val LOGO_CY = %s" % (f(motion.LOGO_CENTER[0]), f(motion.LOGO_CENTER[1])))
    w("private const val OVERLAP = 0.004f        // 세그먼트 겹침 (u)")
    b, h = cfg["breath"], cfg["highlight"]
    w("private const val BREATH_T0 = %s; private const val BREATH_T1 = %s; private const val BREATH_AMP = %s"
      % (f(b["t0"]), f(b["t1"]), f(b["amp"])))
    w("private const val HL_T0 = %s; private const val HL_T1 = %s; private const val HL_PEAK = %s"
      % (f(h["t0"]), f(h["t1"]), f(h["peak"])))
    w("private const val HL_DIR_X = %s; private const val HL_DIR_Y = %s; private const val HL_SPAN = %s; private const val HL_WIDTH = %s"
      % (f(h["dir"][0]), f(h["dir"][1]), f(h["span"]), f(h["width"])))
    w("")
    w("/** 갈래 순서: 0 = 왼쪽(민트), 1 = 가운데(인디고), 2 = 오른쪽(코랄). (길이축 L, 단면 방향 S) */")
    w("private val RIBBON_FRAMES = arrayOf(")
    for r in RIB:
        (lx, ly), (sx, sy) = geom.RIBBON_FRAME[r]
        w("    floatArrayOf(%s, %s, %s, %s),   // %s" % (f(lx), f(ly), f(sx), f(sy), r))
    w(")")
    K = cfg.get("K", {"left": 12, "center": 10, "right": 12})
    w("private val SEGMENTS = intArrayOf(%s)" % ", ".join(str(K[r]) for r in RIB))
    w("")
    w("private val CHOREO = arrayOf(")
    for r in RIB:
        c = cfg["ribbons"][r]
        fl = ", ".join(f(x) for t in c["flutter"] for x in t)
        tw = ", ".join(f(x) for t in c["twist"] for x in t)
        w("    RibbonCfg(   // %s" % r)
        w("        order = %d, uc = %s, t0 = %s, kappa = %s," % (ORDER[c["order"]], f(c.get("uc", 0.5)), f(c["T0"]), f(c["kappa"])))
        w("        fx = %s, fy = %s, h0x = %s, h0y = %s, dinX = %s, dinY = %s, h1 = %s, wf = %s,"
          % (f(c["F"][0]), f(c["F"][1]), f(c["h0"][0]), f(c["h0"][1]), f(c["din"][0]), f(c["din"][1]), f(c["h1"]), f(c["wf"])))
        w("        flutter = floatArrayOf(%s)," % fl)
        w("        twist = floatArrayOf(%s)," % tw)
        w("        omega = %s, zeta = %s, sW0 = %s, sW1 = %s, sa0 = %s, sa1 = %s, sf0 = %s, sf1 = %s, st0 = %s, st1 = %s, kp = %s, taper = %s,"
          % (tuple(f(c[k]) for k in ("omega", "zeta", "s_w0", "s_w1", "sa0", "sa1", "sf0", "sf1", "st0", "st1", "kp"))
             + (f(c.get("taper", 0.16)),)))
        w("    ),")
    w(")")
    w("")
    w("/** 부록 벡터 (레이어 순서 뒤 → 앞). 경로 문자열은 시작할 때 한 번만 파싱한다. */")
    w("private val SURFACES = listOf(")
    for sid in geom.LAYER_ORDER:
        s = surf[sid]
        w("    Surface(")
        w("        \"%s\", %d," % (sid, RIB.index(s["ribbon"])))
        w("        \"%s\"," % s["d"])
        w("        %s, %s, %s, %s," % (f(s["start"][0]), f(s["start"][1]), f(s["end"][0]), f(s["end"][1])))
        w("        floatArrayOf(%s)," % ", ".join(f(o) for o, _ in s["stops"]))
        w("        intArrayOf(%s)," % ", ".join(color(c) for _, c in s["stops"]))
        w("    ),")
    w(")")
    w("private const val SILHOUETTE =")
    w("    \"%s\"" % g["silhouette"])
    return "\n".join(out)


if __name__ == "__main__":
    print(gen(sys.argv[1] if len(sys.argv) > 1 else "A"))
