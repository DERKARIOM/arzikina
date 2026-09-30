import Foundation
import XCTest
@testable import ArzikinaDomain

/// Accès aux jeux de tests PARTAGÉS avec Android (`shared/test-fixtures/` à la racine du dépôt).
///
/// Les mêmes fichiers JSON sont exécutés par `SharedFixturesTest.kt` côté Android : si une règle
/// métier diverge entre les deux plateformes, au moins un des deux jeux de tests échoue.
///
/// Conventions des fichiers :
/// - instants : date locale « AAAA-MM-JJTHH:MM » (ou jour « AAAA-MM-JJ » = début de journée),
///   interprétée dans le `timeZone` du fichier (ou du cas, s'il en précise un) ;
/// - montants : unité mineure ; `null` = résultat absent.
enum SharedFixtures {

    static func load(_ name: String, file: StaticString = #filePath, line: UInt = #line) throws -> [String: Any] {
        let url = directory.appendingPathComponent(name)
        let data = try Data(contentsOf: url)
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw FixtureError.invalid("\(name) : objet JSON attendu")
        }
        return root
    }

    /// `shared/test-fixtures/`, retrouvé à partir de l'emplacement de ce fichier source :
    /// ios/Packages/ArzikinaKit/Tests/ArzikinaDomainTests/SharedFixtures.swift → racine du dépôt.
    private static var directory: URL {
        var url = URL(fileURLWithPath: #filePath)
        for _ in 0..<6 { url.deleteLastPathComponent() }
        return url.appendingPathComponent("shared/test-fixtures", isDirectory: true)
    }

    static func calendar(_ timeZoneIdentifier: String) throws -> Calendar {
        guard let timeZone = TimeZone(identifier: timeZoneIdentifier) else {
            throw FixtureError.invalid("Fuseau inconnu : \(timeZoneIdentifier)")
        }
        return ArzikinaCalendar.make(timeZone: timeZone)
    }

    /// « 2026-09-30T08:00 » ou « 2026-09-30 » → instant dans le fuseau de [calendar].
    static func millis(_ text: String, calendar: Calendar) throws -> EpochMillis {
        let parts = text.split(separator: "T")
        guard let day = CalendarDay(iso: String(parts[0])) else { throw FixtureError.invalid("Date invalide : \(text)") }
        guard parts.count == 2 else { return day.startOfDayMillis(calendar: calendar) }
        let time = parts[1].split(separator: ":").compactMap { Int($0) }
        guard time.count == 2 else { throw FixtureError.invalid("Heure invalide : \(text)") }
        return day.millis(hour: time[0], minute: time[1], calendar: calendar)
    }

    static func day(_ text: String) throws -> CalendarDay {
        guard let day = CalendarDay(iso: text) else { throw FixtureError.invalid("Jour invalide : \(text)") }
        return day
    }

    enum FixtureError: Error, CustomStringConvertible {
        case invalid(String)
        var description: String {
            switch self { case .invalid(let message): return message }
        }
    }
}

// MARK: - Lecture typée des valeurs JSON

extension Dictionary where Key == String, Value == Any {

    func string(_ key: String) throws -> String {
        guard let value = self[key] as? String else { throw SharedFixtures.FixtureError.invalid("Chaîne attendue : \(key)") }
        return value
    }

    func optionalString(_ key: String) -> String? {
        self[key] as? String
    }

    func int64(_ key: String) throws -> Int64 {
        guard let number = self[key] as? NSNumber else { throw SharedFixtures.FixtureError.invalid("Nombre attendu : \(key)") }
        return number.int64Value
    }

    func optionalInt64(_ key: String) -> Int64? {
        (self[key] as? NSNumber)?.int64Value
    }

    func int(_ key: String) throws -> Int { Int(try int64(key)) }

    func double(_ key: String) throws -> Double {
        guard let number = self[key] as? NSNumber else { throw SharedFixtures.FixtureError.invalid("Nombre attendu : \(key)") }
        return number.doubleValue
    }

    func bool(_ key: String) throws -> Bool {
        guard let value = self[key] as? Bool else { throw SharedFixtures.FixtureError.invalid("Booléen attendu : \(key)") }
        return value
    }

    func object(_ key: String) throws -> [String: Any] {
        guard let value = self[key] as? [String: Any] else { throw SharedFixtures.FixtureError.invalid("Objet attendu : \(key)") }
        return value
    }

    func optionalObject(_ key: String) -> [String: Any]? {
        self[key] as? [String: Any]
    }

    func objects(_ key: String) throws -> [[String: Any]] {
        guard let value = self[key] as? [[String: Any]] else { throw SharedFixtures.FixtureError.invalid("Tableau attendu : \(key)") }
        return value
    }

    func strings(_ key: String) throws -> [String] {
        guard let value = self[key] as? [String] else { throw SharedFixtures.FixtureError.invalid("Tableau de chaînes attendu : \(key)") }
        return value
    }

    func isNull(_ key: String) -> Bool {
        self[key] == nil || self[key] is NSNull
    }
}
