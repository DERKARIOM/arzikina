<?php

declare(strict_types=1);

require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../utils/admin_auth.php';
require_once __DIR__ . '/../../utils/admin_http.php';
require_once __DIR__ . '/../../utils/audit_log.php';
require_once __DIR__ . '/../../utils/version_admin.php';

/**
 * POST /api/admin/versions/save.php — permission `versions.manage` (Super Admin).
 *
 * Crée une version (sans `id`, statut « draft ») ou modifie ses informations (avec `id`) :
 *   { id?, versionName, versionCode, releaseNotes?, updateUrl? }
 * Le STATUT ne se change pas ici (voir set_status.php). Le versionCode d'une version déjà
 * publiée/obsolète/retirée est figé : il identifie ce qui a réellement été distribué.
 */

requireHttpMethod('POST');
$pdo = getDatabaseConnection();
$admin = requirePermission($pdo, 'versions.manage');
$body = readJsonObject();

$name = bodyString($body, 'versionName', 32);
if (!preg_match('/^\d+(\.\d+){1,3}([-+][A-Za-z0-9.]+)?$/', (string) $name)) {
    sendError('invalid_body', 'versionName invalide (ex. 1.2.0).', 400);
}
$code = bodyInt($body, 'versionCode', 1, 2100000000);
$notes = bodyString($body, 'releaseNotes', 5000, false);
$urlRaw = bodyString($body, 'updateUrl', 512, false);
$url = $urlRaw !== null ? validateHttpsUrl($urlRaw, 'updateUrl') : null;
$id = isset($body['id']) ? bodyInt($body, 'id', 1, PHP_INT_MAX) : null;
$now = nowMillis();

try {
    $pdo->beginTransaction();

    if ($id === null) {
        $pdo->prepare(
            "INSERT INTO android_versions
                (version_name, version_code, status, release_notes, update_url, created_at, updated_at, updated_by)
             VALUES (:name, :code, 'draft', :notes, :url, :created, :updated, :admin)"
        )->execute(['name' => $name, 'code' => $code, 'notes' => $notes, 'url' => $url, 'created' => $now, 'updated' => $now, 'admin' => $admin['userId']]);
        $newId = (string) $pdo->lastInsertId();
        recordAdminAudit($pdo, $admin, 'version.created', 'android_version', $newId, null, [
            'versionName' => $name, 'versionCode' => $code, 'status' => 'draft', 'releaseNotes' => $notes, 'updateUrl' => $url,
        ]);
        $pdo->commit();
        sendJson(['id' => (int) $newId], 201);
    }

    $stmt = $pdo->prepare('SELECT * FROM android_versions WHERE id = :id FOR UPDATE');
    $stmt->execute(['id' => $id]);
    $current = $stmt->fetch();
    if ($current === false) {
        $pdo->rollBack();
        sendError('not_found', 'Version introuvable.', 404);
    }
    if ($current['status'] !== 'draft' && (int) $current['version_code'] !== $code) {
        $pdo->rollBack();
        sendError('version_code_locked', "Le versionCode d'une version déjà publiée ne peut pas changer.", 409);
    }

    $old = [
        'versionName' => $current['version_name'], 'versionCode' => (int) $current['version_code'],
        'releaseNotes' => $current['release_notes'], 'updateUrl' => $current['update_url'],
    ];
    $new = ['versionName' => $name, 'versionCode' => $code, 'releaseNotes' => $notes, 'updateUrl' => $url];
    if ($old !== $new) {
        $pdo->prepare(
            'UPDATE android_versions SET version_name = :name, version_code = :code, release_notes = :notes,
                    update_url = :url, updated_at = :now, updated_by = :admin WHERE id = :id'
        )->execute(['name' => $name, 'code' => $code, 'notes' => $notes, 'url' => $url, 'now' => $now, 'admin' => $admin['userId'], 'id' => $id]);
        recordAdminAudit($pdo, $admin, 'version.updated', 'android_version', (string) $id, $old, $new);
    }
    $pdo->commit();
    sendJson(['id' => $id]);
} catch (PDOException $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    if ((int) ($e->errorInfo[1] ?? 0) === 1062) {
        sendError('version_code_taken', 'Ce versionCode existe déjà.', 409);
    }
    error_log('Arzikina API — admin/versions/save : ' . $e->getMessage());
    sendError('server_error', "L'enregistrement a échoué, réessaie plus tard.", 500);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('Arzikina API — admin/versions/save : ' . $e->getMessage());
    sendError('server_error', "L'enregistrement a échoué, réessaie plus tard.", 500);
}
