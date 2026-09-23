package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.model.AppLanguage

/**
 * Langue de l'interface. Le domaine ignore COMMENT elle est appliquée et persistée (AppCompat,
 * `LocaleManager` d'Android 13+, DataStore) : voir
 * [com.naniger.arzikina.data.locale.AppLanguageRepositoryImpl].
 *
 * Réglage PAR APPAREIL (comme le verrou biométrique), non synchronisé entre appareils : un
 * utilisateur peut légitimement vouloir son téléphone en anglais et sa tablette en français. Une
 * synchronisation pourra être ajoutée plus tard sans changer ce contrat.
 */
interface AppLanguageRepository {

    /** Choix de l'utilisateur, [AppLanguage.SYSTEM] tant qu'il n'a jamais choisi. */
    fun getSelectedLanguage(): AppLanguage

    /** Langue réellement affichée (jamais [AppLanguage.SYSTEM]), voir
     *  [com.naniger.arzikina.domain.model.AppLanguageResolver]. */
    fun getEffectiveLanguage(): AppLanguage

    /**
     * Enregistre puis applique [language]. L'interface se met à jour immédiatement : l'écran
     * courant est reconstruit par Android sans redémarrer l'application ni perdre la navigation.
     */
    suspend fun setLanguage(language: AppLanguage)

    /** À appeler UNE fois au démarrage du processus (voir `ArzikinaApplication.onCreate`), avant
     *  la création de toute Activity : réapplique le dernier choix enregistré. */
    fun restoreOnStartup()
}
