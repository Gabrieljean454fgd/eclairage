"""DXF generation from Sébastien's template: existing blocks only (never redrawn), layers, attributes, legend."""
from __future__ import annotations

import csv
from dataclasses import dataclass, field
from pathlib import Path

import ezdxf
from ezdxf import bbox

from .geo import Similarity
from .merge import Obj
from .package import ALIASES_PENDING, ALIASES_VALIDATED

LAYER_EQUIP = "RES - Elec"
LAYER_DIM = "cote"
LAYER_PHOTO = "photométrie"
LAYER_DIAG = "diagnostic"
LAYER_OSM = "OSM"
ATTR_TAGS = ["ID", "STATUT", "SOUS_TYPE", "TECHNO", "CULOT", "PUISSANCE_W", "HAUTEUR_M", "REF_ETIQUETTE",
             "SIGMA_XY_M", "SOURCES", "DATE_RELEVE", "CIRCUIT", "TABLEAU"]
# Presentation scales (purely graphical). Spot sizes ↔ PAR20/MR16/LED: mapping to be given by Sébastien (§ 15).
PRESENTATION_DEFAULT: dict[str, dict] = {
    "spot": {"default": 1.208, "by_sous_type": {}},
    "boîte de dérivation": {"default": 1.0, "by_sous_type": {}},
    "projecteur thorn contrast": {"default": 0.571},
    "extincteur": {"default": 0.6},
    "tgbt": {"default": 1.417}, "TGBT": {"default": 1.417},
    "lineaire fluo": {"default": 1.0, "by_sous_type": {}}, "linéaire fluo": {"default": 1.0, "by_sous_type": {}},
}
LEGEND_TITLE = "Block Legend"


@dataclass
class Shape:
    id: str
    kind: str  # shape | annotation | zone
    geometry_type: str  # Point | LineString | Polygon
    points: list[tuple[float, float]]
    text: str | None = None


@dataclass
class DxfOptions:
    presentation: dict = field(default_factory=lambda: dict(PRESENTATION_DEFAULT))
    apply_pending_renames: bool = False
    xref_name: str | None = None
    xref_fit: Similarity | None = None
    mleader_style: str = "Standard"
    mleader_height: float = 0.36
    osm_rings: list[list[tuple[float, float]]] = field(default_factory=list)


@dataclass
class DxfStats:
    inserted: int = 0
    counts: dict[str, int] = field(default_factory=dict)
    missing_blocks: list[str] = field(default_factory=list)
    renamed_blocks: list[str] = field(default_factory=list)
    geodata_removed: bool = False
    audit_errors: int = 0
    notes: list[str] = field(default_factory=list)


def scale_for(block: str, sous_type: str | None, table: dict) -> tuple[float, bool]:
    rule = table.get(block, {})
    s = rule.get("by_sous_type", {}).get(sous_type or "", rule.get("default", 1.0))
    return float(s), bool(rule.get("mirror", False))


def _rename_blocks(doc, pairs: dict[str, str], stats: DxfStats) -> None:
    """One name per block: renames old → new once, retargeting every INSERT; drops the old block if both exist."""
    for old, new in pairs.items():
        if old not in doc.blocks:
            continue
        for layout in list(doc.layouts) + list(doc.blocks):
            for e in layout.query(f'INSERT[name=="{old}"]'):
                e.dxf.name = new
        if new in doc.blocks:
            doc.blocks.delete_block(old, safe=False)
        else:
            doc.blocks.rename_block(old, new)
        stats.renamed_blocks.append(f"{old} → {new}")


def _remove_geodata(doc, stats: DxfStats) -> None:
    msp = doc.modelspace()
    geo = msp.get_geodata()
    if geo is None:
        return
    msp.block_record.get_extension_dict().discard("ACAD_GEOGRAPHICDATA")
    doc.objects.delete_entity(geo)
    stats.geodata_removed = True


def _ensure_attdefs(block) -> None:
    present = {a.dxf.tag for a in block.query("ATTDEF")}
    for i, tag in enumerate(ATTR_TAGS):
        if tag not in present:
            block.add_attdef(tag, (0, -0.2 * i), dxfattribs={"flags": 1, "height": 0.1, "layer": "0"})


def _attr_values(o: Obj) -> dict[str, str]:
    d = o.props.get("details") or {}
    v = {
        "ID": o.id, "STATUT": o.props.get("status"), "SOUS_TYPE": d.get("sous_type"), "TECHNO": d.get("technologie"),
        "CULOT": d.get("culot"), "PUISSANCE_W": d.get("puissance_W"),
        "HAUTEUR_M": o.props.get("height_m"), "REF_ETIQUETTE": d.get("ref_etiquette"),
        "SIGMA_XY_M": None if o.sigma_assumed else round(o.sigma, 3), "SOURCES": ";".join(o.sources),
        "DATE_RELEVE": (o.props.get("time") or {}).get("captured_utc"), "CIRCUIT": d.get("circuit"), "TABLEAU": d.get("tableau"),
    }
    return {k: str(x) for k, x in v.items() if x not in (None, "")}


def _add_annotation(msp, s: Shape, opts: DxfOptions, stats: DxfStats) -> None:
    text = s.text or ""
    try:
        ml = msp.add_multileader_mtext(opts.mleader_style, dxfattribs={"layer": LAYER_EQUIP})
        ml.set_content(text, char_height=opts.mleader_height)
        ml.add_leader_line(ezdxf.render.mleader.ConnectionSide.left, [s.points[0]])
        ml.build(insert=(s.points[0][0] + 1.0, s.points[0][1] + 1.0))
    except Exception as e:  # noqa: BLE001 - old DXF versions without MULTILEADER
        stats.notes.append(f"annotation {s.id} en MTEXT ({type(e).__name__})")
        msp.add_mtext(text, dxfattribs={"layer": LAYER_EQUIP, "char_height": opts.mleader_height, "insert": s.points[0]})


