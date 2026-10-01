import ArzikinaDomain
import SwiftUI

extension LoanStatus {
    var titleKey: LocalizedStringKey {
        switch self {
        case .ongoing: return "loans.status.ongoing"
        case .repaid: return "loans.status.repaid"
        case .overdue: return "loans.status.overdue"
        case .upcoming: return "loans.status.upcoming"
        }
    }

    /// Couleur du badge ; toujours accompagnée du libellé (jamais la couleur seule).
    var color: Color {
        switch self {
        case .ongoing: return Brand.primary
        case .repaid: return .secondary
        case .overdue: return Brand.expense
        case .upcoming: return Color(argb: 0xFFF5_9E0B)
        }
    }
}

extension LoanType {
    var titleKey: LocalizedStringKey { self == .lent ? "loans.type.lent" : "loans.type.borrowed" }
}

/// Badge de statut d'un prêt (point coloré + libellé).
struct LoanStatusBadge: View {
    let status: LoanStatus

    var body: some View {
        HStack(spacing: 5) {
            Circle().fill(status.color).frame(width: 7, height: 7)
            Text(status.titleKey).font(.caption.weight(.semibold))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(status.color.opacity(0.14), in: Capsule())
    }
}

/// « À Awa » (prêt) ou « De Awa » (emprunt).
func loanPersonLine(_ summary: LoanSummary) -> String {
    let name = summary.person?.name ?? DomainDisplay.localized("loans.unknown_person")
    let format = DomainDisplay.localized(summary.loan.type == .lent ? "loans.person.to %@" : "loans.person.from %@")
    return String(format: format, name)
}

/// Titre d'un prêt : sa description, sinon « Prêt » / « Emprunt ».
func loanTitle(_ summary: LoanSummary) -> String {
    let description = summary.loan.description.trimmingCharacters(in: .whitespacesAndNewlines)
    if !description.isEmpty { return description }
    return DomainDisplay.localized(summary.loan.type == .lent ? "loans.type.lent" : "loans.type.borrowed")
}
