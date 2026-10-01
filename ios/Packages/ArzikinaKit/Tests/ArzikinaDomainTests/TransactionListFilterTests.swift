import XCTest
@testable import ArzikinaDomain

/// Recherche et filtres de la liste des transactions — comportements d'Android
/// `TransactionsViewModel`.
final class TransactionListFilterTests: XCTestCase {

    private let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Africa/Niamey")!
        calendar.firstWeekday = 2
        return calendar
    }()

    /// Mercredi 30 septembre 2026.
    private let today = CalendarDay(year: 2026, month: 9, day: 30)

    private let cash = Account(id: "cash", name: "Espèces")
    private let bank = Account(id: "bank", name: "Banque Atlantique")
    private let salary = Category(id: "salary", name: "Salaire", type: .income)
    private let food = Category(id: "food", name: "Nourriture", type: .expense)

    private func at(_ day: CalendarDay, hour: Int = 12) -> EpochMillis {
        day.millis(hour: hour, minute: 0, calendar: calendar)
    }

    private var entries: [TransactionLedgerEntry] {
        [
            TransactionLedgerEntry(
                transaction: Transaction(id: "transfer", amount: 5_000, type: .transfer, accountId: "cash", transferAccountId: "bank", date: at(today), description: "Épargne"),
                sourceAccount: cash, destinationAccount: bank, sourceBalanceAfter: 10_000, destinationBalanceAfter: 25_000
            ),
            TransactionLedgerEntry(
                transaction: Transaction(id: "lunch", amount: 2_000, type: .expense, accountId: "cash", categoryId: "food", date: at(CalendarDay(year: 2026, month: 9, day: 28), hour: 0), description: "Déjeuner"),
                sourceAccount: cash, category: food, feeAmount: 100, sourceBalanceAfter: 15_000
            ),
            TransactionLedgerEntry(
                transaction: Transaction(id: "pay", amount: 100_000, type: .income, accountId: "bank", categoryId: "salary", date: at(CalendarDay(year: 2026, month: 9, day: 27), hour: 23)),
                sourceAccount: bank, category: salary, sourceBalanceAfter: 20_000
            ),
            TransactionLedgerEntry(
                transaction: Transaction(id: "old", amount: 700, type: .expense, accountId: "cash", categoryId: "food", date: at(CalendarDay(year: 2026, month: 8, day: 31))),
                sourceAccount: cash, category: food
            ),
        ]
    }

    private func ids(_ filters: TransactionFilters, names: TransactionListFilter.SearchableNames = .storedNames) -> [EntityID] {
        TransactionListFilter.apply(entries, filters: filters, today: today, calendar: calendar, names: names).map(\.id)
    }

    func testNoFilterKeepsOrderAndShowsTransfersFromTheSourceAccount() {
        let items = TransactionListFilter.apply(entries, filters: TransactionFilters(), today: today, calendar: calendar, names: .storedNames)
        XCTAssertEqual(items.map(\.id), ["transfer", "lunch", "pay", "old"])
        XCTAssertEqual(items[0].account?.id, "cash")
        XCTAssertEqual(items[0].transferAccount?.id, "bank")
        XCTAssertEqual(items[0].runningBalance, 10_000)
        XCTAssertEqual(items[1].feeAmount, 100)
        XCTAssertNil(items[1].transferAccount)
    }

    func testKindFilterNeverShowsTransfers() {
        XCTAssertEqual(ids(TransactionFilters(kind: .income)), ["pay"])
        XCTAssertEqual(ids(TransactionFilters(kind: .expense)), ["lunch", "old"])
    }

    func testAccountFilterFindsTransfersOnBothSidesFromThatAccountsPointOfView() {
        XCTAssertEqual(ids(TransactionFilters(accountId: "cash")), ["transfer", "lunch", "old"])

        let bankItems = TransactionListFilter.apply(entries, filters: TransactionFilters(accountId: "bank"), today: today, calendar: calendar, names: .storedNames)
        XCTAssertEqual(bankItems.map(\.id), ["transfer", "pay"])
        XCTAssertEqual(bankItems[0].account?.id, "bank", "Transfert reçu : vu depuis le compte destination")
        XCTAssertEqual(bankItems[0].transferAccount?.id, "cash")
        XCTAssertEqual(bankItems[0].runningBalance, 25_000)
    }

    func testCategoryFilter() {
        XCTAssertEqual(ids(TransactionFilters(categoryId: "food")), ["lunch", "old"])
    }

    func testPeriodsAreTheCurrentIsoWeekAndCalendarMonth() {
        // Semaine du lundi 28 septembre (00:00) au dimanche 4 octobre : le dimanche 27 à 23 h
        // n'en fait pas partie.
        XCTAssertEqual(ids(TransactionFilters(period: .thisWeek)), ["transfer", "lunch"])
        XCTAssertEqual(ids(TransactionFilters(period: .thisMonth)), ["transfer", "lunch", "pay"])
    }

    func testFiltersCombine() {
        XCTAssertEqual(ids(TransactionFilters(kind: .expense, accountId: "cash", categoryId: "food", period: .thisMonth)), ["lunch"])
    }

    func testQueryMatchesDescriptionCategoryOrAccountIgnoringCaseAndSurroundingSpaces() {
        XCTAssertEqual(ids(TransactionFilters(query: "  déjeuner ")), ["lunch"])
        XCTAssertEqual(ids(TransactionFilters(query: "NOURR")), ["lunch", "old"])
        XCTAssertEqual(ids(TransactionFilters(query: "atlantique")), ["pay"], "Compte de la ligne : la source par défaut")
        XCTAssertEqual(ids(TransactionFilters(query: "atlantique", accountId: "bank")), ["transfer", "pay"], "Transfert reçu : compte destination")
        XCTAssertEqual(ids(TransactionFilters(query: "introuvable")), [])
    }

    func testQueryAlsoMatchesTranslatedNames() {
        let english = TransactionListFilter.SearchableNames(
            account: { [$0.name] },
            category: { $0.id == "salary" ? ["Salary", $0.name] : [$0.name] }
        )
        XCTAssertEqual(ids(TransactionFilters(query: "salary"), names: english), ["pay"])
        XCTAssertEqual(ids(TransactionFilters(query: "salaire"), names: english), ["pay"])
    }

    func testActiveFiltersIgnoreQueryAndResetKeepsIt() {
        var filters = TransactionFilters(query: "riz")
        XCTAssertFalse(filters.hasActiveFilters)
        filters.period = .thisWeek
        filters.accountId = "cash"
        XCTAssertTrue(filters.hasActiveFilters)
        filters.resetFilters()
        XCTAssertFalse(filters.hasActiveFilters)
        XCTAssertEqual(filters.query, "riz")
    }
}
