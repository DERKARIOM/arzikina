<?php

declare(strict_types=1);

require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../utils/admin_auth.php';
require_once __DIR__ . '/../../utils/admin_http.php';
require_once __DIR__ . '/../../utils/audit_log.php';

/**
 * GET /api/admin/users/detail.php?id=<userId> — permission `users.view`.
 *
 * Fiche d'un compte : uniquement des informations d'administration non sensibles. JAMAIS : mot de
 * passe, jeton, secret OAuth, réponse de sécurité, téléphone, données financières. La consultation
 * d'une fiche (donnée personnelle) est journalisée.
 */

requireHttpMethod('GET');
$pdo = getDatabaseConnection();
$admin = requirePermission($pdo, 'users.view');

$id = (string) ($_GET['id'] ?? '');
if ($id === '' || strlen($id) > 64) {
    sendError('invalid_query', 'id obligatoire.', 400);
}

try {
    $stmt = $pdo->prepare(
        'SELECT id, full_name, username, email, created_at, admin_role, (google_id IS NOT NULL) AS has_google,
                (photo_path IS NOT NULL) AS has_photo
         FROM users WHERE id = :id AND deleted_at IS NULL LIMIT 1'
    );
    $stmt->execute(['id' => $id]);
    $user = $stmt->fetch();
    if ($user === false) {
        sendError('not_found', 'Compte introuvable.', 404);
    }

    $now = nowMillis();
    $sessions = $pdo->prepare(
        'SELECT COUNT(*) AS active_sessions, MAX(COALESCE(last_used_at, created_at)) AS last_activity
         FROM auth_tokens WHERE user_id = :id AND revoked_at IS NULL AND expires_at > :now'
    );
    $sessions->execute(['id' => $id, 'now' => $now]);
    $session = $sessions->fetch();

    $last = $pdo->prepare('SELECT MAX(COALESCE(last_used_at, created_at)) FROM auth_tokens WHERE user_id = :id');
    $last->execute(['id' => $id]);

    recordAdminAudit($pdo, $admin, 'user.viewed', 'user', $id);
} catch (Throwable $e) {
    error_log('Arzikina API — admin/users/detail : ' . $e->getMessage());
    sendError('server_error', 'Fiche indisponible, réessaie plus tard.', 500);
}

$lastActivity = $last->fetchColumn();
sendJson([
    'id' => $user['id'],
    'fullName' => $user['full_name'],
    'username' => $user['username'],
    'email' => $user['email'],
    'createdAt' => (int) $user['created_at'],
    'authMethod' => $user['has_google'] ? 'google' : 'email',
    'adminRole' => $user['admin_role'],
    'hasProfilePhoto' => (bool) $user['has_photo'],
    'activeSessions' => (int) $session['active_sessions'],
    'lastActivityAt' => $lastActivity !== false && $lastActivity !== null ? (int) $lastActivity : null,
]);
