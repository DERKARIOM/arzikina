<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
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
 * `categories` et `savings_goals` sont câblées pour l'instant (voir la doc de tête de `push.php`
 * pour la même décision et sa justification). `entity_type` est déjà un paramètre — pas juste
 * "pull categories" en dur — pour que l'extension future n'ait pas besoin de changer la FORME de
 * cette route, seulement d'ajouter un `case` dans le switch ci-dessous (confirmé par cet ajout).
 *
 * Réponse : { "entities": [...], "serverTime": <millis> }. `serverTime` (horloge du SERVEUR, pas
 * de l'appareil) est la valeur que l'appareil doit conserver comme `updated_after` pour son
 * PROCHAIN pull — évite tout problème de décalage d'horloge entre l'appareil et le serveur.
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

switch ($entityType) {
    case 'categories':
        $stmt = $pdo->prepare(
            "SELECT id, user_id, name, icon, color_argb, type, created_at, updated_at, deleted_at, version
             FROM categories
             WHERE user_id = :user_id AND updated_at > :updated_after
             ORDER BY updated_at ASC
             LIMIT $batchLimit"
        );
        $stmt->execute(['user_id' => $userId, 'updated_after' => $updatedAfter]);
        $rows = $stmt->fetchAll();
        break;

    case 'savings_goals':
        $stmt = $pdo->prepare(
            "SELECT id, user_id, name, target_amount, current_amount, currency_code, deadline, created_at, updated_at, deleted_at, version
             FROM savings_goals
             WHERE user_id = :user_id AND updated_at > :updated_after
             ORDER BY updated_at ASC
             LIMIT $batchLimit"
        );
        $stmt->execute(['user_id' => $userId, 'updated_after' => $updatedAfter]);
        $rows = $stmt->fetchAll();
        break;

    default:
        sendError('unsupported_entity_type', "Type d'entité non pris en charge pour l'instant : $entityType", 400);
}

sendJson([
    'entities' => array_map('toCamelCaseRow', $rows),
    'serverTime' => (int) round(microtime(true) * 1000),
]);
