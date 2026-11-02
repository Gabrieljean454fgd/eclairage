# Compte rendu — prompt v5 (02/11/2026)

Tests : CI (émulateur Pixel 7, API 34) et émulateur local seulement ; aucun test sur le Pixel 7 physique (fait par un autre agent). Rien n'est fusionné (PR #17, #18, #19 ouvertes).

## Lot M — corrections du matin

### M1 — Accueil > « Nouveau » > « Créer sans contours OSM » : bouton sous la barre de navigation (etape-1, fusionné vers etape-2 et etape-3)
- **Cause réelle** : `targetSdk 35` → Android 15+ impose l'affichage bord à bord ; aucune marge n'était réservée à la barre de navigation. Sur le Pixel 7 (navigation à 3 boutons), `nouveau_site_creer` était dessiné sous la barre et l'appui au centre tombait sur Retour. L'émulateur API 34 n'impose pas le bord à bord, d'où le vert en CI. La cause « dossiers non recréés » du compte rendu du 01/11 (1.3) était fausse ; elle y est rectifiée.
- **Correctif** : bord à bord activé sur tous les niveaux d'API (`enableEdgeToEdge()`), et marge `safeDrawing` (barres système, encoche, clavier) appliquée à la racine, donc sur Accueil, Accueil > Nouveau, Site, Réglages et Session. L'émulateur API 34 se comporte maintenant comme le Pixel.
- **CI** : `.github/scripts/maestro-ci.sh` passe l'émulateur en navigation à 3 boutons (`cmd overlay enable com.android.internal.systemui.navbar.threebutton`) avant tous les parcours ; `scenario1_1_creer_releve` n'est pas modifié. Vérifié en local : parcours 1.1 vert en navigation à 3 boutons.
- À vérifier sur le Pixel 7 : appui au centre de « Créer sans contours OSM » en navigation à 3 boutons et par gestes.

### M4 — `docs/COMPTE_RENDU_2026-11-01.md` (etape-3)
- Ligne `||||||| bcf8971` (reste d'un conflit de fusion) supprimée ; `git grep -nE '^(<<<<<<<|\|\|\|\|\|\|\||>>>>>>>)'` ne renvoie plus rien.

### Plan révisé (22 h)
- M2 (contraste des libellés) et l'option B (« Enregistrer le relevé » fixé) étaient codés et verts en local sur etape-2 ; non publiés, ils passent au LOT 4 (etape-3). M3 et les options A et C aussi.
