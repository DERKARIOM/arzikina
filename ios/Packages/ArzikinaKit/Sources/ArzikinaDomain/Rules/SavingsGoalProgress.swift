/// Situation d'un compte « Objectif d'épargne », prête à afficher (unité mineure).
public struct SavingsGoalSnapshot: Equatable, Sendable {
    /// Solde RÉEL du compte (peut être négatif).
    public let balance: MinorUnits
    /// Montant cible (toujours > 0).
    public let target: MinorUnits
    /// Montant considéré comme épargné : le solde, jamais négatif.
    public let saved: MinorUnits
    /// Reste à épargner, jamais négatif.
    public let remaining: MinorUnits
    /// Dépassement de la cible (0 tant qu'elle n'est pas dépassée).
    public let exceededBy: MinorUnits
    /// Pourcentage affiché, borné à 0…100 : la barre est pleine en cas de dépassement, jamais
    /// « 120 % » ([isExceeded] signale le dépassement).
    public let percent: Int
    public let isReached: Bool

    public var isExceeded: Bool { exceededBy > 0 }
}

/// Calculs d'un objectif d'épargne — portage d'Android `SavingsGoalProgress`. Jamais stockés :
/// recalculés à chaque affichage à partir du solde du compte.
public enum SavingsGoalProgress {

    /// Pourcentage = solde / cible × 100, arrondi à l'entier INFÉRIEUR (jamais 100 % avant
    /// d'atteindre réellement la cible), borné à 0…100 ; 0 pour une cible nulle ou négative.
    public static func progressPercent(current: MinorUnits, target: MinorUnits) -> Int {
        guard target > 0 else { return 0 }
        let saved = max(current, 0)
        if saved >= target { return 100 }
        // Calcul exact sur 128 bits : `saved × 100` ne peut pas déborder, et le quotient (< 100
        // puisque saved < target) tient toujours dans un Int.
        let product = saved.multipliedFullWidth(by: 100)
        return Int(target.dividingFullWidth(product).quotient)
    }

    public static func isCompleted(current: MinorUnits, target: MinorUnits) -> Bool {
        target > 0 && current >= target
    }

    public static func remaining(current: MinorUnits, target: MinorUnits) -> MinorUnits {
        max(target - max(current, 0), 0)
    }

    /// `nil` si [target] est absent ou ≤ 0 (le compte n'est pas, ou plus, un objectif valide).
    public static func snapshot(balance: MinorUnits, target: MinorUnits?) -> SavingsGoalSnapshot? {
        guard let target, target > 0 else { return nil }
        let saved = max(balance, 0)
        return SavingsGoalSnapshot(
            balance: balance,
            target: target,
            saved: saved,
            remaining: remaining(current: balance, target: target),
            exceededBy: max(saved - target, 0),
            percent: progressPercent(current: balance, target: target),
            isReached: isCompleted(current: balance, target: target)
        )
    }
}
