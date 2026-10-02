public enum LoanType: String, CaseIterable, Codable, Sendable {
    /// J'ai prêté : de l'argent à recevoir.
    case lent = "LENT"
    /// J'ai emprunté : de l'argent à rendre.
    case borrowed = "BORROWED"
}

public enum LoanReason: String, CaseIterable, Codable, Sendable {
    case financialHelp = "FINANCIAL_HELP"
    case education = "EDUCATION"
    case health = "HEALTH"
    case purchase = "PURCHASE"
    case project = "PROJECT"
    case emergency = "EMERGENCY"
    case other = "OTHER"
}

public enum RepaymentMode: String, CaseIterable, Codable, Sendable {
    case single = "SINGLE"
    case installments = "INSTALLMENTS"
    case monthly = "MONTHLY"
    case weekly = "WEEKLY"
    case custom = "CUSTOM"
}

/// Statut d'un prêt — TOUJOURS recalculé à l'affichage (voir `LoanStatusRule`), la valeur stockée
/// ne servant qu'aux filtres rapides.
public enum LoanStatus: String, CaseIterable, Codable, Sendable {
    case ongoing = "ONGOING"
    case repaid = "REPAID"
    case overdue = "OVERDUE"
    case upcoming = "UPCOMING"
    /// Le reste a été transformé en cadeau (Android `LoanStatus.GIFTED`) : statut FINAL, comme
    /// `.repaid`, prioritaire sur tous les autres.
    case gifted = "GIFTED"

    /// Dette éteinte (remboursée ou offerte) : plus aucun remboursement attendu.
    public var isSettled: Bool { self == .repaid || self == .gifted }
}

/// Personne liée à des prêts/emprunts.
public struct Person: Identifiable, Equatable, Hashable, Sendable {
    public var id: EntityID
    public var name: String
    public var phone: String?
    public var createdAt: EpochMillis

    public init(id: EntityID, name: String, phone: String? = nil, createdAt: EpochMillis = 0) {
        self.id = id
        self.name = name
        self.phone = phone
        self.createdAt = createdAt
    }
}

/// Prêt ou emprunt. Sa création génère une transaction ([transactionId]) ; chaque remboursement
/// en génère une autre (voir `LoanPayment`).
public struct Loan: Identifiable, Equatable, Hashable, Sendable {
    public var id: EntityID
    public var personId: EntityID
    public var accountId: EntityID
    public var type: LoanType
    public var amount: MinorUnits
    public var amountRepaid: MinorUnits
    public var remainingAmount: MinorUnits
    /// Date ET heure de début.
    public var startDate: EpochMillis
    public var dueDate: EpochMillis
    public var reason: LoanReason
    public var reasonCustomText: String?
    public var repaymentMode: RepaymentMode
    public var description: String
    public var status: LoanStatus
    public var transactionId: EntityID
    public var createdAt: EpochMillis
    public var updatedAt: EpochMillis
    /// Part du reste transformée en cadeau (0 sinon) — gérée uniquement par la transformation.
    public var giftedAmount: MinorUnits
    /// Transaction « Cadeaux » qui porte [giftedAmount] ; égale à [transactionId] quand rien
    /// n'avait été remboursé (décaissement reclassé sur place).
    public var giftTransactionId: EntityID?
    /// Instant de la transformation.
    public var giftedAt: EpochMillis?

    /// Transformé en cadeau : montant, compte, personne et remboursements sont verrouillés.
    public var isGifted: Bool { giftedAmount > 0 }

    /// Transactions qui appartiennent au prêt : décaissement et transaction cadeau éventuelle
    /// (Android `LoanEntity.ownTransactionIds`).
    public var ownTransactionIds: [EntityID] {
        guard let gift = giftTransactionId, gift != transactionId else { return [transactionId] }
        return [transactionId, gift]
    }

    public init(
        id: EntityID,
        personId: EntityID,
        accountId: EntityID,
        type: LoanType,
        amount: MinorUnits,
        amountRepaid: MinorUnits = 0,
        remainingAmount: MinorUnits,
        startDate: EpochMillis,
        dueDate: EpochMillis,
        reason: LoanReason = .other,
        reasonCustomText: String? = nil,
        repaymentMode: RepaymentMode = .single,
        description: String = "",
        status: LoanStatus = .ongoing,
        transactionId: EntityID,
        createdAt: EpochMillis = 0,
        updatedAt: EpochMillis = 0,
        giftedAmount: MinorUnits = 0,
        giftTransactionId: EntityID? = nil,
        giftedAt: EpochMillis? = nil
    ) {
        self.id = id
        self.personId = personId
        self.accountId = accountId
        self.type = type
        self.amount = amount
        self.amountRepaid = amountRepaid
        self.remainingAmount = remainingAmount
        self.startDate = startDate
        self.dueDate = dueDate
        self.reason = reason
        self.reasonCustomText = reasonCustomText
        self.repaymentMode = repaymentMode
        self.description = description
        self.status = status
        self.transactionId = transactionId
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.giftedAmount = giftedAmount
        self.giftTransactionId = giftTransactionId
        self.giftedAt = giftedAt
    }
}

/// Remboursement (partiel ou total) d'un prêt/emprunt.
public struct LoanPayment: Identifiable, Equatable, Hashable, Sendable {
    public var id: EntityID
    public var loanId: EntityID
    public var accountId: EntityID
    public var amount: MinorUnits
    /// Date ET heure du remboursement.
    public var date: EpochMillis
    public var note: String
    public var transactionId: EntityID
    public var createdAt: EpochMillis

    public init(
        id: EntityID,
        loanId: EntityID,
        accountId: EntityID,
        amount: MinorUnits,
        date: EpochMillis,
        note: String = "",
        transactionId: EntityID,
        createdAt: EpochMillis = 0
    ) {
        self.id = id
        self.loanId = loanId
        self.accountId = accountId
        self.amount = amount
        self.date = date
        self.note = note
        self.transactionId = transactionId
        self.createdAt = createdAt
    }
}
