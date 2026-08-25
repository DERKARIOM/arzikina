<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
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
 * `categories`, `savings_goals`, `financial_plans` et `persons` sont câblées pour l'instant — voir
 * `pull.php` pour la même décision. Le découpage en petites fonctions ci-dessous
 * (`createCategory`/`createSavingsGoal`/`createFinancialPlan`/`createPerson`,
 * `upsertExistingCategory`/`upsertExistingSavingsGoal`/`upsertExistingFinancialPlan`/
 * `upsertExistingPerson`...) n'est TOUJOURS PAS extrait vers `services/` malgré cette QUATRIÈME
 * entité : la généralisation reste une étape SÉPARÉE et délibérément différée (voir le plan validé
 * à la règle de trois), pour ne jamais mélanger une nouvelle entité et un refactor comportemental
 * dans le même changement à vérifier.
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
$supportedEntityTypes = ['categories', 'savings_goals', 'financial_plans', 'persons'];
if (!in_array($entityType, $supportedEntityTypes, true)) {
    sendError('unsupported_entity_type', "Type d'entité non pris en charge pour l'instant : $entityType", 400);
}

$results = [];
foreach ($body['operations'] as $operation) {
    if (!is_array($operation) || !isset($operation['operation'], $operation['entity']) || !is_array($operation['entity'])) {
        $results[] = ['status' => 'error', 'errorCode' => 'invalid_operation'];
        continue;
    }

    $results[] = match ($entityType) {
        'categories' => applyCategoryOperation($pdo, $userId, (string) $operation['operation'], $operation['entity']),
        'savings_goals' => applySavingsGoalOperation($pdo, $userId, (string) $operation['operation'], $operation['entity']),
        'financial_plans' => applyFinancialPlanOperation($pdo, $userId, (string) $operation['operation'], $operation['entity']),
        'persons' => applyPersonOperation($pdo, $userId, (string) $operation['operation'], $operation['entity']),
    };
}

sendJson(['results' => $results, 'serverTime' => (int) round(microtime(true) * 1000)]);

/**
 * Applique UNE opération (CREATE/UPDATE/DELETE) sur `categories`, avec détection de conflit.
 * Retourne toujours `status` ("accepted"|"conflict_resolved"|"error") et, sauf erreur,
 * `entityId`/`serverEntity` (état final côté serveur, en camelCase).
 */
function applyCategoryOperation(PDO $pdo, string $userId, string $operationType, array $entity): array
{
    $id = (string) ($entity['id'] ?? '');
    if ($id === '') {
        // Repli défensif — voir la doc de tête de `utils/uuid.php` : ne devrait quasiment jamais
        // s'activer, le Sync Engine Android génère normalement toujours l'id avant l'envoi.
        $id = generateUuidV4();
    }

    $nowMillis = (int) round(microtime(true) * 1000);

    switch ($operationType) {
        case 'CREATE':
            return createCategory($pdo, $userId, $id, $entity, $nowMillis);

        case 'UPDATE':
        case 'DELETE':
            return upsertExistingCategory($pdo, $userId, $id, $operationType, $entity, $nowMillis);

        default:
            return ['status' => 'error', 'errorCode' => 'invalid_operation_type', 'entityId' => $id];
    }
}

function createCategory(PDO $pdo, string $userId, string $id, array $entity, int $nowMillis): array
{
    // Idempotence : si cette ligne existe déjà (ex. l'appareil a renvoyé la même opération après un
    // timeout réseau sans avoir reçu la réponse du premier essai), on ne recrée pas — on renvoie
    // l'état actuel, comme si la création avait réussi du premier coup. Évite un doublon silencieux.
    $existing = fetchCategoryRow($pdo, $userId, $id);
    if ($existing !== null) {
        return ['status' => 'accepted', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($existing)];
    }

    $stmt = $pdo->prepare(
        'INSERT INTO categories (id, user_id, name, icon, color_argb, type, created_at, updated_at, deleted_at, version)
         VALUES (:id, :user_id, :name, :icon, :color_argb, :type, :created_at, :updated_at, NULL, 1)'
    );
    $stmt->execute([
        'id' => $id,
        'user_id' => $userId,
        'name' => (string) ($entity['name'] ?? ''),
        'icon' => (string) ($entity['icon'] ?? ''),
        'color_argb' => (int) ($entity['colorArgb'] ?? 0),
        'type' => (string) ($entity['type'] ?? ''),
        'created_at' => (int) ($entity['createdAt'] ?? $nowMillis),
        'updated_at' => (int) ($entity['updatedAt'] ?? $nowMillis),
    ]);

    $row = fetchCategoryRow($pdo, $userId, $id);
    return ['status' => 'accepted', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($row)];
}

