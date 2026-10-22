"""Compose 이식 검증용 기준값: 여러 시각의 세그먼트 경로·그라데이션·음영을 JSON 으로."""
import json
import sys

import numpy as np

import choreo
import motion


def dump(variant, out, times=(0.3, 0.6, 0.9, 1.2, 1.5, 1.8, 2.1, 2.4, 2.7, 3.0)):
    sc = motion.Scene(choreo.VARIANTS[variant])
    res = {"times": list(times), "frames": []}
    for t in times:
        fr = sc.frame(t)
        segs = []
        for sid, ss in fr["layers"]:
            for sg in ss:
                segs.append(dict(sid=sid, k=int(sg["k"]), n=int(len(sg["cubics"])),
                                 cubics=np.round(sg["cubics"], 4).reshape(-1).tolist(),
                                 grad=np.round(np.concatenate(sg["grad"]), 4).tolist(),
                                 shade=[round(float(x), 5) for x in sg["shade"]],
                                 alpha=round(float(sg["alpha"]), 5)))
        res["frames"].append(dict(t=t, segments=segs, breath=fr["breath"]))
    json.dump(res, open(out, "w"))


if __name__ == "__main__":
    dump(sys.argv[1] if len(sys.argv) > 1 else "A", sys.argv[2] if len(sys.argv) > 2 else "reference.json")
