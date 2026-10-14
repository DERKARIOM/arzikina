<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/admin_auth.php';
require_once __DIR__ . '/../utils/admin_http.php';

/**
 * GET /api/admin/me.php — permission `dashboard.view` (Admin, Super Admin).
 *
 * Rôle et permissions du compte connecté, relus en base. Sert UNIQUEMENT à adapter l'interface
 * (menus affichés) : la sécurité réelle est appliquée par chaque endpoint, jamais par ce retour.
 * Un compte sans rôle reçoit 403 : la console n'affiche alors aucune section d'administration.
 */

requireHttpMethod('GET');
$admin = requirePermission(getDatabaseConnection(), 'dashboard.view');

sendJson([
    'userId' => $admin['userId'],
    'fullName' => $admin['fullName'],
    'email' => $admin['email'],
    'role' => $admin['role'],
    'permissions' => permissionsForRole($admin['role']),
]);
