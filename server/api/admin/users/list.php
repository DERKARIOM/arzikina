<?php

declare(strict_types=1);

require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../utils/admin_auth.php';
require_once __DIR__ . '/../../utils/admin_http.php';

/**
 * GET /api/admin/users/list.php — permission `users.view` (Admin, Super Admin).
 *
 * Paramètres (tous optionnels) :
 *   q          recherche : nom, nom d'utilisateur, e-mail (contient) ou identifiant exact
 *   from, to   période de création, AAAA-MM-JJ inclus, heure du projet (Africa/Niamey)
 *   authMethod "google" | "email"
 *   sort       "created_desc" (défaut) | "created_asc"
 *   page, pageSize (≤ 100)
 *
 * Colonnes sélectionnées EXPLICITEMENT : jamais de mot de passe, de réponse de sécurité, de jeton,
 * de téléphone ni de contenu financier. Comptes supprimés exclus.
 */

requireHttpMethod('GET');
$pdo = getDatabaseConnection();
requirePermission($pdo, 'users.view');

$pagination = readPagination();
$where = ['u.deleted_at IS NULL'];
$params = [];

$q = trim((string) ($_GET['q'] ?? ''));
if ($q !== '') {
    if (mb_strlen($q) > 100) {
        sendError('invalid_query', 'Recherche trop longue (100 caractères maximum).', 400);
    }
    $where[] = '(u.full_name LIKE :like1 OR u.email LIKE :like2 OR u.username LIKE :like3 OR u.id = :exact)';
    $like = '%' . escapeLike($q) . '%';
    $params['like1'] = $params['like2'] = $params['like3'] = $like;
    $params['exact'] = $q;
}

if (($_GET['from'] ?? '') !== '') {
    $from = parseProjectDate((string) $_GET['from']);
    if ($from === null) {
        sendError('invalid_date', 'from doit être au format AAAA-MM-JJ.', 400);
    }
    $where[] = 'u.created_at >= :from';
    $params['from'] = $from;
}
if (($_GET['to'] ?? '') !== '') {
    $to = parseProjectDate((string) $_GET['to']);
    if ($to === null) {
        sendError('invalid_date', 'to doit être au format AAAA-MM-JJ.', 400);
    }
    // Borne haute EXCLUE = minuit du lendemain : le jour « to » est inclus en entier.
    $where[] = 'u.created_at < :to';
    $params['to'] = projectDayStartMillis($to + 12 * 3600 * 1000, 0) + 86400000;
}

$authMethod = (string) ($_GET['authMethod'] ?? '');
if ($authMethod !== '') {
    if (!in_array($authMethod, ['google', 'email'], true)) {
        sendError('invalid_filter', 'authMethod doit valoir google ou email.', 400);
    }
    $where[] = $authMethod === 'google' ? 'u.google_id IS NOT NULL' : 'u.google_id IS NULL';
}

$sort = (string) ($_GET['sort'] ?? 'created_desc');
if (!in_array($sort, ['created_desc', 'created_asc'], true)) {
    sendError('invalid_sort', 'sort doit valoir created_desc ou created_asc.', 400);
}
$direction = $sort === 'created_asc' ? 'ASC' : 'DESC';
$whereSql = implode(' AND ', $where);

try {
    $count = $pdo->prepare("SELECT COUNT(*) FROM users u WHERE {$whereSql}");
    $count->execute($params);
    $total = (int) $count->fetchColumn();

    $stmt = $pdo->prepare(
        "SELECT u.id, u.full_name, u.username, u.email, u.created_at, u.admin_role,
                (u.google_id IS NOT NULL) AS has_google
         FROM users u WHERE {$whereSql}
         ORDER BY u.created_at {$direction}, u.id {$direction}
         LIMIT :limit OFFSET :offset"
    );
    foreach ($params as $name => $value) {
        $stmt->bindValue(':' . $name, $value, is_int($value) ? PDO::PARAM_INT : PDO::PARAM_STR);
    }
    $stmt->bindValue(':limit', $pagination['pageSize'], PDO::PARAM_INT);
    $stmt->bindValue(':offset', $pagination['offset'], PDO::PARAM_INT);
    $stmt->execute();
    $rows = $stmt->fetchAll();

    // Dernière activité = dernier usage d'une session (vraie donnée enregistrée), uniquement pour
    // les lignes de la page.
    $activity = [];
    if ($rows !== []) {
        $ids = array_column($rows, 'id');
        $in = implode(',', array_fill(0, count($ids), '?'));
        $act = $pdo->prepare(
            "SELECT user_id, MAX(COALESCE(last_used_at, created_at)) AS last_activity
             FROM auth_tokens WHERE user_id IN ({$in}) GROUP BY user_id"
        );
        $act->execute($ids);
        foreach ($act->fetchAll() as $a) {
            $activity[$a['user_id']] = (int) $a['last_activity'];
        }
    }
} catch (Throwable $e) {
    error_log('Arzikina API — admin/users/list : ' . $e->getMessage());
    sendError('server_error', 'Liste indisponible, réessaie plus tard.', 500);
}

sendJson([
    'items' => array_map(static fn(array $r): array => [
        'id' => $r['id'],
        'fullName' => $r['full_name'],
        'username' => $r['username'],
        'email' => $r['email'],
        'createdAt' => (int) $r['created_at'],
        'lastActivityAt' => $activity[$r['id']] ?? null,
        'authMethod' => $r['has_google'] ? 'google' : 'email',
        'adminRole' => $r['admin_role'],
    ], $rows),
    'total' => $total,
    'page' => $pagination['page'],
    'pageSize' => $pagination['pageSize'],
]);
