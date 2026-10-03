import Foundation

/// « Transformer un prêt / emprunt en cadeau » — règles PURES, portage d'Android
/// `domain/model/LoanGift.kt` (même règle que le Web, `lib/loan-gift.ts`).
///
/// PRINCIPE COMPTABLE : l'argent du prêt est DÉJÀ sorti (ou entré) avec la transaction de
/// décaissement. La transformation ne crée aucun mouvement d'argent : elle RECLASSE la part non
/// remboursée dans « Cadeaux » :
/// - le décaissement garde la part déjà remboursée ;
/// - la transaction cadeau porte le reste.
/// Leur somme reste égale au montant du prêt : solde, total des revenus et des dépenses inchangés.
public enum LoanGift {

    /// Reclassement à écrire.
    public struct Plan: Equatable, Sendable {
        /// Montant de la transaction cadeau (= reste dû).
        public let giftAmount: MinorUnits
        /// Nouveau montant du décaissement (= part remboursée), 0 si rien n'a été remboursé.
        public let disbursementAmountAfter: MinorUnits
        /// Rien de remboursé : le décaissement est reclassé SUR PLACE (même identifiant) au lieu
        /// d'être réduit à 0 et doublé d'une nouvelle ligne.
        public var reusesDisbursementTransaction: Bool { disbursementAmountAfter == 0 }
    }

    /// Reste dû à partir des montants SOURCES (jamais du `remainingAmount` stocké) — Android
    /// `outstandingAmount`. [repaid] : somme des remboursements.
    public static func outstanding(_ loan: Loan, repaid: MinorUnits) -> MinorUnits {
        loan.amount - repaid - loan.giftedAmount
    }

    /// Permis si la dette n'est ni remboursée ni déjà offerte et qu'il reste quelque chose à offrir
    /// — Android `canConvertToGift` (en cours, en retard ou à venir).
    public static func canConvert(_ loan: Loan, repaid: MinorUnits, now: EpochMillis, calendar: Calendar) -> Bool {
        let status = LoanStatusRule.status(
            amount: loan.amount, amountRepaid: repaid, startDate: loan.startDate, dueDate: loan.dueDate,
            now: now, calendar: calendar, giftedAmount: loan.giftedAmount
        )
        return loan.giftedAmount == 0 && outstanding(loan, repaid: repaid) > 0 && !status.isSettled
    }

    /// Reclassement de [loan], `nil` si la transformation n'est pas (ou plus) permise.
    public static func plan(_ loan: Loan, repaid: MinorUnits, now: EpochMillis, calendar: Calendar) -> Plan? {
        guard canConvert(loan, repaid: repaid, now: now, calendar: calendar) else { return nil }
        let gift = outstanding(loan, repaid: repaid)
        return Plan(giftAmount: gift, disbursementAmountAfter: loan.amount - gift)
    }
}

extension LoanType {
    /// « Cadeaux » en DÉPENSE pour un prêt (l'argent a été offert), en REVENU pour un emprunt
    /// (l'argent a été reçu en cadeau) — Android `giftCategoryKey`.
    public var giftCategory: SystemCategoryKey { self == .lent ? .gifts : .giftsReceived }
}

extension LoanSummary {
    /// L'action « Transformer en cadeau » est proposée (statut recalculé à l'affichage).
    public var canConvertToGift: Bool {
        !loan.isGifted && remaining > 0 && !status.isSettled
    }
}

/// Élision française devant un prénom : « de la part d'Aïcha », mais « de la part de Moussa » —
/// Android `FrenchElision`. Voyelles seulement (accents ignorés) : « h » et « y » sont le plus
/// souvent aspirés dans les prénoms courants d'Afrique de l'Ouest (« Halima », « Yacouba »).
public enum FrenchElision {
    public static func requiresElision(_ word: String) -> Bool {
        guard let first = word.drop(while: { $0.isWhitespace }).first else { return false }
        let base = String(first).folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "fr"))
        return base.first.map { "aeiou".contains($0) } ?? false
    }
}