def _legend(doc, counts: dict[str, int], x0: float, y0: float) -> None:
    msp = doc.modelspace()
    a = {"layer": LAYER_DIAG}
    row, w = 2.0, [3.0, 3.0, 12.0]
    msp.add_mtext(LEGEND_TITLE, dxfattribs={**a, "char_height": 0.8, "insert": (x0, y0 + 1.5)})
    names = sorted(counts, key=str.casefold)
    for c, h in enumerate(["Symbol", "Count", "Name"]):
        msp.add_mtext(h, dxfattribs={**a, "char_height": 0.5, "insert": (x0 + sum(w[:c]) + 0.2, y0 - 0.3)})
    for i, name in enumerate(names):
        y = y0 - row * (i + 1)
        msp.add_blockref(name, (x0 + w[0] / 2, y - row / 2), dxfattribs=a)
        msp.add_mtext(str(counts[name]), dxfattribs={**a, "char_height": 0.5, "insert": (x0 + w[0] + 0.2, y - 0.5)})
        msp.add_mtext(name, dxfattribs={**a, "char_height": 0.5, "insert": (x0 + w[0] + w[1] + 0.2, y - 0.5)})
    total_h = row * (len(names) + 1)
    for i in range(len(names) + 2):
        msp.add_line((x0, y0 + row - row * i), (x0 + sum(w), y0 + row - row * i), dxfattribs=a)
    for c in range(4):
        msp.add_line((x0 + sum(w[:c]), y0 + row), (x0 + sum(w[:c]), y0 + row - total_h), dxfattribs=a)


def generate(template: str | Path, objects: list[Obj], shapes: list[Shape], out: str | Path, opts: DxfOptions | None = None) -> DxfStats:
    opts = opts or DxfOptions()
    stats = DxfStats()
    doc = ezdxf.readfile(str(template))  # a copy is written to `out`: the template file is never modified
    pairs = dict(ALIASES_VALIDATED) | (ALIASES_PENDING if opts.apply_pending_renames else {})
    _rename_blocks(doc, pairs, stats)
    _remove_geodata(doc, stats)
    doc.header["$INSUNITS"] = 6
    for name in (LAYER_EQUIP, LAYER_DIM, LAYER_PHOTO, LAYER_DIAG, LAYER_OSM):
        if name not in doc.layers:
            doc.layers.add(name)
    msp = doc.modelspace()

    if opts.xref_name and opts.xref_fit:
        refs = msp.query(f'INSERT[name=="{opts.xref_name}"]')
        if not refs:
            stats.notes.append(f"xref « {opts.xref_name} » absente du gabarit : recalage non appliqué")
        for ref in refs:
            base = doc.blocks.get(opts.xref_name).base_point if opts.xref_name in doc.blocks else (0, 0, 0)
            f = opts.xref_fit
            ref.dxf.insert = (*f.apply(base[0], base[1]), 0)
            ref.dxf.rotation = f.rotation_deg
            ref.dxf.xscale = ref.dxf.yscale = f.scale

    for o in objects:
        if o.block not in doc.blocks:
            stats.missing_blocks.append(f"{o.id} ({o.block})")
            continue
        _ensure_attdefs(doc.blocks.get(o.block))
        sous_type = (o.props.get("details") or {}).get("sous_type")
        s, mirror = scale_for(o.block, sous_type, opts.presentation)
        rot = o.props.get("_rotation", 0.0)
        ins = msp.add_blockref(o.block, (o.x, o.y), dxfattribs={
            "layer": LAYER_EQUIP, "xscale": -s if mirror else s, "yscale": s, "zscale": s, "rotation": rot})
        ins.add_auto_attribs(_attr_values(o))
        h = o.props.get("height_m")
        if h is not None:
            msp.add_mtext(f"H {float(h):.2f} m", dxfattribs={"layer": LAYER_DIM, "char_height": 0.5, "insert": (o.x + 0.3, o.y - 0.3)})
        stats.inserted += 1

    for s in shapes:
        if s.kind == "annotation" or s.geometry_type == "Point":
            _add_annotation(msp, s, opts, stats)
        else:
            msp.add_lwpolyline(s.points, close=s.geometry_type == "Polygon", dxfattribs={"layer": LAYER_EQUIP})
    for ring in opts.osm_rings:
        msp.add_lwpolyline(ring, close=True, dxfattribs={"layer": LAYER_OSM})

    for ins in msp.query(f'INSERT[layer=="{LAYER_EQUIP}"]'):
        stats.counts[ins.dxf.name] = stats.counts.get(ins.dxf.name, 0) + 1
    if stats.counts:
        ext = bbox.extents(msp, fast=True)
        x0 = (ext.extmax.x if ext.has_data else 0.0) + 10.0
        y0 = ext.extmax.y if ext.has_data else 0.0
        _legend(doc, stats.counts, x0, y0)
    stats.audit_errors = len(doc.audit().errors)
    doc.saveas(str(out))
    return stats


def write_counts_csv(counts: dict[str, int], path: str | Path) -> None:
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["Name", "Count"])
        for n in sorted(counts, key=str.casefold):
            w.writerow([n, counts[n]])
