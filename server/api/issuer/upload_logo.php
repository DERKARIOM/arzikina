<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/issuer_logo.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * POST /api/issuer/upload_logo.php (multipart/form-data, champs `logo` = fichier, `name` = nom)
 *
 * Envoie le logo d'émetteur de reçus de l'utilisateur authentifié. Hors du registre générique de
 * synchronisation (JSON uniquement), même raisonnement que `profile/upload_photo.php` ; stockage
 * PRIVÉ (voir `utils/issuer_logo.php`).
 *
 * - `name` : nom de fichier choisi par le client (UUID + `.png`/`.jpg`), validé strictement. Doit
 *   correspondre au type RÉEL du fichier (octets, pas l'extension).
 * - Idempotent : un nom déjà présent n'est jamais écrasé (les noms sont immuables) → `200` avec
 *   `alreadyExisted: true`. Un renvoi après un timeout réseau est donc sans effet.
 * - Écriture atomique (fichier temporaire puis `rename`).
 *
 * Réponse : { "name": "...", "size": <octets>, "alreadyExisted": <bool> }.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);
$userId = $auth['userId'];

$name = (string) ($_POST['name'] ?? '');
if (!isValidIssuerLogoName($name)) {
    sendError('invalid_logo_name', 'Nom de logo invalide.', 400);
}

if (!isset($_FILES['logo']) || !is_array($_FILES['logo'])) {
    sendError('missing_logo', 'Champ multipart "logo" manquant.', 400);
}
if (($_FILES['logo']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
    sendError('upload_failed', 'Échec de l\'envoi du fichier (trop volumineux ou envoi interrompu).', 400);
}

$tmpPath = (string) $_FILES['logo']['tmp_name'];
$size = (int) ($_FILES['logo']['size'] ?? 0);
if ($size <= 0 || $size > ISSUER_LOGO_MAX_BYTES) {
    sendError('invalid_logo_size', 'Le logo doit peser au maximum ' . (ISSUER_LOGO_MAX_BYTES / 1_000_000) . ' Mo.', 400);
}

$detected = detectIssuerLogoExtension($tmpPath);
if ($detected === null || $detected !== pathinfo($name, PATHINFO_EXTENSION)) {
    sendError('invalid_logo_type', 'Le logo doit être une image PNG ou JPEG, avec une extension correspondante.', 400);
}

$directory = issuerLogoDirectory($userId);
if ($directory === null) {
    sendError('storage_error', 'Impossible d\'enregistrer le logo.', 500);
}
if (!is_dir($directory) && !mkdir($directory, 0755, true) && !is_dir($directory)) {
    error_log("Arzikina API — echec de creation du dossier de logos pour l'utilisateur {$userId}.");
    sendError('storage_error', 'Impossible d\'enregistrer le logo.', 500);
}

$target = $directory . '/' . $name;
if (is_file($target)) {
    sendJson(['name' => $name, 'size' => (int) filesize($target), 'alreadyExisted' => true]);
}

if (count(glob($directory . '/*') ?: []) >= ISSUER_LOGO_MAX_FILES_PER_USER) {
    sendError('logo_quota_exceeded', 'Nombre maximum de logos atteint.', 409);
}

$temp = $target . '.tmp';
$moved = is_uploaded_file($tmpPath) ? move_uploaded_file($tmpPath, $temp) : @copy($tmpPath, $temp);
if (!$moved || !@rename($temp, $target)) {
    @unlink($temp);
    error_log("Arzikina API — echec d'ecriture du logo pour l'utilisateur {$userId}.");
    sendError('storage_error', 'Impossible d\'enregistrer le logo.', 500);
}

sendJson(['name' => $name, 'size' => $size, 'alreadyExisted' => false]);
