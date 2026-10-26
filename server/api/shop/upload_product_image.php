<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/product_image.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * POST /api/shop/upload_product_image.php (multipart/form-data, champs `image` = fichier, `name` = nom)
 *
 * Envoie la photo d'un produit de l'utilisateur authentifié. Hors du registre générique de synchronisation
 * (JSON uniquement), même raisonnement que `issuer/upload_logo.php` ; stockage PRIVÉ (voir
 * `utils/product_image.php`).
 *
 * - `name` : nom choisi par le client (UUID + `.jpg`), validé strictement ; le fichier doit être un vrai JPEG.
 * - Idempotent : un nom déjà présent n'est jamais écrasé → `200` avec `alreadyExisted: true`.
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
if (!isValidProductImageName($name)) {
    sendError('invalid_image_name', 'Nom de photo invalide.', 400);
}

if (!isset($_FILES['image']) || !is_array($_FILES['image'])) {
    sendError('missing_image', 'Champ multipart "image" manquant.', 400);
}
if (($_FILES['image']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
    sendError('upload_failed', 'Échec de l\'envoi du fichier (trop volumineux ou envoi interrompu).', 400);
}

$tmpPath = (string) $_FILES['image']['tmp_name'];
$size = (int) ($_FILES['image']['size'] ?? 0);
if ($size <= 0 || $size > PRODUCT_IMAGE_MAX_BYTES) {
    sendError('invalid_image_size', 'La photo doit peser au maximum ' . (PRODUCT_IMAGE_MAX_BYTES / 1_000_000) . ' Mo.', 400);
}
if (!isJpegFile($tmpPath)) {
    sendError('invalid_image_type', 'La photo doit être une image JPEG.', 400);
}

$directory = productImageDirectory($userId);
if ($directory === null) {
    sendError('storage_error', 'Impossible d\'enregistrer la photo.', 500);
}
if (!is_dir($directory) && !mkdir($directory, 0755, true) && !is_dir($directory)) {
    error_log("Arzikina API — echec de creation du dossier de photos produits pour l'utilisateur {$userId}.");
    sendError('storage_error', 'Impossible d\'enregistrer la photo.', 500);
}

$target = $directory . '/' . $name;
if (is_file($target)) {
    sendJson(['name' => $name, 'size' => (int) filesize($target), 'alreadyExisted' => true]);
}

if (count(glob($directory . '/*.jpg') ?: []) >= PRODUCT_IMAGE_MAX_FILES_PER_USER) {
    sendError('image_quota_exceeded', 'Nombre maximum de photos atteint.', 409);
}

$temp = $target . '.tmp';
$moved = is_uploaded_file($tmpPath) ? move_uploaded_file($tmpPath, $temp) : @copy($tmpPath, $temp);
if (!$moved || !@rename($temp, $target)) {
    @unlink($temp);
    error_log("Arzikina API — echec d'ecriture de la photo produit pour l'utilisateur {$userId}.");
    sendError('storage_error', 'Impossible d\'enregistrer la photo.', 500);
}

sendJson(['name' => $name, 'size' => $size, 'alreadyExisted' => false]);
