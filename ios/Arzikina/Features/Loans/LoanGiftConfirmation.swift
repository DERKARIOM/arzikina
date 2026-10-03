import ArzikinaDomain
import SwiftUI

/// Confirmation de « Transformer en cadeau » et alerte d'échec, regroupées pour garder le corps
/// de `LoanDetailView` court.
struct LoanGiftConfirmation: ViewModifier {

    let summary: LoanSummary?
    @Binding var isPresented: Bool
    @Binding var failed: Bool
    let onConfirm: @MainActor (_ description: String) -> Void

    func body(content: Content) -> some View {
        content
            .confirmationDialog(
                titleKey,
                isPresented: $isPresented,
                titleVisibility: .visible,
                presenting: summary
            ) { summary in
                Button("loans.gift.action") {
                    onConfirm(LoanGiftText.description(type: summary.loan.type, personName: summary.person?.name))
                }
            } message: { summary in
                Text(verbatim: LoanGiftText.confirmationMessage(
                    for: summary,
                    description: LoanGiftText.description(type: summary.loan.type, personName: summary.person?.name)
                ))
            }
            .alert("loans.gift.not_convertible", isPresented: $failed) {
                Button("common.ok", role: .cancel) {}
            }
    }

    private var titleKey: LocalizedStringKey {
        summary?.loan.type == .borrowed ? "loans.gift.confirm.title.borrowed" : "loans.gift.confirm.title.lent"
    }
}
