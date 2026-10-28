<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/usage_profile.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * POST /api/auth/set_usage_mode.php          (Authorization: Bearer <token>)
 *
 * Corps : { "usageMode": "SHOP" | "PERSONAL" | "BOTH" }
 *
 * Enregistre le mode d'utilisation choisi par un utilisateur qui ne l'a pas encore fait : typiquement un
 * NOUVEAU compte créé via Google (qui n'a pas pu choisir avant l'authentification). Voir
 * database/migrations/013_users_usage_mode.sql.
 *
 * IDEMPOTENT et sans conflit entre appareils : le mode n'est écrit que s'il est encore NULL
 * (`WHERE usage_mode IS NULL`). Si un choix existe déjà (double appui, nouvelle tentative, autre appareil plus
 * rapide), il est conservé et RENVOYÉ : le serveur reste la source de vérité et le client s'aligne dessus.
 *
 * Réponse : { "usageMode": "...", "hasAccounts": bool } (voir utils/usage_profile.php) — `hasAccounts` permet au
 * client de ne créer les comptes par défaut que si le serveur n'en détient aucun.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    sendError('invalid_body', 'Corps JSON invalide.', 400);
}

$usageMode = trim((string) ($body['usageMode'] ?? ''));
if (!in_array($usageMode, ['SHOP', 'PERSONAL', 'BOTH'], true)) {
    sendError('invalid_usage_mode', "Mode d'utilisation inconnu.", 400);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);
$userId = $auth['userId'];

try {
    $update = $pdo->prepare('UPDATE users SET usage_mode = :usage_mode WHERE id = :id AND usage_mode IS NULL AND deleted_at IS NULL');
    $update->execute(['usage_mode' => $usageMode, 'id' => $userId]);

    $profile = fetchUsageProfile($pdo, $userId);
    if ($profile['usageMode'] === null) {
        // La colonne existe mais la valeur reste illisible : ne jamais répondre un succès trompeur.
        sendError('server_error', "Le choix n'a pas pu être enregistré, réessaie plus tard.", 500);
    }

    sendJson(['usageMode' => $profile['usageMode'], 'hasAccounts' => $profile['hasAccounts']]);
} catch (Throwable $e) {
    error_log('Arzikina API — echec set_usage_mode : ' . $e->getMessage());
    sendError('server_error', "Le choix n'a pas pu être enregistré, réessaie plus tard.", 500);
}
