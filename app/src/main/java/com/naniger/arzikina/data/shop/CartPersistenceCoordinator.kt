package com.naniger.arzikina.data.shop

import com.naniger.arzikina.data.repository.ActiveShopProvider
import com.naniger.arzikina.data.repository.ShopScope
import com.naniger.arzikina.di.ApplicationScope
import com.naniger.arzikina.domain.shop.CartSnapshot
import com.naniger.arzikina.domain.shop.SaleCartStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Relie le [SaleCartStore] (mémoire) à Room, sans que le store connaisse la base :
 * - à chaque changement d'utilisateur ou de boutique active ([ActiveShopProvider.observeScope]), le panier en
 *   cours est d'abord enregistré pour l'ANCIEN périmètre, puis remplacé par celui du NOUVEAU (ou par un panier
 *   vide sans boutique) : un panier ne passe jamais d'un utilisateur à un autre ;
 * - ensuite, chaque modification du store est enregistrée (les rafales sont fusionnées : seul le dernier état
 *   compte, et une écriture commencée n'est jamais interrompue).
 *
 * Démarré une fois, au lancement de l'application (voir `ArzikinaApplication`). Une écriture qui échoue est
 * simplement retentée à la modification suivante : le panier en mémoire reste la source de vérité de l'écran.
 */
@Singleton
class CartPersistenceCoordinator @Inject constructor(
    private val store: SaleCartStore,
    private val activeShop: ActiveShopProvider,
    private val local: CartLocalDataSource,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private val started = AtomicBoolean(false)

    // Accédés uniquement à la suite, jamais en parallèle (collectLatest attend la fin du bloc précédent).
    private var activeScope: ShopScope? = null
    private var persisted: CartSnapshot? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            activeShop.observeScope().collectLatest { scope -> follow(scope) }
        }
    }

    private suspend fun follow(scope: ShopScope?) {
        flushPending()
        activeScope = scope
        if (scope == null) {
            persisted = null
            store.replaceWith(CartSnapshot.fresh())
            return
        }
        val restored = try {
            local.load(scope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CartSnapshot.fresh()
        }
        persisted = restored
        store.replaceWith(restored)
        store.snapshot.collectLatest { snapshot -> persist(scope, snapshot) }
    }

    /** Enregistre l'état courant pour le périmètre qu'on s'apprête à quitter, s'il n'a pas encore été écrit. */
    private suspend fun flushPending() {
        val scope = activeScope ?: return
        persist(scope, store.snapshot.value)
    }

    private suspend fun persist(scope: ShopScope, snapshot: CartSnapshot) {
        if (snapshot == persisted) return
        withContext(NonCancellable) {
            try {
                local.save(scope, snapshot)
                persisted = snapshot
            } catch (e: Exception) {
                // Retenté à la prochaine modification ; le panier en mémoire reste intact.
            }
        }
    }
}
