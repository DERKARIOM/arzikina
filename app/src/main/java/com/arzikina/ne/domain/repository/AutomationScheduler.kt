package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.RecurringTransaction

/**
 * Programme/annule le déclenchement précis (heure exacte, voir [RecurringTransaction.triggerHour]/
 * [RecurringTransaction.triggerMinute]) d'une automatisation — voir cahier des charges "Ajouter
 * l'heure de déclenchement à Automatisation", section 15 : "créer un composant/service dédié,
 * responsable de programmer/reprogrammer/annuler/restaurer les déclenchements", jamais depuis un
 * Fragment/ViewModel.
 *
 * EXCEPTION à la règle "le domaine n'importe jamais de type Android" (voir [BiometricAuthenticator]
 * pour la même dérogation délibérée) : cette interface reste néanmoins nécessaire dans le domaine
 * pour qu'`AutomationSchedulerImpl` (voir `com.arzikina.ne.work`, seule implémentation permise à
 * dépendre d'`AlarmManager`) soit injectable dans `RecurringTransactionRepositoryImpl` sans que ce
 * dernier ne connaisse directement Android — [RecurringTransaction] reste un modèle domaine pur, ce
 * qui suffit à garder cette interface elle-même sans import Android.
 */
interface AutomationScheduler {

    /**
     * Annule toute alarme déjà programmée pour `rule.id`, PUIS programme la nouvelle échéance à
     * `rule.nextTriggerInstant()` si `rule.isActive` — ne programme rien sinon (règle désactivée).
     *
     * Point d'entrée UNIQUE pour créer/mettre à jour/réactiver/désactiver la programmation d'une
     * règle (voir `RecurringTransactionRepositoryImpl`) : annuler-puis-reprogrammer inconditionnellement
     * évite tout doublon, y compris lors d'une simple modification de l'heure (cahier des charges,
     * sections 6/7 : "il ne doit jamais y avoir deux notifications pour la même automatisation").
     */
    fun schedule(rule: RecurringTransaction)

    /**
     * Annule l'alarme programmée pour la règle [recurringTransactionId] sans en reprogrammer une
     * nouvelle — réservé à la suppression DÉFINITIVE d'une règle (voir [schedule] pour tous les
     * autres cas, y compris la désactivation). Ne lève jamais d'exception si aucune alarme
     * n'était programmée.
     */
    fun cancel(recurringTransactionId: Long)

    /**
     * Reprogramme la totalité de [activeRules] — utilisé après un redémarrage du téléphone (voir
     * `com.arzikina.ne.work.BootCompletedReceiver`), les alarmes `AlarmManager` ne survivant jamais
     * à un redémarrage. Équivalent à appeler [schedule] pour chacune.
     */
    fun rescheduleAll(activeRules: List<RecurringTransaction>)
}
