package com.naniger.arzikina.domain.issuedreceipt

/** Erreurs métier du repository des reçus émis (voir `IssuedReceiptRepository`). */
sealed class IssuedReceiptException(message: String) : Exception(message) {

    /** Un ou plusieurs champs invalides, voir [errors]. */
    class Invalid(val errors: Set<IssuedReceiptError>) :
        IssuedReceiptException("Reçu invalide : $errors")

    /** Ce numéro est déjà attribué à un autre reçu de l'utilisateur (y compris supprimé : jamais réutilisé). */
    class DuplicateNumber(val number: String) :
        IssuedReceiptException("Numéro de reçu déjà utilisé : $number")

    class NotFound(val id: Long) : IssuedReceiptException("Reçu introuvable : $id")
}
