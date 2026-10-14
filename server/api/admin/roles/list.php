<?php

declare(strict_types=1);

require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../utils/admin_auth.php';
require_once __DIR__ . '/../../utils/admin_http.php';

/** GET /api/admin/roles/list.php — permission `roles.manage` (Super Admin). Comptes ayant un rôle d'administration. */

requireHttpMethod('GET');
$pdo = getDatabaseConnection();
requirePermission($pdo, 'roles.manage');

try {
    $rows = $pdo->query(
        'SELECT id, full_name, email, admin_role, created_at FROM users
         WHERE admin_role IS NOT NULL AND deleted_at IS NULL ORDER BY admin_role DESC, created_at ASC LIMIT 200'
    )->fetchAll();
} catch (Throwable $e) {
    error_log('Arzikina API — admin/roles/list : ' . $e->getMessage());
    sendError('server_error', 'Liste indisponible, réessaie plus tard.', 500);
}

sendJson(['items' => array_map(static fn(array $r): array => [
    'id' => $r['id'], 'fullName' => $r['full_name'], 'email' => $r['email'],
    'role' => $r['admin_role'], 'createdAt' => (int) $r['created_at'],
], $rows)]);
