<?php

declare(strict_types=1);

require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../utils/admin_auth.php';
require_once __DIR__ . '/../../utils/admin_http.php';
require_once __DIR__ . '/../../utils/version_admin.php';

/**
 * GET /api/admin/versions/list.php — permission `versions.view` (Super Admin).
 * Toutes les versions (brouillons compris), de la plus récente à la plus ancienne.
 */

requireHttpMethod('GET');
$pdo = getDatabaseConnection();
requirePermission($pdo, 'versions.view');

try {
    $rows = $pdo->query(versionSelectSql() . ' ORDER BY v.version_code DESC LIMIT 500')->fetchAll();
} catch (Throwable $e) {
    error_log('Arzikina API — admin/versions/list : ' . $e->getMessage());
    sendError('server_error', 'Liste indisponible, réessaie plus tard.', 500);
}

sendJson(['items' => array_map('versionRowToArray', $rows)]);
