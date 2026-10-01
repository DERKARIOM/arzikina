import Foundation

/// Saisie du formulaire de compte (création ou modification), telle que l'utilisateur l'a tapée.
public struct AccountDraft: Equatable, Sendable {
    public var name: String
    public var icon: AccountIcon
    public var colorArgb: Int64
    public var currencyCode: String
    /// Montant saisi (unité majeure, « 10 000 », « 2 500,50 »).
    public var initialBalanceInput: String
    public var type: AccountType
    public var isExcludedFromStatistics: Bool
    /// Carte de crédit : les 4 derniers chiffres (voir `AccountForm`).
    public var cardLastFourInput: String
    /// Carte de crédit : « MM/AA ».
    public var cardExpiryInput: String
    /// Objectif d'épargne : montant cible saisi.
    public var savingsTargetInput: String
    public var savingsDescriptionInput: String

    public init(
        name: String = "",
        icon: AccountIcon = .cash,
        colorArgb: Int64 = AccountForm.defaultColorArgb,
        currencyCode: String = SupportedCurrency.defaultCode,
        initialBalanceInput: String = "0",
        type: AccountType = .cash,
        isExcludedFromStatistics: Bool = false,
        cardLastFourInput: String = "",
        cardExpiryInput: String = "",
        savingsTargetInput: String = "",
        savingsDescriptionInput: String = ""
    ) {
        self.name = name
        self.icon = icon
        self.colorArgb = colorArgb
        self.currencyCode = currencyCode
        self.initialBalanceInput = initialBalanceInput
        self.type = type
        self.isExcludedFromStatistics = isExcludedFromStatistics
        self.cardLastFourInput = cardLastFourInput
        self.cardExpiryInput = cardExpiryInput
        self.savingsTargetInput = savingsTargetInput
        self.savingsDescriptionInput = savingsDescriptionInput
    }

    /// Formulaire pré-rempli pour modifier [account]. [displayName] : le nom affiché (traduit
    /// pour un compte par défaut), que l'enregistrement reconvertira en nom de référence.
    public init(editing account: Account, displayName: String) {
        self.init(
            name: displayName,
            icon: account.icon,
            colorArgb: account.colorArgb,
            currencyCode: account.currencyCode,
            initialBalanceInput: Money.formatForInput(account.initialBalance),
            type: account.type,
            isExcludedFromStatistics: account.isExcludedFromStatistics,
            cardLastFourInput: account.cardLastFourDigits ?? "",
            cardExpiryInput: {
                guard let month = account.cardExpiryMonth, let year = account.cardExpiryYear else { return "" }
                return String(format: "%02d/%02d", month, year % 100)
            }(),
            savingsTargetInput: account.savingsTargetAmount.map(Money.formatForInput) ?? "",
            savingsDescriptionInput: account.savingsDescription ?? ""
        )
    }
}

/// Champ en erreur dans le formulaire de compte.
public enum AccountFormError: Equatable, Sendable {
    case nameRequired
    case invalidInitialBalance
    /// Montant cible absent, invalide ou nul (la progression serait indéfinie).
    case invalidSavingsTarget
    case invalidCardLastFour
    case invalidCardExpiry
}

/// Règles du formulaire de compte — portage d'Android `AccountFormViewModel.save` et
/// `CardInputFormatter`.
///
/// ÉCART VOLONTAIRE avec Android pour les cartes de crédit : Android demande le numéro complet et
/// le CVV, les chiffre et les garde UNIQUEMENT sur le téléphone (ils ne sont jamais synchronisés ;
/// seuls les 4 derniers chiffres et l'expiration le sont). iOS ne les demande pas du tout : seuls
/// les 4 derniers chiffres et l'expiration sont saisis. Même résultat synchronisé, et aucune donnée
/// de carte sensible n'est jamais saisie ni stockée sur l'iPhone.
public enum AccountForm {

    /// Couleur par défaut d'un nouveau compte (Android `AccountFormState.colorArgb`).
    public static let defaultColorArgb: Int64 = 0xFF10_B981

    public enum Outcome: Equatable, Sendable {
        case valid(Account)
        case invalid(AccountFormError)
        /// Un objectif d'épargne va redevenir un compte classique : sa cible et sa description
        /// seront effacées. L'interface demande confirmation puis rappelle avec
        /// `confirmedSavingsGoalRemoval: true`.
        case needsSavingsGoalRemovalConfirmation
    }

