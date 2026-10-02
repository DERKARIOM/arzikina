import XCTest
@testable import ArzikinaDomain

final class AutomationFormTests: XCTestCase {

    private let calendar = ArzikinaCalendar.make(timeZone: TimeZone(identifier: "Africa/Niamey")!)

    private func day(_ month: Int, _ day: Int, hour: Int = 0, minute: Int = 0) -> EpochMillis {
        CalendarDay(year: 2026, month: month, day: day).millis(hour: hour, minute: minute, calendar: calendar)
    }

    private func validDraft() -> AutomationDraft {
        var draft = AutomationDraft(today: CalendarDay(year: 2026, month: 10, day: 2), accountId: "a")
        draft.details.amountInput = "5 000"
        draft.details.categoryId = "c"
        draft.details.description = "  Loyer  "
        return draft
    }

    private func existingRule() -> RecurringTransaction {
        RecurringTransaction(id: "r", type: .expense, amount: 1, accountId: "a", categoryId: "c", startDate: day(8, 1), frequency: .monthly, nextExecutionDate: day(11, 1), isActive: false, triggerHour: 9, triggerMinute: 15, createdAt: 42, updatedAt: 42)
    }

    // MARK: - Saisie

    func testNewDraftIsAMonthlyExpenseAtEightOClock() {
        let draft = AutomationDraft(today: CalendarDay(year: 2026, month: 10, day: 2), accountId: "a")
        XCTAssertEqual(draft.details.type, .expense)
        XCTAssertEqual(draft.frequency, .monthly)
        XCTAssertEqual(draft.triggerHour, 8)
        XCTAssertEqual(draft.triggerMinute, 0)
        XCTAssertFalse(draft.hasEndDate)
        XCTAssertEqual(draft.details.accountId, "a")
    }

    func testChangingTypeResetsTheCategoryAndRefusesTransfers() {
        var details = AutomationDetailsDraft(categoryId: "c")
        details.changeType(.transfer)
        XCTAssertEqual(details.type, .expense)
        XCTAssertEqual(details.categoryId, "c", "Un transfert est refusé : rien ne change")
        details.changeType(.income)
        XCTAssertEqual(details.type, .income)
        XCTAssertNil(details.categoryId)
        XCTAssertEqual(AutomationDetailsDraft(type: .transfer).type, .expense)
    }

    func testValidationOrderFollowsAndroid() {
        var details = AutomationDetailsDraft()
        XCTAssertEqual(details.validate(), .failure(.invalidAmount))
        details.amountInput = "0"
        XCTAssertEqual(details.validate(), .failure(.invalidAmount))
        details.amountInput = "250"
        XCTAssertEqual(details.validate(), .failure(.accountRequired))
        details.accountId = "a"
        XCTAssertEqual(details.validate(), .failure(.categoryRequired))
        details.categoryId = "c"
        XCTAssertEqual(try details.validate().get().amount, Money.parseToMinorUnits("250"))
    }

    func testEditingDraftRoundTripsTheRule() {
        var rule = existingRule()
        rule.endDate = day(12, 31)
        rule.paymentMethod = .mobileMoney
        let draft = AutomationDraft(editing: rule, calendar: calendar)
        XCTAssertEqual(draft.startDay, CalendarDay(year: 2026, month: 8, day: 1))
        XCTAssertTrue(draft.hasEndDate)
        XCTAssertEqual(draft.endDay, CalendarDay(year: 2026, month: 12, day: 31))
        XCTAssertEqual(draft.triggerHour, 9)
        XCTAssertEqual(draft.details.paymentMethod, .mobileMoney)
        let built = try? AutomationForm.build(draft, existing: rule, newId: "x", now: 99, calendar: calendar).get()
        XCTAssertEqual(built?.endDate, day(12, 31))
        XCTAssertEqual(built?.amount, 1)
    }

    // MARK: - Construction

    func testBuildNewRule() throws {
        var draft = validDraft()
        draft.triggerHour = 18
        draft.triggerMinute = 45
        let rule = try AutomationForm.build(draft, existing: nil, newId: "new", now: 1_000, calendar: calendar).get()
        XCTAssertEqual(rule.id, "new")
        XCTAssertEqual(rule.amount, Money.parseToMinorUnits("5000"))
        XCTAssertEqual(rule.description, "Loyer")
        XCTAssertEqual(rule.startDate, day(10, 2))
        XCTAssertEqual(rule.nextExecutionDate, day(10, 2))
        XCTAssertNil(rule.endDate)
        XCTAssertTrue(rule.isActive)
        XCTAssertEqual(rule.triggerHour, 18)
        XCTAssertEqual(rule.triggerMinute, 45)
        XCTAssertEqual(rule.createdAt, 1_000)
    }

