"""엔진 그리기 목록 → PNG/MP4 (skia-python; 안드로이드와 같은 Skia 렌더러).

Lottie·Compose 와 같은 규칙으로 그린다:
  세그먼트마다 ① 그라데이션 채움(시작·끝점 매핑) ② 검정 음영 램프(두 단면 사이 선형) — 같은 경로에.
"""
import math
import os
import subprocess

import numpy as np
import skia

import geom
from motion import ARTBOARD, LOGO_CENTER, SCREEN_CENTER, UNIT, FPS


def hexcol(h, a=1.0):
    h = h.lstrip("#")
    return skia.Color4f(int(h[0:2], 16) / 255, int(h[2:4], 16) / 255, int(h[4:6], 16) / 255, a)


def path_of(cub):
    p = skia.Path()
    p.moveTo(*cub[0][0])
    for c in cub:
        p.cubicTo(*c[1], *c[2], *c[3])
    p.close()
    return p


def logo_matrix(breath=1.0, scale=1.0):
    m = skia.Matrix()
    m.preScale(scale, scale)
    m.preTranslate(*SCREEN_CENTER)
    m.preScale(UNIT * breath, UNIT * breath)
    m.preTranslate(*(-LOGO_CENTER))
    return m


class Renderer:
    def __init__(self, scene, scale=1.0, bg="#0B0B1A", label=None):
        self.scene = scene
        self.scale = scale
        self.W = int(round(ARTBOARD[0] * scale))
        self.H = int(round(ARTBOARD[1] * scale))
        self.bg = bg
        self.label = label
        self.sil = path_of(np.array(scene.silhouette))
        self.font = None

    def draw(self, canvas, fr):
        if self.bg:
            canvas.clear(hexcol(self.bg).toColor())
        canvas.save()
        canvas.concat(logo_matrix(fr["breath"], self.scale))
        for sid, segs in fr["layers"]:
            for sg in segs:
                op = 1.0
                if sid == "04_center_fold":
                    op = float(np.clip((sg["alpha"] - 0.85) / 0.15, 0, 1))
                    op = op * op * (3 - 2 * op)
                    if op <= 0.001:
                        continue
                path = path_of(sg["cubics"])
                # 바탕 그라데이션: 로고 좌표의 원래 그라데이션을 세그먼트 아핀 (A, t) 로 옮긴다 (Lottie·Compose 와 같은 방식)
                M = sg["aff"]
                st, en = sg["grad_logo"]
                cols = [hexcol(c).toColor() for _, c in sg["stops"]]
                pos = [o for o, _ in sg["stops"]]
                lm = skia.Matrix.MakeAll(float(M[0, 0]), float(M[1, 0]), float(M[2, 0]),
                                         float(M[0, 1]), float(M[1, 1]), float(M[2, 1]), 0, 0, 1)
                sh = skia.GradientShader.MakeLinear([skia.Point(*st), skia.Point(*en)], cols, pos,
                                                    skia.TileMode.kClamp, 0, lm)
                canvas.drawPath(path, skia.Paint(AntiAlias=True, Shader=sh, Alphaf=op))
                p0, p1 = sg["shade_pts"]
                if np.linalg.norm(p1 - p0) < 1e-3:
                    p1 = p0 + np.array([1e-3, 0])
                for (a0, a1), rgb in ((sg["shade"], (0, 0, 0)), (sg["light"], (1, 1, 1))):
                    if max(a0, a1) > 0.003:
                        ramp = skia.GradientShader.MakeLinear(
                            [skia.Point(*p0), skia.Point(*p1)],
                            [skia.Color4f(*rgb, a0).toColor(), skia.Color4f(*rgb, a1).toColor()])
                        canvas.drawPath(path, skia.Paint(AntiAlias=True, Shader=ramp, Alphaf=op))
        hl = fr["hl"]
        if hl is not None:
            w = skia.Color4f(1, 1, 1, 0).toColor()
            pk = skia.Color4f(1, 1, 1, hl["peak"]).toColor()
            sh = skia.GradientShader.MakeLinear([skia.Point(*hl["s"]), skia.Point(*hl["e"])],
                                                [w, pk, w], [0.0, 0.5, 1.0])
            canvas.drawPath(self.sil, skia.Paint(AntiAlias=True, Shader=sh))
        canvas.restore()
        if self.label:
            if self.font is None:
                self.font = skia.Font(None, 26 * self.scale)
            canvas.drawString(self.label, 40 * self.scale, (ARTBOARD[1] - 60) * self.scale, self.font,
                              skia.Paint(AntiAlias=True, Color=skia.Color(150, 150, 180)))
            canvas.drawString("t = %.2fs" % fr["t"], 40 * self.scale, (ARTBOARD[1] - 25) * self.scale, self.font,
                              skia.Paint(AntiAlias=True, Color=skia.Color(110, 110, 140)))

    def image(self, t):
        surf = skia.Surface(self.W, self.H)
        self.draw(surf.getCanvas(), self.scene.frame(t))
        return surf.makeImageSnapshot()

    def array(self, t):
        return self.image(t).toarray(colorType=skia.kRGBA_8888_ColorType)[:, :, :3]

    def video(self, out, t0, t1, fps=FPS, crf=18):
        n = int(round((t1 - t0) * fps))
        cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
               "-s", "%dx%d" % (self.W, self.H), "-r", str(fps), "-i", "-",
               "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", str(crf), "-preset", "medium",
               "-movflags", "+faststart", out]
        proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
        for i in range(n):
            proc.stdin.write(np.ascontiguousarray(self.array(t0 + i / fps)).tobytes())
        proc.stdin.close()
        proc.wait()
        return n


def contact_sheet(renderer, times, out, cols=6, crop=None):
    """여러 시점을 한 장에. crop=(x0,y0,x1,y1) 아트보드 좌표."""
    tiles = []
    for t in times:
        a = renderer.array(t)
        if crop:
            s = renderer.scale
            x0, y0, x1, y1 = [int(round(v * s)) for v in crop]
            a = a[y0:y1, x0:x1]
        tiles.append(a)
    h, w = tiles[0].shape[:2]
    rows = int(math.ceil(len(tiles) / cols))
    sheet = np.full((rows * (h + 4), cols * (w + 4), 3), 40, np.uint8)
    for i, a in enumerate(tiles):
        r, c = divmod(i, cols)
        sheet[r * (h + 4):r * (h + 4) + h, c * (w + 4):c * (w + 4) + w] = a
    img = skia.Image.fromarray(np.dstack([sheet, np.full(sheet.shape[:2], 255, np.uint8)]),
                               colorType=skia.kRGBA_8888_ColorType)
    img.save(out)
