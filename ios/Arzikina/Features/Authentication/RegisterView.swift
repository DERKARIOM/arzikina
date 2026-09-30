import ArzikinaDomain
import SwiftUI

/// Écran d'inscription — formulaire iOS natif (`Form`), erreurs affichées sous chaque champ.
struct RegisterView: View {

    @State private var model: RegisterViewModel
    @FocusState private var focusedField: AuthField?

    init(model: RegisterViewModel) {
        _model = State(initialValue: model)
    }

    var body: some View {
        Form {
            Section {
                AuthHeaderView(titleKey: "register.title", subtitleKey: "register.subtitle")
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }

            Section("register.section.identity") {
                field(.fullName) {
                    TextField("register.full_name", text: $model.form.fullName)
                        .textContentType(.name)
                        .textInputAutocapitalization(.words)
                }
                field(.username) {
                    TextField("register.username", text: $model.form.username)
                        .textContentType(.username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                field(.email) {
                    TextField("register.email", text: $model.form.email)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                TextField("register.phone", text: $model.form.phoneNumber)
                    .textContentType(.telephoneNumber)
                    .keyboardType(.phonePad)
            }

            Section {
                field(.password) {
                    SecureField("register.password", text: $model.form.password)
                        .textContentType(.newPassword)
                }
                field(.passwordConfirmation) {
                    SecureField("register.password_confirmation", text: $model.form.passwordConfirmation)
                        .textContentType(.newPassword)
                }
            } header: {
                Text("register.section.password")
            } footer: {
                Text("register.password_hint")
            }

            Section {
                Picker("register.security_question", selection: $model.form.securityQuestion) {
                    ForEach(SecurityQuestion.allCases, id: \.self) { question in
                        Text(question.titleKey).tag(question)
                    }
                }
                .pickerStyle(.navigationLink)
                field(.securityAnswer) {
                    TextField("register.security_answer", text: $model.form.securityAnswer)
                        .autocorrectionDisabled()
                }
            } header: {
                Text("register.section.security")
            } footer: {
                Text("register.security_hint")
            }

            Section {
                if let error = model.generalError {
                    AuthErrorText(message: AuthMessages.text(for: error))
                }
                Button {
                    focusedField = nil
                    Task { await model.submit() }
                } label: {
                    HStack {
                        Spacer()
                        if model.isSubmitting {
                            ProgressView()
                        } else {
                            Text("register.submit").fontWeight(.semibold)
                        }
                        Spacer()
                    }
                }
                .disabled(model.isSubmitting)
            }
        }
        .navigationTitle("register.navigation_title")
        .navigationBarTitleDisplayMode(.inline)
        .scrollDismissesKeyboard(.interactively)
    }

    /// Champ du formulaire avec son éventuel message d'erreur juste en dessous.
    @ViewBuilder
    private func field<Content: View>(_ field: AuthField, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            content()
                .focused($focusedField, equals: field)
            if let issue = model.issue(for: field) {
                AuthErrorText(message: AuthMessages.text(for: issue))
            }
        }
    }
}
