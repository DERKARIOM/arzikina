package com.naniger.arzikina.domain.model

/**
 * Refus métier liés à « Transformer un prêt/emprunt en cadeau » (voir `LoanGift.kt`) — exceptions
 * du domaine (même principe que [TemplateAlreadyLinkedException]) pour que la présentation puisse
 * distinguer un refus attendu d'une vraie erreur technique.
 */
sealed class LoanGiftException(val loanId: Long, message: String) : IllegalStateException(message) {

    /** La transformation n'est pas (ou plus) permise : dette déjà remboursée, déjà transformée, ou
     * reste nul — voir [canConvertToGift]. Peut arriver si l'état a changé entre l'affichage de
     * l'action et sa confirmation (ex. remboursement arrivé par synchronisation). */
    class NotConvertible(loanId: Long) :
        LoanGiftException(loanId, "Ce prêt/emprunt ne peut plus être transformé en cadeau.")

    /** Opération refusée sur un prêt/emprunt DÉJÀ transformé en cadeau (ajout/suppression d'un
     * remboursement, modification du montant/compte/personne/type) : elle désynchroniserait le
     * reclassement décaissement + cadeau (voir la doc de `LoanGift.kt`). */
    class Locked(loanId: Long) :
        LoanGiftException(loanId, "Ce prêt/emprunt a été transformé en cadeau : il ne peut plus être modifié ainsi.")
}
