<?php

declare(strict_types=1);

/**
 * Script de MAINTENANCE, à exécuter en CLI UNIQUEMENT (jamais exposé en HTTP) — Étape B du chantier
 * "audit auth + sync + doublons". Fusionne les catégories et comptes DÉJÀ dupliqués en base MySQL
 * (voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md). L'Étape A (voir SyncEngineImpl.kt/
 * SyncAuthRepositoryImpl.kt côté Android) empêche la RÉCURRENCE de ce problème mais ne touche pas
 * aux lignes déjà créées avant elle — c'est l'objet unique de ce script.
 *
 * Usage (depuis le serveur, en SSH, dans le dossier où ce fichier est déployé) :
 *   php merge_duplicate_categories_accounts.php                        (simulation, AUCUNE écriture)
 *   php merge_duplicate_categories_accounts.php --apply                (applique réellement la fusion)
 *   php merge_duplicate_categories_accounts.php --apply --user=<uuid>  (limité à un seul utilisateur)
 *
 * TOUJOURS lancer d'abord SANS --apply et relire le rapport avant d'ajouter --apply — voir cahier
 * des charges, point 14 ("ne supprime pas les données existantes sans migration contrôlée").
 *
 * STRATÉGIE (cahier des charges, points 6/8) : pour chaque groupe de lignes ACTIVES (deleted_at IS
 * NULL) d'une même table partageant (user_id, name, type), la plus ANCIENNE (created_at le plus
 * petit, id en repli si égalité) est conservée comme "canonique". Toutes les RÉFÉRENCES des autres
 * lignes du groupe (transactions, budgets, prêts, automatisations, planifications...) sont
 * réassignées vers la ligne canonique, PUIS ces autres lignes sont supprimées EN DOUCE (deleted_at,
 * jamais un vrai DELETE SQL) — aucune transaction n'est jamais perdue, et la suppression douce se
 * propage ensuite à tous les appareils au prochain pull normal, exactement comme n'importe quelle
 * autre suppression (voir SyncEngineImpl.applyCategoryServerState/applyAccountServerState côté
 * Android) : aucun nouveau système de synchronisation n'est introduit par ce script, il ne fait que
 * réécrire des lignes existantes à travers le mécanisme déjà en place.
 *
 * `updated_at`/`version` sont explicitement avancés sur TOUTES les lignes touchées (références
 * réassignées ET doublons supprimés) : sans cela, le pull normal (`WHERE updated_at > :cursor`,
 * voir pull.php) ne remonterait jamais ce changement aux appareils déjà synchronisés.
 */

if (PHP_SAPI !== 'cli') {
    http_response_code(403);
    exit("Ce script ne s'exécute qu'en ligne de commande (CLI), jamais via HTTP.\n");
}

require_once __DIR__ . '/../api/config/database.php';

$options = getopt('', ['apply', 'user::']);
$apply = array_key_exists('apply', $options);
$onlyUserId = isset($options['user']) && $options['user'] !== false ? (string) $options['user'] : null;

$pdo = getDatabaseConnection();

echo $apply ? "=== MODE APPLICATION (écriture réelle) ===\n" : "=== MODE SIMULATION (aucune écriture, relancer avec --apply pour appliquer) ===\n";
if ($onlyUserId !== null) {
    echo "Limité à l'utilisateur : $onlyUserId\n";
}

// Tables/colonnes qui référencent `categories.id` — voir database/migrations/001_initial_schema.sql.
$categoryReferences = [
    ['table' => 'transactions', 'column' => 'category_id'],
    ['table' => 'budgets', 'column' => 'category_id'],
    ['table' => 'recurring_transactions', 'column' => 'category_id'],
    ['table' => 'financial_plan_items', 'column' => 'category_id'],
];

// Tables/colonnes qui référencent `accounts.id`.
$accountReferences = [
    ['table' => 'transactions', 'column' => 'account_id'],
    ['table' => 'transactions', 'column' => 'transfer_account_id'],
    ['table' => 'loans', 'column' => 'account_id'],
    ['table' => 'loan_payments', 'column' => 'account_id'],
    ['table' => 'recurring_transactions', 'column' => 'account_id'],
];

echo "\n--- Catégories ---\n";
mergeDuplicates($pdo, 'categories', $categoryReferences, $onlyUserId, $apply);

echo "\n--- Comptes ---\n";
mergeDuplicates($pdo, 'accounts', $accountReferences, $onlyUserId, $apply);

echo "\nTerminé.\n";

/**
 * Fusionne les doublons de [$table] ('categories' ou 'accounts' — même forme de schéma utile ici :
 * id, user_id, name, type, created_at, updated_at, deleted_at, version) en réassignant
 * [$references] (tables/colonnes qui pointent vers [$table]) vers la ligne canonique de chaque
 * groupe avant de supprimer les autres lignes EN DOUCE. Un groupe = une transaction SQL : un
 * problème sur l'un des deux n'affecte jamais les groupes déjà traités ni les suivants.
 */
