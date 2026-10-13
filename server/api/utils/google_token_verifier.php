<?php

declare(strict_types=1);

/**
 * Verification d'un jeton d'identite (ID token) Google - utilise par `api/auth/google_login.php`
 * et `api/auth/link_google_account.php`. Jamais appele avec confiance sur un simple e-mail/nom
 * envoye par le client (cahier des charges : "Ne considere jamais qu'un simple e-mail ou un nom
 * transmis par le client suffit a authentifier un utilisateur") - ce fichier EST la verification.
 *
 * Choix d'implementation : endpoint `tokeninfo` officiel de Google
 * (https://oauth2.googleapis.com/tokeninfo) plutot qu'une verification JWT locale (JWK + RS256) -
 * ce serveur n'a pas Composer (voir api/utils/uuid.php) et `tokeninfo` fait tout le travail de
 * verification (signature, emetteur, expiration) sans bibliotheque supplementaire. Google
 * documente cette approche comme valable pour un volume de requetes modere (largement le cas
 * d'Arzikina) ; si le volume devenait tres important, une verification JWK locale deviendrait
 * preferable - meme type de compromis volume/simplicite que celui deja documente ailleurs dans ce
 * projet (voir login.php, limitation anti brute-force signalee explicitement plutot qu'omise).
 *
 * N'utilise PAS `push/HttpClient.php` (POST uniquement, pense pour FCM) : `tokeninfo` est un GET,
 * et coupler ce fichier-ci a l'infrastructure de notifications push aurait ete un couplage
 * artificiel entre deux fonctionnalites sans rapport.
 */

final class GoogleTokenVerificationException extends RuntimeException
{
    public function __construct(public readonly string $errorCode, string $message)
    {
        parent::__construct($message);
    }
}

/**
 * @param string[] $allowedAudiences Client IDs OAuth Arzikina acceptes (Android ET Web) - un jeton
 *                                   dont le "aud" n'en fait pas partie a ete emis pour une AUTRE
 *                                   application, donc toujours rejete.
 * @return array{sub: string, email: string, emailVerified: bool, name: ?string}
 * @throws GoogleTokenVerificationException si le jeton est invalide, expire, mal destine, ou si
 *                                           Google est injoignable.
 */
function verifyGoogleIdToken(string $idToken, array $allowedAudiences): array
{
    if ($idToken === '') {
        throw new GoogleTokenVerificationException('missing_id_token', 'idToken manquant.');
    }

    $url = 'https://oauth2.googleapis.com/tokeninfo?id_token=' . urlencode($idToken);

    $handle = curl_init($url);
    if ($handle === false) {
        throw new GoogleTokenVerificationException('google_unreachable', 'Impossible de contacter Google.');
    }
    curl_setopt_array($handle, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 5,
        CURLOPT_TIMEOUT => 10,
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_SSL_VERIFYHOST => 2,
    ]);
    $responseBody = curl_exec($handle);
    $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);

    if ($responseBody === false) {
        // Jamais journaliser $idToken lui-meme (cahier des charges : "ne journalise aucun jeton
        // d'identite, token de session ou secret") - seul le message d'erreur curl est journalise.
        error_log('Arzikina API - Google tokeninfo injoignable : ' . curl_error($handle));
        throw new GoogleTokenVerificationException('google_unreachable', 'Impossible de contacter Google.');
    }

    $claims = json_decode((string) $responseBody, true);

    // tokeninfo repond 400 avec {"error_description": "..."} pour un jeton invalide/expire/malforme
    // - jamais un detail a renvoyer tel quel au client (variable, pas destine a etre affiche).
    if ($status !== 200 || !is_array($claims)) {
        throw new GoogleTokenVerificationException('invalid_google_token', 'Jeton Google invalide ou expire.');
    }

    $audience = (string) ($claims['aud'] ?? '');
    if (!in_array($audience, $allowedAudiences, true)) {
        // Jeton authentique mais emis pour une AUTRE application - rejete avec la meme rigueur
        // qu'un jeton falsifie : accepter aveuglement n'importe quel "aud" reviendrait a faire
        // confiance a n'importe quel tiers ayant simplement obtenu un jeton Google valide ailleurs.
        throw new GoogleTokenVerificationException('invalid_google_token', 'Jeton Google invalide ou expire.');
    }

    $issuer = (string) ($claims['iss'] ?? '');
    if ($issuer !== 'accounts.google.com' && $issuer !== 'https://accounts.google.com') {
        throw new GoogleTokenVerificationException('invalid_google_token', 'Jeton Google invalide ou expire.');
    }

    $sub = (string) ($claims['sub'] ?? '');
    if ($sub === '') {
        throw new GoogleTokenVerificationException('invalid_google_token', 'Jeton Google invalide ou expire.');
    }

    $email = (string) ($claims['email'] ?? '');
    // tokeninfo renvoie une chaine "true"/"false", jamais un booleen JSON natif.
    $emailVerified = ($claims['email_verified'] ?? 'false') === 'true';

    return [
        'sub' => $sub,
        'email' => $email,
        'emailVerified' => $emailVerified,
        'name' => isset($claims['name']) ? (string) $claims['name'] : null,
    ];
}
