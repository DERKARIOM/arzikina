import ArzikinaDomain
import Foundation
import GRDB
import XCTest
@testable import ArzikinaData

final class SyncEngineTests: XCTestCase {

    private var clock: TestClock!
    private var space: UserDataSpace!
    private var server: FakeSyncServer!
    private var engine: SyncEngine!
    private var token: String? = "token"

    override func setUpWithError() throws {
        clock = TestClock()
        space = try UserDataSpace.inMemory(userId: "u1", now: clock.clock)
        server = FakeSyncServer()
        engine = makeEngine()
    }

    override func tearDown() {
        space.close()
    }

    private func makeEngine() -> SyncEngine {
        SyncEngine(space: space, remote: server, accessToken: { [unowned self] in self.token }, now: clock.clock)
    }

    // MARK: - Envoi

    func testLocalCreationIsPushedWithApiKeysAndAdoptsServerVersion() async throws {
        try await space.accounts.save(Account(id: "acc-1", name: "Caisse", initialBalance: 150_000, isExcludedFromStatistics: true))

        let report = try await engine.synchronize()

        XCTAssertEqual(report.pushed, 1)
        let pushed = try XCTUnwrap(server.pushedBodies.first)
        XCTAssertEqual(pushed.0, .accounts)
        XCTAssertEqual(pushed.1.first?.operation, .create)
        let entity = try XCTUnwrap(pushed.1.first?.entity)
        XCTAssertEqual(entity["initialBalanceMinor"], .int(150_000))
        XCTAssertNil(entity["initialBalance"])
        XCTAssertEqual(entity["isExcludedFromStatistics"], .int(1))
        XCTAssertEqual(entity["baseVersion"], .int(0))
        // Champ facultatif vide : envoyé explicitement à null (« effacer »), jamais omis.
        XCTAssertEqual(entity["cardLastFourDigits"], .null)

        let pending = try await space.pendingChangesCount()
        XCTAssertEqual(pending, 0)
        XCTAssertEqual(try localVersion("accounts", "acc-1"), 1)
        XCTAssertEqual(server.row(.accounts, "acc-1")?["initialBalanceMinor"], .int(150_000))
    }

    func testReferencesUseSyncIdKeys() async throws {
        try await space.accounts.save(Account(id: "acc-1", name: "A"))
        try await space.transactions.save(Transaction(id: "t-1", amount: 500, type: .expense, accountId: "acc-1", categoryId: "cat-9", date: 42, latitude: 13.5))

        _ = try await engine.synchronize()

        let row = try XCTUnwrap(server.row(.transactions, "t-1"))
        XCTAssertEqual(row["accountSyncId"], .string("acc-1"))
        XCTAssertEqual(row["categorySyncId"], .string("cat-9"))
        XCTAssertEqual(row["latitude"], .double(13.5))
        XCTAssertEqual(row["transferAccountSyncId"], .null)
        // Les comptes partent avant les transactions qui les référencent.
        XCTAssertEqual(server.pushedBodies.map(\.0), [.accounts, .transactions])
    }

    func testDeletionSendsOnlyIdAndBaseVersion() async throws {
        try await space.accounts.save(Account(id: "acc-1", name: "A"))
        _ = try await engine.synchronize()
        try await space.accounts.delete(id: "acc-1")

        _ = try await engine.synchronize()

        let operation = try XCTUnwrap(server.pushedBodies.last?.1.first)
        XCTAssertEqual(operation.operation, .delete)
        XCTAssertEqual(operation.entity, ["id": .string("acc-1"), "baseVersion": .int(1)])
        XCTAssertNotNil(server.row(.accounts, "acc-1")?["deletedAt"]?.int64Value)
        XCTAssertEqual(try localVersion("accounts", "acc-1"), 2)
    }

