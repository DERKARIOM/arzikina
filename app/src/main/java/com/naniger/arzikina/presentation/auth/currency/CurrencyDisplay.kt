package com.naniger.arzikina.presentation.auth.currency

import android.content.Context
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.currency.CurrencyInfo
import java.util.Currency
import java.util.Locale

/**
 * Nom affiché d'une devise (le domaine ne contient aucun texte d'interface). Les francs CFA ont un libellé
 * dédié (les deux zones partagent le même symbole mais sont distinctes) ; les autres devises utilisent le nom
 * fourni par la plateforme dans la langue courante, avec le code ISO en repli.
 */
fun CurrencyInfo.displayName(context: Context): String = when (code) {
    "XOF" -> context.getString(R.string.currency_name_xof)
    "XAF" -> context.getString(R.string.currency_name_xaf)
    else -> runCatching { Currency.getInstance(code).getDisplayName(Locale.getDefault()) }
        .getOrNull()
        ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        ?: code
}

/** « Franc CFA BCEAO (FCFA) » — libellé des listes déroulantes de devise des formulaires. */
fun CurrencyInfo.pickerLabel(context: Context): String = "${displayName(context)} ($symbol)"
