/// Catégories créées par le système, reconnues par leur nom de référence FRANÇAIS (stocké et
/// synchronisé tel quel) et traduites à l'affichage — portage d'Android `SystemCategoryKey`.
///
/// Le nom stocké reste le nom de référence : changer la langue de l'app ne modifie jamais les
/// données, et Android, le Web et iOS reconnaissent la même catégorie.
public enum SystemCategoryKey: String, CaseIterable, Sendable {
    case salary
    case otherIncome
    case food
    case transport
    case health
    case shopping
    case gifts
    /// Cadeau REÇU (emprunt transformé en cadeau) : même nom que [gifts], type revenu (Android
    /// `GIFTS_RECEIVED`).
    case giftsReceived
    case internet
    case water
    case electricity
    case education
    case home
    case otherExpense
    case loanDisbursementLent
    case loanRepaymentLent
    case loanDisbursementBorrowed
    case loanRepaymentBorrowed
    case fees

    /// Nom de référence (identique à Android, `LoanCategoryNames`, `FeeCategoryNames`).
    public var canonicalName: String {
        switch self {
        case .salary: return "Salaire"
        case .otherIncome, .otherExpense: return "Divers"
        case .food: return "Nourriture"
        case .transport: return "Transport"
        case .health: return "Santé"
        case .shopping: return "Shopping"
        case .gifts, .giftsReceived: return "Cadeaux"
        case .internet: return "Internet"
        case .water: return "Eau"
        case .electricity: return "Électricité"
        case .education: return "Éducation"
        case .home: return "Maison"
        case .loanDisbursementLent: return "Prêt accordé"
        case .loanRepaymentLent: return "Remboursement de prêt reçu"
        case .loanDisbursementBorrowed: return "Emprunt reçu"
        case .loanRepaymentBorrowed: return "Remboursement d'emprunt"
        case .fees: return "Frais et commissions"
        }
    }

    public var type: TransactionType {
        switch self {
        case .salary, .otherIncome, .giftsReceived, .loanRepaymentLent, .loanDisbursementBorrowed: return .income
        default: return .expense
        }
    }

    /// Clé système d'une catégorie (même nom ET même type), `nil` pour une catégorie personnelle.
    public static func of(name: String, type: TransactionType) -> SystemCategoryKey? {
        allCases.first { $0.type == type && $0.canonicalName == name }
    }
}

/// Comptes créés par défaut, reconnus par leur nom de référence — portage d'Android
/// `DefaultAccountKey`.
public enum DefaultAccountKey: String, CaseIterable, Sendable {
    case cash
    case bank
    case mobileMoney
    case savings
    case wallet

    public var canonicalName: String {
        switch self {
        case .cash: return "Espèces"
        case .bank: return "Banque"
        case .mobileMoney: return "Mobile Money"
        case .savings: return "Épargne"
        case .wallet: return "Wallet"
        }
    }

    public static func of(name: String) -> DefaultAccountKey? {
        allCases.first { $0.canonicalName == name }
    }
}

extension Category {
    /// Clé système de la catégorie, `nil` si elle a été créée par l'utilisateur.
    public var systemKey: SystemCategoryKey? { SystemCategoryKey.of(name: name, type: type) }
}

extension Account {
    /// Clé du compte par défaut, `nil` si l'utilisateur l'a nommé lui-même.
    public var defaultKey: DefaultAccountKey? { DefaultAccountKey.of(name: name) }
}
