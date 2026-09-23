package com.naniger.arzikina.util

import android.content.Context
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Formats de date d'Arzikina, centralisés (remplacent 20 `DateTimeFormatter` dupliqués, dont 14
 * copies de "dd/MM/yyyy" et 6 formats textuels figés en `Locale.FRENCH`).
 *
 * Choix produit, cohérent avec les montants (voir [Money.AMOUNT_LOCALE]) : l'ORDRE jour/mois/année
 * est le même dans toutes les langues. C'est l'usage en Afrique de l'Ouest, francophone comme
 * anglophone (Nigeria, Ghana), et cela évite l'ambiguïté 03/04 du format américain. Seuls les NOMS
 * de mois changent selon la langue : « 23 septembre 2026 » / « 23 September 2026 ».
 *
 * Les formateurs textuels sont mis en cache PAR LOCALE (et non dans un `companion object` figé) :
 * après un changement de langue, le processus reste vivant mais les dates suivent la nouvelle
 * langue. [DateTimeFormatter] est immuable et thread-safe : un cache partagé est sans risque.
 */
object AppDateFormats {

    /** Date numérique « 23/09/2026 », identique dans toutes les langues (chiffres latins). */
    val NUMERIC_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    /** « 23 sept. » / « 23 Sep » */
    fun dayMonth(context: Context): DateTimeFormatter = cached("d MMM", context.appLocale())

    /** « 23 sept. 2026 » / « 23 Sep 2026 » */
    fun mediumDate(context: Context): DateTimeFormatter = cached("d MMM yyyy", context.appLocale())

    /** « 23 septembre 2026 » / « 23 September 2026 » */
    fun longDate(context: Context): DateTimeFormatter = cached("d MMMM yyyy", context.appLocale())

    /** « sept. » / « Sep » (axes des graphiques). */
    fun shortMonth(context: Context): DateTimeFormatter = cached("MMM", context.appLocale())

    private val cache = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

    private fun cached(pattern: String, locale: Locale): DateTimeFormatter =
        cache.getOrPut(pattern to locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}
