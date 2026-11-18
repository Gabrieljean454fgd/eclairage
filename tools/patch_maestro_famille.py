import io

FILES = [
    "maj/maj_avant.yaml", "maj/v6_releve.yaml", "scenario_p0_reprise_session.yaml",
    "scenario_p2_annoter_deplacer.yaml", "scenario_p2_photo_implantation.yaml",
    "scenario_v6_export_medias.yaml", "v176/releve_au_centre.yaml"]

OLD_LOOP = 'notVisible: { id: "session_annoter_famille_Amplificateur" }'
NEW_LOOP = 'notVisible: { id: "session_releve_categorie_SON" }'
OLD_TAP = '- tapOn: { id: "session_annoter_famille_Amplificateur" }'
NEW_TAP = (
    '- tapOn: { id: "session_releve_categorie_SON" }\n'
    '- repeat:\n'
    '    while: { notVisible: { id: "session_releve_type_LS_SON_AMPLIFICATEUR" } }\n'
    '    times: 8\n'
    '    commands:\n'
    '      - swipe: { start: "50%,74%", end: "50%,58%", duration: 600 }\n'
    '- tapOn: { id: "session_releve_type_LS_SON_AMPLIFICATEUR" }')

for f in FILES:
    p = ".maestro/" + f
    s = io.open(p, encoding="utf-8").read()
    n = s.replace(OLD_LOOP, NEW_LOOP).replace(OLD_TAP, NEW_TAP)
    if n != s:
        io.open(p, "w", encoding="utf-8", newline="\n").write(n)
        print("patché", f)
