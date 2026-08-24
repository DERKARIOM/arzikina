<?php

declare(strict_types=1);

/**
 * Convertit les clés `snake_case` d'une ligne MySQL (`color_argb`, `created_at`...) en `camelCase`
 * (`colorArgb`, `createdAt`...) — la convention déjà utilisée par toutes les classes Kotlin côté
 * Android (voir `data/backup/BackupDto.kt`). Centralisé ici pour ne JAMAIS être dupliqué entre
 * `pull.php`, `push.php`, et les futurs endpoints des 13 autres entités (cahier des charges :
 * "évite absolument le code dupliqué").
 */
function toCamelCaseRow(array $row): array
{
    $result = [];
    foreach ($row as $key => $value) {
        $camelKey = preg_replace_callback('/_([a-z0-9])/', static fn (array $m): string => strtoupper($m[1]), (string) $key);
        $result[$camelKey] = $value;
    }
    return $result;
}
