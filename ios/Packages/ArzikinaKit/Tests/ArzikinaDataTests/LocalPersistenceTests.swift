import ArzikinaDomain
import Foundation
import GRDB
import XCTest
@testable import ArzikinaData

/// Horloge réglable pour vérifier les dates de création/modification.
final class TestClock: @unchecked Sendable {
    private let lock = NSLock()
    private var value: EpochMillis

    init(_ start: EpochMillis = 1_000) { value = start }

    var now: EpochMillis {
        lock.lock(); defer { lock.unlock() }
        return value
    }

    func advance(by delta: EpochMillis = 1_000) {
        lock.lock(); defer { lock.unlock() }
        value += delta
    }

    var clock: Clock { { [unowned self] in self.now } }
}

final class LocalPersistenceTests: XCTestCase {

    private var clock: TestClock!
    private var space: UserDataSpace!

    override func setUpWithError() throws {
        clock = TestClock()
        space = try UserDataSpace.inMemory(now: clock.clock)
    }

    override func tearDown() {
        space.close()
    }

    // MARK: - Schéma

    func testMigrationsCreateEveryTable() throws {
        let tables = try space.database.writer.read { db in
            try String.fetchAll(db, sql: "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'grdb_%' ORDER BY name")
        }
        let expected = (SyncEntityType.allCases.map(\.rawValue) + ["sync_queue", "sync_cursors", "sync_state"]).sorted()
        XCTAssertEqual(tables, expected, "Une table par type synchronisé + file d'envoi + curseurs + état")
    }

    func testEverySyncedTableHasSyncColumns() throws {
        try space.database.writer.read { db in
            for type in SyncEntityType.allCases {
                let columns = Set(try db.columns(in: type.rawValue).map(\.name))
                for required in ["id", "createdAt", "updatedAt", "deletedAt", "version"] {
                    XCTAssertTrue(columns.contains(required), "\(type.rawValue).\(required)")
                }
            }
        }
    }

    /// Le registre de synchronisation (miroir de `entity_sync_configs.php`) et le schéma local
    /// doivent décrire exactement les mêmes colonnes, avec la même nullabilité.
    func testSyncRegistryMatchesLocalSchema() throws {
        XCTAssertEqual(Set(SyncEntitySchema.all.map(\.type)), Set(SyncEntityType.allCases))
        try space.database.writer.read { db in
            for schema in SyncEntitySchema.all {
                let columns = try db.columns(in: schema.table)
                let specific = columns.filter { !["id", "createdAt", "updatedAt", "deletedAt", "version"].contains($0.name) }
                XCTAssertEqual(specific.map(\.name).sorted(), schema.fields.map(\.local).sorted(), schema.table)
                for field in schema.fields {
                    let column = try XCTUnwrap(columns.first { $0.name == field.local })
                    XCTAssertEqual(!column.isNotNull, field.nullable, "\(schema.table).\(field.local)")
                }
            }
        }
    }

