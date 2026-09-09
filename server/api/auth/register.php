<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/uuid.php';

/**
 * POST /api/auth/register.php
 *
 * Corps attendu (JSON) :
 *   {
 *     "fullName": "...", "username": "...", "email": "...", "phoneNumber": "..." (optionnel),
 *     "password": "...",
 *     "securityQuestion": "MOTHER_MAIDEN_NAME" (optionnel), "securityAnswer": "..." (optionnel),
 *     "deviceId": "...", "deviceLabel": "..." (optionnels, voir login.php)
 *   }
 *
 * Étape D du chantier "audit auth + sync + doublons" — jusqu'ici PAS d'endpoint d'inscription côté
 * serveur (voir l'ancienne doc de tête de login.php) : les comptes étaient créés à la main en SQL.
 * Introduit maintenant pour fusionner le login applicatif et le login de synchronisation en un
 * SEUL flux (voir `presentation/auth` côté Android, écran de connexion unique) : ce endpoint sert
 * aussi bien une inscription volontaire (nouvel utilisateur) qu'un rattachement AUTOMATIQUE d'un
 * compte local déjà existant sur un appareil (l'app appelle ce endpoint EN SILENCE avec le profil
 * déjà connu localement la première fois qu'un utilisateur pré-existant se connecte à la sync).
 *
 * `securityQuestion`/`securityAnswer` OPTIONNELS : un rattachement automatique silencieux ne peut
 * PAS fournir de réponse en clair (seul le hash local existe, voir `UserEntity.securityAnswerHash`,
 * jamais réversible). Quand ils sont absents, une réponse ALÉATOIRE et donc jamais devinable est
 * générée ici — ces deux colonnes restent NOT NULL par cohérence de schéma avec `users` (voir
 * database/migrations/001_initial_schema.sql), mais AUCUN endpoint serveur ne s'appuie encore
 * dessus pour une réinitialisation de mot de passe (ce mécanisme reste, à ce stade, 100% local à
 * chaque appareil — voir `resetPasswordWithSecurityAnswer` côté Android) : un compte enregistré
 * sans vraie question ici n'est donc, en pratique, pas moins sûr qu'un autre côté serveur.
 *
 * Émet directement un token de session (mêmes garanties que login.php) : évite un second
 * aller-retour réseau immédiat après l'inscription.
 *
 * Sécurité : mêmes principes que login.php — `password_hash()` (Argon2id/bcrypt), jamais de mot de
 * passe en clair stocké, messages d'erreur SANS détail interne. Validation de FORMAT ci-dessous
 * volontairement calquée sur `util/AuthValidator.kt` (côté Android) : mêmes seuils numériques, pour
 * qu'un mot de passe/nom d'utilisateur refusé par l'un le soit aussi par l'autre.
 */

const MIN_USERNAME_LENGTH = 3;
const MAX_USERNAME_LENGTH = 30;
const MIN_PASSWORD_LENGTH = 8;

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    sendError('invalid_body', 'Corps JSON invalide.', 400);
}

$fullName = trim((string) ($body['fullName'] ?? ''));
$username = trim((string) ($body['username'] ?? ''));
$email = trim((string) ($body['email'] ?? ''));
$phoneNumber = isset($body['phoneNumber']) && trim((string) $body['phoneNumber']) !== '' ? trim((string) $body['phoneNumber']) : null;
$password = (string) ($body['password'] ?? '');
$securityQuestion = isset($body['securityQuestion']) ? trim((string) $body['securityQuestion']) : null;
$securityAnswer = isset($body['securityAnswer']) ? (string) $body['securityAnswer'] : null;
$deviceId = isset($body['deviceId']) ? (string) $body['deviceId'] : null;
$deviceLabel = isset($body['deviceLabel']) ? (string) $body['deviceLabel'] : null;

if ($fullName === '' || $username === '' || $email === '' || $password === '') {
    sendError('invalid_body', 'fullName, username, email et password sont obligatoires.', 400);
}
if (mb_strlen($username) < MIN_USERNAME_LENGTH || mb_strlen($username) > MAX_USERNAME_LENGTH || !preg_match('/^[a-zA-Z0-9._]+$/', $username)) {
    sendError('invalid_username', "Le nom d'utilisateur doit faire 3 à 30 caractères (lettres, chiffres, point, underscore).", 400);
}
if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    sendError('invalid_email', "Format d'adresse e-mail invalide.", 400);
}
if (strlen($password) < MIN_PASSWORD_LENGTH) {
    sendError('password_too_short', 'Le mot de passe doit faire au moins 8 caractères.', 400);
}

