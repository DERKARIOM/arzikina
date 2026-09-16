<?php

declare(strict_types=1);

/**
 * Réponses JSON uniformes pour tous les endpoints de l'API — évite de dupliquer
 * `header()`/`http_response_code()`/`json_encode()` dans chaque fichier (cahier des charges :
 * "évite absolument le code dupliqué").
 */

/**
 * En-têtes CORS — ce fichier est inclus par TOUS les points d'entrée de l'API (`require_once
 * __DIR__ . '/../utils/json_response.php';`), c'est donc le seul endroit à toucher pour couvrir
 * chaque endpoint sans dupliquer ce bloc partout. Nécessaire dès que le frontend web (déployé sur
 * un domaine/sous-domaine DIFFÉRENT de cette API, voir docs/DEPLOIEMENT-HOSTINGER.md) fait un appel
 * `fetch()` depuis le navigateur : sans ces en-têtes, le navigateur bloque la réponse même si l'API
 * répond correctement (invisible en testant avec Postman, qui n'applique pas la politique CORS).
 *
 * Origine explicite (PAS `*`) : plus sûr, et compatible avec un futur passage à des cookies de
 * session si besoin (un `*` combiné à des identifiants ne fonctionne de toute façon pas). L'app
 * Android n'est pas concernée — CORS est une politique appliquée uniquement par les navigateurs.
 *
 * Frontend web déployé sur Render (voir arzikina-web-sync/render.yaml) — pas un secret, peut
 * rester dans le code versionné, contrairement aux identifiants de `config_arzikina_secrets.php`.
 * Sans slash final : un en-tête Origin de navigateur n'en a jamais.
 */
const ALLOWED_WEB_ORIGIN = 'https://arziki.naniger.com';

header('Access-Control-Allow-Origin: ' . ALLOWED_WEB_ORIGIN);
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

// Requête de pré-vérification envoyée automatiquement par le navigateur avant tout appel "non
// simple" (ex. avec un en-tête Authorization) — aucun endpoint ne doit exécuter sa logique métier
// pour celle-ci, une réponse vide suffit.
if (($_SERVER['REQUEST_METHOD'] ?? '') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

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
