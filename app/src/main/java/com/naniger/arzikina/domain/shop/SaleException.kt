package com.naniger.arzikina.domain.shop

/** Raison pour laquelle une vente est refusée. Toujours levée AVANT toute écriture. */
sealed class SaleException(message: String) : Exception(message) {
    class EmptyCart : SaleException("Panier vide")

    /** Produit supprimé, archivé ou d'une autre boutique. */
    class ProductUnavailable(val productId: Long) : SaleException("Produit indisponible : $productId")

    /** Quantité nulle, trop grande ou fractionnaire pour une unité entière. */
    class InvalidQuantity(val productId: Long, val error: StockMovementError) :
        SaleException("Quantité invalide pour $productId : $error")

    /** Stock insuffisant : aucun stock négatif n'est jamais toléré. */
    class InsufficientStock(val productIds: List<Long>) : SaleException("Stock insuffisant : $productIds")

    /** Montant hors plafond ou remise invalide. */
    class InvalidAmount : SaleException("Montant invalide")

    /** Total nul : une vente doit encaisser un montant strictement positif. */
    class ZeroTotal : SaleException("Total nul")

    class AccountUnavailable : SaleException("Compte d'encaissement indisponible")

    /** Seul un compte de type Boutique peut encaisser une vente (voir [ShopAccountPolicy]). */
    class NotShopAccount : SaleException("Seul un compte de type Boutique peut encaisser une vente")

    class CurrencyMismatch : SaleException("Devise du compte différente de celle de la boutique")

    class ShopNotReady : SaleException("Boutique non initialisée")

    /** Reçu ou annulation demandés pour une vente qui n'est pas (ou plus) validée. */
    class NotConfirmed(val saleId: Long) : SaleException("Vente non validée : $saleId")

    /** Le profil de l'émetteur (nom, adresse, téléphone) est absent ou incomplet : aucun reçu possible. */
    class IssuerProfileIncomplete : SaleException("Profil de l'émetteur incomplet")

    class NotFound(val saleId: Long) : SaleException("Vente introuvable : $saleId")

    class NoSession : SaleException("Aucune session")
}
