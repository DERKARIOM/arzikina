package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.update.UpdatePromptDismissal

/**
 * Mémorise le dernier « Plus tard » donné à une proposition de mise à jour Flexible, pour ne pas la
 * réafficher à chaque ouverture (voir `InAppUpdateConfig.flexibleSnoozeMillis`). Persisté : survit
 * au redémarrage de l'app, contrairement à `InAppUpdateSessionState`.
 */
interface InAppUpdatePromptStore {

    /** `null` si l'utilisateur n'a jamais reporté de mise à jour (ou lecture impossible). */
    suspend fun lastDismissal(): UpdatePromptDismissal?

    suspend fun recordDismissal(dismissal: UpdatePromptDismissal)
}