    func testMigrationsAreIdempotent() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appendingPathComponent("test.sqlite")
        try AppDatabase.open(at: url).close()
        let reopened = try AppDatabase.open(at: url)
        let applied = try reopened.writer.read { db in try AppDatabaseSchema.migrator.appliedMigrations(db) }
        XCTAssertEqual(applied, ["v1_initial", "v2_sync_engine", "v3_dashboard_indexes", "v4_loan_gift"])
        try reopened.close()
    }

    /// Les prêts reçus avant la v4 n'ont pas les champs « cadeau » : ils sont redemandés au serveur.
    func testLoanGiftMigrationRepullsLoansOnly() throws {
        let queue = try DatabaseQueue()
        let migrator = AppDatabaseSchema.migrator
        try migrator.migrate(queue, upTo: "v3_dashboard_indexes")
        try queue.write { db in
            try db.execute(sql: "INSERT INTO sync_cursors (entityType, lastPulledAt) VALUES ('loans', 123), ('transactions', 456)")
        }
        try migrator.migrate(queue)
        let cursors = try queue.read { db in
            Dictionary(uniqueKeysWithValues: try Row.fetchAll(db, sql: "SELECT entityType, lastPulledAt FROM sync_cursors").map { ($0["entityType"] as String, $0["lastPulledAt"] as Int64) })
        }
        XCTAssertEqual(cursors, ["loans": 0, "transactions": 456])
    }

    // MARK: - Écritures et file d'envoi

    func testCreateThenUpdateKeepsCreationAndSingleCreateInQueue() async throws {
        var account = Account(id: "acc-1", name: "Espèces", initialBalance: 50_000)
        try await space.accounts.save(account)
        clock.advance()
        account.name = "Caisse"
        try await space.accounts.save(account)

        let stored = try await space.database.writer.read { db in try AccountRecord.fetchOne(db, key: "acc-1") }
        XCTAssertEqual(stored?.name, "Caisse")
        XCTAssertEqual(stored?.createdAt, 1_000)
        XCTAssertEqual(stored?.updatedAt, 2_000)
        XCTAssertEqual(stored?.version, 0, "Jamais envoyé au serveur")
        let queue = try await queueEntries()
        XCTAssertEqual(queue.map(\.operation), [.create], "CREATE + UPDATE hors ligne = un seul CREATE")
    }

    func testProvidedCreationDateIsKept() async throws {
        try await space.accounts.save(Account(id: "acc-1", name: "Banque", createdAt: 42))
        let stored = try await space.database.writer.read { db in try AccountRecord.fetchOne(db, key: "acc-1") }
        XCTAssertEqual(stored?.createdAt, 42)
    }

    func testCreateThenDeleteLeavesNothingToSend() async throws {
        try await space.categories.save(Category(id: "cat-1", name: "Nourriture", type: .expense))
        try await space.categories.delete(id: "cat-1")
        let queue = try await queueEntries()
        XCTAssertTrue(queue.isEmpty, "Créée puis supprimée hors ligne : rien à envoyer")
        let category = try await space.categories.category(id: "cat-1")
        XCTAssertNil(category)
    }

    func testSyncedEntityUpdateThenDelete() async throws {
        // Ligne déjà connue du serveur (version 3), sans entrée en attente.
        try await insertSyncedTransaction(id: "t-1", version: 3)
        let fetched = try await space.transactions.transaction(id: "t-1")
        var transaction = try XCTUnwrap(fetched)
        transaction.amount = 9_900
        try await space.transactions.save(transaction)
        var queue = try await queueEntries()
        XCTAssertEqual(queue.map(\.operation), [.update])

        try await space.transactions.delete(id: "t-1")
        queue = try await queueEntries()
        XCTAssertEqual(queue.map(\.operation), [.delete])
        let stored = try await space.database.writer.read { db in try TransactionRecord.fetchOne(db, key: "t-1") }
        XCTAssertNotNil(stored?.deletedAt, "Suppression DOUCE : la ligne reste pour être synchronisée")
        XCTAssertEqual(stored?.version, 3)
    }

    func testDeletedEntityIsNotResurrected() async throws {
        try await insertSyncedTransaction(id: "t-1", version: 1)
        try await space.transactions.delete(id: "t-1")
        try await space.transactions.save(Transaction(id: "t-1", amount: 1, type: .expense, accountId: "a", date: 0))
        let queue = try await queueEntries()
        XCTAssertEqual(queue.map(\.operation), [.delete])
        let transaction = try await space.transactions.transaction(id: "t-1")
        XCTAssertNil(transaction)
    }

    func testDomainRoundTrip() async throws {
        let account = Account(id: "acc-goal", name: "Moto", icon: .savings, colorArgb: 0xFF12_3456, currencyCode: "XOF",
                              initialBalance: 1_000, type: .savingsGoal, cardLastFourDigits: nil, isExcludedFromStatistics: true,
                              displayOrder: 3, savingsTargetAmount: 50_000_000, savingsDescription: "Pour la rentrée", createdAt: 7)
        try await space.accounts.save(account)
        let readAccount = try await space.accounts.account(id: "acc-goal")
        XCTAssertEqual(readAccount, account)

        let transaction = Transaction(id: "t-9", amount: 2_500, type: .transfer, accountId: "a", transferAccountId: "b", categoryId: nil,
                                      date: 1_790_000_000_000, description: "Épargne", latitude: 13.5, longitude: 2.1,
                                      paymentMethod: .mobileMoney, feeTransactionId: "t-fee", feeType: .transfer, createdAt: 8)
        try await space.transactions.save(transaction)
        let readTransaction = try await space.transactions.transaction(id: "t-9")
        XCTAssertEqual(readTransaction, transaction)
    }

    // MARK: - Lectures observées

    func testAccountsObservationFollowsChanges() async throws {
        var iterator = space.accounts.observeAccounts().makeAsyncIterator()
        let initial = await iterator.next()
        XCTAssertEqual(initial, [])

        try await space.accounts.save(Account(id: "b", name: "Banque", displayOrder: 2))
        try await space.accounts.save(Account(id: "a", name: "Espèces", displayOrder: 1))
        var names = await nextValue(&iterator) { $0.count == 2 }?.map(\.name)
        XCTAssertEqual(names, ["Espèces", "Banque"], "Triés par ordre d'affichage")

        try await space.accounts.delete(id: "a")
        names = await nextValue(&iterator) { $0.count == 1 }?.map(\.name)
        XCTAssertEqual(names, ["Banque"], "Comptes supprimés masqués")
    }

    func testRecentTransactionsAndDateRange() async throws {
        for (index, date) in [100, 300, 200, 400].enumerated() {
            try await space.transactions.save(Transaction(id: "t\(index)", amount: 1, type: .expense, accountId: "a", date: EpochMillis(date)))
        }
        var recent = space.transactions.observeRecentTransactions(limit: 2).makeAsyncIterator()
        let recentDates = await recent.next()?.map(\.date)
        XCTAssertEqual(recentDates, [400, 300])

        var ranged = space.transactions.observeTransactions(from: 200, to: 400).makeAsyncIterator()
        let rangedDates = await ranged.next()?.map(\.date)
        XCTAssertEqual(rangedDates, [300, 200], "Borne de début incluse, borne de fin exclue")
    }

    func testCategoriesFilteredByType() async throws {
        try await space.categories.save(Category(id: "c1", name: "salaire", type: .income))
        try await space.categories.save(Category(id: "c2", name: "Transport", type: .expense))
        try await space.categories.save(Category(id: "c3", name: "alimentation", type: .expense))
        var iterator = space.categories.observeCategories(type: .expense).makeAsyncIterator()
        let names = await iterator.next()?.map(\.name)
        XCTAssertEqual(names, ["alimentation", "Transport"], "Filtrées et triées sans tenir compte de la casse")
    }

    func testPendingChangesCount() async throws {
        try await space.accounts.save(Account(id: "a", name: "A"))
        try await space.transactions.save(Transaction(id: "t", amount: 1, type: .expense, accountId: "a", date: 0))
        let count = try await space.pendingChangesCount()
        XCTAssertEqual(count, 2)
    }

    // MARK: - Une base par utilisateur

    func testEachUserHasHisOwnDatabase() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let locator = UserDatabaseLocator(directory: directory)

        let awa = try UserDataSpace.open(userId: "user-awa", locator: locator)
        try await awa.accounts.save(Account(id: "a", name: "Compte d'Awa"))
        let moussa = try UserDataSpace.open(userId: "user-moussa", locator: locator)
        let moussaAccount = try await moussa.accounts.account(id: "a")
        XCTAssertNil(moussaAccount, "Les données d'un utilisateur ne sont jamais visibles par un autre")
        XCTAssertGreaterThan(awa.sizeOnDisk(), 0)

        awa.close()
        let reopened = try UserDataSpace.open(userId: "user-awa", locator: locator)
        let reopenedAccount = try await reopened.accounts.account(id: "a")
        XCTAssertEqual(reopenedAccount?.name, "Compte d'Awa", "Données conservées après déconnexion")

        try reopened.closeAndErase()
        XCTAssertFalse(FileManager.default.fileExists(atPath: locator.databaseURL(for: "user-awa").path))
        let erased = try UserDataSpace.open(userId: "user-awa", locator: locator)
        let erasedAccount = try await erased.accounts.account(id: "a")
        XCTAssertNil(erasedAccount, "Base vidée")
        erased.close()
        moussa.close()
    }

    func testDatabaseFileNameCannotEscapeDirectory() {
        let locator = UserDatabaseLocator(directory: URL(fileURLWithPath: "/tmp/arzikina"))
        XCTAssertEqual(locator.databaseURL(for: "4f6c1a2e-9b7d-4e1f-8a3c-1234567890ab").lastPathComponent, "user-4f6c1a2e-9b7d-4e1f-8a3c-1234567890ab.sqlite")
        XCTAssertEqual(locator.databaseURL(for: "../../etc/passwd").lastPathComponent, "user-etcpasswd.sqlite")
        XCTAssertEqual(locator.databaseURL(for: "../../etc/passwd").deletingLastPathComponent().path, "/tmp/arzikina")
        XCTAssertEqual(locator.databaseURL(for: "").lastPathComponent, "user-unknown.sqlite")
    }

    // MARK: - Outils

    private func queueEntries() async throws -> [SyncQueueRecord] {
        try await space.database.writer.read { db in try SyncQueueRecord.order(Column("id")).fetchAll(db) }
    }

    /// Simule une transaction reçue du serveur (version > 0, rien en attente d'envoi).
    private func insertSyncedTransaction(id: String, version: Int64) async throws {
        let record = TransactionRecord(
            Transaction(id: id, amount: 1_000, type: .expense, accountId: "a", date: 0),
            meta: SyncMetadata(createdAt: 1, updatedAt: 1, deletedAt: nil, version: version)
        )
        try await space.database.writer.write { db in try record.insert(db) }
    }

    /// Prochaine valeur observée satisfaisant [predicate] (les écritures successives peuvent être
    /// regroupées ou détaillées par l'observation).
    private func nextValue<Value>(_ iterator: inout AsyncStream<Value>.Iterator, where predicate: (Value) -> Bool) async -> Value? {
        for _ in 0..<10 {
            guard let value = await iterator.next() else { return nil }
            if predicate(value) { return value }
        }
        return nil
    }
}
