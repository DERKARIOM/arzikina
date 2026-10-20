package com.naniger.arzikina.data.repository

import androidx.room.withTransaction
import com.naniger.arzikina.data.issuedreceipt.IssuerLogoProcessor
import com.naniger.arzikina.data.issuedreceipt.IssuerLogoStorage
import com.naniger.arzikina.data.local.dao.IssuerProfileDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.local.entity.IssuerProfileEntity
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptException
import com.naniger.arzikina.domain.issuedreceipt.IssuerValidator
import com.naniger.arzikina.domain.issuedreceipt.LogoChange
import com.naniger.arzikina.domain.model.IssuerProfile
import com.naniger.arzikina.domain.repository.IssuerProfileRepository
import com.naniger.arzikina.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implémentation Room de [IssuerProfileRepository]. Pas encore de mise en file de synchronisation
 * (étape « Synchronisation ») : `syncId` reste `null`.
 *
 * Ordre sûr pour le logo : 1) traiter la nouvelle image (peut échouer, rien n'est encore écrit),
 * 2) écrire le nouveau fichier, 3) enregistrer la ligne en transaction, 4) seulement alors supprimer
 * l'ancien fichier. Si 3) échoue, le NOUVEAU fichier est supprimé et l'ancien logo reste intact : ni
 * perte, ni fichier orphelin.
 */
class IssuerProfileRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val profileDao: IssuerProfileDao,
    private val logoProcessor: IssuerLogoProcessor,
    private val logoStorage: IssuerLogoStorage,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IssuerProfileRepository {

    override fun observeProfile(): Flow<IssuerProfile?> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) flowOf(null) else profileDao.observeForUser(userId).map { it?.toDomain() }
        }

    override suspend fun getProfile(): IssuerProfile? = withContext(ioDispatcher) {
        profileDao.getForUser(requireCurrentUserId())?.toDomain()
    }

    override suspend fun saveProfile(profile: IssuerProfile, logo: LogoChange) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val normalized = profile.normalized()
        val errors = IssuerValidator.validate(
            businessName = normalized.businessName,
            address = normalized.address,
            phone = normalized.phone,
            email = normalized.email,
            taxId = normalized.taxId,
            issuedByName = normalized.defaultIssuedByName,
            issuedByTitle = normalized.defaultIssuedByTitle
        )
        if (errors.isNotEmpty()) throw IssuedReceiptException.Invalid(errors)

        val newLogoFile = (logo as? LogoChange.Replace)?.let {
            val processed = logoProcessor.process(it.sourceUri)
            logoStorage.write(processed.bytes, processed.extension)
        }

        var oldLogoFile: String? = null
        try {
            database.withTransaction {
                val existing = profileDao.getForUser(userId)
                oldLogoFile = existing?.logoFileName
                val logoFileName = when (logo) {
                    LogoChange.Keep -> existing?.logoFileName
                    LogoChange.Remove -> null
                    is LogoChange.Replace -> newLogoFile
                }
                val now = System.currentTimeMillis()
                profileDao.upsert(
                    IssuerProfileEntity(
                        id = existing?.id ?: 0L,
                        userId = userId,
                        businessName = normalized.businessName,
                        address = normalized.address,
                        phone = normalized.phone,
                        email = normalized.email,
                        taxId = normalized.taxId,
                        logoFileName = logoFileName,
                        defaultIssuedByName = normalized.defaultIssuedByName,
                        defaultIssuedByTitle = normalized.defaultIssuedByTitle,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        syncId = existing?.syncId,
                        deletedAt = null,
                        version = existing?.version ?: 1
                    )
                )
            }
        } catch (e: Throwable) {
            newLogoFile?.let { logoStorage.delete(it) }
            throw e
        }

        // Transaction validée : l'ancien fichier n'est plus référencé par le profil.
        // Les instantanés de reçus déjà émis peuvent encore citer ce nom : l'étape PDF retombe alors
        // sur le logo ACTUEL du profil (décision d'audit n°2), ou n'en met aucun.
        oldLogoFile?.takeIf { it != newLogoFile && logo != LogoChange.Keep }?.let { logoStorage.delete(it) }
        Unit
    }

    private fun IssuerProfile.normalized() = copy(
        businessName = businessName.trim(),
        address = address.trim(),
        phone = phone.trim(),
        email = email.cleaned(),
        taxId = taxId.cleaned(),
        defaultIssuedByName = defaultIssuedByName.cleaned(),
        defaultIssuedByTitle = defaultIssuedByTitle.cleaned()
    )

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
