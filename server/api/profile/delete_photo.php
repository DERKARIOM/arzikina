<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * POST /api/profile/delete_photo.php (aucun corps requis)
 *
 * Retour à l'avatar par défaut pour l'utilisateur authentifié — voir la KDoc de tête de
 * `upload_photo.php` pour le raisonnement d'ensemble (stockage disque, versionnement via
 * `users.version`/`updated_at`, endpoint dédié hors du registre générique de synchronisation).
 *
 * IDEMPOTENT : appeler cet endpoint alors qu'il n'y a déjà aucune photo ne change rien (pas
 * d'erreur, pas d'incrément de version inutile) — un appareil hors ligne qui rejoue une suppression
 * déjà synchronisée par un autre appareil ne doit jamais produire d'effet de bord.
 *
 * Réponse : { "photoPath": null, "version": <int>, "updatedAt": <bigint millis> }.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);
$userId = $auth['userId'];

$stmt = $pdo->prepare('SELECT photo_path, version, updated_at FROM users WHERE id = :id LIMIT 1');
$stmt->execute(['id' => $userId]);
$user = $stmt->fetch();
if ($user === false) {
    sendError('user_not_found', 'Utilisateur introuvable.', 404);
}

if ($user['photo_path'] === null) {
    sendJson([
        'photoPath' => null,
        'version' => (int) $user['version'],
        'updatedAt' => (int) $user['updated_at'],
    ]);
}

$oldPhotoPath = $user['photo_path'];
$newVersion = ((int) $user['version']) + 1;
$nowMillis = (int) round(microtime(true) * 1000);

$update = $pdo->prepare(
    'UPDATE users SET photo_path = NULL, version = :version, updated_at = :updated_at WHERE id = :id'
);
$update->execute([
    'version' => $newVersion,
    'updated_at' => $nowMillis,
    'id' => $userId,
]);

$oldAbsolutePath = __DIR__ . '/../../' . $oldPhotoPath;
if (is_file($oldAbsolutePath)) {
    @unlink($oldAbsolutePath);
}

sendJson([
    'photoPath' => null,
    'version' => $newVersion,
    'updatedAt' => $nowMillis,
]);
