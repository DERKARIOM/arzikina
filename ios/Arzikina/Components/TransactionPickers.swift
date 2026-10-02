import ArzikinaDomain
import SwiftUI

// Choix d'un compte et d'une catégorie, partagés par les formulaires de transaction et
// d'automatisation (mêmes contrôles, même apparence).

/// Choix d'un compte (nom affiché et devise).
struct AccountPicker: View {
    let titleKey: LocalizedStringKey
    let selection: EntityID?
    let accounts: [Account]
    let onSelect: @MainActor (EntityID?) -> Void

    var body: some View {
        Picker(titleKey, selection: Binding(get: { selection }, set: { onSelect($0) })) {
            if selection == nil {
                Text("transaction.form.account.choose").tag(EntityID?.none)
            }
            ForEach(accounts) { account in
                Label {
                    Text(verbatim: "\(account.displayName) (\(Money.symbol(of: account.currencyCode)))")
                } icon: {
                    Image(systemName: account.icon.systemImage)
                }
                .tag(EntityID?.some(account.id))
            }
        }
    }
}

/// Catégories sous forme de pastilles (icône sur sa couleur + nom), touchables d'un coup d'œil.
struct CategoryGrid: View {
    let categories: [ArzikinaDomain.Category]
    let selection: EntityID?
    let onSelect: @MainActor (EntityID?) -> Void

    private let columns = [GridItem(.adaptive(minimum: 76), spacing: 10)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(categories) { category in
                let isSelected = category.id == selection
                Button {
                    onSelect(category.id)
                } label: {
                    VStack(spacing: 6) {
                        Image(systemName: category.systemImage)
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(isSelected ? .white : Color(argb: category.colorArgb))
                            .frame(width: 44, height: 44)
                            .background(isSelected ? Color(argb: category.colorArgb) : Color(.tertiarySystemFill), in: Circle())
                        Text(verbatim: category.displayName)
                            .font(.caption2)
                            .lineLimit(2)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(isSelected ? .primary : .secondary)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: category.displayName))
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.vertical, 6)
    }
}
