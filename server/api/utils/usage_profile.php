<?php

declare(strict_types=1);

/**
 * Profil d'usage d'un utilisateur, renvoyé par login.php et google_login.php pour que l'application retrouve
 * son choix de départ (« Gestion de boutique », « Finances personnelles », « Les deux ») après une
 * déconnexion, une réinstallation ou un changement d'appareil.
 *
 * Retourne :
 *   - `usageMode` : 'SHOP' | 'PERSONAL' | 'BOTH', ou null (jamais demandé : compte antérieur au choix, ou
 *     inscrit depuis le web) ;
 *   - `hasAccounts` : true si AU MOINS UNE ligne `accounts` existe pour cet utilisateur (supprimées comprises).
 *     Sert à l'application à reprendre une inscription interrompue (utilisateur créé sur le serveur mais jamais
 *     configuré) sans jamais recréer des comptes pour un utilisateur qui en a déjà eu.
 *
 * Tolérant par construction : si la colonne `usage_mode` n'existe pas encore (migration 013 non appliquée) ou si
 * une requête échoue, la connexion NE DOIT PAS échouer. Valeurs de repli : usageMode = null, hasAccounts = true
 * (l'application ne crée alors rien).
 *
 * @return array{usageMode: ?string, hasAccounts: bool}
 */
function fetchUsageProfile(PDO $pdo, string $userId): array
{
    $usageMode = null;
    $hasAccounts = true;

    try {
        $stmt = $pdo->prepare('SELECT usage_mode FROM users WHERE id = :id LIMIT 1');
        $stmt->execute(['id' => $userId]);
        $value = $stmt->fetchColumn();
        if (is_string($value) && in_array($value, ['SHOP', 'PERSONAL', 'BOTH'], true)) {
            $usageMode = $value;
        }
    } catch (Throwable $e) {
        error_log('Arzikina API - lecture usage_mode impossible : ' . $e->getMessage());
    }

    try {
        $stmt = $pdo->prepare('SELECT EXISTS(SELECT 1 FROM accounts WHERE user_id = :id)');
        $stmt->execute(['id' => $userId]);
        $hasAccounts = ((int) $stmt->fetchColumn()) === 1;
    } catch (Throwable $e) {
        error_log('Arzikina API - lecture des comptes impossible : ' . $e->getMessage());
    }

    return ['usageMode' => $usageMode, 'hasAccounts' => $hasAccounts];
}
