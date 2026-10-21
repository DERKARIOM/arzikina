package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Profil professionnel de l'émetteur de reçus (voir
 * [com.naniger.arzikina.domain.model.IssuerProfile]). Au plus UNE ligne par [userId] (index
 * unique, même principe que [UserPreferencesEntity]) : créée à la première sauvegarde, puis mise à
 * jour. Aucun profil professionnel n'existait dans l'application, d'où cette table dédiée.
 *
 * [logoFileName] : fichier du logo dans le stockage privé de l'application (jamais une URI externe).
 * [signatureFileName] : fichier de la signature manuscrite (même stockage et mêmes règles de nom que le
 * logo, voir `IssuerLogoStorage`) ; `null` = pas de signature. Ajoutée en v35.
 *
 * Colonnes de synchronisation : voir [IssuedReceiptEntity]. [deletedAt] ne devrait jamais être
 * renseigné en pratique (on modifie le profil, on ne le supprime pas) mais reste présent par
 * cohérence avec le schéma générique attendu par le Sync Engine.
 */
@Entity(
    tableName = "issuer_profiles",
    indices = [Index(value = ["userId"], unique = true), Index(value = ["syncId"], unique = true)]
)
data class IssuerProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val businessName: String,
    val address: String,
    val phone: String,
    val email: String?,
    val taxId: String?,
    val logoFileName: String?,
    val defaultIssuedByName: String?,
    val defaultIssuedByTitle: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncId: String? = null,
    val deletedAt: Long? = null,
    val version: Int = 1,
    val signatureFileName: String? = null
)
