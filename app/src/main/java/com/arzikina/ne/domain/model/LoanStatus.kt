package com.arzikina.ne.domain.model

import java.time.Instant
import java.time.ZoneId

/**
 * Statut d'un [Loan] — TOUJOURS recalculé (voir [computeLoanStatus]), jamais saisi
 * manuellement, mais PERSISTÉ (dénormalisé) sur [Loan.status] pour permettre des requêtes SQL
 * filtrées rapides (ex. "combien de prêts en retard", voir la future étape Statistiques) sans
 * recharger et recalculer chaque prêt en mémoire.
 *
 * Recalculé à chaque écriture (création, remboursement) — voir `LoanRepository` — donc fiable
 * juste après une action utilisateur. Peut devenir périmé UNIQUEMENT par le simple écoulement du
 * temps (ex. passage de [ONGOING] à [OVERDUE] sans qu'aucune action n'ait eu lieu) : les écrans
 * de lecture doivent donc idéalement re-vérifier via [computeLoanStatus] à l'affichage plutôt que
 * de faire confiance aveuglément à la valeur stockée (voir les étapes Écran principal/Détail).
 */
enum class LoanStatus {
    /** Entre [Loan.startDate] et [Loan.dueDate], solde restant > 0. */
    ONGOING,

    /** [Loan.remainingAmount] == 0 — statut final, ne redevient jamais un autre statut. */
    REPAID,

    /** [Loan.dueDate] dépassée, solde restant > 0. */
    OVERDUE,

    /** [Loan.startDate] pas encore atteinte (prêt/emprunt convenu mais pas encore débuté). */
    UPCOMING
}

/**
 * Calcule le statut réel d'un prêt/emprunt à l'instant [nowEpochMillis], à partir de ses seules
 * données (aucun accès Room/réseau) — fonction pure, testable isolément, même principe que
 * [com.arzikina.ne.util.CardInputFormatter]. [REPAID] est vérifié EN PREMIER : un prêt totalement
 * remboursé reste [REPAID] même si sa date d'échéance est dépassée (le retard n'a plus de sens
 * une fois la dette éteinte).
 *
 * Recalculée à CHAQUE affichage (voir les écrans `presentation/utilities/loans`), pas seulement à
 * l'écriture : sinon un prêt qui franchit son échéance sans aucune action utilisateur resterait
 * affiché [ONGOING] indéfiniment, la valeur persistée sur [Loan.status] ne servant alors qu'à
 * accélérer des requêtes SQL filtrées (voir la doc de [LoanStatus]).
 */
fun computeLoanStatus(
    amount: Long,
    amountRepaid: Long,
    startDate: Long,
    dueDate: Long,
    nowEpochMillis: Long
): LoanStatus = when {
    amountRepaid >= amount -> LoanStatus.REPAID
    nowEpochMillis < startDate -> LoanStatus.UPCOMING
    isPastDueDay(dueDate, nowEpochMillis) -> LoanStatus.OVERDUE
    else -> LoanStatus.ONGOING
}

/**
 * Compare des JOURS calendaires, pas des millisecondes brutes : un prêt reste [LoanStatus.ONGOING]
 * pendant toute la journée de son échéance et ne devient [LoanStatus.OVERDUE] qu'à partir du
 * lendemain. Sans cette distinction, un prêt échéant "aujourd'hui" s'afficherait en retard dès
 * 00h01 le jour même (voir [Loan.dueDate], normalisée à minuit local à la saisie — voir
 * `LoanFormFragment.showDatePicker`), ce qui serait incohérent avec [LoanStatus.UPCOMING] qui, lui,
 * traite déjà le jour de [Loan.startDate] comme débuté.
 */
private fun isPastDueDay(dueDate: Long, nowEpochMillis: Long): Boolean {
    val zone = ZoneId.systemDefault()
    val dueDay = Instant.ofEpochMilli(dueDate).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
    return today.isAfter(dueDay)
}
