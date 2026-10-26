<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/product_image.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * GET /api/shop/get_product_image.php?name={uuid}.jpg
 *
 * Renvoie la photo, UNIQUEMENT depuis le dossier de l'utilisateur authentifié : un nom appartenant à un autre
 * utilisateur est indistinguable d'un nom inexistant (404). Réponse binaire, cache privé longue durée : les
 * noms sont immuables.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    sendError('method_not_allowed', 'Cette route accepte uniquement GET.', 405);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);

$name = (string) ($_GET['name'] ?? '');
if (!isValidProductImageName($name)) {
    sendError('invalid_image_name', 'Nom de photo invalide.', 400);
}

$directory = productImageDirectory($auth['userId']);
$path = $directory === null ? '' : $directory . '/' . $name;
if ($directory === null || !is_file($path)) {
    sendError('image_not_found', 'Photo introuvable.', 404);
}

header('Content-Type: image/jpeg');
header('Content-Length: ' . (string) filesize($path));
header('Cache-Control: private, max-age=31536000, immutable');
header('X-Content-Type-Options: nosniff');
readfile($path);
exit;
