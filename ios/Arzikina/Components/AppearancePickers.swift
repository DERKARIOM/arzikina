import SwiftUI

/// Choix d'une icône (comptes, catégories) : chaque icône sur la couleur choisie, la sélection
/// en plein.
struct IconGrid<Icon: Hashable>: View {
    @Binding var selection: Icon
    let icons: [Icon]
    let colorArgb: Int64
    let systemImage: (Icon) -> String
    let accessibilityName: (Icon) -> String

    private let columns = [GridItem(.adaptive(minimum: 44), spacing: 12)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(icons, id: \.self) { icon in
                let isSelected = icon == selection
                Button {
                    selection = icon
                } label: {
                    Image(systemName: systemImage(icon))
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(isSelected ? .white : Color(argb: colorArgb))
                        .frame(width: 44, height: 44)
                        .background(isSelected ? Color(argb: colorArgb) : Color(.tertiarySystemFill), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: accessibilityName(icon)))
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.vertical, 6)
    }
}

/// Choix d'une couleur de la palette partagée (`ColorPalette`).
struct ColorGrid: View {
    @Binding var selection: Int64
    let choices: [Int64]

    private let columns = [GridItem(.adaptive(minimum: 36), spacing: 12)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(choices, id: \.self) { argb in
                let isSelected = argb == selection
                Button {
                    selection = argb
                } label: {
                    Circle()
                        .fill(Color(argb: argb))
                        .frame(width: 32, height: 32)
                        .overlay {
                            if isSelected {
                                Image(systemName: "checkmark")
                                    .font(.footnote.weight(.bold))
                                    .foregroundStyle(.white)
                            }
                        }
                        .padding(2)
                        .overlay(Circle().stroke(isSelected ? Color(argb: argb) : .clear, lineWidth: 2))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("common.color"))
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.vertical, 6)
    }
}
