<?php

declare(strict_types=1);

/**
 * Accès à la table `user_devices` (migration 009). Seul endroit qui écrit dans cette table :
 * les endpoints `devices/*` et `auth/logout.php` ainsi que `FcmSender` passent tous par ici.
 *
 * Toutes les méthodes prennent l'utilisateur AUTHENTIFIÉ (issu du middleware) en paramètre et
 * filtrent dessus : un client ne peut jamais lire, déplacer ou révoquer l'appareil d'un autre compte
 * en devinant un `installation_id`.
 */
final class UserDeviceRepository
{
    public const PLATFORMS = ['android', 'ios', 'web'];

    public function __construct(private readonly PDO $pdo)
    {
    }

    /**
     * Enregistre (ou met à jour) l'installation pour l'utilisateur et la session donnés.
     *
     * Dans une transaction :
     * 1. le même token FCM rattaché à une AUTRE installation est supprimé (FCM peut réattribuer un
     *    token après restauration de sauvegarde : il ne doit jamais exister deux lignes pour lui) ;
     * 2. upsert sur `installation_id` : si l'installation existait pour un autre compte, elle est
     *    déplacée vers ce compte (changement de compte sur le même téléphone) et réactivée.
     *
     * `ON DUPLICATE KEY UPDATE ... VALUES()` : syntaxe acceptée à la fois par MySQL et MariaDB
     * (l'alias `AS new` de MySQL 8.0.19+ n'existe pas sous MariaDB).
     */
    public function register(
        string $userId,
        int $authTokenId,
        string $installationId,
        string $fcmToken,
        string $platform,
        ?string $appVersion,
        ?string $locale,
        int $nowMillis,
    ): void {
        $this->pdo->beginTransaction();
        try {
            $this->pdo->prepare(
                'DELETE FROM user_devices WHERE fcm_token = :fcm_token AND installation_id <> :installation_id'
            )->execute(['fcm_token' => $fcmToken, 'installation_id' => $installationId]);

            $this->pdo->prepare(
                'INSERT INTO user_devices
                    (user_id, installation_id, fcm_token, platform, app_version, locale, auth_token_id,
                     created_at, updated_at, last_seen_at, revoked_at)
                 VALUES
                    (:user_id, :installation_id, :fcm_token, :platform, :app_version, :locale, :auth_token_id,
                     :created_at, :updated_at, :last_seen_at, NULL)
                 ON DUPLICATE KEY UPDATE
                    user_id = VALUES(user_id),
                    fcm_token = VALUES(fcm_token),
                    platform = VALUES(platform),
                    app_version = VALUES(app_version),
                    locale = VALUES(locale),
                    auth_token_id = VALUES(auth_token_id),
                    updated_at = VALUES(updated_at),
                    last_seen_at = VALUES(last_seen_at),
                    revoked_at = NULL'
            )->execute([
                'user_id' => $userId,
                'installation_id' => $installationId,
                'fcm_token' => $fcmToken,
                'platform' => $platform,
                'app_version' => $appVersion,
                'locale' => $locale,
                'auth_token_id' => $authTokenId,
                'created_at' => $nowMillis,
                'updated_at' => $nowMillis,
                'last_seen_at' => $nowMillis,
            ]);

            $this->pdo->commit();
        } catch (Throwable $e) {
            $this->pdo->rollBack();
            throw $e;
        }
    }

    /**
     * Désactive l'installation de CET utilisateur (notifications coupées par l'utilisateur, ou
     * application qui se désinscrit). Sans effet si l'installation appartient à un autre compte.
     *
     * @return bool true si une ligne active a été révoquée
     */
    public function revokeInstallation(string $userId, string $installationId, int $nowMillis): bool
    {
        $stmt = $this->pdo->prepare(
            'UPDATE user_devices SET revoked_at = :now, updated_at = :now_updated
             WHERE installation_id = :installation_id AND user_id = :user_id AND revoked_at IS NULL'
        );
        $stmt->execute([
            'now' => $nowMillis,
            'now_updated' => $nowMillis,
            'installation_id' => $installationId,
            'user_id' => $userId,
        ]);

        return $stmt->rowCount() > 0;
    }

    /** Désactive tous les appareils enregistrés par une session (déconnexion). */
    public function revokeForSession(string $userId, int $authTokenId, int $nowMillis): int
    {
        $stmt = $this->pdo->prepare(
            'UPDATE user_devices SET revoked_at = :now, updated_at = :now_updated
             WHERE auth_token_id = :auth_token_id AND user_id = :user_id AND revoked_at IS NULL'
        );
        $stmt->execute([
            'now' => $nowMillis,
            'now_updated' => $nowMillis,
            'auth_token_id' => $authTokenId,
            'user_id' => $userId,
        ]);

        return $stmt->rowCount();
    }

    /**
     * Destinataires d'un utilisateur : appareils non révoqués dont la session d'origine est encore
     * valide (non révoquée, non expirée) ET appartient bien au même utilisateur.
     *
     * @return list<array{id: int, fcm_token: string}>
     */
    public function findDeliverableDevices(string $userId, int $nowMillis): array
    {
        $stmt = $this->pdo->prepare(
            'SELECT d.id, d.fcm_token
             FROM user_devices d
             JOIN auth_tokens t ON t.id = d.auth_token_id AND t.user_id = d.user_id
             WHERE d.user_id = :user_id
               AND d.revoked_at IS NULL
               AND t.revoked_at IS NULL
               AND t.expires_at > :now'
        );
        $stmt->execute(['user_id' => $userId, 'now' => $nowMillis]);

        return array_map(
            static fn (array $row): array => ['id' => (int) $row['id'], 'fcm_token' => (string) $row['fcm_token']],
            $stmt->fetchAll(),
        );
    }

    /** Désactive un appareil dont FCM a refusé le token (désinstallé, token expiré…). */
    public function revokeDevice(int $deviceId, int $nowMillis): void
    {
        $this->pdo->prepare(
            'UPDATE user_devices SET revoked_at = :now, updated_at = :now_updated
             WHERE id = :id AND revoked_at IS NULL'
        )->execute(['now' => $nowMillis, 'now_updated' => $nowMillis, 'id' => $deviceId]);
    }
}
