package com.arzikina.ne.domain.model

/**
 * Sens de l'argent pour un [Loan] : qui a prêté à qui.
 *
 * [LENT] : l'utilisateur a prêté de l'argent à [Person] (l'argent QUITTE un compte Arzikina —
 * voir [Loan.accountId] — et [Person] doit le rembourser).
 * [BORROWED] : l'utilisateur a emprunté de l'argent à [Person] (l'argent ENTRE sur un compte
 * Arzikina, et c'est l'utilisateur qui doit le rembourser).
 *
 * Détermine le sens des transactions générées automatiquement (voir la doc de [Loan] et
 * `domain/repository/LoanRepository`, étape "Synchronisation avec les transactions").
 */
enum class LoanType {
    LENT,
    BORROWED
}
