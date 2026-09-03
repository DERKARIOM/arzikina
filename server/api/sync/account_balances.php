<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * GET /api/sync/account_balances.php
 *
 * Renvoie le solde RÉEL (calculé côté serveur, en SQL) de chaque compte de l'utilisateur
 * authentifié — même formule que `accountBalance()` côté web (`services/finance.ts`) et
 * `computeCurrentBalances()` côté Android (`AccountsViewModel.kt`) :
 *
 *   solde = initial_balance_minor
 *         + SUM(montant des INCOME sur ce compte, non supprimées)
 *         - SUM(montant des EXPENSE/TRANSFER SORTANTS sur ce compte, non supprimées)
 *         + SUM(montant des TRANSFER ENTRANTS vers ce compte, non supprimées)
 *
 * Introduit pour permettre au web (voir `store/app-store.tsx`) de détecter un ÉCART entre le
 * solde qu'il affiche (calculé localement à partir de son cache incrémental) et le solde RÉEL en
 * base, puis de déclencher automatiquement une resynchronisation complète en cas de dérive — voir
 * la KDoc de tête de `push.php` pour l'origine de ce type de dérive (curseur incrémental qui a
 * dépassé un `updated_at` réécrit après-coup).
 *
 * Lecture SEULE, aucune écriture, aucune nouvelle table — un simple calcul d'agrégats sur les
 * données déjà synchronisées. `deleted_at IS NULL` appliqué systématiquement (comptes ET
 * transactions) : un compte supprimé n'apparaît pas dans la réponse, une transaction supprimée ne
 * pèse jamais dans le calcul, même règle que partout ailleurs dans ce backend.
 *
 * Réponse : { "balances": { "<accountId>": <soldeMinor>, ... } }.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    sendError('method_not_allowed', 'Cette route accepte uniquement GET.', 405);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);
$userId = $auth['userId'];

$stmt = $pdo->prepare(
    'SELECT
        a.id AS account_id,
        a.initial_balance_minor
            + COALESCE((
                SELECT SUM(
                    CASE
                        WHEN t.type = \'INCOME\' THEN t.amount
                        WHEN t.type IN (\'EXPENSE\', \'TRANSFER\') THEN -t.amount
                        ELSE 0
                    END
                )
                FROM transactions t
                WHERE t.account_id = a.id AND t.deleted_at IS NULL
            ), 0)
            + COALESCE((
                SELECT SUM(t2.amount)
                FROM transactions t2
                WHERE t2.transfer_account_id = a.id
                  AND t2.type = \'TRANSFER\'
                  AND t2.deleted_at IS NULL
            ), 0) AS balance_minor
     FROM accounts a
     WHERE a.user_id = :user_id AND a.deleted_at IS NULL'
);
$stmt->execute(['user_id' => $userId]);
$rows = $stmt->fetchAll();

$balances = [];
foreach ($rows as $row) {
    $balances[$row['account_id']] = (int) $row['balance_minor'];
}

sendJson(['balances' => $balances]);
