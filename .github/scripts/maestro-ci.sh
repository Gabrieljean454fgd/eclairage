#!/usr/bin/env bash
# Les parcours de .maestro/ (racine) sont répartis sur deux émulateurs.
# Les tests de conservation des données tournent sur un troisième émulateur.
set -u
APK="$1"
suite="${2:-tout}"
case "$suite" in
  tout|parcours-1|parcours-2|mises-a-jour) ;;
  *) echo "Suite Maestro inconnue : $suite" >&2; exit 2 ;;
esac

export MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
mkdir -p maestro-out || exit 1
: > maestro-out/resume.md
adb install -r "$APK" || exit 1
# Reproduit le cas du Pixel 7 réel : en-tête AR de Session à sa taille maximale (bloc caméra simulé, APK debug seulement).
adb shell settings put global lumisite_simuler_entete_ar 1 || exit 1
# Navigation à 3 boutons, comme le Pixel 7 de test : un bouton dessiné sous la barre de navigation fait alors échouer l'appui au centre.
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton || true
sleep 3
echo "navigation_mode=$(adb shell settings get secure navigation_mode) (0 = 3 boutons)"

fail=0
record() {
  printf '%s\n' "$1" | tee -a maestro-out/resume.md
}

run_scenarios() {
  local f name ok attempt z index=0 total=0
  shopt -s nullglob
  local flows=(.maestro/*.yaml)
  for f in "${flows[@]}"; do
    index=$((index + 1))
    case "$suite:$((index % 2))" in
      parcours-1:0|parcours-2:1) continue ;;
    esac
    total=$((total + 1))
    name=$(basename "$f" .yaml)
    ok=0
    for attempt in 1 2; do
      echo "Maestro : $name (essai $attempt)"
      # Le démarrage à froid peut laisser un écran noir sur émulateur. Chaque parcours repart d'une application vide.
      adb shell am force-stop app.lumisite.android; adb shell pm clear app.lumisite.android >/dev/null; adb shell input keyevent KEYCODE_HOME; sleep 2
      if maestro test "$f" --format junit --output "maestro-out/${name}-${attempt}.xml" --debug-output "maestro-out/${name}-${attempt}" > "maestro-out/${name}-${attempt}.log" 2>&1; then
        ok=1; break
      fi
    done
    if [ "$ok" = 1 ]; then
      record "- ✅ ${name} (essai ${attempt})"
    else
      record "- ❌ ${name} (2 essais)"
      fail=1
    fi
    # Retour Pixel du 09/11 (point 4) : le ZIP copié dans Documents/LumiSite (conservé par pm clear) est relu hors de l'appli.
    if [ "$name" = scenario_v6_export_medias ] && [ "$ok" = 1 ]; then
      z=$(adb shell "ls -t /sdcard/Documents/LumiSite/*/*/exports/*.zip 2>/dev/null" | tr -d '\r' | grep -i medias | head -1)
      if [ -n "$z" ] && adb pull "$z" maestro-out/export_medias.zip >/dev/null && python3 .github/scripts/verifier_export_medias.py maestro-out/export_medias.zip > maestro-out/export_medias_zip.log 2>&1; then
        record "- ✅ ZIP relu : media/ (photo, fiche JSON, WAV) et chemins du GeoJSON"
      else
        record "- ❌ ZIP de scenario_v6_export_medias non conforme ou introuvable (maestro-out/export_medias_zip.log)"
        fail=1
      fi
    fi
  done
  if [ "$total" = 0 ]; then
    record "- ❌ Aucun parcours Maestro trouvé pour $suite"
    fail=1
  fi
}

run_upgrade_tests() {
  # LOT 1.5 : une session créée avec la dernière pré-release de la même étape doit être lisible et exportable après mise à jour.
  local n_etape prev_tag prev_apk ok attempt
  n_etape=$(basename "$APK" | sed -nE 's/^lumisite-etape([0-9]+)-.*/\1/p')
  prev_tag=$(gh release list --limit 50 --json tagName -q ".[] | .tagName" 2>/dev/null | grep -E "^etape${n_etape}-" | head -1)
  if [ -n "$prev_tag" ] && gh release download "$prev_tag" -p '*.apk' -D prev >/dev/null 2>&1; then
    prev_apk=$(ls prev/*.apk | head -1)
    echo "Mise à jour : $(basename "$prev_apk") → $(basename "$APK")"
    ok=0
    for attempt in 1 2; do
      adb uninstall app.lumisite.android >/dev/null 2>&1; adb shell input keyevent KEYCODE_HOME; sleep 2
      adb install "$prev_apk" >/dev/null || continue
      maestro test .maestro/maj/maj_avant.yaml --debug-output "maestro-out/maj_avant-${attempt}" > "maestro-out/maj_avant-${attempt}.log" 2>&1 || continue
      adb shell am force-stop app.lumisite.android
      adb install -r "$APK" > "maestro-out/maj_install-${attempt}.log" 2>&1 || continue
      if maestro test .maestro/maj/maj_apres.yaml --debug-output "maestro-out/maj_apres-${attempt}" > "maestro-out/maj_apres-${attempt}.log" 2>&1; then ok=1; break; fi
    done
    if [ "$ok" = 1 ]; then
      record "- ✅ mise à jour $(basename "$prev_apk") → $(basename "$APK") (essai ${attempt})"
    else
      record "- ❌ mise à jour $(basename "$prev_apk") → $(basename "$APK") (2 essais)"
      fail=1
    fi
  else
    record "- ⚠️ mise à jour non exécutée : aucune pré-release etape${n_etape} téléchargeable"
  fi

  # Paquet v6 (anomalies 1 à 4 du 08/11) : même APK, calibrage 2 points + position du téléphone + relevé, arrêt brutal,
  # adb install -r par-dessus (données conservées), reprise : relevé et calibrage précédents affichés, nouveau calibrage, relevé.
  ok=0
  for attempt in 1 2; do
    adb uninstall app.lumisite.android >/dev/null 2>&1; adb shell input keyevent KEYCODE_HOME; sleep 2
    adb install "$APK" >/dev/null || continue
    maestro test .maestro/maj/v6_avant.yaml --debug-output "maestro-out/v6_avant-${attempt}" > "maestro-out/v6_avant-${attempt}.log" 2>&1 || continue
    adb shell am force-stop app.lumisite.android
    adb install -r "$APK" > "maestro-out/v6_install-${attempt}.log" 2>&1 || continue
    if maestro test .maestro/maj/v6_apres.yaml --debug-output "maestro-out/v6_apres-${attempt}" > "maestro-out/v6_apres-${attempt}.log" 2>&1; then ok=1; break; fi
  done
  if [ "$ok" = 1 ]; then
    record "- ✅ réinstallation adb install -r, reprise, calibrage et relevé (essai ${attempt})"
  else
    record "- ❌ réinstallation adb install -r, reprise, calibrage et relevé (2 essais)"
    fail=1
  fi
}

case "$suite" in
  tout) run_scenarios; run_upgrade_tests ;;
  parcours-1|parcours-2) run_scenarios ;;
  mises-a-jour) run_upgrade_tests ;;
esac
exit "$fail"
