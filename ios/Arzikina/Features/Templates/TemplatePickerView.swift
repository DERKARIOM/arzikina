import ArzikinaDomain
import SwiftUI

/// « Utiliser un modèle » depuis une nouvelle transaction (Android `TemplatePickerDialog`) :
/// favoris en tête, recherche ; toucher un modèle remplit le formulaire et ferme la feuille.
struct TemplatePickerView: View {

    let templates: [TransactionTemplate]
    let categories: [ArzikinaDomain.Category]
    let accounts: [Account]
    let onSelect: (TransactionTemplate) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private var library: TemplateLibrary {
        TemplateLibrary.make(
            templates: templates,
            categories: categories,
            accounts: accounts,
            filters: TemplateFilters(query: query),
            categoryName: { $0.displayName }
        )
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("templates.picker.title")
                .navigationBarTitleDisplayMode(.inline)
                .searchable(text: $query, prompt: Text("templates.search_placeholder"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("common.cancel") { dismiss() }
                    }
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        let library = self.library
        if library.isEmpty {
            ContentUnavailableView {
                Label(library.totalCount == 0 ? LocalizedStringKey("templates.empty.title") : LocalizedStringKey("templates.no_results.title"), systemImage: "square.stack.3d.up")
            } description: {
                Text("templates.picker.empty")
            }
        } else {
            List {
                if !library.favorites.isEmpty {
                    Section {
                        rows(library.favorites)
                    } header: {
                        Label("templates.section.favorites", systemImage: "star.fill")
                    }
                }
                Section {
                    rows(library.others)
                } footer: {
                    Text("templates.picker.footer")
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    private func rows(_ items: [TemplateItem]) -> some View {
        ForEach(items) { item in
            Button {
                onSelect(item.template)
                dismiss()
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: item.category.systemImage)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 34, height: 34)
                        .background(Color(argb: item.category.colorArgb), in: Circle())
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: item.template.name)
                            .font(.subheadline.weight(.semibold))
                        Text(verbatim: item.category.displayName)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 8)
                    Text(verbatim: Money.format(CurrencyAmount(currencyCode: item.account.currencyCode, amountMinor: item.template.amount)))
                        .font(.subheadline.weight(.semibold).monospacedDigit())
                        .foregroundStyle(item.template.type == .income ? Brand.income : Brand.expense)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }
}
