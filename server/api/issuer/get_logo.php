<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/issuer_logo.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * GET /api/issuer/get_logo.php?name={uuid}.{png|jpg}
 *
 * Renvoie l'image du logo, UNIQUEMENT depuis le dossier de l'utilisateur authentifié (voir
 * `utils/issuer_logo.php`) : un nom appartenant à un autre utilisateur est indistinguable d'un nom
 * inexistant (404 dans les deux cas). Réponse binaire, mise en cache privée longue durée : les noms
 * sont immuables.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    sendError('method_not_allowed', 'Cette route accepte uniquement GET.', 405);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);

$name = (string) ($_GET['name'] ?? '');
if (!isValidIssuerLogoName($name)) {
    sendError('invalid_logo_name', 'Nom de logo invalide.', 400);
}

$directory = issuerLogoDirectory($auth['userId']);
$path = $directory === null ? '' : $directory . '/' . $name;
if ($directory === null || !is_file($path)) {
    sendError('logo_not_found', 'Logo introuvable.', 404);
}

header('Content-Type: ' . (substr($name, -4) === '.png' ? 'image/png' : 'image/jpeg'));
header('Content-Length: ' . (string) filesize($path));
header('Cache-Control: private, max-age=31536000, immutable');
header('X-Content-Type-Options: nosniff');
readfile($path);
exit;
