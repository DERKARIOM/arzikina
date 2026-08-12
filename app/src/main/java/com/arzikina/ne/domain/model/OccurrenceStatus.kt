package com.arzikina.ne.domain.model

/**
 * Statut d'une [RecurringTransactionOccurrence] — TOUJOURS écrit explicitement par
 * `RecurringTransactionRepositoryImpl` au moment où l'utilisateur agit (voir sa doc), jamais
 * recalculé à la lecture (contrairement à [LoanStatus] : une occurrence n'a pas d'état "dérivable"
 * de la date, seule sa génération initiale en [PENDING] dépend de la date d'échéance).
 */
enum class OccurrenceStatus {
    /** Échéance atteinte (ou dépassée, voir la génération des occurrences manquées), en attente
     * d'une décision de l'utilisateur (Enregistrer/Modifier/Rejeter). */
    PENDING,
    /** Enregistrée telle quelle : une [Transaction] a été créée à partir des valeurs de la règle. */
    ACCEPTED,
    /** Enregistrée après modification ponctuelle (montant/compte/catégorie/date/description) : une
     * [Transaction] a été créée à partir des valeurs modifiées, SANS changer la règle d'origine
     * (voir `RecurringTransaction`), sauf si l'utilisateur choisit explicitement de la modifier. */
    MODIFIED,
    /** Refusée : aucune [Transaction] créée, ne sera plus jamais proposée. */
    REJECTED
}
