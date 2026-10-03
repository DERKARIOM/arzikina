import XCTest
@testable import ArzikinaDomain

/// Modèles de transactions — Android `MarketplaceViewModel`, `TemplateFromTransaction`.
final class TemplatesTests: XCTestCase {

    private let calendar = ArzikinaCalendar.make(timeZone: TimeZone(identifier: "Africa/Niamey")!)
    private let food = ArzikinaDomain.Category(id: "food", name: "Nourriture", type: .expense)
    private let salary = ArzikinaDomain.Category(id: "salary", name: "Salaire", type: .income)
    private let unused = ArzikinaDomain.Category(id: "unused", name: "Eau", type: .expense)
    private let cash = Account(id: "cash", name: "Espèces")

    private func template(_ id: String, _ name: String, category: EntityID = "food", account: EntityID = "cash", favorite: Bool = false, description: String = "") -> TransactionTemplate {
        TransactionTemplate(id: id, name: name, type: .expense, amount: 1_500, categoryId: category, accountId: account, description: description, isFavorite: favorite)
    }

    private var templates: [TransactionTemplate] {
        [
            template("f", "Déjeuner", favorite: true),
            template("a", "Café", description: "Kiosque du bureau"),
            template("s", "Salaire", category: "salary"),
            template("x", "Orphelin", account: "deleted")
        ]
    }

    func testFavoritesFirstWithoutFilters() {
        let library = TemplateLibrary.make(templates: templates, categories: [food, salary, unused], accounts: [cash], filters: TemplateFilters())
        XCTAssertEqual(library.favorites.map(\.id), ["f"])
        XCTAssertEqual(library.others.map(\.id), ["a", "s"], "Compte disparu : modèle non affiché")
        XCTAssertEqual(library.totalCount, 4)
        XCTAssertEqual(library.categoryOptions.map(\.id), ["food", "salary"], "Seulement les catégories utilisées, par nom")
        XCTAssertEqual(library.others.first?.account.id, "cash")
    }

    func testSearchAndCategoryFilterDropTheSections() {
        var filters = TemplateFilters(query: "  kiosque ")
        var library = TemplateLibrary.make(templates: templates, categories: [food, salary], accounts: [cash], filters: filters)
        XCTAssertTrue(library.favorites.isEmpty)
        XCTAssertEqual(library.others.map(\.id), ["a"], "Recherche dans la description, sans casse")
        filters = TemplateFilters(categoryId: "food")
        library = TemplateLibrary.make(templates: templates, categories: [food, salary], accounts: [cash], filters: filters)
        XCTAssertEqual(library.others.map(\.id), ["f", "a"])
        XCTAssertTrue(filters.hasActiveFilters)
        XCTAssertFalse(TemplateFilters(query: "x").hasActiveFilters)
        filters = TemplateFilters(query: "introuvable")
        library = TemplateLibrary.make(templates: templates, categories: [food], accounts: [cash], filters: filters)
        XCTAssertTrue(library.isEmpty)
        XCTAssertEqual(library.totalCount, 4, "« Aucun résultat », pas « aucun modèle »")
    }

    func testUsingATemplatePrefillsTodayAtItsDefaultTime() {
        let now = CalendarDay(year: 2026, month: 10, day: 2).millis(hour: 15, minute: 42, calendar: calendar)
        var lunch = template("f", "Déjeuner", description: "Maquis")
        lunch.type = .income
        lunch.defaultHour = 12
        lunch.defaultMinute = 30
        let draft = TransactionDraft(template: lunch, now: now, calendar: calendar)
        XCTAssertEqual(draft.type, .income)
        XCTAssertEqual(draft.amountInput, Money.formatForInput(1_500))
        XCTAssertEqual(draft.accountId, "cash")
        XCTAssertEqual(draft.categoryId, "food")
        XCTAssertEqual(draft.description, "Maquis")
        XCTAssertEqual(draft.date, CalendarDay(year: 2026, month: 10, day: 2).millis(hour: 12, minute: 30, calendar: calendar), "Aujourd'hui à l'heure du modèle")
        XCTAssertFalse(draft.hasFee)

        lunch.defaultHour = nil
        XCTAssertEqual(TransactionDraft(template: lunch, now: now, calendar: calendar).date, now, "Sans heure par défaut : maintenant")
    }

    func testDuplicateName() {
        XCTAssertEqual(TemplateNaming.duplicateName(of: "Déjeuner", suffix: "(copie)"), "Déjeuner (copie)")
    }

