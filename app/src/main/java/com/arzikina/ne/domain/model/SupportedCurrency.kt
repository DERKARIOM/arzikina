package com.arzikina.ne.domain.model

/**
 * Devises proposées dans les sélecteurs de l'application.
 *
 * [Account.currencyCode] reste un simple code ISO 4217 ([String]) pour rester
 * flexible ; cette liste n'est qu'un ensemble d'options courantes,
 * privilégiant le contexte ouest-africain. Elle sera remplacée par une
 * gestion complète des devises (taux de change, devise globale de l'app)
 * à l'étape "Paramètres".
 */
enum class SupportedCurrency(val code: String, val displayName: String, val symbol: String) {
    XOF("XOF", "Franc CFA (UEMOA)", "F CFA"),
    NGN("NGN", "Naira nigérian", "₦"),
    GHS("GHS", "Cedi ghanéen", "GH₵"),
    EUR("EUR", "Euro", "€"),
    USD("USD", "Dollar américain", "$")
}
