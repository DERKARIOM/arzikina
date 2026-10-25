package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Encaissement d'une vente sur UN compte. Une vente payée sur plusieurs comptes (V2) aura plusieurs
 * lignes ; chaque compte ne reçoit que sa part. Le montant encaissé d'une vente est la somme de ses
 * paiements.
 *
 * - [transactionId] : transaction `INCOME` (table `transactions`) créée par ce paiement — c'est elle
 *   qui crédite le solde du compte, via le mécanisme comptable EXISTANT (aucun système financier
 *   parallèle). Même pattern que `LoanPaymentEntity.transactionId`.
 * - [reversalTransactionId] : transaction `EXPENSE` correctrice créée à l'annulation de la vente ;
 *   `null` tant que la vente n'est pas annulée. La transaction d'origine n'est jamais effacée.
 * - [method] : NOM de `PaymentMethod` (texte, valeur inconnue → `OTHER`).
 *
 * Voir [ShopEntity] : pas de `ForeignKey`.
 */
@Entity(
    tableName = "sale_payments",
    indices = [
        Index("userId"),
        Index("saleId"),
        Index("accountId"),
        Index("transactionId"),
        Index(value = ["syncId"], unique = true)
    ]
)
data class SalePaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val saleId: Long,
    val accountId: Long,
    val method: String,
    val amountMinor: Long,
    val paidAt: Long,
    val transactionId: Long?,
    val reversalTransactionId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncId: String? = null,
    val deletedAt: Long? = null,
    val version: Int = 1
)
