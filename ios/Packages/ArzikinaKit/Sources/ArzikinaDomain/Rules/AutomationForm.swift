import Foundation

// MARK: - Champs communs

/// Ce que contient la transaction d'une automatisation (type, montant, compte, catégorie,
/// description, moyen de paiement) — partagé par le formulaire de la RÈGLE et par « Modifier puis
/// valider » une échéance, qui saisissent exactement les mêmes champs (Android
/// `RecurringTransactionFormState` / `OccurrenceEditState`).
///
/// Revenu ou dépense seulement : une automatisation de transfert n'existe pas (Android
/// `RecurringTransaction.categoryId`, réservé à un futur type transfert).
public struct AutomationDetailsDraft: Equatable, Sendable {
    public private(set) var type: TransactionType
    public var amountInput: String
    public var accountId: EntityID?
    public var categoryId: EntityID?
    public var description: String
    public var paymentMethod: PaymentMethod?

    public init(
        type: TransactionType = .expense,
        amountInput: String = "",
        accountId: EntityID? = nil,
        categoryId: EntityID? = nil,
        description: String = "",
        paymentMethod: PaymentMethod? = nil
    ) {
        self.type = type == .transfer ? .expense : type
        self.amountInput = amountInput
        self.accountId = accountId
        self.categoryId = categoryId
        self.description = description
        self.paymentMethod = paymentMethod
    }

    /// Champs pré-remplis depuis [rule].
    public init(rule: RecurringTransaction) {
        self.init(
            type: rule.type,
            amountInput: Money.formatForInput(rule.amount),
            accountId: rule.accountId,
            categoryId: rule.categoryId,
            description: rule.description,
            paymentMethod: rule.paymentMethod
        )
    }

    /// Types proposés.
    public static let types: [TransactionType] = [.expense, .income]

    /// Change le type ; la catégorie, propre à un type, est réinitialisée (comme Android).
    public mutating func changeType(_ newType: TransactionType) {
        guard newType != .transfer, newType != type else { return }
        type = newType
        categoryId = nil
    }

    /// Champs vérifiés, ou la PREMIÈRE erreur dans l'ordre d'Android (montant, compte, catégorie).
    public func validate() -> Result<AutomationDetails, AutomationFormError> {
        guard let amount = Money.parseToMinorUnits(amountInput), amount > 0 else { return .failure(.invalidAmount) }
        guard let accountId else { return .failure(.accountRequired) }
        guard let categoryId else { return .failure(.categoryRequired) }
        return .success(AutomationDetails(
            type: type,
            amount: amount,
            accountId: accountId,
            categoryId: categoryId,
            description: description.trimmingCharacters(in: .whitespacesAndNewlines),
            paymentMethod: paymentMethod
        ))
    }
}

/// [AutomationDetailsDraft] vérifié.
public struct AutomationDetails: Equatable, Sendable {
    public var type: TransactionType
    public var amount: MinorUnits
    public var accountId: EntityID
    public var categoryId: EntityID
    public var description: String
    public var paymentMethod: PaymentMethod?
}

/// Erreur de saisie d'une automatisation ou d'une échéance modifiée.
public enum AutomationFormError: Error, Equatable, Sendable {
    case invalidAmount
    case accountRequired
    case categoryRequired
    /// La date de fin précède la première échéance.
    case endBeforeStart
}

// MARK: - Règle

/// Saisie du formulaire d'automatisation — Android `RecurringTransactionFormState`.
///
/// L'état actif de la règle n'est PAS modifiable ici (interrupteur de la liste) : une modification
/// ne réactive jamais une règle en pause ou terminée.
public struct AutomationDraft: Equatable, Sendable {
    public var details: AutomationDetailsDraft
    public var frequency: RecurringFrequency
    /// Première échéance.
    public var startDay: CalendarDay
    public var hasEndDate: Bool
    public var endDay: CalendarDay
    public var triggerHour: Int
    public var triggerMinute: Int

    /// Nouvelle automatisation : dépense mensuelle à partir de [today], à 08:00 (Android).
    public init(today: CalendarDay, accountId: EntityID?) {
        details = AutomationDetailsDraft(accountId: accountId)
        frequency = .monthly
        startDay = today
        hasEndDate = false
        endDay = today
        triggerHour = RecurringTransaction.defaultTriggerHour
        triggerMinute = RecurringTransaction.defaultTriggerMinute
    }

    /// Formulaire pré-rempli pour modifier [rule].
    public init(editing rule: RecurringTransaction, calendar: Calendar) {
        details = AutomationDetailsDraft(rule: rule)
        frequency = rule.frequency
        startDay = CalendarDay(epochMillis: rule.startDate, calendar: calendar)
        hasEndDate = rule.endDate != nil
        endDay = CalendarDay(epochMillis: rule.endDate ?? rule.startDate, calendar: calendar)
        triggerHour = rule.triggerHour
        triggerMinute = rule.triggerMinute
    }

