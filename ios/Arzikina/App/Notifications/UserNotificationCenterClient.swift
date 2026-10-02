import ArzikinaDomain
import Foundation
import UserNotifications

/// [LocalNotificationCenter] adossé à `UNUserNotificationCenter`.
///
/// Rappels datés en heure LOCALE (`UNCalendarNotificationTrigger`) : « 08:30 » reste 08:30 même
/// après un changement de fuseau, comme l'heure choisie dans l'automatisation.
struct UserNotificationCenterClient: LocalNotificationCenter {

    /// Regroupe les rappels d'automatisation dans le centre de notifications.
    static let threadIdentifier = "automations"

    private var center: UNUserNotificationCenter { .current() }

    func authorization() async -> ReminderAuthorization {
        switch await center.notificationSettings().authorizationStatus {
        case .notDetermined: return .notDetermined
        case .denied: return .denied
        case .authorized, .provisional, .ephemeral: return .allowed
        @unknown default: return .denied
        }
    }

    func requestAuthorization() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
    }

    func pendingIdentifiers(withPrefix prefix: String) async -> [String] {
        await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(prefix) }
    }

    func schedule(_ requests: [ReminderRequest]) async {
        let calendar = ArzikinaCalendar.current
        for request in requests {
            let content = UNMutableNotificationContent()
            content.title = request.title
            content.body = request.body
            content.sound = .default
            content.threadIdentifier = Self.threadIdentifier
            let date = Date(timeIntervalSince1970: TimeInterval(request.fireAt) / 1000)
            let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            // Même identifiant = remplacement : jamais deux rappels pour la même échéance.
            try? await center.add(UNNotificationRequest(identifier: request.id, content: content, trigger: trigger))
        }
    }

    func removePending(withIdentifiers identifiers: [String]) {
        guard !identifiers.isEmpty else { return }
        center.removePendingNotificationRequests(withIdentifiers: identifiers)
    }

    func removeDelivered(withPrefix prefix: String) async {
        let ids = await center.deliveredNotifications().map(\.request.identifier).filter { $0.hasPrefix(prefix) }
        guard !ids.isEmpty else { return }
        center.removeDeliveredNotifications(withIdentifiers: ids)
    }
}

/// Réagit aux rappels d'automatisation : affichés aussi quand l'app est ouverte, et un toucher
/// ouvre l'écran Automatisations (Android : ouverture de l'app).
///
/// Doit être le délégué du centre de notifications AVANT la fin du lancement (voir `ArzikinaApp`),
/// sinon le toucher qui a lancé l'app est perdu.
final class NotificationResponder: NSObject, UNUserNotificationCenterDelegate {

    /// Un rappel arrive pendant que l'app est ouverte : les échéances dues peuvent être créées.
    var onReminderDelivered: @MainActor () -> Void = {}
    /// L'utilisateur a touché un rappel.
    var onReminderOpened: @MainActor () -> Void = {}

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        guard Self.isAutomationReminder(notification.request.identifier) else { return [] }
        let handler = onReminderDelivered
        await MainActor.run { handler() }
        return [.banner, .list, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard Self.isAutomationReminder(response.notification.request.identifier) else { return }
        let handler = onReminderOpened
        await MainActor.run { handler() }
    }

    private static func isAutomationReminder(_ identifier: String) -> Bool {
        identifier.hasPrefix(AutomationReminders.identifierPrefix)
    }
}
