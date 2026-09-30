/// Calculs d'une planification — portage d'Android `FinancialPlanProgress`.
public enum FinancialPlanProgress {

    /// Somme des dépenses prévues, hors dépenses annulées.
    public static func totalPlanned(_ items: [FinancialPlanItem]) -> MinorUnits {
        items.lazy.filter { $0.status != .cancelled }.reduce(0) { $0 + $1.amount }
    }

    /// Budget restant (négatif en cas de dépassement).
    public static func remaining(available: MinorUnits, totalPlanned: MinorUnits) -> MinorUnits {
        available - totalPlanned
    }

    /// Part du budget déjà planifiée, 0…100 (100 si le budget est nul et qu'une dépense existe).
    public static func progress(available: MinorUnits, totalPlanned: MinorUnits) -> Int {
        guard available > 0 else { return totalPlanned > 0 ? 100 : 0 }
        return Int(min(max((totalPlanned * 100) / available, 0), 100))
    }

    public static func isOverBudget(available: MinorUnits, totalPlanned: MinorUnits) -> Bool {
        totalPlanned > available
    }
}
