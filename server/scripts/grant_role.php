<?php

declare(strict_types=1);

/**
 * Attribution / retrait d'un rôle d'administration — LIGNE DE COMMANDE UNIQUEMENT.
 *
 *   php server/scripts/grant_role.php --email=adresse@exemple.com --role=super_admin
 *   php server/scripts/grant_role.php --email=adresse@exemple.com --role=admin
 *   php server/scripts/grant_role.php --email=adresse@exemple.com --role=none
 *
 * Voie d'initialisation sécurisée du PREMIER Super Admin : aucune requête HTTP ne peut créer un
 * Super Admin sans déjà en être un. Refuse de s'exécuter hors CLI. L'action est journalisée dans
 * `admin_audit_log` (acteur « cli »).
 *
 * Alternative sans SSH (phpMyAdmin) :
 *   UPDATE users SET admin_role = 'super_admin' WHERE email = '...' AND deleted_at IS NULL;
 */

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/../api/config/database.php';
require_once __DIR__ . '/../api/utils/permissions.php';
require_once __DIR__ . '/../api/utils/audit_log.php';

$options = getopt('', ['email:', 'role:']);
$email = trim((string) ($options['email'] ?? ''));
$role = trim((string) ($options['role'] ?? ''));

if ($email === '' || !in_array($role, [ROLE_ADMIN, ROLE_SUPER_ADMIN, 'none'], true)) {
    fwrite(STDERR, "Usage : php grant_role.php --email=<adresse> --role=<admin|super_admin|none>\n");
    exit(2);
}

$pdo = getDatabaseConnection();
$newRole = $role === 'none' ? null : $role;

$pdo->beginTransaction();
try {
    $stmt = $pdo->prepare('SELECT id, admin_role FROM users WHERE email = :email AND deleted_at IS NULL LIMIT 1');
    $stmt->execute(['email' => $email]);
    $user = $stmt->fetch();
    if ($user === false) {
        $pdo->rollBack();
        fwrite(STDERR, "Aucun compte actif avec cette adresse e-mail.\n");
        exit(1);
    }

    $update = $pdo->prepare('UPDATE users SET admin_role = :role WHERE id = :id');
    $update->execute(['role' => $newRole, 'id' => $user['id']]);

    recordAdminAudit(
        $pdo,
        ['userId' => null, 'email' => 'cli'],
        'role.changed',
        'user',
        (string) $user['id'],
        ['adminRole' => $user['admin_role']],
        ['adminRole' => $newRole],
    );
    $pdo->commit();
} catch (Throwable $e) {
    $pdo->rollBack();
    fwrite(STDERR, 'Échec : ' . $e->getMessage() . "\n");
    exit(1);
}

echo "OK : {$email} → " . ($newRole ?? 'aucun rôle') . "\n";
