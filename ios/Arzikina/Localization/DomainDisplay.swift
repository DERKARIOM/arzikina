import ArzikinaDomain
import Foundation

/// Libellés traduits des valeurs du domaine (noms par défaut, moyens de paiement…).
///
/// Le domaine ne connaît que des noms de référence et des codes stables ; la traduction se fait
/// ICI, au moment de l'affichage, dans la langue de l'app (`Localizable.xcstrings`). Les données
/// stockées et synchronisées ne changent jamais avec la langue.
enum DomainDisplay {

    /// Traduction d'une clé du String Catalog construite dynamiquement.
    static func localized(_ key: String) -> String {
        String(localized: String.LocalizationValue(key))
    }
}

extension SystemCategoryKey {
    /// Clé du String Catalog (mêmes libellés qu'Android `category_default_*` / `category_system_*`).
    var localizationKey: String {
        switch self {
        case .salary: return "category.default.salary"
        case .otherIncome: return "category.default.other_income"
        case .food: return "category.default.food"
        case .transport: return "category.default.transport"
        case .health: return "category.default.health"
        case .shopping: return "category.default.shopping"
        case .gifts: return "category.default.gifts"
        case .internet: return "category.default.internet"
        case .water: return "category.default.water"
        case .electricity: return "category.default.electricity"
        case .education: return "category.default.education"
        case .home: return "category.default.home"
        case .otherExpense: return "category.default.other_expense"
        case .loanDisbursementLent: return "category.system.loan_disbursement_lent"
        case .loanRepaymentLent: return "category.system.loan_repayment_lent"
        case .loanDisbursementBorrowed: return "category.system.loan_disbursement_borrowed"
        case .loanRepaymentBorrowed: return "category.system.loan_repayment_borrowed"
        case .fees: return "category.system.fees"
        }
    }
}

extension DefaultAccountKey {
    var localizationKey: String {
        switch self {
        case .cash: return "account.default.cash"
        case .bank: return "account.default.bank"
        case .mobileMoney: return "account.default.mobile_money"
        case .savings: return "account.default.savings"
        case .wallet: return "account.default.wallet"
        }
    }
}

extension ArzikinaDomain.Category {
    /// Nom affiché : traduit pour une catégorie système, tel quel pour une catégorie personnelle.
    var displayName: String {
        systemKey.map { DomainDisplay.localized($0.localizationKey) } ?? name
    }

    /// Symbole SF de l'icône (équivalent iOS des icônes Android `CategoryIconMapper`).
    var systemImage: String { icon.systemImage }
}

extension Account {
    var displayName: String {
        defaultKey.map { DomainDisplay.localized($0.localizationKey) } ?? name
    }
}

extension CategoryIcon {
    var systemImage: String {
        switch self {
        case .food: return "fork.knife"
        case .transport: return "car.fill"
        case .health: return "cross.case.fill"
        case .salary: return "banknote.fill"
        case .shopping: return "bag.fill"
        case .gifts: return "gift.fill"
        case .internet: return "wifi"
        case .water: return "drop.fill"
        case .electricity: return "bolt.fill"
        case .education: return "graduationcap.fill"
        case .home: return "house.fill"
        case .other: return "square.grid.2x2.fill"
        case .loan: return "person.2.fill"
        case .fee: return "percent"
        }
    }
}

extension PaymentMethod {
    var displayName: String {
        switch self {
        case .cash: return DomainDisplay.localized("payment_method.cash")
        case .card: return DomainDisplay.localized("payment_method.card")
        case .mobileMoney: return DomainDisplay.localized("payment_method.mobile_money")
        case .bankTransfer: return DomainDisplay.localized("payment_method.bank_transfer")
        case .other: return DomainDisplay.localized("payment_method.other")
        }
    }
}
