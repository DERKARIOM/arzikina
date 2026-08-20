package com.arzikina.ne.util

/**
 * Constantes globales de l'application. Centralisées ici pour éviter les
 * chaînes/valeurs magiques dupliquées dans plusieurs fichiers.
 */
object Constants {
    const val DATABASE_NAME = "arzikina.db"

    /** Devise proposée par défaut à la première installation (contexte ouest-africain). */
    const val DEFAULT_CURRENCY_CODE = "XOF"

    /** Nom de fichier suggéré par défaut lors d'un export (voir `presentation/settings/BackupFragment`). */
    const val DEFAULT_BACKUP_FILE_NAME = "arzikina_backup.json"

    /**
     * Authority du `FileProvider` déclaré dans `AndroidManifest.xml` (voir aussi
     * `res/xml/file_paths.xml`) — cahier des charges "Gestion des reçus", section 9 : un reçu PDF
     * n'est jamais partagé via un chemin de fichier privé exposé directement, toujours via une URI
     * `content://` sécurisée (voir `data/receipts/ReceiptFileStorage.contentUriFor`). `${applicationId}`
     * dans le manifeste et [android.content.Context.getPackageName] à l'exécution désignent la même
     * valeur (`com.arzikina.ne`, voir `app/build.gradle.kts`) — centralisé ici pour ne jamais laisser
     * les deux bouts diverger silencieusement.
     */
    const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * Type MIME des reçus (voir `AndroidManifest.xml`, `<data android:mimeType="...">` — la même
     * valeur y est dupliquée en dur, XML ne pouvant pas référencer cette constante Kotlin) —
     * centralisé ici pour tout le code Kotlin qui en a besoin : réception du partage
     * ([com.arzikina.ne.MainActivity]), import manuel (sélecteur de fichiers, à venir).
     */
    const val RECEIPT_MIME_TYPE = "application/pdf"

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
