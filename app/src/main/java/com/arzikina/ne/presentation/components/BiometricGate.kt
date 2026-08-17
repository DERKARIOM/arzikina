package com.arzikina.ne.presentation.components

import androidx.fragment.app.Fragment
import com.arzikina.ne.domain.repository.BiometricAuthenticator

/**
 * Gate biométrique réutilisable avant une action sensible (voir cahier des charges,
 * "Authentification par empreinte digitale" — révélation numéro de carte/CVV
 * ([com.arzikina.ne.presentation.accounts.AccountDetailFragment]), export/import de sauvegarde
 * ([com.arzikina.ne.presentation.settings.BackupFragment])) : authentifie AVANT d'autoriser
 * l'action, sauf si aucun matériel biométrique n'est disponible/enrôlé sur l'appareil — dans ce
 * cas, l'action reste autorisée SANS ce gate supplémentaire (aucune régression par rapport au
 * comportement actuel de l'app sur un appareil sans empreinte enregistrée ; bloquer totalement une
 * fonctionnalité faute de matériel n'apporterait aucune protection réelle, sans échappatoire — même
 * logique de repli que [com.arzikina.ne.MainActivity.resolveStartDestination]).
 *
 * Extension sur [Fragment] (plutôt qu'une fonction libre prenant une `FragmentActivity` en
 * paramètre) : `requireActivity()` est résolu ici, au point d'appel, jamais retenu au-delà de
 * cette unique invocation — voir la doc de [BiometricAuthenticator.authenticate] sur pourquoi un
 * ViewModel ne doit jamais porter cette responsabilité.
 */
suspend fun Fragment.authenticateForSensitiveAction(biometricAuthenticator: BiometricAuthenticator): Boolean =
    !biometricAuthenticator.isAvailable() || biometricAuthenticator.authenticate(requireActivity())
