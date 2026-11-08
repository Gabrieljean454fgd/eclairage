#!/usr/bin/env bash
# Parcours Maestro de .maestro/ (racine) sur l'émulateur de la CI. Chaque parcours a droit à un second essai : le démarrage à
# froid après clearState est parfois très lent sur émulateur (écran noir). Code de sortie non nul si un parcours échoue deux fois.
set -u
APK="$1"
export MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
adb install -r "$APK"
# Reproduit le cas du Pixel 7 réel : en-tête AR de Session à sa taille maximale (bloc caméra simulé, APK debug seulement).
adb shell settings put global lumisite_simuler_entete_ar 1
# Navigation à 3 boutons, comme le Pixel 7 de test : un bouton dessiné sous la barre de navigation fait alors échouer l'appui au centre.
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton || true
sleep 3
echo "navigation_mode=$(adb shell settings get secure navigation_mode) (0 = 3 boutons)"
mkdir -p maestro-out
fail=0; summary=""
for f in .maestro/*.yaml; do
  name=$(basename "$f" .yaml)
  ok=0
  for attempt in 1 2; do
    # Appli arrêtée et vidée avant chaque parcours : un parcours qui s'est fini dans Session laisse sinon, sur émulateur,
    # un écran noir de plus de 90 s au lancement suivant (non reproduit à la main, ni signalé sur le Pixel 7).
    adb shell am force-stop app.lumisite.android; adb shell pm clear app.lumisite.android >/dev/null; adb shell input keyevent KEYCODE_HOME; sleep 2
    if maestro test "$f" --format junit --output "maestro-out/${name}-${attempt}.xml" --debug-output "maestro-out/${name}-${attempt}" > "maestro-out/${name}-${attempt}.log" 2>&1; then
      ok=1; break
    fi
  done
  if [ $ok = 1 ]; then summary+="- ✅ ${name} (essai ${attempt})"$'\n'; else summary+="- ❌ ${name} (2 essais)"$'\n'; fail=1; fi
done
# LOT 1.5 : conservation des données à la mise à jour. Version N = dernière pré-release publiée de la même étape (même clé
# de test), session créée, arrêt brutal, puis adb install -r de cette version N+1 : la session doit rester lisible et exportable.
N_ETAPE=$(basename "$APK" | sed -nE 's/^lumisite-etape([0-9]+)-.*/\1/p')
PREV_TAG=$(gh release list --limit 50 --json tagName -q ".[] | .tagName" 2>/dev/null | grep -E "^etape${N_ETAPE}-" | head -1)
if [ -n "$PREV_TAG" ] && gh release download "$PREV_TAG" -p '*.apk' -D prev >/dev/null 2>&1; then
  PREV_APK=$(ls prev/*.apk | head -1)
  echo "Mise à jour : $(basename "$PREV_APK") → $(basename "$APK")"
  ok=0
  for attempt in 1 2; do
    adb uninstall app.lumisite.android >/dev/null 2>&1; adb shell input keyevent KEYCODE_HOME; sleep 2
    adb install "$PREV_APK" >/dev/null || continue
    maestro test .maestro/maj/maj_avant.yaml --debug-output "maestro-out/maj_avant-${attempt}" > "maestro-out/maj_avant-${attempt}.log" 2>&1 || continue
    adb shell am force-stop app.lumisite.android
    adb install -r "$APK" > "maestro-out/maj_install-${attempt}.log" 2>&1 || continue
    if maestro test .maestro/maj/maj_apres.yaml --debug-output "maestro-out/maj_apres-${attempt}" > "maestro-out/maj_apres-${attempt}.log" 2>&1; then ok=1; break; fi
  done
  if [ $ok = 1 ]; then summary+="- ✅ mise à jour $(basename "$PREV_APK") → $(basename "$APK") (essai ${attempt})"$'\n'
  else summary+="- ❌ mise à jour $(basename "$PREV_APK") → $(basename "$APK") (2 essais)"$'\n'; fail=1; fi
else
  summary+="- ⚠️ mise à jour non exécutée : aucune pré-release etape${N_ETAPE} téléchargeable"$'\n'
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
if [ $ok = 1 ]; then summary+="- ✅ réinstallation adb install -r, reprise, calibrage et relevé (essai ${attempt})"$'\n'
else summary+="- ❌ réinstallation adb install -r, reprise, calibrage et relevé (2 essais)"$'\n'; fail=1; fi
printf '%s' "$summary" | tee maestro-out/resume.md
exit $fail
