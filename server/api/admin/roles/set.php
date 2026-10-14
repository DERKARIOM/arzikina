<?php

declare(strict_types=1);

require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../utils/admin_auth.php';
require_once __DIR__ . '/../../utils/admin_http.php';
require_once __DIR__ . '/../../utils/audit_log.php';

/**
 * POST /api/admin/roles/set.php — permission `roles.manage` (Super Admin UNIQUEMENT).
 * { userId, role: "admin" | "super_admin" | null }
 *
 * Garde-fous : on ne modifie jamais SON PROPRE rôle (ni élévation, ni retrait) ; la cible doit être
 * un compte actif ; le dernier Super Admin ne peut pas être rétrogradé. Un Admin ne peut en aucun
 * cas atteindre cet endpoint (403). Le PREMIER Super Admin s'attribue hors API (grant_role.php).
 */

requireHttpMethod('POST');
$pdo = getDatabaseConnection();
$admin = requirePermission($pdo, 'roles.manage');
$body = readJsonObject();

$userId = bodyString($body, 'userId', 64);
if (!array_key_exists('role', $body) || ($body['role'] !== null && !in_array($body['role'], [ROLE_ADMIN, ROLE_SUPER_ADMIN], true))) {
    sendError('invalid_body', 'role doit valoir admin, super_admin ou null.', 400);
}
$newRole = $body['role'];

if ($userId === $admin['userId']) {
    sendError('cannot_change_own_role', 'Vous ne pouvez pas modifier votre propre rôle.', 403);
}

try {
    $pdo->beginTransaction();
    $stmt = $pdo->prepare('SELECT id, admin_role FROM users WHERE id = :id AND deleted_at IS NULL FOR UPDATE');
    $stmt->execute(['id' => $userId]);
    $target = $stmt->fetch();
    if ($target === false) {
        $pdo->rollBack();
        sendError('not_found', 'Compte introuvable.', 404);
    }

    if ($target['admin_role'] === ROLE_SUPER_ADMIN && $newRole !== ROLE_SUPER_ADMIN) {
        $others = $pdo->prepare(
            "SELECT COUNT(*) FROM users WHERE admin_role = 'super_admin' AND deleted_at IS NULL AND id <> :id"
        );
        $others->execute(['id' => $userId]);
        if ((int) $others->fetchColumn() < 1) {
            $pdo->rollBack();
            sendError('last_super_admin', 'Le dernier Super Admin ne peut pas être rétrogradé.', 409);
        }
    }

    if ($target['admin_role'] !== $newRole) {
        $pdo->prepare('UPDATE users SET admin_role = :role WHERE id = :id')->execute(['role' => $newRole, 'id' => $userId]);
        recordAdminAudit($pdo, $admin, 'role.changed', 'user', $userId, ['adminRole' => $target['admin_role']], ['adminRole' => $newRole]);
    }
    $pdo->commit();
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('Arzikina API — admin/roles/set : ' . $e->getMessage());
    sendError('server_error', 'Le changement de rôle a échoué, réessaie plus tard.', 500);
}

sendJson(['userId' => $userId, 'role' => $newRole]);
