import XCTest
@testable import ArzikinaDomain

/// « Transformer en cadeau » — mêmes cas qu'Android `LoanGiftTest` / Web `loan-gift.test.ts`.
final class LoanGiftTests: XCTestCase {

    private let calendar = ArzikinaCalendar.make(timeZone: TimeZone(identifier: "Africa/Niamey")!)
    private let day: EpochMillis = 24 * 60 * 60 * 1000
    private let now: EpochMillis = 1_790_000_000_000

    private func loan(_ type: LoanType = .lent, amount: MinorUnits = 100_000, gifted: MinorUnits = 0, start: EpochMillis? = nil, due: EpochMillis? = nil) -> Loan {
        Loan(id: "l", personId: "p", accountId: "a", type: type, amount: amount, remainingAmount: amount, startDate: start ?? now - 10 * day, dueDate: due ?? now + 10 * day, transactionId: "t", giftedAmount: gifted)
    }

    func testNothingRepaidReclassifiesTheDisbursementInPlace() throws {
        let plan = try XCTUnwrap(LoanGift.plan(loan(), repaid: 0, now: now, calendar: calendar))
        XCTAssertEqual(plan.giftAmount, 100_000)
        XCTAssertEqual(plan.disbursementAmountAfter, 0)
        XCTAssertTrue(plan.reusesDisbursementTransaction)
    }

    func testPartialRepaymentGivesOnlyTheRest() throws {
        let plan = try XCTUnwrap(LoanGift.plan(loan(), repaid: 40_000, now: now, calendar: calendar))
        XCTAssertEqual(plan.giftAmount, 60_000)
        XCTAssertEqual(plan.disbursementAmountAfter, 40_000)
        XCTAssertFalse(plan.reusesDisbursementTransaction)
        XCTAssertEqual(plan.giftAmount + plan.disbursementAmountAfter, 100_000, "Solde inchangé")
    }

    func testRefusedWhenRepaidOrAlreadyGiftedButAllowedOverdueOrUpcoming() {
        XCTAssertNil(LoanGift.plan(loan(), repaid: 100_000, now: now, calendar: calendar), "Entièrement remboursé")
        XCTAssertNil(LoanGift.plan(loan(gifted: 60_000), repaid: 40_000, now: now, calendar: calendar), "Déjà transformé")
        XCTAssertNotNil(LoanGift.plan(loan(due: now - 5 * day), repaid: 0, now: now, calendar: calendar), "En retard")
        XCTAssertNotNil(LoanGift.plan(loan(start: now + day, due: now + 5 * day), repaid: 0, now: now, calendar: calendar), "À venir")
    }

    func testGiftCategoryFollowsTheLoanType() {
        XCTAssertEqual(LoanType.lent.giftCategory, .gifts)
        XCTAssertEqual(LoanType.lent.giftCategory.type, .expense, "Même sens que le décaissement")
        XCTAssertEqual(LoanType.borrowed.giftCategory, .giftsReceived)
        XCTAssertEqual(LoanType.borrowed.giftCategory.type, .income)
    }

    func testSummaryOffersTheAction() {
        func summary(_ loan: Loan, repaid: MinorUnits) -> LoanSummary {
            LoanSummary(loan: loan, person: nil, currencyCode: "XOF", amountRepaid: repaid, now: now, calendar: calendar)
        }
        XCTAssertTrue(summary(loan(), repaid: 40_000).canConvertToGift)
        XCTAssertFalse(summary(loan(), repaid: 100_000).canConvertToGift)
        XCTAssertFalse(summary(loan(gifted: 60_000), repaid: 40_000).canConvertToGift)
    }

    func testFrenchElision() {
        for name in ["Aïcha", "Ève", "ousmane", "  Issa", "Ëlle", "Ali"] {
            XCTAssertTrue(FrenchElision.requiresElision(name), name)
        }
        for name in ["Moussa", "Halima", "Yacouba", "Hamidou", "", "  "] {
            XCTAssertFalse(FrenchElision.requiresElision(name), name)
        }
    }
}
