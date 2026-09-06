# Rapport final — Gestion de la photo de profil (Arsikina)

## 0. Bugs trouvés et corrigés lors des tests sur appareil réel

La checklist section 10 a révélé plusieurs bugs que la relecture de code seule n'avait pas fait
apparaître — consignés ici pour référence future :

1. **Écran de recadrage inutilisable (bouton "Valider" invisible)** — `CropImageActivity` (bibliothèque
   image-cropper) affiche son bouton de validation comme un item de menu sur une vraie ActionBar
   système ; notre thème global (`Theme.Arzikina`) hérite de `Theme.Material3.DayNight.NoActionBar`,
   donc l'activité n'avait aucune ActionBar pour afficher ce bouton. Corrigé par un thème dédié
   `Theme.Arzikina.Cropper` (avec ActionBar) appliqué à `CropImageActivity` via `AndroidManifest.xml`.
2. **Écran de recadrage figé (aucune interaction possible)** — l'option `canChangeCropWindow = false`
   mappe directement sur `View.isEnabled` du cadre de recadrage, désactivant TOUT geste tactile
   (déplacement, redimensionnement, pinch-zoom). Corrigé en repassant à `true` (le format carré reste
   garanti par `fixAspectRatio = true`).
3. **Photo affichée en carré de couleur unie** — `app:tint` en XML (prévu pour l'icône silhouette par
   défaut) s'applique aussi à une vraie photo chargée par Coil, l'écrasant via un filtre `SRC_IN`.
   Corrigé en retirant/réappliquant `imageTintList` dynamiquement selon le contenu affiché, sur les
   3 écrans concernés (Dashboard, Profil, Réglages).
4. **Avatar carré au lieu de circulaire** — le `android:background` circulaire ne découpe pas le
   contenu affiché. Corrigé en passant les 3 `ImageView` avatar en `ShapeableImageView` (Material)
   avec un `shapeAppearanceOverlay` circulaire, padding retiré/restauré dynamiquement pour qu'une
   vraie photo remplisse tout le cercle.
5. **Bouton "Synchroniser maintenant" n'envoyait jamais la photo** — `SyncButtonController` (partagé
   Réglages/Dashboard) appelait seulement le registre générique (push/pull), jamais
   `profilePhotoRepository.syncWithServer()`. Corrigé en l'ajoutant, dans le même ordre que le
   `SyncWorker` automatique.
6. **`upload_photo.php` : fonction PHP 8.0+ (`str_starts_with`)** sur un hébergement PHP 7.x, causant
   une erreur fatale (page HTML au lieu de JSON). Remplacée par `substr(...) === 'image/'`
   (compatible toutes versions).
7. **Migration SQL jamais appliquée en production** — `users.photo_path` n'existait pas sur la base
   réelle (`ALTER TABLE` documenté comme étape manuelle, jamais exécuté). Résolu en l'exécutant
   manuellement via phpMyAdmin.
