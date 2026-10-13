<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/uuid.php';
require_once __DIR__ . '/../utils/google_token_verifier.php';

/**
 * POST /api/auth/google_login.php
 *
 * Corps attendu (JSON) : { "idToken": "<jeton d'identite Google>", "deviceId": "...", "deviceLabel": "..." }
 *
 * Nouveau point d'entree d'authentification Google (Android + Web). N'AJOUTE PAS un second systeme
 * d'authentification : ce fichier ne fait qu'identifier l'utilisateur via Google puis reutilise
 * EXACTEMENT le meme mecanisme d'emission de session que `login.php`/`register.php` (table
 * `auth_tokens`, meme forme de reponse JSON) - un appareil connecte par Google ou par mot de passe
 * est donc indistinguable pour tout le reste de l'API apres cet appel.
 *
 * Identification : UNIQUEMENT par `users.google_id` (claim "sub" du jeton, stable et jamais
 * reutilise par Google pour un autre compte) - jamais par e-mail ni par nom, voir
 * `google_token_verifier.php`.
 *
 * Trois cas (cahier des charges, section "Gestion des comptes existants et nouveaux
 * utilisateurs") :
 *   - Cas A : `google_id` deja connu -> connexion directe, aucune creation.
 *   - Cas B : aucun compte ne correspond (ni par `google_id`, ni par e-mail) -> creation d'un
 *     nouveau compte, selon le meme schema que `register.php`.
 *   - Cas C : un compte existe deja avec le meme e-mail mais pas encore lie a CE Google -> PAS de
 *     fusion automatique (cahier des charges : "Ne fusionne pas automatiquement les comptes sur la
 *     seule base de l'adresse e-mail") - erreur dediee `account_exists_link_required`, qui invite
 *     l'utilisateur a se connecter classiquement puis a lier son compte Google via
 *     `link_google_account.php`.
 *
 * Cas D (annulation de la fenetre Google) : entierement gere cote client, cet endpoint n'est meme
 * pas appele.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    sendError('invalid_body', 'Corps JSON invalide.', 400);
}

$idToken = trim((string) ($body['idToken'] ?? ''));
$deviceId = isset($body['deviceId']) ? (string) $body['deviceId'] : null;
$deviceLabel = isset($body['deviceLabel']) ? (string) $body['deviceLabel'] : null;

if ($idToken === '') {
    sendError('invalid_body', 'idToken est obligatoire.', 400);
}

$pdo = getDatabaseConnection();

try {
    $claims = verifyGoogleIdToken($idToken, [GOOGLE_CLIENT_ID_ANDROID, GOOGLE_CLIENT_ID_WEB]);
} catch (GoogleTokenVerificationException $e) {
    sendError($e->errorCode, 'Authentification Google impossible, reessaie.', 401);
}

if (!$claims['emailVerified'] || $claims['email'] === '') {
    // Cahier des charges : "Verifier l'adresse e-mail selon les regles de confiance recommandees
    // par Google" - un e-mail non verifie par Google ne doit jamais servir a rattacher ou creer un
    // compte Arzikina.
    sendError('google_email_not_verified', "L'adresse e-mail Google n'est pas verifiee.", 401);
}

$googleId = $claims['sub'];
$email = $claims['email'];

try {
    // Cas A : compte deja lie a ce Google.
    $stmt = $pdo->prepare('SELECT id, full_name FROM users WHERE google_id = :google_id AND deleted_at IS NULL LIMIT 1');
    $stmt->execute(['google_id' => $googleId]);
    $existingByGoogleId = $stmt->fetch();

    if ($existingByGoogleId !== false) {
        issueGoogleSession($pdo, $existingByGoogleId['id'], $existingByGoogleId['full_name'] ?? '', $email, false, $deviceId, $deviceLabel);
    }

    // Cas C : un compte existe deja avec cet e-mail, mais pas encore lie a CE Google.
    $stmt = $pdo->prepare('SELECT id FROM users WHERE email = :email AND deleted_at IS NULL LIMIT 1');
    $stmt->execute(['email' => $email]);
    $existingByEmail = $stmt->fetch();

    if ($existingByEmail !== false) {
        sendError(
            'account_exists_link_required',
            "Un compte Arzikina existe deja avec cette adresse e-mail. Connecte-toi avec ton mot de passe, puis lie ton compte Google depuis Parametres.",
            409
        );
    }

    // Cas B : aucun compte ne correspond - creation.
    $userId = generateUuidV4();
    $fullName = trim((string) ($claims['name'] ?? '')) !== '' ? trim($claims['name']) : googleEmailLocalPart($email);
    $username = generateUniqueUsernameFromEmail($pdo, $email);
    $nowMillis = (int) round(microtime(true) * 1000);

    // `password_hash`/`security_answer_hash` restent NOT NULL par coherence de schema (voir
    // database/migrations/001_add_google_id_to_users.sql) : un compte cree uniquement via Google
    // recoit un hash ALEATOIRE et donc jamais utilisable pour se connecter par mot de passe - meme
    // principe deja en place dans `register.php` pour `security_answer_hash` quand aucune question
    // de securite n'est fournie. Un futur ecran "definir un mot de passe" pourra remplacer ce hash.
    $randomPasswordHash = password_hash(bin2hex(random_bytes(32)), PASSWORD_DEFAULT);
    $knownSecurityQuestions = ['FIRST_PET_NAME', 'BIRTH_CITY', 'MOTHER_MAIDEN_NAME', 'FAVORITE_TEACHER', 'CHILDHOOD_BEST_FRIEND'];
    $randomSecurityAnswerHash = password_hash(bin2hex(random_bytes(32)), PASSWORD_DEFAULT);

    $insertUser = $pdo->prepare(
        'INSERT INTO users
            (id, full_name, username, email, phone_number, password_hash, google_id, security_question, security_answer_hash, created_at, updated_at, deleted_at, version)
         VALUES
            (:id, :full_name, :username, :email, NULL, :password_hash, :google_id, :security_question, :security_answer_hash, :created_at, :updated_at, NULL, 1)'
    );
    $insertUser->execute([
        'id' => $userId,
        'full_name' => $fullName,
        'username' => $username,
        'email' => $email,
        'password_hash' => $randomPasswordHash,
        'google_id' => $googleId,
        'security_question' => $knownSecurityQuestions[0],
        'security_answer_hash' => $randomSecurityAnswerHash,
        'created_at' => $nowMillis,
        'updated_at' => $nowMillis,
    ]);

    issueGoogleSession($pdo, $userId, $fullName, $email, true, $deviceId, $deviceLabel);
} catch (PDOException $e) {
    // Course rarissime (meme raisonnement que register.php) : un `google_id` ou un `email` deja
    // pris entre la verification ci-dessus et l'INSERT tranche ici via les contraintes UNIQUE.
    error_log('Arzikina API - echec connexion Google (PDO) : ' . $e->getMessage());
    sendError('server_error', 'La connexion avec Google a echoue, reessaie plus tard.', 500);
} catch (Throwable $e) {
    error_log('Arzikina API - echec connexion Google : ' . $e->getMessage());
    sendError('server_error', 'La connexion avec Google a echoue, reessaie plus tard.', 500);
}

/**
 * Emet un token de session Arzikina - logique IDENTIQUE a la fin de `login.php`/`register.php`
 * (extraire une fonction commune aux trois fichiers serait premature pour l'instant, meme
 * raisonnement "regle de trois" deja ecrit dans `register.php` ; cette fonction-ci existe
 * seulement parce que CE fichier a deux points de sortie - cas A et cas B - qui en ont besoin).
 *
 * Reponse enrichie de deux champs ABSENTS de `login.php`/`register.php` (voir
 * `data/remote/dto/GoogleLoginResponseDto.kt` cote Android) :
 * - `email` : le client (Android/Web) n'a jamais a relire le jeton Google lui-meme pour
 *   l'obtenir - il recoit directement la valeur deja VERIFIEE par ce serveur.
 * - `isNewAccount` : indique au client si un compte Arzikina VIENT d'etre cree (Cas B) ou
 *   existait deja (Cas A) - necessaire pour decider, cote Android, si les donnees par defaut
 *   doivent etre semees pour le nouvel utilisateur (meme logique que `register.php`, jamais pour
 *   une connexion a un compte deja existant - voir `UnifiedAuthRepositoryImpl.resolveOrCreateLocalUser`).
 */
