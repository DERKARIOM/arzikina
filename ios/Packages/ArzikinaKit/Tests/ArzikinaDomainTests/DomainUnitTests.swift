import Foundation
import XCTest
@testable import ArzikinaDomain

/// Tests propres à iOS (hors fixtures partagées) : outils de calendrier, langues, valeurs brutes
/// des énumérations (contrat avec l'API).
final class DomainUnitTests: XCTestCase {

    private let niamey = ArzikinaCalendar.make(timeZone: TimeZone(identifier: "Africa/Niamey")!)

    func testCalendarDayRoundTrip() {
        let day = CalendarDay(year: 2026, month: 9, day: 30)
        XCTAssertEqual(CalendarDay(epochMillis: day.startOfDayMillis(calendar: niamey), calendar: niamey), day)
        XCTAssertEqual(CalendarDay(epochMillis: day.millis(hour: 23, minute: 59, calendar: niamey), calendar: niamey), day)
        XCTAssertEqual(day.description, "2026-09-30")
        XCTAssertEqual(CalendarDay(iso: "2026-09-30"), day)
        XCTAssertNil(CalendarDay(iso: "2026-13-01"))
    }

    func testCalendarDayArithmetic() {
        let day = CalendarDay(year: 2026, month: 1, day: 31)
        XCTAssertEqual(day.adding(.month, 1, calendar: niamey), CalendarDay(year: 2026, month: 2, day: 28))
        XCTAssertEqual(day.days(to: CalendarDay(year: 2026, month: 3, day: 1), calendar: niamey), 29)
        XCTAssertEqual(CalendarDay(year: 2026, month: 9, day: 28).isoWeekday(calendar: niamey), 1) // lundi
        XCTAssertEqual(CalendarDay(year: 2026, month: 10, day: 4).isoWeekday(calendar: niamey), 7) // dimanche
        XCTAssertEqual(CalendarDay(year: 2028, month: 2, day: 1).daysInMonth(calendar: niamey), 29)
    }

    func testAppLanguageResolution() {
        XCTAssertEqual(AppLanguage.from(languageTag: "fr-NE"), .french)
        XCTAssertEqual(AppLanguage.from(languageTag: "en_NG"), .english)
        XCTAssertEqual(AppLanguage.from(languageTag: "EN"), .english)
        XCTAssertNil(AppLanguage.from(languageTag: "ha-NG"))
        XCTAssertNil(AppLanguage.from(languageTag: ""))
        XCTAssertEqual(AppLanguage.resolve(preferred: ["ha-NG", "en-GB", "fr"]), .english)
        XCTAssertEqual(AppLanguage.resolve(preferred: ["ar-SA"]), .french)
    }

    /// Les valeurs brutes sont le CONTRAT avec l'API et Android : les changer casserait la
    /// synchronisation.
    func testEnumRawValuesMatchApi() {
        XCTAssertEqual(AccountType.allCases.map(\.rawValue), ["CASH", "BANK", "MOBILE_MONEY", "SAVINGS", "CREDIT_CARD", "SAVINGS_GOAL"])
        XCTAssertEqual(TransactionType.allCases.map(\.rawValue), ["INCOME", "EXPENSE", "TRANSFER"])
        XCTAssertEqual(LoanStatus.allCases.map(\.rawValue), ["ONGOING", "REPAID", "OVERDUE", "UPCOMING", "GIFTED"])
        XCTAssertEqual(RecurringFrequency.allCases.map(\.rawValue), ["ONCE", "DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "QUARTERLY", "SEMIANNUAL", "YEARLY"])
        XCTAssertEqual(OccurrenceStatus.allCases.map(\.rawValue), ["PENDING", "ACCEPTED", "MODIFIED", "REJECTED"])
        XCTAssertEqual(PlanItemStatus.allCases.map(\.rawValue), ["TO_PLAN", "DONE", "CANCELLED"])
        XCTAssertEqual(ThemeMode.allCases.map(\.rawValue), ["SYSTEM", "LIGHT", "DARK"])
    }

    func testSignedAmount() {
        let base = Transaction(id: "t", amount: 500, type: .income, accountId: "a", date: 0)
        XCTAssertEqual(base.signedAmount, 500)
        var expense = base
        expense.type = .expense
        XCTAssertEqual(expense.signedAmount, -500)
        var transfer = base
        transfer.type = .transfer
        XCTAssertEqual(transfer.signedAmount, -500)
    }

    func testLoanStatusOfLoan() {
        let start = CalendarDay(year: 2026, month: 9, day: 1).startOfDayMillis(calendar: niamey)
        let due = CalendarDay(year: 2026, month: 9, day: 29).startOfDayMillis(calendar: niamey)
        let now = CalendarDay(year: 2026, month: 9, day: 30).millis(hour: 9, minute: 0, calendar: niamey)
        let loan = Loan(id: "l", personId: "p", accountId: "a", type: .lent, amount: 1000, remainingAmount: 1000,
                        startDate: start, dueDate: due, transactionId: "t")
        XCTAssertEqual(LoanStatusRule.status(of: loan, now: now, calendar: niamey), .overdue)
    }

    func testSavingsPercentDoesNotOverflow() {
        XCTAssertEqual(SavingsGoalProgress.progressPercent(current: Int64.max / 2, target: Int64.max), 49)
    }
}

final class AccountListRulesTests: XCTestCase {

    func testEveryAccountTypeBelongsToExactlyOneGroup() {
        XCTAssertEqual(AccountType.creditCard.group, .bankCards)
        XCTAssertEqual(AccountType.savingsGoal.group, .savingsGoals)
        for type in [AccountType.cash, .bank, .mobileMoney, .savings] {
            XCTAssertEqual(type.group, .accounts, type.rawValue)
        }
    }

    func testDayGroupingNewestDayFirstKeepingOrderWithinDay() {
        let calendar = ArzikinaCalendar.make(timeZone: TimeZone(identifier: "Africa/Niamey")!)
        func at(_ day: Int, _ hour: Int) -> EpochMillis {
            CalendarDay(year: 2026, month: 9, day: day).millis(hour: hour, minute: 0, calendar: calendar)
        }
        // 23 h 30 à Niamey = 22 h 30 UTC : le jour est celui du fuseau, pas celui d'UTC.
        let items: [(String, EpochMillis)] = [("c", at(30, 23)), ("b", at(30, 8)), ("a", at(29, 12)), ("z", at(1, 0))]
        let sections = DayGrouping.group(items, calendar: calendar) { $0.1 }
        XCTAssertEqual(sections.map(\.day), [
            CalendarDay(year: 2026, month: 9, day: 30),
            CalendarDay(year: 2026, month: 9, day: 29),
            CalendarDay(year: 2026, month: 9, day: 1)
        ])
        XCTAssertEqual(sections[0].items.map(\.0), ["c", "b"])
    }
}
