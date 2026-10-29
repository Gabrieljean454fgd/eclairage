"""python -m session2dxf PAQUET [PAQUET…] --template gabarit.dxf --out releve.dxf"""
from __future__ import annotations

import argparse
import csv
import json
import shutil
import subprocess
from pathlib import Path

from . import geo
from .dxf import DxfOptions, PRESENTATION_DEFAULT, Shape, generate, write_counts_csv
from .merge import DEFAULT_SIGMA_M, Obj, merge
from .package import Package, load_package


def _lonlat(feat: dict, pkg: Package) -> tuple[float, float] | None:
    g = feat.get("geometry")
    if g and g.get("type") == "Point":
        return tuple(g["coordinates"][:2])
    loc = feat["properties"].get("local")
    origin = (pkg.frames.get(feat["properties"].get("session_id")) or {}).get("origin")
    if loc and origin and loc.get("x") is not None:
        return geo.local_to_lonlat(origin["lat"], origin["lon"], loc["x"], loc["y"])
    return None


def collect(pkgs: list[Package], default_sigma: float = DEFAULT_SIGMA_M):
    objs: list[Obj] = []
    shapes: list[Shape] = []
    skipped: list[str] = []
    for pkg in pkgs:
        for f in pkg.features:
            p = f["properties"]
            if p.get("kind") == "equipment":
                ll = _lonlat(f, pkg)
                if ll is None:
                    skipped.append(f"{f['id']} ({p.get('block_name')}) : sans position")
                    continue
                x, y = geo.lonlat_to_l93(*ll)
                sig = (p.get("accuracy") or {}).get("sigma_xy_m")
                z = (p.get("local") or {}).get("z")
                o = Obj(f["id"], p["block_name"], x, y, z, sig or default_sigma, (p.get("aim") or {}).get("azimuth_deg"),
                        p.get("device_id", "?"), dict(p), sigma_assumed=sig is None)
                o.props["_conv"] = geo.convergence_deg(*ll)
                objs.append(o)
            elif p.get("kind") in ("shape", "annotation", "zone") and f.get("geometry"):
                g = f["geometry"]
                ring = g["coordinates"][0] if g["type"] == "Polygon" else g["coordinates"] if g["type"] == "LineString" else [g["coordinates"]]
                pts = [geo.lonlat_to_l93(c[0], c[1]) for c in ring]
                if g["type"] == "Polygon" and len(pts) > 1 and pts[0] == pts[-1]:
                    pts = pts[:-1]
                shapes.append(Shape(f["id"], p["kind"], g["type"], pts, p.get("label") or p.get("note")))
    return objs, shapes, skipped


def osm_rings(pkgs: list[Package]) -> list[list[tuple[float, float]]]:
    """Closed building/landuse ways of refs/osm_source.json (OSM API 0.6 JSON or Overpass JSON) in L93."""
    rings = []
    for pkg in pkgs:
        src = pkg.root / "refs" / "osm_source.json"
        if not src.exists():
            continue
        data = json.loads(src.read_text(encoding="utf-8"))
        docs = data if isinstance(data, list) else data.get("responses", [data])
        for d in docs:
            d = d.get("body", d) if isinstance(d, dict) else d
            if isinstance(d, str):
                d = json.loads(d)
            els = d.get("elements", []) if isinstance(d, dict) else []
            nodes = {e["id"]: (e["lon"], e["lat"]) for e in els if e.get("type") == "node" and "lon" in e}
            for e in els:
                if e.get("type") == "way" and e.get("nodes") and e["nodes"][0] == e["nodes"][-1]:
                    pts = [nodes[n] for n in e["nodes"][:-1] if n in nodes]
                    if len(pts) >= 3:
                        rings.append([geo.lonlat_to_l93(*p) for p in pts])
    return rings


def _read_pairs(path: str) -> tuple[list, list]:
    src, dst = [], []
    with open(path, encoding="utf-8") as f:
        for row in csv.reader(f, delimiter=";"):
            if not row or row[0].startswith("#") or not row[0].replace(".", "").replace("-", "").isdigit():
                continue
            a, b, c, d = map(float, row[:4])
            src.append((a, b)); dst.append((c, d))
    return src, dst


def apply_validated(pkgs: list[Package], path: str) -> None:
    """Validated voice revisions (rev+1, source "voice") replace the matching Feature when their rev is higher."""
    upd = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if line.strip():
            f = json.loads(line); upd[f["id"]] = f
    for pkg in pkgs:
        for i, f in enumerate(pkg.features):
            u = upd.get(f["id"])
            if u and u["properties"].get("rev", 0) > f["properties"].get("rev", 0):
                pkg.features[i] = u


