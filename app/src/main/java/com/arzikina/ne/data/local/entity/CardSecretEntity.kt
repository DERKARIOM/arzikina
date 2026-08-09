package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Numéro complet et CVV d'une carte de crédit, CHIFFRÉS (voir `data/security/CardCipher`) — table
 * séparée de [AccountEntity] plutôt que des colonnes supplémentaires dessus : ces données sont
 * d'une sensibilité fondamentalement différente (voir `domain/model/AccountType.CREDIT_CARD`) et
 * ne doivent JAMAIS transiter par les mêmes chemins que le reste d'un compte (mapper vers le
 * domaine, sauvegarde JSON — voir `data/backup/BackupMappers`, qui ne connaît volontairement PAS
 * cette table) — les isoler structurellement rend une fuite accidentelle par ces chemins
 * impossible plutôt que de compter sur la vigilance de chaque futur développeur.
 *
 * `accountId` est la clé primaire (relation 1:1, une carte a au plus un secret enregistré) ET une
 * clé étrangère CASCADE vers `accounts` : supprimer un compte efface automatiquement son secret
 * (voir section sécurité, "efface-le lorsque la carte est supprimée") sans code de nettoyage à
 * écrire ni à oublier — possible dès la création de cette table (contrairement à `AccountEntity`
 * vers `users`, voir `MIGRATION_6_7`, qui a dû s'en passer pour une contrainte ajoutée après coup).
 */
@Entity(
    tableName = "card_secrets",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CardSecretEntity(
    @PrimaryKey val accountId: Long,
    val cardNumberEncrypted: String,
    val cardNumberIv: String,
    val cardCvvEncrypted: String,
    val cardCvvIv: String
)
