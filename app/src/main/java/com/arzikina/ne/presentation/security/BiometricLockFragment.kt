package com.arzikina.ne.presentation.security

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentBiometricLockBinding
import com.arzikina.ne.domain.repository.BiometricAuthenticator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Écran de verrouillage biométrique, avec DEUX points d'entrée distincts (voir `nav_graph.xml`,
 * argument `isResumeCheck`) :
 * - à l'ouverture (voir `MainActivity.resolveStartDestination`) : `isResumeCheck = false`,
 *   destination de DÉPART du graphe (jamais empilée par-dessus rien) — le succès remplace tout le
 *   graphe par `dashboardFragment` (voir [navigateAfterUnlock]).
 * - au retour au premier plan (voir `MainActivity.checkBiometricReentryLock`) : `isResumeCheck =
 *   true`, empilé PAR-DESSUS l'écran que l'utilisateur avait quitté — le succès dépile simplement
 *   cet écran de verrouillage pour révéler EXACTEMENT l'écran d'origine (formulaire en cours de
 *   saisie compris), plutôt que de le renvoyer au Dashboard et lui faire perdre sa position.
 *
 * Déclenche le prompt système AUTOMATIQUEMENT dès l'affichage dans les deux cas (voir cahier des
 * charges : "Ouverture Arsikina → Vérification de la sécurité → Authentification biométrique",
 * sans étape manuelle intermédiaire) — [retryButton] ne sert qu'à relancer le prompt après un
 * échec/une annulation.
 *
 * [biometricAuthenticator] est injecté directement ici PAR CHAMP (pas via [BiometricLockViewModel],
 * voir sa doc) : `authenticate()` a besoin de CETTE `FragmentActivity` précise (`requireActivity()`),
 * qu'un ViewModel ne doit jamais retenir.
 *
 * Écran plein cadre SANS Toolbar ni flèche retour (voir `fragment_biometric_lock.xml`) : la seule
 * échappatoire volontaire est [BiometricLockViewModel.onLogout] (bouton "Se déconnecter"). Le
 * bouton Précédent système est explicitement INTERCEPTÉ ET IGNORÉ (voir [onViewCreated],
 * `OnBackPressedCallback`) — indispensable en mode `isResumeCheck = true`, où ce verrou EST empilé
 * par-dessus un écran réel : sans cette interception, Précédent le dépilerait et révélerait l'écran
 * du dessous sans aucune authentification, un contournement total. En mode ouverture
 * (`isResumeCheck = false`), l'interception est redondante (le verrou est déjà la destination de
 * départ, sans rien en dessous) mais sans effet de bord — conservée pour un comportement uniforme.
 */
@AndroidEntryPoint
class BiometricLockFragment : Fragment(R.layout.fragment_biometric_lock) {

    private val viewModel: BiometricLockViewModel by viewModels()
    private var binding: FragmentBiometricLockBinding? = null

    @Inject
    lateinit var biometricAuthenticator: BiometricAuthenticator

    /** Voir la doc de classe : distingue les deux points d'entrée. Défaut à `false` cohérent avec
     * `nav_graph.xml` (utilisé si ce Fragment est ouvert sans argument, ex. aperçu Android Studio). */
    private val isResumeCheck: Boolean
        get() = arguments?.getBoolean(ARG_IS_RESUME_CHECK) ?: false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentBiometricLockBinding.bind(view)
        binding = viewBinding

        viewBinding.retryButton.setOnClickListener { requestAuthentication() }
        viewBinding.logoutAction.setOnClickListener { viewModel.onLogout() }

        // Voir la doc de classe, "bouton Précédent système INTERCEPTÉ" : `isEnabled = true` en
        // permanence, callback vide — consomme l'événement sans jamais rien dépiler.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Volontairement vide.
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event -> handleEvent(event) }
            }
        }

        requestAuthentication()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    /**
     * `requireActivity()` (jamais `requireContext()`) : `BiometricPrompt` exige une
     * `FragmentActivity` réelle pour s'attacher à son cycle de vie (voir la doc de
     * [BiometricAuthenticator]). Lancé sur [viewLifecycleOwner.lifecycleScope] : si la vue est
     * détruite pendant la vérification (écran quitté autrement), la coroutine — et donc le prompt
     * système — s'annule proprement (voir `BiometricAuthenticatorImpl.authenticate`,
     * `invokeOnCancellation`).
     */
    private fun requestAuthentication() {
        viewLifecycleOwner.lifecycleScope.launch {
            val success = biometricAuthenticator.authenticate(requireActivity())
            if (success) {
                viewModel.onUnlocked()
            }
            // Échec ou annulation : on reste simplement sur cet écran, boutons "Réessayer" et
            // "Se déconnecter" toujours actionnables — jamais de nouvelle tentative automatique.
        }
    }

    private fun handleEvent(event: BiometricLockEvent) {
        when (event) {
            BiometricLockEvent.Unlocked -> navigateAfterUnlock()
            BiometricLockEvent.LoggedOut -> navigateReplacingGraph(R.id.loginFragment)
        }
    }

    /** Voir la doc de classe : les deux points d'entrée divergent UNIQUEMENT ici. */
    private fun navigateAfterUnlock() {
        if (isResumeCheck) {
            findNavController().popBackStack()
        } else {
            navigateReplacingGraph(R.id.dashboardFragment)
        }
    }

    /** Même schéma que `LoginFragment`/`ProfileFragment` (fondu, `popUpTo(nav_graph, true)`, voir
     * leur doc) : remplace tout le graphe par [destinationId], sans pile de retour à préserver —
     * utilisé pour la déconnexion (toujours) et pour le succès en mode ouverture. */
    private fun navigateReplacingGraph(destinationId: Int) {
        val options = NavOptions.Builder()
            .setEnterAnim(R.anim.fade_in)
            .setExitAnim(R.anim.fade_out)
            .setPopUpTo(R.id.nav_graph, true)
            .build()
        findNavController().navigate(destinationId, null, options)
    }

    companion object {
        /** Voir `nav_graph.xml` — même nom d'argument des deux côtés. */
        const val ARG_IS_RESUME_CHECK = "isResumeCheck"
    }
}
