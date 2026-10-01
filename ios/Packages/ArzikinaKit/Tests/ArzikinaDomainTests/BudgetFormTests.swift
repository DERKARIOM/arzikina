import XCTest
@testable import ArzikinaDomain

/// Formulaire et présentation des budgets — comportements d'Android `BudgetFormViewModel`,
/// `QuickDateRange` et `BudgetModernAdapter`.
final class BudgetFormTests: XCTestCase {

    private let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Africa/Niamey")!
        calendar.firstWeekday = 2
        return calendar
    }()

    /// Mercredi 30 septembre 2026.
    private let today = CalendarDay(year: 2026, month: 9, day: 30)

    private func day(_ month: Int, _ day: Int, year: Int = 2026) -> CalendarDay {
        CalendarDay(year: year, month: month, day: day)
    }

    func testQuickRangesMatchAndroid() {
        func bounds(_ range: BudgetQuickRange) -> [CalendarDay] {
            let value = range.bounds(today: today, calendar: calendar)
            return [value.start, value.end]
        }
        XCTAssertEqual(bounds(.thisWeek), [day(9, 28), day(10, 4)], "Lundi → dimanche")
        XCTAssertEqual(bounds(.thisMonth), [day(9, 1), day(9, 30)])
        XCTAssertEqual(bounds(.nextMonth), [day(10, 1), day(10, 31)])
        XCTAssertEqual(bounds(.thisYear), [day(1, 1), day(12, 31)])

        let december = CalendarDay(year: 2026, month: 12, day: 15)
        let next = BudgetQuickRange.nextMonth.bounds(today: december, calendar: calendar)
        XCTAssertEqual([next.start, next.end], [day(1, 1, year: 2027), day(1, 31, year: 2027)], "Changement d'année")
    }

    func testValidationOrderAndDates() throws {
        var draft = BudgetDraft(today: today, calendar: calendar)
        XCTAssertEqual(draft.quickRange, .thisMonth, "« Ce mois » présélectionné")

        func error() -> BudgetFormError? {
            if case .failure(let error) = BudgetForm.build(draft, existing: nil, newId: "b", now: 5, calendar: calendar) { return error }
            return nil
        }
        XCTAssertEqual(error(), .categoryRequired)
        draft.categoryId = "food"
        XCTAssertEqual(error(), .invalidLimit)
        draft.limitInput = "0"
        XCTAssertEqual(error(), .invalidLimit)
        draft.limitInput = "25000"
        draft.changeStartDay(day(10, 5))
        XCTAssertNil(draft.quickRange, "Date choisie à la main : « Personnalisée »")
        draft.changeEndDay(day(10, 4))
        XCTAssertEqual(error(), .endBeforeStart)
        draft.changeEndDay(day(10, 5))
        XCTAssertNil(error(), "Un seul jour : autorisé")

        let budget = try BudgetForm.build(draft, existing: nil, newId: "b", now: 5, calendar: calendar).get()
        XCTAssertEqual(budget.limitAmount, 2_500_000)
        XCTAssertEqual(budget.startDate, day(10, 5).startOfDayMillis(calendar: calendar))
        XCTAssertEqual(budget.endDate, day(10, 5).startOfDayMillis(calendar: calendar), "Début du jour, comme Android")
        XCTAssertEqual(budget.createdAt, 5)
    }

    func testLegacyRecurringBudgetKeepsItsPeriodAndNoDates() throws {
        let legacy = Budget(id: "old", categoryId: "food", period: .weekly, limitAmount: 10_000, createdAt: 3)
        var draft = BudgetDraft(editing: legacy, calendar: calendar)
        XCTAssertTrue(draft.isLegacyRecurring)
        draft.limitInput = "200"
        let saved = try BudgetForm.build(draft, existing: legacy, newId: "x", now: 9, calendar: calendar).get()
        XCTAssertEqual(saved.id, "old")
        XCTAssertEqual(saved.createdAt, 3)
        XCTAssertEqual(saved.period, .weekly)
        XCTAssertNil(saved.startDate)
        XCTAssertNil(saved.endDate)
    }

    func testOnlyExpenseCategoriesWithoutActiveBudgetAreOffered() {
        let categories = [
            Category(id: "food", name: "Nourriture", type: .expense),
            Category(id: "fuel", name: "Transport", type: .expense),
            Category(id: "rent", name: "Maison", type: .expense),
            Category(id: "health", name: "Santé", type: .expense),
            Category(id: "pay", name: "Salaire", type: .income),
        ]
        let millis = { (d: CalendarDay) in d.startOfDayMillis(calendar: self.calendar) }
        let budgets = [
            Budget(id: "ongoing", categoryId: "food", limitAmount: 1, startDate: millis(day(9, 1)), endDate: millis(day(9, 30))),
            Budget(id: "done", categoryId: "fuel", limitAmount: 1, startDate: millis(day(8, 1)), endDate: millis(day(8, 31))),
            Budget(id: "recurring", categoryId: "rent", limitAmount: 1),
            Budget(id: "soon", categoryId: "health", limitAmount: 1, startDate: millis(day(10, 1)), endDate: millis(day(10, 31))),
        ]
        let offered = BudgetForm.availableCategories(categories, budgets: budgets, editingBudgetId: nil, today: today, calendar: calendar)
        XCTAssertEqual(offered.map(\.id), ["fuel"], "Un budget terminé libère sa catégorie")

        let editing = BudgetForm.availableCategories(categories, budgets: budgets, editingBudgetId: "ongoing", today: today, calendar: calendar)
        XCTAssertEqual(editing.map(\.id), ["food", "fuel"])
    }

    func testSummaryStatusDaysAndFeatured() {
        let millis = { (d: CalendarDay) in d.startOfDayMillis(calendar: self.calendar) }
        let ongoing = Budget(id: "a", categoryId: "c", limitAmount: 30_000, startDate: millis(day(9, 1)), endDate: millis(day(10, 10)))
        let upcoming = Budget(id: "b", categoryId: "c", limitAmount: 10_000, startDate: millis(day(10, 3)), endDate: millis(day(10, 9)))
        let completed = Budget(id: "c", categoryId: "c", limitAmount: 10_000, startDate: millis(day(8, 1)), endDate: millis(day(8, 31)))
        let recurring = Budget(id: "d", categoryId: "c", period: .weekly, limitAmount: 10_000)

        let a = BudgetSummary(budget: ongoing, category: nil, spent: 15_000, today: today, calendar: calendar)
        XCTAssertEqual(a.displayStatus, .ongoing)
        XCTAssertEqual(a.days, 10, "Du 30/09 au 10/10")
        XCTAssertEqual(a.percent, 50)
        XCTAssertEqual(a.remaining, 15_000)

        let b = BudgetSummary(budget: upcoming, category: nil, spent: 0, today: today, calendar: calendar)
        XCTAssertEqual(b.displayStatus, .upcoming)
        XCTAssertEqual(b.days, 3)
        XCTAssertFalse(b.showsPace)

        let c = BudgetSummary(budget: completed, category: nil, spent: 12_000, today: today, calendar: calendar)
        XCTAssertEqual(c.displayStatus, .overspent, "Dépassé l'emporte sur Terminé")
        XCTAssertEqual(c.remaining, -2_000)

        let d = BudgetSummary(budget: recurring, category: nil, spent: 0, today: today, calendar: calendar)
        XCTAssertNil(d.periodStatus)
        XCTAssertEqual(d.displayStatus, .ongoing)
        XCTAssertEqual(d.days, 4, "Jusqu'au dimanche 4 octobre")

        XCTAssertEqual(BudgetSummary.featured(in: [a, b, c, d])?.id, "c")
        XCTAssertNil(BudgetSummary.featured(in: []))
        XCTAssertTrue(BudgetStatusFilter.all.matches(nil))
        XCTAssertFalse(BudgetStatusFilter.ongoing.matches(nil), "Récurrent : seulement dans « Tous »")
        XCTAssertTrue(BudgetStatusFilter.completed.matches(.completed))
    }
}
