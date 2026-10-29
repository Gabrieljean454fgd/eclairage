# session2dxf — outil de bureau LumiSite (lot 11)

Paquet GeoJSON de l'appli (lot 6) → DXF au format du gabarit de Sébastien → DWG. L'appli ne produit jamais de DXF (D1, D3).

```bash
pip install -r tools/session2dxf/requirements.txt
cd tools/session2dxf
python -m session2dxf LumiSite_A.zip LumiSite_B.zip --template gabarit_copie.dxf --out releve.dxf \
  [--presentation echelles.json] [--xref "02-Plan au sol" --homologous points.csv [--free-scale]] \
  [--apply-pending-renames] [--oda /chemin/ODAFileConverter]
python -m pytest -q tests
```

Sorties : `releve.dxf`, `releve.counts.csv` (comptages de la légende), `releve.report.json` (fusions, conflits, RMS, à classer, hors catalogue, sans position, avertissements).

## Ce que fait l'outil
1. **Import** : sha256 du manifeste, `.geojsonl` lus en flux, dernière `rev` gardée, `delete` retirés, ligne tronquée signalée. Noms normalisés par les alias validés (D4) ; les 4 renommages en attente seulement avec `--apply-pending-renames`. `LUMI_*_GENERIC` et `autre (à classer)` → « à classer » ; noms inconnus → « hors catalogue ». Ces objets ne sont pas insérés.
2. **Fusion** : doublons entre appareils **différents** (même bloc, distance < 2·√(σ1²+σ2²), azimuts à moins de 20°) → moyenne en 1/σ², avec la liste des sources. Blocs différents ou visées incompatibles → **conflit listé**, les deux objets sont gardés. σ absent dans l'export (cas actuel) → `--default-sigma` (0,5 m), signalé.
3. **Lambert 93** : WGS84 → EPSG:2154 point par point (pyproj) ; convergence des méridiens ajoutée à l'azimut ; DXF en mètres (`$INSUNITS = 6`). Recalage du plan d'architecte (xref) par similitude sur 2 à 4 points homologues, échelle fixe par défaut, RMS et résidus dans le rapport. Sans plan : contours OSM fermés de `refs/osm_source.json` sur le calque `OSM`.
4. **DXF** : écrit à partir d'une copie du gabarit (version conservée, AC1015). Un INSERT par objet avec le **bloc existant** ; rotation = azimut converti ; échelles de présentation (table par défaut ou `--presentation`) ; ATTDEF invisibles ajoutés aux blocs utilisés et remplis par ATTRIB ; « H x m » sur `cote` ; formes en LWPOLYLINE ; annotations en MULTILEADER (MTEXT si la version ne le permet pas) ; « Block Legend » (Symbol / Count / Name) sur `diagnostic`, hors emprise. Renommage des blocs **une seule fois** et INSERT redirigés. GEODATA faux du gabarit supprimé ; aucun GEODATA écrit.
5. **DWG** : `--oda` lance ODA File Converter (ACAD2000).

## Limites connues
- Le gabarit réel et `eclairage03.dxf` ne sont pas dans le dépôt : l'aller-retour est testé sur un dessin **synthétique** de 293 INSERT aux 25 noms.
- Les horloges ne sont pas recalées par les chirps : la fusion est spatiale.
- Helmert sur points de contrôle OSM : fonction `geo.fit_similarity(fixed_scale=True)` prête, mais l'appli n'exporte pas encore de `control_point`.
- La correspondance taille de spot ↔ PAR20/MR16/LED reste à fournir par Sébastien (§ 15) : 1,208 par défaut.
- Pas d'écran de validation des conflits : ils sont listés dans le rapport.
- Ouverture dans AutoCAD et conversion DWG non testées ici.
