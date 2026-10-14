<?php

declare(strict_types=1);

require_once __DIR__ . '/json_response.php';

/**
 * Utilitaires HTTP communs aux endpoints `api/admin/*` (méthode, corps JSON, pagination, validation,
 * calendrier du projet) — écrits UNE fois ici pour ne pas être dupliqués dans chaque endpoint.
 */

/** Fuseau retenu par le projet pour « aujourd'hui », « 7 derniers jours » et les courbes. */
const PROJECT_TIMEZONE = 'Africa/Niamey';

function requireHttpMethod(string $method): void
{
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== $method) {
        sendError('method_not_allowed', "Cette route accepte uniquement {$method}.", 405);
    }
}

/** @return array<string, mixed> */
function readJsonObject(): array
{
    $body = json_decode((string) file_get_contents('php://input'), true);
    if (!is_array($body) || ($body !== [] && array_is_list($body))) {
        sendError('invalid_body', 'Corps JSON invalide.', 400);
    }
    return $body;
}

/** @return array{page: int, pageSize: int, offset: int} Pagination bornée (jamais de liste illimitée). */
function readPagination(int $defaultSize = 25, int $maxSize = 100): array
{
    $page = filter_var($_GET['page'] ?? 1, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1, 'max_range' => 100000]]);
    $size = filter_var($_GET['pageSize'] ?? $defaultSize, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1, 'max_range' => $maxSize]]);
    if ($page === false || $size === false) {
        sendError('invalid_pagination', "page ou pageSize invalide (pageSize ≤ {$maxSize}).", 400);
    }
    return ['page' => $page, 'pageSize' => $size, 'offset' => ($page - 1) * $size];
}

/** Échappe `%`, `_` et `\` pour un LIKE : la saisie de l'utilisateur ne doit jamais agir comme joker. */
function escapeLike(string $value): string
{
    return addcslashes($value, '\\%_');
}

function nowMillis(): int
{
    return (int) round(microtime(true) * 1000);
}

/** Minuit (heure du projet) du jour situé à `$daysAgo` jours de `$nowMs`, en millisecondes UTC. */
function projectDayStartMillis(int $nowMs, int $daysAgo = 0): int
{
    $tz = new DateTimeZone(PROJECT_TIMEZONE);
    $day = (new DateTimeImmutable('@' . intdiv($nowMs, 1000)))->setTimezone($tz)->setTime(0, 0);
    if ($daysAgo > 0) {
        $day = $day->modify("-{$daysAgo} days");
    }
    return $day->getTimestamp() * 1000;
}

/** Décalage UTC (ms) du fuseau du projet — constant pour Niamey (pas d'heure d'été). */
function projectUtcOffsetMillis(int $nowMs): int
{
    return (new DateTimeZone(PROJECT_TIMEZONE))->getOffset(new DateTimeImmutable('@' . intdiv($nowMs, 1000))) * 1000;
}

/** Parse `AAAA-MM-JJ` (heure du projet) → minuit en ms UTC, ou null si invalide. */
function parseProjectDate(string $value): ?int
{
    $date = DateTimeImmutable::createFromFormat('!Y-m-d', $value, new DateTimeZone(PROJECT_TIMEZONE));
    $errors = DateTimeImmutable::getLastErrors();
    if ($date === false || ($errors !== false && ($errors['warning_count'] > 0 || $errors['error_count'] > 0))) {
        return null;
    }
    return $date->getTimestamp() * 1000;
}

/** Chaîne obligatoire/optionnelle, bornée. `null` si absente et non obligatoire. */
function bodyString(array $body, string $key, int $maxLength, bool $required = true): ?string
{
    $value = $body[$key] ?? null;
    if ($value === null || $value === '') {
        if ($required) {
            sendError('invalid_body', "{$key} est obligatoire.", 400);
        }
        return null;
    }
    if (!is_string($value) || mb_strlen(trim($value)) > $maxLength) {
        sendError('invalid_body', "{$key} invalide (texte de {$maxLength} caractères maximum).", 400);
    }
    return trim($value);
}

/** Booléen JSON strict (`true`/`false`) — refuse "1", "true", 1... */
function bodyBool(array $body, string $key): bool
{
    if (!array_key_exists($key, $body) || !is_bool($body[$key])) {
        sendError('invalid_body', "{$key} doit être un booléen.", 400);
    }
    return $body[$key];
}

/** Entier JSON strict dans [min, max]. */
function bodyInt(array $body, string $key, int $min, int $max): int
{
    $value = $body[$key] ?? null;
    if (!is_int($value) || $value < $min || $value > $max) {
        sendError('invalid_body', "{$key} doit être un entier entre {$min} et {$max}.", 400);
    }
    return $value;
}

/** URL de téléchargement : https uniquement, sans identifiants intégrés. */
function validateHttpsUrl(string $url, string $field): string
{
    $parts = parse_url($url);
    if (
        strlen($url) > 512
        || filter_var($url, FILTER_VALIDATE_URL) === false
        || !is_array($parts)
        || ($parts['scheme'] ?? '') !== 'https'
        || ($parts['host'] ?? '') === ''
        || isset($parts['user'])
        || isset($parts['pass'])
    ) {
        sendError('invalid_url', "{$field} doit être une URL HTTPS valide (512 caractères maximum).", 400);
    }
    return $url;
}