// Question de sécurité : valeur fournie SI ET SEULEMENT SI une réponse en clair l'accompagne aussi
// — sinon on retombe sur le repli aléatoire décrit dans la doc de tête. Liste fermée ci-dessous
// VOLONTAIREMENT identique à `domain/model/SecurityQuestion.kt` (côté Android).
$knownSecurityQuestions = ['FIRST_PET_NAME', 'BIRTH_CITY', 'MOTHER_MAIDEN_NAME', 'FAVORITE_TEACHER', 'CHILDHOOD_BEST_FRIEND'];
if ($securityQuestion !== null && $securityAnswer !== null && $securityAnswer !== '' && in_array($securityQuestion, $knownSecurityQuestions, true)) {
    $storedSecurityQuestion = $securityQuestion;
    $storedSecurityAnswerHash = password_hash(mb_strtolower(trim($securityAnswer)), PASSWORD_DEFAULT);
} else {
    // Repli — voir doc de tête : réponse aléatoire, jamais devinable, aucune fonctionnalité
    // serveur n'en dépend actuellement.
    $storedSecurityQuestion = $knownSecurityQuestions[0];
    $storedSecurityAnswerHash = password_hash(bin2hex(random_bytes(32)), PASSWORD_DEFAULT);
}

$pdo = getDatabaseConnection();

// try/catch AUTOUR DE TOUT LE BLOC métier (pas seulement l'INSERT `users`) : même raisonnement que
// `sync/push.php`/`pull.php` (voir leur doc de tête) — une exception non attrapée ici (connexion DB
// perdue, requête sur une colonne pas encore déployée, etc.) laisserait échapper la page d'erreur
// HTML par défaut de PHP à la place du JSON attendu, que le Sync Engine / UnifiedAuthRepository
// Android ne savent pas parser. `PDOException` reste attrapée EN PREMIER (plus spécifique) pour
// conserver la distinction `username_taken`/`email_taken` (409, cas attendu et actionnable par
// l'utilisateur) d'une vraie panne serveur (`server_error`, 500, détail uniquement dans le journal).
try {
    $existing = $pdo->prepare('SELECT id FROM users WHERE username = :username AND deleted_at IS NULL LIMIT 1');
    $existing->execute(['username' => $username]);
    if ($existing->fetch() !== false) {
        sendError('username_taken', "Ce nom d'utilisateur est déjà pris.", 409);
    }

    $existing = $pdo->prepare('SELECT id FROM users WHERE email = :email AND deleted_at IS NULL LIMIT 1');
    $existing->execute(['email' => $email]);
    if ($existing->fetch() !== false) {
        sendError('email_taken', 'Cette adresse e-mail est déjà associée à un compte.', 409);
    }

    $userId = generateUuidV4();
    $nowMillis = (int) round(microtime(true) * 1000);
    $passwordHash = password_hash($password, PASSWORD_DEFAULT);

    $insertUser = $pdo->prepare(
        'INSERT INTO users
            (id, full_name, username, email, phone_number, password_hash, security_question, security_answer_hash, created_at, updated_at, deleted_at, version)
         VALUES
            (:id, :full_name, :username, :email, :phone_number, :password_hash, :security_question, :security_answer_hash, :created_at, :updated_at, NULL, 1)'
    );
    $insertUser->execute([
        'id' => $userId,
        'full_name' => $fullName,
        'username' => $username,
        'email' => $email,
        'phone_number' => $phoneNumber,
        'password_hash' => $passwordHash,
        'security_question' => $storedSecurityQuestion,
        'security_answer_hash' => $storedSecurityAnswerHash,
        'created_at' => $nowMillis,
        'updated_at' => $nowMillis,
    ]);

    // Émission immédiate d'un token de session — même logique que login.php (aucune duplication de
    // requête SQL : ce bloc est volontairement identique, une extraction commune serait prématurée
    // pour deux endpoints seulement, voir cahier des charges "règle de trois").
    $rawToken = bin2hex(random_bytes(32));
    $tokenHash = hash('sha256', $rawToken);
    $expiresAtMillis = $nowMillis + (TOKEN_EXPIRY_SECONDS * 1000);

    $insertToken = $pdo->prepare(
        'INSERT INTO auth_tokens (user_id, token_hash, device_id, device_label, created_at, expires_at)
         VALUES (:user_id, :token_hash, :device_id, :device_label, :created_at, :expires_at)'
    );
    $insertToken->execute([
        'user_id' => $userId,
        'token_hash' => $tokenHash,
        'device_id' => $deviceId,
        'device_label' => $deviceLabel,
        'created_at' => $nowMillis,
        'expires_at' => $expiresAtMillis,
    ]);

    sendJson([
        'token' => $rawToken,
        'userId' => $userId,
        'expiresAt' => $expiresAtMillis,
        'fullName' => $fullName,
    ], 201);
} catch (PDOException $e) {
    // Course rarissime entre la vérification d'unicité ci-dessus et l'insertion (voir
    // AuthRepositoryImpl.register côté Android, même raisonnement) : les index UNIQUE de `users`
    // tranchent en dernier recours. Code MySQL 1062 = entrée dupliquée.
    if ((int) $e->errorInfo[1] === 1062) {
        sendError('email_taken', 'Ce nom d\'utilisateur ou cette adresse e-mail est déjà pris.', 409);
    }
    error_log('Arzikina API — echec inscription (PDO) : ' . $e->getMessage());
    sendError('registration_failed', "L'inscription a échoué, réessaie plus tard.", 500);
} catch (Throwable $e) {
    error_log('Arzikina API — echec inscription : ' . $e->getMessage());
    sendError('server_error', "L'inscription a échoué, réessaie plus tard.", 500);
}
