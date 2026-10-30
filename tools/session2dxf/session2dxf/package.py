"""Import of a LumiSite export package: sha256 check, streaming GeoJSONSeq, last rev, name normalisation."""
from __future__ import annotations

import hashlib
import json
import atexit
import shutil
import tempfile
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

# D4, validated. Never extended without Sébastien's validation.
ALIASES_VALIDATED = {"bloc secour": "bloc secours", "tableau divisionaire": "tableau divisionnaire"}
# Proposed renames, applied only with --apply-pending-renames (once Sébastien has validated them).
ALIASES_PENDING = {"Camera": "caméra", "hublot ecl": "hublot éclairage", "lineaire fluo": "linéaire fluo", "tgbt": "TGBT"}
TO_CLASSIFY = "autre (à classer)"


@dataclass
class Package:
    root: Path
    manifest: dict
    catalog: dict
    features: list[dict] = field(default_factory=list)
    frames: dict[str, dict] = field(default_factory=dict)
    warnings: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    to_classify: list[str] = field(default_factory=list)
    out_of_catalog: list[str] = field(default_factory=list)

    @property
    def canonical_names(self) -> set[str]:
        return {e["block_name"] for e in self.catalog.get("entries", [])}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def _find_root(path: Path) -> Path:
    if path.is_file() and zipfile.is_zipfile(path):
        tmp = Path(tempfile.mkdtemp(prefix="lumisite_"))
        atexit.register(shutil.rmtree, tmp, True)
        with zipfile.ZipFile(path) as z:
            z.extractall(tmp)
        path = tmp
    if (path / "manifest.json").exists():
        return path
    for child in path.iterdir():
        if (child / "manifest.json").exists():
            return child
    raise FileNotFoundError(f"manifest.json introuvable dans {path}")


def alias_map(catalog: dict, apply_pending: bool) -> dict[str, str]:
    m = dict(ALIASES_VALIDATED)
    for e in catalog.get("entries", []):
        for a in e.get("aliases", []):
            m[a] = e["block_name"]
    if apply_pending:
        m.update(ALIASES_PENDING)
    return m


def normalise(name: str, aliases: dict[str, str]) -> str:
    return aliases.get(name, name)


def read_geojsonl(path: Path, warnings: list[str]) -> list[dict]:
    """Streams a GeoJSONSeq file and returns the current state (highest rev per id, deletes removed)."""
    current: dict[str, dict] = {}
    with open(path, encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.strip().lstrip("\x1e")
            if not line:
                continue
            try:
                feat = json.loads(line)
            except json.JSONDecodeError:
                warnings.append(f"{path.name}:{n} : ligne tronquée ignorée")
                continue
            fid = feat.get("id")
            rev = feat.get("properties", {}).get("rev", 0)
            if fid is None:
                warnings.append(f"{path.name}:{n} : Feature sans id ignorée")
                continue
            if fid not in current or rev >= current[fid]["properties"].get("rev", 0):
                current[fid] = feat
    return [f for f in current.values() if f["properties"].get("op") != "delete"]


def load_package(path: str | Path, apply_pending: bool = False, default_catalog: Path | None = None) -> Package:
    root = _find_root(Path(path))
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    cat_path = root / "catalog" / "equipment_catalog.json"
    if not cat_path.exists() and default_catalog:
        cat_path = default_catalog
    catalog = json.loads(cat_path.read_text(encoding="utf-8")) if cat_path.exists() else {"entries": []}
    pkg = Package(root, manifest, catalog)

    for f in manifest.get("files", []):
        p = root / f["path"]
        if not p.exists():
            pkg.errors.append(f"fichier manquant : {f['path']}")
        elif sha256(p) != f["sha256"]:
            pkg.errors.append(f"sha256 différent : {f['path']}")

    aliases = alias_map(catalog, apply_pending)
    canonical = pkg.canonical_names | set(aliases.values())
    for s in manifest.get("sessions", []):
        for n in s.get("truncated_lines", []):
            pkg.warnings.append(f"{s['features_file']} : ligne {n} tronquée sur l'appareil")
        fp = root / s["features_file"]
        if not fp.exists():
            pkg.errors.append(f"session sans fichier : {s['features_file']}")
            continue
        frames_path = root / s.get("frames_file", "")
        if s.get("frames_file") and frames_path.exists():
            pkg.frames[s["session_id"]] = json.loads(frames_path.read_text(encoding="utf-8"))
        for feat in read_geojsonl(fp, pkg.warnings):
            props = feat["properties"]
            props.setdefault("session_id", s["session_id"])
            props.setdefault("device_id", s["device_id"])
            if props.get("kind") == "equipment":
                original = props.get("block_name", "")
                name = normalise(original, aliases)
                props["block_name"] = name
                if original != name:
                    props["block_name_original"] = original
                if name == TO_CLASSIFY or (name.startswith("LUMI_") and name.endswith("_GENERIC")):
                    pkg.to_classify.append(f"{feat['id']} ({original})")
                    continue
                if name not in canonical:
                    pkg.out_of_catalog.append(f"{feat['id']} ({original})")
                    continue
            pkg.features.append(feat)
    return pkg
