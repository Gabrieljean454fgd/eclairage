"""Dictaphone → objects (lot 12, bureau): Whisper transcription, extraction, attachment proposals, human validation.

python -m session2dxf.voice transcribe dict_1.flac --model small --out transcript_1.json
python -m session2dxf.voice propose PAQUET --dictaphone dictaphone_1.json --transcript transcript_1.json --speaker DEVICE --out proposals.json
python -m session2dxf.voice validate proposals.json --out decisions.json        # écran texte : accepter / corriger / réattribuer / rejeter
python -m session2dxf.voice apply PAQUET proposals.json decisions.json --out voice_validated.geojsonl
python -m session2dxf PAQUET --validated voice_validated.geojsonl …              # seules les propositions validées vont dans les ATTRIB
"""
from __future__ import annotations

import argparse
import copy
import json
import math
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

WINDOW_BEFORE_S = 30.0
WINDOW_AFTER_S = 60.0
MAX_DIST_M = 8.0
CONE_DEG = 35.0
THRESHOLD = 1.5
AMBIGUITY_MARGIN = 0.25

FAMILY_KEYWORDS = {
    "spot": ["spot", "dichro", "mr16", "mr 16", "par 20", "par20", "gu10", "gu 10"],
    "projecteur thorn contrast": ["thorn"],
    "projecteur": ["projecteur"],
    "bloc secours": ["bloc secours", "bloc de secours", "baes"],
    "lampe": ["lampe"], "hublot ecl": ["hublot"], "lineaire fluo": ["fluo", "réglette", "tube"],
    "interrupteur": ["interrupteur"], "prise électrique": ["prise"], "tableau divisionnaire": ["tableau divisionnaire", "tableau"],
    "tgbt": ["tgbt"], "extincteur": ["extincteur"], "Lustre": ["lustre"], "Lanterne EP moderne": ["lanterne"],
    "boîte de dérivation": ["boîte de dérivation", "boite de dérivation"],
}
DEICTIC = ["devant moi", " là", "au-dessus", "ici", "celui-ci", "celui-là"]


def _norm(t: str) -> str:
    return " " + t.lower().replace("’", "'") + " "


def extract(text: str) -> dict:
    """Optional details spoken in a segment (field names of the lot 7 Details)."""
    t = _norm(text)
    d: dict = {}
    if m := re.search(r"(\d+(?:[.,]\d+)?)\s*(?:w\b|watts?)", t):
        d["puissance_W"] = float(m.group(1).replace(",", "."))
    if m := re.search(r"(\d{4})\s*(?:k\b|kelvins?)", t):
        d["temp_couleur_K"] = int(m.group(1))
    if m := re.search(r"\b(gu ?10|gu ?5[.,]3|e ?27|e ?14|g ?13|g ?9|g ?4|r7s|b ?22)\b", t):
        d["culot"] = m.group(1).upper().replace(" ", "").replace(",", ".")
    if m := re.search(r"\b(mr ?16|mr ?11|par ?(?:16|20|30|38)|ar ?111)\b", t):
        d["sous_type"] = m.group(1).upper().replace(" ", "")
    elif "dichro" in t:
        d["sous_type"] = "dichroïque"
    for kw, tech in (("led", "LED"), ("halog", "halogène"), ("dichro", "halogène"), ("fluo", "fluorescent"), ("sodium", "sodium"), ("iodure", "iodures métalliques")):
        if re.search(rf"\b{kw}", t):
            d["technologie"] = tech
            break
    if re.search(r"\b(hs|hors service|en panne|grillée?|ne marche pas)\b", t):
        d["etat"] = "HS"
    elif re.search(r"\b(cassée?|abîmée?|fendue?)\b", t):
        d["etat"] = "endommagé"
    elif re.search(r"\b(ok|fonctionne|marche)\b", t):
        d["etat"] = "OK"
    if m := re.search(r"étiquette\s+([\w-]+)", t):
        d["ref_etiquette"] = m.group(1).upper()
    return d


def families_in(text: str) -> set[str]:
    t = _norm(text)
    found = {fam for fam, kws in FAMILY_KEYWORDS.items() if any(k in t for k in kws)}
    if "projecteur thorn contrast" in found:
        found.discard("projecteur")
    return found


@dataclass
class Segment:
    id: str
    t: float  # common time, epoch seconds (middle of the segment)
    text: str


@dataclass
class Target:
    id: str
    block: str
    t: float
    x: float
    y: float
    feature: dict = field(default_factory=dict)


