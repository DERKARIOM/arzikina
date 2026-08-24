package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.model.SyncStatus

/**
 * File d'attente locale des écritures en attente d'envoi au serveur — l'"outbox" du modèle
 * offline-first (voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 8). Chaque écriture d'un
 * repository (créer/modifier/supprimer une transaction, un compte...) est censée enfiler ICI une
 * entrée, EN PLUS de son écriture Room habituelle dans sa propre table.
 *
 * ÉTAPE ACTUELLE — cette table existe mais RIEN ne l'alimente ni ne la vide encore : aucun
 * repository n'enfile d'entrée, aucun Sync Engine ne la lit. C'est une fondation posée à l'avance
 * (voir instructions du projet : "prévois dès maintenant une architecture compatible avec la
 * synchronisation cloud"), le câblage réel viendra dans une étape ultérieure, une fois le client
 * réseau et le stockage sécurisé du token en place. Zéro impact visible pour l'instant.
 *
 * [entitySyncId] référence le `syncId` (UUID additif, voir [AccountEntity.syncId] et les entités
 * équivalentes ajoutées par `MIGRATION_22_23`) de la ligne concernée — JAMAIS son `id` Room local,
 * qui n'a de sens que sur CET appareil et ne doit jamais traverser le réseau.
 *
 * [payloadJson] est un instantané complet de l'entité au moment de l'enfilage (sérialisé en JSON
 * via `kotlinx.serialization`, déjà une dépendance du projet — voir le système de
 * sauvegarde/restauration existant, `data/backup/BackupDto.kt`) : le futur Sync Engine envoie ce
 * JSON tel quel à `server/api/sync/push.php`, sans avoir besoin de relire la ligne d'origine (qui a
 * pu être modifiée à nouveau entre-temps).
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index("status"),
        Index(value = ["entityType", "entitySyncId"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** Nom technique de la table concernée (ex. "categories", "transactions" — voir le tableau de
     * correspondance section 4 du document ci-dessus). Une chaîne plutôt qu'un enum : évite de
     * faire dépendre cette table d'une énumération à mettre à jour à chaque nouvelle entité rendue
     * synchronisable. */
    val entityType: String,
    val entitySyncId: String,
    val operation: SyncOperation,
    val payloadJson: String,
    val createdAt: Long,
    /** Nombre de tentatives d'envoi déjà échouées — base du recul exponentiel du futur Sync Engine. */
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val status: SyncStatus,
    /** Message d'erreur COURT de la dernière tentative échouée (diagnostic), `null` tant qu'aucune
     * tentative n'a échoué — jamais affiché tel quel à l'utilisateur (voir cahier des charges,
     * section sécurité : pas de détail technique brut exposé). */
    val errorMessage: String? = null
)
