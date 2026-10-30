package com.naniger.arzikina.domain.currency

/**
 * Catalogue structuré et extensible des devises (codes ISO 4217).
 *
 * Pour ajouter une devise : ajouter UNE ligne à [ALL]. Aucun autre fichier n'est à modifier.
 * Une devise absente du catalogue (ex. ancien code importé) reste affichable : voir [infoOrFallback].
 */
object CurrencyCatalog {

    /** Devise proposée par défaut (Niger / UEMOA) ; ne doit jamais être imposée (voir [defaultFor]). */
    const val DEFAULT_CODE = "XOF"

    private fun c(code: String, symbol: String = code, digits: Int = 2, region: String = "", featured: Boolean = false) =
        CurrencyInfo(code, symbol, digits, region, featured)

    /** Toutes les devises connues. Les devises « mises en avant » sont listées en premier par [all]. */
    private val ALL: List<CurrencyInfo> = listOf(
        // Mises en avant (cahier des charges)
        c("XOF", "FCFA", 0, "Niger Mali Sénégal Burkina Bénin Togo Côte d'Ivoire Guinée-Bissau UEMOA BCEAO", true),
        c("XAF", "FCFA", 0, "Cameroun Gabon Tchad Congo Centrafrique Guinée équatoriale CEMAC BEAC", true),
        c("EUR", "€", 2, "Zone euro Europe", true),
        c("USD", "$", 2, "États-Unis", true),
        c("GNF", "GNF", 0, "Guinée", true),
        c("NGN", "₦", 2, "Nigeria", true),
        c("GHS", "GH₵", 2, "Ghana", true),
        c("MAD", "MAD", 2, "Maroc", true),
        c("GBP", "£", 2, "Royaume-Uni", true),
        c("CAD", "CA$", 2, "Canada", true),
        c("CHF", "CHF", 2, "Suisse", true),
        // Afrique
        c("DZD", region = "Algérie"), c("TND", digits = 3, region = "Tunisie"), c("EGP", "E£", region = "Égypte"),
        c("LYD", digits = 3, region = "Libye"), c("MRU", region = "Mauritanie"), c("GMD", region = "Gambie"),
        c("SLE", region = "Sierra Leone"), c("LRD", region = "Liberia"), c("CVE", region = "Cap-Vert"),
        c("CDF", "FC", region = "RD Congo"), c("RWF", digits = 0, region = "Rwanda"), c("BIF", digits = 0, region = "Burundi"),
        c("KMF", digits = 0, region = "Comores"), c("DJF", digits = 0, region = "Djibouti"), c("MGA", region = "Madagascar"),
        c("MUR", region = "Maurice"), c("KES", "KSh", region = "Kenya"), c("UGX", digits = 0, region = "Ouganda"),
        c("TZS", "TSh", region = "Tanzanie"), c("ETB", region = "Éthiopie"), c("ZAR", "R", region = "Afrique du Sud"),
        c("ZMW", region = "Zambie"), c("AOA", region = "Angola"), c("MZN", region = "Mozambique"),
        c("SDG", region = "Soudan"), c("STN", region = "Sao Tomé-et-Principe"),
        // Amériques
        c("MXN", "MX$", region = "Mexique"), c("BRL", "R$", region = "Brésil"), c("ARS", region = "Argentine"),
        c("CLP", digits = 0, region = "Chili"), c("COP", region = "Colombie"), c("PEN", region = "Pérou"),
        c("HTG", region = "Haïti"), c("DOP", region = "République dominicaine"),
        // Asie / Moyen-Orient / Océanie
        c("JPY", "¥", 0, "Japon"), c("CNY", "CN¥", 2, "Chine"), c("INR", "₹", 2, "Inde"), c("PKR", region = "Pakistan"),
        c("BDT", region = "Bangladesh"), c("KRW", "₩", 0, "Corée du Sud"), c("IDR", "Rp", 2, "Indonésie"),
        c("MYR", region = "Malaisie"), c("SGD", "S$", 2, "Singapour"), c("THB", "฿", 2, "Thaïlande"),
        c("VND", "₫", 0, "Viêt Nam"), c("PHP", "₱", 2, "Philippines"), c("TRY", "₺", 2, "Turquie"),
        c("AED", region = "Émirats arabes unis"), c("SAR", region = "Arabie saoudite"), c("QAR", region = "Qatar"),
        c("KWD", digits = 3, region = "Koweït"), c("BHD", digits = 3, region = "Bahreïn"), c("OMR", digits = 3, region = "Oman"),
        c("JOD", digits = 3, region = "Jordanie"), c("LBP", region = "Liban"), c("ILS", "₪", 2, "Israël"),
        c("AUD", "A$", 2, "Australie"), c("NZD", "NZ$", 2, "Nouvelle-Zélande"), c("XPF", "CFP", 0, "Polynésie française Nouvelle-Calédonie"),
        // Europe (hors zone euro)
        c("SEK", "kr", 2, "Suède"), c("NOK", "kr", 2, "Norvège"), c("DKK", "kr", 2, "Danemark"),
        c("PLN", "zł", 2, "Pologne"), c("CZK", "Kč", 2, "Tchéquie"), c("RUB", "₽", 2, "Russie")
    )

