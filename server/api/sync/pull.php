<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/entity_sync_configs.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/case_convert.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * GET /api/sync/pull.php?entity_type=categories&updated_after=<millis>
 *
 * Renvoie toutes les lignes de `entity_type` appartenant à l'utilisateur authentifié, modifiées
 * (ou supprimées — une suppression douce touche `updatedAt` comme toute autre modification, voir
 * `api/sync/push.php`) STRICTEMENT après `updated_after` (millisecondes epoch) — jamais un dump
 * complet de la table (docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 10 : "pull incrémental via
 * updated_after").
 *
 * GÉNÉRALISÉ (voir la doc de tête de `push.php`) : les colonnes à lire pour chaque `entity_type`
 * viennent de `config/entity_sync_configs.php`, même registre que `push.php` — plus de `switch`
 * dupliquant une requête par entité. `entity_type` reste un paramètre (pas juste "pull categories"
 * en dur) : ajouter une future entité ne change toujours pas la FORME de cette route, seulement le
 * registre partagé.
 *
 * Réponse : { "entities": [...], "serverTime": <millis> }. `serverTime` (horloge du SERVEUR, pas
 * de l'appareil) est la valeur que l'appareil doit conserver comme `updated_after` pour son
 * PROCHAIN pull — évite tout problème de décalage d'horloge entre l'appareil et le serveur.
 * IMPORTANT : `serverTime` est capturé AVANT l'exécution du SELECT ci-dessous, jamais après — voir
 * le commentaire sur sa capture plus bas pour la race condition que cet ordre évite.
 *
 * Lot plafonné à 500 lignes : si la réponse contient exactement 500 entités, l'appareil doit
 * rappeler pull.php avec le `serverTime` reçu (qui devient son nouveau `updated_after`) jusqu'à
 * recevoir un lot plus petit — protège contre une réponse démesurée après une longue période
 * hors-ligne.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    sendError('method_not_allowed', 'Cette route accepte uniquement GET.', 405);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);
$userId = $auth['userId'];

$entityType = (string) ($_GET['entity_type'] ?? '');
$updatedAfter = isset($_GET['updated_after']) ? (int) $_GET['updated_after'] : 0;
$batchLimit = 500;

// Capturé AVANT le SELECT (et non après son exécution) : si une écriture concurrente (ex. push.php
// appelé par un autre appareil) commit une ligne PENDANT que ce SELECT s'exécute, cette ligne a un
// `updated_at` postérieur à `$updatedAfter` mais n'apparaît pas dans `$rows` (le SELECT était déjà
// lancé). En capturant `serverTime` après coup, ce curseur aurait alors DÉPASSÉ cette ligne malgré
// tout, la rendant invisible pour toujours aux pulls incrémentaux suivants (bug réel observé : dérive
// de solde détectée à quasi chaque connexion, voir historique Git de ce fichier). En capturant
// `serverTime` ici, avant le SELECT, une telle ligne reste simplement au-delà du curseur renvoyé et
// sera renvoyée par le PROCHAIN pull — au pire un doublon inoffensif (upsert idempotent côté client
// via `applyEntities`), jamais une perte silencieuse.
$serverTime = (int) round(microtime(true) * 1000);

$entityConfig = ENTITY_CONFIGS[$entityType] ?? null;
if ($entityConfig === null) {
    sendError('unsupported_entity_type', "Type d'entité non pris en charge pour l'instant : $entityType", 400);
}

// Mêmes colonnes IMPLICITES + spécifiques que `push.php` (voir `fetchEntityRow` de ce fichier et
// la doc de tête de `ENTITY_CONFIGS`) — un seul autre endroit à faire évoluer si cet ensemble
// changeait un jour.
$columnNames = array_merge(
    ['id', 'user_id'],
    array_map(static fn (array $c): string => $c['db'], $entityConfig['columns']),
    ['created_at', 'updated_at', 'deleted_at', 'version']
);

$stmt = $pdo->prepare(
    'SELECT ' . implode(', ', $columnNames) . '
     FROM ' . $entityConfig['table'] . "
     WHERE user_id = :user_id AND updated_at > :updated_after
     ORDER BY updated_at ASC
     LIMIT $batchLimit"
);
$stmt->execute(['user_id' => $userId, 'updated_after' => $updatedAfter]);
$rows = $stmt->fetchAll();

sendJson([
    'entities' => array_map(static fn (array $row): array => toCamelCaseRow($row, $entityConfig), $rows),
    'serverTime' => $serverTime,
]);
