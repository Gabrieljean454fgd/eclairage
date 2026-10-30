import hashlib
import json
from pathlib import Path

import ezdxf

from session2dxf import geo

REPO = Path(__file__).resolve().parents[3]
CATALOG = json.loads((REPO / "catalog" / "equipment_catalog.json").read_text(encoding="utf-8"))
# The 25 names as they are in eclairage03.dwg (old spellings).
DWG_NAMES = [("bloc secour" if e["block_name"] == "bloc secours" else "tableau divisionaire" if e["block_name"] == "tableau divisionnaire" else e["block_name"])
             for e in CATALOG["entries"] if e["block_name"] != "autre (à classer)"]
SBC = (0.5725, 43.0306)


def make_template(path: Path, version: str = "R2000", names=DWG_NAMES, geodata: bool = False) -> Path:
    doc = ezdxf.new(version)
    for n in names:
        b = doc.blocks.new(n)
        b.add_circle((0, 0), 0.2)
    doc.blocks.new("02-Plan au sol").add_line((0, 0), (10, 0))
    doc.layers.add("RES - Elec")
    doc.modelspace().add_blockref("02-Plan au sol", (0, 0))
    if geodata:
        g = doc.modelspace().new_geodata()
        g.dxf.design_point = (0, 0, 0)
        g.dxf.reference_point = (0.258, 46.35, 0)
    doc.saveas(str(path))
    return path


def feature(fid, block, lon, lat, rev=1, op="add", device="devA", session="s1", **props):
    p = {"rev": rev, "op": op, "kind": "equipment", "block_name": block, "layer": "RES - Elec", "status": "existant",
         "local": None, "accuracy": {"sigma_xy_m": None, "sigma_z_m": None, "method": "AR_PLANE"},
         "time": {"captured_utc": "2026-10-28T10:00:00Z"}, "device_id": device, "session_id": session}
    p.update(props)
    geom = None if lon is None else {"type": "Point", "coordinates": [lon, lat]}
    return {"type": "Feature", "id": fid, "geometry": geom, "properties": p}


def make_package(root: Path, sessions: dict, frames: dict | None = None, extra_lines: dict | None = None) -> Path:
    """sessions = {(device, session): [features]}"""
    pkg = root / "LumiSite_test"
    files, sess = [], []
    for (dev, sid), feats in sessions.items():
        p = pkg / "sessions" / dev / f"{sid}.geojsonl"
        p.parent.mkdir(parents=True, exist_ok=True)
        text = "".join(json.dumps(f, ensure_ascii=False) + "\n" for f in feats) + (extra_lines or {}).get((dev, sid), "")
        p.write_text(text, encoding="utf-8")
        fr = p.with_name(f"{sid}.frames.json")
        fr.write_text(json.dumps((frames or {}).get(sid, {"origin": {"lat": SBC[1], "lon": SBC[0]}})), encoding="utf-8")
        sess.append({"session_id": sid, "device_id": dev, "features_file": str(p.relative_to(pkg)), "frames_file": str(fr.relative_to(pkg)),
                     "feature_count": len(feats), "closed": True, "truncated_lines": []})
        for q in (p, fr):
            files.append({"path": str(q.relative_to(pkg)), "bytes": q.stat().st_size, "sha256": hashlib.sha256(q.read_bytes()).hexdigest()})
    (pkg / "catalog").mkdir(parents=True, exist_ok=True)
    (pkg / "catalog" / "equipment_catalog.json").write_text(json.dumps(CATALOG, ensure_ascii=False), encoding="utf-8")
    (pkg / "manifest.json").write_text(json.dumps({"schema": "lumisite-geojson-export", "schema_version": "1.0.0", "project_id": "t",
                                                   "sessions": sess, "files": files}), encoding="utf-8")
    return pkg


def dxf_to_features(dxf: Path, sous_type_by_scale: dict) -> list:
    """Test fixture: INSERTs of a drawing in L93 → equipment Features (WGS84 + true azimuth + sous_type)."""
    doc = ezdxf.readfile(str(dxf))
    out = []
    for i, ins in enumerate(doc.modelspace().query('INSERT[layer=="RES - Elec"]')):
        x, y = ins.dxf.insert.x, ins.dxf.insert.y
        lon, lat = geo.l93_to_lonlat(x, y)
        conv = geo.convergence_deg(lon, lat)
        az = (90.0 - ins.dxf.rotation) + conv
        props = {"aim": {"azimuth_deg": az}}
        st = sous_type_by_scale.get((ins.dxf.name, round(ins.dxf.xscale, 3)))
        if st:
            props["details"] = {"sous_type": st}
        out.append(feature(f"o{i}", ins.dxf.name, lon, lat, **props))
    return out
