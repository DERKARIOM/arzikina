# Déploiement de l'API Arzikina sur Hostinger

Guide spécifique au backend PHP/MySQL du projet (`server/` + `database/migrations/`), basé sur un audit du code actuel. Il part du principe que tu as un hébergement Hostinger avec PHP + MySQL (plan Premium/Business ou VPS).

## 0. Ce que le code actuel suppose (à savoir avant de commencer)

- **PHP 8.0 minimum** (le code utilise `match`), pas de version maximale connue — Hostinger propose généralement PHP 8.1/8.2/8.3 au choix dans hPanel, prends la plus récente disponible.
- **Aucune dépendance Composer** : rien à installer, juste des fichiers `.php` à copier.
- **Pas de front controller** : chaque endpoint est un fichier `.php` appelé directement par son chemin (`.../api/auth/login.php`, `.../api/sync/push.php`, etc.), pas de routage à configurer.
- **La config actuelle pointe vers des chemins codés en dur** (`/home/Admin/config_arzikina.php` et `/home/Admin/config_arzikina_secrets.php`, en dehors du repo Git) — ces chemins **ne correspondront pas** à ton compte Hostinger. C'est le point d'adaptation le plus important de ce guide (étape 3).
- **Authentification par token opaque** (`Authorization: Bearer <token>`), pas de JWT ni de sessions PHP — dépend de la bonne transmission de l'en-tête `Authorization` par le serveur web (point d'attention classique en hébergement mutualisé, voir étape 7).
- **Un seul dossier d'upload actif** : `server/avatars/` (photos de profil), doit rester accessible en écriture par PHP.

## 1. Créer la base de données MySQL

Dans hPanel : **Bases de données → Bases de données MySQL**.

1. Crée une base (ex. `u123456789_arzikina`) et un utilisateur MySQL dédié avec mot de passe fort — Hostinger préfixe automatiquement le nom de la base et de l'utilisateur par ton identifiant de compte.
2. Note précieusement : nom d'hôte MySQL (généralement `localhost` sur mutualisé), nom de la base, nom d'utilisateur, mot de passe.
3. Ouvre **phpMyAdmin** (accessible depuis la même page hPanel) sur cette base.

## 2. Exécuter les migrations SQL, dans l'ordre

Dans `database/migrations/`, exécute ces 4 fichiers **un par un, dans cet ordre exact**, via l'onglet "Importer" de phpMyAdmin (ou l'onglet SQL, copier-coller) :

1. `001_initial_schema.sql` (crée toutes les tables — InnoDB, `utf8mb4_unicode_ci`)
2. `002_add_profile_photo_to_users.sql`
3. `003_verify_and_fix_recurring_transactions.sql`
4. `004_add_display_order_to_accounts.sql`

