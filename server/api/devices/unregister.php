<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/uuid.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';
require_once __DIR__ . '/../push/UserDeviceRepository.php';

/**
 * POST /api/devices/unregister.php — authentifié (Bearer).
 *
 * Corps attendu (JSON) : { "installationId": "<UUID>" }
 *
 * Coupe les notifications push de CETTE installation pour le compte connecté, sans fermer la
 * session (ex. futur interrupteur « Notifications » dans l'application). Pour une déconnexion
 * complète, l'application appelle `api/auth/logout.php`, qui révoque aussi les appareils.
 *
 * Réponse identique que l'installation existe ou non (`{"unregistered": true}`) : un client ne peut
 * pas sonder les installations des autres comptes ; la requête ne touche de toute façon que les
 * lignes du compte authentifié.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    sendError('invalid_body', 'Corps JSON invalide.', 400);
}

$installationId = strtolower(trim((string) ($body['installationId'] ?? '')));
if (!isValidUuid($installationId)) {
    sendError('invalid_installation_id', 'installationId doit être un UUID.', 400);
}

try {
    (new UserDeviceRepository($pdo))->revokeInstallation(
        $auth['userId'],
        $installationId,
        (int) round(microtime(true) * 1000),
    );
    sendJson(['unregistered' => true]);
} catch (Throwable $e) {
    error_log('Arzikina API — echec desinscription appareil : ' . $e->getMessage());
    sendError('server_error', 'La désinscription a échoué, réessaie plus tard.', 500);
}
