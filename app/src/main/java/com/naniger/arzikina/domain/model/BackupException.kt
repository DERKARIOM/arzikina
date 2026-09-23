package com.naniger.arzikina.domain.model

/**
 * Échecs PRÉVISIBLES d'une restauration, que l'utilisateur doit comprendre (et peut corriger).
 *
 * Le domaine exprime la CAUSE, jamais le texte affiché : la présentation choisit le message
 * traduit (voir `BackupViewModel`). Les messages passés à [Exception] sont techniques (journaux),
 * jamais montrés à l'utilisateur. Toute autre exception (disque plein, fichier illisible…) reste
 * une erreur inattendue, affichée avec un message générique.
 */
sealed class BackupException(technicalMessage: String, cause: Throwable? = null) :
    Exception(technicalMessage, cause) {

    /** Le fichier a été produit par une version plus récente d'Arzikina. */
    class NewerSchemaVersion : BackupException("Backup schema version is newer than supported")

    /** Le fichier n'est pas une sauvegarde Arzikina valide (JSON corrompu ou d'un autre format). */
    class InvalidFile(cause: Throwable) : BackupException("Backup file cannot be parsed", cause)

    /** Le nom d'utilisateur du fichier appartient déjà à un AUTRE compte de cet appareil. */
    class UsernameConflict(val username: String) :
        BackupException("Username already used by another account on this device")

    /** L'adresse e-mail du fichier appartient déjà à un AUTRE compte de cet appareil. */
    class EmailConflict(val email: String) :
        BackupException("Email already used by another account on this device")
}
