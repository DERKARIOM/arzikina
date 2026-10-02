import XCTest
@testable import ArzikinaDomain

final class AutomationRemindersTests: XCTestCase {

    private let calendar = ArzikinaCalendar.make(timeZone: TimeZone(identifier: "Africa/Niamey")!)

    private func day(_ month: Int, _ day: Int, hour: Int = 0, minute: Int = 0) -> EpochMillis {
        CalendarDay(year: 2026, month: month, day: day).millis(hour: hour, minute: minute, calendar: calendar)
    }

    private func rule(
        _ frequency: RecurringFrequency,
        id: EntityID = "r",
        next: EpochMillis,
        end: EpochMillis? = nil,
        active: Bool = true,
        hour: Int = 8,
        minute: Int = 30
    ) -> RecurringTransaction {
        RecurringTransaction(id: id, type: .expense, amount: 1, accountId: "a", startDate: next, endDate: end, frequency: frequency, nextExecutionDate: next, isActive: active, triggerHour: hour, triggerMinute: minute)
    }

    func testRemindersFireAtTheRuleTimeAndSkipThePast() {
        let reminders = AutomationReminders.upcoming(rules: [rule(.weekly, next: day(10, 1))], after: day(10, 8, hour: 9), limit: 3, calendar: calendar)
        // 1er octobre et 8 octobre à 08:30 sont passés à 9 h le 8.
        XCTAssertEqual(reminders.map(\.fireAt), [day(10, 15, hour: 8, minute: 30), day(10, 22, hour: 8, minute: 30), day(10, 29, hour: 8, minute: 30)])
        XCTAssertEqual(reminders.first?.scheduledDate, day(10, 15))
    }

    func testTodayIsIncludedWhileItsTimeIsAhead() {
        let reminders = AutomationReminders.upcoming(rules: [rule(.once, next: day(10, 2))], after: day(10, 2, hour: 8, minute: 29), calendar: calendar)
        XCTAssertEqual(reminders.map(\.fireAt), [day(10, 2, hour: 8, minute: 30)], "Une seule fois : un seul rappel")
        XCTAssertTrue(AutomationReminders.upcoming(rules: [rule(.once, next: day(10, 2))], after: day(10, 2, hour: 8, minute: 30), calendar: calendar).isEmpty)
    }

    func testEndDateIsInclusiveAndPausedRulesAreIgnored() {
        let ending = rule(.daily, next: day(10, 2), end: day(10, 4))
        let paused = rule(.daily, id: "p", next: day(10, 2), active: false)
        let reminders = AutomationReminders.upcoming(rules: [ending, paused], after: day(10, 1), calendar: calendar)
        XCTAssertEqual(reminders.map(\.scheduledDate), [day(10, 2), day(10, 3), day(10, 4)])
    }

    func testTheClosestRemindersWinAcrossRulesAndIdsAreStable() {
        let daily = rule(.daily, id: "d", next: day(10, 2), hour: 20, minute: 0)
        let monthly = rule(.monthly, id: "m", next: day(10, 5), hour: 7, minute: 0)
        let reminders = AutomationReminders.upcoming(rules: [daily, monthly], after: day(10, 1), limit: 5, calendar: calendar)
        XCTAssertEqual(reminders.map(\.ruleId), ["d", "d", "d", "m", "d"])
        XCTAssertEqual(reminders.count, 5)
        XCTAssertEqual(Set(reminders.map(\.id)).count, 5)
        XCTAssertEqual(reminders[3].id, "automation.m.\(day(10, 5))")
        XCTAssertEqual(reminders, AutomationReminders.upcoming(rules: [monthly, daily], after: day(10, 1), limit: 5, calendar: calendar), "Ordre des règles sans effet")
    }

    func testDefaultLimitStaysUnderTheIOSLimit() {
        let reminders = AutomationReminders.upcoming(rules: [rule(.daily, next: day(1, 1))], after: day(1, 1), calendar: calendar)
        XCTAssertEqual(reminders.count, AutomationReminders.defaultLimit)
        XCTAssertLessThan(AutomationReminders.defaultLimit, 64)
    }

    func testLongForgottenRuleStillGetsItsNextReminder() {
        // Règle quotidienne jamais rouverte depuis 2 ans : la boucle rattrape le présent.
        let old = RecurringTransaction(id: "o", type: .expense, amount: 1, accountId: "a", startDate: 0, frequency: .daily, nextExecutionDate: CalendarDay(year: 2024, month: 10, day: 2).startOfDayMillis(calendar: calendar), triggerHour: 8, triggerMinute: 0)
        let reminders = AutomationReminders.upcoming(rules: [old], after: day(10, 2, hour: 9), limit: 1, calendar: calendar)
        XCTAssertEqual(reminders.map(\.fireAt), [day(10, 3, hour: 8)])
    }
}
