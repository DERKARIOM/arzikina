package com.naniger.arzikina.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Cadence de renouvellement d'une [RecurringTransaction] (voir [computeNextExecutionDate]).
 *
 * Architecture volontairement ouverte à des règles plus fines plus tard (jour spécifique du mois,
 * dernier jour du mois, fréquence personnalisée en nombre de jours...) : le jour où l'une d'elles
 * sera nécessaire, elle s'ajoute ici et dans [computeNextExecutionDate], sans toucher aux entités
 * Room ni aux écrans déjà écrits (voir instructions projet, "évolutivité sans refonte majeure").
 */
enum class RecurringFrequency {
    /** Une seule occurrence, jamais reconduite (voir [RecurringTransaction.endDate], ignoré ici). */
    ONCE,
    DAILY,
    WEEKLY,
    /** Toutes les 2 semaines. */
    BIWEEKLY,
    MONTHLY,
    /** Tous les 3 mois. */
    QUARTERLY,
    /** Tous les 6 mois. */
    SEMIANNUAL,
    YEARLY
}

/**
 * Calcule la prochaine date d'échéance après [currentDate] pour [frequency] — fonction pure
 * (aucun accès Room), même principe que [computeLoanStatus]. `null` pour
 * [RecurringFrequency.ONCE] : une occurrence unique n'a par définition pas de suite, ce qui indique
 * à `RecurringTransactionRepository` de désactiver la règle ([RecurringTransaction.isActive])
 * après avoir généré sa seule occurrence plutôt que de continuer à en produire.
 *
 * Raisonne en [LocalDate] (jour calendaire), pas en millisecondes brutes, pour que "tous les mois"
 * avance correctement d'un mois civil (28-31 jours selon le mois) plutôt que d'un nombre fixe de
 * jours — le résultat est ensuite reconverti en début de journée locale, même convention que
 * [Loan.startDate]/[Loan.dueDate] (voir `isPastDueDay` dans `LoanStatus.kt`).
 */
fun computeNextExecutionDate(currentDate: Long, frequency: RecurringFrequency): Long? = when (frequency) {
    RecurringFrequency.ONCE -> null
    RecurringFrequency.DAILY -> shiftDate(currentDate) { it.plusDays(1) }
    RecurringFrequency.WEEKLY -> shiftDate(currentDate) { it.plusWeeks(1) }
    RecurringFrequency.BIWEEKLY -> shiftDate(currentDate) { it.plusWeeks(2) }
    RecurringFrequency.MONTHLY -> shiftDate(currentDate) { it.plusMonths(1) }
    RecurringFrequency.QUARTERLY -> shiftDate(currentDate) { it.plusMonths(3) }
    RecurringFrequency.SEMIANNUAL -> shiftDate(currentDate) { it.plusMonths(6) }
    RecurringFrequency.YEARLY -> shiftDate(currentDate) { it.plusYears(1) }
}

/**
 * Génère, sans jamais toucher Room, la liste des dates d'échéance à transformer en occurrences
 * `PENDING` : toutes celles dont l'INSTANT exact de déclenchement ([triggerHour]/[triggerMinute]
 * combinées au jour via [combineDayAndTime]) est déjà passé par rapport à [nowEpochMillis] — couvre
 * à la fois l'échéance du jour, une fois son heure atteinte, ET toute échéance manquée si
 * l'utilisateur n'a pas ouvert l'app depuis plusieurs cycles (cahier des charges, section "Gestion
 * des dates manquées"). `RecurringTransactionRepository` persiste ensuite chaque date retournée en
 * `RecurringTransactionOccurrenceEntity` et avance [RecurringTransaction.nextExecutionDate] d'autant.
 *
 * [triggerHour]/[triggerMinute] par défaut à `0`/`0` (minuit) : préserve EXACTEMENT le comportement
 * historique (comparaison par simple JOUR calendaire, `isAfterDay`) pour les DEUX appels internes de
 * `RecurringTransactionRepositoryImpl` qui se testent contre EUX-MÊMES (`hasMoreOccurrences`,
 * `deactivateIfPastEndDate`) — ces deux-là ne veulent tester QUE la borne [endDate], jamais l'heure
 * de déclenchement ; leur passer l'heure réelle de la règle ferait échouer ce test pour absolument
 * toute règle dont l'heure n'est pas 00:00 (l'instant combiné serait alors TOUJOURS postérieur à la
 * référence auto-comparée). Seul `RecurringTransactionRepositoryImpl.generateMissingOccurrences`
 * (l'appel qui détermine "qu'est-ce qui est dû MAINTENANT") doit passer `rule.triggerHour`/
 * `rule.triggerMinute` explicitement — voir sa doc pour le bug corrigé ("le dialogue de validation
 * s'affichait dès l'ouverture de l'app le jour même, avant l'heure configurée").
 *
 * S'arrête à [endDate] si renseignée (dernière date générée <= [endDate], comparaison de JOUR pure,
 * volontairement indépendante de l'heure de déclenchement — voir ci-dessus), et après une seule date
 * pour [RecurringFrequency.ONCE] (voir [computeNextExecutionDate], qui retourne alors `null`).
 *
 * [MAX_GENERATED_OCCURRENCES_PER_CALL] protège contre une règle restée inactive très longtemps
 * (ex. quotidienne depuis plusieurs années sans jamais avoir ouvert l'app) : au-delà, le reste de
 * l'historique manquant sera simplement généré aux ouvertures suivantes plutôt que de tout produire
 * d'un coup — voir instructions projet, "ne jamais dégrader les performances/le temps de démarrage".
 */
fun generateMissingScheduledDates(
    nextExecutionDate: Long,
    frequency: RecurringFrequency,
    endDate: Long?,
    nowEpochMillis: Long,
    triggerHour: Int = 0,
    triggerMinute: Int = 0
): List<Long> {
    val dates = mutableListOf<Long>()
    var candidate: Long? = nextExecutionDate
    while (dates.size < MAX_GENERATED_OCCURRENCES_PER_CALL) {
        val date = candidate ?: break
        if (combineDayAndTime(date, triggerHour, triggerMinute) > nowEpochMillis) break
        if (endDate != null && isAfterDay(date, endDate)) break
        dates.add(date)
        candidate = if (frequency == RecurringFrequency.ONCE) null else computeNextExecutionDate(date, frequency)
    }
    return dates
}

private const val MAX_GENERATED_OCCURRENCES_PER_CALL = 500

private fun shiftDate(epochMillis: Long, transform: (LocalDate) -> LocalDate): Long {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    return transform(date).atStartOfDay(zone).toInstant().toEpochMilli()
}

/** Compare des JOURS calendaires, pas des millisecondes brutes — même principe que
 * [isPastDueDay] : `true` si le jour de [date] est postérieur au jour de [referenceEpochMillis]. */
private fun isAfterDay(date: Long, referenceEpochMillis: Long): Boolean {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()
    val referenceDay = Instant.ofEpochMilli(referenceEpochMillis).atZone(zone).toLocalDate()
    return day.isAfter(referenceDay)
}
