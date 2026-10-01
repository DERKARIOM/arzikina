import Foundation

/// Échéances d'une automatisation — portage d'Android `generateMissingOccurrences`, avec ce qu'il
/// faut pour que PLUSIEURS appareils puissent les générer sans doublon.
public enum Automations {

    // MARK: - Identifiant d'échéance

    /// Identifiant DÉTERMINISTE d'une échéance générée sur iOS : même règle + même jour = même
    /// identifiant sur tous les iPhone. Le serveur traite alors le second envoi comme une
    /// répétition (création idempotente) au lieu de le refuser (contrainte d'unicité règle + date).
    ///
    /// Format UUID (version 5, variante RFC 4122) à partir de deux empreintes FNV-1a 64 bits.
    public static func occurrenceId(recurringTransactionId: EntityID, scheduledDate: EpochMillis) -> EntityID {
        let key = Array("arzikina-occurrence|\(recurringTransactionId)|\(scheduledDate)".utf8)
        var bytes = withUnsafeBytes(of: fnv1a(key, seed: 0xCBF2_9CE4_8422_2325).bigEndian, Array.init)
            + withUnsafeBytes(of: fnv1a(key, seed: 0x8422_2325_CBF2_9CE4).bigEndian, Array.init)
        bytes[6] = (bytes[6] & 0x0F) | 0x50 // version 5
        bytes[8] = (bytes[8] & 0x3F) | 0x80 // variante RFC 4122
        let hex = bytes.map { String(format: "%02x", $0) }.joined()
        let parts = [hex.prefix(8), hex.dropFirst(8).prefix(4), hex.dropFirst(12).prefix(4), hex.dropFirst(16).prefix(4), hex.dropFirst(20)]
        return parts.map(String.init).joined(separator: "-")
    }

    private static func fnv1a(_ bytes: [UInt8], seed: UInt64) -> UInt64 {
        bytes.reduce(seed) { ($0 ^ UInt64($1)) &* 0x0000_0100_0000_01B3 }
    }

    // MARK: - Génération

    /// Résultat de la génération pour une règle active.
    public struct GenerationPlan: Equatable, Sendable {
        /// Jours d'échéance à créer (sans ceux qui existent déjà).
        public var newDates: [EpochMillis]
        /// Règle mise à jour (prochaine échéance, désactivation), `nil` si elle ne change pas.
        public var updatedRule: RecurringTransaction?
    }

    /// Échéances dues à [now] (heure de déclenchement comprise) et nouvel état de la règle —
    /// Android `generateMissingOccurrences` / `deactivateIfPastEndDate`.
    public static func plan(
        for rule: RecurringTransaction,
        existingDates: Set<EpochMillis>,
        now: EpochMillis,
        calendar: Calendar
    ) -> GenerationPlan {
        guard rule.isActive else { return GenerationPlan(newDates: [], updatedRule: nil) }
        let dates = Recurrence.missingScheduledDates(
            nextExecutionDate: rule.nextExecutionDate,
            frequency: rule.frequency,
            endDate: rule.endDate,
            now: now,
            triggerHour: rule.triggerHour,
            triggerMinute: rule.triggerMinute,
            calendar: calendar
        )
        guard let last = dates.last else {
            // Rien de dû : la règle s'arrête si sa prochaine échéance dépasse déjà sa date de fin.
            if let endDate = rule.endDate,
               CalendarDay(epochMillis: rule.nextExecutionDate, calendar: calendar) > CalendarDay(epochMillis: endDate, calendar: calendar) {
                var updated = rule
                updated.isActive = false
                return GenerationPlan(newDates: [], updatedRule: updated)
            }
            return GenerationPlan(newDates: [], updatedRule: nil)
        }
        var updated = rule
        let next = Recurrence.nextExecutionDate(after: last, frequency: rule.frequency, calendar: calendar)
        updated.nextExecutionDate = next ?? rule.nextExecutionDate
        updated.isActive = next.map { candidate in
            rule.endDate.map { CalendarDay(epochMillis: candidate, calendar: calendar) <= CalendarDay(epochMillis: $0, calendar: calendar) } ?? true
        } ?? false
        return GenerationPlan(newDates: dates.filter { !existingDates.contains($0) }, updatedRule: updated)
    }

    // MARK: - Réconciliation entre appareils

    /// Une échéance telle que la base la connaît, avec ce qu'il faut pour départager des doublons.
    public struct StoredOccurrence: Equatable, Sendable {
        public var occurrence: RecurringTransactionOccurrence
        /// `true` si le serveur la connaît (version > 0).
        public var isKnownByServer: Bool

        public init(occurrence: RecurringTransactionOccurrence, isKnownByServer: Bool) {
            self.occurrence = occurrence
            self.isKnownByServer = isKnownByServer
        }
    }

    /// Ce qu'il faut faire d'un groupe d'échéances de même règle et même jour.
    public struct ReconciliationPlan: Equatable, Sendable {
        /// Échéance conservée, avec la décision reportée si besoin (`nil` = inchangée).
        public var updatedKeeper: RecurringTransactionOccurrence?
        public var keeperId: EntityID
        /// Échéances en double à supprimer.
        public var removedIds: [EntityID]
        /// Transactions créées en double (même échéance validée sur deux appareils) à supprimer.
        public var duplicateTransactionIds: [EntityID]
    }

