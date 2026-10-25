package com.naniger.arzikina.data.repository

import androidx.room.withTransaction
import com.naniger.arzikina.data.local.dao.ShopDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.local.entity.ShopEntity
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.domain.repository.ShopRepository
import com.naniger.arzikina.domain.repository.UserPreferencesRepository
import com.naniger.arzikina.domain.shop.ProductException
import com.naniger.arzikina.domain.shop.Shop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implémentation Room de [ShopRepository]. La boutique est créée sans `syncId` : le gestionnaire de
 * synchronisation du module (étape « Ventes ») rattrape les lignes à `syncId` nul.
 */
class ShopRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val shopDao: ShopDao,
    private val sessionManager: SessionManager,
    private val preferences: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ShopRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeShop(): Flow<Shop?> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) flowOf(null) else shopDao.observeFirstForUser(userId).map { it?.toDomain() }
        }

    override suspend fun getOrCreateShop(defaultName: String): Shop = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: throw ProductException.NoSession()
        val currency = preferences.observePreferences().first().currencyCode
        database.withTransaction {
            // Lecture + insertion dans la même transaction : deux ouvertures simultanées ne créent qu'une boutique.
            shopDao.getFirstForUser(userId)?.toDomain() ?: run {
                val now = System.currentTimeMillis()
                val entity = ShopEntity(
                    userId = userId,
                    name = defaultName.trim().ifEmpty { DEFAULT_NAME },
                    accountId = null,
                    currencyCode = currency,
                    createdAt = now,
                    updatedAt = now
                )
                entity.copy(id = shopDao.insert(entity)).toDomain()
            }
        }
    }

    private companion object {
        const val DEFAULT_NAME = "Boutique"
    }
}
