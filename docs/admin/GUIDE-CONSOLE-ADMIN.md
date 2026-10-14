# Console d'administration Arzikina — versions Android, utilisateurs, rôles

## 1. Architecture en bref
- **Rôles** : colonne `users.admin_role` (`NULL` | `admin` | `super_admin`). Contrôlés **côté serveur** à chaque requête (`requirePermission`), relus en base : un retrait de rôle est immédiat.
  - **Admin** : tableau de bord, page *Utilisateurs* (liste, fiche, statistiques).
  - **Super Admin** : tout cela + *Versions Android*, politique de mise à jour, rôles, historique d'audit.
- **Politique de version** : une seule ligne `app_version_policy` (version minimale, mise à jour obligatoire, lien, maintenance).
- **Contrôle** : l'app Android envoie `X-App-Version-Code` à chaque requête ; le serveur compare **l'entier** `versionCode` (jamais le nom) et répond **426 `upgrade_required`** (ou **503 `maintenance`**).
- **Endpoint public** `GET /api/app/version_policy.php` : jamais bloqué, sans authentification.
- **Exemptions** : endpoint public, `auth/logout.php`, `auth/delete_account.php`, tout `api/admin/*`.
- **Journal d'audit** : `admin_audit_log` (politique, versions, rôles, consultations de fiches). Aucun mot de passe, jeton ni secret n'y figure.

## 2. Attribuer un rôle
Le premier Super Admin ne se crée **jamais** depuis le navigateur. Au choix :
1. CLI (recommandé, journalisé) : `php server/scripts/grant_role.php --email=bachirabdoulkader62331@gmail.com --role=super_admin` (`--role=admin` ou `none`).
2. SQL : `UPDATE users SET admin_role='super_admin' WHERE email='…' AND deleted_at IS NULL;`

Ensuite, un Super Admin peut gérer les rôles via l'API `admin/roles/*` (pas de changement de son propre rôle ; le dernier Super Admin ne peut pas être rétrogradé).

## 3. Publier une nouvelle version
1. Publier l'AAB sur Google Play (versionCode strictement supérieur au précédent).
2. Console → **Versions Android** → *Ajouter une version* : nom, versionCode, notes → *Enregistrer* (statut **Brouillon**).
3. Quand la version est disponible sur Play : menu `…` → **Publier**.
4. Une version plus récente **n'obsolète pas** les anciennes : seule la politique décide.

## 4. Déprécier / bloquer une ancienne version
1. **Déprécier** (information seulement) : menu `…` de l'ancienne version → *Marquer obsolète*. Aucun blocage.
2. **Bloquer** : *Politique de mise à jour* → *Modifier* → choisir la **version minimale** (une version **publiée**) → activer **Mise à jour obligatoire** → confirmer. Toute app dont le `versionCode` est **inférieur** est bloquée (écran « Mise à jour obligatoire » + 426 côté API).
3. **Débloquer** : désactiver l'obligation, ou abaisser la version minimale. Effet immédiat.
4. Garde-fou : l'obligation est refusée si la version minimale n'est pas publiée (`policy_would_lock_users`).
5. Une version **retirée** est définitive ; les versions ne sont jamais supprimées.

## 5. Procédure de déploiement sans risque (ordre important)
1. Appliquer `database/migrations/010_admin_and_app_versions.sql` (fait).
2. Déployer le serveur (PHP) : le contrôle est **inerte** tant que l'obligation est désactivée (politique neutre).
3. Console : publier la version **7 (1.2.0)** (celle déjà en production).
4. Publier sur Play l'AAB contenant le contrôle de version (étape 5, `versionCode` > 7) ; l'ajouter comme version **publiée**.
5. **Attendre l'adoption** (Play Console → statistiques de versions).
6. Seulement alors : version minimale = la version avec contrôle, obligation activée.
7. **N'activez « Bloquer aussi les anciennes installations » qu'en dernier recours** : cela bloque aussi les clients sans en-tête, **dont la console Web**.
- **Retour arrière immédiat** : désactiver l'obligation (console) ou `UPDATE app_version_policy SET force_update=0;`.

## 6. Mode maintenance
*Politique de mise à jour* → *Mode maintenance* + message. Les apps affichent « Maintenance en cours » ; les routes protégées répondent 503. La console admin, l'endpoint public, la déconnexion et la suppression de compte restent disponibles.

## 7. Comportement hors ligne de l'app
Le dernier verdict connu est conservé : une app bloquée le reste hors ligne. Sans aucune donnée (jamais connectée), l'app **n'est pas** bloquée. La maintenance n'est jamais déduite du cache.

## 8. Tests
- Domaine Android (JVM) : `VersionGateTest` (7 cas).
- API directe : `server/tests/api_version_and_authorization.sh` (autorisations 401/403/200 par rôle sur tous les endpoints admin ; 426/503 ; exemptions ; échec ouvert). Résultat sur base de test : **tous les tests passent**.

## 9. Limites connues
- Pas de limitation de débit (rate limiting) sur les endpoints admin.
- Le contrôle de version est une barrière d'expérience et de compatibilité, **pas une défense contre un client malveillant** (l'en-tête est déclaratif) ; la sécurité repose sur l'authentification et les droits.
