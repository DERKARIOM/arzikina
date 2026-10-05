-- Arzikina — appareils qui reçoivent les notifications push (Firebase Cloud Messaging).
--
-- Une ligne = une INSTALLATION de l'application (Android aujourd'hui, iOS/Web demain), identifiée
-- par `installation_id` : un UUID aléatoire généré par l'application à sa première ouverture.
-- Ce n'est PAS `ANDROID_ID` ni aucun identifiant matériel : il disparaît avec une désinstallation
-- ou un effacement des données, et ne permet donc aucun suivi de l'appareil au-delà de l'app
-- (voir claude/fcm/AUDIT-FCM-ANDROID.md, sections 3 et 4.3).
--
-- Colonnes :
-- - `user_id`         : compte actuellement connecté sur cette installation. Un changement de compte
--                       DÉPLACE la ligne (upsert par `installation_id`) : jamais deux comptes pour
--                       une même installation, donc aucune notification envoyée à l'ancien compte ;
-- - `fcm_token`       : token d'enregistrement FCM (opaque, ~160-200 caractères ASCII aujourd'hui,
--                       512 par sécurité). UNIQUE : un token n'appartient qu'à une installation ;
-- - `platform`        : `android` | `ios` | `web` (validé par `devices/register.php`) ;
-- - `app_version`     : version de l'application, pour adapter un futur format de payload ;
-- - `locale`          : langue de l'application (`fr`, `en`…), pour de futurs textes serveur ;
-- - `auth_token_id`   : session (`auth_tokens.id`) qui a enregistré l'appareil. L'envoi n'a lieu que
--                       si cette session est encore valide : une session révoquée ou expirée coupe
--                       les notifications même si l'application n'a pas pu prévenir le serveur ;
-- - `last_seen_at`    : dernier enregistrement reçu (nettoyage des appareils inactifs, plus tard) ;
-- - `revoked_at`      : déconnexion, désinscription ou token refusé par FCM (NULL = actif).
-- Toutes les dates en epoch millis (BIGINT), comme le reste du schéma.
--
-- À exécuter MANUELLEMENT sur la base de production (même convention que 001 à 008), AVANT de
-- publier la version Android qui appelle `api/devices/register.php`.
--
-- Non destructif : nouvelle table uniquement, aucune table existante modifiée.
--
-- Clés étrangères :
-- - `user_id` → `users` ON DELETE CASCADE : supprimer un compte supprime ses appareils ;
-- - `auth_token_id` → `auth_tokens` ON DELETE SET NULL : la purge d'anciennes sessions ne supprime
--   pas l'appareil, mais celui-ci ne reçoit plus rien (jointure obligatoire à l'envoi) tant qu'il ne
--   s'est pas réenregistré avec une session valide.
CREATE TABLE IF NOT EXISTS user_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id CHAR(36) NOT NULL,
    installation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fcm_token VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    platform VARCHAR(16) NOT NULL,
    app_version VARCHAR(32) NULL,
    locale VARCHAR(16) NULL,
    auth_token_id BIGINT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    revoked_at BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_devices_installation (installation_id),
    UNIQUE KEY uq_user_devices_fcm_token (fcm_token),
    KEY idx_user_devices_user_active (user_id, revoked_at),
    KEY idx_user_devices_auth_token (auth_token_id),
    CONSTRAINT fk_user_devices_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_devices_auth_token FOREIGN KEY (auth_token_id) REFERENCES auth_tokens (id)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
