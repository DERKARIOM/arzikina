import Foundation
import XCTest
@testable import ArzikinaDomain

/// Exécute chaque jeu de `shared/test-fixtures/` contre les règles Swift. Le même contenu est
/// vérifié côté Android par `SharedFixturesTest.kt` : les deux doivent passer.
final class SharedFixtureTests: XCTestCase {

    // MARK: - Montants

    func testMoneyParse() throws {
        let fixture = try SharedFixtures.load("money.json")
        for testCase in try fixture.objects("parse") {
            let input = try testCase.string("input")
            XCTAssertEqual(Money.parseToMinorUnits(input), testCase.optionalInt64("expected"), "parse « \(input) »")
        }
    }

    func testMoneyFormatting() throws {
        let fixture = try SharedFixtures.load("money.json")
        for testCase in try fixture.objects("formatForInput") {
            XCTAssertEqual(Money.formatForInput(try testCase.int64("minor")), try testCase.string("expected"))
        }
        for testCase in try fixture.objects("formatAmount") {
            XCTAssertEqual(Money.formatAmount(try testCase.int64("minor")), try testCase.string("expected"))
        }
        for testCase in try fixture.objects("formatWithCurrency") {
            let amount = CurrencyAmount(currencyCode: try testCase.string("currency"), amountMinor: try testCase.int64("minor"))
            XCTAssertEqual(Money.format(amount), try testCase.string("expected"))
        }
    }

    // MARK: - Objectif d'épargne

    func testSavingsGoal() throws {
        for testCase in try SharedFixtures.load("savings-goal.json").objects("cases") {
            let name = try testCase.string("name")
            let snapshot = SavingsGoalProgress.snapshot(balance: try testCase.int64("balance"), target: testCase.optionalInt64("target"))
            guard let expected = testCase.optionalObject("expected") else {
                XCTAssertNil(snapshot, name)
                continue
            }
            let actual = try XCTUnwrap(snapshot, name)
            XCTAssertEqual(actual.saved, try expected.int64("saved"), name)
            XCTAssertEqual(actual.remaining, try expected.int64("remaining"), name)
            XCTAssertEqual(actual.exceededBy, try expected.int64("exceededBy"), name)
            XCTAssertEqual(actual.percent, try expected.int("percent"), name)
            XCTAssertEqual(actual.isReached, try expected.bool("isReached"), name)
        }
    }

    // MARK: - Prêts

    func testLoanStatus() throws {
        let fixture = try SharedFixtures.load("loan-status.json")
        let calendar = try SharedFixtures.calendar(try fixture.string("timeZone"))
        for testCase in try fixture.objects("cases") {
            let status = LoanStatusRule.status(
                amount: try testCase.int64("amount"),
                amountRepaid: try testCase.int64("amountRepaid"),
                startDate: try SharedFixtures.millis(try testCase.string("startDate"), calendar: calendar),
                dueDate: try SharedFixtures.millis(try testCase.string("dueDate"), calendar: calendar),
                now: try SharedFixtures.millis(try testCase.string("now"), calendar: calendar),
                calendar: calendar
            )
            let name = try testCase.string("name")
            XCTAssertEqual(status.rawValue, try testCase.string("expected"), name)
        }
    }

    // MARK: - Soldes

    func testAccountBalances() throws {
        let fixture = try SharedFixtures.load("account-balances.json")
        let accounts = try fixture.objects("accounts").map {
            Account(id: try $0.string("id"), name: try $0.string("id"), initialBalance: try $0.int64("initialBalance"))
        }
        let transactions = try fixture.objects("transactions").enumerated().map { index, item in
            Transaction(
                id: "t\(index)",
                amount: try item.int64("amount"),
                type: try XCTUnwrap(TransactionType(rawValue: try item.string("type"))),
                accountId: try item.string("accountId"),
                transferAccountId: item.optionalString("transferAccountId"),
                date: 0
            )
        }
        let balances = AccountBalances.compute(accounts: accounts, transactions: transactions)
        let expected = try fixture.object("expected")
        XCTAssertEqual(balances.count, expected.count, "Un solde par compte connu, jamais pour un compte inconnu")
        for (accountId, value) in expected {
            XCTAssertEqual(balances[accountId], (value as? NSNumber)?.int64Value, accountId)
        }
    }

    // MARK: - Budgets