/**
 * UPDATE et DELETE (douce) partagent la même logique de détection de conflit — seule la ligne
 * réellement modifiée diffère (une suppression douce ne touche que `deletedAt`/`updatedAt`/
 * `version`, jamais les autres champs, voir section 8 du document).
 */
function upsertExistingCategory(PDO $pdo, string $userId, string $id, string $operationType, array $entity, int $nowMillis): array
{
    $current = fetchCategoryRow($pdo, $userId, $id);
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
        logConflict($pdo, $userId, 'categories', $id, $entity, $current, $nowMillis);
        return ['status' => 'conflict_resolved', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($current)];
    }

    if ($hasConflict) {
        // L'appareil GAGNE (son `updatedAt` est strictement plus récent) : la version serveur
        // actuelle est journalisée comme perdante AVANT d'être remplacée ci-dessous.
        logConflict($pdo, $userId, 'categories', $id, $current, $entity, $nowMillis);
    }

    if ($operationType === 'DELETE') {
        $stmt = $pdo->prepare(
            'UPDATE categories SET deleted_at = :deleted_at, updated_at = :updated_at, version = version + 1
             WHERE id = :id AND user_id = :user_id'
        );
        $stmt->execute([
            'deleted_at' => $incomingUpdatedAt,
            'updated_at' => $incomingUpdatedAt,
            'id' => $id,
            'user_id' => $userId,
        ]);
    } else {
        $stmt = $pdo->prepare(
            'UPDATE categories
             SET name = :name, icon = :icon, color_argb = :color_argb, type = :type,
                 updated_at = :updated_at, deleted_at = NULL, version = version + 1
             WHERE id = :id AND user_id = :user_id'
        );
        $stmt->execute([
            'name' => (string) ($entity['name'] ?? $current['name']),
            'icon' => (string) ($entity['icon'] ?? $current['icon']),
            'color_argb' => (int) ($entity['colorArgb'] ?? $current['color_argb']),
            'type' => (string) ($entity['type'] ?? $current['type']),
            'updated_at' => $incomingUpdatedAt,
            'id' => $id,
            'user_id' => $userId,
        ]);
    }

    $row = fetchCategoryRow($pdo, $userId, $id);
    $status = $hasConflict ? 'conflict_resolved' : 'accepted';
    return ['status' => $status, 'entityId' => $id, 'serverEntity' => toCamelCaseRow($row)];
}

