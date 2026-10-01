import ArzikinaDomain
import Foundation
import XCTest

final class TransactionFormTests: XCTestCase {

    private let names: (EntityID) -> String? = { ["cash": "Espèces", "bank": "Banque", "om": "Orange Money"][$0] }

    private func build(_ draft: TransactionDraft, existing: Transaction? = nil) -> Result<(transaction: Transaction, fee: TransactionFee?), TransactionFormError> {
        TransactionForm.build(draft, existing: existing, newId: "new", now: 777)
    }

    private func error(_ draft: TransactionDraft) -> TransactionFormError? {
        if case .failure(let error) = build(draft) { return error }
        return nil
    }

    func testValidationOrderMatchesAndroid() {
        var draft = TransactionDraft(now: 1)
        XCTAssertEqual(error(draft), .invalidAmount)
        draft.amountInput = "0"
        XCTAssertEqual(error(draft), .invalidAmount)
        draft.amountInput = "1 500"
        XCTAssertEqual(error(draft), .accountRequired)
        draft.changeAccount("cash", accountName: names)
        XCTAssertEqual(error(draft), .categoryRequired)
        draft.changeType(.transfer, accountName: names)
        XCTAssertEqual(error(draft), .destinationRequired)
        draft.changeTransferAccount("cash", accountName: names)
        XCTAssertEqual(error(draft), .sameTransferAccount)
        draft.changeTransferAccount("bank", accountName: names)
        draft.hasFee = true
        XCTAssertEqual(error(draft), .invalidFeeAmount)
        draft.feeAmountInput = "100"
        XCTAssertNil(error(draft))
    }

    func testTransferHasNoCategoryAndOthersNoDestination() throws {
        var draft = TransactionDraft(now: 1, presetAccountId: "cash")
        draft.amountInput = "10"
        draft.categoryId = "food"
        draft.changeType(.transfer, accountName: names)
        XCTAssertNil(draft.categoryId, "Changer de type réinitialise la catégorie")
        draft.changeTransferAccount("bank", accountName: names)
        let transfer = try build(draft).get().transaction
        XCTAssertNil(transfer.categoryId)
        XCTAssertEqual(transfer.transferAccountId, "bank")

        draft.changeType(.income, accountName: names)
        XCTAssertNil(draft.transferAccountId, "Le compte destination n'a de sens que pour un transfert")
    }

    func testTransferDescriptionIsAutoFilledButNeverOverridesTypedText() {
        var draft = TransactionDraft(now: 1, presetAccountId: "cash")
        draft.changeType(.transfer, accountName: names)
        draft.changeTransferAccount("bank", accountName: names)
        XCTAssertEqual(draft.description, "Espèces → Banque")
        draft.changeAccount("om", accountName: names)
        XCTAssertEqual(draft.description, "Orange Money → Banque", "Régénérée tant qu'elle est automatique")
        draft.changeType(.expense, accountName: names)
        XCTAssertEqual(draft.description, "", "Description automatique effacée en quittant le transfert")

        draft.changeDescription("Loyer octobre", accountName: names)
        draft.changeType(.transfer, accountName: names)
        draft.changeTransferAccount("cash", accountName: names)
        XCTAssertEqual(draft.description, "Loyer octobre", "Une description tapée n'est jamais écrasée")
    }

    func testFeeAccountFollowsSourceUntilChosen() throws {
        var draft = TransactionDraft(now: 1, presetAccountId: "cash")
        draft.hasFee = true
        XCTAssertEqual(draft.feeAccountId, "cash")
        draft.changeAccount("om", accountName: names)
        XCTAssertEqual(draft.feeAccountId, "om")
        draft.changeFeeAccount("bank")
        draft.changeAccount("cash", accountName: names)
        XCTAssertEqual(draft.feeAccountId, "bank")

        draft.amountInput = "5 000"
        draft.categoryId = "c"
        draft.feeAmountInput = "150"
        draft.feeType = .commission
        draft.feeDescription = "  guichet  "
        let fee = try XCTUnwrap(try build(draft).get().fee)
        XCTAssertEqual(fee, TransactionFee(amount: 15_000, accountId: "bank", type: .commission, description: "guichet"))
    }

    func testEditingKeepsHiddenFieldsAndPrefillsFee() throws {
        let existing = Transaction(id: "t", amount: 250_000, type: .expense, accountId: "om", categoryId: "food", date: 99,
                                   description: "Marché", latitude: 13.5, longitude: 2.1, paymentMethod: .mobileMoney,
                                   feeTransactionId: "f", createdAt: 42)
        let feeRow = Transaction(id: "f", amount: 500, type: .expense, accountId: "om", date: 99, feeType: .service)
        var draft = TransactionDraft(editing: existing, fee: feeRow)
        XCTAssertEqual(draft.amountInput, "2 500")
        XCTAssertTrue(draft.hasFee)
        XCTAssertEqual(draft.feeAmountInput, "5")
        XCTAssertEqual(draft.feeType, .service)

        draft.amountInput = "3 000"
        let (saved, fee) = try build(draft, existing: existing).get()
        XCTAssertEqual(saved.id, "t")
        XCTAssertEqual(saved.amount, 300_000)
        XCTAssertEqual(saved.latitude, 13.5)
        XCTAssertEqual(saved.createdAt, 42)
        XCTAssertEqual(saved.paymentMethod, .mobileMoney)
        XCTAssertEqual(fee?.amount, 500)
    }

    func testLoanAndFeeCategoriesAreNotSelectable() {
        XCTAssertFalse(TransactionForm.isSelectable(Category(id: "1", name: "Frais et commissions", type: .expense)))
        XCTAssertFalse(TransactionForm.isSelectable(Category(id: "2", name: "Prêt accordé", type: .expense)))
        XCTAssertFalse(TransactionForm.isSelectable(Category(id: "3", name: "Emprunt reçu", type: .income)))
        XCTAssertTrue(TransactionForm.isSelectable(Category(id: "4", name: "Nourriture", type: .expense)))
        XCTAssertTrue(TransactionForm.isSelectable(Category(id: "5", name: "Tontine", type: .expense)))
    }
}
