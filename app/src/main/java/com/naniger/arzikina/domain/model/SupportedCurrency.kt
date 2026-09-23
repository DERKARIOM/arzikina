package com.naniger.arzikina.domain.model

/**
 * Devises proposées dans les sélecteurs de l'application.
 *
 * [Account.currencyCode] reste un simple code ISO 4217 ([String]) pour rester
 * flexible ; cette liste n'est qu'un ensemble d'options courantes,
 * privilégiant le contexte ouest-africain. Elle sera remplacée par une
 * gestion complète des devises (taux de change, devise globale de l'app)
 * à l'étape "Paramètres".
 *
 * Le NOM de chaque devise (« Franc CFA (UEMOA) », « CFA franc (WAEMU) »…) est un texte d'interface :
 * il vit dans `strings.xml` (voir `SupportedCurrency.nameRes` côté présentation), pas ici. Le code
 * ISO et le symbole, eux, ne dépendent d'aucune langue.
 */
enum class SupportedCurrency(val code: String, val symbol: String) {
    XOF("XOF", "F CFA"),
    NGN("NGN", "₦"),
    GHS("GHS", "GH₵"),
    EUR("EUR", "€"),
    USD("USD", "$")
}
