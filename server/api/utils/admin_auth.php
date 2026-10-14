<?php

declare(strict_types=1);

require_once __DIR__ . '/json_response.php';
require_once __DIR__ . '/permissions.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * Garde d'accès de TOUT endpoint `api/admin/*`.
 *
 * 1. Authentifie le jeton de session (401 sinon) via `requireAuthenticatedUser()`.
 * 2. Relit le compte EN BASE : le rôle n'est jamais lu dans le jeton ni dans la requête, donc un
 *    retrait de rôle prend effet immédiatement, sans attendre l'expiration de la session. Un compte
 *    supprimé est refusé.
 * 3. Vérifie la permission (403 `forbidden` sinon). Message volontairement identique pour « pas de
 *    rôle » et « rôle insuffisant » : ne révèle pas quels droits existent.
 *
 * @return array{userId: string, role: string, email: string, fullName: string}
 */
function requirePermission(PDO $pdo, string $permission): array
{
    $auth = requireAuthenticatedUser($pdo);

    $stmt = $pdo->prepare(
        'SELECT admin_role, email, full_name FROM users WHERE id = :id AND deleted_at IS NULL LIMIT 1'
    );
    $stmt->execute(['id' => $auth['userId']]);
    $row = $stmt->fetch();

    $role = $row !== false ? ($row['admin_role'] ?? null) : null;
    if ($row === false || !roleHasPermission($role, $permission)) {
        sendError('forbidden', "Vous n'avez pas l'autorisation d'effectuer cette action.", 403);
    }

    return [
        'userId' => $auth['userId'],
        'role' => $role,
        'email' => (string) $row['email'],
        'fullName' => (string) $row['full_name'],
    ];
}
