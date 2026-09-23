package com.naniger.arzikina.domain.model

/**
 * Langue de l'interface d'Arzikina, choisie par l'utilisateur dans Paramètres → Langue.
 *
 * [SYSTEM] n'est PAS une langue : c'est le choix « suivre la langue du téléphone », résolu en une
 * langue réellement supportée par [AppLanguageResolver]. [languageTag] est l'étiquette BCP 47
 * utilisée pour appliquer la langue (`null` pour [SYSTEM], qui correspond à « aucune langue
 * imposée »).
 *
 * Ajouter une langue = ajouter une entrée ici + un dossier `res/values-xx/` + une ligne dans
 * `res/xml/locales_config.xml` (et `localeFilters` dans `app/build.gradle.kts`). Aucune autre
 * modification n'est nécessaire : la détection, la persistance et l'écran de choix s'appuient
 * uniquement sur [supported].
 *
 * Indépendante de la devise ([UserPreferences.currencyCode]) : changer de langue ne modifie
 * JAMAIS la devise ni aucun montant.
 */
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    FRENCH("fr"),
    ENGLISH("en");

    companion object {
        /** Langue de référence d'Arzikina, et repli pour toute langue système non supportée
         *  (ex. ar-SA, ha-NG, es-ES). Correspond au dossier `res/values/` (sans qualificatif). */
        val DEFAULT: AppLanguage = FRENCH

        /** Langues réellement disponibles (hors [SYSTEM]), dans l'ordre d'affichage. */
        val supported: List<AppLanguage> = entries.filter { it != SYSTEM }

        /**
         * Associe une étiquette de langue (ex. "fr-NE", "en_NG", "EN") à une langue supportée,
         * en ne tenant compte QUE de la langue principale : la région n'influence jamais le
         * choix (fr-CA et fr-NI donnent [FRENCH], en-GB et en-NG donnent [ENGLISH]).
         * Retourne `null` si la langue n'est pas supportée ou si [tag] est vide.
         */
        fun fromLanguageTag(tag: String?): AppLanguage? {
            val primary = tag
                ?.trim()
                ?.substringBefore('-')
                ?.substringBefore('_')
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            return supported.firstOrNull { it.languageTag == primary }
        }
    }
}