8. **Logs de diagnostic ajoutés en cours de route** — `SyncWorker` logue désormais l'exception exacte
   (et l'étape en cause : push/pull/photo) au lieu d'un `Result.retry()` silencieux — gardé en
   permanence, utile au-delà de ce bug précis.

## 1. Cause des problèmes actuels (audit initial)

Avant cette intervention, l'écran Profil (Android) permettait de choisir une image via `GetContent()`
et l'affectait directement à `UserEntity.profilePhotoUri` sans recadrage, sans optimisation et
**sans aucune synchronisation serveur** :

- L'image restait une simple `content://` locale au téléphone, jamais copiée ni envoyée nulle part.
- Aucune colonne côté serveur (`users`) ne portait de photo — l'API/MySQL n'avaient aucune notion
  de photo de profil.
- Rien n'existait côté Web : la page Profil affichait uniquement des initiales générées.
- Conséquence : changement de téléphone, réinstallation de l'app, ou consultation depuis le Web =
  photo perdue. Aucun mécanisme de cache, de version, ni de gestion hors-ligne.

L'objectif de cette intervention était de transformer ce comportement local en une véritable
fonctionnalité synchronisée (Android → Serveur → Web/autres appareils), **sans toucher au moteur de
synchronisation générique existant** (`SyncEngine`/`entity_sync_configs.php`), pour deux raisons
techniques découvertes en cours d'audit :

1. `UserEntity.kt` ne peut pas être modifié dans ce projet sans casser la compilation KSP2/Room
   (`kspDebugKotlin` échoue avec `[MissingType]`) — limitation déjà documentée et déjà contournée une
   fois via `UserServerLinkEntity`. Il a donc fallu créer une **table séparée** pour la photo plutôt
   que d'ajouter des colonnes à `UserEntity`.
2. Le registre générique de sync (`entity_sync_configs.php` / `push.php` / `pull.php`) ne transporte
   que du JSON, pas de binaire — et la table `users` en est volontairement absente (le
   `password_hash` ne doit jamais transiter par ce canal). Il fallait donc des **endpoints REST
   dédiés** plutôt qu'une extension de ce registre.

## 2. Fichiers modifiés / créés

### Android

| Fichier | Statut | Rôle |
|---|---|---|
| `data/local/entity/UserProfilePhotoEntity.kt` | Nouveau | Table séparée (1 ligne/utilisateur), contourne le bug `UserEntity`/KSP2 |
| `data/local/dao/UserProfilePhotoDao.kt` | Nouveau | Accès Room (observe/find/upsert) |
| `data/local/database/Migration26To27.kt` | Nouveau | Création de la table `user_profile_photos` |
| `data/local/database/ArzikinaDatabase.kt` | Modifié | Version 27, déclaration entité/DAO |
| `di/DatabaseModule.kt` | Modifié | Enregistrement migration + provider DAO |
| `data/profile/ProfilePhotoFileStorage.kt` | Nouveau | Écriture/lecture/suppression fichier local (`filesDir/profile_photos/`) |
| `AndroidManifest.xml` | Modifié | Permission `CAMERA` (pas de permission galerie, Photo Picker) |
| `res/xml/file_paths.xml` | Modifié | Chemin FileProvider `profile_photos/` |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modifié | Dépendance `com.vanniktech:android-image-cropper:4.7.0` |
| `domain/repository/ProfilePhotoRepository.kt` | Nouveau | Contrat métier (observe/save/delete/sync) |
| `data/repository/ProfilePhotoRepositoryImpl.kt` | Nouveau | Implémentation complète (local + réseau + résolution de conflit) |
| `data/local/dao/UserDao.kt` | Modifié | `updateProfilePhotoUri()` (mise à jour ciblée, ne touche pas nom/email/téléphone) |
| `di/RepositoryModule.kt` | Modifié | Binding Hilt |
| `res/layout/bottom_sheet_change_profile_photo.xml` | Nouveau | UI "Modifier la photo" |
| `presentation/profile/ChangeProfilePhotoBottomSheet.kt` | Nouveau | Bottom sheet (Fragment Result API) |
| `res/drawable/ic_photo_library_24.xml` | Nouveau | Icône galerie |
| `res/layout/dialog_profile_photo_preview.xml` | Nouveau | UI aperçu/validation |
| `presentation/profile/ProfilePhotoPreviewDialogFragment.kt` | Nouveau | Dialogue "Aperçu de votre photo de profil" |
| `res/layout/fragment_profile.xml` | Modifié | Indicateur de chargement sur l'avatar |
| `presentation/profile/ProfileViewModel.kt` | Modifié | État photo séparé du formulaire nom/email/téléphone |
| `presentation/profile/ProfileFragment.kt` | Modifié | Câblage caméra/galerie/recadrage/suppression |
| `res/values/strings.xml` | Modifié | 12 chaînes UI |
| `data/remote/dto/ProfilePhotoSyncResponseDto.kt` | Nouveau | DTO réponse serveur |
| `data/remote/api/ProfilePhotoApi.kt` | Nouveau | Upload/delete/get/download (OkHttp direct, pas de Retrofit) |
| `work/SyncWorker.kt` | Modifié | Appel `profilePhotoRepository.syncWithServer()` après le push/pull générique |

### Serveur (PHP/MySQL)

| Fichier | Statut | Rôle |
|---|---|---|
| `database/migrations/002_add_profile_photo_to_users.sql` | Nouveau | `ALTER TABLE users ADD COLUMN photo_path VARCHAR(255) NULL` |
| `database/migrations/001_initial_schema.sql` | Modifié | `photo_path` ajouté (installations neuves) |
| `server/api/profile/upload_photo.php` | Nouveau | Upload multipart, validation taille/MIME, écriture atomique |
| `server/api/profile/delete_photo.php` | Nouveau | Suppression (idempotente) |
| `server/api/profile/get.php` | Nouveau | Lecture état courant (poll multi-appareils/Web) |
| `server/avatars/.htaccess` | Nouveau | Interdiction d'exécution de scripts dans le dossier d'upload |
| `server/api/config/entity_sync_configs.php` | Modifié | Commentaire expliquant l'exclusion volontaire de `users`/photo |

### Web (arzikina-web-sync)

| Fichier | Statut | Rôle |
|---|---|---|
| `src/lib/api/client.ts` | Modifié | `profilePhoto()`, `profilePhotoUrl()` |
| `src/routes/profil.tsx` | Modifié | Hook `useProfilePhotoUrl`, affichage `<img>` ou repli initiales |

## 3. Classes/composants concernés

- **Domain** : `ProfilePhotoRepository` (contrat).
- **Data** : `ProfilePhotoRepositoryImpl`, `UserProfilePhotoDao`, `UserProfilePhotoEntity`,
  `ProfilePhotoFileStorage`, `ProfilePhotoApi`, `ProfilePhotoSyncResponseDto`.
- **Presentation** : `ProfileFragment`, `ProfileViewModel`, `ChangeProfilePhotoBottomSheet`,
  `ProfilePhotoPreviewDialogFragment`.
- **Infrastructure transverse réutilisée sans modification de sa logique** : `SyncWorker`,
  `SyncWorkScheduler`, `SyncConnectivityObserver`, `SessionManager`, `FileProvider`.

## 4. Tables MySQL modifiées

- `users` : ajout de la colonne `photo_path VARCHAR(255) NULL` (chemin relatif serveur, ex.
  `avatars/{userId}/{version}.jpg`, jamais d'URL absolue ni de binaire en base).
- Réutilisation des colonnes **déjà existantes** `users.version` et `users.updated_at` comme
  mécanisme de version/cache-invalidation — aucune nouvelle colonne `photo_version` créée.
- Aucune autre table serveur créée ou modifiée.

Côté Android (Room, local uniquement) : nouvelle table `user_profile_photos` (v27) — n'a pas
d'équivalent direct côté serveur, c'est un cache/état de synchronisation local.

## 5. Endpoints API ajoutés

| Méthode | Endpoint | Auth | Rôle |
|---|---|---|---|
| `POST` | `/api/profile/upload_photo.php` | Bearer token | Upload multipart (champ `photo`, max 5 Mo, JPEG/PNG), bump `version`, remplace l'ancien fichier |
| `POST` | `/api/profile/delete_photo.php` | Bearer token | Remet `photo_path = NULL`, bump `version` (idempotent) |
| `GET` | `/api/profile/get.php` | Bearer token | État courant `{ photoPath, version, updatedAt }` |

Le fichier image lui-même est servi statiquement depuis `server/avatars/` (pas d'endpoint PHP dédié
au téléchargement — accès direct, protégé de l'exécution de script par `.htaccess`).

## 6. Stratégie de stockage de l'image

- **Serveur** : fichier JPEG sur disque (`server/avatars/{userId}/{version}.jpg`), chemin **relatif**
  stocké en base (`users.photo_path`) — jamais de Base64/binaire en MySQL, jamais d'URL absolue en
  base (chaque client préfixe avec sa propre URL de base configurée).
- **Android** : fichier JPEG local (`filesDir/profile_photos/{uuid}.jpg`), exposé via `FileProvider`
  (`content://`). Écriture du nouveau fichier **avant** suppression de l'ancien (jamais de fenêtre
  sans photo valide en cas d'échec).
- **Web** : aucun stockage local, `<img>` pointe directement vers l'URL serveur.
- **Optimisation** : recadrage carré + redimensionnement (512×512 px) + compression JPEG qualité 85 +
  correction automatique de la rotation EXIF, tout intégré dans la bibliothèque CanHub
  Android-Image-Cropper (une seule intégration couvre recadrage ET optimisation).

## 7. Stratégie de cache

- Le **nom de fichier serveur encode la version** (`{version}.jpg`) : changement de photo = nouvelle
  URL = invalidation automatique du cache HTTP navigateur (Web) sans code supplémentaire.
- Android compare `response.version` à la version connue localement (`user_profile_photos.version`)
  et ne retélécharge que si strictement supérieure — jamais de retéléchargement inutile.
- Démarrage de l'app : l'avatar s'affiche immédiatement depuis le fichier local en cache
  (`observeCurrentUserPhotoUri()`), aucun appel réseau bloquant.

## 8. Stratégie de synchronisation

- **Émission** : après modification/suppression locale, la ligne `user_profile_photos` est marquée
  `pendingUpload = true`, puis `SyncWorkScheduler.triggerNow()` tente un envoi immédiat si une
  connexion est disponible (sinon différé automatiquement par WorkManager, contrainte
  `NetworkType.CONNECTED`).
- **Réutilisation totale de l'infrastructure existante** : `profilePhotoRepository.syncWithServer()`
  est appelé depuis le `SyncWorker` déjà en place, juste après `syncEngine.pushPendingChanges()` /
  `pullRemoteChanges()` — même planification périodique (6h), même retry avec backoff, même
  déclenchement sur retour de connectivité. Aucun Worker/Scheduler dédié créé.
- **Résolution de conflit** : "dernière version gagne", le **serveur est seul autorité** sur le
  numéro de version (incrémenté atomiquement à chaque écriture acceptée). Un envoi local en attente
  est toujours prioritaire sur une lecture (jamais écrasé silencieusement par un pull).
- **Une seule ligne par utilisateur** (index unique `userId`) : jamais plusieurs photos associées à
  un même profil, y compris après suppression (la ligne reste, avec `localPath = NULL`).

## 9. Tests effectués par moi (vérifications automatisées disponibles dans ce sandbox)

- `npx tsc --noEmit` sur `arzikina-web-sync` : **aucune erreur**.
- `npx eslint` sur les fichiers Web modifiés : **aucune erreur** (un seul avertissement Prettier
  préexistant, sans rapport avec ce changement, sur une ligne non modifiée).
- Relecture manuelle complète de chaque fichier PHP/Kotlin créé ou modifié (pas de compilateur
  Kotlin/PHP disponible dans ce sandbox — voir checklist ci-dessous pour les tests à exécuter par
  toi, sur appareil réel / serveur réel).

## 10. Checklist de tests manuels à effectuer (section 15 du cahier des charges)

### A. Sur serveur (curl, avant tout test mobile)

Remplace `TOKEN` par un jeton Bearer valide (obtenu via `login.php`) et `BASE_URL` par l'URL de ton
serveur (ex. celle de `RemoteConfig.BASE_URL` côté Android ou `getDefaultApiBaseUrl()` côté Web).

```bash
# 1. État initial (doit renvoyer photoPath: null si aucune photo)
curl -s -H "Authorization: Bearer TOKEN" "BASE_URL/api/profile/get.php"

# 2. Upload d'une photo de test
curl -s -H "Authorization: Bearer TOKEN" \
     -F "photo=@/chemin/vers/test.jpg" \
     "BASE_URL/api/profile/upload_photo.php"
# → vérifier que la réponse contient photoPath (ex. avatars/12/1.jpg), version incrémentée, updatedAt

# 3. Vérifier que le fichier est bien accessible directement (sans auth)
curl -sI "BASE_URL/avatars/12/1.jpg"   # → 200 OK, Content-Type image/jpeg

# 4. Re-upload → vérifier que l'ANCIEN fichier a disparu (pas de fichier orphelin)
curl -s -H "Authorization: Bearer TOKEN" -F "photo=@/chemin/vers/test2.jpg" "BASE_URL/api/profile/upload_photo.php"
# puis vérifier côté serveur (ls server/avatars/12/) qu'il ne reste qu'un seul fichier

# 5. Suppression
curl -s -H "Authorization: Bearer TOKEN" -X POST "BASE_URL/api/profile/delete_photo.php"
# → photoPath: null, version encore incrémentée

# 6. Suppression idempotente (appel répété sans photo) → ne doit PAS incrémenter version à nouveau
curl -s -H "Authorization: Bearer TOKEN" -X POST "BASE_URL/api/profile/delete_photo.php"

# 7. Fichier/dossier bien protégé contre l'exécution de script
curl -sI "BASE_URL/avatars/quelquechose.php"   # → doit échouer (403/404), jamais exécuter du PHP
```

### B. Sur Android (appareil réel ou émulateur, connexion active)

1. **Choisir une photo depuis la galerie** : Profil → avatar → "Choisir dans la galerie" →
   sélectionner une image → écran de recadrage s'ouvre.
2. **Prendre une photo avec la caméra** : "Prendre une photo" → (1ère fois : vérifier la demande de
   permission caméra) → prise de vue → recadrage.
3. **Recadrage** : déplacer l'image, zoomer/dézoomer, vérifier l'aperçu temps réel, format carré
   respecté.
4. **Annuler le recadrage** : retour à l'écran Profil sans changement.
5. **Valider le recadrage** : ouverture du dialogue "Aperçu de votre photo de profil".
6. **Annuler l'aperçu** : aucun changement appliqué.
7. **Valider l'aperçu** ("Utiliser cette photo") : avatar mis à jour **immédiatement**, indicateur de
   chargement bref, puis disparition.
8. **Modifier une photo déjà existante** : répéter 1–7, vérifier que l'ancienne disparaît (pas de
   doublon visuel, pas de scintillement).
9. **Supprimer la photo** : "Supprimer la photo" → confirmation → retour à l'avatar par défaut.
10. **Mode hors ligne** : couper le réseau, changer la photo → vérifier qu'elle s'affiche quand même
    immédiatement en local, avec un état "en attente" (à défaut d'indicateur visuel dédié, vérifier
    en base/logs que `pendingUpload = true`).
11. **Retour de connexion** : réactiver le réseau → vérifier (logs, ou `get.php` côté serveur) que la
    photo a bien été envoyée automatiquement, sans action supplémentaire de l'utilisateur.
12. **Redémarrage de l'app** : tuer et relancer l'app → la photo doit s'afficher immédiatement
    (depuis le cache local), sans attendre le réseau.
13. **Cache/pas de retéléchargement** : avec une photo déjà synchronisée et à jour, observer (logs
    réseau) qu'aucun appel `downloadPhoto` n'est fait tant que la version serveur n'a pas changé.
14. **Multi-appareils** : changer la photo sur un appareil A, attendre la sync (ou déclencher
    manuellement), puis ouvrir l'app sur un appareil B connecté au même compte → la nouvelle photo
    doit apparaître après le prochain cycle de sync.
15. **Absence de doublons/fichiers orphelins** : après plusieurs changements de photo, vérifier
    côté serveur (`ls server/avatars/{userId}/`) et côté Android (`filesDir/profile_photos/`) qu'il
    ne reste qu'un seul fichier actif par utilisateur.

### C. Sur le Web (arzikina-web-sync)

16. **Affichage** : se connecter avec un compte ayant une photo synchronisée → la page `/profil`
    doit afficher la photo (pas les initiales).
17. **Compte sans photo** : se connecter avec un compte sans photo → repli sur les initiales, aucune
    erreur console.
18. **Photo changée depuis Android** : changer la photo sur mobile, recharger la page `/profil` sur
    le Web → la nouvelle photo doit apparaître (pas de cache navigateur périmé, grâce au nom de
    fichier versionné).

## 11. Git

Branche proposée :

```
feature/profile-photo-sync
```

Commits proposés (Conventional Commits), dans l'ordre logique des étapes livrées :

```
feat(android): add UserProfilePhotoEntity and local storage for profile photo
feat(android): add camera/gallery selection, crop and optimization for profile photo
feat(server): add profile photo upload/delete/get endpoints and users.photo_path column
feat(android): sync profile photo with server via existing SyncWorker
feat(web): display synced profile photo on profil page
```

Un seul commit squashé est aussi envisageable si tu préfères un historique plus condensé — dans ce
cas : `feat(profile): add end-to-end profile photo management (camera/gallery, crop, sync)`.
