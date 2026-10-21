package com.naniger.arzikina.domain.model

/**
 * Profil professionnel de l'utilisateur, mémorisé pour pré-remplir chaque nouveau reçu émis (voir
 * [IssuedReceipt]). Une seule instance par utilisateur. Sa copie figée dans chaque reçu est
 * [IssuerSnapshot].
 *
 * [logoFileName] : nom du fichier du logo dans le stockage privé de l'application, jamais une URI
 * externe (voir l'étape « Profil émetteur et logo »).
 * [signatureFileName] : idem pour la signature manuscrite (même stockage que le logo) ; `null` = aucune.
 * [toSnapshot] ne la copie JAMAIS d'office : signer un reçu est un choix, fait à l'émission.
 */
data class IssuerProfile(
    val businessName: String,
    val address: String,
    val phone: String,
    val email: String? = null,
    val taxId: String? = null,
    val logoFileName: String? = null,
    val defaultIssuedByName: String? = null,
    val defaultIssuedByTitle: String? = null,
    val signatureFileName: String? = null
) {
    fun toSnapshot(): IssuerSnapshot = IssuerSnapshot(
        businessName = businessName,
        address = address,
        phone = phone,
        email = email,
        taxId = taxId,
        logoFileName = logoFileName
    )
}
