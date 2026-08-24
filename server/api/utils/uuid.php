<?php

declare(strict_types=1);

/**
 * Génère un UUID v4 (aléatoire), conforme RFC 4122. Pas de dépendance externe (pas de Composer
 * installé sur ce serveur à ce jour) — implémentation directe via `random_bytes()`.
 *
 * Utilisé côté serveur uniquement en repli défensif (voir `api/sync/push.php`) : le cas normal est
 * que le Sync Engine Android génère toujours le `syncId` avant l'envoi (voir
 * docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 6.3), ce repli ne devrait donc quasiment jamais
 * s'activer en pratique.
 */
function generateUuidV4(): string
{
    $data = random_bytes(16);
    $data[6] = chr((ord($data[6]) & 0x0f) | 0x40); // version 4
    $data[8] = chr((ord($data[8]) & 0x3f) | 0x80); // variant RFC 4122

    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
}
