import Foundation

/// Frais d'une transaction — Android `TransactionFee`. Jamais stockés sur la transaction
/// principale : ils deviennent une SECONDE transaction (dépense, catégorie système « Frais et
/// commissions »), reliée par `Transaction.feeTransactionId`.
public struct TransactionFee: Equatable, Sendable {
    /// Unité mineure, toujours positif.
    public var amount: MinorUnits
    /// Compte débité des frais : par défaut celui de la transaction, mais peut être un autre.
    public var accountId: EntityID
    public var type: FeeType
    public var description: String

    public init(amount: MinorUnits, accountId: EntityID, type: FeeType, description: String = "") {
        self.amount = amount
        self.accountId = accountId
        self.type = type
        self.description = description
    }
}

/// Saisie du formulaire de transaction — Android `TransactionFormState`.
///
/// Les transitions (changement de type, de compte…) appliquent les mêmes règles qu'Android : la
/// catégorie est réinitialisée quand le type change, le compte destination n'a de sens que pour
/// un transfert, le compte des frais suit le compte source tant qu'il n'a pas été choisi, et la
/// description d'un transfert est remplie automatiquement (« Espèces → Banque ») sans jamais
/// écraser une description tapée à la main.
public struct TransactionDraft: Equatable, Sendable {
    public var amountInput: String
    public private(set) var type: TransactionType
    public private(set) var accountId: EntityID?
    public private(set) var transferAccountId: EntityID?
    public var categoryId: EntityID?
    public var date: EpochMillis
    public private(set) var description: String
    /// `true` si [description] a été générée (« A → B ») et peut donc être régénérée.
    public private(set) var isDescriptionAutoFilled: Bool
    public var paymentMethod: PaymentMethod?
    public var hasFee: Bool {
        didSet { if hasFee && feeAccountId == nil { feeAccountId = accountId } }
    }
    public var feeAmountInput: String
    public var feeType: FeeType
    public private(set) var feeAccountId: EntityID?
    public private(set) var isFeeAccountAutoFilled: Bool
    public var feeDescription: String

    /// Nouvelle transaction : une dépense, datée de [now], sur [presetAccountId] s'il est fourni
    /// (ouverture depuis le détail d'un compte).
    public init(now: EpochMillis, presetAccountId: EntityID? = nil) {
        amountInput = ""
        type = .expense
        accountId = presetAccountId
        transferAccountId = nil
        categoryId = nil
        date = now
        description = ""
        isDescriptionAutoFilled = false
        paymentMethod = nil
        hasFee = false
        feeAmountInput = ""
        feeType = .transfer
        feeAccountId = nil
        isFeeAccountAutoFilled = true
        feeDescription = ""
    }

    /// Formulaire pré-rempli pour modifier [transaction] et ses frais éventuels ([fee] : la
    /// transaction de frais liée, si elle existe encore).
    public init(editing transaction: Transaction, fee: Transaction?) {
        self.init(now: transaction.date, presetAccountId: transaction.accountId)
        amountInput = Money.formatForInput(transaction.amount)
        type = transaction.type
        transferAccountId = transaction.transferAccountId
        categoryId = transaction.categoryId
        description = transaction.description
        paymentMethod = transaction.paymentMethod
        if let fee {
            hasFee = true
            feeAmountInput = Money.formatForInput(fee.amount)
            feeType = fee.feeType ?? .other
            feeAccountId = fee.accountId
            isFeeAccountAutoFilled = fee.accountId == transaction.accountId
            feeDescription = fee.description
        }
    }

    // MARK: - Transitions

    /// [accountName] donne le nom affiché d'un compte (pour la description automatique).
    public mutating func changeType(_ newType: TransactionType, accountName: (EntityID) -> String?) {
        let leavingTransfer = type == .transfer && newType != .transfer
        type = newType
        // La catégorie précédente peut ne plus correspondre au nouveau type.
        categoryId = nil
        if newType != .transfer { transferAccountId = nil }
        // La description « A → B » n'a plus de sens hors transfert : effacée seulement si elle
        // était automatique.
        if leavingTransfer && isDescriptionAutoFilled {
            description = ""
            isDescriptionAutoFilled = false
        }
        autoFillTransferDescription(accountName)
    }

    public mutating func changeAccount(_ id: EntityID?, accountName: (EntityID) -> String?) {
        accountId = id
        if isFeeAccountAutoFilled { feeAccountId = id }
        autoFillTransferDescription(accountName)
    }

    public mutating func changeTransferAccount(_ id: EntityID?, accountName: (EntityID) -> String?) {
        transferAccountId = id
        autoFillTransferDescription(accountName)
    }

    /// Choix explicite du compte des frais : il ne suit plus le compte source.
    public mutating func changeFeeAccount(_ id: EntityID?) {
        feeAccountId = id
        isFeeAccountAutoFilled = false
    }

