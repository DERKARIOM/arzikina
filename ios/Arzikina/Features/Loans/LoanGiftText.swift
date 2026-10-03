import ArzikinaDomain
import Foundation

/// Textes de « Transformer en cadeau » — mêmes libellés qu'Android (`LoanGiftDescription`,
/// `loan_gift_confirm_*`).
enum LoanGiftText {

    /// Description de la transaction cadeau, dans la langue ACTUELLE puis enregistrée telle quelle
    /// (comme une description tapée à la main) : « Cadeau à Awa », « Cadeau de la part d'Aïcha »,
    /// « Gift from Aïcha ». Le nom réel de la personne est toujours utilisé.
    static func description(type: LoanType, personName: String?) -> String {
        let name = (personName ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        // Personne introuvable (pas encore synchronisée) : repli neutre, comme Android.
        guard !name.isEmpty else { return DomainDisplay.localized("loans.status.gifted") }
        let key: String
        switch type {
        case .lent: key = "loans.gift.description.to %@"
        case .borrowed:
            key = FrenchElision.requiresElision(name) ? "loans.gift.description.from_elided %@" : "loans.gift.description.from %@"
        }
        return String(format: DomainDisplay.localized(key), name)
    }

    /// Confirmation : ce qui ne sera plus une dette, la part offerte (et la part remboursée, qui ne
    /// change pas) et le libellé de la transaction.
    static func confirmationMessage(for summary: LoanSummary, description: String) -> String {
        let gift = Money.format(CurrencyAmount(currencyCode: summary.currencyCode, amountMinor: summary.remaining))
        let amountLine: String
        if summary.amountRepaid > 0 {
            let repaid = Money.format(CurrencyAmount(currencyCode: summary.currencyCode, amountMinor: summary.amountRepaid))
            amountLine = String(format: DomainDisplay.localized("loans.gift.confirm.partial %@ %@"), gift, repaid)
        } else {
            amountLine = String(format: DomainDisplay.localized("loans.gift.confirm.full %@"), gift)
        }
        return [
            DomainDisplay.localized("loans.gift.confirm.message"),
            amountLine,
            String(format: DomainDisplay.localized("loans.gift.confirm.label %@"), description)
        ].joined(separator: "\n\n")
    }
}
