package com.naniger.arzikina.data.local.database

import androidx.room.withTransaction
import com.naniger.arzikina.data.local.dao.AccountDao
import com.naniger.arzikina.data.local.dao.CategoryDao
import com.naniger.arzikina.domain.model.DefaultAccountPlan
import com.naniger.arzikina.domain.model.UsageMode
import javax.inject.Inject

/**
 * Peuple les comptes et catégories par défaut d'un utilisateur qui vient de
 * s'inscrire (voir `presentation/auth/RegisterViewModel`).
 *
 * Reste dans la couche data (dépend directement des DAO et entités) : ni le
 * domaine ni la présentation n'ont besoin de connaître l'existence de ces
 * données par défaut, elles apparaissent simplement dans les listes
 * observées juste après l'inscription, comme n'importe quelle autre donnée.
 * Voir `di/DatabaseModule` pour l'historique (ce peuplement se faisait
 * auparavant à la création de la base, avant l'authentification).
 *
 * Tout se déroule dans UNE transaction Room : soit les comptes ET les catégories sont créés, soit rien (jamais
 * un utilisateur à moitié configuré). L'opération est IDEMPOTENTE : la rappeler (double appui, reprise après
 * une coupure) ne crée aucun doublon.
 */
class NewUserDefaultDataSeeder @Inject constructor(
    private val database: ArzikinaDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao
) {
    /**
     * @param usageMode mode choisi par l'utilisateur : détermine EXACTEMENT les comptes créés (voir
     * [DefaultAccountPlan]). Obligatoire : aucun parcours ne crée plus de comptes sans ce choix.
     */
    suspend fun seed(userId: Long, usageMode: UsageMode) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val missing = DefaultAccountPlan.missingKeys(usageMode, accountDao.activeTypesForUser(userId).toSet())
            if (missing.isNotEmpty()) {
                val firstOrder = (accountDao.maxDisplayOrder(userId) ?: -1L) + 1L
                accountDao.insertAll(
                    DefaultAccounts.seedFor(
                        now = now,
                        userId = userId,
                        keys = missing,
                        firstDisplayOrder = firstOrder,
                        excludeShopFromStatistics = DefaultAccountPlan.excludesShopFromStatistics(usageMode)
                    )
                )
            }
            // Les catégories par défaut ne sont semées qu'une fois (nécessaires aux transactions et aux frais,
            // quel que soit le mode).
            if (categoryDao.countForUser(userId) == 0) {
                categoryDao.insertAll(DefaultCategories.seed(now, userId))
            }
        }
    }
}
