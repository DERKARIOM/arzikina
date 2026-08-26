<?php

declare(strict_types=1);

/**
 * Convertit les clés `snake_case` d'une ligne MySQL (`color_argb`, `created_at`...) en `camelCase`
 * (`colorArgb`, `createdAt`...) — la convention déjà utilisée par toutes les classes Kotlin côté
 * Android (voir `data/backup/BackupDto.kt`). Centralisé ici pour ne JAMAIS être dupliqué entre
 * `pull.php`, `push.php`, et les futurs endpoints des 13 autres entités (cahier des charges :
 * "évite absolument le code dupliqué").
 *
 * [$config] (voir `ENTITY_CONFIGS`) est OBLIGATOIRE depuis le bug réel rencontré à l'étape 17.6 :
 * pour les colonnes spécifiques à l'entité, la clé JSON renvoyée doit être `$col['payload']`, PAS
 * une conversion snake_case→camelCase mécanique du nom de colonne `$col['db']`. Ces deux valeurs
 * coïncidaient par coïncidence pour toutes les entités jusqu'à `transactions` (ex. `color_argb` →
 * `colorArgb` des deux façons) — mais `transactions.account_id` doit devenir `accountSyncId`
 * (voir la KDoc de `ENTITY_CONFIGS['transactions']`), pas `accountId` (ce que produisait l'ancienne
 * conversion mécanique). Résultat concret du bug : `TransactionServerStateDto` (côté Android)
 * recevait un JSON sans jamais le champ `accountSyncId` qu'il exige, `kotlinx.serialization`
 * levait une `SerializationException` à chaque décodage — toutes les transactions déjà acceptées
 * par le serveur repassaient `FAILED` côté appareil (voir `SyncEngineImpl.pushBatch`) sans jamais
 * pouvoir se stabiliser. Colonnes IMPLICITES (`id`, `user_id`, `created_at`...) : toujours absentes
 * de `$config['columns']`, donc toujours converties mécaniquement (repli `??`), inchangé.
 */
function toCamelCaseRow(array $row, array $config): array
{
    $payloadKeyByColumn = [];
    foreach ($config['columns'] as $col) {
        $payloadKeyByColumn[$col['db']] = $col['payload'];
    }

    $result = [];
    foreach ($row as $key => $value) {
        $camelKey = $payloadKeyByColumn[$key]
            ?? preg_replace_callback('/_([a-z0-9])/', static fn (array $m): string => strtoupper($m[1]), (string) $key);
        $result[$camelKey] = $value;
    }
    return $result;
}
