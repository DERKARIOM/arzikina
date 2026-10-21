-- Arzikina — Synchronisation de « Gestion de reçus » (reçus ÉMIS par l'utilisateur).
--
-- Contexte : l'utilitaire existe côté Android (Room v34, tables `issued_receipts` et
-- `issuer_profiles`, voir `IssuedReceiptEntity`/`IssuerProfileEntity`) et a été construit avec les
-- colonnes de synchronisation déjà posées (`syncId`, `deletedAt`, `version`). Cette migration ajoute
-- leur pendant serveur ; entrées correspondantes dans `api/config/entity_sync_configs.php`
-- (`issuer_profiles`, `issued_receipts`) ajoutées avec ce script.
--
-- À exécuter MANUELLEMENT sur la base de production (aucun outil de migration automatique dans ce
-- projet — même convention que 001 à 010). Rejouable : `CREATE TABLE IF NOT EXISTS`.
--
-- À ne pas confondre avec la table `receipts` (justificatifs importés, utilitaire « Justificatifs »).
--
-- Choix de schéma
-- - Aucune `FOREIGN KEY` autre que `user_id` : un reçu est un document autonome (pas de lien vers
--   comptes ni transactions).
-- - L'instantané de l'émetteur figé dans chaque reçu est stocké en COLONNES (`issuer_*`) plutôt qu'en
--   JSON : lisible par la console Web sans décodage, et identique à `issuer_profiles`.
-- - `pricing_json` (lignes, remise, taxe) reste un JSON texte : jamais interrogé seul, un reçu est
--   un instantané (même décision que côté Android). `NULL` = reçu simple (un seul montant).
-- - Montants en unités mineures (BIGINT), comme `transactions.amount`.
-- - PAS d'index UNIQUE sur `(user_id, number)` : deux appareils hors ligne peuvent attribuer le même
--   numéro ; le serveur doit accepter les deux lignes, c'est le CLIENT qui renumérote le doublon le
--   plus récent (voir le rapport de l'étape « Synchronisation »). Un index simple sert la recherche.
-- - `issuer_profiles` : AU PLUS une ligne par utilisateur (`uq_issuer_profiles_user`, comme
--   `user_preferences`). Les clients utilisent un identifiant DÉTERMINISTE pour cette ligne, donc deux
--   appareils créent « la même » ligne (CREATE idempotent) au lieu d'entrer en collision.
-- - `logo_file_name` / `issuer_logo_file_name` : NOM du fichier (UUID + .png/.jpg), jamais l'image.
--   Les images passent par `api/issuer/upload_logo.php` / `get_logo.php` (stockage privé sur disque).

CREATE TABLE IF NOT EXISTS issuer_profiles (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    business_name VARCHAR(191) NOT NULL,
    address TEXT NOT NULL,
    phone VARCHAR(64) NOT NULL,
    email VARCHAR(191) NULL,
    tax_id VARCHAR(191) NULL,
    logo_file_name VARCHAR(80) NULL,
    default_issued_by_name VARCHAR(191) NULL,
    default_issued_by_title VARCHAR(191) NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_issuer_profiles_user (user_id),
    CONSTRAINT fk_issuer_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS issued_receipts (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    number VARCHAR(64) NOT NULL,
    issued_at BIGINT NOT NULL,
    customer_name VARCHAR(191) NOT NULL,
    customer_phone VARCHAR(64) NULL,
    description TEXT NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    transaction_reference VARCHAR(191) NULL,
    notes TEXT NULL,
    issued_by_name VARCHAR(191) NULL,
    issued_by_title VARCHAR(191) NULL,
    pricing_json MEDIUMTEXT NULL,
    issuer_business_name VARCHAR(191) NOT NULL,
    issuer_address TEXT NOT NULL,
    issuer_phone VARCHAR(64) NOT NULL,
    issuer_email VARCHAR(191) NULL,
    issuer_tax_id VARCHAR(191) NULL,
    issuer_logo_file_name VARCHAR(80) NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_issued_receipts_user_updated (user_id, updated_at),
    KEY idx_issued_receipts_user_number (user_id, number),
    KEY idx_issued_receipts_user_issued (user_id, issued_at),
    CONSTRAINT fk_issued_receipts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Rollback (manuel) :
--   DROP TABLE IF EXISTS issued_receipts;
--   DROP TABLE IF EXISTS issuer_profiles;
