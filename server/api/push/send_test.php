<?php

declare(strict_types=1);

/**
 * Envoi d'une notification de TEST (`SYSTEM_MESSAGE`) à tous les appareils actifs d'un compte.
 *
 * LIGNE DE COMMANDE UNIQUEMENT :
 *     php send_test.php <email ou nom d'utilisateur>
 *
 * Sur Hostinger (pas de terminal SSH obligatoire) : créer une tâche cron ponctuelle « PHP » qui
 * exécute ce fichier avec l'adresse e-mail en argument, attendre son passage, puis la supprimer.
 * Le résultat (nombre d'appareils, envoyés, révoqués, échecs) arrive dans l'e-mail de sortie du cron.
 *
 * Appelé depuis le Web (malgré le `.htaccess` du dossier) : réponse 404, rien n'est exécuté.
 */

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/FcmSender.php';

$identifier = trim((string) ($argv[1] ?? ''));
if ($identifier === '') {
    fwrite(STDERR, "Usage : php send_test.php <email ou nom d'utilisateur>\n");
    exit(2);
}

$pdo = getDatabaseConnection();
$stmt = $pdo->prepare(
    'SELECT id FROM users
     WHERE (email = :identifier_email OR username = :identifier_username) AND deleted_at IS NULL
     LIMIT 1'
);
$stmt->execute(['identifier_email' => $identifier, 'identifier_username' => $identifier]);
$userId = $stmt->fetchColumn();
if ($userId === false) {
    fwrite(STDERR, "Aucun compte actif pour cet identifiant.\n");
    exit(1);
}

try {
    $result = FcmSender::fromEnvironment($pdo)->sendToUser(
        (string) $userId,
        PushPayload::create(PushPayload::TYPE_SYSTEM_MESSAGE),
    );
} catch (Throwable $e) {
    fwrite(STDERR, 'Envoi impossible : ' . $e->getMessage() . "\n");
    exit(1);
}

echo sprintf(
    "Appareils ciblés : %d — envoyés : %d — révoqués : %d — échecs : %d\n",
    $result->targeted,
    $result->sent,
    $result->revoked,
    $result->failed,
);
if ($result->targeted === 0) {
    echo "Aucun appareil actif : l'application doit d'abord s'enregistrer (étape 2, Android).\n";
}
exit($result->failed > 0 ? 1 : 0);
