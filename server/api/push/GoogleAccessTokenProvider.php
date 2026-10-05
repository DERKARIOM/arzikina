<?php

declare(strict_types=1);

require_once __DIR__ . '/HttpClient.php';

/**
 * Obtient le jeton d'accès OAuth2 nécessaire à l'API FCM HTTP v1, à partir du compte de service.
 *
 * Flux « JWT bearer » de Google, sans bibliothèque (pas de Composer sur l'hébergement) :
 * 1. un JWT est signé en RS256 avec la clé privée du compte de service (`openssl_sign`) ;
 * 2. il est échangé contre un jeton d'accès valable environ 1 heure ;
 * 3. ce jeton est mis en cache dans un fichier privé (permissions 600, HORS du dossier web) et
 *    réutilisé jusqu'à 5 minutes avant son expiration : un envoi groupé ou un cron ne redemande
 *    pas un jeton à chaque message.
 *
 * Sécurité : la clé privée n'est lue qu'en mémoire, jamais journalisée ni renvoyée ; seul le jeton
 * d'accès (durée de vie courte) est écrit sur disque, dans le même dossier privé que la clé.
 */
final class GoogleAccessTokenProvider
{
    private const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
    private const DEFAULT_TOKEN_URI = 'https://oauth2.googleapis.com/token';
    private const ASSERTION_LIFETIME_SECONDS = 3600;
    /** Marge avant expiration : on renouvelle 5 minutes avant, jamais un jeton « limite ». */
    private const EXPIRY_MARGIN_SECONDS = 300;

    /** @var callable(): int */
    private $clock;

    /**
     * @param array{client_email: string, private_key: string, token_uri?: string} $serviceAccount
     * @param callable(): int|null $clock horloge en secondes (injectable pour les tests)
     */
    public function __construct(
        private readonly array $serviceAccount,
        private readonly HttpClient $http,
        private readonly string $cacheFilePath,
        ?callable $clock = null,
    ) {
        foreach (['client_email', 'private_key'] as $field) {
            if (!isset($serviceAccount[$field]) || !is_string($serviceAccount[$field])) {
                throw new InvalidArgumentException("Compte de service incomplet : champ $field manquant.");
            }
        }
        $this->clock = $clock ?? static fn (): int => time();
    }

    /** Jeton d'accès valide, depuis le cache si possible. */
    public function getAccessToken(): string
    {
        $cached = $this->readCache();
        if ($cached !== null) {
            return $cached;
        }

        return $this->fetchAndCache();
    }

    /**
     * Oublie le jeton en cache (appelé quand FCM répond 401 : jeton révoqué ou clé changée).
     */
    public function invalidate(): void
    {
        if (is_file($this->cacheFilePath)) {
            @unlink($this->cacheFilePath);
        }
    }

    private function fetchAndCache(): string
    {
        $now = ($this->clock)();
        $tokenUri = $this->serviceAccount['token_uri'] ?? self::DEFAULT_TOKEN_URI;

        $assertion = $this->signJwt([
            'iss' => $this->serviceAccount['client_email'],
            'scope' => self::SCOPE,
            'aud' => $tokenUri,
            'iat' => $now,
            'exp' => $now + self::ASSERTION_LIFETIME_SECONDS,
        ]);

        $response = $this->http->post(
            $tokenUri,
            ['Content-Type: application/x-www-form-urlencoded'],
            http_build_query([
                'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                'assertion' => $assertion,
            ]),
        );

        $data = $response->json();
        if ($response->status !== 200 || !isset($data['access_token'], $data['expires_in'])) {
            // `error` / `error_description` de Google ne contiennent aucun secret : utiles au diagnostic.
            $reason = ($data['error'] ?? 'unknown') . ' ' . ($data['error_description'] ?? '');
            throw new RuntimeException("Jeton OAuth2 refusé (HTTP {$response->status}) : " . trim($reason));
        }

        $accessToken = (string) $data['access_token'];
        $expiresAt = $now + (int) $data['expires_in'];
        $this->writeCache($accessToken, $expiresAt);

        return $accessToken;
    }

    /** @param array<string, int|string> $claims */
    private function signJwt(array $claims): string
    {
        $header = self::base64Url((string) json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
        $payload = self::base64Url((string) json_encode($claims, JSON_UNESCAPED_SLASHES));
        $signingInput = $header . '.' . $payload;

        $privateKey = openssl_pkey_get_private($this->serviceAccount['private_key']);
        if ($privateKey === false) {
            throw new RuntimeException('Clé privée du compte de service illisible.');
        }

        $signature = '';
        if (!openssl_sign($signingInput, $signature, $privateKey, OPENSSL_ALGO_SHA256)) {
            throw new RuntimeException('Signature du JWT impossible.');
        }

        return $signingInput . '.' . self::base64Url($signature);
    }

    private function readCache(): ?string
    {
        if (!is_file($this->cacheFilePath)) {
            return null;
        }
        $data = json_decode((string) @file_get_contents($this->cacheFilePath), true);
        if (!is_array($data) || !isset($data['access_token'], $data['expires_at'])) {
            return null;
        }
        if ((int) $data['expires_at'] - self::EXPIRY_MARGIN_SECONDS <= ($this->clock)()) {
            return null;
        }

        return (string) $data['access_token'];
    }

    /**
     * Écriture atomique (fichier temporaire puis `rename`) avec permissions 600 posées AVANT d'y
     * écrire le jeton : aucun instant où le jeton serait lisible par un autre compte.
     * Un échec d'écriture n'empêche pas l'envoi (le jeton sera simplement redemandé la fois suivante).
     */
    private function writeCache(string $accessToken, int $expiresAt): void
    {
        $tmpPath = $this->cacheFilePath . '.' . bin2hex(random_bytes(4)) . '.tmp';
        if (@touch($tmpPath) === false) {
            error_log('Arzikina push — cache du jeton OAuth2 non écrit (dossier non accessible).');
            return;
        }
        @chmod($tmpPath, 0600);
        $json = (string) json_encode(['access_token' => $accessToken, 'expires_at' => $expiresAt]);
        if (@file_put_contents($tmpPath, $json, LOCK_EX) === false || !@rename($tmpPath, $this->cacheFilePath)) {
            @unlink($tmpPath);
            error_log('Arzikina push — cache du jeton OAuth2 non écrit.');
        }
    }

    private static function base64Url(string $data): string
    {
        return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
    }
}
