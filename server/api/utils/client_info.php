<?php

declare(strict_types=1);

/**
 * Identification de l'application cliente d'une requête, pour l'affichage d'administration
 * (« dernière application utilisée »). Information d'USAGE uniquement : jamais une décision de sécurité
 * (les en-têtes sont fournis par le client et falsifiables).
 */

/**
 * Plateforme déduite de la requête, dans cet ordre :
 *   1. en-tête explicite `X-App-Platform` (`android` | `ios` | `web`) ;
 *   2. `X-App-Version-Code` présent → application Android (c'est elle qui l'envoie, voir `AppVersionInterceptor`) ;
 *   3. User-Agent `Arsikina-Web/…` → console Web (posé par le relais `routes/api/proxy.ts`) ;
 *   sinon `null` (inconnu : ancien client, outil, test).
 *
 * @param array<string, mixed> $server Normalement `$_SERVER` ; paramètre pour rester testable.
 */
function detectClientPlatform(array $server): ?string
{
    $explicit = strtolower(trim((string) ($server['HTTP_X_APP_PLATFORM'] ?? '')));
    if (in_array($explicit, ['android', 'ios', 'web'], true)) {
        return $explicit;
    }
    $code = (string) ($server['HTTP_X_APP_VERSION_CODE'] ?? '');
    if ($code !== '' && ctype_digit($code)) {
        return 'android';
    }
    if (stripos((string) ($server['HTTP_USER_AGENT'] ?? ''), 'Arsikina-Web') === 0) {
        return 'web';
    }
    return null;
}
