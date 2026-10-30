"""Multi-device fusion: duplicates averaged in 1/σ², conflicts listed, never decided silently."""
from __future__ import annotations

import math
from dataclasses import dataclass, field

DEFAULT_SIGMA_M = 0.5
AZIMUTH_TOL_DEG = 20.0


@dataclass
class Obj:
    id: str
    block: str
    x: float
    y: float
    z: float | None
    sigma: float
    azimuth: float | None
    device: str
    props: dict
    sources: list[str] = field(default_factory=list)
    sigma_assumed: bool = False


@dataclass
class MergeResult:
    objects: list[Obj]
    fusions: list[dict]
    conflicts: list[dict]


def _az_diff(a: float, b: float) -> float:
    d = abs(a - b) % 360.0
    return min(d, 360.0 - d)


def merge(objs: list[Obj], az_tol: float = AZIMUTH_TOL_DEG) -> MergeResult:
    """Two objects from DIFFERENT devices are duplicates when same block, distance < 2·√(σ1²+σ2²) and compatible
    azimuths. Objects of one device are never merged together (they were placed deliberately).
    Without a measured σ on either side nothing is merged automatically (spot grids are ~1 m apart): the pair is
    listed as « doublon probable ». The result depends on the input order (first object kept as target)."""
    out: list[Obj] = []
    fusions: list[dict] = []
    conflicts: list[dict] = []
    for o in objs:
        o.sources = o.sources or [o.id]
        target = None
        for m in out:
            if o.device in {d for d in m.props.get("_devices", [m.device])}:
                continue
            d = math.hypot(o.x - m.x, o.y - m.y)
            if d >= 2 * math.sqrt(o.sigma ** 2 + m.sigma ** 2):
                continue
            if o.sigma_assumed or m.sigma_assumed:
                conflicts.append({"type": "doublon probable (σ absent, non fusionné)", "ids": [m.id, o.id], "distance_m": round(d, 3)})
                continue
            if o.block != m.block:
                conflicts.append({"type": "familles différentes", "ids": [m.id, o.id], "blocks": [m.block, o.block], "distance_m": round(d, 3)})
                continue
            if o.azimuth is not None and m.azimuth is not None and _az_diff(o.azimuth, m.azimuth) > az_tol:
                conflicts.append({"type": "visées incompatibles", "ids": [m.id, o.id], "azimuths": [m.azimuth, o.azimuth], "distance_m": round(d, 3)})
                continue
            target = m
            break
        if target is None:
            o.props["_devices"] = [o.device]
            out.append(o)
            continue
        w1, w2 = 1 / target.sigma ** 2, 1 / o.sigma ** 2
        before = list(target.sources)
        target.x = (target.x * w1 + o.x * w2) / (w1 + w2)
        target.y = (target.y * w1 + o.y * w2) / (w1 + w2)
        if target.z is not None and o.z is not None:
            target.z = (target.z * w1 + o.z * w2) / (w1 + w2)
        if target.azimuth is None:
            target.azimuth = o.azimuth
        target.sigma = 1 / math.sqrt(w1 + w2)
        target.sources = before + o.sources
        target.props["_devices"].append(o.device)
        fusions.append({"kept": target.id, "merged": o.id, "sources": target.sources})
    for m in out:
        m.props.pop("_devices", None)
    return MergeResult(out, fusions, conflicts)
