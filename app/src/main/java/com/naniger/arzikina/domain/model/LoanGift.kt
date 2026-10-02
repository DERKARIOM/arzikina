package com.naniger.arzikina.domain.model

/**
 * « Transformer un prêt/emprunt en cadeau » — règles métier PURES (aucun accès Room/réseau),
 * partagées par la couche data (`LoanRepositoryImpl.convertToGift`) et la présentation (affichage
 * de l'action) pour qu'une seule source de vérité décide de ce qui est permis.
 *
 * PRINCIPE COMPTABLE (voir `docs` du projet, « AUDIT-PRET-EN-CADEAU ») : l'argent du prêt/emprunt
 * est DÉJÀ entré/sorti du solde via sa transaction de décaissement. La transformation ne crée donc
 * AUCUN nouveau mouvement d'argent : elle RECLASSE la part non remboursée dans la catégorie
 * « Cadeaux ». Règle unique :
 * - le décaissement garde la part déjà remboursée ([LoanGiftPlan.disbursementAmountAfter]) ;
 * - la transaction cadeau porte la part restante ([LoanGiftPlan.giftAmount]).
 * Leur somme reste égale à [Loan.amount] : solde, total des revenus et total des dépenses sont
 * strictement inchangés, seule la répartition par catégorie évolue.
 */

/**
 * `true` si l'action « Transformer en cadeau » est permise à [nowEpochMillis] : dette ni
 * remboursée ni déjà transformée (voir [isSettled]), et un reste strictement positif.
 * Les statuts [LoanStatus.ONGOING], [LoanStatus.OVERDUE] et [LoanStatus.UPCOMING] sont acceptés.
 */
fun Loan.canConvertToGift(nowEpochMillis: Long): Boolean =
    giftedAmount == 0L && outstandingAmount() > 0L && !liveStatus(nowEpochMillis).isSettled

/**
 * Reste dû recalculé à partir des montants sources (`amount - amountRepaid - giftedAmount`), plutôt
 * que lu sur [Loan.remainingAmount] (dénormalisé) : la transformation ne doit jamais s'appuyer sur
 * une valeur dérivée potentiellement désynchronisée.
 */
fun Loan.outstandingAmount(): Long = amount - amountRepaid - giftedAmount

/**
 * Résultat du calcul de la transformation (voir la doc de ce fichier).
 *
 * @param giftAmount montant de la transaction cadeau (= reste dû).
 * @param disbursementAmountAfter nouveau montant de la transaction de décaissement (= part déjà
 * remboursée). 0 quand rien n'avait été remboursé.
 * @param reusesDisbursementTransaction `true` quand rien n'avait été remboursé : la transaction de
 * décaissement est alors RECLASSÉE sur place (même id, même `syncId`) au lieu d'être réduite à 0 et
 * doublée d'une nouvelle ligne — aucune transaction à 0 F, aucun doublon possible en
 * synchronisation.
 */
data class LoanGiftPlan(
    val giftAmount: Long,
    val disbursementAmountAfter: Long,
    val reusesDisbursementTransaction: Boolean
)

/**
 * Calcule la transformation de ce prêt/emprunt (voir [LoanGiftPlan]).
 *
 * @throws LoanGiftException.NotConvertible si [canConvertToGift] est faux à [nowEpochMillis].
 */
fun Loan.planGiftConversion(nowEpochMillis: Long): LoanGiftPlan {
    if (!canConvertToGift(nowEpochMillis)) throw LoanGiftException.NotConvertible(id)
    val giftAmount = outstandingAmount()
    val disbursementAmountAfter = amount - giftAmount
    return LoanGiftPlan(
        giftAmount = giftAmount,
        disbursementAmountAfter = disbursementAmountAfter,
        reusesDisbursementTransaction = disbursementAmountAfter == 0L
    )
}

/**
 * Catégorie système de la transaction cadeau : « Cadeaux » en DÉPENSE pour un prêt accordé
 * (l'utilisateur a offert l'argent), « Cadeaux » en REVENU pour un emprunt (l'utilisateur a reçu
 * l'argent en cadeau).
 */
val LoanType.giftCategoryKey: SystemCategoryKey
    get() = when (this) {
        LoanType.LENT -> SystemCategoryKey.GIFTS
        LoanType.BORROWED -> SystemCategoryKey.GIFTS_RECEIVED
    }

/**
 * Type de la transaction cadeau — TOUJOURS celui du décaissement (dépense pour un prêt, revenu pour
 * un emprunt), puisqu'elle en est la part reclassée. Déduit de [giftCategoryKey] pour qu'une seule
 * table décide des deux.
 */
val LoanType.giftTransactionType: TransactionType
    get() = giftCategoryKey.type