    /// Une échéance unique n'a pas de date de fin.
    public var showsEndDate: Bool { frequency != .once }
}

public enum AutomationForm {

    /// Règle à enregistrer, ou la PREMIÈRE erreur de saisie.
    ///
    /// Nouvelle règle : active, première échéance = [AutomationDraft.startDay]. En modification,
    /// l'état actif, la date de création et la prochaine échéance de [existing] sont conservés :
    /// c'est le dépôt qui décide si la prochaine échéance peut suivre la nouvelle date de début
    /// (voir [ruleToStore]).
    public static func build(
        _ draft: AutomationDraft,
        existing: RecurringTransaction?,
        newId: @autoclosure () -> EntityID,
        now: EpochMillis,
        calendar: Calendar
    ) -> Result<RecurringTransaction, AutomationFormError> {
        let details: AutomationDetails
        switch draft.details.validate() {
        case .success(let value): details = value
        case .failure(let error): return .failure(error)
        }
        let hasEnd = draft.hasEndDate && draft.showsEndDate
        if hasEnd, draft.endDay < draft.startDay { return .failure(.endBeforeStart) }

        let start = draft.startDay.startOfDayMillis(calendar: calendar)
        return .success(RecurringTransaction(
            id: existing?.id ?? newId(),
            type: details.type,
            amount: details.amount,
            accountId: details.accountId,
            categoryId: details.categoryId,
            description: details.description,
            paymentMethod: details.paymentMethod,
            startDate: start,
            endDate: hasEnd ? draft.endDay.startOfDayMillis(calendar: calendar) : nil,
            frequency: draft.frequency,
            nextExecutionDate: existing?.nextExecutionDate ?? start,
            isActive: existing?.isActive ?? true,
            triggerHour: draft.triggerHour,
            triggerMinute: draft.triggerMinute,
            createdAt: existing?.createdAt ?? now,
            updatedAt: now
        ))
    }

    /// Règle réellement écrite par le dépôt, relue DANS la transaction SQL — Android
    /// `saveRecurringTransaction` :
    /// - nouvelle règle ([stored] `nil`) : active, première échéance = date de début ;
    /// - modification : état actif et date de création de la version enregistrée ; la prochaine
    ///   échéance suit la nouvelle date de début SEULEMENT si aucune échéance n'a encore été
    ///   générée (on ne rembobine jamais une progression entamée, ni l'historique).
    public static func ruleToStore(
        _ edited: RecurringTransaction,
        stored: RecurringTransaction?,
        hasGeneratedOccurrences: Bool
    ) -> RecurringTransaction {
        var rule = edited
        guard let stored else {
            rule.isActive = true
            rule.nextExecutionDate = edited.startDate
            return rule
        }
        rule.isActive = stored.isActive
        rule.createdAt = stored.createdAt
        rule.nextExecutionDate = hasGeneratedOccurrences ? stored.nextExecutionDate : edited.startDate
        return rule
    }
}

// MARK: - « Modifier puis valider » une échéance

/// Transaction ponctuelle d'une échéance, modifiable avant validation — Android
/// `OccurrenceEditState`. La règle d'origine n'est JAMAIS modifiée par ce chemin.
public struct OccurrenceEditDraft: Equatable, Sendable {
    public var details: AutomationDetailsDraft
    /// Date et heure de la transaction (par défaut : le jour de l'échéance à l'heure de la règle).
    public var date: EpochMillis

    public init(occurrence: RecurringTransactionOccurrence, rule: RecurringTransaction, calendar: Calendar) {
        details = AutomationDetailsDraft(rule: rule)
        date = Recurrence.triggerInstant(day: occurrence.scheduledDate, hour: rule.triggerHour, minute: rule.triggerMinute, calendar: calendar)
    }

    /// Transaction à créer, ou la PREMIÈRE erreur de saisie.
    public func build(id: EntityID, now: EpochMillis) -> Result<Transaction, AutomationFormError> {
        details.validate().map { details in
            Transaction(
                id: id,
                amount: details.amount,
                type: details.type,
                accountId: details.accountId,
                categoryId: details.categoryId,
                date: date,
                description: details.description,
                paymentMethod: details.paymentMethod,
                createdAt: now
            )
        }
    }
}

// MARK: - Suppression

/// Ce que la suppression d'une automatisation emporterait.
public struct AutomationDeletionImpact: Equatable, Sendable {
    /// Transactions déjà créées par ses échéances validées (encore présentes).
    public var createdTransactionCount: Int

    public init(createdTransactionCount: Int) {
        self.createdTransactionCount = createdTransactionCount
    }
}
