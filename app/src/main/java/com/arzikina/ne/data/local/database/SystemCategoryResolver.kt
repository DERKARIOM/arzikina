package com.arzikina.ne.data.local.database

import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.repository.CategorySyncEnqueuer
import com.arzikina.ne.domain.model.SyncOperation
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
    suspend fun resolve(
        categoryDao: CategoryDao,
        categorySyncEnqueuer: CategorySyncEnqueuer,
        name: String,
        userId: Long
    ): CategoryEntity {
        categoryDao.getFirstByNameForUser(name, userId)?.let { return it }

        val template = DefaultCategories.seed(System.currentTimeMillis(), userId)
            .first { it.name == name }
            .copy(syncId = UUID.randomUUID().toString())
        categoryDao.upsert(template)
        val created = categoryDao.getFirstByNameForUser(name, userId)
            ?: error("Impossible de recréer la catégorie système \"$name\".")
        categorySyncEnqueuer.enqueue(created, SyncOperation.CREATE)
        return created
    }
}
