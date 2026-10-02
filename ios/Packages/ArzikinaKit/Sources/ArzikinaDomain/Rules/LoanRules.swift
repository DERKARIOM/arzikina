import Foundation

/// Transactions créées par un prêt / emprunt — Android `LoanRepositoryImpl` : le décaissement
/// (prêt accordé = dépense, emprunt reçu = revenu) et chaque remboursement (sens inverse), chacun
/// dans sa catégorie système.
extension LoanType {
    public var disbursementTransactionType: TransactionType { self == .lent ? .expense : .income }
    public var disbursementCategory: SystemCategoryKey { self == .lent ? .loanDisbursementLent : .loanDisbursementBorrowed }
    public var repaymentTransactionType: TransactionType { self == .lent ? .income : .expense }
    public var repaymentCategory: SystemCategoryKey { self == .lent ? .loanRepaymentLent : .loanRepaymentBorrowed }
}

/// Saisie du formulaire de prêt / emprunt — Android `LoanFormState` (étapes 1 et 2).
public struct LoanDraft: Equatable, Sendable {
    public var type: LoanType
    public var personId: EntityID?
    public var accountId: EntityID?
    public var amountInput: String
    /// Date ET heure de début.
    public var startDate: EpochMillis
    public var dueDate: EpochMillis
    public var description: String
    /// Premier remboursement facultatif, à la création seulement (vide = aucun).
    public var firstPaymentInput: String
    public var firstPaymentDate: EpochMillis

    /// Échéance par défaut : 30 jours après le début, comme Android.
    public static let defaultTermMillis: EpochMillis = 30 * 24 * 60 * 60 * 1000

    public init(now: EpochMillis, type: LoanType = .lent) {
        self.type = type
        personId = nil
        accountId = nil
        amountInput = ""
        startDate = now
        dueDate = now + Self.defaultTermMillis
        description = ""
        firstPaymentInput = ""
        firstPaymentDate = now
    }

    /// Modification d'un prêt existant.
    public init(editing loan: Loan) {
        self.init(now: loan.startDate, type: loan.type)
        personId = loan.personId
        accountId = loan.accountId
        amountInput = Money.formatForInput(loan.amount)
        dueDate = loan.dueDate
        description = loan.description
    }
}

/// Même ordre de vérification qu'Android (`LoanFormViewModel.goToStep2` puis `save`).
public enum LoanFormError: Error, Equatable, Sendable {
    case personRequired
    case accountRequired
    case invalidAmount
    /// Modification : le montant ne peut pas descendre sous ce qui a déjà été remboursé.
    case amountBelowRepaid
    case dueBeforeStart
    case invalidFirstPayment
    case firstPaymentExceedsAmount
    case firstPaymentBeforeStart
}

public enum LoanForm {

    /// Prêt à enregistrer et son premier remboursement éventuel.
    ///
    /// - Création ([existing] `nil`) : raison « Autre » et remboursement « en une fois », comme
    ///   Android ; le dépôt crée la transaction de décaissement.
    /// - Modification : le TYPE n'est jamais changé (la transaction de décaissement et les
    ///   remboursements existants sont dans le sens du type d'origine) ; [repaid] = montant déjà
    ///   remboursé, plancher du nouveau montant.
    /// - Prêt transformé en cadeau : personne, compte et montant restent ceux de [existing]
    ///   (verrouillés, comme Android) ; dates, description et le reste restent modifiables.
    public static func build(
        _ draft: LoanDraft,
        existing: Loan?,
        repaid: MinorUnits = 0,
        newId: @autoclosure () -> EntityID,
        newPaymentId: @autoclosure () -> EntityID,
        now: EpochMillis
    ) -> Result<(loan: Loan, firstPayment: LoanPayment?), LoanFormError> {
        let locked = existing.flatMap { $0.isGifted ? $0 : nil }
        guard let personId = locked?.personId ?? draft.personId else { return .failure(.personRequired) }
        guard let accountId = locked?.accountId ?? draft.accountId else { return .failure(.accountRequired) }
        guard let amount = locked?.amount ?? Money.parseToMinorUnits(draft.amountInput), amount > 0 else { return .failure(.invalidAmount) }
        if existing != nil, locked == nil, amount < repaid { return .failure(.amountBelowRepaid) }
        guard draft.dueDate > draft.startDate else { return .failure(.dueBeforeStart) }

        var firstPayment: (amount: MinorUnits, date: EpochMillis)?
        let firstInput = draft.firstPaymentInput.trimmingCharacters(in: .whitespacesAndNewlines)
        if existing == nil, !firstInput.isEmpty {
            guard let paid = Money.parseToMinorUnits(firstInput), paid > 0 else { return .failure(.invalidFirstPayment) }
            guard paid <= amount else { return .failure(.firstPaymentExceedsAmount) }
            guard draft.firstPaymentDate >= draft.startDate else { return .failure(.firstPaymentBeforeStart) }
            firstPayment = (paid, draft.firstPaymentDate)
        }

        var loan = existing ?? Loan(
            id: newId(), personId: personId, accountId: accountId, type: draft.type, amount: amount,
            remainingAmount: amount, startDate: draft.startDate, dueDate: draft.dueDate,
            reason: .other, repaymentMode: .single, transactionId: "", createdAt: now, updatedAt: now
        )
        loan.personId = personId
        loan.accountId = accountId
        loan.amount = amount
        loan.startDate = draft.startDate
        loan.dueDate = draft.dueDate
        loan.description = draft.description.trimmingCharacters(in: .whitespacesAndNewlines)
        loan.amountRepaid = repaid
        loan.remainingAmount = max(amount - repaid - loan.giftedAmount, 0)

        let payment = firstPayment.map {
            LoanPayment(id: newPaymentId(), loanId: loan.id, accountId: accountId, amount: $0.amount, date: $0.date, transactionId: "", createdAt: now)
        }
        return .success((loan, payment))
    }
}

