package com.arzikina.ne.util

import java.time.LocalDate

/**
 * Regroupement générique d'une liste datée sous un jour donné — "Aujourd'hui"/"Hier"/une date
 * précise. Volontairement sans texte affichable : la couche presentation (qui a accès aux
 * ressources) est seule responsable de traduire [Today]/[Yesterday] en libellé localisé, un
 * ViewModel ne devant jamais dépendre d'un `Context` Android.
 *
 * Partagé entre TOUTES les listes de l'application groupées par jour — initialement propre à
 * `presentation/transactions/TransactionDayGrouping.kt` (Transactions, "Détail du compte"), déplacé
 * ici lors de l'ajout de "Gestion des reçus" (voir `presentation/utilities/receipts/ReceiptDayGrouping.kt`)
 * pour éviter de dupliquer ce type entre les deux fonctionnalités — seule la fonction de
 * regroupement elle-même (`groupByDay`, propre au type d'élément listé) reste distincte par
 * fonctionnalité, voir chacun de ces deux fichiers.
 */
sealed interface DayLabel {
    data object Today : DayLabel
    data object Yesterday : DayLabel
    data class Other(val date: LocalDate) : DayLabel
}
