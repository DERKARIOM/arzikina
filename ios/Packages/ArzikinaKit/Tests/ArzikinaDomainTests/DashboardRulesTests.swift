import ArzikinaDomain
import Foundation
import XCTest

final class DashboardRulesTests: XCTestCase {

    private let calendar = ArzikinaCalendar.make(timeZone: TimeZone(identifier: "Africa/Niamey")!)

    private func millis(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 0) -> EpochMillis {
        let date = calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
        return EpochMillis(date.timeIntervalSince1970 * 1000)
    }

    func testMonthIntervalUsesCalendarTimeZone() {
        let interval = DashboardRules.monthInterval(containing: millis(2026, 2, 14, 15), calendar: calendar)
        XCTAssertEqual(interval.start, millis(2026, 2, 1))
        XCTAssertEqual(interval.end, millis(2026, 3, 1))
        // Minuit pile le 1er appartient au nouveau mois.
        XCTAssertEqual(DashboardRules.monthInterval(containing: millis(2026, 3, 1), calendar: calendar).start, millis(2026, 3, 1))
    }

    func testTotalBalancesExcludeAccountsOutOfStatisticsAndGroupByCurrency() {
        let accounts = [
            Account(id: "cash", name: "Espèces", currencyCode: "XOF", initialBalance: 1_000),
            Account(id: "usd", name: "Dollars", currencyCode: "USD", initialBalance: 500),
            Account(id: "bank", name: "Banque", currencyCode: "XOF", initialBalance: 0),
            Account(id: "hidden", name: "Caisse asso", currencyCode: "XOF", initialBalance: 9_999, isExcludedFromStatistics: true)
        ]
        let totals = DashboardRules.totalBalances(accounts: accounts, balances: ["cash": 2_000, "bank": 3_000, "hidden": 9_999])
        // "usd" n'a pas de solde calculé : son solde initial est utilisé.
        XCTAssertEqual(totals, [
            CurrencyAmount(currencyCode: "XOF", amountMinor: 5_000),
            CurrencyAmount(currencyCode: "USD", amountMinor: 500)
        ])
    }

    func testPeriodTotalsCountOnlyIncomeAndExpenseOfIncludedAccountsInPeriod() {
        let accounts = [
            Account(id: "a", name: "A", currencyCode: "XOF"),
            Account(id: "e", name: "E", currencyCode: "EUR"),
            Account(id: "x", name: "X", currencyCode: "XOF", isExcludedFromStatistics: true)
        ]
        let start = millis(2026, 9, 1)
        let end = millis(2026, 10, 1)
        let transactions = [
            Transaction(id: "1", amount: 100, type: .income, accountId: "a", date: start),
            Transaction(id: "2", amount: 40, type: .expense, accountId: "a", date: end - 1),
            Transaction(id: "3", amount: 7, type: .expense, accountId: "a", date: end), // mois suivant
            Transaction(id: "4", amount: 8, type: .expense, accountId: "a", date: start - 1), // mois précédent
            Transaction(id: "5", amount: 50, type: .transfer, accountId: "a", transferAccountId: "e", date: start + 5),
            Transaction(id: "6", amount: 900, type: .expense, accountId: "x", date: start + 5), // compte exclu
            Transaction(id: "7", amount: 30, type: .expense, accountId: "e", date: start + 5),
            Transaction(id: "8", amount: 2, type: .expense, accountId: "a", date: start + 6, feeTransactionId: nil),
            Transaction(id: "9", amount: 11, type: .expense, accountId: "orphan", date: start + 6)
        ]
        let totals = DashboardRules.periodTotals(accounts: accounts, transactions: transactions, start: start, end: end)
        XCTAssertEqual(totals.income, [CurrencyAmount(currencyCode: "XOF", amountMinor: 100)])
        XCTAssertEqual(totals.expense, [
            CurrencyAmount(currencyCode: "XOF", amountMinor: 42),
            CurrencyAmount(currencyCode: "EUR", amountMinor: 30)
        ])
    }

    func testFeeTransactionIds() {
        let transactions = [
            Transaction(id: "p", amount: 1_000, type: .expense, accountId: "a", date: 1, feeTransactionId: "f"),
            Transaction(id: "f", amount: 50, type: .expense, accountId: "a", date: 1)
        ]
        XCTAssertEqual(DashboardRules.feeTransactionIds(transactions), ["f"])
    }

    func testSystemNamesAreRecognisedByNameAndType() {
        XCTAssertEqual(Category(id: "c", name: "Divers", type: .income).systemKey, .otherIncome)
        XCTAssertEqual(Category(id: "c", name: "Divers", type: .expense).systemKey, .otherExpense)
        XCTAssertEqual(Category(id: "c", name: "Frais et commissions", type: .expense).systemKey, .fees)
        XCTAssertNil(Category(id: "c", name: "Salaire", type: .expense).systemKey)
        XCTAssertNil(Category(id: "c", name: "Tontine", type: .expense).systemKey)
        XCTAssertEqual(Account(id: "a", name: "Mobile Money").defaultKey, .mobileMoney)
        XCTAssertNil(Account(id: "a", name: "Orange Money").defaultKey)
    }
}
