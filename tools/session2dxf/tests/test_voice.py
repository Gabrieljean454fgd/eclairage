import random

import numpy as np

from helpers import SBC, feature, make_package
from session2dxf import audio, geo
from session2dxf.cli import apply_validated
from session2dxf.package import load_package
from session2dxf.voice import Segment, Target, Track, apply_decisions, extract, propose, subtype_stats

PHRASE = "spot dichroïque MR16, 50 watts"


def test_extract_details():
    d = extract("Spot dichroïque MR16, 50 watts, culot GU5.3, 3000 K, étiquette A12, hors service")
    assert d == {"puissance_W": 50.0, "temp_couleur_K": 3000, "culot": "GU5.3", "sous_type": "MR16", "technologie": "halogène",
                 "etat": "HS", "ref_etiquette": "A12"}
    assert extract("projecteur LED 30 W")["technologie"] == "LED"


def test_phrase_attached_to_right_spot_at_least_8_of_10():
    """Synthetic visits (not a real 30 min visit): spot posed < 30 s from the phrase, other spots 30–60 s later, lamps around."""
    ok = 0
    for trial in range(10):
        rnd = random.Random(trial)
        t0 = 1_000_000.0
        target = Target("spot-ok", "spot", t0 + rnd.uniform(-25, 25), rnd.uniform(-2, 2), rnd.uniform(1, 3))
        # sequential survey: the other spots are posed at another moment (30–60 s away), anywhere within 8 m
        others = [Target(f"spot{i}", "spot", t0 + rnd.uniform(31, 60), rnd.uniform(-8, 8), rnd.uniform(-8, 8)) for i in range(2)]
        others += [Target(f"lampe{i}", "lampe", t0 + rnd.uniform(-20, 20), rnd.uniform(-3, 3), rnd.uniform(-3, 3)) for i in range(3)]
        tracks = [Track(t0, 0.0, 0.0, 0.0)]
        p = propose([Segment("s", t0, PHRASE)], [target] + others, tracks)[0]
        ok += p["object_id"] == "spot-ok"
    assert ok >= 8, ok


def test_ambiguous_and_missing_object():
    t0 = 0.0
    a, b = Target("a", "spot", t0, 1, 1), Target("b", "spot", t0, 1, 1.01)
    assert propose([Segment("s", t0, PHRASE)], [a, b], [Track(t0, 0, 0)])[0]["status"] == "à choisir"
    assert propose([Segment("s", t0, "extincteur à côté de la porte")], [Target("l", "lampe", t0, 1, 1)], [Track(t0, 0, 0)])[0]["status"] == "objet non relevé ?"
    assert propose([Segment("s", t0, PHRASE)], [Target("far", "spot", t0, 30, 30)], [Track(t0, 0, 0)])[0]["object_id"] is None


def test_only_validated_proposals_reach_the_dxf_attributes(tmp_path):
    lon, lat = SBC
    p = make_package(tmp_path, {("devA", "s1"): [feature("a", "spot", lon, lat), feature("b", "spot", lon + 1e-4, lat)]})
    pkg = load_package(p)
    feats = {f["id"]: f for f in pkg.features}
    props = [{"segment_id": "d1#0", "text": PHRASE, "extracted": extract(PHRASE), "object_id": "a"},
             {"segment_id": "d1#1", "text": "spot GU10 35 W", "extracted": extract("spot GU10 35 W"), "object_id": "b"}]
    upd = apply_decisions(props, [{"segment_id": "d1#0", "action": "accept"}, {"segment_id": "d1#1", "action": "reject"}], feats)
    assert len(upd) == 1 and upd[0]["id"] == "a"
    pr = upd[0]["properties"]
    assert pr["rev"] == 2 and pr["source"] == "voice" and pr["segment_id"] == "d1#0" and pr["details"]["puissance_W"] == 50.0
    assert subtype_stats(upd) == {"spot": {"MR16": 1}}
    v = tmp_path / "v.geojsonl"
    import json
    v.write_text(json.dumps(upd[0]) + "\n")
    apply_validated([pkg], str(v))
    by = {f["id"]: f for f in pkg.features}
    assert by["a"]["properties"]["details"]["sous_type"] == "MR16" and "details" not in by["b"]["properties"]


def test_reassign_and_correct():
    f = {"x": {"id": "x", "properties": {"rev": 1}}, "y": {"id": "y", "properties": {"rev": 3}}}
    props = [{"segment_id": "s", "text": "t", "extracted": {"culot": "E27"}, "object_id": "x"}]
    u = apply_decisions(props, [{"segment_id": "s", "action": "reassign", "object_id": "y"}], f)
    assert u[0]["id"] == "y" and u[0]["properties"]["rev"] == 4
    u = apply_decisions(props, [{"segment_id": "s", "action": "correct", "values": {"culot": "E14"}}], f)
    assert u[0]["properties"]["details"] == {"culot": "E14"}


def test_chirps_found_in_long_recording(tmp_path):
    sr = audio.SR
    rng = np.random.default_rng(1)
    x = (rng.normal(0, 0.02, sr * 12)).astype(np.float32)
    c = audio.chirp()
    at = [sr * 1 + 123, sr * 5 + 7, sr * 9 + 4000]
    for i in at:
        x[i:i + len(c)] += 0.3 * c
    path = tmp_path / "dict_1.wav"
    import wave
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes((x * 32767).astype(np.int16).tobytes())
    found = audio.detect_chirps(audio.read_audio(path))
    assert [f["sample_index"] for f in found] == at
    flac = tmp_path / "dict_1.flac"
    import subprocess
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(path), str(flac)], check=True)
    assert [f["sample_index"] for f in audio.detect_chirps(audio.read_audio(flac))] == at


def test_values_outside_bounds_are_not_applied():
    from session2dxf import voice
    d = voice.extract("spot led 9000 watts 20000 kelvins")
    assert "puissance_W" not in d and "temp_couleur_K" not in d and len(d["hors_bornes"]) == 2
    assert voice.extract("spot 12 W 3000 K")["puissance_W"] == 12.0


def test_accept_without_object_is_an_error():
    import pytest
    from session2dxf import voice
    props = [{"segment_id": "s1", "text": "spot", "status": "à choisir", "object_id": None, "extracted": {}}]
    with pytest.raises(ValueError, match="réattribuer"):
        voice.apply_decisions(props, [{"segment_id": "s1", "action": "accept", "object_id": None}], {})


def test_clock_offset_from_chirps():
    from session2dxf import voice
    r = voice.clock_offset_from_chirps([100.0, 160.2, 400.0], [102.5, 162.6, 1000.0])
    assert r["chirps_apparies"] == 2 and abs(r["offset_s"] - 2.45) < 1e-9
    assert voice.clock_offset_from_chirps([1.0], [50.0]) is None
