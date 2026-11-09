#!/usr/bin/env python3
"""Relit le ZIP produit par scenario_v6_export_medias (retour Pixel du 09/11, point 4) :
media/ contient la photo, sa fiche JSON à côté (sidecar avec les tags du relevé) et au moins un WAV ;
chaque chemin photo de releves.geojson désigne une entrée du ZIP. Code de sortie non nul sinon."""
import json, sys, zipfile

z = zipfile.ZipFile(sys.argv[1])
names = set(z.namelist())
errors = []
photos = sorted(n for n in names if n.startswith("media/") and n.lower().endswith((".jpg", ".jpeg")))
wavs = sorted(n for n in names if n.startswith("media/") and n.endswith(".wav"))
if not photos: errors.append("aucune photo dans media/")
if not wavs: errors.append("aucun WAV dans media/")
for p in photos:
    if p + ".json" not in names: errors.append(f"fiche JSON absente pour {p}"); continue
    side = json.loads(z.read(p + ".json"))
    if side.get("photo") != p: errors.append(f"{p}.json : champ photo = {side.get('photo')}")
    if not (side.get("releve") or side.get("releve_journal")): errors.append(f"{p}.json : aucun tag de relevé")
fc = json.loads(z.read("releves.geojson"))
refs = [ph.get("path") for f in fc.get("features", []) for ph in (f.get("properties") or {}).get("photos") or []]
if not refs: errors.append("releves.geojson : aucune photo référencée")
for r in refs:
    if r not in names: errors.append(f"releves.geojson : {r} absent du ZIP")
print(f"photos={photos} wav={wavs} references_geojson={refs}")
for e in errors: print("ERREUR :", e)
sys.exit(1 if errors else 0)