    /// Garde l'échéance connue du serveur (sinon une déjà traitée, puis la plus ancienne) ; si elle
    /// est encore en attente alors qu'un doublon a été traité, elle reprend cette décision. Une
    /// transaction créée par un doublon ALORS QUE l'échéance gardée a déjà la sienne est en trop.
    public static func reconcile(_ group: [StoredOccurrence]) -> ReconciliationPlan? {
        guard group.count > 1 else { return nil }
        let ordered = group.sorted { lhs, rhs in
            if lhs.isKnownByServer != rhs.isKnownByServer { return lhs.isKnownByServer }
            let lhsDone = lhs.occurrence.status != .pending, rhsDone = rhs.occurrence.status != .pending
            if lhsDone != rhsDone { return lhsDone }
            if lhs.occurrence.createdAt != rhs.occurrence.createdAt { return lhs.occurrence.createdAt < rhs.occurrence.createdAt }
            return lhs.occurrence.id < rhs.occurrence.id
        }
        var keeper = ordered[0].occurrence
        let original = keeper
        var duplicateTransactions: [EntityID] = []
        for other in ordered.dropFirst().map(\.occurrence) where other.status != .pending {
            if keeper.status == .pending {
                keeper.status = other.status
                keeper.transactionId = other.transactionId
                keeper.processedAt = other.processedAt
            } else if let transactionId = other.transactionId, transactionId != keeper.transactionId {
                duplicateTransactions.append(transactionId)
            }
        }
        return ReconciliationPlan(
            updatedKeeper: keeper == original ? nil : keeper,
            keeperId: keeper.id,
            removedIds: ordered.dropFirst().map(\.occurrence.id),
            duplicateTransactionIds: duplicateTransactions
        )
    }

    // MARK: - Validation

    /// Transaction créée quand une échéance est validée telle quelle : le jour de l'échéance à
    /// l'heure de la règle — Android `acceptOccurrence` / `combineDayAndTime`.
    public static func transaction(for occurrence: RecurringTransactionOccurrence, rule: RecurringTransaction, id: EntityID, now: EpochMillis, calendar: Calendar) -> Transaction {
        Transaction(
            id: id,
            amount: rule.amount,
            type: rule.type,
            accountId: rule.accountId,
            categoryId: rule.categoryId,
            date: Recurrence.triggerInstant(day: occurrence.scheduledDate, hour: rule.triggerHour, minute: rule.triggerMinute, calendar: calendar),
            description: rule.description,
            paymentMethod: rule.paymentMethod,
            createdAt: now
        )
    }
}

/// Une échéance (ou la prochaine d'une règle) prête à afficher.
public struct AutomationItem: Identifiable, Equatable, Sendable {
    /// `nil` pour une échéance à venir (pas encore générée).
    public var occurrence: RecurringTransactionOccurrence?
    public var rule: RecurringTransaction
    public var account: Account?
    public var category: Category?
    /// Jour de l'échéance.
    public var scheduledDate: EpochMillis

    public var id: String { occurrence?.id ?? "next-\(rule.id)" }

    public init(occurrence: RecurringTransactionOccurrence?, rule: RecurringTransaction, account: Account?, category: Category?, scheduledDate: EpochMillis) {
        self.occurrence = occurrence
        self.rule = rule
        self.account = account
        self.category = category
        self.scheduledDate = scheduledDate
    }
}

/// Écran Automatisations — Android `RecurringTransactionsUiState` : à traiter, à venir, historique.
public struct AutomationOverview: Equatable, Sendable {
    public var pending: [AutomationItem]
    public var upcoming: [AutomationItem]
    public var history: [AutomationItem]
    /// Toutes les règles (actives ou non), pour les gérer.
    public var rules: [RecurringTransaction]

    public init(pending: [AutomationItem], upcoming: [AutomationItem], history: [AutomationItem], rules: [RecurringTransaction]) {
        self.pending = pending
        self.upcoming = upcoming
        self.history = history
        self.rules = rules
    }

    public static let empty = AutomationOverview(pending: [], upcoming: [], history: [], rules: [])

    /// Sections construites comme Android : à traiter par date, à venir = prochaine échéance des
    /// règles actives (sauf si elle attend déjà une validation), historique du plus récent au plus
    /// ancien. Une échéance dont la règle a disparu n'est pas affichée.
    public static func make(
        rules: [RecurringTransaction],
        occurrences: [RecurringTransactionOccurrence],
        accounts: [EntityID: Account],
        categories: [EntityID: Category]
    ) -> AutomationOverview {
        let rulesById = Dictionary(uniqueKeysWithValues: rules.map { ($0.id, $0) })
        func item(_ occurrence: RecurringTransactionOccurrence) -> AutomationItem? {
            guard let rule = rulesById[occurrence.recurringTransactionId] else { return nil }
            return AutomationItem(occurrence: occurrence, rule: rule, account: accounts[rule.accountId], category: rule.categoryId.flatMap { categories[$0] }, scheduledDate: occurrence.scheduledDate)
        }
        let pendingOccurrences = occurrences.filter { $0.status == .pending }
        let pendingKeys = Set(pendingOccurrences.map { "\($0.recurringTransactionId)|\($0.scheduledDate)" })
        let upcoming = rules
            .filter { $0.isActive && !pendingKeys.contains("\($0.id)|\($0.nextExecutionDate)") }
            .sorted { $0.nextExecutionDate < $1.nextExecutionDate }
            .map { AutomationItem(occurrence: nil, rule: $0, account: accounts[$0.accountId], category: $0.categoryId.flatMap { categories[$0] }, scheduledDate: $0.nextExecutionDate) }
        return AutomationOverview(
            pending: pendingOccurrences.sorted { $0.scheduledDate < $1.scheduledDate }.compactMap(item),
            upcoming: upcoming,
            history: occurrences.filter { $0.status != .pending }.sorted { ($0.processedAt ?? 0) > ($1.processedAt ?? 0) }.compactMap(item),
            rules: rules
        )
    }
}
