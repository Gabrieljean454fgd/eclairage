import json
import math
import random
from types import SimpleNamespace

import ezdxf
import pytest

from helpers import DWG_NAMES, SBC, dxf_to_features, feature, make_package, make_template
from session2dxf import geo
from session2dxf.cli import collect, run
from session2dxf.dxf import DxfOptions, generate
from session2dxf.merge import merge
from session2dxf.package import load_package

PRES = {"spot": {"default": 1.208, "by_sous_type": {"A": 1.208, "B": 2.415, "C": 3.614}},
        "extincteur": {"default": 0.6}, "tgbt": {"default": 1.417}}


def args(tmp_path, pkgs, template, **kw):
    a = dict(packages=[str(p) for p in pkgs], template=str(template), out=str(tmp_path / "out.dxf"), presentation=None, xref=None,
             homologous=None, free_scale=False, no_osm=True, apply_pending_renames=False, default_sigma=0.5, oda=None)
    a.update(kw)
    return SimpleNamespace(**a)


def ins_by_id(path):
    doc = ezdxf.readfile(str(path))
    return doc, {i.get_attrib_text("ID"): i for i in doc.modelspace().query('INSERT[layer=="RES - Elec"]')}


def test_roundtrip_293_inserts(tmp_path):
    """eclairage03-like drawing: 293 INSERT of the 25 blocks → GeoJSON → DXF regenerated from the empty template."""
    rnd = random.Random(3)
    x0, y0 = geo.lonlat_to_l93(*SBC)
    src = tmp_path / "eclairage03_like.dxf"
    make_template(src)
    doc = ezdxf.readfile(str(src)); msp = doc.modelspace()
    scales = {"spot": [1.208, 2.415, 3.614], "extincteur": [0.6], "tgbt": [1.417], "projecteur thorn contrast": [0.571]}
    for i in range(293):
        n = DWG_NAMES[i % 25]
        s = rnd.choice(scales.get(n, [1.0]))
        msp.add_blockref(n, (x0 + rnd.uniform(-60, 60), y0 + rnd.uniform(-60, 60)),
                         dxfattribs={"layer": "RES - Elec", "rotation": rnd.uniform(0, 360), "xscale": s, "yscale": s})
    doc.saveas(str(src))
    feats = dxf_to_features(src, {("spot", 1.208): "A", ("spot", 2.415): "B", ("spot", 3.614): "C"})
    pkg = make_package(tmp_path, {("devA", "s1"): feats})
    presentation = tmp_path / "pres.json"; presentation.write_text(json.dumps(PRES))
    rep = run(args(tmp_path, [pkg], make_template(tmp_path / "gabarit.dxf"), presentation=str(presentation)))
    assert rep["inserts"] == 293 and rep["audit_erreurs"] == 0 and not rep["erreurs"]
    orig = list(ezdxf.readfile(str(src)).modelspace().query('INSERT[layer=="RES - Elec"]'))
    out_doc, out = ins_by_id(tmp_path / "out.dxf")
    for i, o in enumerate(orig):
        r = out[f"o{i}"]
        assert math.dist((o.dxf.insert.x, o.dxf.insert.y), (r.dxf.insert.x, r.dxf.insert.y)) < 1e-3
        assert abs((o.dxf.rotation - r.dxf.rotation + 180) % 360 - 180) < 1e-6
        assert r.dxf.xscale == pytest.approx(o.dxf.xscale)
        canon = {"bloc secour": "bloc secours", "tableau divisionaire": "tableau divisionnaire"}.get(o.dxf.name, o.dxf.name)
        assert r.dxf.name == canon
    assert "bloc secour" not in out_doc.blocks and "bloc secours" in out_doc.blocks
    assert sum(rep["comptages"].values()) == 293 and len(rep["comptages"]) == 25
    assert out_doc.dxfversion == "AC1015" and out_doc.header["$INSUNITS"] == 6


def test_two_devices_ten_duplicates_merge_and_conflicts(tmp_path):
    x0, y0 = geo.lonlat_to_l93(*SBC)
    a, b = [], []
    for i in range(10):
        lon, lat = geo.l93_to_lonlat(x0 + 5 * i, y0)
        lon2, lat2 = geo.l93_to_lonlat(x0 + 5 * i + 0.3, y0 + 0.2)
        a.append(feature(f"a{i}", "spot", lon, lat, device="devA"))
        b.append(feature(f"b{i}", "spot", lon2, lat2, device="devB", session="s2"))
    lon, lat = geo.l93_to_lonlat(x0 + 100, y0)
    a.append(feature("ac", "lampe", lon, lat, device="devA"))
    b.append(feature("bc", "spot", lon, lat, device="devB", session="s2"))
    pa = make_package(tmp_path / "A", {("devA", "s1"): a}); pb = make_package(tmp_path / "B", {("devB", "s2"): b})
    objs, _, _ = collect([load_package(pa), load_package(pb)])
    res = merge(objs)
    assert len(res.fusions) == 10
    assert len(res.conflicts) == 1 and res.conflicts[0]["type"] == "familles différentes"
    assert len(res.objects) == 12
    m = next(o for o in res.objects if o.id == "a0")
    assert m.sources == ["a0", "b0"] and m.x == pytest.approx(x0 + 0.15, abs=1e-6)


def test_same_device_never_merged(tmp_path):
    lon, lat = SBC
    p = make_package(tmp_path, {("devA", "s1"): [feature("a", "spot", lon, lat), feature("b", "spot", lon, lat)]})
    assert len(merge(collect([load_package(p)])[0]).objects) == 2


