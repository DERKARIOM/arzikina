import ArzikinaDomain
import GRDB

/// [BackupRepository] adossé à la base locale : toutes les tables de l'utilisateur, lignes non
/// supprimées, lues dans UNE transaction de lecture (un instantané cohérent, même si la
/// synchronisation écrit pendant l'export).
public struct LocalBackupRepository: BackupRepository {

    private let database: AppDatabase

    public init(database: AppDatabase) {
        self.database = database
    }

    public func snapshot() async throws -> BackupSnapshot {
        try await database.writer.read { db in
            let preferences = try Row.fetchOne(db, sql: """
                SELECT themeMode, currencyCode FROM user_preferences
                WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1
                """)
            return BackupSnapshot(
                accounts: try Self.active(AccountRecord.self, db).map(\.domain),
                categories: try Self.active(CategoryRecord.self, db).map(\.domain),
                transactions: try Self.active(TransactionRecord.self, db).map(\.domain),
                budgets: try Self.active(BudgetRecord.self, db).map(\.domain),
                persons: try Self.active(PersonRecord.self, db).map(\.domain),
                loans: try Self.active(LoanRecord.self, db).map(\.domain),
                loanPayments: try Self.active(LoanPaymentRecord.self, db).map(\.domain),
                recurringRules: try Self.active(RecurringTransactionRecord.self, db).map(\.domain),
                occurrences: try Self.active(RecurringOccurrenceRecord.self, db).map(\.domain),
                plans: try Self.active(FinancialPlanRecord.self, db).map(\.domain),
                planItems: try Self.active(FinancialPlanItemRecord.self, db).map(\.domain),
                templates: try Self.active(TransactionTemplateRecord.self, db).map(\.domain),
                themeMode: preferences?["themeMode"],
                currencyCode: preferences?["currencyCode"]
            )
        }
    }

    private static func active<Record: FetchableRecord & TableRecord>(_: Record.Type, _ db: Database) throws -> [Record] {
        try Record.filter(Column("deletedAt") == nil).fetchAll(db)
    }
}
