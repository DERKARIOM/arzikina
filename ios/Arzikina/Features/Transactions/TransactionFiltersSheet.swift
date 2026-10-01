import ArzikinaDomain
import SwiftUI

/// Filtres de la liste des transactions (type, période, compte, catégorie) — Android
/// « Filtres ». Appliqués IMMÉDIATEMENT (la liste derrière la feuille se met à jour en direct) :
/// pas de bouton « Appliquer » à oublier.
struct TransactionFiltersSheet: View {

    @Binding var filters: TransactionFilters
    let accounts: [Account]
    let categories: [ArzikinaDomain.Category]

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("transactions.filters.kind") {
                    Picker("transactions.filters.kind", selection: $filters.kind) {
                        Text("transactions.filters.kind.all").tag(TransactionFilters.Kind.all)
                        Text("transactions.filters.kind.income").tag(TransactionFilters.Kind.income)
                        Text("transactions.filters.kind.expense").tag(TransactionFilters.Kind.expense)
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                }

                Section("transactions.filters.period") {
                    Picker("transactions.filters.period", selection: $filters.period) {
                        Text("transactions.period.all").tag(TransactionFilters.Period.all)
                        Text("transactions.period.this_week").tag(TransactionFilters.Period.thisWeek)
                        Text("transactions.period.this_month").tag(TransactionFilters.Period.thisMonth)
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                }

                Section {
                    Picker("transactions.filters.account", selection: $filters.accountId) {
                        Text("transactions.filters.all_accounts").tag(EntityID?.none)
                        ForEach(accounts) { account in
                            Text(verbatim: account.displayName).tag(EntityID?.some(account.id))
                        }
                    }
                    Picker("transactions.filters.category", selection: $filters.categoryId) {
                        Text("transactions.filters.all_categories").tag(EntityID?.none)
                        ForEach(categories) { category in
                            Text(verbatim: category.displayName).tag(EntityID?.some(category.id))
                        }
                    }
                }
            }
            .navigationTitle("transactions.filters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("transactions.filters.reset") { filters.resetFilters() }
                        .disabled(!filters.hasActiveFilters)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.done") { dismiss() }
                }
            }
        }
    }
}

#Preview {
    TransactionFiltersSheet(
        filters: .constant(TransactionFilters(period: .thisMonth)),
        accounts: [Account(id: "a", name: "Espèces")],
        categories: [ArzikinaDomain.Category(id: "c", name: "Nourriture", type: .expense)]
    )
}