    func testConflictAdoptsServerArbitration() async throws {
        try await space.accounts.save(Account(id: "acc-1", name: "Initial"))
        _ = try await engine.synchronize()
        // Un autre appareil modifie le compte (version serveur 2)…
        var other = try XCTUnwrap(server.row(.accounts, "acc-1"))
        other["name"] = .string("Autre appareil")
        other["baseVersion"] = .int(1)
        server.otherDevice(.accounts, .update, other)
        // … pendant que cet iPhone le modifie hors ligne en partant de la version 1.
        try await space.accounts.save(Account(id: "acc-1", name: "iPhone"))

        _ = try await engine.synchronize()

        // push.php : la dernière écriture ARRIVÉE gagne (conflict_resolved) ; l'app l'adopte.
        XCTAssertEqual(server.row(.accounts, "acc-1")?["name"], .string("iPhone"))
        XCTAssertEqual(server.row(.accounts, "acc-1")?["version"], .int(3))
        let account = try await space.accounts.account(id: "acc-1")
        XCTAssertEqual(account?.name, "iPhone")
        XCTAssertEqual(try localVersion("accounts", "acc-1"), 3)
    }

    func testUpdateUnknownToServerIsResentAsCreate() async throws {
        // Ligne connue localement (version 4) mais absente du serveur (ex. base serveur restaurée).
        try await space.accounts.save(Account(id: "acc-1", name: "Perdu"))
        try await space.database.writer.write { db in
            try db.execute(sql: "UPDATE accounts SET version = 4")
            try db.execute(sql: "UPDATE sync_queue SET operation = 'UPDATE'")
        }

        let report = try await engine.synchronize()

        XCTAssertEqual(server.pushedBodies.map { $0.1.first?.operation }, [.update, .create])
        XCTAssertEqual(server.row(.accounts, "acc-1")?["name"], .string("Perdu"))
        XCTAssertEqual(report.pushed, 1)
        let pending = try await space.pendingChangesCount()
        XCTAssertEqual(pending, 0)
    }

    func testDeletionUnknownToServerIsDropped() async throws {
        try await space.accounts.save(Account(id: "acc-1", name: "A"))
        try await space.database.writer.write { db in
            try db.execute(sql: "UPDATE accounts SET version = 1, deletedAt = 5")
            try db.execute(sql: "UPDATE sync_queue SET operation = 'DELETE'")
        }

        _ = try await engine.synchronize()

        let pending = try await space.pendingChangesCount()
        XCTAssertEqual(pending, 0)
        XCTAssertEqual(server.count(.accounts), 0)
    }

    func testEditDuringPushIsKeptAndSentAsUpdate() async throws {
        try await space.accounts.save(Account(id: "acc-1", name: "Avant"))
        let space = self.space!
        server.duringPush = { try await space.accounts.save(Account(id: "acc-1", name: "Pendant")) }

        _ = try await engine.synchronize()

        // La modification faite pendant l'envoi n'est pas écrasée par la réponse du serveur…
        let account = try await space.accounts.account(id: "acc-1")
        XCTAssertEqual(account?.name, "Pendant")
        // … et repartira comme une MODIFICATION (un second CREATE serait ignoré par push.php).
        let entry = try await queueEntry("acc-1")
        XCTAssertEqual(entry?.operation, .update)
        XCTAssertEqual(try localVersion("accounts", "acc-1"), 1)

        server.duringPush = nil
        _ = try await engine.synchronize()
        XCTAssertEqual(server.row(.accounts, "acc-1")?["name"], .string("Pendant"))
        XCTAssertEqual(server.row(.accounts, "acc-1")?["version"], .int(2))
    }

    func testDeleteDuringCreationPushIsForwarded() async throws {
        try await space.accounts.save(Account(id: "acc-1", name: "Éphémère"))
        let space = self.space!
        server.duringPush = { try await space.accounts.delete(id: "acc-1") }

        _ = try await engine.synchronize()
        server.duringPush = nil
        _ = try await engine.synchronize()

        XCTAssertNotNil(server.row(.accounts, "acc-1")?["deletedAt"]?.int64Value)
        let pending = try await space.pendingChangesCount()
        XCTAssertEqual(pending, 0)
    }

