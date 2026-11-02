# Synchro multi-appareils — note de conception (reportée, rien n'est codé)

**Objectif** : aligner après coup les enregistrements de plusieurs appareils (principal + secondaires) sans les refaire. Les sessions enregistrées depuis le LOT 1 le permettent : chaque ligne de chaque flux porte `t_mono_ns` (horloge monotone de l'appareil) et l'id de sa base de temps ; chaque redémarrage ou reprise ouvre une base de temps (`TimeBaseAdded`) ; les interruptions sont des trous explicites (voir `docs/CONTRAT_DONNEES.md`).

## Modèle
- Par paire (secondaire, base de temps) : `t_principal = a · t_secondaire + b`, où `a` est la dérive (de l'ordre de 1 ± 50 ppm) et `b` le décalage.
- Estimation sur **plusieurs balises** communes (au moins 3, réparties sur la session) par régression robuste : moindres carrés après rejet des aberrants (RANSAC ou résidu > 3 × MAD). Le résultat donne `a`, `b`, leur **incertitude** (écart type des résidus, intervalle sur `b` aux extrémités) et la liste des balises rejetées.
- Une base de temps = un modèle : jamais d'extrapolation à travers un redémarrage ni à travers un trou. Un segment sans balise reste **non synchronisé**, et c'est affiché.

## Approche hybride
1. **Réseau local** (Wi-Fi ou BLE) pour coordonner : découverte, choix du principal, échange des identifiants d'appareil et de session, et premier décalage grossier par aller-retour (précision de l'ordre de 10 ms).
2. **Balises audio** pour les marqueurs fins : chirp fenêtré émis par le principal, portant l'identifiant d'appareil et un numéro de séquence ; chaque appareil horodate la détection en `t_mono_ns` dans son flux audio brut.
3. **Recalage à l'import** (bureau ou appareil principal) : appariement des balises par numéro de séquence, estimation de `a` et `b`, puis réécriture **dérivée** des temps (`derived/`). Les flux bruts ne sont jamais modifiés.

## À mesurer avant de figer
- Bande de fréquence, durée du chirp et période des balises : banc d'essai à 3 bandes (7–12, 15–18, 17,5–19,5 kHz) sur le Pixel 7 et le Xiaomi, à 1, 3 et 5 m. Critère : au moins 95 % de détection et une gigue inférieure à 1 ms.
- Période minimale qui garde l'incertitude de `b` sous la cible avec la dérive mesurée.
- Coût batterie et CPU de la détection continue.
