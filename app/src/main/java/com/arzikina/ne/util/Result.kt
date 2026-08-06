package com.arzikina.ne.util

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
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>()
}
