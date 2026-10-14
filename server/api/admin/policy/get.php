<?php

declare(strict_types=1);

require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../utils/admin_auth.php';
require_once __DIR__ . '/../../utils/admin_http.php';
require_once __DIR__ . '/../../utils/version_admin.php';

/** GET /api/admin/policy/get.php — permission `versions.view` (Super Admin). Politique + plus haute version publiée. */

requireHttpMethod('GET');
$pdo = getDatabaseConnection();
requirePermission($pdo, 'versions.view');

try {
    $policy = loadPolicyForAdmin($pdo);
    $maxPublished = maxPublishedVersionCode($pdo);
} catch (Throwable $e) {
    error_log('Arzikina API — admin/policy/get : ' . $e->getMessage());
    sendError('server_error', 'Politique indisponible, réessaie plus tard.', 500);
}

sendJson($policy + ['maxPublishedVersionCode' => $maxPublished]);