/// Saisie d'un remboursement — Android `LoanPaymentFormState`.
public struct LoanPaymentDraft: Equatable, Sendable {
    public var accountId: EntityID?
    public var amountInput: String
    public var date: EpochMillis
    public var note: String

    /// Compte du prêt présélectionné, comme Android.
    public init(loan: Loan, now: EpochMillis) {
        accountId = loan.accountId
        amountInput = ""
        date = now
        note = ""
    }
}

public enum LoanPaymentFormError: Error, Equatable, Sendable {
    case accountRequired
    case invalidAmount
    case exceedsRemaining
    /// Écart volontaire avec Android, qui ne le vérifie que pour le premier remboursement.
    case beforeStart
    /// Prêt transformé en cadeau : plus aucun remboursement (comme Android et le Web).
    case loanGifted
}

public enum LoanPaymentForm {

    public static func build(
        _ draft: LoanPaymentDraft,
        summary: LoanSummary,
        newId: @autoclosure () -> EntityID,
        now: EpochMillis
    ) -> Result<LoanPayment, LoanPaymentFormError> {
        guard !summary.loan.isGifted else { return .failure(.loanGifted) }
        guard let accountId = draft.accountId else { return .failure(.accountRequired) }
        guard let amount = Money.parseToMinorUnits(draft.amountInput), amount > 0 else { return .failure(.invalidAmount) }
        guard amount <= summary.remaining else { return .failure(.exceedsRemaining) }
        guard draft.date >= summary.loan.startDate else { return .failure(.beforeStart) }
        return .success(LoanPayment(
            id: newId(),
            loanId: summary.loan.id,
            accountId: accountId,
            amount: amount,
            date: draft.date,
            note: draft.note.trimmingCharacters(in: .whitespacesAndNewlines),
            transactionId: "",
            createdAt: now
        ))
    }
}

/// Un prêt / emprunt tel qu'une liste l'affiche : personne, devise, montant remboursé (TOUJOURS
/// la somme de ses remboursements, jamais la valeur stockée, qui peut être en retard sur une
/// synchronisation) et statut recalculé à l'instant de l'affichage.
///
/// Reste dû = montant − remboursé − part offerte (Android `outstandingAmount`).
public struct LoanSummary: Identifiable, Equatable, Sendable {
    public let loan: Loan
    public let person: Person?
    public let currencyCode: String
    public let amountRepaid: MinorUnits
    public let status: LoanStatus

    public var id: EntityID { loan.id }
    public var remaining: MinorUnits { max(loan.amount - amountRepaid - loan.giftedAmount, 0) }
    /// 0…100, arrondi à l'inférieur — Android `computeLoanProgressPercent`.
    public var progressPercent: Int {
        guard loan.amount > 0 else { return 0 }
        return Int(min(max(amountRepaid * 100 / loan.amount, 0), 100))
    }

    public init(loan: Loan, person: Person?, currencyCode: String, amountRepaid: MinorUnits, now: EpochMillis, calendar: Calendar) {
        self.loan = loan
        self.person = person
        self.currencyCode = currencyCode
        self.amountRepaid = amountRepaid
        status = LoanStatusRule.status(
            amount: loan.amount,
            amountRepaid: amountRepaid,
            startDate: loan.startDate,
            dueDate: loan.dueDate,
            now: now,
            calendar: calendar,
            giftedAmount: loan.giftedAmount
        )
    }
}

/// Filtres de la liste — Android `LoanFilters`.
public struct LoanFilters: Equatable, Sendable {
    public var query: String
    /// `nil` = tous.
    public var type: LoanType?
    public var status: LoanStatus?

    public init(query: String = "", type: LoanType? = nil, status: LoanStatus? = nil) {
        self.query = query
        self.type = type
        self.status = status
    }

    public var hasActiveFilters: Bool { type != nil || status != nil }
}

public enum LoanList {

    /// Lignes visibles : dettes éteintes (remboursées ou offertes) en dernier (ordre conservé sinon), puis filtres et recherche
    /// sur le nom de la personne et la description — Android `LoansViewModel`.
    public static func apply(_ summaries: [LoanSummary], filters: LoanFilters) -> [LoanSummary] {
        let query = filters.query.trimmingCharacters(in: .whitespacesAndNewlines)
        let ordered = summaries.filter { !$0.status.isSettled } + summaries.filter { $0.status.isSettled }
        return ordered.filter { summary in
            (filters.type == nil || summary.loan.type == filters.type)
                && (filters.status == nil || summary.status == filters.status)
                && (query.isEmpty
                    || (summary.person?.name.range(of: query, options: .caseInsensitive) != nil)
                    || summary.loan.description.range(of: query, options: .caseInsensitive) != nil)
        }
    }

    /// Reste à recevoir (prêts) ou à rembourser (emprunts), par devise, dans l'ordre d'apparition.
    public static func remainingByCurrency(_ summaries: [LoanSummary], type: LoanType) -> [CurrencyAmount] {
        var order: [String] = []
        var totals: [String: MinorUnits] = [:]
        for summary in summaries where summary.loan.type == type {
            if totals[summary.currencyCode] == nil { order.append(summary.currencyCode) }
            totals[summary.currencyCode, default: 0] += summary.remaining
        }
        return order.map { CurrencyAmount(currencyCode: $0, amountMinor: totals[$0]!) }
    }
}
