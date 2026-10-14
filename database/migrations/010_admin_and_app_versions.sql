-- Migration 010 — Administration de la console Web + gestion des versions Android.
--
-- NON DESTRUCTIVE : une colonne nullable + un index + 3 nouvelles tables. Aucune donnée existante
-- n'est modifiée ni supprimée ; tous les comptes actuels gardent `admin_role = NULL` (utilisateur
-- ordinaire, aucun accès administrateur).
--
-- Rollback manuel (ordre inverse des dépendances) :
--   DROP TABLE admin_audit_log; DROP TABLE app_version_policy; DROP TABLE android_versions;
--   ALTER TABLE users DROP INDEX idx_users_created_at, DROP COLUMN admin_role;
--
-- Attribution du PREMIER Super Admin : jamais par l'API ni depuis le navigateur. Soit en ligne de
-- commande (`php server/scripts/grant_role.php --email=... --role=super_admin`), soit en SQL :
--   UPDATE users SET admin_role = 'super_admin' WHERE email = 'adresse@exemple.com' AND deleted_at IS NULL;

-- Rôle d'administration : NULL (aucun) | 'admin' | 'super_admin'. Valeurs validées côté PHP
-- (`api/utils/permissions.php`) ; le serveur relit TOUJOURS cette colonne, jamais le client.
ALTER TABLE users ADD COLUMN admin_role VARCHAR(16) NULL AFTER email;

-- Statistiques d'inscriptions (regroupement par jour sur `created_at`) et tri de la liste.
ALTER TABLE users ADD INDEX idx_users_created_at (created_at);

-- Versions publiées de l'application Android. `version_code` = versionCode Android officiel
-- (entier) : seule valeur utilisée pour comparer, jamais `version_name`.
-- Statuts : draft | published | deprecated | withdrawn.
CREATE TABLE IF NOT EXISTS android_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_name VARCHAR(32) NOT NULL,
    version_code INT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'draft',
    release_notes TEXT NULL,
    update_url VARCHAR(512) NULL,
    published_at BIGINT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    updated_by CHAR(36) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_android_versions_code (version_code),
    KEY idx_android_versions_status (status, version_code),
    CONSTRAINT fk_android_versions_user FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Politique de mise à jour : UNE SEULE ligne (id = 1), source de vérité unique lue par l'app
-- Android via `api/app/version_policy.php`.
--   - Un client est BLOQUÉ si force_update = 1 ET version_code installé < min_version_code.
--   - `block_unversioned_clients` : bloque aussi les clients qui n'envoient aucune version (anciennes
--     installations) — à activer seulement quand la version qui envoie sa version est déployée.
CREATE TABLE IF NOT EXISTS app_version_policy (
    id TINYINT UNSIGNED NOT NULL,
    min_version_code INT UNSIGNED NOT NULL DEFAULT 0,
    force_update TINYINT(1) NOT NULL DEFAULT 0,
    update_url VARCHAR(512) NOT NULL,
    block_unversioned_clients TINYINT(1) NOT NULL DEFAULT 0,
    maintenance_enabled TINYINT(1) NOT NULL DEFAULT 0,
    maintenance_message VARCHAR(500) NULL,
    updated_at BIGINT NOT NULL,
    updated_by CHAR(36) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_app_version_policy_user FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Valeurs initiales NEUTRES : aucune mise à jour forcée, aucun blocage tant que le Super Admin
-- n'a rien configuré.
INSERT IGNORE INTO app_version_policy
    (id, min_version_code, force_update, update_url, block_unversioned_clients, maintenance_enabled, updated_at)
VALUES
    (1, 0, 0, 'https://play.google.com/store/apps/details?id=com.naniger.arzikina', 0, 0,
     CAST(UNIX_TIMESTAMP() * 1000 AS UNSIGNED));

-- Journal des actions d'administration (append-only : aucun endpoint ne le modifie ni ne le purge).
-- `actor_label` conserve l'identité (e-mail) à l'instant de l'action : l'historique reste lisible
-- même si le compte est supprimé (`actor_user_id` passe alors à NULL).
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id CHAR(36) NULL,
    actor_label VARCHAR(191) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NULL,
    target_id VARCHAR(64) NULL,
    old_value MEDIUMTEXT NULL,
    new_value MEDIUMTEXT NULL,
    ip_address VARCHAR(45) NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_admin_audit_created (created_at),
    KEY idx_admin_audit_action (action, created_at),
    CONSTRAINT fk_admin_audit_user FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
