<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/google_token_verifier.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * POST /api/auth/link_google_account.php - authentifie (Bearer).
 *
 * Corps attendu (JSON) : { "idToken": "<jeton d'identite Google>" }
 *
 * Mecanisme de liaison securisee du cahier des charges (Cas C) : un utilisateur deja connecte par
 * mot de passe classique (Bearer valide, donc identite deja prouvee par `requireAuthenticatedUser`)
 * associe son compte Google a son compte Arzikina existant - AUCUNE fusion automatique sur la
 * seule base de l'e-mail, voir `google_login.php` qui renvoie `account_exists_link_required`
 * plutot que de lier quoi que ce soit sans cette etape explicite.
 *
 * `google_id` reste la seule information ecrite : `email`/`full_name` du compte Arzikina ne sont
 * JAMAIS remplaces par les valeurs Google (l'utilisateur a pu les modifier depuis, cahier des
 * charges "ne modifie pas les fonctionnalites... sans necessite").
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);
$userId = $auth['userId'];

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    sendError('invalid_body', 'Corps JSON invalide.', 400);
}

$idToken = trim((string) ($body['idToken'] ?? ''));
if ($idToken === '') {
    sendError('invalid_body', 'idToken est obligatoire.', 400);
}

try {
    $claims = verifyGoogleIdToken($idToken, [GOOGLE_CLIENT_ID_ANDROID, GOOGLE_CLIENT_ID_WEB]);
} catch (GoogleTokenVerificationException $e) {
    sendError($e->errorCode, 'Authentification Google impossible, reessaie.', 401);
}

if (!$claims['emailVerified'] || $claims['email'] === '') {
    sendError('google_email_not_verified', "L'adresse e-mail Google n'est pas verifiee.", 401);
}

$googleId = $claims['sub'];

try {
    // Un meme compte Google deja lie a un AUTRE compte Arzikina ne doit jamais etre rattache une
    // seconde fois ailleurs - la contrainte UNIQUE sur `users.google_id` protege deja ce cas en
    // base, cette verification offre seulement un message clair plutot qu'une erreur SQL brute.
    $stmt = $pdo->prepare('SELECT id FROM users WHERE google_id = :google_id AND deleted_at IS NULL LIMIT 1');
    $stmt->execute(['google_id' => $googleId]);
    $existing = $stmt->fetch();

    if ($existing !== false && $existing['id'] !== $userId) {
        sendError('google_account_already_linked', 'Ce compte Google est deja associe a un autre compte Arzikina.', 409);
    }

    $pdo->prepare('UPDATE users SET google_id = :google_id WHERE id = :id')
        ->execute(['google_id' => $googleId, 'id' => $userId]);

    sendJson(['linked' => true]);
} catch (PDOException $e) {
    error_log('Arzikina API - echec liaison compte Google (PDO) : ' . $e->getMessage());
    sendError('server_error', 'La liaison du compte Google a echoue, reessaie plus tard.', 500);
} catch (Throwable $e) {
    error_log('Arzikina API - echec liaison compte Google : ' . $e->getMessage());
    sendError('server_error', 'La liaison du compte Google a echoue, reessaie plus tard.', 500);
}
