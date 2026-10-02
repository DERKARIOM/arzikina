import ArzikinaDomain
import Foundation
import Observation

/// Autorisation des rappels, telle que l'utilisateur l'a réglée.
enum ReminderAuthorization: Equatable, Sendable {
    /// Jamais demandée.
    case notDetermined
    /// Refusée (modifiable seulement dans Réglages iOS).
    case denied
    case allowed
}

/// Un rappel prêt à programmer (contenu déjà traduit).
struct ReminderRequest: Equatable, Sendable {
    let id: String
    let title: String
    let body: String
    let fireAt: EpochMillis
}

/// Accès au centre de notifications du système — protocole pour garder le planificateur testable
/// et indépendant d'`UserNotifications`.
protocol LocalNotificationCenter: Sendable {
    func authorization() async -> ReminderAuthorization
    /// Affiche la demande système ; `true` si l'utilisateur accepte.
    func requestAuthorization() async -> Bool
    func pendingIdentifiers(withPrefix prefix: String) async -> [String]
    /// Programme [requests] ; un identifiant déjà programmé est remplacé (jamais de doublon).
    func schedule(_ requests: [ReminderRequest]) async
    func removePending(withIdentifiers identifiers: [String])
    /// Retire du centre de notifications les rappels déjà affichés commençant par [prefix].
    func removeDelivered(withPrefix prefix: String) async
}

/// Programme les rappels des automatisations de l'utilisateur connecté — rôle d'Android
/// `AutomationSchedulerImpl`, adapté à iOS (voir `AutomationReminders` : les prochains rappels sont
/// programmés à l'avance, puis recalculés à chaque changement).
///
/// Suit en continu les règles de la base (création, modification, pause, suppression, réception
/// par synchronisation) : aucun écran n'a à penser aux rappels. À la déconnexion, tout est retiré,
/// y compris les rappels déjà affichés (ils montrent les libellés d'un autre compte).
@MainActor
@Observable
final class AutomationReminderScheduler {

    private(set) var authorization: ReminderAuthorization = .notDetermined

    @ObservationIgnored private let center: LocalNotificationCenter
    @ObservationIgnored private let calendar: Calendar
    @ObservationIgnored private let now: () -> EpochMillis
    @ObservationIgnored private var rules: [RecurringTransaction] = []
    /// Derniers rappels programmés : rien n'est refait si la liste ne change pas.
    @ObservationIgnored private var applied: [ReminderRequest]?
    @ObservationIgnored private var observation: Task<Void, Never>?
    /// Incrémenté à chaque arrêt : un calcul commencé avant n'a plus le droit de programmer.
    @ObservationIgnored private var generation = 0
    /// Retrait des rappels de la session précédente (voir [stop]).
    @ObservationIgnored private var cleanup: Task<Void, Never>?

    init(
        center: LocalNotificationCenter,
        calendar: Calendar = ArzikinaCalendar.current,
        now: @escaping () -> EpochMillis = { EpochMillis((Date().timeIntervalSince1970 * 1000).rounded()) }
    ) {
        self.center = center
        self.calendar = calendar
        self.now = now
    }

    // MARK: - Cycle de vie (session)

    /// Suit les règles de [repository] (utilisateur qui vient de se connecter).
    func start(_ repository: RecurringRepository) {
        stopObserving()
        let current = generation
        observation = Task { [weak self] in
            for await overview in repository.observeOverview() {
                guard let self, self.generation == current else { return }
                self.rules = overview.rules
                await self.apply()
            }
        }
    }

    /// Déconnexion ou base vidée : plus aucun rappel, ni programmé ni affiché.
    func stop() {
        stopObserving()
        let center = center
        cleanup = Task {
            let ids = await center.pendingIdentifiers(withPrefix: AutomationReminders.identifierPrefix)
            center.removePending(withIdentifiers: ids)
            await center.removeDelivered(withPrefix: AutomationReminders.identifierPrefix)
        }
    }

    private func stopObserving() {
        generation += 1
        observation?.cancel()
        observation = nil
        rules = []
        applied = nil
    }

    // MARK: - Mises à jour

    /// Retour au premier plan : l'autorisation a pu changer dans Réglages, et le temps a passé.
    func refresh() async {
        await apply()
    }

    /// Demande l'autorisation si elle ne l'a jamais été (après la création d'une automatisation,
    /// ou depuis l'invitation de l'écran Automatisations), puis programme les rappels.
    func requestAuthorizationIfNeeded() async {
        guard await center.authorization() == .notDetermined else { return }
        _ = await center.requestAuthorization()
        await apply()
    }

    private func apply() async {
        let current = generation
        let status = await center.authorization()
        guard current == generation else { return }
        authorization = status
        // Sans autorisation, iOS n'afficherait rien : inutile de programmer.
        guard status == .allowed else {
            applied = nil
            return
        }

        let rulesById = Dictionary(rules.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let requests = AutomationReminders.upcoming(rules: rules, after: now(), calendar: calendar).compactMap { reminder in
            rulesById[reminder.ruleId].map { Self.request(for: reminder, rule: $0) }
        }
        guard requests != applied else { return }
        // Nettoyage d'une session précédente encore en cours : il ne doit pas retirer nos rappels.
        await cleanup?.value
        guard current == generation else { return }

        let pending = await center.pendingIdentifiers(withPrefix: AutomationReminders.identifierPrefix)
        guard current == generation else { return }
        let wanted = Set(requests.map(\.id))
        center.removePending(withIdentifiers: Array(Set(pending).subtracting(wanted)))
        await center.schedule(requests)
        guard current == generation else {
            // Déconnexion pendant la programmation : on retire ce qui vient d'être ajouté.
            center.removePending(withIdentifiers: Array(wanted))
            return
        }
        applied = requests
    }

    /// Même contenu qu'Android : libellé de la règle (description, sinon « Transaction
    /// automatique ») et invitation à l'enregistrer — jamais le montant, visible sur l'écran
    /// verrouillé.
    private static func request(for reminder: AutomationReminder, rule: RecurringTransaction) -> ReminderRequest {
        ReminderRequest(
            id: reminder.id,
            title: rule.displayTitle(),
            body: DomainDisplay.localized("automation.notification.body"),
            fireAt: reminder.fireAt
        )
    }
}
