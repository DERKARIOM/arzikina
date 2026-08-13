package com.arzikina.ne.data.local.database

import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.entity.CategoryEntity

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
 */
internal object SystemCategoryResolver {
    suspend fun resolve(categoryDao: CategoryDao, name: String, userId: Long): CategoryEntity {
        categoryDao.getFirstByNameForUser(name, userId)?.let { return it }
        val template = DefaultCategories.seed(System.currentTimeMillis(), userId).first { it.name == name }
        categoryDao.upsert(template)
        return categoryDao.getFirstByNameForUser(name, userId)
            ?: error("Impossible de recréer la catégorie système \"$name\".")
    }
}
