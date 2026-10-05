<?php

declare(strict_types=1);

/**
 * Réponse HTTP minimale (statut + corps brut) renvoyée par un `HttpClient`.
 */
final class HttpResponse
{
    public function __construct(
        public readonly int $status,
        public readonly string $body,
    ) {
    }

    /** Corps décodé en tableau, ou tableau vide si ce n'est pas du JSON objet. */
    public function json(): array
    {
        $decoded = json_decode($this->body, true);

        return is_array($decoded) ? $decoded : [];
    }
}

/**
 * Abstraction du transport HTTP utilisé pour parler à Google (OAuth2 et FCM).
 *
 * Existe uniquement pour que `FcmSender` et `GoogleAccessTokenProvider` soient testables sans
 * réseau (un faux client renvoie des réponses préparées). En production : `CurlHttpClient`.
 */
interface HttpClient
{
    /**
     * @param string[] $headers en-têtes au format « Nom: valeur »
     * @throws RuntimeException si la requête n'a pas pu aboutir (réseau, DNS, délai dépassé)
     */
    public function post(string $url, array $headers, string $body): HttpResponse;
}

/**
 * Implémentation cURL (extension disponible sur l'hébergement, vérifié le 4 octobre 2026).
 * Délais courts : un envoi push ne doit jamais bloquer longtemps un script.
 */
final class CurlHttpClient implements HttpClient
{
    public function __construct(
        private readonly int $connectTimeoutSeconds = 5,
        private readonly int $timeoutSeconds = 10,
    ) {
    }

    public function post(string $url, array $headers, string $body): HttpResponse
    {
        $handle = curl_init($url);
        if ($handle === false) {
            throw new RuntimeException('curl_init a échoué.');
        }

        curl_setopt_array($handle, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => $body,
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CONNECTTIMEOUT => $this->connectTimeoutSeconds,
            CURLOPT_TIMEOUT => $this->timeoutSeconds,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
        ]);

        $responseBody = curl_exec($handle);
        if ($responseBody === false) {
            $error = curl_error($handle);
            throw new RuntimeException('Requête HTTP échouée : ' . $error);
        }

        // Pas de curl_close() : sans effet depuis PHP 8.0 et déprécié en 8.5, la ressource est
        // libérée en sortie de fonction.
        $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);

        return new HttpResponse($status, (string) $responseBody);
    }
}
