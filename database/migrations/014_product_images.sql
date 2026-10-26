-- Arzikina — Photos de produits (module Boutique), suite de 013_shop.sql.
--
-- À exécuter MANUELLEMENT sur la base de production (même convention que 001 à 013), UNE SEULE FOIS :
-- `ALTER TABLE ... ADD COLUMN` n'est pas rejouable (une seconde exécution échoue avec « Duplicate column
-- name », sans dommage).
--
-- ORDRE DE DÉPLOIEMENT : exécuter cette migration AVANT de déployer `api/config/entity_sync_configs.php`
-- (l'entrée `products` référence la nouvelle colonne ; sans elle, toute écriture d'un produit échouerait).
--
-- Choix de schéma
-- - Même principe que le logo d'émetteur : on stocke le NOM du fichier (UUID + .jpg), jamais l'image. Les
--   images passent par `api/shop/upload_product_image.php` / `get_product_image.php` (stockage privé protégé,
--   Bearer exigé) ; la suppression du compte efface tout le dossier de l'utilisateur.
-- - NULLABLE : un produit sans photo, ou un client qui ne connaît pas encore les photos, laisse NULL.

ALTER TABLE products
    ADD COLUMN image_file_name VARCHAR(80) NULL AFTER expiry_date;
