package com.naniger.arzikina.domain.issuedreceipt

/** Échec d'import d'une image de logo OU de signature ; [reason] est traduit en message FR/EN par la présentation. */
class LogoImportException(val reason: Reason, cause: Throwable? = null) : Exception("Import du logo refusé : $reason", cause) {

    enum class Reason {
        /** Format non pris en charge (autre que PNG, JPEG, WebP) ou fichier qui n'est pas une image. */
        UNSUPPORTED_TYPE,

        /** Fichier trop lourd ou image trop grande. */
        TOO_LARGE,

        /** Fichier illisible ou corrompu. */
        UNREADABLE,

        /** Signature importée sans aucune encre détectée (image vide ou entièrement blanche). */
        EMPTY
    }
}
