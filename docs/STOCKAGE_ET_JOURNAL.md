# Stockage, réimport, sauvegarde Android et journal de diagnostic (étape 1)

Libellés réels : Accueil > « Réglages » (nouveau bouton, étape 1) > Stockage / Journal.

## 1.2 Écriture locale atomique en segments + copie continue

- L'acquisition écrit d'abord dans l'appli : `files/sessions/<session>/seg/<flux>.<n°>.part`.
  Un segment est fermé après **1 Mio ou 60 s** : `fsync`, puis renommage atomique en `<flux>.<n°>.ndjson`.
  Un segment terminé ne change plus jamais.
- `fsync` : événements à chaque ligne ; échantillons (poses, IMU, GPS) au moins toutes les **2 s**.
- À chaque pause de la session (écran quitté, appel, photo…), les segments ouverts sont fermés et copiés.
- Chaque segment terminé, chaque pièce jointe, l'en-tête et `site.json` sont copiés au fil de l'eau dans
  **`Documents/LumiSite/<nom du site>_<id site>/<id session>/`**, via MediaStore (Android 10+, aucune autorisation à
  redonner). La copie est relue et comparée par **SHA-256** ; `copies.json` (dans le dossier public) liste chemin → empreinte.
- Android 9 et moins : écriture directe du dossier public, qui demande l'autorisation de stockage ; cette version ne la
  demande pas (le Xiaomi 13T Pro est sous Android 13/14, non concerné). **Question ouverte** : faut-il la demander ?

### Ce que l'on récupère après un arrêt brutal (réserve R2)

| Situation | Récupéré | Perdu |
|---|---|---|
| Segments terminés | tout (fsync + renommage atomique) | rien |
| Segment ouvert (`.part`) | toutes les lignes jusqu'au dernier fsync (≤ 2 s pour les échantillons, 0 pour les événements) | au plus les ~2 dernières secondes d'échantillons non synchronisés |
| Ligne en cours d'écriture | conservée telle quelle, puis signalée comme illisible (`integrity` dans l'export) | son contenu |

À la réouverture de la session : chaque `.part` est terminé (`\n` ajouté), renommé `.ndjson`, et un événement
`stream_state` `REPAIRED` / `ABRUPT_STOP` est écrit dans le journal de session (R1 : la coupure est signalée, rien n'est inventé).
Tests JVM : `JournalSegmentsTest` (rotation, réparation, segments immuables, lecture des sessions mono-fichier).
Non testé : coupure d'alimentation réelle sur le Xiaomi.

### Réimport après réinstallation

Réglages > Stockage > « Réimporter une session depuis Documents/LumiSite ».
Après une désinstallation, Android ne rend plus les fichiers à la nouvelle installation (ils appartiennent à l'ancienne).
Le réimport ouvre donc le sélecteur de dossier du système (positionné sur `Documents/LumiSite`) : l'utilisateur choisit
`LumiSite` (ou un site, ou une session). L'appli recopie chaque session trouvée (dossier contenant `session_header.json`),
vérifie chaque fichier contre `copies.json`, restaure le site s'il manque, et n'écrase jamais une session déjà présente.
**Écart signalé** : c'est un choix de dossier (geste unique), pas une autorisation persistante ; le prompt demandait « sans
dépendre d'une autorisation qu'il faudrait redonner après réinstallation » : la copie n'en dépend pas, le réimport
demande ce geste. **Question ouverte** : acceptable pour Sébastien ?

## 1.4 Sauvegarde Android (filet complémentaire)

`android:allowBackup="true"`, `dataExtractionRules` (Android 12+) et `fullBackupContent` (Android ≤ 11) incluent
`sites/`, `sessions/` (médias clients compris, décision de Sébastien), `diag/` et les préférences ; exclus : `exports/`
(régénérables) et le cache de tuiles.

Limites : la sauvegarde cloud Google est plafonnée à **25 Mo par appli** ; au-delà, Android **n'envoie rien** pour
l'appli. Elle a lieu au plus une fois par jour, téléphone en charge, sur Wi-Fi, inactif. Une session de relevé avec
photos dépasse vite 25 Mo : ce n'est donc pas la sauvegarde principale, qui reste la copie `Documents/LumiSite`.
Le transfert d'appareil à appareil (`device-transfer`) n'a pas ce plafond.
Non vérifié sur le Xiaomi : `adb shell bmgr backupnow app.lumisite.android` puis restauration.

## 1.5 Journal de diagnostic

- Fichier texte tournant `files/diag/journal.txt` (512 Kio × 4 fichiers), copié dans **`Documents/LumiSite/_journal/`**
  (`journal-<date>.txt` à chaque rotation, `journal-courant.txt` à chaque pause, partage ou fin de session).
- Chaque ligne : `date heure | Écran > Menu > Sous-menu actif | catégorie | message`.
- Catégories : `ÉCRAN`, `SESSION`, `FLUX` (arrêts/reprises/réparations), `MÉMOIRE` (tas Java, mémoire système),
  `ARCORE ERREUR` (création, resume, update, torche, playback), `EXPORT` / `EXPORT ERREUR`, `RÉIMPORT`, `COPIE ERREUR`,
  `JOURNAL`, `CRASH` (exception non rattrapée, avec la pile). La synchro (étape 3) n'écrit encore rien : elle n'existe pas.
- Réglages > Journal > « Partager le journal » : un seul fichier, du plus ancien au plus récent.
- Limite : l'écran actif est noté au niveau Écran (Accueil, Site, Session, Réglages) et, pour l'export et le réimport,
  jusqu'au sous-menu ; les panneaux de Session (Synchro, Calibrer…) ne sont pas encore tous notés.
