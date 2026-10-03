import ArzikinaDomain
import SwiftUI

/// Sections communes au formulaire d'automatisation, à « Modifier puis valider » et au formulaire
/// de modèle : type, montant, compte, catégorie, puis [schedule] (planification, date, heure par
/// défaut…), puis description et moyen de paiement (absent d'un modèle, comme sur Android).
struct AutomationDetailsSections<Schedule: View>: View {

    @Binding var details: AutomationDetailsDraft
    let choices: AutomationChoices
    let error: AutomationFormError?
    var showsPaymentMethod = true
    @ViewBuilder let schedule: () -> Schedule

    @FocusState private var focusedField: Field?

    private enum Field { case amount, description }

    var body: some View {
        Group {
            typeSection
            amountSection
            accountSection
            categorySection
            schedule()
            detailsSection
        }
    }

    private var typeSection: some View {
        Section {
            Picker("transaction.form.type", selection: Binding(get: { details.type }, set: { details.changeType($0) })) {
                Text("transaction.type.expense").tag(TransactionType.expense)
                Text("transaction.type.income").tag(TransactionType.income)
            }
            .pickerStyle(.segmented)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
        }
    }

    private var amountSection: some View {
        Section {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                TextField("transaction.form.amount", text: $details.amountInput)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .keyboardType(.decimalPad)
                    .focused($focusedField, equals: .amount)
                    .foregroundStyle(details.type == .income ? Brand.income : Brand.expense)
                Text(verbatim: Money.symbol(of: choices.currencyCode(of: details.accountId)))
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .combine)
        } header: {
            Text("transaction.form.amount")
        } footer: {
            if error == .invalidAmount { FormErrorText(key: "transaction.form.error.amount") }
        }
    }

    private var accountSection: some View {
        Section {
            AccountPicker(
                titleKey: "transaction.form.account",
                selection: details.accountId,
                accounts: choices.accounts,
                onSelect: { details.accountId = $0 }
            )
        } footer: {
            if error == .accountRequired { FormErrorText(key: "transaction.form.error.account") }
        }
    }

    private var categorySection: some View {
        Section {
            let categories = choices.categories(for: details.type)
            if categories.isEmpty {
                Text("transaction.form.no_category")
                    .foregroundStyle(.secondary)
            } else {
                CategoryGrid(categories: categories, selection: details.categoryId) { id in
                    focusedField = nil
                    details.categoryId = id
                }
            }
        } header: {
            Text("transaction.form.category")
        } footer: {
            if error == .categoryRequired { FormErrorText(key: "transaction.form.error.category") }
        }
    }

    private var detailsSection: some View {
        Section {
            TextField("transaction.form.description", text: $details.description, axis: .vertical)
                .lineLimit(1...3)
                .focused($focusedField, equals: .description)
            if showsPaymentMethod {
                Picker("transaction.form.payment_method", selection: $details.paymentMethod) {
                    Text("transaction.form.payment_method.none").tag(PaymentMethod?.none)
                    ForEach(PaymentMethod.allCases, id: \.self) { method in
                        Text(verbatim: method.displayName).tag(PaymentMethod?.some(method))
                    }
                }
            }
        }
    }
}