function fetchCategoryRow(PDO $pdo, string $userId, string $id): ?array
{
    $stmt = $pdo->prepare(
        'SELECT id, user_id, name, icon, color_argb, type, created_at, updated_at, deleted_at, version
         FROM categories WHERE id = :id AND user_id = :user_id LIMIT 1'
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

/**
 * `savings_goals` — même schéma que `categories` ci-dessus (voir la doc de tête sur la décision de
 * dupliquer plutôt que de généraliser à cette étape). `deadline` est le seul champ NULLABLE de
 * cette entité : `array_key_exists` (pas `isset`) dans `upsertExistingSavingsGoal` pour distinguer
 * "l'appareil n'a pas envoyé ce champ" (conserver la valeur actuelle) de "l'appareil a explicitement
 * mis `deadline` à `null`" (effacer l'échéance) — `isset` traiterait les deux cas identiquement.
 */
function applySavingsGoalOperation(PDO $pdo, string $userId, string $operationType, array $entity): array
{
    $id = (string) ($entity['id'] ?? '');
    if ($id === '') {
        $id = generateUuidV4();
    }

    $nowMillis = (int) round(microtime(true) * 1000);

    switch ($operationType) {
        case 'CREATE':
            return createSavingsGoal($pdo, $userId, $id, $entity, $nowMillis);

        case 'UPDATE':
        case 'DELETE':
            return upsertExistingSavingsGoal($pdo, $userId, $id, $operationType, $entity, $nowMillis);

        default:
            return ['status' => 'error', 'errorCode' => 'invalid_operation_type', 'entityId' => $id];
    }
}

function createSavingsGoal(PDO $pdo, string $userId, string $id, array $entity, int $nowMillis): array
{
    $existing = fetchSavingsGoalRow($pdo, $userId, $id);
    if ($existing !== null) {
        return ['status' => 'accepted', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($existing)];
    }

    $stmt = $pdo->prepare(
        'INSERT INTO savings_goals (id, user_id, name, target_amount, current_amount, currency_code, deadline, created_at, updated_at, deleted_at, version)
         VALUES (:id, :user_id, :name, :target_amount, :current_amount, :currency_code, :deadline, :created_at, :updated_at, NULL, 1)'
    );
    $stmt->execute([
        'id' => $id,
        'user_id' => $userId,
        'name' => (string) ($entity['name'] ?? ''),
        'target_amount' => (int) ($entity['targetAmount'] ?? 0),
        'current_amount' => (int) ($entity['currentAmount'] ?? 0),
        'currency_code' => (string) ($entity['currencyCode'] ?? ''),
        'deadline' => isset($entity['deadline']) ? (int) $entity['deadline'] : null,
        'created_at' => (int) ($entity['createdAt'] ?? $nowMillis),
        'updated_at' => (int) ($entity['updatedAt'] ?? $nowMillis),
    ]);

    $row = fetchSavingsGoalRow($pdo, $userId, $id);
    return ['status' => 'accepted', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($row)];
}

function upsertExistingSavingsGoal(PDO $pdo, string $userId, string $id, string $operationType, array $entity, int $nowMillis): array
{
    $current = fetchSavingsGoalRow($pdo, $userId, $id);
    if ($current === null) {
        return ['status' => 'error', 'errorCode' => 'not_found', 'entityId' => $id];
    }

    $baseVersion = isset($entity['baseVersion']) ? (int) $entity['baseVersion'] : null;
    $incomingUpdatedAt = (int) ($entity['updatedAt'] ?? $nowMillis);
    $currentVersion = (int) $current['version'];
    $currentUpdatedAt = (int) $current['updated_at'];

    $hasConflict = $baseVersion !== null && $baseVersion !== $currentVersion;

    if ($hasConflict && $currentUpdatedAt >= $incomingUpdatedAt) {
        logConflict($pdo, $userId, 'savings_goals', $id, $entity, $current, $nowMillis);
        return ['status' => 'conflict_resolved', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($current)];
    }

    if ($hasConflict) {
        logConflict($pdo, $userId, 'savings_goals', $id, $current, $entity, $nowMillis);
    }

    if ($operationType === 'DELETE') {
        $stmt = $pdo->prepare(
            'UPDATE savings_goals SET deleted_at = :deleted_at, updated_at = :updated_at, version = version + 1
             WHERE id = :id AND user_id = :user_id'
        );
        $stmt->execute([
            'deleted_at' => $incomingUpdatedAt,
            'updated_at' => $incomingUpdatedAt,
            'id' => $id,
            'user_id' => $userId,
        ]);
    } else {
        $stmt = $pdo->prepare(
            'UPDATE savings_goals
             SET name = :name, target_amount = :target_amount, current_amount = :current_amount,
                 currency_code = :currency_code, deadline = :deadline, updated_at = :updated_at,
                 deleted_at = NULL, version = version + 1
             WHERE id = :id AND user_id = :user_id'
        );
        $stmt->execute([
            'name' => (string) ($entity['name'] ?? $current['name']),
            'target_amount' => (int) ($entity['targetAmount'] ?? $current['target_amount']),
            'current_amount' => (int) ($entity['currentAmount'] ?? $current['current_amount']),
            'currency_code' => (string) ($entity['currencyCode'] ?? $current['currency_code']),
            'deadline' => array_key_exists('deadline', $entity)
                ? ($entity['deadline'] !== null ? (int) $entity['deadline'] : null)
                : $current['deadline'],
            'updated_at' => $incomingUpdatedAt,
            'id' => $id,
            'user_id' => $userId,
        ]);
    }

    $row = fetchSavingsGoalRow($pdo, $userId, $id);
    $status = $hasConflict ? 'conflict_resolved' : 'accepted';
    return ['status' => $status, 'entityId' => $id, 'serverEntity' => toCamelCaseRow($row)];
}

function fetchSavingsGoalRow(PDO $pdo, string $userId, string $id): ?array
{
    $stmt = $pdo->prepare(
        'SELECT id, user_id, name, target_amount, current_amount, currency_code, deadline, created_at, updated_at, deleted_at, version
         FROM savings_goals WHERE id = :id AND user_id = :user_id LIMIT 1'
    );
    $stmt->execute(['id' => $id, 'user_id' => $userId]);
    $row = $stmt->fetch();
    return $row === false ? null : $row;
}

/**
 * `financial_plans` — même schéma que `categories`/`savings_goals` ci-dessus. QUATRE champs
 * NULLABLE (`description`, `target_amount`, `start_date`, `end_date`, voir
 * `database/migrations/001_initial_schema.sql`) : `array_key_exists` (pas `isset`) pour chacun dans
 * `upsertExistingFinancialPlan`, même raisonnement que `deadline` pour `savings_goals`. Les
 * dépenses prévues (`financial_plan_items`) ne sont PAS synchronisées à cette étape — seule la
 * planification elle-même l'est (voir le plan validé).
 */
function applyFinancialPlanOperation(PDO $pdo, string $userId, string $operationType, array $entity): array
{
    $id = (string) ($entity['id'] ?? '');
    if ($id === '') {
        $id = generateUuidV4();
    }

    $nowMillis = (int) round(microtime(true) * 1000);

    switch ($operationType) {
        case 'CREATE':
            return createFinancialPlan($pdo, $userId, $id, $entity, $nowMillis);

        case 'UPDATE':
        case 'DELETE':
            return upsertExistingFinancialPlan($pdo, $userId, $id, $operationType, $entity, $nowMillis);

        default:
            return ['status' => 'error', 'errorCode' => 'invalid_operation_type', 'entityId' => $id];
    }
}

function createFinancialPlan(PDO $pdo, string $userId, string $id, array $entity, int $nowMillis): array
{
    $existing = fetchFinancialPlanRow($pdo, $userId, $id);
    if ($existing !== null) {
        return ['status' => 'accepted', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($existing)];
    }

    $stmt = $pdo->prepare(
        'INSERT INTO financial_plans (id, user_id, name, description, available_amount, target_amount,
             period_type, start_date, end_date, icon, color_argb, status, created_at, updated_at, deleted_at, version)
         VALUES (:id, :user_id, :name, :description, :available_amount, :target_amount,
             :period_type, :start_date, :end_date, :icon, :color_argb, :status, :created_at, :updated_at, NULL, 1)'
    );
    $stmt->execute([
        'id' => $id,
        'user_id' => $userId,
        'name' => (string) ($entity['name'] ?? ''),
        'description' => isset($entity['description']) ? (string) $entity['description'] : null,
        'available_amount' => (int) ($entity['availableAmount'] ?? 0),
        'target_amount' => isset($entity['targetAmount']) ? (int) $entity['targetAmount'] : null,
        'period_type' => (string) ($entity['periodType'] ?? ''),
        'start_date' => isset($entity['startDate']) ? (int) $entity['startDate'] : null,
        'end_date' => isset($entity['endDate']) ? (int) $entity['endDate'] : null,
        'icon' => (string) ($entity['icon'] ?? ''),
        'color_argb' => (int) ($entity['colorArgb'] ?? 0),
        'status' => (string) ($entity['status'] ?? ''),
        'created_at' => (int) ($entity['createdAt'] ?? $nowMillis),
        'updated_at' => (int) ($entity['updatedAt'] ?? $nowMillis),
    ]);

    $row = fetchFinancialPlanRow($pdo, $userId, $id);
    return ['status' => 'accepted', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($row)];
}

function upsertExistingFinancialPlan(PDO $pdo, string $userId, string $id, string $operationType, array $entity, int $nowMillis): array
{
    $current = fetchFinancialPlanRow($pdo, $userId, $id);
    if ($current === null) {
        return ['status' => 'error', 'errorCode' => 'not_found', 'entityId' => $id];
    }

    $baseVersion = isset($entity['baseVersion']) ? (int) $entity['baseVersion'] : null;
    $incomingUpdatedAt = (int) ($entity['updatedAt'] ?? $nowMillis);
    $currentVersion = (int) $current['version'];
    $currentUpdatedAt = (int) $current['updated_at'];

    $hasConflict = $baseVersion !== null && $baseVersion !== $currentVersion;

    if ($hasConflict && $currentUpdatedAt >= $incomingUpdatedAt) {
        logConflict($pdo, $userId, 'financial_plans', $id, $entity, $current, $nowMillis);
        return ['status' => 'conflict_resolved', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($current)];
    }

    if ($hasConflict) {
        logConflict($pdo, $userId, 'financial_plans', $id, $current, $entity, $nowMillis);
    }

    if ($operationType === 'DELETE') {
        $stmt = $pdo->prepare(
            'UPDATE financial_plans SET deleted_at = :deleted_at, updated_at = :updated_at, version = version + 1
             WHERE id = :id AND user_id = :user_id'
        );
        $stmt->execute([
            'deleted_at' => $incomingUpdatedAt,
            'updated_at' => $incomingUpdatedAt,
            'id' => $id,
            'user_id' => $userId,
        ]);
    } else {
        $stmt = $pdo->prepare(
            'UPDATE financial_plans
             SET name = :name, description = :description, available_amount = :available_amount,
                 target_amount = :target_amount, period_type = :period_type, start_date = :start_date,
                 end_date = :end_date, icon = :icon, color_argb = :color_argb, status = :status,
                 updated_at = :updated_at, deleted_at = NULL, version = version + 1
             WHERE id = :id AND user_id = :user_id'
        );
        $stmt->execute([
            'name' => (string) ($entity['name'] ?? $current['name']),
            'description' => array_key_exists('description', $entity)
                ? ($entity['description'] !== null ? (string) $entity['description'] : null)
                : $current['description'],
            'available_amount' => (int) ($entity['availableAmount'] ?? $current['available_amount']),
            'target_amount' => array_key_exists('targetAmount', $entity)
                ? ($entity['targetAmount'] !== null ? (int) $entity['targetAmount'] : null)
                : $current['target_amount'],
            'period_type' => (string) ($entity['periodType'] ?? $current['period_type']),
            'start_date' => array_key_exists('startDate', $entity)
                ? ($entity['startDate'] !== null ? (int) $entity['startDate'] : null)
                : $current['start_date'],
            'end_date' => array_key_exists('endDate', $entity)
                ? ($entity['endDate'] !== null ? (int) $entity['endDate'] : null)
                : $current['end_date'],
            'icon' => (string) ($entity['icon'] ?? $current['icon']),
            'color_argb' => (int) ($entity['colorArgb'] ?? $current['color_argb']),
            'status' => (string) ($entity['status'] ?? $current['status']),
            'updated_at' => $incomingUpdatedAt,
            'id' => $id,
            'user_id' => $userId,
        ]);
    }

    $row = fetchFinancialPlanRow($pdo, $userId, $id);
    $status = $hasConflict ? 'conflict_resolved' : 'accepted';
    return ['status' => $status, 'entityId' => $id, 'serverEntity' => toCamelCaseRow($row)];
}

function fetchFinancialPlanRow(PDO $pdo, string $userId, string $id): ?array
{
    $stmt = $pdo->prepare(
        'SELECT id, user_id, name, description, available_amount, target_amount, period_type,
             start_date, end_date, icon, color_argb, status, created_at, updated_at, deleted_at, version
         FROM financial_plans WHERE id = :id AND user_id = :user_id LIMIT 1'
    );
    $stmt->execute(['id' => $id, 'user_id' => $userId]);
    $row = $stmt->fetch();
    return $row === false ? null : $row;
}

/**
 * `persons` — même schéma que `categories`/`savings_goals`/`financial_plans` ci-dessus. UN SEUL
 * champ NULLABLE (`phone`, voir `database/migrations/001_initial_schema.sql`) : `array_key_exists`
 * (pas `isset`) dans `upsertExistingPerson`, même raisonnement que `deadline`/`description` sur les
 * autres entités. Les prêts/emprunts (`loans`) ne sont PAS synchronisés à cette étape — seule la
 * personne elle-même l'est (voir le plan validé) ; côté Android, leur suppression reste physique et
 * explicite (voir `PersonRepositoryImpl.deletePerson`), inchangée par ce câblage.
 */
function applyPersonOperation(PDO $pdo, string $userId, string $operationType, array $entity): array
{
    $id = (string) ($entity['id'] ?? '');
    if ($id === '') {
        $id = generateUuidV4();
    }

    $nowMillis = (int) round(microtime(true) * 1000);

    switch ($operationType) {
        case 'CREATE':
            return createPerson($pdo, $userId, $id, $entity, $nowMillis);

        case 'UPDATE':
        case 'DELETE':
            return upsertExistingPerson($pdo, $userId, $id, $operationType, $entity, $nowMillis);

        default:
            return ['status' => 'error', 'errorCode' => 'invalid_operation_type', 'entityId' => $id];
    }
}

function createPerson(PDO $pdo, string $userId, string $id, array $entity, int $nowMillis): array
{
    $existing = fetchPersonRow($pdo, $userId, $id);
    if ($existing !== null) {
        return ['status' => 'accepted', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($existing)];
    }

    $stmt = $pdo->prepare(
        'INSERT INTO persons (id, user_id, name, phone, created_at, updated_at, deleted_at, version)
         VALUES (:id, :user_id, :name, :phone, :created_at, :updated_at, NULL, 1)'
    );
    $stmt->execute([
        'id' => $id,
        'user_id' => $userId,
        'name' => (string) ($entity['name'] ?? ''),
        'phone' => isset($entity['phone']) ? (string) $entity['phone'] : null,
        'created_at' => (int) ($entity['createdAt'] ?? $nowMillis),
        'updated_at' => (int) ($entity['updatedAt'] ?? $nowMillis),
    ]);

    $row = fetchPersonRow($pdo, $userId, $id);
    return ['status' => 'accepted', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($row)];
}

function upsertExistingPerson(PDO $pdo, string $userId, string $id, string $operationType, array $entity, int $nowMillis): array
{
    $current = fetchPersonRow($pdo, $userId, $id);
    if ($current === null) {
        return ['status' => 'error', 'errorCode' => 'not_found', 'entityId' => $id];
    }

    $baseVersion = isset($entity['baseVersion']) ? (int) $entity['baseVersion'] : null;
    $incomingUpdatedAt = (int) ($entity['updatedAt'] ?? $nowMillis);
    $currentVersion = (int) $current['version'];
    $currentUpdatedAt = (int) $current['updated_at'];

    $hasConflict = $baseVersion !== null && $baseVersion !== $currentVersion;

    if ($hasConflict && $currentUpdatedAt >= $incomingUpdatedAt) {
        logConflict($pdo, $userId, 'persons', $id, $entity, $current, $nowMillis);
        return ['status' => 'conflict_resolved', 'entityId' => $id, 'serverEntity' => toCamelCaseRow($current)];
    }

    if ($hasConflict) {
        logConflict($pdo, $userId, 'persons', $id, $current, $entity, $nowMillis);
    }

    if ($operationType === 'DELETE') {
        $stmt = $pdo->prepare(
            'UPDATE persons SET deleted_at = :deleted_at, updated_at = :updated_at, version = version + 1
             WHERE id = :id AND user_id = :user_id'
        );
        $stmt->execute([
            'deleted_at' => $incomingUpdatedAt,
            'updated_at' => $incomingUpdatedAt,
            'id' => $id,
            'user_id' => $userId,
        ]);
    } else {
        $stmt = $pdo->prepare(
            'UPDATE persons
             SET name = :name, phone = :phone, updated_at = :updated_at, deleted_at = NULL, version = version + 1
             WHERE id = :id AND user_id = :user_id'
        );
        $stmt->execute([
            'name' => (string) ($entity['name'] ?? $current['name']),
            'phone' => array_key_exists('phone', $entity)
                ? ($entity['phone'] !== null ? (string) $entity['phone'] : null)
                : $current['phone'],
            'updated_at' => $incomingUpdatedAt,
            'id' => $id,
            'user_id' => $userId,
        ]);
    }

    $row = fetchPersonRow($pdo, $userId, $id);
    $status = $hasConflict ? 'conflict_resolved' : 'accepted';
    return ['status' => $status, 'entityId' => $id, 'serverEntity' => toCamelCaseRow($row)];
}

function fetchPersonRow(PDO $pdo, string $userId, string $id): ?array
{
    $stmt = $pdo->prepare(
        'SELECT id, user_id, name, phone, created_at, updated_at, deleted_at, version
         FROM persons WHERE id = :id AND user_id = :user_id LIMIT 1'
    );
    $stmt->execute(['id' => $id, 'user_id' => $userId]);
    $row = $stmt->fetch();
    return $row === false ? null : $row;
}
