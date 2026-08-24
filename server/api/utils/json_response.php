<?php

declare(strict_types=1);

/**
 * Réponses JSON uniformes pour tous les endpoints de l'API — évite de dupliquer
 * `header()`/`http_response_code()`/`json_encode()` dans chaque fichier (cahier des charges :
 * "évite absolument le code dupliqué").
 */

function sendJson(array $data, int $statusCode = 200): void
{
    http_response_code($statusCode);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

/**
 * Réponse d'erreur générique. `$errorCode` est un identifiant COURT et STABLE (ex.
 * "invalid_credentials", "invalid_token"), destiné à être lu par le code Android — jamais un
 * message narratif. Voir cahier des charges, section sécurité : ne jamais renvoyer un message
 * d'exception brut au client (peut révéler des détails internes exploitables).
 */
function sendError(string $errorCode, string $message, int $statusCode): void
{
    sendJson(['error' => $errorCode, 'message' => $message], $statusCode);
}
