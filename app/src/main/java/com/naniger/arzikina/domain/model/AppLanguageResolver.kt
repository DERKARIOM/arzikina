package com.naniger.arzikina.domain.model

/**
 * Règle de priorité des langues, en Kotlin pur (sans Android) pour pouvoir la tester unitairement :
 *
 * 1. Une langue choisie manuellement ([AppLanguage.FRENCH] / [AppLanguage.ENGLISH]) l'emporte
 *    toujours sur la langue du système.
 * 2. Sinon ([AppLanguage.SYSTEM]), on prend la PREMIÈRE langue supportée dans la liste des langues
 *    préférées du téléphone (Android permet d'en déclarer plusieurs, par ordre de préférence).
 * 3. Si aucune n'est supportée, on retombe sur [AppLanguage.DEFAULT] (français).
 *
 * Le point 2 reproduit exactement la résolution des ressources d'Android (API 24+) : le libellé
 * affiché dans Paramètres (« Langue du système (English) ») correspond donc toujours à la langue
 * réellement utilisée par l'interface.
 */
object AppLanguageResolver {

    /**
     * @param selected choix de l'utilisateur.
     * @param systemLanguageTags langues du téléphone par ordre de préférence (ex. `["ha-NG", "fr-NE"]`).
     * @return une langue réellement supportée, jamais [AppLanguage.SYSTEM].
     */
    fun resolve(selected: AppLanguage, systemLanguageTags: List<String>): AppLanguage {
        if (selected != AppLanguage.SYSTEM) return selected
        return systemLanguageTags.firstNotNullOfOrNull(AppLanguage::fromLanguageTag) ?: AppLanguage.DEFAULT
    }
}
