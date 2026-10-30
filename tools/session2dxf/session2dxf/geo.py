"""WGS84 ↔ Lambert 93, meridian convergence, 2D similarity / Helmert fits with RMS and residuals."""
from __future__ import annotations

import math
from dataclasses import dataclass

from pyproj import Proj, Transformer

L93 = "EPSG:2154"
_to_l93 = Transformer.from_crs("EPSG:4326", L93, always_xy=True)
_from_l93 = Transformer.from_crs(L93, "EPSG:4326", always_xy=True)
_proj_l93 = Proj(L93)


def lonlat_to_l93(lon: float, lat: float) -> tuple[float, float]:
    return _to_l93.transform(lon, lat)


def l93_to_lonlat(x: float, y: float) -> tuple[float, float]:
    return _from_l93.transform(x, y)


def local_to_lonlat(origin_lat: float, origin_lon: float, x: float, y: float) -> tuple[float, float]:
    """Site frame (X east, Y north, metres) → WGS84, azimuthal equidistant around the site origin."""
    t = Transformer.from_crs(f"+proj=aeqd +lat_0={origin_lat} +lon_0={origin_lon} +datum=WGS84 +units=m", "EPSG:4326", always_xy=True)
    return t.transform(x, y)


def convergence_deg(lon: float, lat: float) -> float:
    """Meridian convergence of Lambert 93 at a point (pyproj convention)."""
    return _proj_l93.get_factors(lon, lat).meridian_convergence


def azimuth_to_rotation(azimuth_true_deg: float, convergence: float) -> float:
    """True azimuth (0 = north, clockwise) → DXF rotation (0 = +X, counter-clockwise) in the L93 grid."""
    # pyproj meridian convergence γ > 0 east of the central meridian, where true north points at −γ in the grid.
    grid_az = azimuth_true_deg - convergence
    return (90.0 - grid_az) % 360.0


@dataclass
class Similarity:
    a: float  # s·cosθ
    b: float  # s·sinθ
    tx: float
    ty: float
    residuals: list[float]
    rms: float

    @property
    def scale(self) -> float:
        return math.hypot(self.a, self.b)

    @property
    def rotation_deg(self) -> float:
        return math.degrees(math.atan2(self.b, self.a))

    def apply(self, x: float, y: float) -> tuple[float, float]:
        return self.a * x - self.b * y + self.tx, self.b * x + self.a * y + self.ty


def fit_similarity(src: list[tuple[float, float]], dst: list[tuple[float, float]], fixed_scale: bool = False) -> Similarity:
    """Least-squares 2D similarity src → dst (4-parameter Helmert; scale forced to 1 if fixed_scale)."""
    if len(src) != len(dst) or len(src) < 2:
        raise ValueError("au moins 2 points homologues sont nécessaires")
    n = len(src)
    sx = sum(p[0] for p in src) / n; sy = sum(p[1] for p in src) / n
    dx = sum(p[0] for p in dst) / n; dy = sum(p[1] for p in dst) / n
    num_a = num_b = den = 0.0
    for (x, y), (X, Y) in zip(src, dst):
        x -= sx; y -= sy; X -= dx; Y -= dy
        num_a += x * X + y * Y
        num_b += x * Y - y * X
        den += x * x + y * y
    if den == 0:
        raise ValueError("points homologues confondus")
    a, b = num_a / den, num_b / den
    if fixed_scale:
        s = math.hypot(a, b); a, b = a / s, b / s
    tx = dx - (a * sx - b * sy); ty = dy - (b * sx + a * sy)
    sim = Similarity(a, b, tx, ty, [], 0.0)
    res = [math.dist(sim.apply(*p), q) for p, q in zip(src, dst)]
    sim.residuals = res
    sim.rms = math.sqrt(sum(r * r for r in res) / n)
    return sim
