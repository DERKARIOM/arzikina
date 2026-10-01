/// Les trois groupes de l'écran Comptes (Android `AccountsDisplayTab`) : chaque compte appartient
/// à EXACTEMENT un groupe, aucun n'est perdu ni affiché deux fois.
public enum AccountGroup: String, CaseIterable, Sendable {
    /// Espèces, banque, Mobile Money, épargne.
    case accounts
    /// Cartes de crédit (affichées comme une carte bancaire).
    case bankCards
    /// Objectifs d'épargne (comptes avec un montant cible).
    case savingsGoals
}

extension AccountType {
    public var group: AccountGroup {
        switch self {
        case .creditCard: return .bankCards
        case .savingsGoal: return .savingsGoals
        case .cash, .bank, .mobileMoney, .savings: return .accounts
        }
    }
}
