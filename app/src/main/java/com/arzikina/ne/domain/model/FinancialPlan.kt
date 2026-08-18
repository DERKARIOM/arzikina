package com.arzikina.ne.domain.model

/**
 * Une planification financière par projet (voir cahier des charges "Nouvelle fonctionnalité :
 * Planification financière") — répond à "j'ai combien d'argent disponible, quelles dépenses dois-
 * je prévoir et combien va-t-il me rester ?". Fonctionnalité INDÉPENDANTE de "Automatisation"
 * (ex-"Planification", transactions récurrentes, voir [RecurringTransaction]) : ne partage AUCUNE
 * logique ni donnée avec elle.
 *
 * IMPORTANT — [availableAmount] et le total de [FinancialPlanItem.amount] associés sont PUREMENT
 * PRÉVISIONNELS : créer/modifier/supprimer une [FinancialPlanItem] NE crée JAMAIS de
 * [Transaction] réelle, ne débite AUCUN [Account], et n'alimente JAMAIS les statistiques (voir
 * cahier des charges, sections 11/19). Seule une conversion EXPLICITE d'une dépense prévue en
 * transaction (voir [FinancialPlanItem.transactionId]) crée une vraie transaction.
 *
 * @param availableAmount montant disponible pour ce projet, en unité mineure de la devise par
 * défaut de l'utilisateur (voir [com.arzikina.ne.util.Constants.DEFAULT_CURRENCY_CODE]) — AUCUNE
 * notion de compte ici (contrairement à [Loan]) : une planification n'est reliée à aucun compte
 * précis, c'est une enveloppe budgétaire abstraite que l'utilisateur alimente mentalement.
 * @param targetAmount objectif financier optionnel (voir cahier des charges, section 17) —
 * `null` tant que l'utilisateur n'en définit pas. Architecture prête pour l'affichage d'une
 * progression "X % atteint" dès qu'une étape future construira cet écran.
 * @param periodType voir [PlanPeriodType] ; [startDate]/[endDate] n'ont de sens que si
 * [periodType] est différent de [PlanPeriodType.NONE].
 * @param status voir [PlanStatus] — saisi/changé par l'utilisateur, jamais recalculé.
 * @param id 0L tant que la planification n'a pas encore été enregistrée en base.
 */
data class FinancialPlan(
    val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val availableAmount: Long,
    val targetAmount: Long? = null,
    val periodType: PlanPeriodType = PlanPeriodType.NONE,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val icon: FinancialPlanIcon = FinancialPlanIcon.WALLET,
    val colorArgb: Long = 0xFF42B998L,
    val status: PlanStatus = PlanStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long
)
