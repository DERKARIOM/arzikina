<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/entity_sync_configs.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/uuid.php';
require_once __DIR__ . '/../utils/case_convert.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * POST /api/sync/push.php
 *
 * Corps attendu (JSON) :
 * {
 *   "entityType": "categories",
 *   "operations": [
 *     { "operation": "CREATE"|"UPDATE"|"DELETE", "entity": { "id": "<uuid>", "baseVersion": <int|null>, ...champs... } }
 *   ]
 * }
 *
 * Reflète directement la table locale `sync_queue` côté Android (docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md,
 * section 8) : un appel = un lot d'entrées PENDING envoyées d'un coup, jamais une requête HTTP par
 * ligne modifiée. La réponse préserve l'ORDRE du tableau `operations` reçu : `results[i]`
 * correspond toujours à `operations[i]`.
 *
 * GÉNÉRALISÉ à la QUATRIÈME entité (`persons`, après `categories`/`savings_goals`/`financial_plans`
 * dupliquées jusque-là — voir l'historique Git de ce fichier pour la version dupliquée) : les
 * différences entre entités (table, colonnes, nullabilité) vivent désormais dans
 * `config/entity_sync_configs.php` (voir sa doc de tête), plus dans du code répété ici. Ajouter une
 * cinquième entité = une entrée dans ce registre, jamais un nouveau jeu de fonctions.
 *
 * SÉCURITÉ — `entity.userId` (ou toute variante), même présent dans le payload, est TOUJOURS
 * IGNORÉ : le propriétaire réel de chaque écriture est TOUJOURS celui du token (voir
 * `requireAuthenticatedUser`). Un appareil ne peut donc jamais écrire une ligne au nom d'un autre
 * utilisateur, même en modifiant le JSON envoyé — isolation multi-utilisateurs structurelle, pas
 * seulement une convention de code.
 *
 * RÉSOLUTION DE CONFLIT (section 9 du document) : Last-Write-Wins sur `updatedAt`, avec
 * journalisation SYSTÉMATIQUE dans `sync_conflicts` — jamais de perte silencieuse. Chaque résultat
 * indique si l'opération a été acceptée telle quelle ou si un conflit a été résolu ; dans les deux
 * cas, `serverEntity` contient l'état FINAL côté serveur, que l'appareil doit appliquer localement
 * pour rester cohérent (écrase sa propre version locale, même en cas de simple "accepted" — c'est
 * la même donnée, juste avec `version`/`updatedAt` désormais confirmés par le serveur).
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);
$userId = $auth['userId'];

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body) || !isset($body['entityType'], $body['operations']) || !is_array($body['operations'])) {
    sendError('invalid_body', 'Corps JSON invalide : entityType et operations sont obligatoires.', 400);
}

$entityType = (string) $body['entityType'];
$entityConfig = ENTITY_CONFIGS[$entityType] ?? null;
if ($entityConfig === null) {
    sendError('unsupported_entity_type', "Type d'entité non pris en charge pour l'instant : $entityType", 400);
}

$results = [];
foreach ($body['operations'] as $operation) {
    if (!is_array($operation) || !isset($operation['operation'], $operation['entity']) || !is_array($operation['entity'])) {
        $results[] = ['status' => 'error', 'errorCode' => 'invalid_operation'];
        continue;
    }

    $results[] = applyEntityOperation($pdo, $entityConfig, $entityType, $userId, (string) $operation['operation'], $operation['entity']);
}

sendJson(['results' => $results, 'serverTime' => (int) round(microtime(true) * 1000)]);

/**
 * Applique UNE opération (CREATE/UPDATE/DELETE) sur l'entité décrite par [$config] (voir
 * `ENTITY_CONFIGS`), avec détection de conflit. Retourne toujours `status`
 * ("accepted"|"conflict_resolved"|"error") et, sauf erreur, `entityId`/`serverEntity` (état final
 * côté serveur, en camelCase).
 */
function applyEntityOperation(PDO $pdo, array $config, string $entityType, string $userId, string $operationType, array $entity): array
{
    $id = (string) ($entity['id'] ?? '');
    if ($id === '') {
        // Repli défensif — voir la doc de tête de `utils/uuid.php` : ne devrait quasiment jamais
        // s'activer, le Sync Engine Android génère normalement toujours l'id avant l'envoi.
        $id = generateUuidV4();
    }

    $nowMillis = (int) round(microtime(true) * 1000);

    return match ($operationType) {
        'CREATE' => createEntityRow($pdo, $config, $userId, $id, $entity, $nowMillis),
        'UPDATE', 'DELETE' => upsertExistingEntityRow($pdo, $config, $entityType, $userId, $id, $operationType, $entity, $nowMillis),
        default => ['status' => 'error', 'errorCode' => 'invalid_operation_type', 'entityId' => $id],
    };
}

