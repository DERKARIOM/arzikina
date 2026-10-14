package com.naniger.arzikina.domain.versioning

import kotlinx.coroutines.flow.Flow

/**
 * Canal par lequel la couche réseau signale « le serveur a répondu 426 `upgrade_required` » à
 * l'interface, sans que l'une connaisse l'autre. L'interface réagit en relançant une vérification
 * de la politique (qui fournit le message, le lien et les notes à afficher).
 */
interface UpgradeRequiredSignal {
    val events: Flow<Unit>
    fun notifyUpgradeRequired()
}
