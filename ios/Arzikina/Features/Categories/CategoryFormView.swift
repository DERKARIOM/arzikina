import ArzikinaDomain
import SwiftUI

/// Formulaire de catégorie (feuille modale) : nom, type (dépense ou revenu), icône et couleur,
/// avec un aperçu en direct de la pastille telle qu'elle apparaîtra dans les listes.
///
/// Une catégorie gérée par l'app (prêts, frais) s'ouvre en lecture seule.
struct CategoryFormView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(SessionModel.self) private var session
    @State private var model: CategoryFormViewModel
    @State private var isConfirmingDelete = false
    @FocusState private var isNameFocused: Bool

    /// Appelé avec la catégorie enregistrée (ex. pour la sélectionner dans une transaction).
    private let onSaved: @MainActor (ArzikinaDomain.Category) -> Void

    init(
        mode: CategoryFormViewModel.Mode,
        repository: CategoryRepository,
        onSaved: @escaping @MainActor (ArzikinaDomain.Category) -> Void = { _ in }
    ) {
        _model = State(initialValue: CategoryFormViewModel(mode: mode, repository: repository))
        self.onSaved = onSaved
    }

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Form {
                if !model.isEditable {
                    Section {
                        Label("category.form.managed", systemImage: "lock.fill")
                            .foregroundStyle(.secondary)
                    }
                }
                Group {
                    Section {
                        preview
                            .listRowBackground(Color.clear)
                    }
                    Section {
                        TextField("category.form.name", text: $model.draft.name)
                            .focused($isNameFocused)
                            .textInputAutocapitalization(.sentences)
                            .submitLabel(.done)
                        Picker("category.form.type", selection: $model.draft.type) {
                            Text("category.type.expense").tag(TransactionType.expense)
                            Text("category.type.income").tag(TransactionType.income)
                        }
                        .pickerStyle(.segmented)
                    } footer: {
                        if model.error == .nameRequired { FormErrorText(key: "category.form.error.name_required") }
                    }
                    Section("category.form.icon") {
                        IconGrid(
                            selection: $model.draft.icon,
                            icons: CategoryIcon.allCases,
                            colorArgb: model.draft.colorArgb,
                            systemImage: \.systemImage,
                            accessibilityName: \.displayName
                        )
                    }
                    Section("category.form.color") {
                        ColorGrid(selection: $model.draft.colorArgb, choices: model.colorChoices)
                    }
                }
                .disabled(!model.isEditable)

                if model.saveFailed {
                    Section { FormErrorText(key: "category.form.save_failed") }
                }
                if model.isEditing && model.isEditable {
                    deleteSection
                }
            }
            .navigationTitle(model.isEditing ? LocalizedStringKey("category.form.title.edit") : LocalizedStringKey("category.form.title.add"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { dismiss() }
                }
                if model.isEditable {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("common.save") { submit() }
                            .disabled(model.isWorking)
                    }
                }
            }
            .interactiveDismissDisabled(model.isWorking)
            .confirmationDialog("categories.delete.title", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                Button("categories.delete", role: .destructive) { deleteCategory() }
            } message: {
                Text("categories.delete.message \(model.originalName)")
            }
            .onAppear {
                if !model.isEditing { isNameFocused = true }
            }
        }
    }

    /// Pastille et nom tels qu'ils apparaîtront dans les listes.
    private var preview: some View {
        VStack(spacing: 8) {
            Image(systemName: model.draft.icon.systemImage)
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 64, height: 64)
                .background(Color(argb: model.draft.colorArgb), in: Circle())
            Text(verbatim: previewName)
                .font(.headline)
                .lineLimit(1)
                .foregroundStyle(model.draft.name.isEmpty ? .secondary : .primary)
        }
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }

    private var previewName: String {
        let name = model.draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty ? DomainDisplay.localized("category.form.name") : name
    }

    private var deleteSection: some View {
        Section {
            Button(role: .destructive) {
                isNameFocused = false
                isConfirmingDelete = true
            } label: {
                Text("category.form.delete")
                    .frame(maxWidth: .infinity)
            }
            .disabled(model.isWorking)
        } footer: {
            switch model.deletionProblem {
            case .inUse: FormErrorText(key: "categories.delete.in_use")
            case .failed: FormErrorText(key: "categories.delete.failed")
            case nil: EmptyView()
            }
        }
    }

    private func submit() {
        isNameFocused = false
        Task {
            if let saved = await model.save() {
                session.sync?.requestSync(.localChange)
                onSaved(saved)
                dismiss()
            }
        }
    }

    private func deleteCategory() {
        Task {
            if await model.delete() {
                session.sync?.requestSync(.localChange)
                dismiss()
            }
        }
    }
}
