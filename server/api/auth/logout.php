<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';
require_once __DIR__ . '/../push/UserDeviceRepository.php';

/**
 * POST /api/auth/logout.php — authentifié (Bearer). Corps vide.
 *
 * Ferme la session du Bearer CÔTÉ SERVEUR (jusqu'ici la déconnexion n'était que locale : le token
 * restait valide 30 jours) et coupe les notifications push des appareils qu'elle avait enregistrés,
 * dans une seule transaction. Les autres sessions du même compte (autres téléphones, Web) ne sont
 * pas touchées.
 *
 * L'application efface ses données de session localement QUOI QU'IL ARRIVE (hors ligne, erreur) :
 * cet appel est « au mieux ». S'il n'aboutit pas, l'appareil reste protégé par le contrôle du champ
 * `uid` des notifications côté application (voir claude/fcm/AUDIT-FCM-ANDROID.md, 4.3).
 *
 * Après cet appel, le même Bearer est refusé (401) par toutes les routes : appeler logout deux fois
 * renvoie donc 401 la seconde fois, ce que l'application traite comme un succès.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$pdo = getDatabaseConnection();
$auth = requireAuthenticatedUser($pdo);
$nowMillis = (int) round(microtime(true) * 1000);

try {
    $pdo->beginTransaction();

    $pdo->prepare(
        'UPDATE auth_tokens SET revoked_at = :now WHERE id = :id AND user_id = :user_id AND revoked_at IS NULL'
    )->execute(['now' => $nowMillis, 'id' => $auth['tokenId'], 'user_id' => $auth['userId']]);

    (new UserDeviceRepository($pdo))->revokeForSession($auth['userId'], $auth['tokenId'], $nowMillis);

    $pdo->commit();
    sendJson(['loggedOut' => true]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('Arzikina API — echec deconnexion : ' . $e->getMessage());
    sendError('server_error', 'La déconnexion a échoué, réessaie plus tard.', 500);
}
