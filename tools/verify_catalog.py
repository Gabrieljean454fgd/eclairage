# -*- coding: utf-8 -*-
"""Verifications croisees du catalogue 2.0 (miroir des tests JVM)."""
import json, re, csv, unicodedata, os
from collections import Counter

ROOT = os.path.join(os.path.dirname(__file__), "..")
c = json.load(open(os.path.join(ROOT, "catalog/lumisite_catalog_2.json"), encoding="utf-8"))
print(Counter(i["famille"] for i in c["items"]))

kt = open(os.path.join(ROOT, "core/src/main/kotlin/app/lumisite/core/catalog/LsSymbols.kt"), encoding="utf-8").read()
ids = set(re.findall(r'"([a-z_0-9]+)" to listOf', kt))
used = set(i["symbole"] for i in c["items"])
print("symbols manquants:", used - ids)

rows = list(csv.DictReader(open(os.path.join(ROOT, "catalog/table_ls_dwg.csv"), encoding="utf-8")))
dwg = {r["nom_bloc_DWG"] for r in rows}
known = {i["bloc_dwg"] for i in c["items"] if i["bloc_dwg"]}
print("dwg manquants:", dwg - known)
codes = {i["code"] for i in c["items"]}
print("codes CSV absents:", {r["code"] for r in rows} - codes)

o = json.load(open(os.path.join(ROOT, "catalog/equipment_catalog.json"), encoding="utf-8"))
old_names = [n for e in o["entries"] for n in [e["block_name"]] + e.get("aliases", [])]
allalias = set()
for i in c["items"]:
    allalias.update(i["aliases"])
    if i["bloc_dwg"]:
        allalias.add(i["bloc_dwg"])

def norm(s):
    return "".join(ch for ch in unicodedata.normalize("NFD", s.lower()) if unicodedata.category(ch) != "Mn").strip()

al = {norm(a) for a in allalias}
print("anciens non resolus:", [n for n in old_names if norm(n) not in al])
