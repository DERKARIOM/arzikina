<?php

declare(strict_types=1);

/**
 * Registre déclaratif des entités synchronisables — source UNIQUE utilisée par `api/sync/push.php`
 * ET `api/sync/pull.php` pour construire dynamiquement leurs requêtes SQL (colonnes, table).
 *
 * Introduit à la QUATRIÈME entité (`persons`) : jusque-là, `push.php`/`pull.php` dupliquaient un
 * jeu complet de fonctions par entité (`createCategory`/`createSavingsGoal`/...), un choix
 * délibéré tant que le motif commun ne s'était pas confirmé sur assez de cas réels (voir l'ancien
 * historique de ces deux fichiers). Ce registre remplace cette duplication par une DESCRIPTION des
 * différences (table, colonnes, nullabilité, type) plutôt que du code répété — chaque future
 * entité devient une simple entrée ici, jamais un nouveau jeu de fonctions.
 *
 * Chaque entrée :
 * - `table` : nom de la table MySQL.
 * - `columns` : colonnes SPÉCIFIQUES à cette entité, dans l'ordre — `id`, `user_id`, `created_at`,
 *   `updated_at`, `deleted_at`, `version` sont IMPLICITES (communes à toutes, gérées directement par
 *   les fonctions génériques, jamais répétées ici).
 *   - `db` : nom de colonne MySQL (snake_case).
 *   - `payload` : clé correspondante dans le JSON envoyé par l'app Android (camelCase, voir
 *     `data/remote/dto/*SyncPayload.kt`).
 *   - `type` : `'string'`, `'int'` ou `'float'` (ce dernier introduit pour `transactions.latitude`/
 *     `longitude`, les premiers champs non-entiers de ce registre) — pilote le cast PHP appliqué à
 *     la valeur, voir `castConfiguredValue`/`defaultForConfiguredType` dans `push.php`.
 *   - `nullable` : `true` si la colonne MySQL accepte `NULL` — pilote la distinction
 *     `array_key_exists` ("champ non envoyé, conserver la valeur actuelle") vs "champ envoyé
 *     explicitement à `null`" (effacer) lors d'une mise à jour, voir `upsertExistingEntityRow`.
 *     `false` : valeur par défaut `''`/`0` utilisée si absente (voir `createEntityRow`).
 *
 * Les colonnes NE SONT JAMAIS des valeurs utilisateur — construites en dur ici, interpolées
 * directement dans le SQL généré (les noms de colonnes ne peuvent pas être des paramètres liés
 * PDO) : aucun risque d'injection, ce registre n'est jamais alimenté depuis une requête HTTP.
 */
