import XCTest
@testable import ArzikinaDomain

/// Prêts et emprunts — comportements d'Android `LoanFormViewModel`, `LoansViewModel`,
/// `computeLoanProgressPercent`.
final class LoanRulesTests: XCTestCase {

    private let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Africa/Niamey")!
        return calendar
    }()
    private let day: EpochMillis = 24 * 60 * 60 * 1000
    private let now: EpochMillis = 1_790_000_000_000

    func testTransactionsCreatedByEachLoanType() {
        XCTAssertEqual(LoanType.lent.disbursementTransactionType, .expense)
        XCTAssertEqual(LoanType.lent.repaymentTransactionType, .income)
        XCTAssertEqual(LoanType.borrowed.disbursementTransactionType, .income)
        XCTAssertEqual(LoanType.borrowed.repaymentTransactionType, .expense)
        for type in LoanType.allCases {
            XCTAssertEqual(type.disbursementCategory.type, type.disbursementTransactionType, "Catégorie du bon type")
            XCTAssertEqual(type.repaymentCategory.type, type.repaymentTransactionType)
        }
    }

    func testFormValidationOrderAndDefaults() throws {
        var draft = LoanDraft(now: now)
        XCTAssertEqual(draft.dueDate - draft.startDate, 30 * day, "Échéance par défaut : 30 jours")
        func error() -> LoanFormError? {
            if case .failure(let error) = LoanForm.build(draft, existing: nil, newId: "l", newPaymentId: "pay", now: now) { return error }
            return nil
        }
        XCTAssertEqual(error(), .personRequired)
        draft.personId = "p"
        XCTAssertEqual(error(), .accountRequired)
        draft.accountId = "a"
        XCTAssertEqual(error(), .invalidAmount)
        draft.amountInput = "50000"
        draft.dueDate = draft.startDate
        XCTAssertEqual(error(), .dueBeforeStart, "L'échéance doit être APRÈS le début")
        draft.dueDate = draft.startDate + day
        draft.description = "  Moto  "
        let loan = try LoanForm.build(draft, existing: nil, newId: "l", newPaymentId: "pay", now: now).get().loan
        XCTAssertEqual(loan.amount, 5_000_000)
        XCTAssertEqual(loan.remainingAmount, 5_000_000)
        XCTAssertEqual(loan.description, "Moto")
        XCTAssertEqual(loan.reason, .other)
        XCTAssertEqual(loan.repaymentMode, .single)
    }

    private func summary(_ id: String, type: LoanType = .lent, amount: MinorUnits = 1_000, repaid: MinorUnits = 0, start: EpochMillis? = nil, due: EpochMillis? = nil, person: String = "Awa", description: String = "", currency: String = "XOF") -> LoanSummary {
        let loan = Loan(id: id, personId: "p-\(person)", accountId: "a", type: type, amount: amount, amountRepaid: 999_999, remainingAmount: 0, startDate: start ?? now - day, dueDate: due ?? now + day, description: description, status: .repaid, transactionId: "t")
        return LoanSummary(loan: loan, person: Person(id: "p-\(person)", name: person), currencyCode: currency, amountRepaid: repaid, now: now, calendar: calendar)
    }

    func testSummaryUsesPaymentsAndLiveStatusNotStoredValues() {
        let partial = summary("a", amount: 1_000, repaid: 333)
        XCTAssertEqual(partial.status, .ongoing, "Le statut stocké (« remboursé ») est ignoré")
        XCTAssertEqual(partial.remaining, 667)
        XCTAssertEqual(partial.progressPercent, 33, "Arrondi à l'inférieur, comme Android")
        XCTAssertEqual(summary("b", repaid: 1_000).status, .repaid)
        XCTAssertEqual(summary("c", due: now - 2 * day).status, .overdue)
        XCTAssertEqual(summary("d", start: now + day, due: now + 3 * day).status, .upcoming)
        XCTAssertEqual(summary("e", repaid: 5_000).remaining, 0)
        XCTAssertEqual(summary("e", repaid: 5_000).progressPercent, 100)
    }

    func testListOrderFiltersAndTotals() {
        let items = [
            summary("repaid", repaid: 1_000, person: "Boubé"),
            summary("lent", amount: 2_000, repaid: 500, person: "Awa", description: "Moto"),
            summary("borrowed", type: .borrowed, amount: 3_000, person: "Ibrahim"),
            summary("euro", amount: 100, person: "Marie", currency: "EUR"),
        ]
        XCTAssertEqual(LoanList.apply(items, filters: LoanFilters()).map(\.id), ["lent", "borrowed", "euro", "repaid"], "Remboursés en dernier")
        XCTAssertEqual(LoanList.apply(items, filters: LoanFilters(type: .borrowed)).map(\.id), ["borrowed"])
        XCTAssertEqual(LoanList.apply(items, filters: LoanFilters(status: .repaid)).map(\.id), ["repaid"])
        XCTAssertEqual(LoanList.apply(items, filters: LoanFilters(query: " moto")).map(\.id), ["lent"])
        XCTAssertEqual(LoanList.apply(items, filters: LoanFilters(query: "IBRA")).map(\.id), ["borrowed"])

        XCTAssertEqual(LoanList.remainingByCurrency(items, type: .lent), [
            CurrencyAmount(currencyCode: "XOF", amountMinor: 1_500),
            CurrencyAmount(currencyCode: "EUR", amountMinor: 100),
        ])
        XCTAssertEqual(LoanList.remainingByCurrency(items, type: .borrowed), [CurrencyAmount(currencyCode: "XOF", amountMinor: 3_000)])
    }

    func testFirstPaymentOnCreation() throws {
        var draft = LoanDraft(now: now)
        draft.personId = "p"; draft.accountId = "a"; draft.amountInput = "100"
        func result() -> Result<(loan: Loan, firstPayment: LoanPayment?), LoanFormError> {
            LoanForm.build(draft, existing: nil, newId: "l", newPaymentId: "pay", now: now)
        }
        XCTAssertNil(try result().get().firstPayment, "Vide : aucun remboursement")
        draft.firstPaymentInput = "abc"
        if case .failure(let e) = result() { XCTAssertEqual(e, .invalidFirstPayment) } else { XCTFail() }
        draft.firstPaymentInput = "150"
        if case .failure(let e) = result() { XCTAssertEqual(e, .firstPaymentExceedsAmount) } else { XCTFail() }
        draft.firstPaymentInput = "40"
        draft.firstPaymentDate = draft.startDate - 1
        if case .failure(let e) = result() { XCTAssertEqual(e, .firstPaymentBeforeStart) } else { XCTFail() }
        draft.firstPaymentDate = draft.startDate
        let payment = try XCTUnwrap(try result().get().firstPayment)
        XCTAssertEqual(payment.amount, 4_000)
        XCTAssertEqual(payment.loanId, "l")
        XCTAssertEqual(payment.accountId, "a")
    }

    func testEditingKeepsTypeAndRefusesAmountBelowRepaid() throws {
        let existing = Loan(id: "l", personId: "p", accountId: "a", type: .borrowed, amount: 10_000, remainingAmount: 10_000, startDate: now, dueDate: now + day, transactionId: "t", createdAt: 7)
        var draft = LoanDraft(editing: existing)
        XCTAssertEqual(draft.amountInput, "100")
        draft.type = .lent
        draft.amountInput = "30"
        draft.firstPaymentInput = "10"
        if case .failure(let e) = LoanForm.build(draft, existing: existing, repaid: 4_000, newId: "x", newPaymentId: "y", now: now) {
            XCTAssertEqual(e, .amountBelowRepaid)
        } else { XCTFail() }
        draft.amountInput = "50"
        let result = try LoanForm.build(draft, existing: existing, repaid: 4_000, newId: "x", newPaymentId: "y", now: now).get()
        XCTAssertEqual(result.loan.id, "l")
        XCTAssertEqual(result.loan.type, .borrowed, "Le type n'est jamais modifié")
        XCTAssertEqual(result.loan.transactionId, "t")
        XCTAssertEqual(result.loan.remainingAmount, 1_000)
        XCTAssertNil(result.firstPayment, "Pas de « premier remboursement » en modification")
    }

    func testPaymentValidation() throws {
        let loanSummary = summary("l", amount: 1_000, repaid: 600)
        var draft = LoanPaymentDraft(loan: loanSummary.loan, now: now)
        XCTAssertEqual(draft.accountId, "a", "Compte du prêt présélectionné")
        func error() -> LoanPaymentFormError? {
            if case .failure(let e) = LoanPaymentForm.build(draft, summary: loanSummary, newId: "p", now: now) { return e }
            return nil
        }
        XCTAssertEqual(error(), .invalidAmount)
        draft.amountInput = "4.01"
        XCTAssertEqual(error(), .exceedsRemaining)
        draft.amountInput = "4"
        draft.date = loanSummary.loan.startDate - 1
        XCTAssertEqual(error(), .beforeStart)
        draft.date = now
        draft.note = "  espèces "
        let payment = try LoanPaymentForm.build(draft, summary: loanSummary, newId: "p", now: now).get()
        XCTAssertEqual(payment.amount, 400)
        XCTAssertEqual(payment.note, "espèces")
    }
}