Vérifie après coup que les tables existent bien (`users`, `accounts`, `transactions`, `auth_tokens`, etc.) et que le moteur est InnoDB / charset `utf8mb4` (visible dans l'onglet "Opérations" de chaque table dans phpMyAdmin).

## 3. Adapter la configuration de connexion (étape critique)

Le code actuel (`server/api/config/database.php`) fait :
```php
require_once '/home/Admin/config_arzikina.php';
require_once '/home/Admin/config_arzikina_secrets.php';
```

Sur Hostinger, ton répertoire personnel ressemble à `/home/u123456789/domains/tondomaine.com/` (visible dans le Gestionnaire de fichiers hPanel, ou via SSH avec `pwd`/`echo $HOME` si ton plan inclut le SSH). Deux options, choisis-en une :

**Option A — la plus simple, sans toucher au code PHP** : recrée exactement les deux fichiers attendus, au bon endroit, avec le bon nom d'utilisateur système. Dans le Gestionnaire de fichiers, remonte au-dessus de `public_html` (ex. `/home/u123456789/`) et crée :

`config_arzikina.php` :
```php
<?php
define('Host', 'localhost');
define('NomDB', 'u123456789_arzikina');
define('NomUtilisateur', 'u123456789_xxxx');
define('MotDePasse', 'le-mot-de-passe-mysql-choisi-étape-1');
```

`config_arzikina_secrets.php` (copie adaptée de `server/api/config/secrets.example.php`) :
```php
<?php
define('TOKEN_SIGNING_KEY', 'GÉNÈRE-UNE-VALEUR-ALÉATOIRE-ICI');
define('TOKEN_EXPIRY_SECONDS', 60 * 60 * 24 * 30);
```
Génère `TOKEN_SIGNING_KEY` en local avec `php -r "echo bin2hex(random_bytes(32));"` (ou n'importe quel générateur de chaîne aléatoire de 64 caractères hex) et colle le résultat.

Important : ce dossier doit être **hors de `public_html`** (donc jamais accessible par une URL) — c'est déjà comme ça que le code actuel fonctionne sur son serveur d'origine, à reproduire ici.

**Option B — rendre le code portable** (plus propre à terme, mais modifie `database.php`) : remplacer les deux chemins codés en dur par une détection relative (ex. `dirname(__DIR__, 3) . '/config_arzikina.php'`) ou par des variables d'environnement lues via `getenv()`. Je peux le faire si tu préfères cette voie — dis-le-moi et je le traiterai comme un petit chantier séparé (avec sa propre étape de validation), plutôt que de le glisser ici.

## 4. Décider où exposer l'API

Deux façons de faire, à choisir selon ta préférence :

- **Sous-domaine dédié** (recommandé, plus propre) : dans hPanel, crée un sous-domaine `api.tondomaine.com` avec pour racine de document le dossier où tu déploieras `server/`. L'API sera alors à `https://api.tondomaine.com/api/...`.
- **Dossier dans le domaine principal** : dépose le contenu de `server/` dans `public_html/arzikina/` — cohérent avec l'URL actuelle côté Android (`.../arzikina/`), l'API sera à `https://tondomaine.com/arzikina/api/...`.

Dans les deux cas, **ne déploie que `server/api/` et `server/avatars/`** sur le serveur web. Ne mets **pas** `server/scripts/` (script de maintenance, jamais destiné à être accessible par le web) ni `server/postman/` (juste une collection de tests, inutile en production) dans le dossier public — garde-les seulement dans ton repo local/Git.

## 5. Uploader les fichiers

Via le Gestionnaire de fichiers hPanel, FTP/SFTP (identifiants dans hPanel → Comptes FTP), ou SSH+`scp`/`git clone` si ton plan le permet :

1. Copie `server/api/` entier vers la racine choisie à l'étape 4.
2. Copie `server/avatars/` (avec son `.htaccess`) au même niveau que `api/`.
3. Vérifie la structure finale : `.../api/auth/login.php`, `.../api/sync/push.php`, `.../avatars/.htaccess`, etc.

## 6. Permissions du dossier `avatars/`

`upload_photo.php` fait un `mkdir($dir, 0755, true)` et écrit des fichiers dedans — assure-toi que `avatars/` est bien accessible en écriture par PHP (généralement automatique sur Hostinger puisque PHP tourne avec l'utilisateur du compte, mais vérifie via le Gestionnaire de fichiers si un upload échoue : clic droit → Permissions → `755` sur le dossier).

## 7. Activer HTTPS

Dans hPanel : **Sécurité → SSL**, active le certificat Let's Encrypt gratuit sur le domaine/sous-domaine choisi (activation automatique en général, parfois un délai de quelques minutes à quelques heures). L'API doit être servie en `https://`, pas `http://` — nécessaire à terme pour qu'Android puisse retirer l'autorisation de trafic non chiffré (voir `network_security_config.xml`, qui prévoit déjà cette bascule).

## 8. Point d'attention : l'en-tête `Authorization`

Sur certains hébergements mutualisés (Apache/LiteSpeed en mode CGI/FastCGI), l'en-tête `Authorization` envoyé par le client est **supprimé avant d'atteindre PHP**, ce qui casserait toute l'authentification (`auth_middleware.php` renverrait systématiquement `missing_token`). C'est un problème connu et fréquent, pas spécifique à ce projet.

Pour t'en prémunir préventivement, ajoute ce bloc dans un `.htaccess` à la racine de `api/` (nouveau fichier à créer, n'existe pas encore dans le repo) :
```apache
RewriteEngine On
RewriteCond %{HTTP:Authorization} ^(.*)
RewriteRule .* - [E=HTTP_AUTHORIZATION:%1]
```
Le code de `auth_middleware.php` a déjà un repli via `apache_request_headers()` qui couvre une partie des cas, mais ce `.htaccess` est la protection la plus fiable — à tester en priorité à l'étape suivante.

## 9. Tester après déploiement

Le repo contient déjà une collection Postman toute prête : `server/postman/Arzikina-API.postman_collection.json`.

1. Importe-la dans Postman (ou Insomnia).
2. Change l'URL de base de la collection vers ton URL Hostinger (`https://api.tondomaine.com` ou `https://tondomaine.com/arzikina`).
3. Teste dans l'ordre : `POST /api/auth/register.php` (crée un compte de test) → `POST /api/auth/login.php` (récupère un token) → un endpoint protégé comme `POST /api/sync/pull.php` avec l'en-tête `Authorization: Bearer <token>` reçu — si ça répond `missing_token`/`invalid_token` alors que le token est bon, c'est le problème de l'étape 8.
4. Vérifie qu'aucune erreur PHP brute (stack trace, chemin de fichier) n'apparaît jamais dans une réponse — `json_response.php` est déjà conçu pour ça, mais vérifie que `display_errors` est désactivé côté PHP en production (hPanel → PHP Configuration → `display_errors = Off`, `log_errors = On`).

## 10. Mettre à jour les applications clientes

Une fois l'API confirmée fonctionnelle en HTTPS :

- **Android** : `app/src/main/java/com/arzikina/ne/data/remote/RemoteConfig.kt`, ligne `const val BASE_URL = "http://192.168.49.1:2222/arzikina/"` → remplacer par ta nouvelle URL Hostinger (avec `https://` et le `/` final). Recompiler et redistribuer l'app.
- **Web** (`arzikina-web-sync`) : pas besoin de recompiler dans l'immédiat — l'app expose déjà un champ "Adresse du serveur" dans l'écran de connexion (`routes/connexion.tsx`) qui écrase la valeur par défaut via `localStorage`. Tu peux mettre à jour la valeur par défaut dans `src/lib/api/client.ts` (`DEFAULT_BASE_URL`) pour que les nouveaux utilisateurs n'aient plus besoin de la saisir manuellement.

## 11. Nettoyage / sécurité finale

- Ne committe **jamais** `config_arzikina.php`/`config_arzikina_secrets.php` dans Git — ils vivent uniquement sur le serveur, hors du repo (déjà cohérent avec le `.gitignore` actuel qui exclut `secrets.php`).
- Vérifie que `server/avatars/.htaccess` est bien monté (empêche l'exécution de scripts uploadés dans ce dossier).
- Si tu choisis un sous-dossier `public_html/arzikina/` plutôt qu'un sous-domaine, vérifie qu'aucun autre contenu sensible du domaine principal n'est exposé par erreur au même niveau.
- Programme une sauvegarde régulière de la base MySQL (hPanel propose des sauvegardes automatiques selon le plan) — aucune logique de sauvegarde serveur n'existe dans le code actuel, c'est entièrement à la charge de l'hébergement.

---

Dis-moi si tu veux que je t'aide pour l'étape 3 (rendre `database.php` portable plutôt que recréer les chemins en dur) ou pour ajouter le `.htaccess` de l'étape 8 directement dans le repo (`server/api/.htaccess`) — ce sont deux petits chantiers que je peux faire proprement, chacun avec sa propre validation.
