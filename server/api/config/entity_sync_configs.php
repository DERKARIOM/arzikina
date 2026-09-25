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
 *
 * `users` (photo de profil, cahier des charges "Gestion de la photo de profil") N'Y FIGURE PAS,
 * volontairement : ce registre transporte exclusivement du JSON via `push.php`/`pull.php`, jamais
 * un fichier binaire. La photo passe par des endpoints DÉDIÉS —
 * `api/profile/upload_photo.php`/`delete_photo.php`/`get.php` — qui gèrent eux-mêmes le stockage
 * disque et réutilisent directement `users.version`/`users.updated_at` (aucune colonne
 * `photo_version` séparée). Voir la KDoc de tête de `upload_photo.php` pour le raisonnement complet.
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
            // Position d'affichage (glisser-déposer sur l'écran "Comptes" Android) — voir
            // database/migrations/004_add_display_order_to_accounts.sql.
            ['db' => 'display_order', 'payload' => 'displayOrder', 'type' => 'int', 'nullable' => false],
            // Objectif d'épargne (type `SAVINGS_GOAL`) — voir database/migrations/006_savings_goal_accounts.sql.
            // NULLABLES : `null` explicite = compte repassé en compte classique (colonnes effacées),
            // champ absent (ancien client) = valeur actuelle conservée (voir `array_key_exists`, push.php).
            ['db' => 'savings_target_amount', 'payload' => 'savingsTargetAmount', 'type' => 'int', 'nullable' => true],
            ['db' => 'savings_description', 'payload' => 'savingsDescription', 'type' => 'string', 'nullable' => true],
        ],
    ],
    // Étape 19 : `Loan` référence trois AUTRES lignes synchronisées (personne, compte, transaction
    // de décaissement) — même raisonnement que `budgets.category_id`/`transactions.account_id` :
    // colonnes `person_id`/`account_id`/`transaction_id` stockent le `syncId` de la ligne
    // référencée, payload `personSyncId`/`accountSyncId`/`transactionSyncId`. PAS de `FOREIGN KEY`
    // réelle en pratique (celles du schéma initial, `fk_loans_person`/`fk_loans_account`, doivent
    // être supprimées avant déploiement — voir la doc de l'étape 19) : un prêt/emprunt peut être
    // créé hors ligne avant que sa personne/son compte n'ait été confirmé par le serveur.
    'loans' => [
        'table' => 'loans',
        'columns' => [
            ['db' => 'person_id', 'payload' => 'personSyncId', 'type' => 'string', 'nullable' => false],
            ['db' => 'account_id', 'payload' => 'accountSyncId', 'type' => 'string', 'nullable' => false],
            ['db' => 'type', 'payload' => 'type', 'type' => 'string', 'nullable' => false],
            ['db' => 'amount', 'payload' => 'amount', 'type' => 'int', 'nullable' => false],
            ['db' => 'amount_repaid', 'payload' => 'amountRepaid', 'type' => 'int', 'nullable' => false],
            ['db' => 'remaining_amount', 'payload' => 'remainingAmount', 'type' => 'int', 'nullable' => false],
            ['db' => 'start_date', 'payload' => 'startDate', 'type' => 'int', 'nullable' => false],
            ['db' => 'due_date', 'payload' => 'dueDate', 'type' => 'int', 'nullable' => false],
            ['db' => 'reason', 'payload' => 'reason', 'type' => 'string', 'nullable' => false],
            ['db' => 'reason_custom_text', 'payload' => 'reasonCustomText', 'type' => 'string', 'nullable' => true],
            ['db' => 'repayment_mode', 'payload' => 'repaymentMode', 'type' => 'string', 'nullable' => false],
            ['db' => 'description', 'payload' => 'description', 'type' => 'string', 'nullable' => false],
            ['db' => 'status', 'payload' => 'status', 'type' => 'string', 'nullable' => false],
            ['db' => 'transaction_id', 'payload' => 'transactionSyncId', 'type' => 'string', 'nullable' => false],
        ],
    ],
    // Étape 19 : `LoanPayment` référence trois AUTRES lignes synchronisées (prêt/emprunt parent,
    // compte, transaction de remboursement) — même raisonnement que `loans` ci-dessus. Contraintes
    // `fk_loan_payments_loan`/`fk_loan_payments_account` du schéma initial à supprimer également.
    'loan_payments' => [
        'table' => 'loan_payments',
        'columns' => [
            ['db' => 'loan_id', 'payload' => 'loanSyncId', 'type' => 'string', 'nullable' => false],
            ['db' => 'account_id', 'payload' => 'accountSyncId', 'type' => 'string', 'nullable' => false],
            ['db' => 'amount', 'payload' => 'amount', 'type' => 'int', 'nullable' => false],
            ['db' => 'date', 'payload' => 'date', 'type' => 'int', 'nullable' => false],
            ['db' => 'note', 'payload' => 'note', 'type' => 'string', 'nullable' => false],
            ['db' => 'transaction_id', 'payload' => 'transactionSyncId', 'type' => 'string', 'nullable' => false],
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
    // Étape 18 : `Budget` référence une catégorie via `categoryId` — même raisonnement que
    // `transactions.account_id` ci-dessous (colonne `category_id`, payload `categorySyncId`, PAS de
    // `FOREIGN KEY` réelle : voir la remarque de suppression de contrainte dans la doc de
    // déploiement de cette étape, `fk_budgets_category` existe dans le schéma initial et doit être
    // supprimée pour la même raison que `fk_transactions_account`/`fk_transactions_category`
    // — un budget peut être créé hors ligne avant que sa catégorie n'ait été confirmée par le
    // serveur).
    'budgets' => [
        'table' => 'budgets',
        'columns' => [
            ['db' => 'category_id', 'payload' => 'categorySyncId', 'type' => 'string', 'nullable' => false],
            ['db' => 'period', 'payload' => 'period', 'type' => 'string', 'nullable' => false],
            ['db' => 'limit_amount', 'payload' => 'limitAmount', 'type' => 'int', 'nullable' => false],
            ['db' => 'currency_code', 'payload' => 'currencyCode', 'type' => 'string', 'nullable' => false],
            ['db' => 'start_date', 'payload' => 'startDate', 'type' => 'int', 'nullable' => true],
            ['db' => 'end_date', 'payload' => 'endDate', 'type' => 'int', 'nullable' => true],
        ],
    ],
    // Étape 20 : `RecurringTransaction` référence DEUX autres entités synchronisées (compte,
    // catégorie — cette dernière nullable, voir `RecurringTransactionEntity.categoryId`) — même
    // raisonnement que `budgets`/`loans` ci-dessus. Contraintes `fk_recurring_transactions_account`/
    // `fk_recurring_transactions_category` du schéma initial à supprimer avant déploiement.
    'recurring_transactions' => [
        'table' => 'recurring_transactions',
        'columns' => [
            ['db' => 'type', 'payload' => 'type', 'type' => 'string', 'nullable' => false],
            ['db' => 'amount', 'payload' => 'amount', 'type' => 'int', 'nullable' => false],
            ['db' => 'account_id', 'payload' => 'accountSyncId', 'type' => 'string', 'nullable' => false],
            ['db' => 'category_id', 'payload' => 'categorySyncId', 'type' => 'string', 'nullable' => true],
            ['db' => 'description', 'payload' => 'description', 'type' => 'string', 'nullable' => false],
            ['db' => 'payment_method', 'payload' => 'paymentMethod', 'type' => 'string', 'nullable' => true],
            ['db' => 'start_date', 'payload' => 'startDate', 'type' => 'int', 'nullable' => false],
            ['db' => 'end_date', 'payload' => 'endDate', 'type' => 'int', 'nullable' => true],
            ['db' => 'frequency', 'payload' => 'frequency', 'type' => 'string', 'nullable' => false],
            ['db' => 'next_execution_date', 'payload' => 'nextExecutionDate', 'type' => 'int', 'nullable' => false],
            // Booléen Kotlin → JSON `true`/`false` → `(int) true|false` = `1`/`0` côté PHP, même
            // raisonnement que `accounts.is_excluded_from_statistics`.
            ['db' => 'is_active', 'payload' => 'isActive', 'type' => 'int', 'nullable' => false],
            ['db' => 'trigger_hour', 'payload' => 'triggerHour', 'type' => 'int', 'nullable' => false],
            ['db' => 'trigger_minute', 'payload' => 'triggerMinute', 'type' => 'int', 'nullable' => false],
        ],
    ],
    // Étape 20 : `RecurringTransactionOccurrence` référence sa règle parente (`recurring_transaction_id`)
    // ET, une fois traitée (ACCEPTED/MODIFIED), sa transaction générée (`transaction_id`, nullable
    // — `NULL` tant que le statut reste PENDING/REJECTED). Contrainte `fk_occurrences_rule` du
    // schéma initial à supprimer avant déploiement.
    'recurring_transaction_occurrences' => [
        'table' => 'recurring_transaction_occurrences',
        'columns' => [
            ['db' => 'recurring_transaction_id', 'payload' => 'recurringTransactionSyncId', 'type' => 'string', 'nullable' => false],
            ['db' => 'scheduled_date', 'payload' => 'scheduledDate', 'type' => 'int', 'nullable' => false],
            ['db' => 'status', 'payload' => 'status', 'type' => 'string', 'nullable' => false],
            ['db' => 'transaction_id', 'payload' => 'transactionSyncId', 'type' => 'string', 'nullable' => true],
            ['db' => 'processed_at', 'payload' => 'processedAt', 'type' => 'int', 'nullable' => true],
        ],
    ],
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
    // Étape 21 : `FinancialPlanItem` référence sa planification parente (`plan_id`, jamais nulle),
    // une catégorie (`category_id`, nullable — voir `FinancialPlanItemEntity.categoryId`) ET, une
    // fois convertie en dépense réelle, sa transaction (`transaction_id`, nullable — `NULL` tant que
    // `FinancialPlanRepositoryImpl.convertItemToTransaction` n'a pas été appelé) — même raisonnement
    // que `recurring_transaction_occurrences` ci-dessus. Contraintes `fk_plan_items_plan`/
    // `fk_plan_items_category` du schéma initial à supprimer avant déploiement.
    'financial_plan_items' => [
        'table' => 'financial_plan_items',
        'columns' => [
            ['db' => 'plan_id', 'payload' => 'planSyncId', 'type' => 'string', 'nullable' => false],
            ['db' => 'name', 'payload' => 'name', 'type' => 'string', 'nullable' => false],
            ['db' => 'amount', 'payload' => 'amount', 'type' => 'int', 'nullable' => false],
            ['db' => 'actual_amount', 'payload' => 'actualAmount', 'type' => 'int', 'nullable' => true],
            ['db' => 'category_id', 'payload' => 'categorySyncId', 'type' => 'string', 'nullable' => true],
            ['db' => 'description', 'payload' => 'description', 'type' => 'string', 'nullable' => true],
            ['db' => 'planned_date', 'payload' => 'plannedDate', 'type' => 'int', 'nullable' => true],
            ['db' => 'priority', 'payload' => 'priority', 'type' => 'string', 'nullable' => false],
            ['db' => 'status', 'payload' => 'status', 'type' => 'string', 'nullable' => false],
            ['db' => 'transaction_id', 'payload' => 'transactionSyncId', 'type' => 'string', 'nullable' => true],
        ],
    ],
    // Étape 22 : `UserPreferences` (pas d'entité Room source, voir `UserPreferencesEntity`/
    // `UserPreferencesRepositoryImpl` côté Android, DataStore Preferences) — la SEULE entité de ce
    // registre SANS AUCUNE référence croisée (aucune colonne `*_id`/`*SyncId`) : `user_id` reste la
    // colonne IMPLICITE habituelle (l'utilisateur AUTHENTIFIÉ courant, jamais une autre ligne
    // référencée par un `syncId`), donc `fk_user_preferences_user` n'a PAS besoin d'être supprimée
    // avant déploiement (contrairement à `fk_budgets_category`/`fk_loans_person`/etc. ci-dessus) —
    // cette contrainte est garantie satisfaite par construction, comme pour toutes les autres tables
    // de ce registre. `biometric_lock_enabled` reste EXCLU (voir la doc de la table MySQL,
    // `database/migrations/001_initial_schema.sql`, section 10).
    'user_preferences' => [
        'table' => 'user_preferences',
        'columns' => [
            ['db' => 'theme_mode', 'payload' => 'themeMode', 'type' => 'string', 'nullable' => false],
            ['db' => 'currency_code', 'payload' => 'currencyCode', 'type' => 'string', 'nullable' => false],
        ],
    ],
    // "Marketplace personnelle" (modèles de transaction réutilisables) : référence DEUX autres
    // entités synchronisées (compte, catégorie — cette dernière TOUJOURS renseignée, contrairement à
    // `recurring_transactions.category_id`, voir la doc de tête de `TransactionTemplate.kt` côté
    // Android, "jamais de virement pour un modèle") — même raisonnement que `recurring_transactions`
    // ci-dessus. Contrairement à cette dernière (et à `budgets`/`loans`), le schéma initial de
    // `transaction_templates` (voir database/migrations/005_add_transaction_templates.sql) n'a
    // JAMAIS eu de `FOREIGN KEY` sur `account_id`/`category_id` : leçon déjà tirée des entités
    // précédentes, appliquée directement plutôt que corrigée après coup — rien à supprimer avant
    // déploiement ici.
    'transaction_templates' => [
        'table' => 'transaction_templates',
        'columns' => [
            ['db' => 'name', 'payload' => 'name', 'type' => 'string', 'nullable' => false],
            ['db' => 'type', 'payload' => 'type', 'type' => 'string', 'nullable' => false],
            ['db' => 'amount', 'payload' => 'amount', 'type' => 'int', 'nullable' => false],
            ['db' => 'category_id', 'payload' => 'categorySyncId', 'type' => 'string', 'nullable' => false],
            ['db' => 'account_id', 'payload' => 'accountSyncId', 'type' => 'string', 'nullable' => false],
            ['db' => 'description', 'payload' => 'description', 'type' => 'string', 'nullable' => false],
            // Booléen Kotlin → JSON `true`/`false` → `(int) true|false` = `1`/`0` côté PHP, même
            // raisonnement que `accounts.is_excluded_from_statistics`/`recurring_transactions.is_active`.
            ['db' => 'is_favorite', 'payload' => 'isFavorite', 'type' => 'int', 'nullable' => false],
            // "Heure par défaut" (extension optionnelle) : `NULL` = pas d'heure par défaut, même
            // convention que côté Android (`TransactionTemplateEntity.defaultHour`/`defaultMinute`).
            ['db' => 'default_hour', 'payload' => 'defaultHour', 'type' => 'int', 'nullable' => true],
            ['db' => 'default_minute', 'payload' => 'defaultMinute', 'type' => 'int', 'nullable' => true],
        ],
    ],
];
