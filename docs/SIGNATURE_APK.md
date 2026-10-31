# Signature des APK de test et vérification d'une installation

## La clé de TEST publique (étape 1.1)

- Fichier : `app/keystore/lumisite-test.jks` (PKCS12), alias `lumisite-test`, mots de passe `lumisite-test`.
- Certificat : `CN=LumiSite TEST KEY - PUBLIC - NOT FOR PRODUCTION, O=LumiSite test, C=FR`, valable 100 ans.
- Empreinte SHA-256 du certificat : `88:30:D1:32:EA:0F:A4:05:63:85:96:B1:7C:A0:BE:4C:AF:F7:63:EE:2B:EF:03:98:D8:D7:6E:0C:98:74:6F:E4`.
- **Cette clé est publique** (dépôt public) : n'importe qui peut signer un APK avec. Elle sert uniquement à ce que tous les APK de test, construits sur un poste ou par GitHub Actions, aient le même certificat et s'installent les uns par-dessus les autres. Elle n'est **jamais** utilisable pour une publication (Play Store ou diffusion à des clients) : une version de production aura sa propre clé, gardée hors du dépôt.
- Elle est branchée sur `signingConfigs.debug` (`app/build.gradle.kts`). Le type `release` n'a volontairement aucune signature.

## Numéro de version

- `applicationId` : `app.lumisite.android` (inchangé).
- `versionCode` = nombre de commits de la branche (`git rev-list --count HEAD`), donc croissant d'un commit à l'autre sur une même branche d'étape. `-PversionCode=N` le force.
- Nom de l'APK publié par GitHub Actions : `lumisite-etape<N>-v<versionCode>-<hash court>.apk`, dans une pré-release `etape<N>-<hash court>`.

Android refuse d'installer un APK dont le `versionCode` est **inférieur** à celui déjà installé (`INSTALL_FAILED_VERSION_DOWNGRADE`). Si l'on revient d'une étape 2 à une étape 1 plus ancienne, il faut désinstaller.

## Transition depuis le lot 13 (une seule fois)

Les APK des lots 1 à 13 étaient signés avec la clé debug propre à chaque machine de build : leur certificat diffère de la clé de test. Il faut donc **désinstaller une dernière fois** la version lot 13 (rien n'est à garder, décision du 31/10), puis installer l'APK de l'étape 1. Ensuite, toutes les mises à jour s'installent par-dessus.

## Procédure de vérification (confirmer la cause, ne pas la supposer)

Outils : `apksigner` et `aapt2` sont dans `Android/sdk/build-tools/<version>/`, `adb` dans `platform-tools/`.

1. Certificat d'un APK :
   ```bash
   apksigner verify --print-certs lumisite-etape1-v101-abc1234.apk
   # Signer #1 certificate DN: CN=LumiSite TEST KEY - PUBLIC - NOT FOR PRODUCTION, O=LumiSite test, C=FR
   # Signer #1 certificate SHA-256 digest: 8830d132ea0fa4056385...
   ```
2. Version et identifiant de l'APK :
   ```bash
   aapt2 dump badging lumisite-etape1-v101-abc1234.apk | head -1
   # package: name='app.lumisite.android' versionCode='101' versionName='0.2.0-v101' ...
   ```
3. Certificat et version de l'appli déjà installée sur le téléphone :
   ```bash
   adb shell dumpsys package app.lumisite.android | grep -E "versionCode|signatures|Signing"
   adb shell pm path app.lumisite.android        # chemin de base.apk
   adb pull <chemin>/base.apk installe.apk && apksigner verify --print-certs installe.apk
   ```
4. Installer par-dessus et lire l'erreur exacte :
   ```bash
   adb install -r lumisite-etape1-v101-abc1234.apk
   ```
   | Message | Cause |
   |---|---|
   | `INSTALL_FAILED_UPDATE_INCOMPATIBLE: ... signatures do not match` | Certificat différent de l'appli installée (cas du lot 13) : désinstaller. |
   | `INSTALL_FAILED_VERSION_DOWNGRADE` | versionCode plus petit que l'installé. |
   | `INSTALL_PARSE_FAILED_NO_CERTIFICATES` | APK non signé (build release, ou fichier tronqué au téléchargement). |
   | `INSTALL_FAILED_INSUFFICIENT_STORAGE` | Mémoire du téléphone pleine. |
5. Sans ordinateur (installation depuis le téléphone) : le message de l'installeur (« Application non installée », « Conflit avec un package existant ») est générique. Brancher le téléphone et lire le journal :
   ```bash
   adb logcat -d | grep -iE "PackageManager|INSTALL_FAILED|signatures"
   ```
   Sur Xiaomi (HyperOS), l'installeur MIUI peut aussi bloquer : vérifier « Installer via USB » dans les options pour les développeurs.
