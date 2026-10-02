package com.naniger.arzikina.data.local.database

import com.naniger.arzikina.data.local.dao.CategoryDao
import com.naniger.arzikina.data.local.entity.CategoryEntity
import com.naniger.arzikina.data.repository.CategorySyncEnqueuer
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.model.SystemCategoryKey
import java.util.UUID

/**
 * Retrouve une catégorie "système" (générée automatiquement, connue par son nom exact fixe —
 * voir `LoanCategoryNames`/`FeeCategoryNames`) et la RECRÉE silencieusement si l'utilisateur l'a
 * supprimée entre-temps, à partir de [DefaultCategories.seed] comme unique source de vérité pour
 * sa couleur/icône/type.
 *
 * Extrait ici lors de l'introduction d'un second consommateur (fonctionnalité Frais) : cette
 * logique vivait auparavant uniquement dans `LoanRepositoryImpl.resolveLoanCategory` (voir "évite
 * le code dupliqué", instructions projet) — `LoanRepositoryImpl` délègue maintenant ici, aucun
 * changement de comportement.
 *
 * [categorySyncEnqueuer] : CORRECTIF (voir sa KDoc de tête pour le bug réel qu'il corrige) — une
 * catégorie RECRÉÉE ici doit être enfilée pour synchronisation exactement comme
 * `CategoryRepositoryImpl.saveCategory` le fait pour toute autre catégorie ; l'omettre laissait
 * cette catégorie orpheline pour toujours dès qu'un `syncId` de secours lui était assigné ailleurs
 * (voir `TransactionSyncEnqueuer.resolveOrAssignSyncId`). Le `syncId` est désormais généré ICI,
 * au moment de la création, plutôt que laissé `null` en attendant qu'un consommateur en aval en
 * assigne un de secours — un seul endroit responsable, comme pour n'importe quelle autre catégorie.
 */
internal object SystemCategoryResolver {
    /** Résolution par NOM seul — catégories Prêts/Frais, dont les noms sont uniques tous types
     * confondus (comportement historique inchangé). */
    suspend fun resolve(
        categoryDao: CategoryDao,
        categorySyncEnqueuer: CategorySyncEnqueuer,
        name: String,
        userId: Long
    ): CategoryEntity = resolveOrRecreate(
        categoryDao = categoryDao,
        categorySyncEnqueuer = categorySyncEnqueuer,
        find = { categoryDao.getFirstByNameForUser(name, userId) },
        matchesTemplate = { it.name == name },
        label = name,
        userId = userId
    )

    /** Résolution par NOM + TYPE d'une [SystemCategoryKey] — obligatoire pour « Cadeaux », qui existe
     * en dépense ([SystemCategoryKey.GIFTS]) ET en revenu ([SystemCategoryKey.GIFTS_RECEIVED]). */
    suspend fun resolve(
        categoryDao: CategoryDao,
        categorySyncEnqueuer: CategorySyncEnqueuer,
        key: SystemCategoryKey,
        userId: Long
    ): CategoryEntity = resolveOrRecreate(
        categoryDao = categoryDao,
        categorySyncEnqueuer = categorySyncEnqueuer,
        find = { categoryDao.getFirstByNameAndTypeForUser(key.canonicalName, key.type, userId) },
        matchesTemplate = { it.name == key.canonicalName && it.type == key.type },
        label = "${key.canonicalName} (${key.type})",
        userId = userId
    )

    private suspend fun resolveOrRecreate(
        categoryDao: CategoryDao,
        categorySyncEnqueuer: CategorySyncEnqueuer,
        find: suspend () -> CategoryEntity?,
        matchesTemplate: (CategoryEntity) -> Boolean,
        label: String,
        userId: Long
    ): CategoryEntity {
        find()?.let { return it }

        val template = DefaultCategories.seed(System.currentTimeMillis(), userId)
            .first(matchesTemplate)
            .copy(syncId = UUID.randomUUID().toString())
        categoryDao.upsert(template)
        val created = find() ?: error("Impossible de recréer la catégorie système \"$label\".")
        categorySyncEnqueuer.enqueue(created, SyncOperation.CREATE)
        return created
    }
}