    func testBudgets() throws {
        let fixture = try SharedFixtures.load("budget.json")
        let calendar = try SharedFixtures.calendar(try fixture.string("timeZone"))
        for testCase in try fixture.objects("cases") {
            let name = try testCase.string("name")
            let today = try SharedFixtures.day(try testCase.string("today"))
            let budgetJSON = try testCase.object("budget")
            let budget = Budget(
                id: "budget",
                categoryId: try budgetJSON.string("categoryId"),
                period: try XCTUnwrap(BudgetPeriod(rawValue: try budgetJSON.string("period"))),
                limitAmount: try budgetJSON.int64("limitAmount"),
                currencyCode: try budgetJSON.string("currencyCode"),
                startDate: try budgetJSON.optionalString("startDate").map { try SharedFixtures.millis($0, calendar: calendar) },
                endDate: try budgetJSON.optionalString("endDate").map { try SharedFixtures.millis($0, calendar: calendar) }
            )
            var accountsById: [EntityID: Account] = [:]
            for item in try testCase.objects("accounts") {
                let id = try item.string("id")
                accountsById[id] = Account(id: id, name: id, currencyCode: try item.string("currencyCode"))
            }
            let transactions = try testCase.objects("transactions").enumerated().map { index, item in
                Transaction(
                    id: "t\(index)",
                    amount: try item.int64("amount"),
                    type: try XCTUnwrap(TransactionType(rawValue: try item.string("type"))),
                    accountId: try item.string("accountId"),
                    categoryId: try item.string("categoryId"),
                    date: try SharedFixtures.millis(try item.string("date"), calendar: calendar)
                )
            }

            let expected = try testCase.object("expected")
            let progress = BudgetProgress.compute(
                budget: budget, transactions: transactions, accountsById: accountsById, today: today, calendar: calendar
            )
            XCTAssertEqual(progress.spent, try expected.int64("spent"), name)
            XCTAssertEqual(progress.progress, try expected.double("progress"), accuracy: 1e-6, name)
            XCTAssertEqual(
                BudgetPeriodStatus.of(budget: budget, today: today, calendar: calendar)?.rawValue,
                expected.optionalString("periodStatus"),
                name
            )

            let pace = BudgetPace.of(budget: budget, spent: progress.spent, today: today, calendar: calendar)
            let expectedPace = try expected.object("pace")
            XCTAssertEqual(pace.periodStatus.rawValue, try expectedPace.string("periodStatus"), name)
            XCTAssertEqual(pace.periodStart, try SharedFixtures.day(try expectedPace.string("periodStart")), name)
            XCTAssertEqual(pace.periodEnd, try SharedFixtures.day(try expectedPace.string("periodEnd")), name)
            XCTAssertEqual(pace.totalDays, try expectedPace.int("totalDays"), name)
            XCTAssertEqual(pace.elapsedDays, try expectedPace.int("elapsedDays"), name)
            XCTAssertEqual(pace.daysRemaining, try expectedPace.int("daysRemaining"), name)
            XCTAssertEqual(pace.paceState.rawValue, try expectedPace.string("paceState"), name)
        }
    }

    // MARK: - Automatisations

    func testRecurrenceNextDate() throws {
        let fixture = try SharedFixtures.load("recurrence.json")
        let defaultZone = try fixture.string("timeZone")
        for testCase in try fixture.objects("next") {
            let calendar = try SharedFixtures.calendar(testCase.optionalString("timeZone") ?? defaultZone)
            let date = try testCase.string("date")
            let frequency = try XCTUnwrap(RecurringFrequency(rawValue: try testCase.string("frequency")))
            let expected = try testCase.optionalString("expected").map { try SharedFixtures.millis($0, calendar: calendar) }
            XCTAssertEqual(
                Recurrence.nextExecutionDate(after: try SharedFixtures.millis(date, calendar: calendar), frequency: frequency, calendar: calendar),
                expected,
                "\(frequency.rawValue) après \(date)"
            )
        }
    }

    func testRecurrenceMissingDates() throws {
        let fixture = try SharedFixtures.load("recurrence.json")
        let calendar = try SharedFixtures.calendar(try fixture.string("timeZone"))
        for testCase in try fixture.objects("missing") {
            let dates = Recurrence.missingScheduledDates(
                nextExecutionDate: try SharedFixtures.millis(try testCase.string("nextExecutionDate"), calendar: calendar),
                frequency: try XCTUnwrap(RecurringFrequency(rawValue: try testCase.string("frequency"))),
                endDate: try testCase.optionalString("endDate").map { try SharedFixtures.millis($0, calendar: calendar) },
                now: try SharedFixtures.millis(try testCase.string("now"), calendar: calendar),
                triggerHour: try testCase.int("triggerHour"),
                triggerMinute: try testCase.int("triggerMinute"),
                calendar: calendar
            )
            let expected = try testCase.strings("expected").map { try SharedFixtures.millis($0, calendar: calendar) }
            let name = try testCase.string("name")
            XCTAssertEqual(dates, expected, name)
        }
    }

    // MARK: - Authentification

    func testAuthValidation() throws {
        let fixture = try SharedFixtures.load("auth-validation.json")
        let predicates: [(String, (String) -> Bool)] = [
            ("email", AuthValidator.isValidEmail),
            ("username", AuthValidator.isValidUsername),
            ("passwordLongEnough", AuthValidator.isPasswordLongEnough),
            ("securityAnswerLongEnough", AuthValidator.isSecurityAnswerLongEnough)
        ]
        for (group, predicate) in predicates {
            for testCase in try fixture.objects(group) {
                let input = try testCase.string("input")
                XCTAssertEqual(predicate(input), try testCase.bool("expected"), "\(group) « \(input) »")
            }
        }
        for testCase in try fixture.objects("normalizeSecurityAnswer") {
            XCTAssertEqual(AuthValidator.normalizeSecurityAnswer(try testCase.string("input")), try testCase.string("expected"))
        }
    }

    // MARK: - Planification

    func testFinancialPlan() throws {
        for testCase in try SharedFixtures.load("financial-plan.json").objects("cases") {
            let name = try testCase.string("name")
            let available = try testCase.int64("available")
            let items = try testCase.objects("items").enumerated().map { index, item in
                FinancialPlanItem(
                    id: "i\(index)",
                    planId: "plan",
                    name: "item",
                    amount: try item.int64("amount"),
                    status: try XCTUnwrap(PlanItemStatus(rawValue: try item.string("status")))
                )
            }
            let expected = try testCase.object("expected")
            let total = FinancialPlanProgress.totalPlanned(items)
            XCTAssertEqual(total, try expected.int64("totalPlanned"), name)
            XCTAssertEqual(FinancialPlanProgress.remaining(available: available, totalPlanned: total), try expected.int64("remaining"), name)
            XCTAssertEqual(FinancialPlanProgress.progress(available: available, totalPlanned: total), try expected.int("progress"), name)
            XCTAssertEqual(FinancialPlanProgress.isOverBudget(available: available, totalPlanned: total), try expected.bool("isOverBudget"), name)
        }
    }
}