    func testRejectedOperationStaysQueuedWithError() async throws {
        try await space.accounts.save(Account(id: "acc-1", name: "A"))
        try await space.database.writer.write { db in
            try db.execute(sql: "UPDATE sync_queue SET operation = 'UPDATE'")
            try db.execute(sql: "UPDATE accounts SET version = 1")
        }
        // Serveur qui refuse toute écriture.
        let refusing = RefusingRemote()
        let engine = SyncEngine(space: space, remote: refusing, accessToken: { "t" }, now: clock.clock)

        let report = try await engine.synchronize()

        XCTAssertEqual(report.failed, 1)
        let entry = try await queueEntry("acc-1")
        XCTAssertEqual(entry?.attemptCount, 1)
        XCTAssertEqual(entry?.lastError, "server_error")
    }

    /// Parcours complet : formulaire → enregistrement local → envoi. Un objectif d'épargne repassé
    /// en compte classique envoie EXPLICITEMENT `null` pour sa cible (effacée sur le serveur).
    func testAccountFormChangesReachTheServer() async throws {
        let draft = AccountDraft(name: "Moto", initialBalanceInput: "5 000", type: .savingsGoal, savingsTargetInput: "400 000", savingsDescriptionInput: "Yamaha")
        guard case .valid(let goal) = AccountForm.validate(draft, existing: nil, newId: "goal-1", newDisplayOrder: 2, currentYear: 2026, currentMonth: 10) else {
            return XCTFail("Formulaire valide attendu")
        }
        try await space.accounts.save(goal)
        _ = try await engine.synchronize()
        XCTAssertEqual(server.row(.accounts, "goal-1")?["savingsTargetAmount"], .int(40_000_000))
        XCTAssertEqual(server.row(.accounts, "goal-1")?["initialBalanceMinor"], .int(500_000))
        XCTAssertEqual(server.row(.accounts, "goal-1")?["type"], .string("SAVINGS_GOAL"))

        let saved = try await space.accounts.account(id: "goal-1")
        let existing = try XCTUnwrap(saved)
        let back = AccountDraft(editing: existing, displayName: existing.name)
        var asSavings = back
        asSavings.type = .savings
        guard case .valid(let regular) = AccountForm.validate(asSavings, existing: existing, newId: "unused", newDisplayOrder: 0, currentYear: 2026, currentMonth: 10, confirmedSavingsGoalRemoval: true) else {
            return XCTFail("Conversion confirmée attendue")
        }
        try await space.accounts.save(regular)
        _ = try await engine.synchronize()

        let update = try XCTUnwrap(server.pushedBodies.last?.1.first)
        XCTAssertEqual(update.operation, .update)
        XCTAssertEqual(update.entity["savingsTargetAmount"], .null)
        XCTAssertEqual(server.row(.accounts, "goal-1")?["savingsTargetAmount"], .null)
        XCTAssertEqual(server.row(.accounts, "goal-1")?["type"], .string("SAVINGS"))
        XCTAssertEqual(server.row(.accounts, "goal-1")?["displayOrder"], .int(2))
    }

    // MARK: - Réception

    func testPullMapsServerRowsToLocalColumns() async throws {
        server.otherDevice(.accounts, .create, [
            "id": .string("acc-9"), "name": .string("Orange Money"), "icon": .string("WALLET"),
            "colorArgb": .int(4_282_562_968), "currencyCode": .string("XOF"),
            "initialBalanceMinor": .int(2_500_000), "type": .string("MOBILE_MONEY"),
            "isExcludedFromStatistics": .int(1), "displayOrder": .int(3)
        ])
        server.otherDevice(.transactions, .create, [
            "id": .string("t-9"), "amount": .int(10_000), "type": .string("EXPENSE"),
            "accountSyncId": .string("acc-9"), "date": .int(1_700_000_000_000), "description": .string("Taxi")
        ])

        let report = try await engine.synchronize()

        XCTAssertEqual(report.pulled, 2)
        let account = try await space.accounts.account(id: "acc-9")
        XCTAssertEqual(account?.initialBalance, 2_500_000)
        XCTAssertEqual(account?.isExcludedFromStatistics, true)
        XCTAssertEqual(account?.displayOrder, 3)
        let transaction = try await space.transactions.transaction(id: "t-9")
        XCTAssertEqual(transaction?.accountId, "acc-9")
        XCTAssertEqual(transaction?.description, "Taxi")
        // Rien n'est renvoyé au serveur pour des données reçues.
        let pending = try await space.pendingChangesCount()
        XCTAssertEqual(pending, 0)
        XCTAssertTrue(server.pushedBodies.isEmpty)
    }

