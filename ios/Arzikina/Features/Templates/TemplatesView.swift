import ArzikinaDomain
import SwiftUI

/// « Modèles de transactions » (Android « Marketplace personnelle », Réglages › Transactions) :
/// raccourcis vers des dépenses et revenus habituels. « Utiliser » ouvre le formulaire de
/// transaction pré-rempli, daté d'aujourd'hui ; le modèle n'est jamais modifié.
///
/// Favoris en tête, recherche (nom, description), filtre par catégorie ; favori, dupliquer et
/// supprimer par balayage ou menu contextuel.
struct TemplatesView: View {

    @Environment(SessionModel.self) private var session
    @State private var model = TemplatesViewModel()
    @State private var transactionForm: TransactionFormRoute?
    @State private var templateForm: TemplateFormRoute?
    @State private var pendingDeletion: TemplateItem?

    var body: some View {
        screen
            .confirmationDialog(
                "templates.delete.title",
                isPresented: Binding(get: { pendingDeletion != nil }, set: { if !$0 { pendingDeletion = nil } }),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { item in
                Button("templates.delete", role: .destructive) { run { await model.delete(item, using: $0) } }
            } message: { item in
                Text("templates.delete.message \(item.template.name)")
            }
            .alert("templates.action_failed", isPresented: Binding(get: { model.actionFailed }, set: { if !$0 { model.dismissFailure() } })) {
                Button("common.ok", role: .cancel) {}
            }
            .task(id: spaceId) {
                guard let space = session.dataSpace else { return }
                await model.observeTemplates(space.templates)
            }
            .task(id: spaceId) {
                guard let space = session.dataSpace else { return }
                await model.observeCategories(space.categories)
            }
            .task(id: spaceId) {
                guard let space = session.dataSpace else { return }
                await model.observeAccounts(space.accounts)
            }
    }

    /// Contenu, titre, recherche, filtre et formulaire de transaction (séparé de `body` pour des
    /// expressions courtes à vérifier pour le compilateur).
    private var screen: some View {
        @Bindable var model = model
        return content
            .navigationTitle("templates.title")
            .searchable(text: $model.filters.query, prompt: Text("templates.search_placeholder"))
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    categoryFilter
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        templateForm = .create
                    } label: {
                        Label("templates.add", systemImage: "plus")
                    }
                }
            }
            .transactionFormSheet($transactionForm, session: session)
            .templateFormSheet($templateForm, session: session)
    }

    private var spaceId: ObjectIdentifier? {
        session.dataSpace.map(ObjectIdentifier.init)
    }

    // MARK: - Contenu

    @ViewBuilder
    private var content: some View {
        if !model.hasLoaded {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
        } else if model.library.totalCount == 0 {
            ContentUnavailableView {
                Label("templates.empty.title", systemImage: "square.stack.3d.up")
            } description: {
                Text("templates.empty.message")
            } actions: {
                Button("templates.add") { templateForm = .create }
                    .buttonStyle(.borderedProminent)
            }
        } else if model.library.isEmpty {
            ContentUnavailableView {
                Label("templates.no_results.title", systemImage: "magnifyingglass")
            } description: {
                Text("templates.no_results.message")
            } actions: {
                if model.filters.hasActiveFilters {
                    Button("transactions.filters.reset") { model.filters.categoryId = nil }
                }
            }
        } else {
            List {
                if !model.library.favorites.isEmpty {
                    Section {
                        rows(model.library.favorites)
                    } header: {
                        Label("templates.section.favorites", systemImage: "star.fill")
                    }
                }
                if !model.library.others.isEmpty {
                    Section {
                        rows(model.library.others)
                    } header: {
                        if !model.library.favorites.isEmpty { Text("templates.section.all") }
                    } footer: {
                        Text("templates.footer")
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    private func rows(_ items: [TemplateItem]) -> some View {
        ForEach(items) { item in
            TemplateRow(item: item, onEdit: { templateForm = .edit(item.template) }) { transactionForm = .template(item.template) }
                .swipeActions(edge: .leading) {
                    Button {
                        run { await model.toggleFavorite(item, using: $0) }
                    } label: {
                        favoriteLabel(item)
                    }
                    .tint(.yellow)
                }
                // Pas de `role: .destructive` : SwiftUI retirerait la ligne AVANT la confirmation.
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button {
                        pendingDeletion = item
                    } label: {
                        Label("templates.delete", systemImage: "trash")
                    }
                    .tint(.red)
                }
                .contextMenu { menu(item) }
        }
    }

    @ViewBuilder
    private func menu(_ item: TemplateItem) -> some View {
        Button {
            transactionForm = .template(item.template)
        } label: {
            Label("templates.use", systemImage: "plus.circle")
        }
        Button {
            templateForm = .edit(item.template)
        } label: {
            Label("templates.edit", systemImage: "pencil")
        }
        Button {
            run { await model.toggleFavorite(item, using: $0) }
        } label: {
            favoriteLabel(item)
        }
        Button {
            run { await model.duplicate(item, using: $0) }
        } label: {
            Label("templates.duplicate", systemImage: "plus.square.on.square")
        }
        Button(role: .destructive) {
            pendingDeletion = item
        } label: {
            Label("templates.delete", systemImage: "trash")
        }
    }

    private func favoriteLabel(_ item: TemplateItem) -> some View {
        item.template.isFavorite
            ? Label("templates.favorite.remove", systemImage: "star.slash")
            : Label("templates.favorite.add", systemImage: "star")
    }

    /// Filtre par catégorie : seulement les catégories utilisées par au moins un modèle.
    private var categoryFilter: some View {
        @Bindable var model = model
        return Menu {
            Picker("templates.filter.category", selection: $model.filters.categoryId) {
                Text("templates.filter.all").tag(EntityID?.none)
                ForEach(model.library.categoryOptions) { category in
                    Label {
                        Text(verbatim: category.displayName)
                    } icon: {
                        Image(systemName: category.systemImage)
                    }
                    .tag(EntityID?.some(category.id))
                }
            }
        } label: {
            Label("templates.filter.category", systemImage: model.filters.hasActiveFilters
                  ? "line.3.horizontal.decrease.circle.fill"
                  : "line.3.horizontal.decrease.circle")
        }
        .disabled(model.library.categoryOptions.isEmpty)
    }

    // MARK: - Actions

    /// Action sur le dépôt de modèles, suivie d'une synchronisation.
    private func run(_ action: @escaping (TransactionTemplateRepository) async -> Void) {
        guard let space = session.dataSpace else { return }
        Task {
            await action(space.templates)
            session.sync?.requestSync(.localChange)
        }
    }
}

/// Ligne d'un modèle : catégorie (icône sur sa couleur), nom, catégorie · compte (et heure par
/// défaut), montant, et le bouton « Utiliser ». Toucher le reste de la ligne ouvre le modèle.
struct TemplateRow: View {

    let item: TemplateItem
    /// Toucher la ligne (hors bouton « Utiliser ») ouvre le modèle.
    let onEdit: () -> Void
    let onUse: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: item.category.systemImage)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 38, height: 38)
                .background(Color(argb: item.category.colorArgb), in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(verbatim: item.template.name)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    if item.template.isFavorite {
                        Image(systemName: "star.fill")
                            .font(.caption2)
                            .foregroundStyle(.yellow)
                            .accessibilityLabel(Text("templates.favorite.badge"))
                    }
                }
                Text(verbatim: subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Text(verbatim: Money.format(CurrencyAmount(currencyCode: item.account.currencyCode, amountMinor: item.template.amount)))
                    .font(.subheadline.weight(.semibold).monospacedDigit())
                    .foregroundStyle(item.template.type == .income ? Brand.income : Brand.expense)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture(perform: onEdit)
            Button("templates.use", action: onUse)
                .buttonStyle(.bordered)
                .controlSize(.small)
                .tint(Brand.primary)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        .accessibilityAction(named: Text("templates.use"), onUse)
        .accessibilityAction(named: Text("templates.edit"), onEdit)
    }

    private var subtitle: String {
        var parts = [item.category.displayName, item.account.displayName]
        if let hour = item.template.defaultHour, let minute = item.template.defaultMinute {
            parts.append(String(format: "%02d:%02d", hour, minute))
        }
        return parts.joined(separator: " · ")
    }
}

#Preview {
    NavigationStack { TemplatesView() }
        .environment(SessionModel.preview())
}
