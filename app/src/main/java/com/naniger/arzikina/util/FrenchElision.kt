package com.naniger.arzikina.util

import java.text.Normalizer

/**
 * Élision française devant un nom propre : « de la part d'Aïcha », mais « de la part de Moussa ».
 *
 * Règle volontairement SIMPLE et prévisible : élision devant une VOYELLE (accents ignorés :
 * « Ève », « Ïsa », « Ousmane »…). Le « h » et le « y » ne déclenchent PAS d'élision : ils sont
 * le plus souvent aspirés/consonantiques dans les prénoms courants d'Afrique de l'Ouest
 * (« Halima », « Hamidou », « Yacouba »), et une élision fautive (« d'Hamidou ») choque plus
 * qu'une absence d'élision. Fonction PURE, testée isolément (voir `FrenchElisionTest`).
 */
object FrenchElision {

    private const val VOWELS = "aeiou"

    /** `true` si [word] (espaces de tête ignorés) commence par une voyelle, accentuée ou non. */
    fun requiresElision(word: String): Boolean {
        val first = word.trimStart().firstOrNull() ?: return false
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD)
            .firstOrNull()
            ?.lowercaseChar()
            ?: return false
        return base in VOWELS
    }
}
