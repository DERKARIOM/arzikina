import ArzikinaDomain
import GRDB

/// [AccountOverviewRepository] adossé à la base locale : chaque émission est lue en UNE
/// transaction SQL (cohérente) et réémise à chaque modification des tables lues.
public struct LocalAccountOverviewRepository: AccountOverviewRepository {

    private let database: AppDatabase

    public init(database: AppDatabase) {
        self.database = database
    }

    public func observeAccountSummaries() -> AsyncStream<[AccountSummary]> {
        database.observe { db in try Self.summaries(db) }
    }

    public func observeAccountDetail(id: EntityID) -> AsyncStream<AccountDetail?> {
        database.observe { db in try Self.detail(db, accountId: id) }
    }

    static func summaries(_ db: Database) throws -> [AccountSummary] {
        let balances = try LocalAccountRepository.balances(db)
        return try LocalAccountRepository.activeAccounts(db).map {
            AccountSummary(account: $0, balance: balances[$0.id] ?? $0.initialBalance)
        }
    }

    static func detail(_ db: Database, accountId: EntityID) throws -> AccountDetail? {
        guard let record = try AccountRecord
            .filter(Column("id") == accountId && Column("deletedAt") == nil)
            .fetchOne(db)
        else { return nil }
        let account = record.domain

        // Toutes les transactions du compte (frais compris) : nécessaires au solde après chaque
        // ligne. Index sur accountId et transferAccountId. `id` départage deux transactions de même
        // date ET même création (ex. un transfert et son frais, enregistrés ensemble) : sans lui,
        // leur ordre — donc le solde affiché après chacune — pourrait changer d'un affichage à l'autre.
        let transactions = try TransactionRecord.fetchAll(db, sql: """
            SELECT * FROM transactions
            WHERE deletedAt IS NULL AND (accountId = ? OR transferAccountId = ?)
            ORDER BY date DESC, createdAt DESC, id DESC
            """, arguments: [accountId, accountId]).map(\.domain)

        let running = RunningBalances.compute(account: account, transactions: transactions)
        // Même formule SQL que la liste des comptes et le tableau de bord (fixture partagée).
        let balance = try LocalAccountRepository.balances(db)[accountId] ?? account.initialBalance

        // Transactions de frais de TOUTE la base (le frais d'une ligne peut être sur un autre
        // compte, et inversement) : elles n'ont pas de ligne propre.
        let feeIds = Set(try String.fetchAll(db, sql: """
            SELECT feeTransactionId FROM transactions
            WHERE feeTransactionId IS NOT NULL AND deletedAt IS NULL
            """))
        let feeAmounts = try feeAmountsByParent(db, accountId: accountId)
        let categories = try categoriesById(db)
        let others = try accountsById(db)

        let items = transactions
            .filter { !feeIds.contains($0.id) }
            .map { transaction in
                TransactionListItem(
                    transaction: transaction,
                    account: account,
                    transferAccount: transaction.type == .transfer
                        ? others[transaction.accountId == accountId ? (transaction.transferAccountId ?? "") : transaction.accountId]
                        : nil,
                    category: transaction.categoryId.flatMap { categories[$0] },
                    feeAmount: transaction.feeTransactionId.flatMap { feeAmounts[$0] },
                    runningBalance: running[transaction.id]
                )
            }
        return AccountDetail(summary: AccountSummary(account: account, balance: balance), transactions: items)
    }

    /// Montant de chaque transaction de frais rattachée à une transaction de ce compte.
    private static func feeAmountsByParent(_ db: Database, accountId: EntityID) throws -> [EntityID: MinorUnits] {
        let rows = try Row.fetchAll(db, sql: """
            SELECT fee.id AS id, fee.amount AS amount
            FROM transactions parent
            JOIN transactions fee ON fee.id = parent.feeTransactionId AND fee.deletedAt IS NULL
            WHERE parent.deletedAt IS NULL AND (parent.accountId = ? OR parent.transferAccountId = ?)
            """, arguments: [accountId, accountId])
        var amounts: [EntityID: MinorUnits] = [:]
        for row in rows { amounts[row["id"]] = row["amount"] }
        return amounts
    }

    private static func categoriesById(_ db: Database) throws -> [EntityID: ArzikinaDomain.Category] {
        let records = try CategoryRecord.filter(Column("deletedAt") == nil).fetchAll(db)
        return Dictionary(uniqueKeysWithValues: records.map { ($0.id, $0.domain) })
    }

    private static func accountsById(_ db: Database) throws -> [EntityID: Account] {
        Dictionary(uniqueKeysWithValues: try LocalAccountRepository.activeAccounts(db).map { ($0.id, $0) })
    }
}
