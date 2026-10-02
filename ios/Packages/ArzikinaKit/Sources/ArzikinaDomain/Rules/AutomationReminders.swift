import Foundation

/// Rappel à l'heure de déclenchement d'une échéance — l'équivalent iOS de l'alarme Android
/// (`AutomationScheduler` / `AutomationAlarmReceiver`).
public struct AutomationReminder: Equatable, Hashable, Sendable {
    /// Identifiant stable (règle + jour) : reprogrammer ne crée jamais de doublon.
    public let id: String
    public let ruleId: EntityID
    /// Jour de l'échéance (début de journée locale).
    public let scheduledDate: EpochMillis
    /// Instant exact du rappel (jour de l'échéance à l'heure de la règle).
    public let fireAt: EpochMillis

    public init(ruleId: EntityID, scheduledDate: EpochMillis, fireAt: EpochMillis) {
        id = "\(AutomationReminders.identifierPrefix)\(ruleId).\(scheduledDate)"
        self.ruleId = ruleId
        self.scheduledDate = scheduledDate
        self.fireAt = fireAt
    }
}

/// Rappels à programmer pour les automatisations.
///
/// Différence de fond avec Android : iOS n'exécute pas de code à l'heure d'un rappel (pas
/// d'équivalent fiable d'`AlarmManager` + `BroadcastReceiver`). On programme donc À L'AVANCE les
/// prochains rappels de toutes les règles actives, et on recalcule la liste à chaque changement
/// (règle créée, modifiée, mise en pause, supprimée, synchronisée) et à chaque retour de l'app.
/// iOS limitant une app à 64 rappels en attente, seuls les [limit] plus proches sont programmés.
public enum AutomationReminders {

    /// Préfixe des identifiants : permet de ne retirer QUE les rappels d'automatisation.
    public static let identifierPrefix = "automation."

    /// Sous la limite iOS (64) : de la marge pour d'autres rappels à l'avenir.
    public static let defaultLimit = 60

    /// Garde-fou pour une règle quotidienne restée longtemps sans ouverture de l'app.
    static let maxIterationsPerRule = 20_000

    /// Prochains rappels STRICTEMENT après [now], du plus proche au plus lointain : règles actives
    /// seulement, jusqu'à leur date de fin (incluse), une seule fois pour `.once`.
    public static func upcoming(
        rules: [RecurringTransaction],
        after now: EpochMillis,
        limit: Int = defaultLimit,
        calendar: Calendar
    ) -> [AutomationReminder] {
        guard limit > 0 else { return [] }
        var reminders: [AutomationReminder] = []
        for rule in rules where rule.isActive {
            reminders += upcoming(for: rule, after: now, limit: limit, calendar: calendar)
        }
        return Array(reminders.sorted { ($0.fireAt, $0.ruleId) < ($1.fireAt, $1.ruleId) }.prefix(limit))
    }

    private static func upcoming(for rule: RecurringTransaction, after now: EpochMillis, limit: Int, calendar: Calendar) -> [AutomationReminder] {
        let endDay = rule.endDate.map { CalendarDay(epochMillis: $0, calendar: calendar) }
        var reminders: [AutomationReminder] = []
        var candidate: EpochMillis? = rule.nextExecutionDate
        var iterations = 0
        while let day = candidate, reminders.count < limit, iterations < maxIterationsPerRule {
            iterations += 1
            if let endDay, CalendarDay(epochMillis: day, calendar: calendar) > endDay { break }
            let fireAt = Recurrence.triggerInstant(day: day, hour: rule.triggerHour, minute: rule.triggerMinute, calendar: calendar)
            // Une échéance déjà passée sera créée à la prochaine ouverture : pas de rappel.
            if fireAt > now {
                reminders.append(AutomationReminder(ruleId: rule.id, scheduledDate: day, fireAt: fireAt))
            }
            candidate = Recurrence.nextExecutionDate(after: day, frequency: rule.frequency, calendar: calendar)
        }
        return reminders
    }
}
