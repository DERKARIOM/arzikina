package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.issuedreceipt.LogoChange
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
     * @throws com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptException.Invalid champs invalides
     * @throws com.naniger.arzikina.domain.issuedreceipt.LogoImportException logo refusé
     */
    suspend fun saveProfile(profile: IssuerProfile, logo: LogoChange = LogoChange.Keep)

    /**
     * Vérifie qu'une image peut servir de logo (type réel, taille, lisibilité) SANS rien enregistrer :
     * permet de refuser tout de suite un mauvais fichier choisi.
     *
     * @throws com.naniger.arzikina.domain.issuedreceipt.LogoImportException
     */
    suspend fun validateLogo(sourceUri: String)

    /** Fichier du logo mémorisé [fileName] (pour l'afficher), `null` s'il n'existe plus. */
    fun logoFile(fileName: String): java.io.File?
}
