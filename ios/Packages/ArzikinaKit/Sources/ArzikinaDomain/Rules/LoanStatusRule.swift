import Foundation

/// Statut réel d'un prêt/emprunt — portage d'Android `computeLoanStatus`.
///
/// Ordre des règles :
/// 0. transformé en cadeau (`giftedAmount > 0`) → `.gifted`, prioritaire sur « remboursé » ;
/// 1. remboursé (`amountRepaid ≥ amount`) → `.repaid`, même après l'échéance ;
/// 2. avant l'instant de début → `.upcoming` ;
/// 3. à partir du LENDEMAIN du jour d'échéance → `.overdue` (le jour même reste « en cours ») ;
/// 4. sinon → `.ongoing`.
public enum LoanStatusRule {

    public static func status(
        amount: MinorUnits,
        amountRepaid: MinorUnits,
        startDate: EpochMillis,
        dueDate: EpochMillis,
        now: EpochMillis,
        calendar: Calendar,
        giftedAmount: MinorUnits = 0
    ) -> LoanStatus {
        if giftedAmount > 0 { return .gifted }
        if amountRepaid >= amount { return .repaid }
        if now < startDate { return .upcoming }
        if CalendarDay(epochMillis: now, calendar: calendar) > CalendarDay(epochMillis: dueDate, calendar: calendar) {
            return .overdue
        }
        return .ongoing
    }

    /// Statut recalculé d'un [loan] à l'instant [now].
    public static func status(of loan: Loan, now: EpochMillis, calendar: Calendar) -> LoanStatus {
        status(
            amount: loan.amount,
            amountRepaid: loan.amountRepaid,
            startDate: loan.startDate,
            dueDate: loan.dueDate,
            now: now,
            calendar: calendar,
            giftedAmount: loan.giftedAmount
        )
    }
}
