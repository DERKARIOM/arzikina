import ArzikinaDomain
import GRDB

/// [DashboardRepository] adossé à la base locale.
///
/// Tout l'instantané est lu dans UNE transaction SQL (cohérent : jamais un solde d'avant et des
/// transactions d'après une synchronisation) et réémis à chaque modification des tables lues.
/// Les agrégats sont calculés en SQL (sommes, limites) : rien ne charge toutes les transactions
/// en mémoire, quel que soit leur nombre.
public struct LocalDashboardRepository: DashboardRepository {

    private let database: AppDatabase

    public init(database: AppDatabase) {
        self.database = database
    }

    public func observeDashboard(monthStart: EpochMillis, monthEnd: EpochMillis, recentLimit: Int) -> AsyncStream<DashboardSnapshot> {
        database.observe { db in
            try Self.snapshot(db, monthStart: monthStart, monthEnd: monthEnd, recentLimit: recentLimit)
        }
    }

    static func snapshot(_ db: Database, monthStart: EpochMillis, monthEnd: EpochMillis, recentLimit: Int) throws -> DashboardSnapshot {
        let accounts = try LocalAccountRepository.activeAccounts(db)
        let balances = try LocalAccountRepository.balances(db)
        return DashboardSnapshot(
            accounts: accounts,
            totalBalances: DashboardRules.totalBalances(accounts: accounts, balances: balances),
            month: try periodTotals(db, accounts: accounts, start: monthStart, end: monthEnd),
            recentTransactions: try recentTransactions(db, accounts: accounts, limit: recentLimit)
        )
    }

    /// Même résultat que `DashboardRules.periodTotals` (vérifié par les tests), en une requête.
    static func periodTotals(_ db: Database, accounts: [Account], start: EpochMillis, end: EpochMillis) throws -> DashboardRules.PeriodTotals {
        let rows = try Row.fetchAll(db, sql: """
            SELECT a.currencyCode AS currency, t.type AS type, SUM(t.amount) AS total
            FROM transactions t
            JOIN accounts a ON a.id = t.accountId
            WHERE t.deletedAt IS NULL
              AND a.deletedAt IS NULL
              AND a.isExcludedFromStatistics = 0
              AND t.type IN ('INCOME', 'EXPENSE')
              AND t.date >= ? AND t.date < ?
            GROUP BY a.currencyCode, t.type
            """, arguments: [start, end])
        var income: [String: MinorUnits] = [:]
        var expense: [String: MinorUnits] = [:]
        for row in rows {
            let currency: String = row["currency"]
            let total: MinorUnits = row["total"]
            if row["type"] as String == TransactionType.income.rawValue {
                income[currency] = total
            } else {
                expense[currency] = total
            }
        }
        let order = DashboardRules.currencyOrder(of: accounts)
        return DashboardRules.PeriodTotals(
            income: DashboardRules.sorted(income, by: order),
            expense: DashboardRules.sorted(expense, by: order)
        )
    }

    /// Dernières transactions, sans les transactions de frais (affichées sur leur parente).
    static func recentTransactions(_ db: Database, accounts: [Account], limit: Int) throws -> [TransactionListItem] {
        let records = try TransactionRecord.fetchAll(db, sql: """
            SELECT * FROM transactions
            WHERE deletedAt IS NULL
              AND id NOT IN (SELECT feeTransactionId FROM transactions
                             WHERE feeTransactionId IS NOT NULL AND deletedAt IS NULL)
            ORDER BY date DESC, createdAt DESC, id DESC
            LIMIT ?
            """, arguments: [max(limit, 0)])
        let transactions = records.map(\.domain)
        guard !transactions.isEmpty else { return [] }

        let feeIds = transactions.compactMap(\.feeTransactionId)
        var feeAmounts: [EntityID: MinorUnits] = [:]
        if !feeIds.isEmpty {
            let rows = try Row.fetchAll(
                db,
                sql: "SELECT id, amount FROM transactions WHERE deletedAt IS NULL AND id IN (\(placeholders(feeIds.count)))",
                arguments: StatementArguments(feeIds)
            )
            for row in rows { feeAmounts[row["id"]] = row["amount"] }
        }

        let categoryIds = Array(Set(transactions.compactMap(\.categoryId)))
        var categories: [EntityID: ArzikinaDomain.Category] = [:]
        if !categoryIds.isEmpty {
            let records = try CategoryRecord
                .filter(categoryIds.contains(Column("id")) && Column("deletedAt") == nil)
                .fetchAll(db)
            for record in records { categories[record.id] = record.domain }
        }

        let accountsById = Dictionary(uniqueKeysWithValues: accounts.map { ($0.id, $0) })
        return transactions.map { transaction in
            TransactionListItem(
                transaction: transaction,
                account: accountsById[transaction.accountId],
                transferAccount: transaction.transferAccountId.flatMap { accountsById[$0] },
                category: transaction.categoryId.flatMap { categories[$0] },
                feeAmount: transaction.feeTransactionId.flatMap { feeAmounts[$0] }
            )
        }
    }

    private static func placeholders(_ count: Int) -> String {
        Array(repeating: "?", count: count).joined(separator: ", ")
    }
}