    func testPullToleratesNumbersSentAsStrings() async throws {
        let entity: [String: JSONValue] = [
            "id": .string("c-1"), "name": .string("Loyer"), "icon": .string("HOME"),
            "colorArgb": .string("4282562968"), "type": .string("EXPENSE"),
            "createdAt": .string("10"), "updatedAt": .string("20"), "deletedAt": .null, "version": .string("3")
        ]
        let decoded = try XCTUnwrap(SyncRowCodec.localValues(from: entity, schema: .categories))
        XCTAssertEqual(Int64.fromDatabaseValue(decoded.values["colorArgb"]!), 4_282_562_968)
        XCTAssertEqual(Int64.fromDatabaseValue(decoded.values["version"]!), 3)
    }

    func testPullDoesNotOverwritePendingLocalChange() async throws {
        server.otherDevice(.accounts, .create, ["id": .string("acc-1"), "name": .string("Serveur")])
        _ = try await engine.synchronize()
        // Modification locale en attente, et le serveur ne peut pas être joint pour l'envoi…
        try await space.accounts.save(Account(id: "acc-1", name: "Local"))
        var other = try XCTUnwrap(server.row(.accounts, "acc-1"))
        other["name"] = .string("Serveur 2")
        server.otherDevice(.accounts, .update, other)

        let store = SyncStore(database: space.database)
        let page = try await server.pull(.accounts, updatedAfter: 0, token: "t")
        try await store.applyPulled(page.entities, schema: .accounts, newCursor: page.serverTime)

        let account = try await space.accounts.account(id: "acc-1")
        XCTAssertEqual(account?.name, "Local")
    }

    func testPullUsesIncrementalCursor() async throws {
        server.otherDevice(.categories, .create, ["id": .string("c-1"), "name": .string("A")])
        _ = try await engine.synchronize()
        let firstCursor = try XCTUnwrap(server.pullRequests.last { $0.0 == .categories }?.1)
        XCTAssertEqual(firstCursor, 0)

        _ = try await engine.synchronize()

        let secondCursor = try XCTUnwrap(server.pullRequests.last { $0.0 == .categories }?.1)
        XCTAssertGreaterThan(secondCursor, 0)
    }

    /// Le bug de pagination de pull.php : reprendre à `serverTime` après une page pleine sautait
    /// toutes les lignes au-delà des 500 premières. Ici 1 234 lignes, dont beaucoup partagent la
    /// même milliseconde, y compris À CHEVAL sur les limites de page.
    func testPaginationReceivesEveryRowAcrossFullPagesAndTies() async throws {
        server.tick = 0
        for index in 0..<1_234 {
            if index % 7 == 0 { server.tick = 1 } else { server.tick = 0 }
            server.otherDevice(.categories, .create, ["id": .string(String(format: "c-%04d", index)), "name": .string("C\(index)")])
        }

        let report = try await engine.synchronize()

        XCTAssertEqual(try localCount("categories"), 1_234)
        XCTAssertGreaterThanOrEqual(report.pulled, 1_234)
        XCTAssertGreaterThanOrEqual(server.pullRequests.filter { $0.0 == .categories }.count, 3)
    }

