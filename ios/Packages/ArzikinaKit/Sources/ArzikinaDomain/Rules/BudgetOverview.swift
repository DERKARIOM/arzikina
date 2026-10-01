import Foundation

/// Filtre de la liste des budgets — Android `BudgetStatusFilterOption`. Seul [all] montre les
/// budgets récurrents (ils n'ont pas de statut de période).
public enum BudgetStatusFilter: CaseIterable, Sendable {
    case all, upcoming, ongoing, completed

    public func matches(_ status: BudgetPeriodStatus?) -> Bool {
        switch self {
        case .all: return true
        case .upcoming: return status == .upcoming
        case .ongoing: return status == .ongoing
        case .completed: return status == .completed
        }
    }
}

/// Un budget, sa catégorie et sa progression sur sa période — Android `BudgetUiItem` + les
/// calculs de `BudgetModernAdapter` (statut affiché, jours, rythme), rassemblés ici pour que la
/// liste et le tableau de bord affichent exactement la même chose.
public struct BudgetSummary: Identifiable, Equatable, Sendable {

    /// Statut affiché : « Dépassé » l'emporte sur le statut de période (information la plus
    /// importante), un budget récurrent est toujours « En cours ».
    public enum DisplayStatus: Equatable, Sendable {
        case overspent, upcoming, ongoing, completed
    }

    public let budget: Budget
    public let category: Category?
    public let spent: MinorUnits
    /// Dépensé / plafond ; dépasse 1 en cas de dépassement.
    public let progress: Double
    /// `nil` pour un budget récurrent.
    public let periodStatus: BudgetPeriodStatus?
    public let pace: BudgetPace
    /// Jours avant le début (à venir) ou jusqu'à la fin (en cours, récurrent) ; 0 si terminé.
    public let days: Int

    public var id: EntityID { budget.id }
    public var remaining: MinorUnits { budget.limitAmount - spent }
    public var isOverspent: Bool { progress > 1 }
    /// Pourcentage arrondi (peut dépasser 100).
    public var percent: Int { Int((progress * 100).rounded()) }

    public var displayStatus: DisplayStatus {
        if isOverspent { return .overspent }
        switch periodStatus {
        case .upcoming: return .upcoming
        case .completed: return .completed
        case .ongoing, nil: return .ongoing
        }
    }

    /// Le rythme n'a de sens qu'une fois la période commencée.
    public var showsPace: Bool { pace.periodStatus != .upcoming }

    public init(budget: Budget, category: Category?, spent: MinorUnits, today: CalendarDay, calendar: Calendar) {
        self.budget = budget
        self.category = category
        self.spent = spent
        progress = budget.limitAmount > 0 ? Double(spent) / Double(budget.limitAmount) : 0
        periodStatus = BudgetPeriodStatus.of(budget: budget, today: today, calendar: calendar)
        pace = BudgetPace.of(budget: budget, spent: spent, today: today, calendar: calendar)
        switch pace.periodStatus {
        case .upcoming: days = max(today.days(to: pace.periodStart, calendar: calendar), 0)
        case .ongoing: days = max(today.days(to: pace.periodEnd, calendar: calendar), 0)
        case .completed: days = 0
        }
    }

    /// Budget mis en avant sur le tableau de bord : la progression la plus élevée (dépassement
    /// compris), comme Android `featuredBudget`. À égalité, le premier de la liste.
    public static func featured(in summaries: [BudgetSummary]) -> BudgetSummary? {
        summaries.reduce(nil) { best, next in
            guard let best else { return next }
            return next.progress > best.progress ? next : best
        }
    }
}
