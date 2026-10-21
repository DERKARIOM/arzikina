package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.issuedreceipt.LogoChange
import com.naniger.arzikina.domain.issuedreceipt.SignatureChange
import com.naniger.arzikina.domain.issuedreceipt.SignatureImage
import com.naniger.arzikina.domain.model.IssuerProfile
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès au profil professionnel de l'émetteur de reçus (un seul par utilisateur, utilisé
 * pour pré-remplir chaque nouveau reçu). Limité à l'utilisateur connecté.
 */
interface IssuerProfileRepository {

    /** Profil courant, `null` tant qu'il n'a jamais été enregistré. */
    fun observeProfile(): Flow<IssuerProfile?>

    suspend fun getProfile(): IssuerProfile?

    /**
     * Crée ou met à jour le profil. Le champ [IssuerProfile.logoFileName] de [profile] est IGNORÉ :
     * le logo ne change que via [logo]. Le nouveau logo est traité (validé, redimensionné) AVANT toute
     * écriture ; l'ancien fichier n'est supprimé qu'une fois le profil enregistré, donc une erreur ne
     * fait jamais perdre le logo existant.
     *
     * [signature] suit la même logique mais l'ancien fichier de signature n'est JAMAIS supprimé : les
     * reçus déjà signés en gardent une copie figée dont ils ont besoin pour régénérer leur PDF.
     *
     * @throws com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptException.Invalid champs invalides
     * @throws com.naniger.arzikina.domain.issuedreceipt.LogoImportException logo refusé
     */
    suspend fun saveProfile(
        profile: IssuerProfile,
        logo: LogoChange = LogoChange.Keep,
        signature: SignatureChange = SignatureChange.Keep
    )

    /**
     * Vérifie qu'une image peut servir de logo (type réel, taille, lisibilité) SANS rien enregistrer :
     * permet de refuser tout de suite un mauvais fichier choisi.
     *
     * @throws com.naniger.arzikina.domain.issuedreceipt.LogoImportException
     */
    suspend fun validateLogo(sourceUri: String)

    /**
     * Transforme une image choisie en signature prête à stocker (fond blanc transparent, recadrée) SANS
     * rien enregistrer : permet d'afficher le résultat tout de suite et de refuser un mauvais fichier.
     *
     * @throws com.naniger.arzikina.domain.issuedreceipt.LogoImportException
     */
    suspend fun prepareSignature(sourceUri: String): SignatureImage

    /**
     * Idem pour un tracé à main levée, fourni en PNG transparent par le pad de dessin.
     *
     * @throws com.naniger.arzikina.domain.issuedreceipt.LogoImportException
     */
    suspend fun prepareDrawnSignature(png: ByteArray): SignatureImage

    /** Fichier image mémorisé [fileName] (logo OU signature : même dossier), `null` s'il n'existe plus. */
    fun logoFile(fileName: String): java.io.File?
}