    /// Règle de curseur PARTAGÉE avec Android et le Web (`shared/test-fixtures/pull-cursor.json`).
    func testPullCursorMatchesSharedFixture() throws {
        var url = URL(fileURLWithPath: #filePath)
        for _ in 0..<6 { url.deleteLastPathComponent() }
        let data = try Data(contentsOf: url.appendingPathComponent("shared/test-fixtures/pull-cursor.json"))
        let fixture = try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual((fixture["serverBatchLimit"] as? NSNumber)?.intValue, SyncPullCursor.serverBatchLimit)
        let cases = try XCTUnwrap(fixture["cases"] as? [[String: Any]])
        XCTAssertFalse(cases.isEmpty)
        for item in cases {
            let next = SyncPullCursor.next(
                current: (item["current"] as! NSNumber).int64Value,
                updatedAts: (item["updatedAts"] as! [NSNumber]).map(\.int64Value),
                serverTime: (item["serverTime"] as! NSNumber).int64Value,
                isFull: item["full"] as! Bool
            )
            XCTAssertEqual(next, (item["expected"] as! NSNumber).int64Value, item["name"] as? String ?? "")
        }
    }

    // MARK: - Erreurs et état

    func testUnauthorizedMeansSessionExpiredAndKeepsPendingChanges() async throws {
        try await space.accounts.save(Account(id: "acc-1", name: "A"))
        server.nextFailure = .http(status: 401, code: "unauthorized")

        await assertSyncThrows(.sessionExpired)

        let pending = try await space.pendingChangesCount()
        XCTAssertEqual(pending, 1)
    }

    func testTransportFailureMeansOffline() async throws {
        server.nextFailure = .transport
        await assertSyncThrows(.offline)
    }

    func testServerErrorIsReported() async throws {
        server.nextFailure = .http(status: 500, code: "server_error")
        await assertSyncThrows(.server(status: 500))
    }

    func testMissingTokenMeansNotSignedIn() async throws {
        token = nil
        await assertSyncThrows(.notSignedIn)
    }

    func testSuccessfulSyncRecordsItsDate() async throws {
        clock.advance(by: 5_000)
        _ = try await engine.synchronize()

        var iterator = engine.observeLastSuccessfulSync().makeAsyncIterator()
        let value = await iterator.next()
        XCTAssertEqual(value, .some(clock.now))
    }

    func testQueueRevisionChangesOnEveryEdit() async throws {
        try await space.accounts.save(Account(id: "acc-1", name: "A"))
        let first = try await queueEntry("acc-1")
        try await space.accounts.save(Account(id: "acc-1", name: "B"))
        let second = try await queueEntry("acc-1")
        XCTAssertEqual(first?.revision, 0)
        XCTAssertEqual(second?.revision, 1)
    }

    // MARK: - Outils

    private func assertSyncThrows(_ expected: SyncError, file: StaticString = #filePath, line: UInt = #line) async {
        do {
            _ = try await engine.synchronize()
            XCTFail("La synchronisation aurait dû échouer", file: file, line: line)
        } catch {
            XCTAssertEqual(error as? SyncError, expected, file: file, line: line)
        }
    }

    private func localVersion(_ table: String, _ id: String) throws -> Int64? {
        try space.database.writer.read { db in
            try Int64.fetchOne(db, sql: "SELECT version FROM \(table) WHERE id = ?", arguments: [id])
        }
    }

    private func localCount(_ table: String) throws -> Int {
        try space.database.writer.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") ?? 0 }
    }

    private func queueEntry(_ id: String) async throws -> SyncQueueRecord? {
        try await space.database.writer.read { db in
            try SyncQueueRecord.filter(Column("entityId") == id).fetchOne(db)
        }
    }
}

/// Serveur qui répond `error/server_error` à chaque opération et ne renvoie rien au pull.
private struct RefusingRemote: SyncRemote {
    func pull(_ type: SyncEntityType, updatedAfter: Int64, token: String) async throws -> PullPage {
        PullPage(entities: [], serverTime: 1)
    }

    func push(_ type: SyncEntityType, operations: [PushOperation], token: String) async throws -> PushResponse {
        PushResponse(
            results: operations.map { _ in PushResult(status: .error, entityId: nil, serverEntity: nil, errorCode: "server_error") },
            serverTime: 1
        )
    }
}
