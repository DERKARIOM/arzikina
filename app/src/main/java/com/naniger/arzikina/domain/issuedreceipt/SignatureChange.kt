package com.naniger.arzikina.domain.issuedreceipt

/**
 * Image de signature DÉJÀ TRAITÉE (fond transparent, recadrée, redimensionnée), prête à être stockée.
 * Produite par [com.naniger.arzikina.domain.repository.IssuerProfileRepository.prepareSignature] ou
 * `prepareDrawnSignature` : elle ne peut donc pas échouer à l'enregistrement.
 *
 * Classe ordinaire (pas `data class`) : l'égalité sur un tableau d'octets serait trompeuse ; deux
 * instances sont distinctes même à contenu égal, ce qui suffit pour détecter un changement.
 */
class SignatureImage(val pngBytes: ByteArray)

/** Ce que l'utilisateur veut faire de la signature du profil lors de l'enregistrement. */
sealed interface SignatureChange {

    /** Conserver la signature actuelle (ou l'absence de signature). */
    data object Keep : SignatureChange

    /** Retirer la signature du profil (les reçus déjà signés gardent leur copie). */
    data object Remove : SignatureChange

    /** Remplacer par [image]. */
    data class Replace(val image: SignatureImage) : SignatureChange
}
