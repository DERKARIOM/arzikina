package com.naniger.arzikina.util

/**
 * Wrapper générique pour représenter l'état d'une opération asynchrone
 * (chargement Room, calcul de statistiques, import/export...) de façon
 * uniforme entre le domaine et la présentation.
 *
 * Utilisé par les ViewModels pour exposer un [kotlinx.coroutines.flow.StateFlow]
 * d'état d'écran sans dupliquer cette logique dans chaque fonctionnalité.
 */
sealed class AppResult<out T> {
    data object Loading : AppResult<Nothing>()
    data class Success<T>(val data: T) : AppResult<T>()

    /**
     * [message] est un message TECHNIQUE (journaux, débogage, tests), jamais affiché tel quel :
     * il peut être en anglais, contenir un nom de classe ou un détail d'exception. L'écran
     * affiche toujours un texte traduit issu de `strings.xml` (ex. `R.string.error_generic`),
     * choisi selon le contexte — voir chantier i18n, étape 2.
     */
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>()
}

/**
 * Message technique d'une exception, jamais vide : son `message` s'il existe, sinon le nom de sa
 * classe (ex. `IOException`). Remplace les anciens `it.message ?: "Erreur inconnue"` dupliqués
 * dans chaque ViewModel. À réserver aux usages techniques (voir [AppResult.Error.message]).
 */
fun Throwable.technicalMessage(): String = message ?: javaClass.simpleName