def test_alias_one_block_name(tmp_path):
    lon, lat = SBC
    p = make_package(tmp_path, {("devA", "s1"): [feature("a", "bloc secour", lon, lat), feature("b", "bloc secours", lon + 1e-4, lat)]})
    t = make_template(tmp_path / "t.dxf", names=DWG_NAMES + ["bloc secours"])
    rep = run(args(tmp_path, [p], t))
    doc = ezdxf.readfile(str(tmp_path / "out.dxf"))
    assert "bloc secour" not in doc.blocks and rep["comptages"] == {"bloc secours": 2}
    legend_names = [m.text for m in doc.modelspace().query('MTEXT[layer=="diagnostic"]')]
    assert "bloc secours" in legend_names and "bloc secour" not in legend_names


def test_empty_session_truncated_line_attrib_polyline_no_position(tmp_path):
    lon, lat = SBC
    empty = make_package(tmp_path / "E", {("devA", "s0"): []})
    rep = run(args(tmp_path, [empty], make_template(tmp_path / "t.dxf")))
    assert rep["inserts"] == 0 and rep["audit_erreurs"] == 0
    assert ezdxf.readfile(str(tmp_path / "out.dxf")).audit().has_errors is False

    poly = {"type": "Feature", "id": "z1", "geometry": {"type": "Polygon", "coordinates": [[[lon, lat], [lon + 1e-4, lat], [lon + 1e-4, lat + 1e-4], [lon, lat]]]},
            "properties": {"rev": 1, "op": "add", "kind": "zone", "shape": "polygon", "style": {"fill": None}}}
    feats = [feature("a", "spot", lon, lat, details={"sous_type": "x", "puissance_W": 50}), feature("np", "lampe", None, None), poly]
    p = make_package(tmp_path / "P", {("devA", "s1"): feats}, extra_lines={("devA", "s1"): '{"type":"Feature","id":"cut'})
    rep = run(args(tmp_path, [p], make_template(tmp_path / "t2.dxf")))
    assert any("tronquée" in w for w in rep["avertissements"])
    assert rep["sans_position"] and rep["inserts"] == 1
    doc, ins = ins_by_id(tmp_path / "out.dxf")
    assert ins["a"].get_attrib_text("PUISSANCE_W") == "50" and ins["a"].get_attrib("ID").is_invisible
    pl = doc.modelspace().query("LWPOLYLINE").first
    assert pl.closed and pl.dxf.flags & 1 == 1
    assert len(doc.modelspace().query("SOLID HATCH")) == 0


def test_sha_mismatch_and_out_of_catalog(tmp_path):
    lon, lat = SBC
    p = make_package(tmp_path, {("devA", "s1"): [feature("a", "LUMI_SPOT_GENERIC", lon, lat), feature("b", "inconnu", lon, lat)]})
    (p / "sessions/devA/s1.frames.json").write_text("{}")
    pkg = load_package(p)
    assert any("sha256" in e for e in pkg.errors)
    assert pkg.to_classify and pkg.out_of_catalog and not pkg.features


def test_last_rev_wins_and_delete(tmp_path):
    lon, lat = SBC
    feats = [feature("a", "spot", lon, lat), feature("a", "lampe", lon, lat, rev=2, op="update"),
             feature("b", "spot", lon, lat), feature("b", "spot", None, None, rev=2, op="delete")]
    pkg = load_package(make_package(tmp_path, {("devA", "s1"): feats}))
    assert [(f["id"], f["properties"]["block_name"]) for f in pkg.features] == [("a", "lampe")]


def test_similarity_fit_and_xref_registration(tmp_path):
    plan = [(0, 0), (10, 0), (10, 5), (0, 5)]
    rot = math.radians(30)
    l93 = [(500000 + 2 * (x * math.cos(rot) - y * math.sin(rot)), 6200000 + 2 * (x * math.sin(rot) + y * math.cos(rot))) for x, y in plan]
    l93[2] = (l93[2][0] + 0.01, l93[2][1])
    f = geo.fit_similarity(plan, l93)
    assert f.scale == pytest.approx(2, abs=1e-3) and f.rotation_deg == pytest.approx(30, abs=0.05) and 0 < f.rms < 0.01
    t = make_template(tmp_path / "t.dxf")
    generate(t, [], [], tmp_path / "o.dxf", DxfOptions(xref_name="02-Plan au sol", xref_fit=f))
    ref = ezdxf.readfile(str(tmp_path / "o.dxf")).modelspace().query('INSERT[name=="02-Plan au sol"]').first
    assert ref.dxf.insert.x == pytest.approx(f.tx) and ref.dxf.rotation == pytest.approx(f.rotation_deg)


def test_wrong_geodata_removed(tmp_path):
    t = make_template(tmp_path / "t.dxf", version="R2010", geodata=True)
    assert ezdxf.readfile(str(t)).modelspace().get_geodata() is not None
    st = generate(t, [], [], tmp_path / "o.dxf")
    assert st.geodata_removed and ezdxf.readfile(str(tmp_path / "o.dxf")).modelspace().get_geodata() is None


def test_convergence_and_rotation():
    c = geo.convergence_deg(*SBC)
    assert 0.5 < abs(c) < 3
    assert geo.azimuth_to_rotation(0.0, 0.0) == 90.0 and geo.azimuth_to_rotation(90.0, 0.0) == 0.0