    /// Saisie de la description. Reste « automatique » seulement si elle est identique au texte
    /// généré (cas d'un champ qui renvoie la valeur qu'on vient de lui donner).
    public mutating func changeDescription(_ value: String, accountName: (EntityID) -> String?) {
        let generated = TransactionForm.transferDescription(source: accountId, destination: transferAccountId, accountName: accountName)
        isDescriptionAutoFilled = isDescriptionAutoFilled && value == generated
        description = value
    }

    private mutating func autoFillTransferDescription(_ accountName: (EntityID) -> String?) {
        guard type == .transfer else { return }
        if !description.trimmingCharacters(in: .whitespaces).isEmpty && !isDescriptionAutoFilled { return }
        guard let generated = TransactionForm.transferDescription(source: accountId, destination: transferAccountId, accountName: accountName) else { return }
        description = generated
        isDescriptionAutoFilled = true
    }
}

/// Erreur de saisie — même ordre de vérification qu'Android (`TransactionFormViewModel.save`).
public enum TransactionFormError: Error, Equatable, Sendable {
    case invalidAmount
    case accountRequired
    case destinationRequired
    case sameTransferAccount
    case categoryRequired
    case invalidFeeAmount
    case feeAccountRequired
}

public enum TransactionForm {

    /// « Espèces → Banque », `nil` si l'un des deux comptes manque.
    public static func transferDescription(source: EntityID?, destination: EntityID?, accountName: (EntityID) -> String?) -> String? {
        guard let source, let destination, let from = accountName(source), let to = accountName(destination) else { return nil }
        return "\(from) → \(to)"
    }

    /// Catégories proposées dans le formulaire : jamais les catégories système des prêts (leurs
    /// transactions sont gérées par l'écran des prêts) ni celle des frais (créée automatiquement).
    public static func isSelectable(_ category: Category) -> Bool {
        switch category.systemKey {
        case .loanDisbursementLent, .loanRepaymentLent, .loanDisbursementBorrowed, .loanRepaymentBorrowed, .fees:
            return false
        default:
            return true
        }
    }

    /// Transaction à enregistrer (et ses frais), ou la PREMIÈRE erreur de saisie.
    ///
    /// En modification, les champs que le formulaire ne montre pas sont conservés (position GPS
    /// saisie sur Android, date de création). `feeTransactionId` est géré par le dépôt.
    public static func build(
        _ draft: TransactionDraft,
        existing: Transaction?,
        newId: @autoclosure () -> EntityID,
        now: EpochMillis
    ) -> Result<(transaction: Transaction, fee: TransactionFee?), TransactionFormError> {
        guard let amount = Money.parseToMinorUnits(draft.amountInput), amount > 0 else { return .failure(.invalidAmount) }
        guard let accountId = draft.accountId else { return .failure(.accountRequired) }

        let isTransfer = draft.type == .transfer
        if isTransfer {
            guard let destination = draft.transferAccountId else { return .failure(.destinationRequired) }
            guard destination != accountId else { return .failure(.sameTransferAccount) }
        } else if draft.categoryId == nil {
            return .failure(.categoryRequired)
        }

        var fee: TransactionFee?
        if draft.hasFee {
            guard let feeAmount = Money.parseToMinorUnits(draft.feeAmountInput), feeAmount > 0 else { return .failure(.invalidFeeAmount) }
            guard let feeAccount = draft.feeAccountId else { return .failure(.feeAccountRequired) }
            fee = TransactionFee(
                amount: feeAmount,
                accountId: feeAccount,
                type: draft.feeType,
                description: draft.feeDescription.trimmingCharacters(in: .whitespacesAndNewlines)
            )
        }

        let transaction = Transaction(
            id: existing?.id ?? newId(),
            amount: amount,
            type: draft.type,
            accountId: accountId,
            // Un transfert n'a pas de catégorie ; seul un transfert a un compte destination.
            transferAccountId: isTransfer ? draft.transferAccountId : nil,
            categoryId: isTransfer ? nil : draft.categoryId,
            date: draft.date,
            description: draft.description.trimmingCharacters(in: .whitespacesAndNewlines),
            latitude: existing?.latitude,
            longitude: existing?.longitude,
            paymentMethod: draft.paymentMethod,
            feeTransactionId: existing?.feeTransactionId,
            // `feeType` n'a de sens que sur la transaction de frais elle-même.
            feeType: nil,
            createdAt: existing?.createdAt ?? now
        )
        return .success((transaction, fee))
    }
}

extension SystemCategoryKey {
    /// Apparence d'une catégorie système créée par l'app (Android `DefaultCategories`).
    public var defaultIcon: CategoryIcon {
        switch self {
        case .salary: return .salary
        case .otherIncome, .otherExpense: return .other
        case .food: return .food
        case .transport: return .transport
        case .health: return .health
        case .shopping: return .shopping
        case .gifts: return .gifts
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
        case .gifts: return 0xFFEC_4899
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
