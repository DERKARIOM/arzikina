<?php

declare(strict_types=1);

require_once __DIR__ . '/HttpClient.php';
require_once __DIR__ . '/GoogleAccessTokenProvider.php';
require_once __DIR__ . '/PushPayload.php';
require_once __DIR__ . '/UserDeviceRepository.php';

/**
 * Bilan d'un envoi à un utilisateur.
 */
final class FcmSendResult
{
    public function __construct(
        /** Appareils éligibles trouvés. */
        public readonly int $targeted,
        /** Messages acceptés par FCM. */
        public readonly int $sent,
        /** Appareils révoqués parce que FCM a refusé leur token. */
        public readonly int $revoked,
        /** Échecs temporaires (quota, indisponibilité, réseau) : appareil conservé. */
        public readonly int $failed,
    ) {
    }
}

/**
 * Envoie des notifications « data » via l'API FCM HTTP v1.
 *
 * Point d'entrée UNIQUE de l'envoi : un futur cron (échéances de prêts), une alerte de sécurité ou
 * un backend tiers appellent `sendToUser()` et n'ont jamais à connaître les tokens ni Google.
 *
 * - Destinataires : `UserDeviceRepository::findDeliverableDevices()` (session valide obligatoire).
 * - Message : `data` uniquement (aucun bloc `notification`), priorité Android haute, durée de vie
 *   limitée (un rappel vieux de plus d'un jour n'a plus de sens).
 * - Tokens refusés définitivement par FCM → appareil révoqué immédiatement.
 * - 401 → jeton OAuth2 invalidé puis UN seul nouvel essai.
 * - Aucune donnée sensible journalisée : ni token FCM complet, ni jeton d'accès, ni contenu.
 */
final class FcmSender
{
    private const ENDPOINT = 'https://fcm.googleapis.com/v1/projects/%s/messages:send';
    private const MESSAGE_TTL = '86400s';

    /** @var callable(): int */
    private $clockMillis;

    /**
     * @param callable(): int|null $clockMillis horloge en millisecondes (injectable pour les tests)
     */
    public function __construct(
        private readonly string $projectId,
        private readonly GoogleAccessTokenProvider $tokenProvider,
        private readonly HttpClient $http,
        private readonly UserDeviceRepository $devices,
        ?callable $clockMillis = null,
    ) {
        if (preg_match('/^[a-z0-9-]+$/', $projectId) !== 1) {
            throw new InvalidArgumentException('Identifiant de projet Firebase invalide.');
        }
        $this->clockMillis = $clockMillis ?? static fn (): int => (int) round(microtime(true) * 1000);
    }

    /**
     * Construit l'instance de production à partir du compte de service stocké sur le serveur.
     *
     * Emplacement de la clé : constante `FIREBASE_SERVICE_ACCOUNT_PATH` si elle est définie dans
     * `config_arzikina_secrets.php`, sinon `$HOME/firebase-service-account.json` (HORS du dossier
     * web, permissions 600). Le cache du jeton d'accès est écrit à côté.
     *
     * @throws RuntimeException clé absente ou illisible (message sans contenu sensible)
     */
    public static function fromEnvironment(PDO $pdo): self
    {
        $homeDir = getenv('HOME') ?: dirname(__DIR__, 4);
        $keyPath = defined('FIREBASE_SERVICE_ACCOUNT_PATH')
            ? (string) constant('FIREBASE_SERVICE_ACCOUNT_PATH')
            : $homeDir . '/firebase-service-account.json';

        if (!is_readable($keyPath)) {
            throw new RuntimeException('Compte de service Firebase introuvable ou illisible.');
        }
        $serviceAccount = json_decode((string) file_get_contents($keyPath), true);
        if (!is_array($serviceAccount) || !isset($serviceAccount['project_id']) || !is_string($serviceAccount['project_id'])) {
            throw new RuntimeException('Compte de service Firebase invalide (project_id manquant).');
        }

        $http = new CurlHttpClient();

        return new self(
            $serviceAccount['project_id'],
            new GoogleAccessTokenProvider($serviceAccount, $http, dirname($keyPath) . '/.arzikina-fcm-access-token.json'),
            $http,
            new UserDeviceRepository($pdo),
        );
    }

