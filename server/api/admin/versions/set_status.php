<?php

declare(strict_types=1);

require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../utils/admin_auth.php';
require_once __DIR__ . '/../../utils/admin_http.php';
require_once __DIR__ . '/../../utils/audit_log.php';
require_once __DIR__ . '/../../utils/version_admin.php';

/**
 * POST /api/admin/versions/set_status.php — permission `versions.manage` (Super Admin).
 * { id, status: "published" | "deprecated" | "withdrawn" }
 *
 * Publier, déclarer obsolète ou retirer une version (cycle : voir `VERSION_TRANSITIONS`).
 * Garde anti-blocage : refusé (409) si, après le changement, la politique en vigueur exigerait une
 * version minimale qui ne serait plus publiée. Une version n'est jamais supprimée (historique).
 */

requireHttpMethod('POST');
$pdo = getDatabaseConnection();
$admin = requirePermission($pdo, 'versions.manage');
$body = readJsonObject();

$id = bodyInt($body, 'id', 1, PHP_INT_MAX);
$target = bodyString($body, 'status', 16);
if (!in_array($target, VERSION_STATUSES, true) || $target === 'draft') {
    sendError('invalid_body', 'status doit valoir published, deprecated ou withdrawn.', 400);
}

try {
    $pdo->beginTransaction();

    $stmt = $pdo->prepare('SELECT * FROM android_versions WHERE id = :id FOR UPDATE');
    $stmt->execute(['id' => $id]);
    $version = $stmt->fetch();
    if ($version === false) {
        $pdo->rollBack();
        sendError('not_found', 'Version introuvable.', 404);
    }
    $from = (string) $version['status'];
    if (!canTransitionVersionStatus($from, $target)) {
        $pdo->rollBack();
        sendError('invalid_transition', "Transition impossible : {$from} → {$target}.", 409);
    }

    // Simule l'état après changement pour détecter un blocage généralisé AVANT d'écrire.
    $policy = $pdo->query('SELECT min_version_code, force_update FROM app_version_policy WHERE id = 1 FOR UPDATE')->fetch();
    if ($policy !== false) {
        $maxOther = $pdo->prepare("SELECT MAX(version_code) FROM android_versions WHERE status = 'published' AND id <> :id");
        $maxOther->execute(['id' => $id]);
        $m = $maxOther->fetchColumn();
        $maxAfter = $m === false || $m === null ? null : (int) $m;
        if ($target === 'published') {
            $maxAfter = max($maxAfter ?? 0, (int) $version['version_code']);
        }
        $problem = policyLockoutProblem((bool) $policy['force_update'], (int) $policy['min_version_code'], $maxAfter);
        if ($problem !== null) {
            $pdo->rollBack();
            sendError('policy_would_lock_users', 'Action refusée : la politique actuelle bloquerait les utilisateurs. ' . $problem, 409);
        }
    }

    $now = nowMillis();
    $pdo->prepare(
        'UPDATE android_versions SET status = :status,
                published_at = COALESCE(published_at, :published_at), updated_at = :now, updated_by = :admin
         WHERE id = :id'
    )->execute([
        'status' => $target,
        'published_at' => $target === 'published' ? $now : null,
        'now' => $now,
        'admin' => $admin['userId'],
        'id' => $id,
    ]);
    recordAdminAudit(
        $pdo, $admin, 'version.status_changed', 'android_version', (string) $id,
        ['status' => $from, 'versionCode' => (int) $version['version_code']], ['status' => $target]
    );
    $pdo->commit();
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('Arzikina API — admin/versions/set_status : ' . $e->getMessage());
    sendError('server_error', 'Le changement de statut a échoué, réessaie plus tard.', 500);
}

sendJson(['id' => $id, 'status' => $target]);
