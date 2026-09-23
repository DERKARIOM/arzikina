package com.naniger.arzikina.presentation.components

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

/**
 * Construit et affiche un [MaterialTimePicker] préconfiguré, en respectant le réglage 12h/24h de
 * l'appareil (voir [android.text.format.DateFormat.is24HourFormat]) — même logique que
 * `RecurringTransactionFormFragment.showTimePicker`, extraite ici pour être réutilisée sans
 * dupliquer cette configuration (voir cahier des charges "Marketplace personnelle", extension
 * "Heure par défaut").
 *
 * N'est PAS branché sur `TransactionFormFragment`/`RecurringTransactionFormFragment` : ces deux
 * écrans conservent leur implémentation actuelle inchangée (aucune régression) — ce helper ne sert
 * que du nouveau code (`MarketplaceFormFragment`), pour éviter d'introduire une troisième copie de
 * cette même configuration `MaterialTimePicker`.
 */
object TimePickerHelper {

    fun show(
        context: Context,
        fragmentManager: FragmentManager,
        initialHour: Int,
        initialMinute: Int,
        titleText: String,
        tag: String = "time_picker",
        onTimeSelected: (hour: Int, minute: Int) -> Unit
    ) {
        val clockFormat = if (android.text.format.DateFormat.is24HourFormat(context)) {
            TimeFormat.CLOCK_24H
        } else {
            TimeFormat.CLOCK_12H
        }
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(clockFormat)
            .setHour(initialHour)
            .setMinute(initialMinute)
            .setTitleText(titleText)
            .build()
        picker.addOnPositiveButtonClickListener {
            onTimeSelected(picker.hour, picker.minute)
        }
        picker.show(fragmentManager, tag)
    }
}