@dataclass
class Track:
    t: float
    x: float
    y: float
    heading_deg: float | None = None


def iso_to_s(s: str) -> float:
    return datetime.fromisoformat(s.replace("Z", "+00:00")).timestamp()


def speaker_at(tracks: list[Track], t: float) -> Track | None:
    if not tracks:
        return None
    best = min(tracks, key=lambda k: abs(k.t - t))
    return best if abs(best.t - t) <= 5.0 else None


def score(seg: Segment, obj: Target, tracks: list[Track]) -> float | None:
    if not (seg.t - WINDOW_BEFORE_S <= obj.t <= seg.t + WINDOW_AFTER_S):
        return None
    dt = abs(obj.t - seg.t)
    s = 2.0 * (1.0 - dt / WINDOW_BEFORE_S) if dt <= WINDOW_BEFORE_S else 0.0  # the phrase is usually said < 30 s from the pose
    sp = speaker_at(tracks, seg.t)
    if sp is not None:
        d = math.hypot(obj.x - sp.x, obj.y - sp.y)
        if d > MAX_DIST_M:
            return None
        s += 1.0 - d / MAX_DIST_M
        if sp.heading_deg is not None and any(k in _norm(seg.text) for k in DEICTIC):
            bearing = math.degrees(math.atan2(obj.x - sp.x, obj.y - sp.y)) % 360
            if min(abs(bearing - sp.heading_deg) % 360, 360 - abs(bearing - sp.heading_deg) % 360) <= CONE_DEG:
                s += 0.5
    fams = families_in(seg.text)
    if fams:
        if obj.block not in fams:
            return None  # another family was named: never attach to this object
        s += 1.0
    return s


def propose(segments: list[Segment], objects: list[Target], tracks: list[Track]) -> list[dict]:
    out = []
    for seg in segments:
        cands = sorted(((o, sc) for o in objects if (sc := score(seg, o, tracks)) is not None), key=lambda p: -p[1])
        fams = families_in(seg.text)
        p = {"segment_id": seg.id, "t": seg.t, "text": seg.text, "extracted": extract(seg.text),
             "candidates": [{"id": o.id, "block": o.block, "score": round(sc, 3)} for o, sc in cands[:5]]}
        if cands and cands[0][1] >= THRESHOLD:
            if len(cands) > 1 and cands[0][1] - cands[1][1] < AMBIGUITY_MARGIN:
                p["status"], p["object_id"] = "à choisir", None
            else:
                p["status"], p["object_id"] = "proposé", cands[0][0].id
        elif fams and not any(o.block in fams for o, _ in cands):
            p["status"], p["object_id"] = "objet non relevé ?", None
        else:
            p["status"], p["object_id"] = "aucun", None
        out.append(p)
    return out


def apply_decisions(proposals: list[dict], decisions: list[dict], features: dict[str, dict]) -> list[dict]:
    """accept / correct / reassign → a rev+1 update of the target Feature (source "voice", segment_id); reject → nothing."""
    by_seg = {p["segment_id"]: p for p in proposals}
    out: dict[str, dict] = {}
    for d in decisions:
        p = by_seg.get(d["segment_id"])
        if p is None or d["action"] == "reject":
            continue
        oid = d.get("object_id") if d["action"] == "reassign" else d.get("object_id") or p.get("object_id")
        if not oid or oid not in features:
            continue
        base = out.get(oid) or features[oid]
        f = copy.deepcopy(base)
        pr = f["properties"]
        values = d.get("values") if d["action"] == "correct" else p["extracted"]
        pr["details"] = {**(pr.get("details") or {}), **(values or {})}
        pr["note"] = " | ".join(x for x in (pr.get("note"), p["text"]) if x)
        pr.update(rev=pr.get("rev", 1) + 1, op="update", source="voice", segment_id=p["segment_id"])
        out[oid] = f
    return list(out.values())


def subtype_stats(features: list[dict]) -> dict[str, dict[str, int]]:
    st: dict[str, dict[str, int]] = {}
    for f in features:
        p = f["properties"]
        if p.get("source") != "voice":
            continue
        s = (p.get("details") or {}).get("sous_type") or "?"
        fam = st.setdefault(p.get("block_name", "?"), {})
        fam[s] = fam.get(s, 0) + 1
    return st


def segments_from(transcript: dict, dictaphone: dict, clock_offset_s: float = 0.0) -> list[Segment]:
    t0 = iso_to_s(dictaphone["start_utc"]) + clock_offset_s
    return [Segment(f"{Path(dictaphone['file']).stem}#{s['id']}", t0 + (s["start"] + s["end"]) / 2, s["text"].strip())
            for s in transcript["segments"]]


