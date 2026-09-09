-- Arzikina — Script de vérification/correction : synchronisation `recurring_transactions` en échec
-- (erreurs FAILED répétées dans `sync_queue` côté Android, réponse HTML "Fatal error" au lieu de
-- JSON — voir server/api/sync/push.php).
--
-- À exécuter MANUELLEMENT sur la base de production, section par section (voir 002_..._.sql pour
-- la même convention) — AUCUN outil de migration automatique n'existe dans ce projet. Ne PAS lancer
-- tout le fichier d'un bloc sans lire les résultats intermédiaires : les sections C/D ne doivent
-- être exécutées QUE si la section correspondante des diagnostics (A/B) l'indique.
--
-- MISE À JOUR (vérification manuelle du 2026-09-08/09) : les tables existent déjà en production
-- avec le schéma attendu (DESCRIBE conforme à 001_initial_schema.sql), et aucune des contraintes
-- `fk_recurring_transactions_account`/`_category`/`fk_occurrences_rule` n'y est présente (seules
-- `fk_recurring_transactions_user`/`fk_occurrences_user` existent, volontairement conservées) — les
-- deux causes ci-dessous sont donc ÉCARTÉES pour cet environnement. Conservé tel quel pour
-- référence/futur déploiement (nouvel environnement, restauration...). La cause réelle de l'échec
-- observé reste à identifier via le journal serveur, une fois server/api/sync/push.php (corrigé en
-- parallèle pour logger le détail de toute exception dans error_log au lieu de casser la réponse
-- JSON) déployé en production.
--
-- Deux causes plausibles, non exclusives (voir l'audit du code `server/api/sync/push.php`,
-- `server/api/config/entity_sync_configs.php`) :
--   1. Les tables `recurring_transactions`/`recurring_transaction_occurrences` (ajoutées à l'Étape
--      20, après le déploiement initial) n'ont peut-être jamais été créées sur la base réelle.
--   2. Les contraintes de clé étrangère `fk_recurring_transactions_account`/`_category` et
--      `fk_occurrences_rule` sont explicitement documentées dans entity_sync_configs.php comme
--      "à supprimer avant déploiement" (même règle déjà appliquée à budgets/loans/
--      financial_plan_items, dont la synchronisation fonctionne) — cette étape a pu être oubliée
--      pour ces deux tables, plus récentes. Une contrainte encore active fait échouer un
--      CREATE/UPDATE dès que le compte ou la catégorie référencé n'est pas encore synchronisé côté
--      serveur (ordre d'arrivée des lots depuis l'appareil, hors du contrôle du serveur).

-- =============================================================================================
-- SECTION A — Diagnostic : les tables existent-elles ?
-- =============================================================================================
-- Si les deux lignes ci-dessous ne renvoient RIEN : cause n°1 confirmée, exécuter la SECTION C.
-- ATTENTION : vérifier que la base active est bien la bonne (`SELECT DATABASE();`) avant de
-- conclure quoi que ce soit d'un résultat vide — un onglet phpMyAdmin resté sur `information_schema`
-- ou une autre base fausse ce diagnostic silencieusement.
SELECT TABLE_NAME, TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('recurring_transactions', 'recurring_transaction_occurrences');

-- =============================================================================================
-- SECTION B — Diagnostic : les contraintes à supprimer sont-elles encore actives ?
-- =============================================================================================
-- Ne pertinent que si la SECTION A a bien renvoyé les deux tables. Chaque ligne renvoyée ici est
-- une contrainte encore active à supprimer (SECTION D) — reportez-vous à CONSTRAINT_NAME.
SELECT TABLE_NAME, CONSTRAINT_NAME
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
  AND TABLE_NAME IN ('recurring_transactions', 'recurring_transaction_occurrences')
  AND CONSTRAINT_NAME IN (
      'fk_recurring_transactions_account',
      'fk_recurring_transactions_category',
      'fk_occurrences_rule'
  );

-- =============================================================================================
-- SECTION C — Correction cause n°1 : créer les tables si absentes
-- =============================================================================================
-- `IF NOT EXISTS` : sans danger même si les tables existent déjà (ne fait rien dans ce cas) —
-- copie exacte de la définition de database/migrations/001_initial_schema.sql, section 6.
-- N'exécuter que si la SECTION A n'a rien renvoyé.

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
-- SECTION D — Correction cause n°2 : supprimer les contraintes documentées comme à retirer
-- =============================================================================================
-- N'exécuter QUE les lignes correspondant à une contrainte confirmée présente par la SECTION B
-- (le nom exact du message d'erreur MySQL en cas de contrainte déjà absente serait
-- "Error 1091: Can't DROP ...; check that it exists" — sans danger, mais évitez de lancer une
-- ligne pour une contrainte que la SECTION B n'a pas listée).
--
-- `user_id`/`recurring_transaction_id` (ligne fk_occurrences_user / fk_recurring_transactions_user)
-- ne sont volontairement PAS supprimées : ce sont des références vers `users`, toujours présent
-- avant toute écriture (l'utilisateur authentifié existe par construction) — seules les
-- contraintes vers des entités elles-mêmes synchronisées depuis l'appareil (compte, catégorie,
-- règle parente) posent le problème d'ordre d'arrivée décrit plus haut, même règle que pour
-- budgets/loans/financial_plan_items.

ALTER TABLE recurring_transactions
    DROP FOREIGN KEY fk_recurring_transactions_account;

ALTER TABLE recurring_transactions
    DROP FOREIGN KEY fk_recurring_transactions_category;

ALTER TABLE recurring_transaction_occurrences
    DROP FOREIGN KEY fk_occurrences_rule;

-- =============================================================================================
-- APRÈS EXÉCUTION
-- =============================================================================================
-- Les lignes déjà en FAILED dans `sync_queue` (Android) seront réessayées automatiquement au
-- prochain cycle de synchronisation (SyncWorker) une fois la vraie cause corrigée côté serveur —
-- aucune action manuelle nécessaire sur l'appareil. `server/api/sync/push.php`/`pull.php` ont par
-- ailleurs été corrigés en parallèle pour ne plus jamais renvoyer une page HTML à la place du JSON
-- si une erreur serveur imprévue survenait de nouveau à l'avenir (voir leur historique Git) — une
-- fois déployés, le vrai message d'exception (si le problème persiste malgré A-D ci-dessus)
-- apparaîtra dans le journal d'erreurs PHP du serveur, préfixé par "[sync/push]" ou "[sync/pull]".
