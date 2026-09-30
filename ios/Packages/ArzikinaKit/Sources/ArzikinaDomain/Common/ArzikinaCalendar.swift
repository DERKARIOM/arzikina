import Foundation

/// Calendrier de référence des règles métier : grégorien, semaines ISO (lundi → dimanche, première
/// semaine = celle qui contient le 4 janvier), fuseau horaire EXPLICITE.
///
/// Équivaut à `ZoneId.systemDefault()` + `WeekFields.ISO` côté Android. Toutes les règles qui
/// raisonnent en « jour » (échéance d'un prêt, période d'un budget, récurrence) reçoivent ce
/// calendrier en paramètre : l'app passe `ArzikinaCalendar.current`, les tests un fuseau fixe —
/// les résultats ne dépendent donc jamais de l'appareil qui exécute les tests.
public enum ArzikinaCalendar {

    public static func make(timeZone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        calendar.firstWeekday = 2 // lundi
        calendar.minimumDaysInFirstWeek = 4 // règle ISO 8601
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }

    /// Calendrier de l'appareil (fuseau courant), à utiliser par l'application.
    public static var current: Calendar { make(timeZone: .current) }
}
