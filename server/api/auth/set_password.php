<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/google_token_verifier.php';
require_once __DIR__ . '/../utils/password_policy.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * POST /api/auth/set_password.php — authentifié (Bearer).
 *
 * Corps attendu (JSON) :
 *   { "newPassword": "...", "currentPassword": "..." }   -- compte qui connaît son mot de passe
 *   { "newPassword": "...", "idToken": "<jeton Google>" } -- compte lié à Google (y compris créé via Google)
 *
 * Définit ou change le mot de passe du compte authentifié. Un compte créé via Google reçoit à
 * l'inscription un mot de passe aléatoire que personne ne connaît (voir `google_login.php`) : il ne peut donc
 * pas fournir de « mot de passe actuel ». Il prouve alors son identité avec un jeton d'identité Google FRAIS
 * dont le `sub` doit être celui de `users.google_id`. Une session volée seule (Bearer) ne suffit jamais à
 * changer le mot de passe : il faut toujours une preuve en plus (même principe que `delete_account.php`).
 *
 * Preuve : `currentPassword` s'il est fourni (alors vérifié, et rien d'autre n'est accepté à sa place),
 * sinon `idToken`. Aucune des deux : 400.
 *
 * Après le changement, TOUTES les autres sessions du compte sont révoquées (`auth_tokens.revoked_at`) : un
 * jeton qui traînerait ailleurs cesse de fonctionner ; la session courante reste valide.
 *
 * Sécurité : `password_hash()` / `password_verify()` ; message d'erreur unique pour un mot de passe actuel
 * faux ; délai fixe (`usleep`) comme `login.php` (atténue l'essai répété avec un jeton volé, sans prétendre
 * l'éliminer — le verrou par compte reste à faire, voir la doc de `login.php`). Le mot de passe n'est jamais
 * journalisé.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    sendError('invalid_body', 'Corps JSON invalide.', 400);
}

$newPassword = (string) ($body['newPassword'] ?? '');
$currentPassword = (string) ($body['currentPassword'] ?? '');
$idToken = trim((string) ($body['idToken'] ?? ''));

if ($newPassword === '') {
    sendError('invalid_body', 'newPassword est obligatoire.', 400);
}
if ($currentPassword === '' && $idToken === '') {
    sendError('invalid_body', 'currentPassword ou idToken est obligatoire.', 400);
}

$policyError = passwordPolicyError($newPassword);
if ($policyError === 'password_too_short') {
    sendError($policyError, 'Le mot de passe doit faire au moins ' . PASSWORD_MIN_LENGTH . ' caractères.', 400);
}
if ($policyError === 'password_too_long') {
    sendError($policyError, 'Le mot de passe ne doit pas dépasser ' . PASSWORD_MAX_LENGTH . ' caractères.', 400);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);
$userId = $auth['userId'];
$tokenId = $auth['tokenId'];

try {
    $stmt = $pdo->prepare('SELECT password_hash, google_id FROM users WHERE id = :id AND deleted_at IS NULL LIMIT 1');
    $stmt->execute(['id' => $userId]);
    $user = $stmt->fetch();
    if ($user === false) {
        sendError('invalid_token', 'Token invalide, révoqué ou expiré.', 401);
    }

    // Délai fixe AVANT de révéler le résultat de la preuve (voir doc de tête).
    usleep(150000);

    if ($currentPassword !== '') {
        if (!password_verify($currentPassword, $user['password_hash'])) {
            sendError('invalid_credentials', 'Mot de passe actuel incorrect.', 401);
        }
    } else {
        try {
            $claims = verifyGoogleIdToken($idToken, [GOOGLE_CLIENT_ID_ANDROID, GOOGLE_CLIENT_ID_WEB]);
        } catch (GoogleTokenVerificationException $e) {
            sendError($e->errorCode, 'Authentification Google impossible, réessaie.', 401);
        }
        if (!$claims['emailVerified'] || $claims['email'] === '') {
            sendError('google_email_not_verified', "L'adresse e-mail Google n'est pas vérifiée.", 401);
        }
        // Le compte doit être LIÉ à ce Google précis : un autre compte Google valide ne prouve rien.
        if ($user['google_id'] === null || !hash_equals((string) $user['google_id'], (string) $claims['sub'])) {
            sendError('google_identity_mismatch', "Ce compte Google n'est pas celui associé à ton compte Arzikina.", 403);
        }
    }

    $nowMillis = (int) round(microtime(true) * 1000);
    $newHash = password_hash($newPassword, PASSWORD_DEFAULT);

    $pdo->beginTransaction();
    $pdo->prepare('UPDATE users SET password_hash = :hash, updated_at = :now WHERE id = :id')
        ->execute(['hash' => $newHash, 'now' => $nowMillis, 'id' => $userId]);
    $revoke = $pdo->prepare(
        'UPDATE auth_tokens SET revoked_at = :now WHERE user_id = :user_id AND id <> :token_id AND revoked_at IS NULL'
    );
    $revoke->execute(['now' => $nowMillis, 'user_id' => $userId, 'token_id' => $tokenId]);
    $revokedSessions = $revoke->rowCount();
    $pdo->commit();

    sendJson(['updated' => true, 'revokedSessions' => $revokedSessions]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('Arzikina API — echec definition du mot de passe : ' . $e->getMessage());
    sendError('server_error', 'Le changement de mot de passe a échoué, réessaie plus tard.', 500);
}