/** Cast PHP appliqué à une valeur de payload selon le `type` déclaré dans `ENTITY_CONFIGS`
 *  (`'int'`/`'string'`) — les deux seuls types utilisés par les entités actuelles. */
function castConfiguredValue(mixed $value, string $type): int|string
{
    return match ($type) {
        'int' => (int) $value,
        default => (string) $value,
    };
}

/** Valeur par défaut pour une colonne NON nullable absente du payload (ne devrait quasiment
 *  jamais arriver, le Sync Engine Android envoie toujours ses champs requis — filet de sécurité
 *  plutôt qu'un cas attendu). */
function defaultForConfiguredType(string $type): int|string
{
    return match ($type) {
        'int' => 0,
        default => '',
    };
}

function createEntityRow(PDO $pdo, array $config, string $userId, string $id, array $entity, int $nowMillis): array
{
    // Idempotence : si cette ligne existe déjà (ex. l'appareil a renvoyé la même opération après un
    // timeout réseau sans avoir reçu la réponse du premier essai), on ne recrée pas — on renvoie
    // l'état actuel, comme si la création avait réussi du premier coup. Évite un doublon silencieux.
    $existing = fetchEntityRow($pdo, $config, $userId, $id);
    if ($existing !== null) {
        return ['status' => 'accepted', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($existing)];
    }

    $dbColumns = array_map(static fn (array $c): string => $c['db'], $config['columns']);
    $placeholders = array_map(static fn (array $c): string => ':' . $c['db'], $config['columns']);

    $stmt = $pdo->prepare(
        'INSERT INTO ' . $config['table'] . ' (id, user_id, ' . implode(', ', $dbColumns) . ', created_at, updated_at, deleted_at, version)
         VALUES (:id, :user_id, ' . implode(', ', $placeholders) . ', :created_at, :updated_at, NULL, 1)'
    );

    $params = ['id' => $id, 'user_id' => $userId];
    foreach ($config['columns'] as $col) {
        if ($col['nullable']) {
            // Même raisonnement que `deadline`/`description`/`phone` avant la généralisation :
            // absent → NULL (une colonne nullable non envoyée par l'appareil n'a simplement jamais
            // de valeur à la création).
            $params[$col['db']] = isset($entity[$col['payload']]) ? castConfiguredValue($entity[$col['payload']], $col['type']) : null;
        } else {
            $params[$col['db']] = castConfiguredValue($entity[$col['payload']] ?? defaultForConfiguredType($col['type']), $col['type']);
        }
    }
    $params['created_at'] = (int) ($entity['createdAt'] ?? $nowMillis);
    $params['updated_at'] = (int) ($entity['updatedAt'] ?? $nowMillis);

    $stmt->execute($params);

    $row = fetchEntityRow($pdo, $config, $userId, $id);
    return ['status' => 'accepted', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($row)];
}

/**
 * UPDATE et DELETE (douce) partagent la même logique de détection de conflit — seule la ligne
 * réellement modifiée diffère (une suppression douce ne touche que `deleted_at`/`updated_at`/
 * `version`, jamais les autres champs, voir section 8 du document).
 */
