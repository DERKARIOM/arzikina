import ArzikinaDomain
import GRDB
import XCTest
@testable import ArzikinaData

final class TemplateRepositoryTests: XCTestCase {

    private var space: UserDataSpace!

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory()
    }

    override func tearDown() {
        space.close()
    }

    private func templates() async throws -> [TransactionTemplate] {
        var iterator = space.templates.observeTemplates().makeAsyncIterator()
        let value = await iterator.next()
        return try XCTUnwrap(value)
    }

    private func queue() async throws -> [String: String] {
        let rows = try await space.database.writer.read { db in
            try Row.fetchAll(db, sql: "SELECT entityId, operation FROM sync_queue WHERE entityType = 'transaction_templates'")
        }
        return Dictionary(uniqueKeysWithValues: rows.map { ($0["entityId"] as String, $0["operation"] as String) })
    }

    private func template(_ id: String, _ name: String, favorite: Bool = false) -> TransactionTemplate {
        TransactionTemplate(id: id, name: name, type: .expense, amount: 1_000, categoryId: "c", accountId: "a", isFavorite: favorite, sourceTransactionId: "t-\(id)", createdAt: 5)
    }

    func testOrderFavoritesThenName() async throws {
        for item in [template("1", "zébu"), template("2", "Café"), template("3", "Thé", favorite: true), template("4", "Ail")] {
            try await space.templates.save(item)
        }
        let names = try await templates().map(\.name)
        XCTAssertEqual(names, ["Thé", "Ail", "Café", "zébu"])
    }

    func testDuplicateIsANewIndependentTemplate() async throws {
        try await space.templates.save(template("1", "Déjeuner", favorite: true))
        let copyId = try await space.templates.duplicate(id: "1", name: "Déjeuner (copie)")
        let copy = try await space.templates.template(id: copyId)
        XCTAssertEqual(copy?.name, "Déjeuner (copie)")
        XCTAssertEqual(copy?.amount, 1_000)
        XCTAssertEqual(copy?.isFavorite, false)
        XCTAssertNil(copy?.sourceTransactionId, "La copie n'est pas liée à la transaction d'origine")
        let queued = try await queue()
        XCTAssertEqual(queued[copyId], "CREATE")
    }

    func testFavoriteAndSaveKeepTheSourceTransaction() async throws {
        try await space.templates.save(template("1", "Déjeuner"))
        try await space.templates.setFavorite(id: "1", isFavorite: true)
        let loaded = try await space.templates.template(id: "1")
        var edited = try XCTUnwrap(loaded)
        XCTAssertTrue(edited.isFavorite)
        edited.sourceTransactionId = "autre"
        edited.amount = 2_000
        try await space.templates.save(edited)
        let stored = try await space.templates.template(id: "1")
        XCTAssertEqual(stored?.amount, 2_000)
        XCTAssertEqual(stored?.sourceTransactionId, "t-1", "Lien figé à la création")
    }

    func testDeleteIsSoftAndNeverResurrected() async throws {
        try await space.templates.save(template("1", "Déjeuner"))
        try await space.database.writer.write { db in
            try db.execute(sql: "UPDATE transaction_templates SET version = 2")
            try db.execute(sql: "DELETE FROM sync_queue") // déjà envoyé
        }
        try await space.templates.delete(id: "1")
        let remaining = try await templates()
        XCTAssertTrue(remaining.isEmpty)
        let queued = try await queue()
        XCTAssertEqual(queued["1"], "DELETE")
        do {
            try await space.templates.save(template("1", "Déjeuner"))
            XCTFail("Supprimé : jamais recréé")
        } catch let error as TemplateWriteError {
            XCTAssertEqual(error, .templateNotFound)
        }
        do {
            try await space.templates.setFavorite(id: "1", isFavorite: true)
            XCTFail()
        } catch let error as TemplateWriteError {
            XCTAssertEqual(error, .templateNotFound)
        }
    }

    func testOnlyOneTemplatePerTransaction() async throws {
        try await space.templates.save(template("1", "Déjeuner"))
        let linked = try await space.templates.template(createdFromTransaction: "t-1")
        XCTAssertEqual(linked?.id, "1")
        var second = template("2", "Autre")
        second.sourceTransactionId = "t-1"
        do {
            try await space.templates.save(second)
            XCTFail("Déjà un modèle pour cette transaction")
        } catch let error as TemplateWriteError {
            XCTAssertEqual(error, .alreadyLinked(existingTemplateId: "1"))
        }
        try await space.templates.delete(id: "1")
        try await space.templates.save(second)
        let relinked = try await space.templates.template(createdFromTransaction: "t-1")
        XCTAssertEqual(relinked?.id, "2", "Le modèle supprimé ne compte plus")
    }
}
