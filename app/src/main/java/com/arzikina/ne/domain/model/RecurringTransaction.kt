package com.arzikina.ne.domain.model

/**
 * La RÈGLE qui décrit une transaction à reconduire (voir [RecurringTransactionOccurrence] pour son
 * exécution réelle à une date donnée — même séparation modèle/occurrence que [Loan]/[LoanPayment]).
 *
 * Indépendant des [Loan] (voir la doc de `RecurringTransactionRepository`) : une mensualité de
 * remboursement automatique n'est PAS modélisée ici pour l'instant, ce type de règle reste créée
 * manuellement par l'utilisateur comme n'importe quelle autre transaction planifiée.
 *
 * Devise/compte : mêmes choix que [Transaction]/[Loan] — pas de champ devise dédié, entièrement
 * hérité du compte associé ([accountId], voir [Account.currencyCode]).
 *
 * @param categoryId `null` uniquement réservé à un futur type transfert (voir
 * [Transaction.categoryId] pour le même raisonnement), toujours renseigné pour un revenu/une
 * dépense.
 * @param startDate première échéance de la règle (jour calendaire, normalisé à minuit local à la
 * saisie — même convention que [Loan.startDate]/[Loan.dueDate]).
 * @param endDate `null` = pas de date de fin, la règle se reconduit indéfiniment tant que
 * [isActive].
 * @param frequency voir [RecurringFrequency] ; [RecurringFrequency.ONCE] ne génère jamais de
 * seconde occurrence (voir [computeNextExecutionDate]).
 * @param nextExecutionDate date de la PROCHAINE occurrence à générer — voir
 * [generateMissingScheduledDates], TOUJOURS avancée par `RecurringTransactionRepository` après
 * génération, jamais recalculée depuis [startDate] à chaque lecture.
 * @param isActive `false` = la règle ne génère plus de nouvelles occurrences (arrêtée par
 * l'utilisateur), mais son historique reste consultable — jamais supprimée pour autant.
 * @param triggerHour heure locale (0-23) de déclenchement (voir cahier des charges "Ajouter l'heure
 * de déclenchement à Automatisation"). Toujours renseigné (jamais `null`) : les règles créées avant
 * cette fonctionnalité reçoivent `8` par défaut via la migration, plutôt qu'une valeur nullable qui
 * compliquerait chaque calcul/programmation en aval — voir [triggerMinute].
 * @param triggerMinute minute locale (0-59) de déclenchement, même raisonnement que [triggerHour]
 * (défaut `0`, soit 08:00 pour les règles préexistantes).
 * @param id 0L tant que la règle n'a pas encore été enregistrée en base.
 */
data class RecurringTransaction(
    val id: Long = 0L,
    val type: TransactionType,
    val amount: Long,
    val accountId: Long,
    val categoryId: Long?,
    val description: String = "",
    val paymentMethod: PaymentMethod? = null,
    val startDate: Long,
    val endDate: Long? = null,
    val frequency: RecurringFrequency,
    val nextExecutionDate: Long,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val triggerHour: Int = DEFAULT_TRIGGER_HOUR,
    val triggerMinute: Int = DEFAULT_TRIGGER_MINUTE
) {
    companion object {
        /** Valeur de repli pour toute règle créée avant l'ajout de l'heure de déclenchement (voir
         * la migration Room correspondante) — 08:00, choix explicite du cahier des charges. */
        const val DEFAULT_TRIGGER_HOUR = 8
        const val DEFAULT_TRIGGER_MINUTE = 0
    }
}
