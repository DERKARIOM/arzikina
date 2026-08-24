-- Arzikina — Migration 001 : schéma initial de synchronisation.
--
-- Contexte : la base `arzikina` a été confirmée VIDE (aucune table existante) avant l'écriture de
-- cette migration — voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 1. Cette migration est
-- donc une création pure, aucune donnée existante à préserver, aucun ALTER TABLE.
--
-- Convention d'identifiants (voir section 6.3 du document, option B) : chaque table synchronisée
-- utilise un UUID (CHAR(36), format standard avec tirets) comme clé primaire. C'est EXACTEMENT la
-- même valeur que la colonne `syncId` ajoutée côté Android (voir MIGRATION_22_23) — l'id Room
-- local (Long, propre à chaque appareil) n'a aucun sens ici et ne traverse jamais le réseau.
-- L'application (PHP) génère l'UUID à la création si l'appareil n'en a pas encore fourni un.
--
-- Toutes les tables métier partagent 4 colonnes de synchronisation, pour les mêmes raisons que
-- côté Android (voir section 8 du document) :
--   - updated_at BIGINT  : horodatage (millisecondes epoch, même unité que Kotlin
--     System.currentTimeMillis()) de dernière modification — base de la détection de conflit
--     Last-Write-Wins (section 9).
--   - deleted_at BIGINT NULL : suppression douce. NULL = ligne active.
--   - version INT : compteur de version optimiste, incrémenté à chaque écriture acceptée.
--   - created_at BIGINT : horodatage de création, jamais modifié après coup.
--
-- `card_secrets` (numéro de carte/CVV chiffrés côté Android) est volontairement ABSENTE de ce
-- schéma : décision validée section 6.2 du document (clé de chiffrement liée à l'Android Keystore
-- d'un seul appareil, non transportable telle quelle — hors périmètre v1).
--
-- Moteur InnoDB partout (transactions + clés étrangères), jeu de caractères utf8mb4 (Unicode
-- complet : accents, emoji éventuels dans les descriptions, plusieurs langues — cohérent avec
-- `SET NAMES 'utf8mb4'` déjà utilisé par connectBDD.php).

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================================
-- 1. UTILISATEURS ET AUTHENTIFICATION
-- =============================================================================================

-- Comptes utilisateurs serveur. Distinct du profil LOCAL Android (`UserEntity`) : un même compte
-- serveur peut être synchronisé vers plusieurs appareils (c'est tout l'objet de ce chantier).
--
-- `password_hash` : hachage calculé CÔTÉ SERVEUR par `password_hash()` (PHP, Argon2id/bcrypt) au
-- moment du login — voir section 6.1 du document. AUCUN rapport avec le hachage PBKDF2 calculé
-- localement par l'app Android (`util/PasswordHasher`), qui reste un mécanisme séparé pour la
-- connexion hors-ligne entre profils sur un même appareil.
CREATE TABLE IF NOT EXISTS users (
    id CHAR(36) NOT NULL,
    full_name VARCHAR(191) NOT NULL,
    username VARCHAR(191) NOT NULL,
    email VARCHAR(191) NOT NULL,
    phone_number VARCHAR(32) NULL,
    password_hash VARCHAR(255) NOT NULL,
    security_question VARCHAR(64) NOT NULL,
    security_answer_hash VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Tokens de session (voir section 6.1 et 12 du document) : jamais le mot de passe renvoyé à chaque
-- synchronisation, un seul login émet un token que l'app Android stocke via Android Keystore côté
-- client et renvoie dans l'en-tête `Authorization` de chaque appel `api/sync/*`.
--
-- `token_hash` : SHA-256 du token réel, JAMAIS le token en clair — même principe que le hachage
-- d'un mot de passe : une fuite de la base ne rend pas les tokens directement utilisables.
-- `device_id` : identifiant d'appareil Android (ex. ANDROID_ID), permet de lister/révoquer les
-- sessions par appareil (écran Paramètres, "Synchroniser maintenant" + gestion des appareils
-- connectés — fonctionnalité future, la colonne est prévue dès maintenant pour l'anticiper).
CREATE TABLE IF NOT EXISTS auth_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    device_id VARCHAR(191) NULL,
    device_label VARCHAR(191) NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    revoked_at BIGINT NULL,
    last_used_at BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_auth_tokens_token_hash (token_hash),
    KEY idx_auth_tokens_user (user_id),
    CONSTRAINT fk_auth_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Préférences synchronisables (voir section 3 du document : DataStore côté Android, pas une
-- entité Room — nouvelle table ici, sans équivalent Room direct). `biometricLockEnabled` reste
-- volontairement ABSENT : verrouillage biométrique explicitement PAR APPAREIL, jamais synchronisé
-- (même raisonnement que pour le système de sauvegarde fichier existant).
CREATE TABLE IF NOT EXISTS user_preferences (
    user_id CHAR(36) NOT NULL,
    theme_mode VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    currency_code CHAR(3) NOT NULL DEFAULT 'XOF',
    updated_at BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================================
-- 2. DONNÉES FINANCIÈRES DE BASE (comptes, catégories)
-- =============================================================================================

CREATE TABLE IF NOT EXISTS accounts (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(191) NOT NULL,
    icon VARCHAR(64) NOT NULL,
    color_argb BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    initial_balance_minor BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL DEFAULT 'CASH',
    card_last_four_digits VARCHAR(4) NULL,
    card_expiry_month TINYINT NULL,
    card_expiry_year SMALLINT NULL,
    is_excluded_from_statistics TINYINT(1) NOT NULL DEFAULT 0,
    mobile_money_package_name VARCHAR(191) NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_accounts_user_updated (user_id, updated_at),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS categories (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(191) NOT NULL,
    icon VARCHAR(64) NOT NULL,
    color_argb BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_categories_user_updated (user_id, updated_at),
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS persons (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(191) NOT NULL,
    phone VARCHAR(32) NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_persons_user_updated (user_id, updated_at),
    CONSTRAINT fk_persons_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================================
-- 3. TRANSACTIONS
-- =============================================================================================

-- `fee_transaction_id` et `receipt_id` : volontairement SANS contrainte FOREIGN KEY, même
-- raisonnement que côté Android (voir la doc de tête de `TransactionEntity.kt`) — liens
-- secondaires optionnels dont la cohérence (création/suppression conjointe) est gérée au niveau
-- applicatif (services PHP), pas au niveau base. `receipt_photo_uri` (chemin local à un appareil)
-- n'a pas d'équivalent ici, comme documenté section 4/5 du document d'architecture.
CREATE TABLE IF NOT EXISTS transactions (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    amount BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    account_id CHAR(36) NOT NULL,
    transfer_account_id CHAR(36) NULL,
    category_id CHAR(36) NULL,
    `date` BIGINT NOT NULL,
    description TEXT NOT NULL,
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    payment_method VARCHAR(32) NULL,
    fee_transaction_id CHAR(36) NULL,
    fee_type VARCHAR(32) NULL,
    receipt_id CHAR(36) NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_transactions_user_updated (user_id, updated_at),
    KEY idx_transactions_account (account_id),
    KEY idx_transactions_category (category_id),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_transfer_account FOREIGN KEY (transfer_account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================================
-- 4. BUDGETS ET OBJECTIFS D'ÉPARGNE
-- =============================================================================================

CREATE TABLE IF NOT EXISTS budgets (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    category_id CHAR(36) NOT NULL,
    period VARCHAR(32) NOT NULL,
    limit_amount BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    start_date BIGINT NULL,
    end_date BIGINT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_budgets_user_updated (user_id, updated_at),
    KEY idx_budgets_category (category_id),
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_budgets_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS savings_goals (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(191) NOT NULL,
    target_amount BIGINT NOT NULL,
    current_amount BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    deadline BIGINT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_savings_goals_user_updated (user_id, updated_at),
    CONSTRAINT fk_savings_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================================
-- 5. PRÊTS / EMPRUNTS
-- =============================================================================================

-- `transaction_id` (décaissement initial) : SANS FOREIGN KEY, même raisonnement applicatif que
-- `fee_transaction_id` ci-dessus (voir doc de tête de `LoanEntity.kt`/`LoanPaymentEntity.kt`).
CREATE TABLE IF NOT EXISTS loans (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    person_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    type VARCHAR(16) NOT NULL,
    amount BIGINT NOT NULL,
    amount_repaid BIGINT NOT NULL,
    remaining_amount BIGINT NOT NULL,
    start_date BIGINT NOT NULL,
    due_date BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL,
    reason_custom_text VARCHAR(255) NULL,
    repayment_mode VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    transaction_id CHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_loans_user_updated (user_id, updated_at),
    KEY idx_loans_person (person_id),
    KEY idx_loans_account (account_id),
    CONSTRAINT fk_loans_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_loans_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE CASCADE,
    CONSTRAINT fk_loans_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS loan_payments (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    loan_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    amount BIGINT NOT NULL,
    `date` BIGINT NOT NULL,
    note TEXT NOT NULL,
    transaction_id CHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_loan_payments_user_updated (user_id, updated_at),
    KEY idx_loan_payments_loan (loan_id),
    KEY idx_loan_payments_account (account_id),
    CONSTRAINT fk_loan_payments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_loan_payments_loan FOREIGN KEY (loan_id) REFERENCES loans (id) ON DELETE CASCADE,
    CONSTRAINT fk_loan_payments_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================================
-- 6. AUTOMATISATION (transactions récurrentes / planifiées)
-- =============================================================================================

CREATE TABLE IF NOT EXISTS recurring_transactions (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    type VARCHAR(16) NOT NULL,
    amount BIGINT NOT NULL,
    account_id CHAR(36) NOT NULL,
    category_id CHAR(36) NULL,
    description TEXT NOT NULL,
    payment_method VARCHAR(32) NULL,
    start_date BIGINT NOT NULL,
    end_date BIGINT NULL,
    frequency VARCHAR(32) NOT NULL,
    next_execution_date BIGINT NOT NULL,
    is_active TINYINT(1) NOT NULL,
    trigger_hour TINYINT NOT NULL,
    trigger_minute TINYINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_recurring_transactions_user_updated (user_id, updated_at),
    KEY idx_recurring_transactions_account (account_id),
    KEY idx_recurring_transactions_category (category_id),
    CONSTRAINT fk_recurring_transactions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_transactions_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- `transaction_id` : SANS FOREIGN KEY, même raisonnement que `fee_transaction_id` (voir section 3).
CREATE TABLE IF NOT EXISTS recurring_transaction_occurrences (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    recurring_transaction_id CHAR(36) NOT NULL,
    scheduled_date BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    transaction_id CHAR(36) NULL,
    processed_at BIGINT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_occurrences_rule_date (recurring_transaction_id, scheduled_date),
    KEY idx_occurrences_user_updated (user_id, updated_at),
    CONSTRAINT fk_occurrences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_occurrences_rule FOREIGN KEY (recurring_transaction_id) REFERENCES recurring_transactions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================================
-- 7. PLANIFICATION FINANCIÈRE PAR PROJET
-- =============================================================================================

CREATE TABLE IF NOT EXISTS financial_plans (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(191) NOT NULL,
    description TEXT NULL,
    available_amount BIGINT NOT NULL,
    target_amount BIGINT NULL,
    period_type VARCHAR(32) NOT NULL,
    start_date BIGINT NULL,
    end_date BIGINT NULL,
    icon VARCHAR(64) NOT NULL,
    color_argb BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_financial_plans_user_updated (user_id, updated_at),
    CONSTRAINT fk_financial_plans_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- `transaction_id` : SANS FOREIGN KEY, même raisonnement que `fee_transaction_id` (voir section 3).
CREATE TABLE IF NOT EXISTS financial_plan_items (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    plan_id CHAR(36) NOT NULL,
    name VARCHAR(191) NOT NULL,
    amount BIGINT NOT NULL,
    actual_amount BIGINT NULL,
    category_id CHAR(36) NULL,
    description TEXT NULL,
    planned_date BIGINT NULL,
    priority VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    transaction_id CHAR(36) NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_plan_items_user_updated (user_id, updated_at),
    KEY idx_plan_items_plan (plan_id),
    KEY idx_plan_items_category (category_id),
    CONSTRAINT fk_plan_items_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_items_plan FOREIGN KEY (plan_id) REFERENCES financial_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_items_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================================
-- 8. REÇUS PDF
-- =============================================================================================

-- `file_path` : chemin SERVEUR (`receipts/{user_id}/{id}.pdf`), jamais le `local_path` d'un
-- appareil précis. Les OCTETS du PDF ne sont JAMAIS stockés dans MySQL (voir cahier des charges,
-- section 18) : uniquement sur le disque du serveur, au chemin décrit par cette colonne — l'upload
-- binaire passe par `api/receipts/upload.php` (endpoint séparé, étape ultérieure), pas par cette
-- table qui ne contient que des métadonnées.
CREATE TABLE IF NOT EXISTS receipts (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    received_at BIGINT NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(127) NOT NULL,
    source_app VARCHAR(191) NULL,
    source_name VARCHAR(191) NULL,
    amount_minor BIGINT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_receipts_user_updated (user_id, updated_at),
    CONSTRAINT fk_receipts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================================
-- 9. JOURNAL DE CONFLITS (Last-Write-Wins, voir section 9 du document)
-- =============================================================================================

-- Table d'AUDIT uniquement, jamais poussée/tirée comme une entité métier normale (pas de colonne
-- de sync ci-dessus, pas de `id` UUID — clé technique auto-incrémentée suffisante). Alimentée par
-- le serveur lui-même au moment où il détecte un conflit de version pendant un `push` (deux
-- appareils ayant modifié la même ligne entre deux synchronisations) : la valeur perdante n'est
-- JAMAIS perdue définitivement, juste non retenue dans la version courante — voir l'exemple concret
-- Budget 50 000 vs 60 000 dans le document.
CREATE TABLE IF NOT EXISTS sync_conflicts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id CHAR(36) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id CHAR(36) NOT NULL,
    losing_payload JSON NOT NULL,
    winning_payload JSON NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sync_conflicts_user (user_id),
    KEY idx_sync_conflicts_entity (entity_type, entity_id),
    CONSTRAINT fk_sync_conflicts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- `card_secrets` : ABSENTE, intentionnellement (voir doc de tête de ce fichier, décision 6.2).
