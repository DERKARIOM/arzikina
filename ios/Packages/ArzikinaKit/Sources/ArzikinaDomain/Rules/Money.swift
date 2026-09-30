import Foundation

/// Montant associé à sa devise.
public struct CurrencyAmount: Equatable, Hashable, Sendable {
    public var currencyCode: String
    public var amountMinor: MinorUnits

    public init(currencyCode: String, amountMinor: MinorUnits) {
        self.currencyCode = currencyCode
        self.amountMinor = amountMinor
    }
}

/// Conversion saisie ↔ stockage et formatage des montants — portage d'Android `util/Money.kt`.
///
/// Règles communes à Android, iOS, Web et au serveur :
/// - stockage en unité MINEURE, facteur fixe de **100 pour TOUTES les devises, XOF compris** ;
/// - format d'affichage IDENTIQUE quelle que soit la langue (« 10 000 F CFA » en français comme en
///   anglais) : séparateur de milliers espace, virgule décimale, décimales affichées seulement si
///   non nulles.
public enum Money {

    public static let minorUnitsPerMajor: Int64 = 100

    /// Espace fine insécable (U+202F) : séparateur de milliers de l'affichage, identique à ce que
    /// produit `NumberFormat` en français côté Android.
    public static let displayGroupingSeparator: Character = "\u{202F}"

    // MARK: - Saisie

    /// Montant saisi (unité majeure) → unité mineure. `nil` si [input] n'est pas un nombre positif.
    ///
    /// Tolère la virgule OU le point comme séparateur décimal, et les espaces (normale, insécable,
    /// fine) comme séparateurs de milliers : « 10 000 », « 10 000,50 » et « 10000.5 » sont valides.
    /// Arrondi au centime le plus proche (demi vers le haut).
    public static func parseToMinorUnits(_ input: String) -> MinorUnits? {
        let compact = String(input.unicodeScalars.filter { !CharacterSet.whitespacesAndNewlines.contains($0) && !isSpaceSeparator($0) })
        let normalized = compact.replacingOccurrences(of: ",", with: ".")
        guard !normalized.isEmpty, isStrictDecimal(normalized),
              let value = Decimal(string: normalized, locale: Locale(identifier: "en_US_POSIX"))
        else { return nil }
        guard value >= 0 else { return nil }

        var scaled = value * Decimal(minorUnitsPerMajor)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &scaled, 0, .plain)
        let number = NSDecimalNumber(decimal: rounded)
        guard number.compare(NSDecimalNumber(value: Int64.max)) != .orderedDescending else { return nil }
        return number.int64Value
    }

    /// Montant pour un champ de saisie ÉDITABLE : milliers séparés par une espace normale,
    /// décimales seulement si non nulles, jamais de devise — toujours ré-analysable par
    /// [parseToMinorUnits] (ex. « 10 000 », « 10 000,50 »).
    public static func formatForInput(_ minorUnits: MinorUnits) -> String {
        format(minorUnits, groupingSeparator: " ")
    }

    // MARK: - Affichage

    /// Montant affiché sans devise (ex. « 10 000 », « 10 000,50 », « -2 500 »).
    public static func formatAmount(_ minorUnits: MinorUnits) -> String {
        format(minorUnits, groupingSeparator: displayGroupingSeparator)
    }

    /// Montant affiché avec le symbole de sa devise (ex. « 10 000 F CFA »).
    public static func format(_ amount: CurrencyAmount) -> String {
        "\(formatAmount(amount.amountMinor)) \(symbol(of: amount.currencyCode))"
    }

    /// Symbole d'une devise prise en charge, sinon son code ISO.
    public static func symbol(of currencyCode: String) -> String {
        SupportedCurrency(rawValue: currencyCode)?.symbol ?? currencyCode
    }

    /// Valeur numérique en unité majeure (axes de graphiques uniquement, jamais pour un calcul).
    public static func toMajorDouble(_ minorUnits: MinorUnits) -> Double {
        Double(minorUnits) / Double(minorUnitsPerMajor)
    }

    // MARK: - Interne

    /// Seul algorithme de regroupement par milliers du module.
    static func groupThousands(_ digits: String, separator: Character) -> String {
        guard digits.count > 3 else { return digits }
        var result = ""
        for (index, character) in digits.enumerated() {
            if index > 0 && (digits.count - index) % 3 == 0 { result.append(separator) }
            result.append(character)
        }
        return result
    }

    private static func format(_ minorUnits: MinorUnits, groupingSeparator: Character) -> String {
        let magnitude = minorUnits.magnitude
        let sign = minorUnits < 0 ? "-" : ""
        let major = magnitude / UInt64(minorUnitsPerMajor)
        let cents = magnitude % UInt64(minorUnitsPerMajor)
        let grouped = groupThousands(String(major), separator: groupingSeparator)
        guard cents != 0 else { return sign + grouped }
        let centsText = cents < 10 ? "0\(cents)" : "\(cents)"
        return "\(sign)\(grouped),\(centsText)"
    }

    /// Même grammaire que `BigDecimal` côté Android, limitée aux chiffres ASCII :
    /// signe optionnel, partie entière et/ou décimale, exposant optionnel.
    private static func isStrictDecimal(_ text: String) -> Bool {
        text.range(of: #"^[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]+)?$"#, options: .regularExpression) != nil
    }

    /// Espaces typographiques (catégorie Unicode Zs), ex. U+00A0, U+2007, U+202F.
    private static func isSpaceSeparator(_ scalar: Unicode.Scalar) -> Bool {
        scalar.properties.generalCategory == .spaceSeparator
    }
}
