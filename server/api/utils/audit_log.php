<?php

declare(strict_types=1);

/**
 * Journal des actions d'administration (table `admin_audit_log`, append-only).
 *
 * À appeler dans la MÊME transaction que la modification qu'il décrit, pour qu'une action ne soit
 * jamais appliquée sans trace. `$old`/`$new` : valeurs avant/après (tableaux ou null), stockées en
 * JSON — ne JAMAIS y mettre de mot de passe, jeton ou secret.
 *
 * @param array{userId: ?string, email: string} $actor
 */
function recordAdminAudit(
    PDO $pdo,
    array $actor,
    string $action,
    ?string $targetType = null,
    ?string $targetId = null,
    ?array $old = null,
    ?array $new = null,
): void {
    $stmt = $pdo->prepare(
        'INSERT INTO admin_audit_log
            (actor_user_id, actor_label, action, target_type, target_id, old_value, new_value, ip_address, created_at)
         VALUES
            (:actor_user_id, :actor_label, :action, :target_type, :target_id, :old_value, :new_value, :ip, :created_at)'
    );
    $stmt->execute([
        'actor_user_id' => $actor['userId'],
        'actor_label' => $actor['email'],
        'action' => $action,
        'target_type' => $targetType,
        'target_id' => $targetId,
        'old_value' => $old === null ? null : json_encode($old, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
        'new_value' => $new === null ? null : json_encode($new, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
        'ip' => substr((string) ($_SERVER['REMOTE_ADDR'] ?? ''), 0, 45) ?: null,
        'created_at' => (int) round(microtime(true) * 1000),
    ]);
}
