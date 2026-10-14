<?php

declare(strict_types=1);

/**
 * Règles métier de la gestion des versions Android (côté administration) — partagées par les
 * endpoints `admin/versions/*` et `admin/policy/*` pour qu'une règle n'existe qu'à un endroit.
 */

const VERSION_STATUSES = ['draft', 'published', 'deprecated', 'withdrawn'];

/**
 * Cycle de vie autorisé : brouillon → publiée → obsolète → retirée. Une version retirée est
 * définitive (l'historique reste consultable) ; une obsolète peut être republiée.
 */
const VERSION_TRANSITIONS = [
    'draft' => ['published', 'withdrawn'],
    'published' => ['deprecated', 'withdrawn'],
    'deprecated' => ['published', 'withdrawn'],
    'withdrawn' => [],
];

function canTransitionVersionStatus(string $from, string $to): bool
{
    return in_array($to, VERSION_TRANSITIONS[$from] ?? [], true);
}

/** Plus grand versionCode au statut « published », ou null s'il n'y en a aucune. */
function maxPublishedVersionCode(PDO $pdo): ?int
{
    $value = $pdo->query("SELECT MAX(version_code) FROM android_versions WHERE status = 'published'")->fetchColumn();
    return $value === false || $value === null ? null : (int) $value;
}

/**
 * Garde anti-blocage général : une mise à jour obligatoire ne peut exiger une version qui n'est
 * pas (ou plus) PUBLIÉE — sinon plus aucun utilisateur ne pourrait se mettre à jour.
 *
 * @return string|null Message d'erreur si la combinaison est dangereuse, null si elle est saine.
 */
function policyLockoutProblem(bool $forceUpdate, int $minVersionCode, ?int $maxPublished): ?string
{
    if (!$forceUpdate) {
        return null;
    }
    if ($minVersionCode < 1) {
        return 'Une mise à jour obligatoire exige une version minimale supérieure à 0.';
    }
    if ($maxPublished === null || $minVersionCode > $maxPublished) {
        return 'La version minimale exigée doit correspondre à une version déjà publiée '
            . "(plus haute version publiée : " . ($maxPublished ?? 'aucune') . ').';
    }
    return null;
}

/** @return array<string, mixed> */
function versionRowToArray(array $row): array
{
    return [
        'id' => (int) $row['id'],
        'versionName' => $row['version_name'],
        'versionCode' => (int) $row['version_code'],
        'status' => $row['status'],
        'releaseNotes' => $row['release_notes'],
        'updateUrl' => $row['update_url'],
        'publishedAt' => $row['published_at'] !== null ? (int) $row['published_at'] : null,
        'createdAt' => (int) $row['created_at'],
        'updatedAt' => (int) $row['updated_at'],
        'updatedBy' => $row['updated_by_label'] ?? null,
    ];
}

/** Requête de base : versions + e-mail de l'administrateur auteur de la dernière modification. */
function versionSelectSql(): string
{
    return 'SELECT v.*, u.email AS updated_by_label
            FROM android_versions v LEFT JOIN users u ON u.id = v.updated_by';
}

/**
 * Politique complète pour l'administration (contrairement à l'endpoint public, inclut l'auteur).
 *
 * @return array<string, mixed>
 */
function loadPolicyForAdmin(PDO $pdo): array
{
    $row = $pdo->query(
        'SELECT p.*, u.email AS updated_by_label FROM app_version_policy p
         LEFT JOIN users u ON u.id = p.updated_by WHERE p.id = 1 LIMIT 1'
    )->fetch();
    if ($row === false) {
        sendError('server_error', 'Politique de version non initialisée (migration 010).', 500);
    }
    return [
        'minVersionCode' => (int) $row['min_version_code'],
        'forceUpdate' => (bool) $row['force_update'],
        'updateUrl' => $row['update_url'],
        'blockUnversionedClients' => (bool) $row['block_unversioned_clients'],
        'maintenanceEnabled' => (bool) $row['maintenance_enabled'],
        'maintenanceMessage' => $row['maintenance_message'],
        'updatedAt' => (int) $row['updated_at'],
        'updatedBy' => $row['updated_by_label'],
    ];
}
