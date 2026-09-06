-- Arzikina — Migration 002 : colonne `photo_path` sur `users` (cahier des charges "Gestion de la
-- photo de profil").
--
-- CONTRAIREMENT à 001_initial_schema.sql, la table `users` existe DÉJÀ en production avec des
-- comptes réels (voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md) — c'est donc un vrai `ALTER TABLE`,
-- premier de ce projet (toutes les évolutions précédentes du schéma ont jusqu'ici ajouté des
-- TABLES neuves, jamais modifié une table existante en production). À exécuter UNE SEULE FOIS,
-- manuellement, sur la base réelle (voir README de ce dossier ou la doc de déploiement du projet) —
-- aucun outil de migration automatique n'existe encore dans ce projet.
--
-- `photo_path` : chemin SERVEUR relatif (`avatars/{user_id}/{version}.jpg`), stocké et géré par
-- `server/api/profile/upload_photo.php`/`delete_photo.php` — jamais les octets de l'image en base
-- (voir la KDoc de tête de ces fichiers). `NULL` = aucune photo (avatar par défaut côté client).
--
-- Pas de nouvelle colonne de version : les colonnes `version`/`updated_at` déjà présentes sur
-- `users` (schéma initial) sont réutilisées comme mécanisme de versionnement/invalidation de cache
-- de la photo — voir le commentaire de tête de la table `users` dans 001_initial_schema.sql, mis à
-- jour en miroir de cette migration pour que les installations neuves partent directement du bon
-- schéma.
--
-- Idempotent : si cette colonne existe déjà (migration déjà appliquée), la commande échoue avec une
-- erreur MySQL explicite ("Duplicate column name") plutôt que de corrompre quoi que ce soit —
-- vérifier `DESCRIBE users;` avant de relancer en cas de doute.

ALTER TABLE users
    ADD COLUMN photo_path VARCHAR(255) NULL AFTER security_answer_hash;
