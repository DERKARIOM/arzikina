import ArzikinaDomain
import Foundation

// Libellés des automatisations, partagés par les écrans et les rappels (notifications).

extension RecurringTransaction {
    /// Description de la règle, sinon nom de la catégorie, sinon « Transaction automatique ».
    func displayTitle(category: ArzikinaDomain.Category? = nil) -> String {
        let trimmed = description.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { return trimmed }
        return category?.displayName ?? DomainDisplay.localized("automations.fallback_name")
    }
}

extension RecurringFrequency {
    var displayName: String {
        switch self {
        case .once: return DomainDisplay.localized("automations.frequency.once")
        case .daily: return DomainDisplay.localized("automations.frequency.daily")
        case .weekly: return DomainDisplay.localized("automations.frequency.weekly")
        case .biweekly: return DomainDisplay.localized("automations.frequency.biweekly")
        case .monthly: return DomainDisplay.localized("automations.frequency.monthly")
        case .quarterly: return DomainDisplay.localized("automations.frequency.quarterly")
        case .semiannual: return DomainDisplay.localized("automations.frequency.semiannual")
        case .yearly: return DomainDisplay.localized("automations.frequency.yearly")
        }
    }
}