const ENTITY_CONFIGS = [
    'categories' => [
        'table' => 'categories',
        'columns' => [
            ['db' => 'name', 'payload' => 'name', 'type' => 'string', 'nullable' => false],
            ['db' => 'icon', 'payload' => 'icon', 'type' => 'string', 'nullable' => false],
            ['db' => 'color_argb', 'payload' => 'colorArgb', 'type' => 'int', 'nullable' => false],
            ['db' => 'type', 'payload' => 'type', 'type' => 'string', 'nullable' => false],
        ],
    ],
    'savings_goals' => [
        'table' => 'savings_goals',
        'columns' => [
            ['db' => 'name', 'payload' => 'name', 'type' => 'string', 'nullable' => false],
            ['db' => 'target_amount', 'payload' => 'targetAmount', 'type' => 'int', 'nullable' => false],
            ['db' => 'current_amount', 'payload' => 'currentAmount', 'type' => 'int', 'nullable' => false],
            ['db' => 'currency_code', 'payload' => 'currencyCode', 'type' => 'string', 'nullable' => false],
            ['db' => 'deadline', 'payload' => 'deadline', 'type' => 'int', 'nullable' => true],
        ],
    ],
    'financial_plans' => [
        'table' => 'financial_plans',
        'columns' => [
            ['db' => 'name', 'payload' => 'name', 'type' => 'string', 'nullable' => false],
            ['db' => 'description', 'payload' => 'description', 'type' => 'string', 'nullable' => true],
            ['db' => 'available_amount', 'payload' => 'availableAmount', 'type' => 'int', 'nullable' => false],
            ['db' => 'target_amount', 'payload' => 'targetAmount', 'type' => 'int', 'nullable' => true],
            ['db' => 'period_type', 'payload' => 'periodType', 'type' => 'string', 'nullable' => false],
            ['db' => 'start_date', 'payload' => 'startDate', 'type' => 'int', 'nullable' => true],
            ['db' => 'end_date', 'payload' => 'endDate', 'type' => 'int', 'nullable' => true],
            ['db' => 'icon', 'payload' => 'icon', 'type' => 'string', 'nullable' => false],
            ['db' => 'color_argb', 'payload' => 'colorArgb', 'type' => 'int', 'nullable' => false],
            ['db' => 'status', 'payload' => 'status', 'type' => 'string', 'nullable' => false],
        ],
    ],
    'persons' => [
        'table' => 'persons',
        'columns' => [
            ['db' => 'name', 'payload' => 'name', 'type' => 'string', 'nullable' => false],
            ['db' => 'phone', 'payload' => 'phone', 'type' => 'string', 'nullable' => true],
        ],
    ],
    'accounts' => [
        'table' => 'accounts',
        'columns' => [
            ['db' => 'name', 'payload' => 'name', 'type' => 'string', 'nullable' => false],
            ['db' => 'icon', 'payload' => 'icon', 'type' => 'string', 'nullable' => false],
            ['db' => 'color_argb', 'payload' => 'colorArgb', 'type' => 'int', 'nullable' => false],
            ['db' => 'currency_code', 'payload' => 'currencyCode', 'type' => 'string', 'nullable' => false],
            ['db' => 'initial_balance_minor', 'payload' => 'initialBalanceMinor', 'type' => 'int', 'nullable' => false],
            ['db' => 'type', 'payload' => 'type', 'type' => 'string', 'nullable' => false],
            // Derniers chiffres + expiration seulement (affichage masqué) — le numéro complet et le
            // CVV chiffrés vivent dans `CardSecretEntity`/`card_secrets`, volontairement EXCLUS de la
            // synchronisation (voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, décision 6.2 : clé de
            // chiffrement liée au Keystore d'un seul appareil, non transportable telle quelle).
            ['db' => 'card_last_four_digits', 'payload' => 'cardLastFourDigits', 'type' => 'string', 'nullable' => true],
            ['db' => 'card_expiry_month', 'payload' => 'cardExpiryMonth', 'type' => 'int', 'nullable' => true],
            ['db' => 'card_expiry_year', 'payload' => 'cardExpiryYear', 'type' => 'int', 'nullable' => true],
            // Booléen Kotlin → JSON `true`/`false` → `(int) true|false` = `1`/`0` côté PHP
            // (`castConfiguredValue`, voir `push.php`) : pas de type booléen dédié, `'int'` suffit.
            ['db' => 'is_excluded_from_statistics', 'payload' => 'isExcludedFromStatistics', 'type' => 'int', 'nullable' => false],
            ['db' => 'mobile_money_package_name', 'payload' => 'mobileMoneyPackageName', 'type' => 'string', 'nullable' => true],
        ],
    ],
    // Étape 17 : `Transaction` référence d'AUTRES lignes synchronisées (compte, catégorie,
    // transfert, transaction de frais) — voir `data/remote/dto/TransactionSyncPayload.kt` pour le
    // raisonnement complet. Ces colonnes stockent le `syncId` (UUID) de la ligne référencée, PAS
    // son `id` MySQL (qui EST déjà ce même UUID, voir `id CHAR(36)` sur chaque table métier) :
    // aucune résolution supplémentaire n'est nécessaire ici, cast `'string'` simple, comme n'importe
    // quelle autre colonne — mais AUCUNE `FOREIGN KEY` réelle (même principe que `loans.transaction_id`/
    // `loan_payments.transaction_id`, voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, 10bis) : la
    // ligne référencée peut arriver dans un push ULTÉRIEUR (ex. compte tout juste créé hors ligne),
    // une contrainte stricte ferait échouer cette transaction au lieu de la laisser attendre.
    'transactions' => [
        'table' => 'transactions',
        'columns' => [
            ['db' => 'amount', 'payload' => 'amount', 'type' => 'int', 'nullable' => false],
            ['db' => 'type', 'payload' => 'type', 'type' => 'string', 'nullable' => false],
            // Noms de colonnes CONFORMES à database/migrations/001_initial_schema.sql (pas de
            // suffixe `_sync_id` : ces colonnes CHAR(36) SONT déjà l'UUID/syncId de la ligne
            // référencée — seule la clé du payload JSON, côté Android, porte ce suffixe pour rester
            // explicite sur ce qu'elle transporte, voir `TransactionSyncPayload.kt`).
            ['db' => 'account_id', 'payload' => 'accountSyncId', 'type' => 'string', 'nullable' => false],
            ['db' => 'transfer_account_id', 'payload' => 'transferAccountSyncId', 'type' => 'string', 'nullable' => true],
            ['db' => 'category_id', 'payload' => 'categorySyncId', 'type' => 'string', 'nullable' => true],
            ['db' => 'date', 'payload' => 'date', 'type' => 'int', 'nullable' => false],
            ['db' => 'description', 'payload' => 'description', 'type' => 'string', 'nullable' => false],
            ['db' => 'latitude', 'payload' => 'latitude', 'type' => 'float', 'nullable' => true],
            ['db' => 'longitude', 'payload' => 'longitude', 'type' => 'float', 'nullable' => true],
            ['db' => 'payment_method', 'payload' => 'paymentMethod', 'type' => 'string', 'nullable' => true],
            ['db' => 'fee_transaction_id', 'payload' => 'feeTransactionSyncId', 'type' => 'string', 'nullable' => true],
            ['db' => 'fee_type', 'payload' => 'feeType', 'type' => 'string', 'nullable' => true],
        ],
    ],
];
