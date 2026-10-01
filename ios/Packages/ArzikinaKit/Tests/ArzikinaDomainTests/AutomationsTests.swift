import XCTest
@testable import ArzikinaDomain

final class AutomationsTests: XCTestCase {

    private let calendar = ArzikinaCalendar.make(timeZone: TimeZone(identifier: "Africa/Niamey")!)

    private func day(_ month: Int, _ day: Int, hour: Int = 0, minute: Int = 0) -> EpochMillis {
        CalendarDay(year: 2026, month: month, day: day).millis(hour: hour, minute: minute, calendar: calendar)
    }

    private func rule(_ frequency: RecurringFrequency = .monthly, next: EpochMillis? = nil, end: EpochMillis? = nil, hour: Int = 8) -> RecurringTransaction {
        RecurringTransaction(id: "r", type: .expense, amount: 5_000, accountId: "a", categoryId: "c", description: "Loyer", startDate: day(7, 31), endDate: end, frequency: frequency, nextExecutionDate: next ?? day(7, 31), triggerHour: hour, triggerMinute: 30)
    }

    func testOccurrenceIdIsDeterministicAndAValidUuid() {
        let a = Automations.occurrenceId(recurringTransactionId: "r", scheduledDate: 1_000)
        XCTAssertEqual(a, Automations.occurrenceId(recurringTransactionId: "r", scheduledDate: 1_000), "Même règle + même jour = même identifiant")
        XCTAssertNotEqual(a, Automations.occurrenceId(recurringTransactionId: "r", scheduledDate: 1_001))
        XCTAssertNotEqual(a, Automations.occurrenceId(recurringTransactionId: "s", scheduledDate: 1_000))
        XCTAssertNotNil(UUID(uuidString: a))
        XCTAssertEqual(a, a.lowercased())
        XCTAssertEqual(Array(a)[14], "5", "Version 5")
    }

    func testPlanGeneratesDueDatesUpToTheTriggerTimeAndAdvancesTheRule() {
        // 31 juillet, 31 août (→ 30 sept.)… à 8 h 30 ; « maintenant » = 30 sept. 8 h 00.
        let plan = Automations.plan(for: rule(), existingDates: [day(7, 31)], now: day(9, 30, hour: 8), calendar: calendar)
        XCTAssertEqual(plan.newDates, [day(8, 31)], "Celle du 30/09 n'est pas encore due ; celle de juillet existe déjà")
        XCTAssertEqual(plan.updatedRule?.nextExecutionDate, day(9, 30))
        XCTAssertEqual(plan.updatedRule?.isActive, true)

        let later = Automations.plan(for: rule(), existingDates: [], now: day(9, 30, hour: 8, minute: 30), calendar: calendar)
        XCTAssertEqual(later.newDates, [day(7, 31), day(8, 31), day(9, 30)])
    }

    func testOnceAndEndDateDeactivateTheRule() {
        let once = Automations.plan(for: rule(.once), existingDates: [], now: day(8, 1), calendar: calendar)
        XCTAssertEqual(once.newDates, [day(7, 31)])
        XCTAssertEqual(once.updatedRule?.isActive, false)
        XCTAssertEqual(once.updatedRule?.nextExecutionDate, day(7, 31))

        let ending = Automations.plan(for: rule(end: day(8, 31)), existingDates: [], now: day(12, 1), calendar: calendar)
        XCTAssertEqual(ending.newDates, [day(7, 31), day(8, 31)], "Date de fin incluse")
        XCTAssertEqual(ending.updatedRule?.isActive, false)

        let pastEnd = Automations.plan(for: rule(next: day(9, 30), end: day(9, 1)), existingDates: [], now: day(10, 1), calendar: calendar)
        XCTAssertEqual(pastEnd.newDates, [])
        XCTAssertEqual(pastEnd.updatedRule?.isActive, false, "Prochaine échéance après la fin : règle arrêtée")

        var paused = rule()
        paused.isActive = false
        XCTAssertEqual(Automations.plan(for: paused, existingDates: [], now: day(12, 1), calendar: calendar), Automations.GenerationPlan(newDates: [], updatedRule: nil))
    }

    private func stored(_ id: String, _ status: OccurrenceStatus = .pending, server: Bool = false, transaction: String? = nil, createdAt: EpochMillis = 0) -> Automations.StoredOccurrence {
        Automations.StoredOccurrence(
            occurrence: RecurringTransactionOccurrence(id: id, recurringTransactionId: "r", scheduledDate: 1, status: status, transactionId: transaction, processedAt: status == .pending ? nil : 9, createdAt: createdAt),
            isKnownByServer: server
        )
    }

    func testReconciliationKeepsTheServerCopyAndCarriesTheLocalDecision() throws {
        XCTAssertNil(Automations.reconcile([stored("a")]))

        let plan = try XCTUnwrap(Automations.reconcile([stored("local", .accepted, transaction: "t1"), stored("android", server: true)]))
        XCTAssertEqual(plan.keeperId, "android")
        XCTAssertEqual(plan.updatedKeeper?.status, .accepted, "La validation faite sur l'iPhone est reportée")
        XCTAssertEqual(plan.updatedKeeper?.transactionId, "t1")
        XCTAssertEqual(plan.removedIds, ["local"])
        XCTAssertEqual(plan.duplicateTransactionIds, [])

        let both = try XCTUnwrap(Automations.reconcile([stored("local", .accepted, transaction: "t1"), stored("android", .modified, server: true, transaction: "t2")]))
        XCTAssertEqual(both.keeperId, "android")
        XCTAssertNil(both.updatedKeeper)
        XCTAssertEqual(both.duplicateTransactionIds, ["t1"], "Validée deux fois : la transaction en trop disparaît")

        let pendingTwice = try XCTUnwrap(Automations.reconcile([stored("b", createdAt: 5), stored("a", createdAt: 5), stored("c", createdAt: 1)]))
        XCTAssertEqual(pendingTwice.keeperId, "c", "La plus ancienne")
        XCTAssertEqual(pendingTwice.removedIds.sorted(), ["a", "b"])
    }

    func testOverviewSections() {
        var paused = rule()
        paused.id = "paused"
        paused.isActive = false
        var other = rule(next: day(10, 15))
        other.id = "other"
        let rules = [rule(next: day(9, 30)), other, paused]
        let occurrences = [
            RecurringTransactionOccurrence(id: "p", recurringTransactionId: "r", scheduledDate: day(9, 30)),
            RecurringTransactionOccurrence(id: "h1", recurringTransactionId: "r", scheduledDate: day(8, 31), status: .accepted, processedAt: 5),
            RecurringTransactionOccurrence(id: "h2", recurringTransactionId: "r", scheduledDate: day(7, 31), status: .rejected, processedAt: 9),
            RecurringTransactionOccurrence(id: "orphan", recurringTransactionId: "gone", scheduledDate: day(9, 1)),
        ]
        let overview = AutomationOverview.make(rules: rules, occurrences: occurrences, accounts: [:], categories: [:])
        XCTAssertEqual(overview.pending.map(\.id), ["p"], "Échéance sans règle ignorée")
        XCTAssertEqual(overview.upcoming.map(\.rule.id), ["other"], "Pas de doublon avec « à traiter », pas de règle en pause")
        XCTAssertEqual(overview.history.map(\.id), ["h2", "h1"])
    }
}
