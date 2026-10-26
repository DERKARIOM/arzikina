<?php

declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../utils/json_response.php';
require_once __DIR__ . '/../utils/issuer_logo.php';
require_once __DIR__ . '/../utils/product_image.php';
require_once __DIR__ . '/../middleware/auth_middleware.php';

/**
 * POST /api/auth/delete_account.php — authentifié (Bearer).
 *
 * Corps attendu (JSON) : { "password": "..." }
 *
 * Suppression DÉFINITIVE et IMMÉDIATE du compte authentifié — « droit à l'effacement » demandé par
 * la fiche Google Play « Sécurité des données » (voir claude/RAPPORT-FINAL... suppression de
 * compte). Mot de passe RE-DEMANDÉ ici (même principe que `login.php`, `password_verify()`) :
 * protège contre un token volé/qui traîne (presse-papier, log) utilisé seul pour détruire le
 * compte — le propriétaire réel est seul à connaître le mot de passe.
 *
 * Toutes les tables ont `ON DELETE CASCADE` sur `user_id` (voir
 * `database/migrations/001_initial_schema.sql`/`009_user_devices.sql`) : un seul
 * `DELETE FROM users` efface en cascade auth_tokens, user_devices, accounts, categories, persons,
 * transactions, budgets, savings_goals, loans, loan_payments, recurring_transactions(+occurrences),
 * financial_plans(+items), receipts, sync_conflicts, user_preferences — même raisonnement que
 * `logout.php` pour la transaction (tout ou rien).
 *
 * Seules exceptions non couvertes par la base : le fichier de la photo de profil sur le disque
 * (`photo_path`), supprimé explicitement ici — même geste que `profile/delete_photo.php` — et les
 * logos d'émetteur de reçus (`issuer_logos/{user_id}/`, voir `utils/issuer_logo.php`) et les photos
 * de produits (`product_images/{user_id}/`, voir `utils/product_image.php`).
 *
 * Après cet appel, le Bearer utilisé est immédiatement invalide (la ligne `auth_tokens` elle-même
 * vient d'être supprimée par la cascade) — aucun appel supplémentaire n'est nécessaire côté client
 * pour "se déconnecter", l'application efface simplement sa session et sa base locale.
 */

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    sendError('method_not_allowed', 'Cette route accepte uniquement POST.', 405);
}

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    sendError('invalid_body', 'Corps JSON invalide.', 400);
}

$password = (string) ($body['password'] ?? '');
if ($password === '') {
    sendError('invalid_body', 'password est obligatoire.', 400);
}

$pdo = getDatabaseConnection();
// false : exempté du contrôle de version — un utilisateur dont l'app est obsolète doit pouvoir
// se déconnecter / supprimer son compte (voir enforceClientVersionPolicy).
$auth = requireAuthenticatedUser($pdo, false);
$userId = $auth['userId'];

try {
    $stmt = $pdo->prepare('SELECT password_hash, photo_path FROM users WHERE id = :id LIMIT 1');
    $stmt->execute(['id' => $userId]);
    $user = $stmt->fetch();

    if ($user === false) {
        // Session valide mais ligne déjà absente (compte supprimé entretemps par un autre appareil) :
        // traité comme un succès, même raisonnement que `logout.php` rejoué deux fois (idempotent).
        sendJson(['deleted' => true]);
    }

    if (!password_verify($password, $user['password_hash'])) {
        sendError('invalid_credentials', 'Mot de passe incorrect.', 401);
    }

    $photoPath = $user['photo_path'];

    $pdo->beginTransaction();
    $pdo->prepare('DELETE FROM users WHERE id = :id')->execute(['id' => $userId]);
    $pdo->commit();

    deleteIssuerLogosForUser($userId);
    deleteProductImagesForUser($userId);

    if ($photoPath !== null) {
        $absolutePath = __DIR__ . '/../../' . $photoPath;
        if (is_file($absolutePath)) {
            @unlink($absolutePath);
        }
    }

    sendJson(['deleted' => true]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('Arzikina API — echec suppression de compte : ' . $e->getMessage());
    sendError('server_error', 'La suppression du compte a échoué, réessaie plus tard.', 500);
}
