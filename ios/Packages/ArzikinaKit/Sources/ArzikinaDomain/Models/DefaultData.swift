/// Comptes et catégories créés pour un NOUVEL utilisateur, juste après son inscription — portage
/// d'Android `DefaultAccounts` / `DefaultCategories` (mêmes noms de référence, icônes, couleurs,
/// types et ordre), pour qu'un compte créé sur iOS démarre exactement comme sur Android.
///
/// Jamais à la connexion à un compte existant : ses données arrivent par la synchronisation, et
/// les recréer produirait des doublons (règle d'Android `UnifiedAuthRepositoryImpl`).
public enum DefaultData {

    /// Espèces, Banque, Mobile Money, Épargne, Wallet — dans cet ordre d'affichage.
    public static func accounts(now: EpochMillis, newId: () -> EntityID) -> [Account] {
        DefaultAccountKey.allCases.enumerated().map { index, key in
            Account(
                id: newId(),
                name: key.canonicalName,
                icon: key.defaultIcon,
                colorArgb: key.defaultColorArgb,
                currencyCode: SupportedCurrency.defaultCode,
                initialBalance: 0,
                type: key.defaultType,
                displayOrder: Int64(index),
                createdAt: now
            )
        }
    }

    /// Les 13 catégories courantes, les 4 des prêts et « Frais et commissions ».
    public static func categories(now: EpochMillis, newId: () -> EntityID) -> [Category] {
        SystemCategoryKey.allCases.filter(\.isCreatedAtRegistration).map { key in
            Category(
                id: newId(),
                name: key.canonicalName,
                icon: key.defaultIcon,
                colorArgb: key.defaultColorArgb,
                type: key.type,
                createdAt: now
            )
        }
    }
}

extension DefaultAccountKey {
    public var defaultIcon: AccountIcon {
        switch self {
        case .cash: return .cash
        case .bank: return .bank
        case .mobileMoney: return .mobileMoney
        case .savings: return .savings
        case .wallet: return .wallet
        }
    }

    /// Android n'a pas de type « Wallet » : le compte Wallet est un compte d'espèces.
    public var defaultType: AccountType {
        switch self {
        case .cash, .wallet: return .cash
        case .bank: return .bank
        case .mobileMoney: return .mobileMoney
        case .savings: return .savings
        }
    }

    public var defaultColorArgb: Int64 {
        switch self {
        case .cash: return 0xFF16_A34A
        case .bank: return 0xFF00_6C4F
        case .mobileMoney: return 0xFFF5_9E0B
        case .savings: return 0xFF00_A578
        case .wallet: return 0xFF4C_6B3F
        }
    }
}

extension SystemCategoryKey {
    /// Créée à l'inscription (Android `DefaultCategories`). « Cadeaux » en revenu ne l'est pas :
    /// elle est créée au premier emprunt transformé en cadeau, comme sur Android.
    public var isCreatedAtRegistration: Bool { self != .giftsReceived }

    /// Apparence d'une catégorie système créée par l'app (Android `DefaultCategories`).
    public var defaultIcon: CategoryIcon {
        switch self {
        case .salary: return .salary
        case .otherIncome, .otherExpense: return .other
        case .food: return .food
        case .transport: return .transport
        case .health: return .health
        case .shopping: return .shopping
        case .gifts, .giftsReceived: return .gifts
        case .internet: return .internet
        case .water: return .water
        case .electricity: return .electricity
        case .education: return .education
        case .home: return .home
        case .loanDisbursementLent, .loanRepaymentLent, .loanDisbursementBorrowed, .loanRepaymentBorrowed: return .loan
        case .fees: return .fee
        }
    }

    public var defaultColorArgb: Int64 {
        switch self {
        case .salary: return 0xFF00_6C4F
        case .otherIncome, .otherExpense: return 0xFF64_748B
        case .food, .electricity: return 0xFFF5_9E0B
        case .transport: return 0xFF25_63EB
        case .health: return 0xFFDC_2626
        case .shopping: return 0xFF7C_3AED
        case .gifts, .giftsReceived: return 0xFFEC_4899
        case .internet: return 0xFF0E_A5E9
        case .water: return 0xFF06_B6D4
        case .education: return 0xFF16_A34A
        case .home: return 0xFF10_B981
        case .loanDisbursementLent, .loanRepaymentLent: return 0xFF16_A34A
        case .loanDisbursementBorrowed, .loanRepaymentBorrowed: return 0xFFDC_2626
        case .fees: return 0xFF92_400E
        }
    }
}