function issueGoogleSession(PDO $pdo, string $userId, string $fullName, string $email, bool $isNewAccount, ?string $deviceId, ?string $deviceLabel): never
{
    $rawToken = bin2hex(random_bytes(32));
    $tokenHash = hash('sha256', $rawToken);
    $nowMillis = (int) round(microtime(true) * 1000);
    $expiresAtMillis = $nowMillis + (TOKEN_EXPIRY_SECONDS * 1000);

    $insert = $pdo->prepare(
        'INSERT INTO auth_tokens (user_id, token_hash, device_id, device_label, created_at, expires_at)
         VALUES (:user_id, :token_hash, :device_id, :device_label, :created_at, :expires_at)'
    );
    $insert->execute([
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
        'email' => $email,
        'isNewAccount' => $isNewAccount,
    ]);
}

function googleEmailLocalPart(string $email): string
{
    $atPosition = strpos($email, '@');
    $localPart = $atPosition !== false ? substr($email, 0, $atPosition) : $email;
    return $localPart !== '' ? $localPart : 'Utilisateur Google';
}

/**
 * Construit un nom d'utilisateur UNIQUE et valide (memes regles que `register.php` : 3 a 30
 * caracteres, lettres/chiffres/point/underscore) a partir de la partie locale de l'e-mail Google -
 * jamais expose a l'utilisateur comme un choix, purement technique (voir `UserEntity.username`,
 * requis par le schema mais jamais affiche pour un compte cree via Google dans l'interface
 * actuelle).
 */
function generateUniqueUsernameFromEmail(PDO $pdo, string $email): string
{
    $base = preg_replace('/[^a-zA-Z0-9._]/', '', googleEmailLocalPart($email));
    if ($base === '' || $base === null) {
        $base = 'user';
    }
    $base = mb_substr($base, 0, 24);
    if (mb_strlen($base) < 3) {
        $base = $base . 'ggl';
    }

    for ($attempt = 0; $attempt < 10; $attempt++) {
        $candidate = $attempt === 0 ? $base : $base . random_int(100, 999);
        $stmt = $pdo->prepare('SELECT id FROM users WHERE username = :username LIMIT 1');
        $stmt->execute(['username' => $candidate]);
        if ($stmt->fetch() === false) {
            return $candidate;
        }
    }

    // Repli garanti unique (UUID tronque) si 10 tentatives aleatoires echouent toutes - pratiquement
    // jamais atteint en pratique.
    return 'user' . substr(str_replace('-', '', generateUuidV4()), 0, 20);
}
