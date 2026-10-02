import XCTest
@testable import ArzikinaDomain

/// Données par défaut d'un nouvel utilisateur : valeurs recopiées d'Android `DefaultAccounts` et
/// `DefaultCategories` (un écart ici rendrait un compte créé sur iOS différent d'un compte Android).
final class DefaultDataTests: XCTestCase {

    private func ids() -> () -> EntityID {
        var next = 0
        return { next += 1; return "id-\(next)" }
    }

    func testAccountsMatchAndroid() {
        let accounts = DefaultData.accounts(now: 42, newId: ids())

        XCTAssertEqual(accounts.map(\.name), ["Espèces", "Banque", "Mobile Money", "Épargne", "Wallet"])
        XCTAssertEqual(accounts.map(\.type), [.cash, .bank, .mobileMoney, .savings, .cash], "Wallet : compte d'espèces, comme Android")
        XCTAssertEqual(accounts.map(\.icon), [.cash, .bank, .mobileMoney, .savings, .wallet])
        XCTAssertEqual(accounts.map(\.colorArgb), [0xFF16_A34A, 0xFF00_6C4F, 0xFFF5_9E0B, 0xFF00_A578, 0xFF4C_6B3F])
        XCTAssertEqual(accounts.map(\.displayOrder), [0, 1, 2, 3, 4])
        XCTAssertTrue(accounts.allSatisfy { $0.currencyCode == "XOF" && $0.initialBalance == 0 && $0.createdAt == 42 })
        XCTAssertTrue(accounts.allSatisfy { $0.defaultKey != nil }, "Noms de référence : traduits à l'affichage")
    }

    func testCategoriesMatchAndroid() {
        let categories = DefaultData.categories(now: 42, newId: ids())

        XCTAssertEqual(categories.count, 18)
        XCTAssertEqual(Set(categories.map(\.id)).count, 18)
        XCTAssertEqual(categories.filter { $0.type == .income }.map(\.name), ["Salaire", "Divers", "Remboursement de prêt reçu", "Emprunt reçu"])
        XCTAssertEqual(categories.compactMap(\.systemKey), SystemCategoryKey.allCases.filter { $0 != .giftsReceived }, "Chaque catégorie est reconnue par sa clé")
        XCTAssertEqual(SystemCategoryKey.of(name: "Cadeaux", type: .income), .giftsReceived, "« Cadeaux » en revenu (emprunt offert) reconnue, créée à la demande")

        let byKey = Dictionary(uniqueKeysWithValues: categories.map { ($0.systemKey!, $0) })
        XCTAssertEqual(byKey[.salary]?.colorArgb, 0xFF00_6C4F)
        XCTAssertEqual(byKey[.food]?.icon, .food)
        XCTAssertEqual(byKey[.electricity]?.colorArgb, 0xFFF5_9E0B)
        XCTAssertEqual(byKey[.home]?.colorArgb, 0xFF10_B981)
        XCTAssertEqual(byKey[.loanDisbursementBorrowed]?.colorArgb, 0xFFDC_2626)
        XCTAssertEqual(byKey[.loanRepaymentLent]?.icon, .loan)
        XCTAssertEqual(byKey[.fees]?.colorArgb, 0xFF92_400E)
        XCTAssertEqual(byKey[.fees]?.icon, .fee)
    }
}
