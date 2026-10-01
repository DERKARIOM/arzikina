import XCTest
@testable import ArzikinaDomain

/// Formulaire de catégorie — comportements d'Android `CategoryFormViewModel`.
final class CategoryFormTests: XCTestCase {

    private func build(_ draft: CategoryDraft, existing: ArzikinaDomain.Category? = nil, canonical: @escaping (String, TransactionType) -> String = { name, _ in name }) -> Result<ArzikinaDomain.Category, CategoryFormError> {
        CategoryForm.build(draft, existing: existing, newId: "new", now: 1_000, canonicalName: canonical)
    }

    func testNewCategoryDefaultsMatchAndroid() {
        let draft = CategoryDraft()
        XCTAssertEqual(draft.type, .expense)
        XCTAssertEqual(draft.icon, .other)
        XCTAssertEqual(draft.colorArgb, 0xFF10_B981)
        XCTAssertEqual(CategoryDraft(type: .transfer).type, .expense, "Une catégorie n'est jamais un transfert")
    }

    func testNameIsRequiredAndTrimmed() {
        var draft = CategoryDraft()
        draft.name = "   "
        XCTAssertEqual(try? build(draft).get(), nil)
        if case .failure(let error) = build(draft) { XCTAssertEqual(error, .nameRequired) } else { XCTFail() }

        draft.name = "  Dépenses maman "
        draft.icon = .home
        draft.type = .expense
        let category = try? build(draft).get()
        XCTAssertEqual(category?.id, "new")
        XCTAssertEqual(category?.name, "Dépenses maman")
        XCTAssertEqual(category?.icon, .home)
        XCTAssertEqual(category?.createdAt, 1_000)
    }

    func testEditingKeepsIdentityAndCreationDate() {
        let existing = ArzikinaDomain.Category(id: "c", name: "Nourriture", icon: .food, colorArgb: 0xFFF5_9E0B, type: .expense, createdAt: 7)
        var draft = CategoryDraft(editing: existing, displayName: "Food")
        XCTAssertEqual(draft.name, "Food", "Le formulaire montre le nom affiché")
        draft.colorArgb = 0xFFEF_4444
        let saved = try? build(draft, existing: existing, canonical: { name, type in
            SystemCategoryKey.canonicalName(for: name, type: type) { $0 == .food ? ["Food", "Nourriture"] : [] }
        }).get()
        XCTAssertEqual(saved?.id, "c")
        XCTAssertEqual(saved?.createdAt, 7)
        XCTAssertEqual(saved?.name, "Nourriture", "Nom par défaut non modifié : nom de référence conservé")
        XCTAssertEqual(saved?.colorArgb, 0xFFEF_4444)
    }

    func testCanonicalNameDependsOnType() {
        let labels: (SystemCategoryKey) -> [String] = { key in
            switch key {
            case .salary: return ["Salary"]
            case .otherIncome, .otherExpense: return ["Other"]
            default: return []
            }
        }
        XCTAssertEqual(SystemCategoryKey.canonicalName(for: "Salary", type: .income, labelsOf: labels), "Salaire")
        XCTAssertEqual(SystemCategoryKey.canonicalName(for: "Salary", type: .expense, labelsOf: labels), "Salary", "Pas de « Salaire » en dépense")
        XCTAssertEqual(SystemCategoryKey.canonicalName(for: "Other", type: .expense, labelsOf: labels), "Divers")
        XCTAssertEqual(SystemCategoryKey.canonicalName(for: "Tontine", type: .expense, labelsOf: labels), "Tontine")
    }

    func testLoanAndFeeCategoriesAreManagedByTheApp() {
        let fees = ArzikinaDomain.Category(id: "f", name: "Frais et commissions", type: .expense)
        let loan = ArzikinaDomain.Category(id: "l", name: "Prêt accordé", type: .expense)
        let renamedFees = ArzikinaDomain.Category(id: "r", name: "Frais", type: .expense)
        let food = ArzikinaDomain.Category(id: "n", name: "Nourriture", type: .expense)
        XCTAssertFalse(CategoryForm.isEditable(fees))
        XCTAssertFalse(CategoryForm.isEditable(loan))
        XCTAssertTrue(CategoryForm.isEditable(renamedFees))
        XCTAssertTrue(CategoryForm.isEditable(food))
        XCTAssertFalse(TransactionForm.isSelectable(fees))
        XCTAssertTrue(TransactionForm.isSelectable(food))
    }
}
