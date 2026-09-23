package com.naniger.arzikina.util

import android.text.Editable
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.widget.EditText

/**
 * Formatage EN DIRECT (pendant la frappe) d'un champ de saisie de montant : insère
 * progressivement les espaces de séparation des milliers ("1" → "10" → "100" → "1 000" →
 * "10 000"), curseur repositionné pour rester à l'endroit logique pour l'utilisateur, gestion du
 * copier/coller.
 *
 * Composant central UNIQUE : un seul point d'entrée ([attach]) réutilisé par tous les
 * formulaires du projet contenant un champ montant, à la place de
 * `doAfterTextChanged { text -> viewModel.onXxxChange(text) }`. Ne duplique aucune règle : le
 * regroupement par milliers délègue à [Money.groupThousands] (même algorithme que
 * [Money.formatForInput]), et la conversion finale en unité mineure reste, comme avant, la
 * responsabilité exclusive de [Money.parseToMinorUnits] au moment de la sauvegarde — cette classe
 * ne fait QUE de l'affichage, elle ne calcule et ne stocke jamais de valeur numérique.
 *
 * [Money.parseToMinorUnits] tolère déjà les espaces de milliers : la chaîne AFFICHÉE (avec
 * espaces) transmise à [onValueChanged] est donc directement utilisable, sans transformation
 * supplémentaire, partout où le code appelait auparavant `Money.parseToMinorUnits(state.xxxInput)`.
 */
class MoneyInputFormatter private constructor(
    private val editText: EditText,
    private val onValueChanged: (String) -> Unit
) : TextWatcher {

    private var isFormatting = false
    private var deleteExtraDigitIndex: Int? = null
    private var isBulkInsert = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // Un backspace qui supprime uniquement l'espace de séparation ne doit pas être un
        // no-op visuel pour l'utilisateur : on supprime aussi le chiffre juste avant cet
        // espace (voir afterTextChanged), comme le ferait un champ montant natif.
        deleteExtraDigitIndex = if (
            count == 1 && after == 0 && s != null && start < s.length && s[start] == GROUPING_SEPARATOR
        ) {
            (start - 1).takeIf { it >= 0 }
        } else {
            null
        }
        // Insertion de plusieurs caractères d'un coup (collage, autocomplétion) : traité comme
        // une valeur complète à normaliser, pas comme une frappe caractère par caractère (voir
        // afterTextChanged) — permet de coller "10000.00" et d'obtenir directement "10 000",
        // sans conserver des décimales nulles que l'utilisateur n'a pas explicitement tapées.
        isBulkInsert = after > 1
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(editable: Editable) {
        if (isFormatting) return
        isFormatting = true
        try {
            var rawText = editable.toString()
            var cursor = editText.selectionStart.coerceIn(0, rawText.length)

            val extraIndex = deleteExtraDigitIndex
            if (extraIndex != null && extraIndex < rawText.length && rawText[extraIndex].isDigit()) {
                rawText = rawText.removeRange(extraIndex, extraIndex + 1)
                cursor = extraIndex
            }

            val cleaned = clean(rawText)

            val formatted: String
            val newCursor: Int
            if (isBulkInsert) {
                formatted = Money.parseToMinorUnits(cleaned)?.let(Money::formatForInput) ?: regroup(cleaned)
                newCursor = formatted.length
            } else {
                val significantBeforeCursor = countSignificant(rawText, cursor)
                formatted = regroup(cleaned)
                newCursor = positionForSignificantCount(formatted, significantBeforeCursor)
            }

            if (editable.toString() != formatted) {
                editable.replace(0, editable.length, formatted)
            }
            editText.setSelection(newCursor.coerceIn(0, formatted.length))
            onValueChanged(formatted)
        } finally {
            isFormatting = false
            deleteExtraDigitIndex = null
            isBulkInsert = false
        }
    }

    companion object {
        private const val GROUPING_SEPARATOR = ' '
        private const val ACCEPTED_CHARACTERS = "0123456789,. "

        /**
         * Attache le formatage en direct à [editText]. À appeler une seule fois par champ (ex.
         * dans `onViewCreated`), en remplacement de `doAfterTextChanged` :
         * ```
         * MoneyInputFormatter.attach(binding.amountInput) { viewModel.onAmountChange(it) }
         * ```
         *
         * Remplace le [android.text.method.KeyListener] du champ par un [DigitsKeyListener]
         * autorisant explicitement `0-9`, `,`, `.` ET l'espace. Nécessaire : `android:inputType`
         * "numberDecimal"/"number" (utilisé par tous les champs montant du projet) installe par
         * défaut un `DigitsKeyListener` qui agit AUSSI comme `InputFilter` — donc filtre tout
         * `Editable.replace(...)`, y compris ceux faits par CE watcher lui-même (pas seulement la
         * frappe utilisateur), et aurait sinon supprimé silencieusement les espaces de
         * regroupement qu'on essaie d'insérer. Le clavier affiché reste numérique : ce réglage ne
         * touche que les caractères acceptés, pas `inputType`/`imeOptions`.
         */
        fun attach(editText: EditText, onValueChanged: (String) -> Unit) {
            editText.keyListener = DigitsKeyListener.getInstance(ACCEPTED_CHARACTERS)
            editText.addTextChangedListener(MoneyInputFormatter(editText, onValueChanged))
        }

        /**
         * Ne conserve que les chiffres et UN SEUL séparateur décimal (`,` ou `.`, normalisé en
         * `,`), limité à 2 décimales — tout le reste (espaces, lettres, symbole de devise, second
         * séparateur...) est supprimé. Nettoie aussi bien une frappe caractère par caractère
         * qu'un texte collé.
         */
        internal fun clean(raw: String): String {
            val builder = StringBuilder()
            var seenSeparator = false
            var decimalsKept = 0
            for (c in raw) {
                when {
                    c.isDigit() && !seenSeparator -> builder.append(c)
                    c.isDigit() && seenSeparator && decimalsKept < 2 -> {
                        builder.append(c)
                        decimalsKept++
                    }
                    (c == ',' || c == '.') && !seenSeparator -> {
                        builder.append(',')
                        seenSeparator = true
                    }
                    else -> Unit
                }
            }
            return builder.toString()
        }

        /** Regroupe la partie entière de [cleaned] par milliers (voir [Money.groupThousands]). */
        internal fun regroup(cleaned: String): String {
            val separatorIndex = cleaned.indexOf(',')
            val integerPart = if (separatorIndex >= 0) cleaned.substring(0, separatorIndex) else cleaned
            val decimalPart = if (separatorIndex >= 0) cleaned.substring(separatorIndex + 1) else null
            val groupedInteger = Money.groupThousands(integerPart)
            return if (decimalPart != null) "$groupedInteger,$decimalPart" else groupedInteger
        }

        /** Nombre de chiffres/séparateur décimal dans [text] avant l'index [uptoExclusive]. */
        internal fun countSignificant(text: String, uptoExclusive: Int): Int =
            text.take(uptoExclusive).count { it.isDigit() || it == ',' || it == '.' }

        /**
         * Position, dans [formatted], juste après le [targetCount]-ième caractère significatif
         * (chiffre ou séparateur décimal) — les espaces de regroupement sont ignorés du comptage,
         * donc naturellement "sautés" par le curseur.
         */
        internal fun positionForSignificantCount(formatted: String, targetCount: Int): Int {
            if (targetCount <= 0) return 0
            var seen = 0
            for (i in formatted.indices) {
                if (formatted[i].isDigit() || formatted[i] == ',') {
                    seen++
                    if (seen == targetCount) return i + 1
                }
            }
            return formatted.length
        }
    }
}
