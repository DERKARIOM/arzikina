<?php

declare(strict_types=1);

/**
 * Rôles et permissions d'administration — SOURCE UNIQUE des droits (aucune autre liste de droits
 * ne doit exister, ni côté PHP ni dans l'interface Web, qui ne fait que masquer des boutons).
 *
 * Principes :
 *   - Refus par défaut : un rôle absent de {@see ROLE_PERMISSIONS}, ou une permission absente de sa
 *     liste, est refusé.
 *   - Ajouter un droit = ajouter une chaîne ici et l'exiger via `requirePermission()` dans
 *     l'endpoint concerné. Aucune autre modification n'est nécessaire.
 *   - `admin` n'obtient JAMAIS `versions.manage`, `policy.manage` ni `roles.manage` : modification
 *     de la version minimale, mises à jour obligatoires et attribution des rôles restent réservées
 *     au `super_admin`.
 */

const ROLE_ADMIN = 'admin';
const ROLE_SUPER_ADMIN = 'super_admin';

const ROLE_PERMISSIONS = [
    ROLE_ADMIN => [
        'dashboard.view',
        'users.view',
        'users.stats',
    ],
    ROLE_SUPER_ADMIN => [
        'dashboard.view',
        'users.view',
        'users.stats',
        'versions.view',
        'versions.manage',
        'policy.manage',
        'roles.manage',
        'audit.view',
    ],
];

/** Vrai si `$role` est un rôle d'administration connu (jamais pour NULL/inconnu). */
function isKnownAdminRole(?string $role): bool
{
    return $role !== null && array_key_exists($role, ROLE_PERMISSIONS);
}

/** Vrai uniquement si le rôle est connu ET possède explicitement la permission. */
function roleHasPermission(?string $role, string $permission): bool
{
    if (!isKnownAdminRole($role)) {
        return false;
    }
    return in_array($permission, ROLE_PERMISSIONS[$role], true);
}

/** @return list<string> Permissions du rôle (liste vide si rôle inconnu/NULL). */
function permissionsForRole(?string $role): array
{
    return isKnownAdminRole($role) ? ROLE_PERMISSIONS[$role] : [];
}