    // MARK: - Choisir un modèle dans le formulaire de transaction

    func testApplyingATemplateKeepsTheAccountAndTheDay() {
        let day = CalendarDay(year: 2026, month: 9, day: 28)
        var draft = TransactionDraft(now: day.millis(hour: 18, minute: 5, calendar: calendar), presetAccountId: "bank")
        draft.paymentMethod = .mobileMoney
        var lunch = template("f", "Déjeuner", description: "Maquis")
        lunch.defaultHour = 12
        lunch.defaultMinute = 30
        draft.apply(lunch, calendar: calendar)
        XCTAssertEqual(draft.accountId, "bank", "Compte déjà choisi conservé")
        XCTAssertEqual(draft.categoryId, "food")
        XCTAssertEqual(draft.description, "Maquis")
        XCTAssertEqual(draft.amountInput, Money.formatForInput(1_500))
        XCTAssertEqual(draft.date, day.millis(hour: 12, minute: 30, calendar: calendar), "Même jour, heure du modèle")
        XCTAssertEqual(draft.paymentMethod, .mobileMoney)
    }

    // MARK: - Formulaire de modèle

    func testTemplateFormValidationAndBuild() throws {
        var draft = TemplateDraft(accountId: nil)
        func error() -> TemplateFormError? {
            if case .failure(let e) = TemplateForm.build(draft, existing: nil, sourceTransactionId: "t", newId: "n", now: 9) { return e }
            return nil
        }
        XCTAssertEqual(error(), .nameRequired)
        draft.name = "  Déjeuner "
        XCTAssertEqual(error(), .invalidAmount)
        draft.details.amountInput = "15"
        XCTAssertEqual(error(), .categoryRequired)
        draft.details.categoryId = "food"
        XCTAssertEqual(error(), .accountRequired)
        draft.details.accountId = "cash"
        draft.hasDefaultTime = true
        draft.defaultHour = 12
        draft.defaultMinute = 30
        let created = try TemplateForm.build(draft, existing: nil, sourceTransactionId: "t", newId: "n", now: 9).get()
        XCTAssertEqual(created.name, "Déjeuner")
        XCTAssertEqual(created.defaultHour, 12)
        XCTAssertEqual(created.sourceTransactionId, "t")
        XCTAssertFalse(created.isFavorite)
        XCTAssertEqual(created.createdAt, 9)

        var existing = created
        existing.isFavorite = true
        var edit = TemplateDraft(editing: existing)
        XCTAssertTrue(edit.hasDefaultTime)
        edit.hasDefaultTime = false
        let updated = try TemplateForm.build(edit, existing: existing, sourceTransactionId: "autre", newId: "x", now: 20).get()
        XCTAssertEqual(updated.id, "n")
        XCTAssertTrue(updated.isFavorite, "Le favori n'est jamais retiré par une modification")
        XCTAssertNil(updated.defaultHour)
        XCTAssertNil(updated.defaultMinute)
        XCTAssertEqual(updated.sourceTransactionId, "t", "Lien figé à la création")
        XCTAssertEqual(edit.defaultHour, 12, "Heure conservée si on réactive")
    }

    func testTemplateFromTransaction() {
        var transaction = Transaction(id: "t", amount: 2_500, type: .income, accountId: "cash", categoryId: "salary", date: 0, description: "", paymentMethod: .cash)
        var draft = TemplateDraft(from: transaction, categoryName: "Salaire")
        XCTAssertEqual(draft.name, "Salaire", "Sans description : nom de la catégorie")
        XCTAssertEqual(draft.details.type, .income)
        XCTAssertEqual(draft.details.categoryId, "salary")
        XCTAssertEqual(draft.details.accountId, "cash")
        XCTAssertFalse(draft.hasDefaultTime, "Jamais la date ni l'heure")
        transaction.description = "  " + String(repeating: "a", count: 80)
        draft = TemplateDraft(from: transaction, categoryName: "Salaire")
        XCTAssertEqual(draft.name.count, TemplateDraft.maxNameLength)
        XCTAssertEqual(draft.details.description.count, 80, "Description complète conservée")

        XCTAssertTrue(TemplateForm.isEligible(transaction))
        transaction.type = .transfer
        XCTAssertFalse(TemplateForm.isEligible(transaction))
        transaction.type = .expense
        transaction.feeType = .transfer
        XCTAssertFalse(TemplateForm.isEligible(transaction), "Jamais une ligne de frais")
    }
}
