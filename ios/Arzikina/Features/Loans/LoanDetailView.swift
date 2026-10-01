import ArzikinaDomain
import SwiftUI

/// Détail d'un prêt / emprunt : montant, reste, progression et statut, informations, puis les
/// remboursements (enregistrement, suppression par balayage). Modification et suppression
/// confirmée : le prêt, ses remboursements et toutes leurs transactions disparaissent ensemble.
struct LoanDetailView: View {

    let loanId: EntityID

    @Environment(SessionModel.self) private var session
    @Environment(\.dismiss) private var dismiss
    @State private var detail: LoanDetail??
    @State private var isConfirmingDelete = false
    @State private var deleteFailed = false
    @State private var isEditing = false
    @State private var isRecordingPayment = false
    @State private var pendingPaymentDeletion: LoanPayment?

    var body: some View {
        content
            .navigationTitle("loans.detail.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if case .some(.some) = detail {
                    ToolbarItem(placement: .primaryAction) {
                        Button("loans.detail.edit") { isEditing = true }
                    }
                }
            }
            .sheet(isPresented: $isEditing) {
                if let space = session.dataSpace, case .some(.some(let detail)) = detail {
                    LoanFormView(mode: .edit(detail.summary), loans: space.loans, accounts: space.accounts)
                        .environment(session)
                }
            }
            .sheet(isPresented: $isRecordingPayment) {
                if let space = session.dataSpace, case .some(.some(let detail)) = detail {
                    LoanPaymentFormView(summary: detail.summary, loans: space.loans, accounts: space.accounts)
                        .environment(session)
                }
            }
            .confirmationDialog(
                "loans.payment.delete.title",
                isPresented: Binding(get: { pendingPaymentDeletion != nil }, set: { if !$0 { pendingPaymentDeletion = nil } }),
                titleVisibility: .visible,
                presenting: pendingPaymentDeletion
            ) { payment in
                Button("loans.delete", role: .destructive) { deletePayment(payment) }
            } message: { _ in
                Text("loans.payment.delete.message")
            }
            .confirmationDialog("loans.delete.title", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                Button("loans.delete", role: .destructive) { deleteLoan() }
            } message: {
                Text("loans.delete.message")
            }
            .alert("loans.delete.failed", isPresented: $deleteFailed) {
                Button("common.ok", role: .cancel) {}
            }
            .task(id: session.dataSpace.map(ObjectIdentifier.init)) {
                guard let space = session.dataSpace else { return }
                let now = EpochMillis(Date().timeIntervalSince1970 * 1000)
                for await value in space.loans.observeDetail(id: loanId, now: now, calendar: ArzikinaCalendar.current) {
                    detail = .some(value)
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch detail {
        case .none:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
        case .some(.none):
            ContentUnavailableView {
                Label("loans.detail.missing", systemImage: "questionmark.folder")
            }
        case .some(.some(let detail)):
            List {
                Section {
                    header(detail.summary)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                }
                Section("loans.detail.info") {
                    LabeledContent("loans.form.account") {
                        Text(verbatim: detail.account?.displayName ?? DomainDisplay.localized("transaction.unknown_account"))
                    }
                    LabeledContent("loans.form.start") { Text(date(detail.summary.loan.startDate), format: .dateTime.day().month().year().hour().minute()) }
                    LabeledContent("loans.form.due") { Text(date(detail.summary.loan.dueDate), format: .dateTime.day().month().year()) }
                    if let phone = detail.summary.person?.phone, !phone.isEmpty {
                        LabeledContent("loans.person.phone") { Text(verbatim: phone) }
                    }
                }
                Section {
                    if detail.summary.remaining > 0 {
                        Button {
                            isRecordingPayment = true
                        } label: {
                            Label("loans.payment.title", systemImage: "plus.circle.fill")
                        }
                    }
                    if detail.payments.isEmpty {
                        Text("loans.detail.no_payments")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(detail.payments) { payment in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(date(payment.date), format: .dateTime.day().month().year())
                                        .font(.subheadline)
                                    if !payment.note.isEmpty {
                                        Text(verbatim: payment.note)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                Text(verbatim: format(payment.amount, detail.summary))
                                    .font(.subheadline.weight(.semibold).monospacedDigit())
                            }
                            .accessibilityElement(children: .combine)
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button {
                                    pendingPaymentDeletion = payment
                                } label: {
                                    Label("loans.delete", systemImage: "trash")
                                }
                                .tint(.red)
                            }
                            .contextMenu {
                                Button(role: .destructive) {
                                    pendingPaymentDeletion = payment
                                } label: {
                                    Label("loans.delete", systemImage: "trash")
                                }
                            }
                        }
                    }
                } header: {
                    Text("loans.detail.payments \(detail.payments.count)")
                }
                Section {
                    Button(role: .destructive) {
                        isConfirmingDelete = true
                    } label: {
                        Text("loans.delete.action").frame(maxWidth: .infinity)
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    private func header(_ summary: LoanSummary) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(summary.loan.type.titleKey)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text(verbatim: loanTitle(summary))
                        .font(.title3.weight(.semibold))
                    Text(verbatim: loanPersonLine(summary))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                LoanStatusBadge(status: summary.status)
            }
            Text(verbatim: format(summary.remaining, summary))
                .font(.system(size: 30, weight: .bold, design: .rounded).monospacedDigit())
                .contentTransition(.numericText())
            Text("loans.detail.remaining_label")
                .font(.caption)
                .foregroundStyle(.secondary)
            ProgressView(value: Double(summary.progressPercent), total: 100)
                .tint(summary.status == .overdue ? Brand.expense : Brand.primary)
            Text("loans.repaid \(format(summary.amountRepaid, summary)) \(format(summary.loan.amount, summary))")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
        }
        .arzikinaCard()
        .accessibilityElement(children: .combine)
    }

    private func date(_ millis: EpochMillis) -> Date {
        Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
    }

    private func format(_ amount: MinorUnits, _ summary: LoanSummary) -> String {
        Money.format(CurrencyAmount(currencyCode: summary.currencyCode, amountMinor: amount))
    }

    private func deletePayment(_ payment: LoanPayment) {
        guard let space = session.dataSpace else { return }
        Task {
            do {
                try await space.loans.deletePayment(id: payment.id)
                session.sync?.requestSync(.localChange)
            } catch {
                deleteFailed = true
            }
        }
    }

    private func deleteLoan() {
        guard let space = session.dataSpace else { return }
        Task {
            do {
                try await space.loans.delete(id: loanId)
                session.sync?.requestSync(.localChange)
                dismiss()
            } catch {
                deleteFailed = true
            }
        }
    }
}
