<?php

declare(strict_types=1);

require_once __DIR__ . '/../utils/json_response.php';

/**
 * Vérifie l'en-tête `Authorization: Bearer <token>` et retourne l'utilisateur authentifié.
 *
 * Point de sécurité CENTRAL (cahier des charges : "protection contre la falsification de user_id
 * / de token") : TOUT endpoint `api/sync/*` doit appeler cette fonction et utiliser UNIQUEMENT le
 * `userId` qu'elle retourne pour toute lecture/écriture — jamais une valeur envoyée par le client
 * dans le corps de la requête. Un `userId` présent dans le JSON envoyé par Android est donc
 * toujours ignoré pour l'autorisation, même s'il est présent (voir `api/sync/push.php`).
 *
 * Le token n'est JAMAIS stocké en clair côté serveur (voir
 * `database/migrations/001_initial_schema.sql`, table `auth_tokens`, colonne `token_hash`) — seul
 * son hash SHA-256 est comparé, même principe que le hachage d'un mot de passe.
 */
function requireAuthenticatedUser(PDO $pdo): array
{
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if ($header === '' && function_exists('apache_request_headers')) {
        // Certaines configurations Apache/PHP-FPM ne peuplent pas $_SERVER['HTTP_AUTHORIZATION']
        // (en-tête parfois filtrée avant d'atteindre PHP) — repli connu et courant.
        $headers = apache_request_headers();
        $header = $headers['Authorization'] ?? $headers['authorization'] ?? '';
    }

    if (!preg_match('/^Bearer\s+(\S+)$/i', trim($header), $matches)) {
        sendError('missing_token', 'En-tête Authorization manquant ou mal formé.', 401);
    }

    $token = $matches[1];
    $tokenHash = hash('sha256', $token);
    $nowMillis = (int) round(microtime(true) * 1000);

    $stmt = $pdo->prepare(
        'SELECT id, user_id, expires_at, revoked_at FROM auth_tokens WHERE token_hash = :token_hash LIMIT 1'
    );
    $stmt->execute(['token_hash' => $tokenHash]);
    $row = $stmt->fetch();

    if ($row === false || $row['revoked_at'] !== null || (int) $row['expires_at'] < $nowMillis) {
        sendError('invalid_token', 'Token invalide, révoqué ou expiré.', 401);
    }

    $update = $pdo->prepare('UPDATE auth_tokens SET last_used_at = :now WHERE id = :id');
    $update->execute(['now' => $nowMillis, 'id' => $row['id']]);

    return ['userId' => $row['user_id']];
}
