<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/version_policy.php';

/**
 * GET /api/app/version_policy.php[?versionCode=N]
 *
 * Endpoint PUBLIC (aucune authentification, volontairement) : l'app Android doit pouvoir obtenir
 * la politique de mise à jour même quand sa version est bloquée ou que la session a expiré — aucune
 * dépendance circulaire avec la connexion. Ne renvoie que le STRICT nécessaire à la décision
 * côté client ; jamais d'information d'administration (auteurs, historique, brouillons).
 *
 * `versionCode` (paramètre ou en-tête `X-App-Version-Code`) : permet au serveur de répondre
 * directement `updateRequired` / `updateAvailable`. Le client peut aussi comparer lui-même avec
 * `minVersionCode` (valeur entière officielle Android).
 *
 * Réponse 200 :
 *   { minVersionCode, minVersionName, latestVersionCode, latestVersionName, forceUpdate, updateRequired,
 *     updateAvailable, updateUrl, releaseNotes, maintenance: {enabled, message}, serverTime }
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    sendError('method_not_allowed', 'Cette route accepte uniquement GET.', 405);
}

$clientVersionCode = null;
if (isset($_GET['versionCode'])) {
    $raw = (string) $_GET['versionCode'];
    if (!ctype_digit($raw) || strlen($raw) > 9) {
        sendError('invalid_version_code', 'versionCode doit être un entier positif.', 400);
    }
    $clientVersionCode = (int) $raw;
} else {
    $clientVersionCode = readClientVersionCode();
}

try {
    $pdo = getDatabaseConnection();
    $policy = loadVersionPolicy($pdo);
    $latest = loadLatestPublishedVersion($pdo);
    $minVersionName = loadVersionNameByCode($pdo, $policy['minVersionCode']);
} catch (Throwable $e) {
    error_log('Arzikina API — echec lecture politique de version : ' . $e->getMessage());
    sendError('server_error', 'Politique de version indisponible, réessaie plus tard.', 500);
}

$status = evaluateClientVersion($policy, $latest, $clientVersionCode);

// La politique change en temps réel depuis la console : ne jamais la mettre en cache.
header('Cache-Control: no-store');

sendJson([
    'minVersionCode' => $policy['minVersionCode'],
    'minVersionName' => $minVersionName,
    'latestVersionCode' => $latest['versionCode'] ?? null,
    'latestVersionName' => $latest['versionName'] ?? null,
    'forceUpdate' => $policy['forceUpdate'],
    'updateRequired' => $status['updateRequired'],
    'updateAvailable' => $status['updateAvailable'],
    'updateUrl' => $policy['updateUrl'],
    'releaseNotes' => $latest['releaseNotes'] ?? null,
    'maintenance' => [
        'enabled' => $policy['maintenanceEnabled'],
        'message' => $policy['maintenanceMessage'],
    ],
    'serverTime' => (int) round(microtime(true) * 1000),
]);