    func testEndDateMayBeTheStartDayButNotBefore() {
        var draft = validDraft()
        draft.hasEndDate = true
        draft.endDay = draft.startDay
        XCTAssertEqual(try? AutomationForm.build(draft, existing: nil, newId: "n", now: 1, calendar: calendar).get().endDate, day(10, 2))
        draft.endDay = CalendarDay(year: 2026, month: 10, day: 1)
        XCTAssertEqual(AutomationForm.build(draft, existing: nil, newId: "n", now: 1, calendar: calendar).map(\.id), .failure(.endBeforeStart))
    }

    func testOneTimeRulesIgnoreTheEndDate() throws {
        var draft = validDraft()
        draft.frequency = .once
        draft.hasEndDate = true
        draft.endDay = CalendarDay(year: 2026, month: 1, day: 1)
        XCTAssertFalse(draft.showsEndDate)
        XCTAssertNil(try AutomationForm.build(draft, existing: nil, newId: "n", now: 1, calendar: calendar).get().endDate)
    }

    func testEditingKeepsStateActiveCreationAndProgress() throws {
        let rule = try AutomationForm.build(validDraft(), existing: existingRule(), newId: "unused", now: 500, calendar: calendar).get()
        XCTAssertEqual(rule.id, "r")
        XCTAssertFalse(rule.isActive, "Une modification ne réactive jamais une règle")
        XCTAssertEqual(rule.createdAt, 42)
        XCTAssertEqual(rule.updatedAt, 500)
        XCTAssertEqual(rule.nextExecutionDate, day(11, 1))
    }

    // MARK: - Ce que le dépôt écrit

    func testRuleToStore() {
        var edited = existingRule()
        edited.startDate = day(12, 5)
        edited.nextExecutionDate = 0
        edited.isActive = true
        edited.createdAt = 7

        let created = AutomationForm.ruleToStore(edited, stored: nil, hasGeneratedOccurrences: false)
        XCTAssertTrue(created.isActive)
        XCTAssertEqual(created.nextExecutionDate, day(12, 5))

        let stored = existingRule()
        let untouched = AutomationForm.ruleToStore(edited, stored: stored, hasGeneratedOccurrences: true)
        XCTAssertEqual(untouched.nextExecutionDate, day(11, 1), "Jamais de retour en arrière une fois des échéances générées")
        XCTAssertFalse(untouched.isActive, "État actif de la version enregistrée")
        XCTAssertEqual(untouched.createdAt, 42)

        let moved = AutomationForm.ruleToStore(edited, stored: stored, hasGeneratedOccurrences: false)
        XCTAssertEqual(moved.nextExecutionDate, day(12, 5), "Aucune échéance encore : la première suit la date de début")
    }

    // MARK: - Échéance modifiée

    func testOccurrenceEditStartsFromTheRuleAtItsTriggerTime() throws {
        var rule = existingRule()
        rule.amount = 7_500
        rule.description = "Loyer"
        let occurrence = RecurringTransactionOccurrence(id: "o", recurringTransactionId: "r", scheduledDate: day(10, 1))
        var draft = OccurrenceEditDraft(occurrence: occurrence, rule: rule, calendar: calendar)
        XCTAssertEqual(draft.date, day(10, 1, hour: 9, minute: 15))
        XCTAssertEqual(draft.details.amountInput, Money.formatForInput(7_500))

        draft.details.amountInput = "8000"
        draft.details.changeType(.income)
        XCTAssertEqual(draft.build(id: "t", now: 3).map(\.id), .failure(.categoryRequired))
        draft.details.categoryId = "salaire"
        let transaction = try draft.build(id: "t", now: 3).get()
        XCTAssertEqual(transaction.amount, Money.parseToMinorUnits("8000"))
        XCTAssertEqual(transaction.type, .income)
        XCTAssertEqual(transaction.categoryId, "salaire")
        XCTAssertEqual(transaction.date, day(10, 1, hour: 9, minute: 15))
        XCTAssertEqual(transaction.createdAt, 3)
    }
}