    private val byCode: Map<String, CurrencyInfo> = ALL.associateBy { it.code }

    /** Devises mises en avant d'abord (ordre du cahier des charges), puis le reste par code. */
    val all: List<CurrencyInfo> =
        ALL.filter { it.isFeatured } + ALL.filterNot { it.isFeatured }.sortedBy { it.code }

    fun isKnown(code: String): Boolean = byCode.containsKey(code.uppercase())

    fun find(code: String): CurrencyInfo? = byCode[code.uppercase()]

    /**
     * Toujours une valeur : une devise inconnue (code importé d'une ancienne version…) s'affiche
     * avec son code comme symbole et 2 décimales, jamais d'exception.
     */
    fun infoOrFallback(code: String): CurrencyInfo =
        find(code) ?: CurrencyInfo(code = code.uppercase(), symbol = code.uppercase(), fractionDigits = 2)

    /**
     * Recherche insensible à la casse et aux accents sur le code, le symbole, la région et le
     * [displayName] fourni par l'appelant (le nom traduit vit dans la couche présentation).
     * Requête vide → toutes les devises.
     */
    fun search(query: String, displayName: (CurrencyInfo) -> String = { "" }): List<CurrencyInfo> {
        val q = normalize(query)
        if (q.isEmpty()) return all
        return all.filter { info ->
            normalize(info.code).contains(q) ||
                normalize(info.symbol).contains(q) ||
                normalize(info.region).contains(q) ||
                normalize(displayName(info)).contains(q)
        }
    }

    /**
     * Code à PERSISTER pour un choix de l'utilisateur : le code (en majuscules) s'il appartient au catalogue,
     * sinon [DEFAULT_CODE]. Garantit qu'aucune valeur arbitraire n'entre dans le profil ni dans la synchronisation.
     */
    fun sanitize(code: String?): String =
        code?.trim()?.uppercase()?.takeIf { byCode.containsKey(it) } ?: DEFAULT_CODE

    /**
     * Devise PRÉSÉLECTIONNÉE à l'inscription d'après le pays de l'appareil (code ISO 3166, ex. « NE »).
     * Ne dépend jamais de la langue de l'application ; l'utilisateur reste libre de choisir autre chose.
     * Pays inconnu ou absent → [DEFAULT_CODE].
     */
    fun defaultFor(countryCode: String?): String =
        COUNTRY_DEFAULTS[countryCode?.uppercase()] ?: DEFAULT_CODE

    private val COUNTRY_DEFAULTS: Map<String, String> = buildMap {
        listOf("NE", "ML", "SN", "BF", "BJ", "TG", "CI", "GW").forEach { put(it, "XOF") }
        listOf("CM", "GA", "TD", "CG", "CF", "GQ").forEach { put(it, "XAF") }
        put("GN", "GNF"); put("NG", "NGN"); put("GH", "GHS"); put("MA", "MAD")
        put("GB", "GBP"); put("CA", "CAD"); put("CH", "CHF"); put("US", "USD")
        listOf("FR", "DE", "ES", "IT", "BE", "NL", "PT", "IE", "AT", "FI", "GR", "LU").forEach { put(it, "EUR") }
    }

    private fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
}