def run(args) -> dict:
    pkgs = [load_package(p, args.apply_pending_renames) for p in args.packages]
    for path in getattr(args, "validated", None) or []:
        apply_validated(pkgs, path)
    objs, shapes, skipped = collect(pkgs, args.default_sigma)
    res = merge(objs)
    for o in res.objects:
        o.props["_rotation"] = geo.azimuth_to_rotation(o.azimuth, o.props["_conv"]) if o.azimuth is not None else 0.0
    presentation = dict(PRESENTATION_DEFAULT)
    if args.presentation:
        presentation.update(json.loads(Path(args.presentation).read_text(encoding="utf-8")))
    opts = DxfOptions(presentation=presentation, apply_pending_renames=args.apply_pending_renames)
    report: dict = {"packages": [str(p.root) for p in pkgs]}
    if args.homologous and args.xref:
        src, dst = _read_pairs(args.homologous)
        opts.xref_name, opts.xref_fit = args.xref, geo.fit_similarity(src, dst, fixed_scale=not args.free_scale)
        report["recalage_plan"] = {"xref": args.xref, "rms_m": opts.xref_fit.rms, "residus_m": opts.xref_fit.residuals,
                                   "echelle": opts.xref_fit.scale, "rotation_deg": opts.xref_fit.rotation_deg}
    elif not args.no_osm:
        opts.osm_rings = osm_rings(pkgs)
    stats = generate(args.template, res.objects, shapes, args.out, opts)
    out = Path(args.out)
    write_counts_csv(stats.counts, out.with_suffix(".counts.csv"))
    report.update({
        "inserts": stats.inserted, "comptages": stats.counts, "fusions": res.fusions, "conflits": res.conflicts,
        "a_classer": sum((p.to_classify for p in pkgs), []), "hors_catalogue": sum((p.out_of_catalog for p in pkgs), []),
        "sans_position": skipped, "blocs_absents_du_gabarit": stats.missing_blocks, "blocs_renommes": stats.renamed_blocks,
        "geodata_gabarit_supprime": stats.geodata_removed, "audit_erreurs": stats.audit_errors,
        "avertissements": sum((p.warnings for p in pkgs), []), "erreurs": sum((p.errors for p in pkgs), []),
        "sigma_par_defaut_m": args.default_sigma, "notes": stats.notes + [
            "Horloges : la fusion est spatiale ; les décalages des chirps ne sont pas encore appliqués aux dates.",
            "GEODATA Lambert 93 non écrit (en attente d'un test d'ouverture AutoCAD validé)."],
    })
    out.with_suffix(".report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if args.oda:
        report["dwg"] = to_dwg(args.oda, out)
    return report


def to_dwg(oda: str, dxf: Path) -> str:
    """ODA File Converter: <in_dir> <out_dir> <version> <type> <recurse> <audit> [filter]."""
    exe = shutil.which(oda) or oda
    r = subprocess.run([exe, str(dxf.parent), str(dxf.parent), "ACAD2000", "DWG", "0", "1", dxf.name], capture_output=True, text=True)
    return "ok" if r.returncode == 0 else f"échec ODA ({r.returncode}) : {r.stderr.strip()[:200]}"


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="session2dxf", description=__doc__)
    ap.add_argument("packages", nargs="+", help="paquets LumiSite (dossier ou .zip)")
    ap.add_argument("--template", required=True, help="copie DXF du gabarit de Sébastien (jamais modifiée)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--presentation", help="table JSON des échelles de présentation (remplace les valeurs par défaut)")
    ap.add_argument("--xref", help="nom du bloc du plan d'architecte (ex. « 02-Plan au sol »)")
    ap.add_argument("--homologous", help="CSV ; plan_x;plan_y;l93_x;l93_y (2 à 4 points)")
    ap.add_argument("--free-scale", action="store_true", help="échelle libre (PDF importé pas à l'échelle)")
    ap.add_argument("--no-osm", action="store_true")
    ap.add_argument("--apply-pending-renames", action="store_true", help="les 4 renommages en attente de validation")
    ap.add_argument("--default-sigma", type=float, default=DEFAULT_SIGMA_M)
    ap.add_argument("--validated", action="append", help="révisions vocales validées (session2dxf.voice apply)")
    ap.add_argument("--oda", help="chemin d'ODAFileConverter pour produire le DWG")
    rep = run(ap.parse_args(argv))
    print(json.dumps({k: rep[k] for k in ("inserts", "comptages", "conflits", "audit_erreurs", "erreurs")}, ensure_ascii=False, indent=2))
    return 1 if rep["erreurs"] or rep["audit_erreurs"] else 0
