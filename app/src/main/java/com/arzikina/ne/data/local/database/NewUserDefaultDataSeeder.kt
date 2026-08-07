package com.arzikina.ne.data.local.database

import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.CategoryDao
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
 */
class NewUserDefaultDataSeeder @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao
) {
    suspend fun seed(userId: Long) {
        val now = System.currentTimeMillis()
        accountDao.insertAll(DefaultAccounts.seed(now, userId))
        categoryDao.insertAll(DefaultCategories.seed(now, userId))
    }
}
