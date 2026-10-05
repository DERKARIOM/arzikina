<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/uuid.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';
require_once __DIR__ . '/../push/UserDeviceRepository.php';

/**
 * POST /api/devices/register.php — authentifié (Bearer).
 *
 * Corps attendu (JSON) :
 *   { "token": "<token FCM>", "installationId": "<UUID>", "platform": "android",
 *     "appVersion": "1.4.0", "locale": "fr" }
 *
 * Rattache cette installation de l'application au compte ET à la session du Bearer. Appelé par
 * l'application après la connexion, à chaque nouveau token FCM (`onNewToken`) et périodiquement
 * (mise à jour de `last_seen_at`). Idempotent : appeler deux fois avec les mêmes valeurs ne crée
 * rien de plus.
 *
 * Sécurité :
 * - l'utilisateur vient UNIQUEMENT du middleware (jamais du corps) ;
 * - `installationId` est un UUID aléatoire de l'application, pas un identifiant matériel ;
 * - la réponse ne renvoie jamais de token ni d'information sur d'autres appareils.
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

$fcmToken = trim((string) ($body['token'] ?? ''));
$installationId = strtolower(trim((string) ($body['installationId'] ?? '')));
$platform = strtolower(trim((string) ($body['platform'] ?? '')));
$appVersion = isset($body['appVersion']) ? trim((string) $body['appVersion']) : null;
$locale = isset($body['locale']) ? trim((string) $body['locale']) : null;

// Token FCM : opaque, ASCII ; caractères observés [A-Za-z0-9:_-], `.` toléré par prudence.
if (preg_match('/^[A-Za-z0-9:_\-.]{20,512}$/', $fcmToken) !== 1) {
    sendError('invalid_token_format', 'token FCM absent ou invalide.', 400);
}
if (!isValidUuid($installationId)) {
    sendError('invalid_installation_id', 'installationId doit être un UUID.', 400);
}
if (!in_array($platform, UserDeviceRepository::PLATFORMS, true)) {
    sendError('invalid_platform', 'platform doit valoir android, ios ou web.', 400);
}
if ($appVersion !== null && preg_match('/^[A-Za-z0-9 ._()+-]{1,32}$/', $appVersion) !== 1) {
    sendError('invalid_app_version', 'appVersion invalide (32 caractères maximum).', 400);
}
if ($locale !== null && preg_match('/^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8}){0,2}$/', $locale) !== 1) {
    sendError('invalid_locale', 'locale invalide (ex. fr, en, fr-NE).', 400);
}

try {
    (new UserDeviceRepository($pdo))->register(
        $auth['userId'],
        $auth['tokenId'],
        $installationId,
        $fcmToken,
        $platform,
        $appVersion === '' ? null : $appVersion,
        $locale === '' ? null : $locale,
        (int) round(microtime(true) * 1000),
    );
    sendJson(['registered' => true]);
} catch (Throwable $e) {
    error_log('Arzikina API — echec enregistrement appareil : ' . $e->getMessage());
    sendError('server_error', "L'enregistrement de l'appareil a échoué, réessaie plus tard.", 500);
}
