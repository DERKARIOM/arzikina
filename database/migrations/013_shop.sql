-- Arzikina — Synchronisation du module « Boutique » (produits, stocks, ventes).
--
-- Contexte : le module existe côté Android (Room v36, tables `shops`, `product_categories`,
-- `products`, `stock_movements`, `sales`, `sale_lines`, `sale_payments`) et a été construit avec les
-- colonnes de synchronisation déjà posées. Cette migration ajoute leur pendant serveur ; entrées
-- correspondantes dans `api/config/entity_sync_configs.php` ajoutées avec ce script.
--
-- À exécuter MANUELLEMENT sur la base de production (aucun outil de migration automatique dans ce
-- projet — même convention que 001 à 012). Rejouable : `CREATE TABLE IF NOT EXISTS`.
-- Le type de compte `BOUTIQUE` n'exige AUCUNE modification : `accounts.type` est un VARCHAR(32).
--
-- Choix de schéma
-- - Aucune `FOREIGN KEY` autre que `user_id` : une ligne synchronisée peut arriver avant son parent
--   (ordre non garanti, appareils hors ligne). Les colonnes `*_id` de liaison contiennent le `syncId`
--   (CHAR(36)) de la ligne référencée, comme `transactions.account_id` ; payload `*SyncId`.
-- - PAS d'index UNIQUE sur `barcode`, `sku` ni `reference` : deux appareils hors ligne peuvent créer
--   les mêmes valeurs ; l'identité est `id` (UUID). L'anti-doublon est fait côté client.
-- - `products.stock_milli` N'EXISTE PAS ici : le stock est le cache local d'un appareil, recalculé à
--   partir des `stock_movements` (ajoutés seulement, donc sans conflit entre appareils).
-- - `products.image_file_name` N'EXISTE PAS ici : les images produit restent locales en V1.
-- - Montants en unités mineures et quantités en milli-unités : BIGINT, comme `transactions.amount`.
-- - `stock_movements` et `sale_lines` sont immuables ; `updated_at`/`deleted_at`/`version` restent
--   présents car le registre de synchronisation générique les attend sur toute entité.

CREATE TABLE IF NOT EXISTS shops (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(191) NOT NULL,
    account_id CHAR(36) NULL,
    currency_code VARCHAR(8) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_shops_user_updated (user_id, updated_at),
    CONSTRAINT fk_shops_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_categories (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    shop_id CHAR(36) NOT NULL,
    name VARCHAR(191) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_product_categories_user_updated (user_id, updated_at),
    KEY idx_product_categories_shop (shop_id),
    CONSTRAINT fk_product_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS products (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    shop_id CHAR(36) NOT NULL,
    category_id CHAR(36) NULL,
    name VARCHAR(191) NOT NULL,
    description TEXT NULL,
    sku VARCHAR(64) NULL,
    barcode VARCHAR(64) NULL,
    unit VARCHAR(32) NOT NULL,
    purchase_price_minor BIGINT NULL,
    sale_price_minor BIGINT NOT NULL,
    min_stock_milli BIGINT NOT NULL DEFAULT 0,
    expiry_date BIGINT NULL,
    is_archived TINYINT(1) NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_products_user_updated (user_id, updated_at),
    KEY idx_products_shop_name (shop_id, name),
    KEY idx_products_shop_barcode (shop_id, barcode),
    CONSTRAINT fk_products_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_movements (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    product_id CHAR(36) NOT NULL,
    type VARCHAR(32) NOT NULL,
    delta_milli BIGINT NOT NULL,
    unit_cost_minor BIGINT NULL,
    reason TEXT NULL,
    reference VARCHAR(191) NULL,
    sale_id CHAR(36) NULL,
    occurred_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_stock_movements_user_updated (user_id, updated_at),
    KEY idx_stock_movements_product (product_id, occurred_at),
    CONSTRAINT fk_stock_movements_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sales (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    shop_id CHAR(36) NOT NULL,
    reference VARCHAR(64) NOT NULL,
    sold_at BIGINT NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    customer_name VARCHAR(191) NULL,
    customer_phone VARCHAR(64) NULL,
    subtotal_minor BIGINT NOT NULL,
    discount_minor BIGINT NOT NULL DEFAULT 0,
    total_minor BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    cancelled_at BIGINT NULL,
    cancel_reason TEXT NULL,
    issued_receipt_id CHAR(36) NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_sales_user_updated (user_id, updated_at),
    KEY idx_sales_shop_sold (shop_id, sold_at),
    CONSTRAINT fk_sales_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sale_lines (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    sale_id CHAR(36) NOT NULL,
    product_id CHAR(36) NOT NULL,
    product_name VARCHAR(191) NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    unit_cost_minor BIGINT NULL,
    quantity_milli BIGINT NOT NULL,
    line_total_minor BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_sale_lines_user_updated (user_id, updated_at),
    KEY idx_sale_lines_sale (sale_id),
    CONSTRAINT fk_sale_lines_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sale_payments (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    sale_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    method VARCHAR(32) NOT NULL,
    amount_minor BIGINT NOT NULL,
    paid_at BIGINT NOT NULL,
    transaction_id CHAR(36) NULL,
    reversal_transaction_id CHAR(36) NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_sale_payments_user_updated (user_id, updated_at),
    KEY idx_sale_payments_sale (sale_id),
    CONSTRAINT fk_sale_payments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Rollback (manuel) :
--   DROP TABLE IF EXISTS sale_payments;
--   DROP TABLE IF EXISTS sale_lines;
--   DROP TABLE IF EXISTS sales;
--   DROP TABLE IF EXISTS stock_movements;
--   DROP TABLE IF EXISTS products;
--   DROP TABLE IF EXISTS product_categories;
--   DROP TABLE IF EXISTS shops;
