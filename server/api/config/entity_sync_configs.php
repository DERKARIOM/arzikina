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
 *   - `type` : `'string'` ou `'int'` — pilote le cast PHP appliqué à la valeur.
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
];
