-- 001_add_google_id_to_users.sql
--
-- Authentification Google (voir api/auth/google_login.php, api/auth/link_google_account.php) :
-- identifie un utilisateur Google par son identifiant STABLE (claim "sub" du jeton d'identite),
-- jamais par son e-mail ou son nom. Nullable : un compte cree par mot de passe classique n'a pas
-- de compte Google associe (NULL). UNIQUE : empeche qu'un meme compte Google soit rattache a deux
-- comptes Arzikina differents (double protection, applicative ET base de donnees).
--
-- Remarque : aucun dossier de migrations versionne n'existait encore dans ce depot au moment
-- d'ecrire ce fichier (le schema initial - table users, auth_tokens, etc. - a ete cree directement
-- en SQL sur le serveur, sans trace dans Git). Ce fichier demarre donc la numerotation a 001 et
-- sert de reference pour les migrations futures.
--
-- Application : MANUELLE (hebergement mutualise Hostinger, pas d'outil de migration automatise en
-- place) - copier-executer ce SQL dans phpMyAdmin (ou equivalent) sur la base de PRODUCTION, une
-- seule fois. Preserve toutes les lignes existantes de `users` (ajout de colonne nullable, aucune
-- donnee supprimee ni modifiee).
--
-- Reversible : voir le ROLLBACK en bas de fichier.

ALTER TABLE users
    ADD COLUMN google_id VARCHAR(255) NULL UNIQUE AFTER email;

-- ROLLBACK (a n'executer qu'en cas de retour arriere volontaire) :
-- ALTER TABLE users DROP COLUMN google_id;
