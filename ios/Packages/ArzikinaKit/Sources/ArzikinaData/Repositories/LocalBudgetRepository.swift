import ArzikinaDomain
import Foundation
import GRDB

/// [BudgetRepository] adossé à la base locale.
///
/// Le dépensé de chaque budget est une somme SQL sur SA période (index sur `categoryId` et
/// `date`) : aucune transaction n'est chargée en mémoire, même avec des années d'historique.
public struct LocalBudgetRepository: BudgetRepository {

    private let database: AppDatabase
    private let store: SyncedStore<BudgetRecord>

    public init(database: AppDatabase, now: @escaping Clock = Clocks.system) {
        self.database = database
        self.store = SyncedStore(database: database, entityType: .budgets, now: now)
    }

    public func observeBudgets() -> AsyncStream<[Budget]> {
        database.observe { db in try Self.activeBudgets(db) }
    }

    public func save(_ budget: Budget) async throws {
        try await store.save(id: budget.id, createdAt: budget.createdAt) { BudgetRecord(budget, meta: $0) }
    }

    public func delete(id: EntityID) async throws {
        try await store.softDelete(id: id)
    }

    public func observeSummaries(today: CalendarDay, calendar: Calendar) -> AsyncStream<[BudgetSummary]> {
        database.observe { db in try Self.summaries(db, today: today, calendar: calendar) }
    }

    static func activeBudgets(_ db: Database) throws -> [Budget] {
        try BudgetRecord
            .filter(Column("deletedAt") == nil)
            .order(Column("createdAt"), Column("id"))
            .fetchAll(db)
            .map(\.domain)
    }

    static func summaries(_ db: Database, today: CalendarDay, calendar: Calendar) throws -> [BudgetSummary] {
        let categoryRecords = try CategoryRecord.filter(Column("deletedAt") == nil).fetchAll(db)
        let categories = Dictionary(uniqueKeysWithValues: categoryRecords.map { ($0.id, $0.domain) })
        return try activeBudgets(db).map { budget in
            BudgetSummary(
                budget: budget,
                category: categories[budget.categoryId],
                spent: try spent(db, budget: budget, today: today, calendar: calendar),
                today: today,
                calendar: calendar
            )
        }
    }

    /// Même règle que `BudgetProgress.compute` (domaine), appliquée aux transactions du périmètre
    /// personnel (`PersonalStatistics`) : jours de la période inclus, soit [début, lendemain de la
    /// fin[ en millisecondes dans le fuseau de [calendar].
    static func spent(_ db: Database, budget: Budget, today: CalendarDay, calendar: Calendar) throws -> MinorUnits {
        let bounds = BudgetPace.bounds(of: budget, today: today, calendar: calendar)
        let from = bounds.start.startOfDayMillis(calendar: calendar)
        let to = bounds.end.adding(.day, 1, calendar: calendar).startOfDayMillis(calendar: calendar)
        return try MinorUnits.fetchOne(db, sql: """
            SELECT COALESCE(SUM(t.amount), 0)
            FROM transactions t
            JOIN accounts a ON a.id = t.accountId
                AND a.deletedAt IS NULL
                AND a.isExcludedFromStatistics = 0
                AND a.currencyCode = ?
            WHERE t.deletedAt IS NULL
                AND t.type = ?
                AND t.categoryId = ?
                AND t.date >= ? AND t.date < ?
            """, arguments: [budget.currencyCode, TransactionType.expense.rawValue, budget.categoryId, from, to]) ?? 0
    }
}
