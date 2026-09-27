package com.naniger.arzikina.presentation.utilities.marketplace

import com.naniger.arzikina.domain.model.Transaction
import com.naniger.arzikina.domain.model.TransactionType

/**
 * Champs d'un modèle préremplis à partir d'une transaction (« Créer un modèle à partir de cette
 * transaction ») — voir [TemplateFromTransaction.prefillOf]. Montant en unités mineures ;
 * [categoryId] `0L` = aucune catégorie (l'utilisateur devra en choisir une, comme pour tout modèle).
 */
data class TemplatePrefill(
    val name: String,
    val type: TransactionType,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val description: String
)

/**
 * Règles PURES (sans Android, testées par `TemplateFromTransactionTest`) de « Créer un modèle à
 * partir de cette transaction ». Même logique à reproduire côté Web (voir
 * `docs/MODELE-DEPUIS-TRANSACTION.md`).
 */
object TemplateFromTransaction {

    /**
     * Une transaction peut servir de base à un modèle seulement si un modèle peut la représenter :
     * - jamais un TRANSFERT (un modèle est une dépense ou un revenu, voir `TransactionTemplate`) ;
     * - jamais une ligne de FRAIS elle-même ([Transaction.feeType] non nul) : elle n'existe qu'en
     *   accompagnement de sa transaction parente.
     * Le lien éventuel avec un prêt/emprunt et l'existence d'un modèle déjà créé sont vérifiés par
     * l'appelant (ils dépendent d'autres données que la transaction seule).
     */
    fun isEligible(transaction: Transaction): Boolean =
        transaction.type != TransactionType.TRANSFER && transaction.feeType == null

    /**
     * Ne recopie QUE ce qui a du sens pour un modèle : type, montant, catégorie, compte,
     * description. Jamais la date/l'heure (un modèle s'applique « aujourd'hui », voir
     * `TransactionTemplate.defaultHour`, laissé désactivé), ni l'id, les horodatages, le reçu, le
     * mode de paiement, la position, les frais liés (transaction séparée) ou les champs de
     * synchronisation : le modèle est une entité NOUVELLE et indépendante.
     *
     * Nom proposé : la description (ex. « Courses du mois »), à défaut le nom de la catégorie
     * ([categoryName], déjà localisé par l'appelant), à défaut vide — toujours modifiable avant
     * d'enregistrer.
     */
    fun prefillOf(transaction: Transaction, categoryName: String?): TemplatePrefill {
        val description = transaction.description.trim()
        return TemplatePrefill(
            name = description.ifEmpty { categoryName?.trim().orEmpty() }.take(MAX_NAME_LENGTH),
            type = transaction.type,
            amount = transaction.amount,
            categoryId = transaction.categoryId ?: 0L,
            accountId = transaction.accountId,
            description = description
        )
    }

    /** Le nom d'un modèle est un libellé court affiché sur sa carte : une longue description n'y
     * est reprise qu'en partie (la description complète, elle, est conservée telle quelle). */
    const val MAX_NAME_LENGTH = 60
}
