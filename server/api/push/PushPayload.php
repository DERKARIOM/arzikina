<?php

declare(strict_types=1);

require_once __DIR__ . '/../utils/uuid.php';

/**
 * Contenu « data » d'une notification push, format v1 (voir claude/fcm/AUDIT-FCM-ANDROID.md, 4.2).
 *
 * Règles imposées ici, en un seul endroit, pour TOUS les envois :
 * - aucun titre, aucun texte, aucun montant, aucun nom : l'application compose la notification à
 *   partir de sa base locale et de ses traductions FR/EN ;
 * - `uid` = utilisateur destinataire : l'application ignore le message si un autre compte (ou
 *   aucun) est connecté — protection contre une déconnexion qui n'aurait pas atteint le serveur ;
 * - `entity_id` = UUID serveur (`syncId`), jamais l'identifiant local Long d'Android ;
 * - `event_id` unique par événement : l'application peut écarter un message reçu deux fois ;
 * - FCM n'accepte que des chaînes dans `data` : tout est converti ici.
 *
 * Ajouter un type = ajouter une constante et l'entrée correspondante dans `TYPES`, puis le gérer
 * côté application. Un type inconnu de l'application est ignoré par elle, sans planter.
 */
final class PushPayload
{
    public const VERSION = '1';

    /** Message d'information général (annonce, test d'envoi). Aucune entité. */
    public const TYPE_SYSTEM_MESSAGE = 'SYSTEM_MESSAGE';
    /** Échéance d'un prêt ou d'un emprunt (étape 4 : cron serveur). Entité `loan`. */
    public const TYPE_LOAN_DUE = 'LOAN_DUE';
    /** Événement de sécurité du compte (nouvelle connexion…). Aucune entité. */
    public const TYPE_SECURITY_ALERT = 'SECURITY_ALERT';

    /** Type => type d'entité attendu (null = aucune entité). */
    private const TYPES = [
        self::TYPE_SYSTEM_MESSAGE => null,
        self::TYPE_LOAN_DUE => 'loan',
        self::TYPE_SECURITY_ALERT => null,
    ];

    private function __construct(
        public readonly string $type,
        public readonly ?string $entityType,
        public readonly ?string $entityId,
        public readonly string $eventId,
        public readonly int $sentAtMillis,
    ) {
    }

    /**
     * @param string|null $entityId UUID serveur de l'entité, obligatoire si le type en attend une
     * @throws InvalidArgumentException type inconnu ou entité incohérente avec le type
     */
    public static function create(string $type, ?string $entityId = null, ?int $sentAtMillis = null): self
    {
        if (!array_key_exists($type, self::TYPES)) {
            throw new InvalidArgumentException("Type de notification inconnu : $type");
        }

        $entityType = self::TYPES[$type];
        if ($entityType === null && $entityId !== null) {
            throw new InvalidArgumentException("Le type $type ne porte pas d'entité.");
        }
        if ($entityType !== null && ($entityId === null || !isValidUuid($entityId))) {
            throw new InvalidArgumentException("Le type $type exige l'UUID de l'entité $entityType.");
        }

        return new self(
            $type,
            $entityType,
            $entityId === null ? null : strtolower($entityId),
            generateUuidV4(),
            $sentAtMillis ?? (int) round(microtime(true) * 1000),
        );
    }

    /**
     * Bloc `data` FCM pour un destinataire donné (toutes les valeurs en chaînes ; les clés sans
     * valeur sont omises plutôt qu'envoyées vides).
     *
     * @return array<string, string>
     */
    public function toData(string $recipientUserId): array
    {
        $data = [
            'v' => self::VERSION,
            'type' => $this->type,
            'uid' => $recipientUserId,
            'event_id' => $this->eventId,
            'sent_at' => (string) $this->sentAtMillis,
        ];
        if ($this->entityType !== null && $this->entityId !== null) {
            $data['entity_type'] = $this->entityType;
            $data['entity_id'] = $this->entityId;
        }

        return $data;
    }
}
