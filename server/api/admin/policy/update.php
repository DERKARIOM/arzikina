<?php

declare(strict_types=1);

require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../utils/admin_auth.php';
require_once __DIR__ . '/../../utils/admin_http.php';
require_once __DIR__ . '/../../utils/audit_log.php';
require_once __DIR__ . '/../../utils/version_admin.php';

/**
 * POST /api/admin/policy/update.php — permission `policy.manage` (Super Admin UNIQUEMENT : version
 * minimale, mise à jour obligatoire, maintenance, lien de mise à jour).
 *
 * Champs OPTIONNELS (seuls ceux fournis changent) :
 *   minVersionCode (int), forceUpdate (bool), updateUrl (https), blockUnversionedClients (bool),
 *   maintenanceEnabled (bool), maintenanceMessage (texte ≤ 500, null/"" pour effacer)
 *
 * Garde anti-blocage : forceUpdate exige minVersionCode ≥ 1 ET ≤ plus haute version PUBLIÉE.
 * Effet immédiat, sans redéploiement. Valeurs avant/après journalisées.
 */

requireHttpMethod('POST');
$pdo = getDatabaseConnection();
$admin = requirePermission($pdo, 'policy.manage');
$body = readJsonObject();

$changes = [];
if (array_key_exists('minVersionCode', $body)) {
    $changes['min_version_code'] = bodyInt($body, 'minVersionCode', 0, 2100000000);
}
if (array_key_exists('forceUpdate', $body)) {
    $changes['force_update'] = bodyBool($body, 'forceUpdate') ? 1 : 0;
}
if (array_key_exists('updateUrl', $body)) {
    $changes['update_url'] = validateHttpsUrl((string) bodyString($body, 'updateUrl', 512), 'updateUrl');
}
if (array_key_exists('blockUnversionedClients', $body)) {
    $changes['block_unversioned_clients'] = bodyBool($body, 'blockUnversionedClients') ? 1 : 0;
}
if (array_key_exists('maintenanceEnabled', $body)) {
    $changes['maintenance_enabled'] = bodyBool($body, 'maintenanceEnabled') ? 1 : 0;
}
if (array_key_exists('maintenanceMessage', $body)) {
    $changes['maintenance_message'] = bodyString($body, 'maintenanceMessage', 500, false);
}
if ($changes === []) {
    sendError('invalid_body', 'Aucun champ à modifier.', 400);
}

try {
    $pdo->beginTransaction();
    $current = $pdo->query('SELECT * FROM app_version_policy WHERE id = 1 FOR UPDATE')->fetch();
    if ($current === false) {
        $pdo->rollBack();
        sendError('server_error', 'Politique de version non initialisée (migration 010).', 500);
    }

    $effective = array_merge($current, $changes);
    $problem = policyLockoutProblem(
        (bool) $effective['force_update'],
        (int) $effective['min_version_code'],
        maxPublishedVersionCode($pdo)
    );
    if ($problem !== null) {
        $pdo->rollBack();
        sendError('policy_would_lock_users', $problem, 409);
    }

    $old = [];
    $new = [];
    foreach ($changes as $column => $value) {
        if ((string) ($current[$column] ?? '') !== (string) ($value ?? '')) {
            $old[$column] = $current[$column];
            $new[$column] = $value;
        }
    }
    if ($new !== []) {
        $sets = implode(', ', array_map(static fn(string $c): string => "{$c} = :{$c}", array_keys($new)));
        $pdo->prepare("UPDATE app_version_policy SET {$sets}, updated_at = :now_ts, updated_by = :admin WHERE id = 1")
            ->execute($new + ['now_ts' => nowMillis(), 'admin' => $admin['userId']]);
        recordAdminAudit($pdo, $admin, 'policy.updated', 'app_version_policy', '1', $old, $new);
    }
    $pdo->commit();
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('Arzikina API — admin/policy/update : ' . $e->getMessage());
    sendError('server_error', 'La mise à jour a échoué, réessaie plus tard.', 500);
}

sendJson(loadPolicyForAdmin($pdo) + ['maxPublishedVersionCode' => maxPublishedVersionCode($pdo)]);
