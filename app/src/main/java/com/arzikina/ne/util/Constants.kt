package com.arzikina.ne.util

/**
 * Constantes globales de l'application. Centralisées ici pour éviter les
 * chaînes/valeurs magiques dupliquées dans plusieurs fichiers.
 */
object Constants {
    const val DATABASE_NAME = "arzikina.db"

    /** Devise proposée par défaut à la première installation (contexte ouest-africain). */
    const val DEFAULT_CURRENCY_CODE = "XOF"

    /**
     * Compte créé automatiquement par [com.arzikina.ne.data.local.database.MIGRATION_6_7]
     * pour prendre possession des données existantes lors de la mise à jour
     * d'une installation antérieure à l'authentification (voir cette
     * migration pour le détail). Identifiants volontairement simples et
     * documentés ici : à ce stade du projet, la seule personne concernée par
     * cette migration est l'équipe de développement elle-même — ce mot de
     * passe est à changer depuis l'écran Profil dès que l'écran Connexion
     * existe (voir la feuille de route Authentification).
     */
    const val LEGACY_DEFAULT_USER_FULL_NAME = "Utilisateur Arzikina"
    const val LEGACY_DEFAULT_USER_USERNAME = "utilisateur"
    const val LEGACY_DEFAULT_USER_EMAIL = "utilisateur@arzikina.local"
    const val LEGACY_DEFAULT_USER_PASSWORD = "arzikina2026"
}
