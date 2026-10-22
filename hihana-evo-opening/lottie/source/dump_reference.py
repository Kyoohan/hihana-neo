"""Compose 이식 검증용 기준값: 여러 시각의 세그먼트 경로·그라데이션·음영을 JSON 으로."""
import json
import sys

import numpy as np

import choreo
import motion


def dump(variant, out, times=(0.3, 0.6, 0.9, 1.2, 1.5, 1.8, 2.1, 2.4, 2.7, 3.0)):
    """times 는 운동 시간 — 실제 시각은 × time_scale."""
    cfg = choreo.VARIANTS[variant]
    times = tuple(round(x * cfg.get("time_scale", 1.0), 4) for x in times)
    sc = motion.Scene(cfg)
    res = {"times": list(times), "frames": []}
    for t in times:
        fr = sc.frame(t)
        fr2 = sc.frame(t + 1e-4)        # 점마다 속도 → 비교 허용 오차 (카메라 뒤에서 튀어나오는 순간은 초당 수백만 단위로 움직인다)
        segs = []
        for (sid, ss), (_, ss2) in zip(fr["layers"], fr2["layers"]):
            for sg, sg2 in zip(ss, ss2):
                vel = np.abs(sg2["cubics"] - sg["cubics"]) / 1e-4
                segs.append(dict(sid=sid, k=int(sg["k"]), n=int(len(sg["cubics"])),
                                 cubics=np.round(sg["cubics"], 4).reshape(-1).tolist(),
                                 vel=np.round(vel, 1).reshape(-1).tolist(),
                                 grad=np.round(np.concatenate(sg["grad"]), 4).tolist(),
                                 shade=[round(float(x), 5) for x in sg["shade"]],
                                 alpha=round(float(sg["alpha"]), 5)))
        res["frames"].append(dict(t=t, segments=segs, breath=fr["breath"]))
    json.dump(res, open(out, "w"))


if __name__ == "__main__":
    dump(sys.argv[1] if len(sys.argv) > 1 else "A", sys.argv[2] if len(sys.argv) > 2 else "reference.json")
