<?php

declare(strict_types=1);

require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../utils/admin_auth.php';
require_once __DIR__ . '/../../utils/admin_http.php';

/**
 * GET /api/admin/audit/list.php[?action=policy.updated&page=1&pageSize=25] — permission
 * `audit.view` (Super Admin). Historique des actions d'administration, du plus récent au plus ancien.
 * `action` : préfixe exact ou famille (`policy.`, `version.`, `role.`, `user.`).
 */

requireHttpMethod('GET');
$pdo = getDatabaseConnection();
requirePermission($pdo, 'audit.view');
$pagination = readPagination(25, 100);

$where = '1=1';
$params = [];
$action = trim((string) ($_GET['action'] ?? ''));
if ($action !== '') {
    if (!preg_match('/^[a-z_]+(\.[a-z_]*)?$/', $action)) {
        sendError('invalid_filter', 'action invalide.', 400);
    }
    $where = 'action LIKE :action';
    $params['action'] = escapeLike($action) . '%';
}

try {
    $count = $pdo->prepare("SELECT COUNT(*) FROM admin_audit_log WHERE {$where}");
    $count->execute($params);
    $total = (int) $count->fetchColumn();

    $stmt = $pdo->prepare(
        "SELECT id, actor_label, action, target_type, target_id, old_value, new_value, created_at
         FROM admin_audit_log WHERE {$where} ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset"
    );
    foreach ($params as $name => $value) {
        $stmt->bindValue(':' . $name, $value);
    }
    $stmt->bindValue(':limit', $pagination['pageSize'], PDO::PARAM_INT);
    $stmt->bindValue(':offset', $pagination['offset'], PDO::PARAM_INT);
    $stmt->execute();
    $rows = $stmt->fetchAll();
} catch (Throwable $e) {
    error_log('Arzikina API — admin/audit/list : ' . $e->getMessage());
    sendError('server_error', 'Historique indisponible, réessaie plus tard.', 500);
}

sendJson([
    'items' => array_map(static fn(array $r): array => [
        'id' => (int) $r['id'],
        'actor' => $r['actor_label'],
        'action' => $r['action'],
        'targetType' => $r['target_type'],
        'targetId' => $r['target_id'],
        'oldValue' => $r['old_value'] !== null ? json_decode($r['old_value'], true) : null,
        'newValue' => $r['new_value'] !== null ? json_decode($r['new_value'], true) : null,
        'createdAt' => (int) $r['created_at'],
    ], $rows),
    'total' => $total,
    'page' => $pagination['page'],
    'pageSize' => $pagination['pageSize'],
]);
