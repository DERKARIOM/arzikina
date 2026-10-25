package com.naniger.arzikina.domain.shop

import java.util.Locale
import kotlin.random.Random

/**
 * Référence d'affichage d'une vente : `V-AAAAMMJJ-XXXX` (4 caractères aléatoires sans ambiguïté
 * visuelle). Lisible sur le reçu et dans l'historique.
 *
 * Ce n'est PAS l'identité de la vente : celle-ci est son `syncId` (UUID, unique). Deux appareils
 * hors ligne peuvent, très rarement, produire la même référence ; aucun index unique ne la
 * contraint (sinon une ligne reçue de la synchronisation ferait échouer l'insertion).
 */
object SaleReference {

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val SUFFIX_LENGTH = 4

    fun generate(year: Int, month: Int, day: Int, random: Random = Random.Default): String {
        require(year in 1970..9999 && month in 1..12 && day in 1..31) { "Date invalide" }
        val suffix = buildString { repeat(SUFFIX_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        return String.format(Locale.ROOT, "V-%04d%02d%02d-%s", year, month, day, suffix)
    }
}