    /**
     * @throws RuntimeException si le jeton OAuth2 ne peut pas être obtenu (clé révoquée, réseau) :
     *         rien n'est alors tenté, aucun appareil n'est modifié.
     */
    public function sendToUser(string $userId, PushPayload $payload): FcmSendResult
    {
        $devices = $this->devices->findDeliverableDevices($userId, ($this->clockMillis)());
        if ($devices === []) {
            return new FcmSendResult(0, 0, 0, 0);
        }
        // Jeton obtenu UNE fois avant la boucle : si Google le refuse, on échoue tout de suite au
        // lieu de redemander un jeton pour chaque appareil.
        $this->tokenProvider->getAccessToken();

        $sent = 0;
        $revoked = 0;
        $failed = 0;

        foreach ($devices as $device) {
            $outcome = $this->sendToDevice($device['fcm_token'], $payload->toData($userId));
            if ($outcome === 'sent') {
                $sent++;
            } elseif ($outcome === 'invalid_token') {
                $this->devices->revokeDevice($device['id'], ($this->clockMillis)());
                $revoked++;
            } else {
                $failed++;
            }
        }

        return new FcmSendResult(count($devices), $sent, $revoked, $failed);
    }

    /**
     * @param array<string, string> $data
     * @return 'sent'|'invalid_token'|'failed'
     */
    private function sendToDevice(string $fcmToken, array $data): string
    {
        $body = (string) json_encode([
            'message' => [
                'token' => $fcmToken,
                'data' => $data,
                'android' => ['priority' => 'high', 'ttl' => self::MESSAGE_TTL],
            ],
        ], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);

        for ($attempt = 1; $attempt <= 2; $attempt++) {
            try {
                // Depuis le cache dans le cas normal ; nouvelle demande seulement après un 401.
                $response = $this->http->post(
                    sprintf(self::ENDPOINT, $this->projectId),
                    [
                        'Authorization: Bearer ' . $this->tokenProvider->getAccessToken(),
                        'Content-Type: application/json; charset=UTF-8',
                    ],
                    $body,
                );
            } catch (RuntimeException $e) {
                error_log('Arzikina push — envoi impossible : ' . $e->getMessage());
                return 'failed';
            }

            if ($response->status === 200) {
                return 'sent';
            }
            if ($response->status === 401 && $attempt === 1) {
                $this->tokenProvider->invalidate();
                continue;
            }

            $outcome = self::classifyError($response);
            error_log(sprintf(
                'Arzikina push — refus FCM (HTTP %d, %s) pour le token …%s',
                $response->status,
                self::errorCode($response) ?? 'inconnu',
                substr($fcmToken, -6),
            ));

            return $outcome;
        }

        return 'failed';
    }

    /**
     * Token définitivement inutilisable → `invalid_token` ; tout le reste (quota, panne Google,
     * erreur dans NOTRE requête) → `failed`, sans toucher à l'appareil.
     *
     * `INVALID_ARGUMENT` peut aussi viser le contenu du message : on ne révoque que si l'erreur
     * désigne explicitement le token, pour ne jamais désinscrire tous les appareils à cause d'un
     * bug de payload.
     */
    private static function classifyError(HttpResponse $response): string
    {
        $code = self::errorCode($response);
        if ($code === 'UNREGISTERED' || $code === 'SENDER_ID_MISMATCH') {
            return 'invalid_token';
        }
        if ($code === 'INVALID_ARGUMENT' && self::errorTargetsToken($response)) {
            return 'invalid_token';
        }

        return 'failed';
    }

    /** Code d'erreur FCM détaillé (`details[].errorCode`), sinon statut gRPC (`error.status`). */
    private static function errorCode(HttpResponse $response): ?string
    {
        $error = $response->json()['error'] ?? null;
        if (!is_array($error)) {
            return null;
        }
        foreach (($error['details'] ?? []) as $detail) {
            if (is_array($detail) && isset($detail['errorCode']) && is_string($detail['errorCode'])) {
                return $detail['errorCode'];
            }
        }

        return isset($error['status']) && is_string($error['status']) ? $error['status'] : null;
    }

    private static function errorTargetsToken(HttpResponse $response): bool
    {
        $error = $response->json()['error'] ?? [];
        foreach (($error['details'] ?? []) as $detail) {
            foreach ((is_array($detail) ? ($detail['fieldViolations'] ?? []) : []) as $violation) {
                if (is_array($violation) && ($violation['field'] ?? '') === 'message.token') {
                    return true;
                }
            }
        }
        $message = is_array($error) ? (string) ($error['message'] ?? '') : '';

        return stripos($message, 'registration token') !== false;
    }
}
