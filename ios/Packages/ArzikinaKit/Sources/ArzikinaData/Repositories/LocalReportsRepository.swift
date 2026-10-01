import ArzikinaDomain
import Foundation
import GRDB

/// [ReportsRepository] adossé à la base locale : toutes les sommes sont faites en SQL (index sur
/// `date`), en UNE transaction par émission. Aucune transaction n'est chargée en mémoire.
public struct LocalReportsRepository: ReportsRepository {

    private let database: AppDatabase

    public init(database: AppDatabase) {
        self.database = database
    }

    public func observeReport(
        period: (start: CalendarDay, end: CalendarDay)?,
        breakdownType: BreakdownType,
        today: CalendarDay,
        calendar: Calendar
    ) -> AsyncStream<ReportSnapshot> {
        let range = period.map { Self.millisRange(from: $0.start, through: $0.end, calendar: calendar) }
        let months = Reports.evolutionMonths(today: today, calendar: calendar).map { month in
            (month, Self.millisRange(from: month, through: DatePeriods.currentPeriodEnd(.monthly, today: month, calendar: calendar), calendar: calendar))
        }
        return database.observe { db in
            try Self.report(db, range: range, breakdownType: breakdownType, months: months)
        }
    }

    /// [début du premier jour, début du lendemain du dernier jour[.
    static func millisRange(from start: CalendarDay, through end: CalendarDay, calendar: Calendar) -> Range<EpochMillis> {
        start.startOfDayMillis(calendar: calendar)..<end.adding(.day, 1, calendar: calendar).startOfDayMillis(calendar: calendar)
    }

    static func report(
        _ db: Database,
        range: Range<EpochMillis>?,
        breakdownType: BreakdownType,
        months: [(CalendarDay, Range<EpochMillis>)]
    ) throws -> ReportSnapshot {
        let accounts = try LocalAccountRepository.activeAccounts(db)
        let preference = try String.fetchOne(db, sql: """
            SELECT currencyCode FROM user_preferences WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1
            """)
        let currency = Reports.currencyCode(preference: preference, accounts: accounts)

        var income: MinorUnits = 0
        var expense: MinorUnits = 0
        var amounts: [EntityID: MinorUnits] = [:]
        if let range {
            (income, expense) = try totals(db, currency: currency, range: range)
            let rows = try Row.fetchAll(db, sql: """
                SELECT t.categoryId AS categoryId, SUM(t.amount) AS amount
                FROM transactions t \(personalAccountsJoin)
                WHERE t.deletedAt IS NULL AND t.type = ? AND t.categoryId IS NOT NULL
                    AND t.date >= ? AND t.date < ?
                GROUP BY t.categoryId
                """, arguments: [currency, breakdownType.transactionType.rawValue, range.lowerBound, range.upperBound])
            for row in rows { amounts[row["categoryId"]] = row["amount"] }
        }
        let categoryRecords = try CategoryRecord.fetchAll(db, keys: Array(amounts.keys)).filter { $0.deletedAt == nil }
        let categories = Dictionary(uniqueKeysWithValues: categoryRecords.map { ($0.id, $0.domain) })

        let evolution = try months.map { month, monthRange in
            let totals = try totals(db, currency: currency, range: monthRange)
            return MonthTotals(month: month, income: totals.income, expense: totals.expense)
        }
        return ReportSnapshot(
            currencyCode: currency,
            income: income,
            expense: expense,
            breakdown: Reports.breakdown(amountsByCategory: amounts, categories: categories),
            evolution: evolution
        )
    }

    /// Périmètre personnel (`PersonalStatistics`) dans la devise des rapports ; le paramètre lié
    /// est la devise.
    private static let personalAccountsJoin = """
        JOIN accounts a ON a.id = t.accountId
            AND a.deletedAt IS NULL
            AND a.isExcludedFromStatistics = 0
            AND a.currencyCode = ?
        """

    private static func totals(_ db: Database, currency: String, range: Range<EpochMillis>) throws -> (income: MinorUnits, expense: MinorUnits) {
        let rows = try Row.fetchAll(db, sql: """
            SELECT t.type AS type, SUM(t.amount) AS amount
            FROM transactions t \(personalAccountsJoin)
            WHERE t.deletedAt IS NULL AND t.type IN (?, ?) AND t.date >= ? AND t.date < ?
            GROUP BY t.type
            """, arguments: [currency, TransactionType.income.rawValue, TransactionType.expense.rawValue, range.lowerBound, range.upperBound])
        var income: MinorUnits = 0
        var expense: MinorUnits = 0
        for row in rows {
            let type: String = row["type"]
            if type == TransactionType.income.rawValue { income = row["amount"] } else { expense = row["amount"] }
        }
        return (income, expense)
    }
}
