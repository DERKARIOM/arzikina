import ArzikinaDomain
import GRDB
import XCTest
@testable import ArzikinaData

/// Données par défaut créées à l'inscription : écrites une seule fois, sur une base vide, et
/// envoyées au serveur à la première synchronisation.
final class DefaultDataSeedingTests: XCTestCase {

    private var space: UserDataSpace!

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory(userId: "u1")
    }

    override func tearDown() {
        space.close()
    }

    private func first<T>(_ stream: AsyncStream<T>) async throws -> T {
        var iterator = stream.makeAsyncIterator()
        let value = await iterator.next()
        return try XCTUnwrap(value)
    }

    func testSeedsAccountsAndCategoriesAndQueuesTheirCreation() async throws {
        let seeded = try space.seedDefaultDataForNewAccount()
        XCTAssertTrue(seeded)

        let accounts = try await first(space.accounts.observeAccounts())
        let categories = try await first(space.categories.observeCategories(type: nil))
        XCTAssertEqual(accounts.count, 5)
        XCTAssertEqual(accounts.first?.defaultKey, .cash, "Ordre d'affichage d'Android")
        XCTAssertEqual(categories.count, 18)

        let queued = try await space.database.writer.read { db in
            try Row.fetchAll(db, sql: "SELECT entityType, operation FROM sync_queue")
        }
        XCTAssertEqual(queued.count, 23)
        XCTAssertTrue(queued.allSatisfy { ($0["operation"] as String?) == "CREATE" })
    }

    func testSecondCallAndNonEmptyBaseWriteNothing() async throws {
        XCTAssertTrue(try space.seedDefaultDataForNewAccount())
        XCTAssertFalse(try space.seedDefaultDataForNewAccount(), "Jamais deux fois")
        let accounts = try await first(space.accounts.observeAccounts())
        XCTAssertEqual(accounts.count, 5)

        let other = try UserDataSpace.inMemory(userId: "u2")
        defer { other.close() }
        try await other.categories.save(ArzikinaDomain.Category(id: "c", name: "Tontine", type: .expense))
        try await other.categories.delete(id: "c")
        XCTAssertFalse(try other.seedDefaultDataForNewAccount(), "Même une catégorie supprimée signale une base déjà utilisée")
    }

    func testSeededDataReachesTheServerOnFirstSync() async throws {
        let server = FakeSyncServer()
        let engine = SyncEngine(space: space, remote: server, accessToken: { "token" }, now: Clocks.system)
        try space.seedDefaultDataForNewAccount()

        let report = try await engine.synchronize()

        XCTAssertEqual(report.pushed, 23)
        XCTAssertEqual(server.count(.accounts), 5)
        XCTAssertEqual(server.count(.categories), 18)
        let pending = try await space.pendingChangesCount()
        XCTAssertEqual(pending, 0)
    }
}
