import Foundation

/// Calcul des échéances d'une automatisation — portage d'Android `RecurringFrequency.kt` et
/// `RecurringTransactionTriggerTime.kt`.
///
/// Les échéances sont des JOURS (début de journée locale) ; l'heure de déclenchement de la règle
/// ne sert qu'à décider si une échéance est déjà due.
public enum Recurrence {

    /// Plafond d'occurrences générées en un appel (règle inactive depuis très longtemps) : le reste
    /// sera généré aux ouvertures suivantes, sans ralentir le démarrage.
    public static let maxGeneratedOccurrencesPerCall = 500

    /// Jour de l'échéance suivant [current] (début de journée), `nil` pour `.once`. Les mois sont
    /// civils : le 31 janvier + 1 mois donne le 28 (ou 29) février, comme sur Android.
    public static func nextExecutionDate(after current: EpochMillis, frequency: RecurringFrequency, calendar: Calendar) -> EpochMillis? {
        let day = CalendarDay(epochMillis: current, calendar: calendar)
        let next: CalendarDay
        switch frequency {
        case .once: return nil
        case .daily: next = day.adding(.day, 1, calendar: calendar)
        case .weekly: next = day.adding(.day, 7, calendar: calendar)
        case .biweekly: next = day.adding(.day, 14, calendar: calendar)
        case .monthly: next = day.adding(.month, 1, calendar: calendar)
        case .quarterly: next = day.adding(.month, 3, calendar: calendar)
        case .semiannual: next = day.adding(.month, 6, calendar: calendar)
        case .yearly: next = day.adding(.year, 1, calendar: calendar)
        }
        return next.startOfDayMillis(calendar: calendar)
    }

    /// Instant exact de déclenchement : le jour de [day] à [hour]:[minute].
    public static func triggerInstant(day: EpochMillis, hour: Int, minute: Int, calendar: Calendar) -> EpochMillis {
        CalendarDay(epochMillis: day, calendar: calendar).millis(hour: hour, minute: minute, calendar: calendar)
    }

    /// Échéances dues à transformer en occurrences `PENDING` : toutes celles dont l'instant de
    /// déclenchement est déjà passé à [now] (échéance du jour ET échéances manquées), en s'arrêtant
    /// au jour de [endDate] (inclus) et après une seule date pour `.once`.
    public static func missingScheduledDates(
        nextExecutionDate: EpochMillis,
        frequency: RecurringFrequency,
        endDate: EpochMillis?,
        now: EpochMillis,
        triggerHour: Int = 0,
        triggerMinute: Int = 0,
        calendar: Calendar
    ) -> [EpochMillis] {
        let endDay = endDate.map { CalendarDay(epochMillis: $0, calendar: calendar) }
        var dates: [EpochMillis] = []
        var candidate: EpochMillis? = nextExecutionDate
        while dates.count < maxGeneratedOccurrencesPerCall, let date = candidate {
            if triggerInstant(day: date, hour: triggerHour, minute: triggerMinute, calendar: calendar) > now { break }
            if let endDay, CalendarDay(epochMillis: date, calendar: calendar) > endDay { break }
            dates.append(date)
            candidate = frequency == .once ? nil : Recurrence.nextExecutionDate(after: date, frequency: frequency, calendar: calendar)
        }
        return dates
    }
}
