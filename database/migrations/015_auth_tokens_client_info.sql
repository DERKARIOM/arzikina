-- Arzikina — Application utilisée par session : plateforme (android | ios | web) et versionCode.
--
-- Contexte : la console d'administration (fiche d'un utilisateur) affiche, pour sa DERNIÈRE activité,
-- quelle application il utilisait (Web ou mobile) et sa version. La donnée est rattachée à la SESSION
-- (`auth_tokens`), mise à jour à chaque requête authentifiée en même temps que `last_used_at`.
--
-- - `client_platform`     : 'android' | 'ios' | 'web' | NULL (inconnu : session antérieure, ancien client) ;
-- - `client_version_code` : `versionCode` entier annoncé par l'application mobile (en-tête
--                           `X-App-Version-Code`) ; NULL pour le Web et les clients sans version.
--
-- Migration ADDITIVE : aucune ligne existante n'est modifiée. À exécuter UNE fois (phpMyAdmin), AVANT de
-- déployer le PHP qui écrit ces colonnes — le code reste malgré tout tolérant si elle manque.

ALTER TABLE auth_tokens
    ADD COLUMN client_platform VARCHAR(16) NULL DEFAULT NULL,
    ADD COLUMN client_version_code INT NULL DEFAULT NULL;
