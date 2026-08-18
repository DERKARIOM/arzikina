package com.arzikina.ne.domain.model

/**
 * Une dépense prévue au sein d'une [FinancialPlan] (voir cahier des charges "Planification
 * financière", sections 3/9/10/13) — voir la doc de [FinancialPlan] pour le principe fondamental
 * "prévision ≠ transaction réelle".
 *
 * @param planId la [FinancialPlan] parente — supprimer celle-ci supprime en cascade toutes ses
 * dépenses prévues (voir `data/local/entity/FinancialPlanItemEntity`, `ON DELETE CASCADE`).
 * @param amount montant PRÉVU, en unité mineure — TOUJOURS renseigné.
 * @param actualAmount montant RÉELLEMENT dépensé (voir cahier des charges, section 13) —
 * optionnel pour cette première version, `null` tant que non renseigné (typiquement au moment de
 * la conversion en transaction réelle, voir [transactionId]). Permet d'afficher une "économie"
 * (`amount - actualAmount`) sans obliger l'utilisateur à la saisir.
 * @param categoryId catégorie optionnelle (voir cahier des charges, section 9 : "réutiliser les
 * catégories existantes lorsque cela est pertinent") — `null` autorisé, AUCUNE contrainte
 * `ON DELETE` particulière (voir `FinancialPlanItemEntity`, même raisonnement que
 * [Transaction.categoryId] : `NO_ACTION`, la suppression d'une catégorie encore référencée est
 * refusée par SQLite plutôt que de silencieusement orpheliner cette dépense prévue).
 * @param priority voir [PlanItemPriority].
 * @param status voir [PlanItemStatus].
 * @param transactionId voir cahier des charges, section 12 ("Convertir une dépense prévue en
 * transaction") : `null` tant qu'aucune [Transaction] réelle n'a été créée à partir de cette
 * dépense prévue. Une fois renseigné, POINTE vers cette transaction — SANS contrainte `ForeignKey`
 * SQL (même raisonnement que [Transaction.feeTransactionId] : lien secondaire entre deux tables,
 * la cohérence de création/suppression conjointe sera gérée par le repository à l'étape qui
 * implémentera cette conversion, pas par SQLite).
 * @param id 0L tant que la dépense prévue n'a pas encore été enregistrée en base.
 */
data class FinancialPlanItem(
    val id: Long = 0L,
    val planId: Long,
    val name: String,
    val amount: Long,
    val actualAmount: Long? = null,
    val categoryId: Long? = null,
    val description: String? = null,
    val plannedDate: Long? = null,
    val priority: PlanItemPriority = PlanItemPriority.IMPORTANT,
    val status: PlanItemStatus = PlanItemStatus.TO_PLAN,
    val transactionId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
