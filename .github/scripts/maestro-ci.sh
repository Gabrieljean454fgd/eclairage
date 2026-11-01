#!/usr/bin/env bash
# Parcours Maestro de .maestro/ (racine) sur l'émulateur de la CI. Chaque parcours a droit à un second essai : le démarrage à
# froid après clearState est parfois très lent sur émulateur (écran noir). Code de sortie non nul si un parcours échoue deux fois.
set -u
APK="$1"
export MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
adb install -r "$APK"
# Reproduit le cas du Pixel 7 réel : en-tête AR de Session à sa taille maximale (bloc caméra simulé, APK debug seulement).
adb shell settings put global lumisite_simuler_entete_ar 1
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
printf '%s' "$summary" | tee maestro-out/resume.md
exit $fail
