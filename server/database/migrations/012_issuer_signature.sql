-- Arzikina — Signature manuscrite des reçus émis (utilitaire « Gestion de reçus »).
--
-- Contexte : suite de 011_issued_receipts.sql. Le profil émetteur peut avoir une signature (dessinée
-- sur Android ou importée en image), et chaque reçu SIGNÉ en garde une copie figée.
--
-- À exécuter MANUELLEMENT sur la base de production (même convention que 001 à 011), UNE SEULE FOIS :
-- `ALTER TABLE ... ADD COLUMN` n'est pas rejouable (une seconde exécution échoue avec « Duplicate
-- column name », sans dommage). Entrées correspondantes dans `api/config/entity_sync_configs.php`.
--
-- Choix de schéma
-- - Même principe que le logo : on stocke le NOM du fichier (UUID + .png/.jpg), jamais l'image.
--   Les images passent par `api/issuer/upload_logo.php` / `get_logo.php` (stockage privé protégé,
--   Bearer exigé) ; la suppression du compte efface tout le dossier de l'utilisateur.
-- - NULLABLE : un reçu non signé, ou un client qui ne connaît pas encore la signature, laisse NULL.
-- - `issued_receipts.issuer_signature_file_name` : copie figée au moment de l'émission, comme
--   `issuer_logo_file_name` ; changer la signature du profil ne modifie pas les reçus déjà émis.

ALTER TABLE issuer_profiles
    ADD COLUMN signature_file_name VARCHAR(80) NULL AFTER logo_file_name;

ALTER TABLE issued_receipts
    ADD COLUMN issuer_signature_file_name VARCHAR(80) NULL AFTER issuer_logo_file_name;