function upsertExistingEntityRow(PDO $pdo, array $config, string $entityType, string $userId, string $id, string $operationType, array $entity, int $nowMillis): array
{
    $current = fetchEntityRow($pdo, $config, $userId, $id);
    if ($current === null) {
        return ['status' => 'error', 'errorCode' => 'not_found', 'entityId' => $id];
    }

    $baseVersion = isset($entity['baseVersion']) ? (int) $entity['baseVersion'] : null;
    $incomingUpdatedAt = (int) ($entity['updatedAt'] ?? $nowMillis);
    $currentVersion = (int) $current['version'];
    $currentUpdatedAt = (int) $current['updated_at'];

    $hasConflict = $baseVersion !== null && $baseVersion !== $currentVersion;

    // Conflit ET le serveur est déjà plus récent (ou égal) que l'écriture entrante : le serveur
    // GAGNE (Last-Write-Wins, section 9). L'écriture de l'appareil est journalisée comme perdante,
    // la ligne serveur n'est PAS modifiée — l'appareil doit adopter `serverEntity` tel quel.
    if ($hasConflict && $currentUpdatedAt >= $incomingUpdatedAt) {
        logConflict($pdo, $userId, $entityType, $id, $entity, $current, $nowMillis);
        return ['status' => 'conflict_resolved', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($current)];
    }

    if ($hasConflict) {
        // L'appareil GAGNE (son `updatedAt` est strictement plus récent) : la version serveur
        // actuelle est journalisée comme perdante AVANT d'être remplacée ci-dessous.
        logConflict($pdo, $userId, $entityType, $id, $current, $entity, $nowMillis);
    }

    if ($operationType === 'DELETE') {
        // Suppression douce GÉNÉRIQUE : ne touche jamais les colonnes spécifiques à l'entité,
        // identique quel que soit `$config`.
        $stmt = $pdo->prepare(
            'UPDATE ' . $config['table'] . ' SET deleted_at = :deleted_at, updated_at = :updated_at, version = version + 1
             WHERE id = :id AND user_id = :user_id'
        );
        $stmt->execute([
            'deleted_at' => $incomingUpdatedAt,
            'updated_at' => $incomingUpdatedAt,
            'id' => $id,
            'user_id' => $userId,
        ]);
    } else {
        $setClauses = [];
        $params = ['updated_at' => $incomingUpdatedAt, 'id' => $id, 'user_id' => $userId];
        foreach ($config['columns'] as $col) {
            $setClauses[] = $col['db'] . ' = :' . $col['db'];
            if ($col['nullable']) {
                // `array_key_exists` (pas `isset`) : distingue "l'appareil n'a pas envoyé ce champ"
                // (conserver la valeur actuelle) de "l'appareil a explicitement mis ce champ à
                // `null`" (effacer) — `isset` traiterait les deux cas identiquement (voir
                // `deadline`/`description`/`phone`, même raisonnement avant la généralisation).
                $params[$col['db']] = array_key_exists($col['payload'], $entity)
                    ? ($entity[$col['payload']] !== null ? castConfiguredValue($entity[$col['payload']], $col['type']) : null)
                    : $current[$col['db']];
            } else {
                $params[$col['db']] = castConfiguredValue($entity[$col['payload']] ?? $current[$col['db']], $col['type']);
            }
        }

        $stmt = $pdo->prepare(
            'UPDATE ' . $config['table'] . ' SET ' . implode(', ', $setClauses) . ', updated_at = :updated_at, deleted_at = NULL, version = version + 1
             WHERE id = :id AND user_id = :user_id'
        );
        $stmt->execute($params);
    }

    $row = fetchEntityRow($pdo, $config, $userId, $id);
    $status = $hasConflict ? 'conflict_resolved' : 'accepted';
    return ['status' => $status, 'entityId' => $id, 'serverEntity' => toCamelCaseRow($row)];
}

/** Colonnes IMPLICITES (`id`, `user_id`, `created_at`, `updated_at`, `deleted_at`, `version`) +
 *  colonnes spécifiques de [$config] — voir la doc de tête de `ENTITY_CONFIGS`. Partagée par
 *  `pull.php`, qui construit le même ensemble de colonnes pour son `SELECT`. */
function fetchEntityRow(PDO $pdo, array $config, string $userId, string $id): ?array
{
    $columnNames = array_merge(
        ['id', 'user_id'],
        array_map(static fn (array $c): string => $c['db'], $config['columns']),
        ['created_at', 'updated_at', 'deleted_at', 'version']
    );
    $stmt = $pdo->prepare(
        'SELECT ' . implode(', ', $columnNames) . ' FROM ' . $config['table'] . ' WHERE id = :id AND user_id = :user_id LIMIT 1'
    );
    $stmt->execute(['id' => $id, 'user_id' => $userId]);
    $row = $stmt->fetch();
    return $row === false ? null : $row;
}

function logConflict(PDO $pdo, string $userId, string $entityType, string $entityId, array $losingPayload, array $winningPayload, int $nowMillis): void
{
    $stmt = $pdo->prepare(
        'INSERT INTO sync_conflicts (user_id, entity_type, entity_id, losing_payload, winning_payload, created_at)
         VALUES (:user_id, :entity_type, :entity_id, :losing_payload, :winning_payload, :created_at)'
    );
    $stmt->execute([
        'user_id' => $userId,
        'entity_type' => $entityType,
        'entity_id' => $entityId,
        'losing_payload' => json_encode($losingPayload, JSON_UNESCAPED_UNICODE),
        'winning_payload' => json_encode($winningPayload, JSON_UNESCAPED_UNICODE),
        'created_at' => $nowMillis,
    ]);
}
