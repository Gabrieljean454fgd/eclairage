# -*- coding: utf-8 -*-
"""Genere catalog/lumisite_catalog_2.json (90 items : 78 codes annexe B + 5 codes table LS_DWG
+ 6 SON + LS_AUTRE_A_CLASSER). Script de generation, rejouable ; le JSON produit est la source."""
import json, io, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "catalog")

CALQUE = {"ECLAIRAGE":"LUMI-ECLAIRAGE","ELEC":"LUMI-ELEC","SECOURS":"LUMI-SECOURS",
          "SECURITE":"LUMI-SECURITE","INCENDIE":"LUMI-INCENDIE","CAMERA":"LUMI-CAMERA",
          "SON":"LUMI-SON","AUTRE":"LUMI-AUTRE"}

# code, label, symbole, bloc_dwg, aliases, synonyms[, is_block]
ITEMS = [
# ---- ECLAIRAGE (en premier)
("LS_ECLAIRAGE_DOWNLIGHT","Downlight encastré au plafond","point_lumineux","spot",["spot"],["encastré","spot encastré"]),
("LS_ECLAIRAGE_SPOT_SURFACE","Spot en applique de plafond (surface)","spot_surface",None,[],["spot saillie","plafonnier spot"]),
("LS_ECLAIRAGE_REGLE_IMMERSION","Réglette / luminaire tubulaire","reglette","lineaire fluo",["lineaire fluo","réglette"],["fluo","tube"]),
("LS_ECLAIRAGE_LINEAIRE_LED","Luminaire linéaire LED suspendu","lineaire",None,[],["linéaire","profilé"]),
("LS_ECLAIRAGE_PROJECTEUR","Projecteur (halogène / décharge)","projecteur","projecteur",["projecteur","projecteur thorn contrast"],["projo"]),
("LS_ECLAIRAGE_LUSTRE","Lustre (patrimoine)","lustre","Lustre",["Lustre"],[]),
("LS_ECLAIRAGE_LANTERNE_BOUGIES","Lanterne à bougies (patrimoine)","lanterne",None,[],["bougies"]),
("LS_ECLAIRAGE_SUSPENSION","Suspension / luminaire suspendu","suspension",None,[],["suspendu","pendant"]),
("LS_ECLAIRAGE_APPLIQUE","Applique murale","applique",None,[],["murale"]),
("LS_ECLAIRAGE_BALISE_BASSE","Balise basse encastrée (éclairage indirect)","balise",None,[],["encastrée basse"]),
("LS_ECLAIRAGE_SPOT_SOL","Spot encastré au sol (uplight)","spot_sol",None,[],["uplight","sol"]),
("LS_ECLAIRAGE_PROJO_LED_FACADE","Projecteur LED de mise en valeur de façade","projecteur",None,[],["façade","projo"]),
("LS_ECLAIRAGE_HERSE","Herse / projecteurs sur rail (nef, chœur)","herse",None,[],["rail","projecteurs rail"]),
("LS_ECLAIRAGE_VITRAIL","Projecteur rasant de vitrail","vitrail",None,[],["rasant"]),
("LS_ECLAIRAGE_LAMPE_TEMPORAIRE","Éclairage temporaire (chantier, évènement)","lampe_temp",None,[],["chantier","temporaire"]),
("LS_ECLAIRAGE_POINT_LUMINEUX","Point lumineux","point_lumineux","lampe",["lampe"],["dcl","point de centre"]),
("LS_ECLAIRAGE_BOUGEOIR","Bougeoir électrifié","bougeoir","bougeoir électrifié",["bougeoir électrifié"],["chandelle"]),
("LS_ECLAIRAGE_LANTERNE_EP","Lanterne EP moderne","lanterne","Lanterne EP moderne",["Lanterne EP moderne"],["ep"]),
("LS_ECLAIRAGE_HUBLOT","Hublot éclairage","hublot","hublot ecl",["hublot ecl","hublot éclairage"],[]),
# ---- ELEC
("LS_ELEC_PRISE_2P_T_16A","Prise de courant 2P+T 16 A encastrée","prise","prise électrique",["prise électrique"],["pc","pc 16a","prise de courant"]),
("LS_ELEC_PRISE_2P_T_32A","Prise de courant 2P+T 32 A","prise",None,[],["pc 32a","triphasé"]),
("LS_ELEC_PRISE_RJ45","Prise réseau RJ45","rj45",None,[],["réseau","ethernet","informatique"]),
("LS_ELEC_INTERRUPTEUR_SIMPLE","Interrupteur simple","interrupteur","interrupteur",["interrupteur"],["inter","simple allumage"]),
("LS_ELEC_INT_VA_ET_VIENT","Interrupteur va-et-vient","va_et_vient",None,[],["va et vient","inter vv"]),
("LS_ELEC_VARIATEUR","Variateur d'intensité","variateur",None,[],["dimmer","gradateur"]),
("LS_ELEC_TABLEAU_GENERAL","Tableau général basse tension (TGBT)","tableau","tgbt",["tgbt","TGBT"],["tableau général"]),
("LS_ELEC_TABLEAU_DIVISIONNAIRE","Tableau divisionnaire","tableau_div","tableau divisionnaire",["tableau divisionnaire","tableau divisionaire"],["td","sous-tableau"]),
("LS_ELEC_COFFRET_PROTECTION","Coffret de protection","coffret",None,[],["protection"]),
("LS_ELEC_BOITE_DERIVATION","Boîte de dérivation","derivation","boîte de dérivation",["boîte de dérivation"],["boite","dérivation"]),
("LS_ELEC_INT_DIFFERENTIEL","Interrupteur différentiel 30 mA","differentiel",None,[],["diff","30ma","ddr"]),
("LS_ELEC_CHEMIN_CABLES","Chemin de câbles / caniveau","chemin_cables",None,[],["caniveau","goulotte","chemin de cable"]),
("LS_ELEC_DETECTEUR_MOUVEMENT","Détecteur de mouvement (commutation)","detecteur_mouvement",None,[],["détecteur présence","pir","commande"]),
("LS_ELEC_PDL","Point de livraison (PDL)","pdl","PDL",["PDL"],["arrivée","compteur"]),
# ---- SECOURS
("LS_SECOURS_BAES","BAES — bloc autonome d'éclairage de sécurité","baes","bloc secours",["bloc secours","bloc secour","baes"],["bloc secours","secours"]),
("LS_SECOURS_BAES_ADRESSE","BAES adressable (SATI)","baes_adresse",None,[],["sati","adressable"]),
("LS_SECOURS_BAEH","BAEH — bloc autonome d'éclairage d'habitation","baeh",None,[],["habitation"]),
("LS_SECOURS_BLOC_AUTONOME_NP","Bloc autonome non permanent","bloc_np",None,[],["non permanent"]),
("LS_SECOURS_LSC","LSC — luminaire de sécurité de cheminement (source centralisée)","lsc",None,[],["cheminement"]),
("LS_SECOURS_LSE","LSE — luminaire de sécurité d'évacuation (source centralisée)","lse",None,[],["évacuation"]),
("LS_SECOURS_CAT","CAT — centrale d'alimentation / source centralisée","cat",None,[],["source centralisée"]),
("LS_SECOURS_ASI","ASI / onduleur","asi",None,[],["onduleur","ups"]),
("LS_SECOURS_GE","Groupe électrogène","groupe_electrogene",None,[],["ge","générateur"]),
("LS_SECOURS_INVERSEUR_SOURCE","Inverseur de source","inverseur",None,[],["inverseur normal secours"]),
("LS_SECOURS_COFFRET_RELAIS","Coffret de relayage / boîtier de commande","coffret_relais",None,[],["relayage","boîtier commande"]),
("LS_SECOURS_LUMINAIRE_SUR_GE","Luminaire normal sur circuit secouru","luminaire_secouru",None,[],["circuit secouru"]),
# ---- SECURITE
("LS_SECURITE_EVACUATION","Éclairage d'évacuation (cheminement)","signal_sortie",None,[],["cheminement","sortie"]),
("LS_SECURITE_ANTI_PANIQUE","Éclairage anti-panique (surface ≥ 60 m²)","antipanique",None,[],["anti panique"]),
("LS_SECURITE_AMBIANCE_RENFORCEE","Éclairage d'ambiance renforcée (lieux de sommeil)","ambiance",None,[],["ambiance renforcée","sommeil"]),
("LS_SECURITE_REMPLACEMENT","Éclairage de remplacement","remplacement",None,[],["remplacement"]),
("LS_SECURITE_SORTIE","Signal de sortie (pictogramme sortie de secours)","signal_sortie",None,[],["sortie","pictogramme"]),
("LS_SECURITE_DIRECTION","Signal de direction (flèche directionnelle)","fleche_direction",None,[],["flèche","direction"]),
("LS_SECURITE_ISSUE","Signal d'issue de secours","signal_issue",None,[],["issue"]),
("LS_SECURITE_BALISAGE_SOL","Balisage lumineux de sol","balisage",None,[],["balisage sol"]),
("LS_SECURITE_POINT_RASSEMBLEMENT","Signal de point de rassemblement","rassemblement",None,[],["rassemblement"]),
("LS_SECURITE_PREMIERS_SECOURS","Signal premiers secours (ISO 7010 E003)","premiers_secours",None,[],["premiers secours","croix"]),
("LS_SECURITE_PICTO_EXTINCTEUR","Pictogramme extincteur (ISO 7010 F001)","picto_extincteur",None,[],["picto extincteur"]),
# ---- INCENDIE
("LS_INCENDIE_DETECTEUR_FUMEE","Détecteur automatique de fumée (DAF optique)","detecteur_fumee",None,[],["daf","fumée","optique"]),
("LS_INCENDIE_DETECTEUR_THERMIQUE","Détecteur thermique / thermovélocimétrique","detecteur_thermique",None,[],["thermovélocimétrique","thermique"]),
("LS_INCENDIE_DETECTEUR_FLAMME","Détecteur de flamme IR / UV","detecteur_flamme",None,[],["flamme","ir uv"]),
("LS_INCENDIE_DETECTEUR_LINEAIRE","Détecteur linéaire (câble / faisceau)","detecteur_lineaire",None,[],["linéaire","faisceau","câble"]),
("LS_INCENDIE_CENTRALE_INCENDIE","ECS — centrale de détection incendie","centrale_incendie",None,[],["ecs","centrale incendie"]),
("LS_INCENDIE_CMSI","CMSI — centrale de mise en sécurité incendie","cmsi",None,[],["cmsi","mise en sécurité"]),
("LS_INCENDIE_DECLENCHEUR_MANUEL","Déclencheur manuel (DM)","declencheur","Déclencheur Manuel",["Déclencheur Manuel","dm"],["déclencheur"]),
("LS_INCENDIE_ALARME_GENERALE","Avertisseur général d'alarme (AGS sonore)","alarme",None,[],["ags","alarme générale"]),
("LS_INCENDIE_ALARME_VISUELLE","Avertisseur d'alarme visuelle (flash)","flash",None,[],["flash","visuelle"]),
("LS_INCENDIE_REPETEUR","Tableau répétiteur d'alarme","repeteur",None,[],["répétiteur","tableau répétiteur"]),
("LS_INCENDIE_SIRENE","Sirène / diffuseur sonore","sirene",None,[],["sirène"]),
("LS_INCENDIE_MODULE_RELAIS","Module de commande / relais","module_relais",None,[],["relais","module"]),
("LS_INCENDIE_EXTINCTEUR","Extincteur","extincteur","extincteur",["extincteur"],[]),
("LS_INCENDIE_RIA","Robinet d'incendie armé (RIA)","ria",None,[],["robinet"]),
("LS_INCENDIE_BAAS","BAAS — bloc autonome d'alarme sonore","baas",None,[],["baas","alarme sonore"]),
# ---- CAMERA
("LS_CAMERA_DOME","Caméra dôme","camera_dome",None,[],["dôme"]),
("LS_CAMERA_BULLET","Caméra bullet","camera_bullet",None,[],["bullet","tube"]),
("LS_CAMERA_PTZ","Caméra PTZ motorisée","camera_ptz",None,[],["ptz","motorisée"]),
("LS_CAMERA_FIXE_INTERIEUR","Caméra fixe intérieure","camera","Camera",["Camera","caméra"],["fixe","intérieure"]),
("LS_CAMERA_THERMIQUE","Caméra thermique","camera_thermique",None,[],["thermique"]),
("LS_CAMERA_FISHEYE","Caméra fisheye 360","camera_fisheye",None,[],["fisheye","360"]),
("LS_CAMERA_NVR","Enregistreur NVR","enregistreur",None,[],["nvr","enregistreur"]),
("LS_CAMERA_SERVEUR_VMS","Serveur VMS","serveur",None,[],["vms","serveur vidéo"]),
("LS_CAMERA_LECTEUR_BADGE","Lecteur de badge / contrôle d'accès","badge",None,[],["badge","contrôle accès"]),
("LS_CAMERA_INTERPHONE","Interphone / visiophone","interphone",None,[],["visiophone","interphone"]),
("LS_CAMERA_CENTRALE_INTRUSION","Centrale d'alarme intrusion","centrale_intrusion",None,[],["intrusion","alarme intrusion"]),
("LS_CAMERA_DETECTEUR_IR","Détecteur infrarouge de mouvement (sûreté)","detecteur_ir",None,[],["infrarouge","pir","sûreté"]),
# ---- SON
("LS_SON_AMPLIFICATEUR","Amplificateur","amplificateur","Amplificateur",["Amplificateur"],["ampli"]),
("LS_SON_ENCEINTE","Enceinte","enceinte","enceinte",["enceinte"],[]),
("LS_SON_HAUT_PARLEUR","Haut-parleur","haut_parleur","haut-parleur",["haut-parleur"],["hp"]),
("LS_SON_MICRO_COL","Micro col de cygne","micro","micro col",["micro col"],["micro","col de cygne"]),
("LS_SON_PRISE_XLR","Prise XLR","xlr","prise xlr",["prise xlr"],[]),
("LS_SON_DIFFUSEUR_SONORE_RADIO","Diffuseur sonore radio","diffuseur","Diffuseur Sonore Radio",["Diffuseur Sonore Radio"],["diffuseur","radio"]),
# ---- AUTRE (dernier, is_block = false)
("LS_AUTRE_A_CLASSER","Autre (à classer au bureau)","autre","autre (à classer)",["autre (à classer)"],["autre","à classer"],False),
]

def fam(code):
    for f in ["ECLAIRAGE","ELEC","SECOURS","SECURITE","INCENDIE","CAMERA","SON"]:
        if code.startswith("LS_" + f + "_"):
            return f
    if code.startswith("LS_AUTRE_"):
        return "AUTRE"
    raise Exception(code)

items = []
for row in ITEMS:
    code, label, sym, bloc, aliases, syn = row[:6]
    is_block = row[6] if len(row) > 6 else True
    f = fam(code)
    items.append({"code": code, "label": label, "famille": f, "type": label,
                  "synonyms": syn, "calque": CALQUE[f], "bloc_dwg": bloc,
                  "aliases": aliases, "symbole": sym, "is_block": is_block})

out = {"version": "lumisite-catalog-2.0", "items": items}
path = os.path.join(ROOT, "lumisite_catalog_2.json")
with io.open(path, "w", encoding="utf-8") as fh:
    fh.write(json.dumps(out, ensure_ascii=False, indent=2))
    fh.write("\n")
print(len(items), "items")
print("codes trop longs:", [i["code"] for i in items if len(i["code"]) > 31])
dups = len(items) - len(set(i["code"] for i in items))
print("doublons:", dups)
