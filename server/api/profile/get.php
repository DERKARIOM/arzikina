<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * GET /api/profile/get.php
 *
 * État actuel de la photo de profil de l'utilisateur authentifié — lecture SEULE, aucune écriture.
 * Appelé périodiquement par chaque appareil (voir le futur `ProfilePhotoSyncWorker` côté Android,
 * même principe que `SyncWorker`) et par la version Web (`profil.tsx`) pour détecter qu'une photo
 * plus récente est disponible : comparer le `version` reçu à celui déjà connu localement suffit,
 * jamais besoin de retélécharger l'image si elle n'a pas changé (cahier des charges "Gestion du
 * cache").
 *
 * Volontairement minimal (seulement la photo, pas tout le profil) : voir la KDoc de tête de
 * `upload_photo.php`, aucune autre donnée de `users` n'est encore synchronisée à ce jour.
 *
 * Réponse : { "photoPath": "avatars/...jpg" | null, "version": <int>, "updatedAt": <bigint millis> }.
 * `photoPath` reste un chemin RELATIF à la racine web (même convention que `upload_photo.php`) —
 * chaque client préfixe lui-même son URL de base configurée (voir `RemoteConfig.BASE_URL` côté
 * Android), jamais une URL absolue codée en dur côté serveur.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    sendError('method_not_allowed', 'Cette route accepte uniquement GET.', 405);
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

sendJson([
    'photoPath' => $user['photo_path'],
    'version' => (int) $user['version'],
    'updatedAt' => (int) $user['updated_at'],
]);
