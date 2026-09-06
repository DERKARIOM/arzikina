<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/** 5 Mo : largement suffisant pour un avatar déjà recadré/compressé côté client (quelques dizaines
 *  à centaines de Ko en pratique, voir `ProfileFragment.PROFILE_PHOTO_JPEG_QUALITY`) — une valeur
 *  bien plus haute que nécessaire n'aurait pour seul effet que de retarder la détection d'un envoi
 *  anormal. Déclarée en tête de fichier : un `const` top-level PHP est une instruction exécutée
 *  dans l'ordre normal (pas de "hoisting" comme les fonctions), elle doit donc précéder son usage
 *  ci-dessous. */
const MAX_PHOTO_SIZE_BYTES = 5 * 1_000_000;

/**
 * POST /api/profile/upload_photo.php (multipart/form-data, champ `photo`)
 *
 * Enregistre/remplace la photo de profil de l'utilisateur authentifié — cahier des charges
 * "Gestion de la photo de profil", sections "Synchronisation avec le serveur"/"Stockage serveur".
 *
 * Volontairement HORS du registre générique de synchronisation (`api/config/entity_sync_configs.php`,
 * `api/sync/push.php`/`pull.php`) : ce registre transporte du JSON, jamais de fichier binaire — voir
 * le commentaire de tête de `entity_sync_configs.php`. Le CLIENT (Android/Web) envoie ici l'image
 * DÉJÀ recadrée/redimensionnée/compressée (voir `ProfileFragment.launchCrop` côté Android) : cet
 * endpoint ne fait AUCUN traitement d'image, uniquement stockage + mise à jour des métadonnées.
 *
 * Stockage : fichier sur le DISQUE du serveur (`avatars/{user_id}/{version}.jpg`, relatif à la
 * racine web) — jamais en base MySQL (voir cahier des charges, "éviter de stocker une grosse image
 * en Base64"). Seul le CHEMIN relatif est écrit dans `users.photo_path` (même convention que
 * `receipts.file_path`, voir 001_initial_schema.sql).
 *
 * Versionnement : réutilise `users.version`/`users.updated_at` (déjà présents sur la table, jamais
 * écrits ailleurs à ce jour) — incrémentés à chaque upload accepté. Le NOM du fichier physique
 * intègre directement ce numéro de version (`{version}.jpg`) : un appareil qui a mis en cache
 * l'ancienne URL ne récupère donc jamais accidentellement la nouvelle image sous le même nom (voir
 * cahier des charges "Gestion du cache", "utiliser un mécanisme de versionnement... pour éviter les
 * problèmes de cache").
 *
 * Anti-doublon/fichier orphelin : l'ANCIEN fichier n'est supprimé qu'APRÈS que la ligne `users` a
 * été mise à jour avec succès (jamais l'inverse) — même principe que
 * `ProfilePhotoFileStorage`/`ProfilePhotoRepositoryImpl` côté Android. Une seule ligne par
 * utilisateur (`users.photo_path`), jamais un historique : aucune synchronisation ne peut créer
 * plusieurs photos pour le même profil.
 *
 * Réponse : { "photoPath": "avatars/...jpg", "version": <int>, "updatedAt": <bigint millis> }.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);
$userId = $auth['userId'];

if (!isset($_FILES['photo']) || !is_array($_FILES['photo'])) {
    sendError('missing_photo', 'Champ multipart "photo" manquant.', 400);
}

$uploadError = $_FILES['photo']['error'] ?? UPLOAD_ERR_NO_FILE;
if ($uploadError !== UPLOAD_ERR_OK) {
    // UPLOAD_ERR_INI_SIZE/UPLOAD_ERR_FORM_SIZE : fichier au-delà de upload_max_filesize/post_max_size
    // (php.ini, hors du contrôle de ce script) — message générique, la valeur exacte du serveur
    // n'a pas besoin de fuiter au client.
    sendError('upload_failed', 'Échec de l\'envoi du fichier (trop volumineux ou envoi interrompu).', 400);
}

$tmpPath = $_FILES['photo']['tmp_name'];
$fileSize = (int) ($_FILES['photo']['size'] ?? 0);

if ($fileSize <= 0 || $fileSize > MAX_PHOTO_SIZE_BYTES) {
    sendError('invalid_photo_size', 'La photo doit peser au maximum ' . (MAX_PHOTO_SIZE_BYTES / 1_000_000) . ' Mo.', 400);
}

// Vérifie le CONTENU réel (pas seulement l'extension/le Content-Type annoncé par le client, jamais
// fiable) — voir cahier des charges section sécurité, même prudence que pour l'authentification.
// substr(...) === 'image/' plutôt que str_starts_with() (PHP 8.0+, absent ailleurs dans ce projet —
// utiliser une fonction récente ici aurait cassé cet endpoint sur un hébergement encore en PHP 7.x).
$mimeType = @mime_content_type($tmpPath) ?: '';
if (substr($mimeType, 0, 6) !== 'image/') {
    sendError('invalid_photo_type', 'Le fichier envoyé n\'est pas une image valide.', 400);
}

$stmt = $pdo->prepare('SELECT photo_path, version FROM users WHERE id = :id LIMIT 1');
$stmt->execute(['id' => $userId]);
$user = $stmt->fetch();
if ($user === false) {
    sendError('user_not_found', 'Utilisateur introuvable.', 404);
}

$oldPhotoPath = $user['photo_path'];
$newVersion = ((int) $user['version']) + 1;
$nowMillis = (int) round(microtime(true) * 1000);

$avatarsDirectory = __DIR__ . '/../../avatars/' . $userId;
if (!is_dir($avatarsDirectory) && !mkdir($avatarsDirectory, 0755, true) && !is_dir($avatarsDirectory)) {
    error_log("Arzikina API — echec de creation du dossier avatars pour l'utilisateur {$userId}.");
    sendError('storage_error', 'Impossible d\'enregistrer la photo.', 500);
}

$relativePath = "avatars/{$userId}/{$newVersion}.jpg";
$absolutePath = __DIR__ . '/../../' . $relativePath;

if (!move_uploaded_file($tmpPath, $absolutePath)) {
    error_log("Arzikina API — echec move_uploaded_file pour l'utilisateur {$userId}.");
    sendError('storage_error', 'Impossible d\'enregistrer la photo.', 500);
}

$update = $pdo->prepare(
    'UPDATE users SET photo_path = :photo_path, version = :version, updated_at = :updated_at WHERE id = :id'
);
$update->execute([
    'photo_path' => $relativePath,
    'version' => $newVersion,
    'updated_at' => $nowMillis,
    'id' => $userId,
]);

// Nettoyage de l'ancien fichier — APRÈS le UPDATE réussi ci-dessus (voir la KDoc de tête, "jamais
// l'inverse"). Un ancien chemin égal au nouveau ne devrait jamais arriver (nom basé sur la version,
// toujours strictement croissante) — le `!==` reste une garde défensive, comme côté Android.
if ($oldPhotoPath !== null && $oldPhotoPath !== $relativePath) {
    $oldAbsolutePath = __DIR__ . '/../../' . $oldPhotoPath;
    if (is_file($oldAbsolutePath)) {
        @unlink($oldAbsolutePath);
    }
}

sendJson([
    'photoPath' => $relativePath,
    'version' => $newVersion,
    'updatedAt' => $nowMillis,
]);