    /// Valide [draft] et construit le compte à enregistrer.
    ///
    /// - [existing] : le compte modifié (`nil` pour une création). Ses champs absents du
    ///   formulaire sont conservés (ordre d'affichage, date de création, application Mobile Money).
    /// - [newId] / [newDisplayOrder] : identifiant et position d'un NOUVEAU compte.
    /// - [canonicalName] : convertit un nom traduit d'un compte par défaut (« Cash ») en son nom
    ///   de référence (« Espèces »), comme Android `canonicalAccountName`.
    /// - [currentYear] / [currentMonth] : pour refuser une carte déjà expirée.
    public static func validate(
        _ draft: AccountDraft,
        existing: Account?,
        newId: @autoclosure () -> EntityID,
        newDisplayOrder: Int64,
        currentYear: Int,
        currentMonth: Int,
        confirmedSavingsGoalRemoval: Bool = false,
        canonicalName: (String) -> String = { $0 }
    ) -> Outcome {
        let name = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return .invalid(.nameRequired) }
        guard let initialBalance = Money.parseToMinorUnits(draft.initialBalanceInput) else {
            return .invalid(.invalidInitialBalance)
        }

        var savingsTarget: MinorUnits?
        var savingsDescription: String?
        if draft.type == .savingsGoal {
            guard let target = Money.parseToMinorUnits(draft.savingsTargetInput), target > 0 else {
                return .invalid(.invalidSavingsTarget)
            }
            savingsTarget = target
            let description = draft.savingsDescriptionInput.trimmingCharacters(in: .whitespacesAndNewlines)
            savingsDescription = description.isEmpty ? nil : description
        } else if existing?.type == .savingsGoal && !confirmedSavingsGoalRemoval {
            return .needsSavingsGoalRemovalConfirmation
        }

        var lastFour: String?
        var expiryMonth: Int?
        var expiryYear: Int?
        if draft.type == .creditCard {
            let digits = String(draft.cardLastFourInput.filter(AccountForm.isDigit))
            guard digits.count == 4 else { return .invalid(.invalidCardLastFour) }
            guard let expiry = parseExpiry(draft.cardExpiryInput),
                  isValidExpiry(month: expiry.month, year: expiry.year, currentYear: currentYear, currentMonth: currentMonth)
            else { return .invalid(.invalidCardExpiry) }
            lastFour = digits
            expiryMonth = expiry.month
            expiryYear = expiry.year
        }

        var account = existing ?? Account(id: newId(), name: name, displayOrder: newDisplayOrder)
        account.name = canonicalName(name)
        account.icon = draft.icon
        account.colorArgb = draft.colorArgb
        account.currencyCode = draft.currencyCode
        account.initialBalance = initialBalance
        account.type = draft.type
        account.isExcludedFromStatistics = draft.isExcludedFromStatistics
        // Champs propres à un type : effacés dès que le compte n'est plus de ce type (aucune
        // donnée orpheline synchronisée), comme Android.
        account.cardLastFourDigits = lastFour
        account.cardExpiryMonth = expiryMonth
        account.cardExpiryYear = expiryYear
        account.savingsTargetAmount = savingsTarget
        account.savingsDescription = savingsDescription
        if draft.type != .mobileMoney {
            account.mobileMoneyPackageName = nil
        }
        return .valid(account)
    }

    /// Chiffre ASCII 0…9 uniquement (jamais d'autres chiffres Unicode).
    static func isDigit(_ character: Character) -> Bool {
        character.isASCII && character.isNumber
    }

    /// « MM/AA » (ou « MMAA ») → mois et année complète (20AA).
    public static func parseExpiry(_ input: String) -> (month: Int, year: Int)? {
        let digits = input.filter(AccountForm.isDigit)
        guard digits.count == 4, let month = Int(digits.prefix(2)), let year = Int(digits.suffix(2)) else { return nil }
        return (month, 2000 + year)
    }

    /// Mois 1…12, et carte pas encore expirée (le mois courant est accepté) — Android
    /// `CardInputFormatter.isValidExpiry`.
    public static func isValidExpiry(month: Int, year: Int, currentYear: Int, currentMonth: Int) -> Bool {
        guard (1...12).contains(month) else { return false }
        return year > currentYear || (year == currentYear && month >= currentMonth)
    }

    /// Mise en forme pendant la frappe : « 0727 » → « 07/27 » (Android `formatExpiry`).
    public static func formatExpiryInput(_ input: String) -> String {
        let digits = String(input.filter(AccountForm.isDigit).prefix(4))
        guard digits.count > 2 else { return digits }
        return "\(digits.prefix(2))/\(digits.dropFirst(2))"
    }

    /// Icône proposée quand l'utilisateur change de type (il reste libre d'en choisir une autre).
    /// Un compte EXISTANT transformé en objectif garde son icône.
    public static func defaultIcon(for type: AccountType, current: AccountIcon, isEditing: Bool) -> AccountIcon {
        switch type {
        case .creditCard: return .creditCard
        case .savingsGoal: return isEditing ? current : .savings
        default: return current
        }
    }
}

extension DefaultAccountKey {
    /// Nom de référence si [input] est le nom de référence ou la traduction (dans une langue
    /// prise en charge) d'un compte par défaut, sinon [input] inchangé — Android
    /// `DefaultAccountKey.canonicalNameFor`.
    public static func canonicalName(for input: String, labelsOf: (DefaultAccountKey) -> [String]) -> String {
        allCases.first { key in input == key.canonicalName || labelsOf(key).contains(input) }?.canonicalName ?? input
    }
}
