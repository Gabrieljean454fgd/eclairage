# Compte rendu — 01/11/2026 (Prompt v4)

Mis à jour à la fin de chaque palier. Rien n'est fusionné. Tests réalisés sur émulateur Pixel 7 (API 34, x86_64, sans ARCore réel) et en CI ; **aucun test sur le Pixel 7 physique ni avec ARCore réel** : tout ce qui suit reste à vérifier sur le téléphone.

## Palier P1 — réparer et republier

| Point | Écran > Menu > Sous-menu | Statut |
|---|---|---|
| 1.1 | Session > barre d'actions (« ← Terminer la session », « Synchro », « Calibrer », « Relevé », « Annoter », « Recaler », « Export ») | fait (à vérifier sur Pixel 7) |
| 1.2 | Session > carte > « Point à la croix », « Centrer / Suivre », légende, « Fond : … » | fait (à vérifier sur Pixel 7) |
| 1.3 | Accueil > « Nouveau » > « Créer » (etape-1) | cause probable corrigée, non confirmée sur Pixel 7 |
| 1.4 | Parcours Maestro 1.1 à 1.5 et 2 + CI | voir ci-dessous |

### 1.1 — Cause et correctif
- **Cause** (déduite du code, non observée sur le téléphone) : depuis a4a4da9, l'en-tête AR (bandeau de suivi, aperçu caméra 150 dp, torche/mesure) **et** la barre de modes étaient dans la même colonne défilante plafonnée à 35 % de la hauteur. Avec ARCore actif, l'en-tête AR occupe à lui seul ces 35 % ; la barre de modes se retrouvait sous le pli de cette colonne, donc hors écran. Compose ne compose pas l'arbre d'accessibilité des éléments entièrement hors de la zone visible d'un parent défilant tronqué de cette façon, d'où des testTags « absents » côté Maestro. Sur émulateur, sans ARCore, l'en-tête était petit et la barre restait visible.
- **Correctif** : la barre d'actions (Terminer la session + 6 modes, testTags inchangés) est **fixe**, jamais dans un défilement, placée en premier. Seul le bloc d'état/AR (`session_bloc_ar`) défile, plafonné à 25 % de la hauteur. La correction clavier de Session > « Calibrer » > « Cap de visée » est conservée : le panneau de saisie garde le reste de la hauteur.
- **Simulation du cas réel** : en APK debug uniquement, `adb shell settings put global lumisite_simuler_entete_ar 1` affiche un bloc « AR simulé » de même encombrement que le vrai (200×150 dp + torche/mesure). Les parcours locaux et la CI tournent avec ce réglage.
- La barre compacte à icônes (point 2.1) n'est pas faite dans ce palier (P2).
- **Relecture automatique de la [PR #17](https://github.com/Gabrieljean454fgd/eclairage/pull/17)** (2 remarques, corrigées) :
  - écran étroit (360 dp) avec clavier ouvert : « ← Terminer la session » et les 6 modes sont maintenant dans une seule rangée qui passe à la ligne sur toute la largeur (2 lignes sur Pixel 7, environ 3 sur 360 dp, au lieu d'un mode par ligne) ; en portrait, quand la hauteur utile descend sous 480 dp (clavier ouvert ; le paysage garde le bloc AR et ses commandes torche / mesure), le bloc AR est replié (toujours présent, aperçu caméra non détruit) et la carte cède de la place au panneau de saisie. Testé sur émulateur Pixel 7 seulement ; **non testé en 360 dp** (le parcours 1.1 lui-même n'a pas pu être rejoué à cette taille : défilement du formulaire, à voir séparément) ;
  - session ouverte quand Android recrée l'activité (mode nuit, langue) : l'ancien `SessionController` est maintenant fermé (hors du fil d'interface) ; l'écran Site s'affiche et « Reprendre » attend la fin de cette fermeture (10 s au plus) avant de rouvrir la session, pour qu'il n'y ait jamais deux écritures sur la même session. Non testé sur appareil. Une première version fermait la session à toute destruction de l'activité, sur le fil d'interface : elle provoquait un écran noir au relancement dans le parcours 1.4 ; elle a été limitée à la recréation.

### 1.2 — Chevauchements de la carte
- « Point à la croix » et « Centrer / Suivre » sont empilés en colonne en bas à droite (hauteur ≥ 48 dp chacun), au-dessus du bord bas ; « largeur visible » reste en bas à gauche ; la légende de trajectoire est en haut à gauche, limitée à 45 % de la largeur, donc ne recouvre plus Session > carte > « Fond : … » (en haut à droite).
- La barre d'actions n'est plus recouverte par la carte (elle est hors de la carte, au-dessus).
- Le cartouche « Source : GPS · non calibré… » sera raccourci au point 2.1 (P2).

### 1.3 — Accueil > « Nouveau » > « Créer » : site absent sur Pixel 7
- **Cause la plus probable** (trouvée dans le code, non reproduite sur le Pixel 7) : `Repository` créait les dossiers `sites/`, `sessions/`, `exports/` **une seule fois** au démarrage. Maestro `clearState` (`pm clear`) peut vider le dossier privé alors que le processus garde ses chemins ; l'écriture du site se faisait ensuite dans un dossier absent, `renameTo` échouait **sans erreur**, et l'écran Site, ne trouvant pas le site, revenait silencieusement à l'Accueil vide. C'est exactement le symptôme décrit. Seconde cause possible : la recréation de l'activité par Android (écran courant non sauvegardé).
- **Correctifs** : dossiers recréés à chaque accès ; écriture du site atomique avec `fsync`, échec de renommage levé, relecture immédiate ; en cas d'échec, message « Site non enregistré : … » et entrée dans le journal (Réglages > Journal) au lieu d'un retour silencieux ; si un site ouvert est introuvable, message « Site introuvable (voir Réglages > Journal) » et entrée de journal ; écran courant conservé si Android recrée l'activité (une session en cours revient sur son écran Site, d'où « Reprendre »).
- **À vérifier sur le Pixel 7** : si le symptôme persiste, le journal (Réglages > Journal) contient désormais la raison exacte.

### 1.4 — Parcours Maestro
- Flows inchangés dans leurs vérifications (`session_terminer_la_session`, `session_annoter` toujours vérifiés « visible »). Seul changement : attente du premier écran portée à 90 s dans 1.1 et 1.4 (lancement à froid lent sur émulateur).
- Nouveau script CI `.github/scripts/maestro-ci.sh` et job `maestro` dans `.github/workflows/apk-etape.yml` : émulateur Pixel 7 API 34, en-tête AR simulé, application vidée avant chaque parcours, 2 essais par parcours ; la pré-release n'est publiée que si tous les parcours passent.
- Résultats locaux etape-1 (émulateur, en-tête AR simulé) : 1.1, 1.2, 1.3, 1.4, 1.5 passés au premier essai.