def transcribe(audio: str, model: str = "small", language: str = "fr") -> dict:
    """Local Whisper (openai-whisper, MIT). Nothing leaves the machine."""
    try:
        import whisper  # type: ignore
    except ImportError as e:  # pragma: no cover
        raise SystemExit("Whisper absent : pip install openai-whisper (ou whisper.cpp), traitement local uniquement") from e
    r = whisper.load_model(model).transcribe(audio, language=language, word_timestamps=True)
    return {"model": f"whisper-{model}", "language": language,
            "segments": [{"id": s["id"], "start": s["start"], "end": s["end"], "text": s["text"],
                          "words": [{"w": w["word"], "start": w["start"], "end": w["end"]} for w in s.get("words", [])]} for s in r["segments"]]}


def _targets_and_tracks(pkg, speaker: str | None):
    from . import geo
    objs, tracks = [], []
    for f in pkg.features:
        p, g = f["properties"], f.get("geometry")
        if not g or g.get("type") != "Point":
            continue
        x, y = geo.lonlat_to_l93(*g["coordinates"][:2])
        t = iso_to_s((p.get("time") or {})["captured_utc"])
        if p.get("kind") == "equipment":
            objs.append(Target(f["id"], p["block_name"], t, x, y, f))
        elif p.get("kind") == "track" and (speaker is None or p.get("device_id") == speaker):
            tracks.append(Track(t, x, y, p.get("heading_deg")))
    return objs, tracks


def main(argv=None) -> int:
    from .package import load_package
    ap = argparse.ArgumentParser(prog="session2dxf.voice")
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("transcribe"); a.add_argument("audio"); a.add_argument("--model", default="small"); a.add_argument("--out", required=True)
    b = sub.add_parser("propose"); b.add_argument("package"); b.add_argument("--dictaphone", required=True); b.add_argument("--transcript", required=True)
    b.add_argument("--speaker"); b.add_argument("--clock-offset-s", type=float, default=0.0); b.add_argument("--out", required=True)
    c = sub.add_parser("validate"); c.add_argument("proposals"); c.add_argument("--out", required=True)
    d = sub.add_parser("apply"); d.add_argument("package"); d.add_argument("proposals"); d.add_argument("decisions"); d.add_argument("--out", required=True)
    args = ap.parse_args(argv)
    if args.cmd == "transcribe":
        Path(args.out).write_text(json.dumps(transcribe(args.audio, args.model), ensure_ascii=False, indent=1), encoding="utf-8")
    elif args.cmd == "propose":
        pkg = load_package(args.package)
        objs, tracks = _targets_and_tracks(pkg, args.speaker)
        segs = segments_from(json.loads(Path(args.transcript).read_text()), json.loads(Path(args.dictaphone).read_text()), args.clock_offset_s)
        Path(args.out).write_text(json.dumps(propose(segs, objs, tracks), ensure_ascii=False, indent=1), encoding="utf-8")
    elif args.cmd == "validate":
        decisions = []
        for p in json.loads(Path(args.proposals).read_text()):
            print(f"\n[{p['segment_id']}] « {p['text']} »\n  {p['status']} → {p['object_id']}  valeurs {p['extracted']}\n  candidats {p['candidates']}")
            r = input("  [a]ccepter / [c]orriger / [r]éattribuer / [x] rejeter / [s]auter : ").strip().lower()
            if r == "a":
                decisions.append({"segment_id": p["segment_id"], "action": "accept", "object_id": p["object_id"]})
            elif r == "c":
                decisions.append({"segment_id": p["segment_id"], "action": "correct", "object_id": p["object_id"], "values": json.loads(input("  valeurs JSON : "))})
            elif r == "r":
                decisions.append({"segment_id": p["segment_id"], "action": "reassign", "object_id": input("  id de l'objet : ").strip()})
            elif r == "x":
                decisions.append({"segment_id": p["segment_id"], "action": "reject"})
        Path(args.out).write_text(json.dumps(decisions, ensure_ascii=False, indent=1), encoding="utf-8")
    else:
        pkg = load_package(args.package)
        feats = {f["id"]: f for f in pkg.features}
        upd = apply_decisions(json.loads(Path(args.proposals).read_text()), json.loads(Path(args.decisions).read_text()), feats)
        Path(args.out).write_text("".join(json.dumps(f, ensure_ascii=False) + "\n" for f in upd), encoding="utf-8")
        print(json.dumps(subtype_stats(upd), ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
