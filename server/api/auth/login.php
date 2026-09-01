<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';

/**
 * POST /api/auth/login.php
 *
 * Corps attendu (JSON) :
 *   { "identifier": "<username ou email>", "password": "...", "deviceId": "...", "deviceLabel": "..." }
 *
 * Émet un token de session (voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, sections 6.1 et 12) : le
 * mot de passe RÉEL n'est envoyé qu'ICI, une seule fois par connexion — toute synchronisation
 * suivante utilise le token retourné, jamais le mot de passe à nouveau (voir `middleware/auth_middleware.php`).
 *
 * Voir `api/auth/register.php` (étape D du chantier "audit auth + sync + doublons") pour la
 * création de compte — ce fichier-ci ne fait plus que l'authentification d'un compte déjà existant.
 *
 * Sécurité :
 * - Requête préparée (protection injection SQL) avec DEUX espaces réservés distincts pour le même
 *   identifiant (`:identifier_username` / `:identifier_email`) — certains pilotes MySQL en
 *   requêtes préparées NATIVES (voir `PDO::ATTR_EMULATE_PREPARES => false`) n'acceptent pas de
 *   réutiliser un même paramètre nommé deux fois dans la même requête.
 * - Message d'erreur IDENTIQUE que l'identifiant n'existe pas OU que le mot de passe soit faux —
 *   empêche un attaquant de deviner quels comptes existent (anti énumération).
 * - `password_verify()` (Argon2id/bcrypt selon la configuration PHP), jamais de comparaison directe
 *   de chaînes.
 * - Délai fixe (`usleep`) appliqué dans les deux branches (trouvé/pas trouvé) : atténue une attaque
 *   par mesure de temps de réponse, sans prétendre l'éliminer. Un vrai verrou par IP/compte
 *   (compteur d'échecs, table dédiée) reste À FAIRE dans une étape ultérieure dédiée à la sécurité
 *   de l'authentification — signalé ici explicitement plutôt que silencieusement omis.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    sendError('invalid_body', 'Corps JSON invalide.', 400);
}

$identifier = trim((string) ($body['identifier'] ?? ''));
$password = (string) ($body['password'] ?? '');
$deviceId = isset($body['deviceId']) ? (string) $body['deviceId'] : null;
$deviceLabel = isset($body['deviceLabel']) ? (string) $body['deviceLabel'] : null;

if ($identifier === '' || $password === '') {
    sendError('invalid_body', 'identifier et password sont obligatoires.', 400);
}

$pdo = getDatabaseConnection();

$stmt = $pdo->prepare(
    'SELECT id, password_hash, full_name FROM users
     WHERE (username = :identifier_username OR email = :identifier_email) AND deleted_at IS NULL
     LIMIT 1'
);
$stmt->execute([
    'identifier_username' => $identifier,
    'identifier_email' => $identifier,
]);
$user = $stmt->fetch();

// Délai fixe AVANT de révéler le résultat — voir doc de tête (atténuation timing-based).
usleep(150000);

if ($user === false || !password_verify($password, $user['password_hash'])) {
    sendError('invalid_credentials', 'Identifiant ou mot de passe incorrect.', 401);
}

$rawToken = bin2hex(random_bytes(32));
$tokenHash = hash('sha256', $rawToken);
$nowMillis = (int) round(microtime(true) * 1000);
$expiresAtMillis = $nowMillis + (TOKEN_EXPIRY_SECONDS * 1000);

$insert = $pdo->prepare(
    'INSERT INTO auth_tokens (user_id, token_hash, device_id, device_label, created_at, expires_at)
     VALUES (:user_id, :token_hash, :device_id, :device_label, :created_at, :expires_at)'
);
$insert->execute([
    'user_id' => $user['id'],
    'token_hash' => $tokenHash,
    'device_id' => $deviceId,
    'device_label' => $deviceLabel,
    'created_at' => $nowMillis,
    'expires_at' => $expiresAtMillis,
]);

sendJson([
    'token' => $rawToken,
    'userId' => $user['id'],
    'expiresAt' => $expiresAtMillis,
    // Nom complet réel (colonne users.full_name) — même source que authRepository.observeUser()
    // côté Android, exposée ici plutôt que dupliquée dans une autre table (voir user_preferences,
    // qui reste volontairement limité aux préférences d'affichage : thème/devise/verrou).
    // `?? ''` : garantit le contrat d'API (LoginResponseDto.fullName est non-nullable côté Android,
    // register.php le garantit toujours non vide à l'inscription — mais un compte plus ancien,
    // créé avant l'existence de cette colonne ou directement en SQL, peut avoir NULL en base ; sans
    // ce repli, kotlinx.serialization plante au décodage et la connexion échoue avec une erreur
    // générique trompeuse côté mobile).
    'fullName' => $user['full_name'] ?? '',
]);