function mergeDuplicates(PDO $pdo, string $table, array $references, ?string $onlyUserId, bool $apply): void
{
    $groups = findDuplicateGroups($pdo, $table, $onlyUserId);
    if (count($groups) === 0) {
        echo "Aucun doublon actif trouvé dans `$table`.\n";
        return;
    }

    foreach ($groups as $group) {
        $userId = $group['user_id'];
        $rows = $group['rows']; // triées created_at ASC puis id ASC — voir findDuplicateGroups.

        $canonical = $rows[0];
        $duplicates = array_slice($rows, 1);
        $duplicateIds = array_values(array_map(static fn (array $r): string => $r['id'], $duplicates));

        echo sprintf(
            "  [%s] \"%s\" (%s) — utilisateur %s : %d ligne(s) → conservée %s, fusionnée(s) %s\n",
            $table,
            $group['name'],
            $group['type'],
            $userId,
            count($rows),
            $canonical['id'],
            implode(', ', $duplicateIds)
        );

        if (!$apply) {
            continue;
        }

        $pdo->beginTransaction();
        try {
            $nowMillis = (int) round(microtime(true) * 1000);

            foreach ($references as $ref) {
                $stmt = $pdo->prepare(
                    'UPDATE ' . $ref['table'] . '
                     SET ' . $ref['column'] . ' = :canonical_id, updated_at = :now, version = version + 1
                     WHERE user_id = :user_id AND ' . $ref['column'] . ' IN (' . inPlaceholderList($duplicateIds) . ')'
                );
                $stmt->execute(array_merge(
                    ['canonical_id' => $canonical['id'], 'now' => $nowMillis, 'user_id' => $userId],
                    inPlaceholderValues($duplicateIds)
                ));
                if ($stmt->rowCount() > 0) {
                    echo sprintf("      -> %d reference(s) reassignee(s) dans %s.%s\n", $stmt->rowCount(), $ref['table'], $ref['column']);
                }
            }

            // :deleted_at / :updated_at (pas deux fois :now) : un paramètre nommé ne peut être lié
            // qu'une seule fois par requête avec PDO::ATTR_EMULATE_PREPARES à false (vraies requêtes
            // préparées MySQL) — même piège déjà évité dans push.php (upsertExistingEntityRow), qui
            // utilise deux noms distincts pour la même valeur plutôt que de réutiliser un seul nom.
            $stmt = $pdo->prepare(
                'UPDATE ' . $table . '
                 SET deleted_at = :deleted_at, updated_at = :updated_at, version = version + 1
                 WHERE user_id = :user_id AND id IN (' . inPlaceholderList($duplicateIds) . ')'
            );
            $stmt->execute(array_merge(
                ['deleted_at' => $nowMillis, 'updated_at' => $nowMillis, 'user_id' => $userId],
                inPlaceholderValues($duplicateIds)
            ));

            $pdo->commit();
        } catch (Throwable $e) {
            $pdo->rollBack();
            echo '      ERREUR, groupe ignore (rien ecrit pour ce groupe) : ' . $e->getMessage() . "\n";
        }
    }
}

/**
 * Regroupe les lignes ACTIVES (deleted_at IS NULL) de [$table] par (user_id, name, type) — ne
 * retourne que les groupes de 2 lignes ou plus (les doublons). Chaque groupe est trié par
 * created_at ASC puis id ASC : la première ligne devient la "canonique" (voir [mergeDuplicates]).
 */
function findDuplicateGroups(PDO $pdo, string $table, ?string $onlyUserId): array
{
    $sql = "SELECT id, user_id, name, type, created_at FROM $table WHERE deleted_at IS NULL";
    $params = [];
    if ($onlyUserId !== null) {
        $sql .= ' AND user_id = :user_id';
        $params['user_id'] = $onlyUserId;
    }
    $sql .= ' ORDER BY user_id ASC, name ASC, type ASC, created_at ASC, id ASC';

    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);

    $buckets = [];
    while ($row = $stmt->fetch()) {
        $key = $row['user_id'] . '|' . $row['name'] . '|' . $row['type'];
        if (!isset($buckets[$key])) {
            $buckets[$key] = ['user_id' => $row['user_id'], 'name' => $row['name'], 'type' => $row['type'], 'rows' => []];
        }
        $buckets[$key]['rows'][] = $row;
    }

    return array_values(array_filter($buckets, static fn (array $b): bool => count($b['rows']) > 1));
}

/** Liste de placeholders nommés (`:dup0, :dup1, ...`) pour une clause `IN (...)` — PDO n'accepte pas
 *  un tableau directement dans une seule liaison. */
function inPlaceholderList(array $ids): string
{
    return implode(', ', array_map(static fn (int $i): string => ":dup$i", array_keys($ids)));
}

function inPlaceholderValues(array $ids): array
{
    $values = [];
    foreach ($ids as $i => $id) {
        $values["dup$i"] = $id;
    }
    return $values;
}
