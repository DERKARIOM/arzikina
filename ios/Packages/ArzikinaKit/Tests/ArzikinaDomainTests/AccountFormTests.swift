import ArzikinaDomain
import XCTest

final class AccountFormTests: XCTestCase {

    private func validate(
        _ draft: AccountDraft,
        existing: Account? = nil,
        confirmed: Bool = false,
        canonicalName: (String) -> String = { $0 }
    ) -> AccountForm.Outcome {
        AccountForm.validate(
            draft,
            existing: existing,
            newId: "new-id",
            newDisplayOrder: 7,
            currentYear: 2026,
            currentMonth: 10,
            confirmedSavingsGoalRemoval: confirmed,
            canonicalName: canonicalName
        )
    }

    private func account(_ outcome: AccountForm.Outcome, file: StaticString = #filePath, line: UInt = #line) throws -> Account {
        guard case .valid(let account) = outcome else {
            XCTFail("Compte valide attendu, reçu \(outcome)", file: file, line: line)
            throw XCTSkip()
        }
        return account
    }

    func testNewAccountGetsIdOrderAndParsedBalance() throws {
        let created = try account(validate(AccountDraft(name: "  Caisse  ", initialBalanceInput: "10 000,50")))
        XCTAssertEqual(created.id, "new-id")
        XCTAssertEqual(created.displayOrder, 7)
        XCTAssertEqual(created.name, "Caisse")
        XCTAssertEqual(created.initialBalance, 1_000_050)
    }

    func testRequiredNameAndValidBalance() {
        XCTAssertEqual(validate(AccountDraft(name: "   ")), .invalid(.nameRequired))
        XCTAssertEqual(validate(AccountDraft(name: "A", initialBalanceInput: "abc")), .invalid(.invalidInitialBalance))
        XCTAssertEqual(validate(AccountDraft(name: "A", initialBalanceInput: "")), .invalid(.invalidInitialBalance))
    }

    func testSavingsGoalNeedsStrictlyPositiveTarget() throws {
        var draft = AccountDraft(name: "Moto", type: .savingsGoal, savingsTargetInput: "0")
        XCTAssertEqual(validate(draft), .invalid(.invalidSavingsTarget))
        draft.savingsTargetInput = ""
        XCTAssertEqual(validate(draft), .invalid(.invalidSavingsTarget))
        draft.savingsTargetInput = "500 000"
        draft.savingsDescriptionInput = "  "
        let goal = try account(validate(draft))
        XCTAssertEqual(goal.savingsTargetAmount, 50_000_000)
        XCTAssertNil(goal.savingsDescription, "Description vide = absente")
    }

    func testTurningSavingsGoalBackIntoAccountAsksConfirmationThenClearsGoalFields() throws {
        let goal = Account(id: "g", name: "Moto", type: .savingsGoal, displayOrder: 3, savingsTargetAmount: 100, savingsDescription: "Yamaha", createdAt: 42)
        let draft = AccountDraft(name: "Moto", type: .savings)
        XCTAssertEqual(validate(draft, existing: goal), .needsSavingsGoalRemovalConfirmation)

        let updated = try account(validate(draft, existing: goal, confirmed: true))
        XCTAssertEqual(updated.id, "g", "Même compte : mêmes transactions")
        XCTAssertEqual(updated.displayOrder, 3)
        XCTAssertEqual(updated.createdAt, 42)
        XCTAssertNil(updated.savingsTargetAmount)
        XCTAssertNil(updated.savingsDescription)
    }

    func testCreditCardNeedsLastFourAndNonExpiredDate() throws {
        var draft = AccountDraft(name: "Visa", type: .creditCard, cardLastFourInput: "42", cardExpiryInput: "12/30")
        XCTAssertEqual(validate(draft), .invalid(.invalidCardLastFour))
        draft.cardLastFourInput = "4242"
        draft.cardExpiryInput = "09/26"
        XCTAssertEqual(validate(draft), .invalid(.invalidCardExpiry), "Expirée le mois dernier")
        draft.cardExpiryInput = "13/30"
        XCTAssertEqual(validate(draft), .invalid(.invalidCardExpiry))
        draft.cardExpiryInput = "10/26"
        let card = try account(validate(draft))
        XCTAssertEqual(card.cardLastFourDigits, "4242")
        XCTAssertEqual(card.cardExpiryMonth, 10)
        XCTAssertEqual(card.cardExpiryYear, 2026)
    }

    func testTypeSpecificFieldsAreClearedWhenTypeChanges() throws {
        let card = Account(id: "c", name: "Visa", type: .creditCard, cardLastFourDigits: "4242", cardExpiryMonth: 1, cardExpiryYear: 2030)
        let cleared = try account(validate(AccountDraft(name: "Visa", type: .bank), existing: card))
        XCTAssertNil(cleared.cardLastFourDigits)
        XCTAssertNil(cleared.cardExpiryMonth)

        let momo = Account(id: "m", name: "OM", type: .mobileMoney, mobileMoneyPackageName: "com.orange.om")
        let kept = try account(validate(AccountDraft(name: "OM", type: .mobileMoney), existing: momo))
        XCTAssertEqual(kept.mobileMoneyPackageName, "com.orange.om", "Réglé sur Android : conservé tel quel")
        let dropped = try account(validate(AccountDraft(name: "OM", type: .cash), existing: momo))
        XCTAssertNil(dropped.mobileMoneyPackageName)
    }

    func testDefaultNamesAreStoredUnderTheirReferenceName() throws {
        let labels: (DefaultAccountKey) -> [String] = { $0 == .cash ? ["Espèces", "Cash"] : [] }
        let created = try account(validate(AccountDraft(name: "Cash")) { DefaultAccountKey.canonicalName(for: $0, labelsOf: labels) })
        XCTAssertEqual(created.name, "Espèces")
        XCTAssertEqual(DefaultAccountKey.canonicalName(for: "Tontine", labelsOf: labels), "Tontine")
    }

    func testEditingPrefillsFromAccount() {
        let card = Account(id: "c", name: "Espèces", currencyCode: "XOF", initialBalance: 250_000, type: .creditCard, cardLastFourDigits: "0042", cardExpiryMonth: 3, cardExpiryYear: 2031)
        let draft = AccountDraft(editing: card, displayName: "Cash")
        XCTAssertEqual(draft.name, "Cash")
        XCTAssertEqual(draft.initialBalanceInput, "2 500")
        XCTAssertEqual(draft.cardExpiryInput, "03/31")
        XCTAssertEqual(draft.cardLastFourInput, "0042")
    }

    func testExpiryTypingAndDefaultIcons() {
        XCTAssertEqual(AccountForm.formatExpiryInput("0727"), "07/27")
        XCTAssertEqual(AccountForm.formatExpiryInput("07/2"), "07/2")
        XCTAssertEqual(AccountForm.formatExpiryInput("1"), "1")
        XCTAssertEqual(AccountForm.formatExpiryInput("12345"), "12/34")
        XCTAssertEqual(AccountForm.defaultIcon(for: .creditCard, current: .bank, isEditing: true), .creditCard)
        XCTAssertEqual(AccountForm.defaultIcon(for: .savingsGoal, current: .bank, isEditing: false), .savings)
        XCTAssertEqual(AccountForm.defaultIcon(for: .savingsGoal, current: .bank, isEditing: true), .bank)
        XCTAssertEqual(AccountForm.defaultIcon(for: .bank, current: .wallet, isEditing: false), .wallet)
    }
}
