package com.arzikina.ne.presentation.security

import android.os.Bundle
import android.view.View
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
 * Écran de verrouillage affiché au lancement (voir `MainActivity.resolveStartDestination`) quand
 * une session existe déjà ET que `UserPreferences.biometricLockEnabled` est actif ET que la
 * biométrie est disponible. Déclenche le prompt système AUTOMATIQUEMENT dès l'affichage (voir
 * cahier des charges : "Ouverture Arsikina → Vérification de la sécurité → Authentification
 * biométrique", sans étape manuelle intermédiaire) — [retryButton] ne sert qu'à relancer le prompt
 * après un échec/une annulation.
 *
 * [biometricAuthenticator] est injecté directement ici PAR CHAMP (pas via [BiometricLockViewModel],
 * voir sa doc) : `authenticate()` a besoin de CETTE `FragmentActivity` précise (`requireActivity()`),
 * qu'un ViewModel ne doit jamais retenir.
 *
 * Écran plein cadre SANS Toolbar ni flèche retour (voir `fragment_biometric_lock.xml`) : la seule
 * échappatoire volontaire est [BiometricLockViewModel.onLogout] (bouton "Se déconnecter"). Étant la
 * destination de DÉPART du graphe dans ce scénario (jamais empilée par-dessus le Dashboard), un
 * retour système depuis cet écran quitte simplement l'application plutôt que de révéler le
 * Dashboard "par en dessous" — aucun contournement possible par le bouton Précédent.
 */
@AndroidEntryPoint
class BiometricLockFragment : Fragment(R.layout.fragment_biometric_lock) {

    private val viewModel: BiometricLockViewModel by viewModels()
    private var binding: FragmentBiometricLockBinding? = null

    @Inject
    lateinit var biometricAuthenticator: BiometricAuthenticator

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentBiometricLockBinding.bind(view)
        binding = viewBinding

        viewBinding.retryButton.setOnClickListener { requestAuthentication() }
        viewBinding.logoutAction.setOnClickListener { viewModel.onLogout() }

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
     * détruite pendant la vérification (écran quitté autrement, cas rare vu l'absence de
     * navigation arrière ici), la coroutine — et donc le prompt système — s'annule proprement
     * (voir `BiometricAuthenticatorImpl.authenticate`, `invokeOnCancellation`).
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
            BiometricLockEvent.Unlocked -> navigateTo(R.id.dashboardFragment)
            BiometricLockEvent.LoggedOut -> navigateTo(R.id.loginFragment)
        }
    }

    /** Même schéma que `LoginFragment`/`ProfileFragment` (fondu, `popUpTo(nav_graph, true)`, voir
     * leur doc) : ce verrou n'a aucune pile de retour propre à préserver, seulement le graphe
     * entier à remplacer par la destination choisie. */
    private fun navigateTo(destinationId: Int) {
        val options = NavOptions.Builder()
            .setEnterAnim(R.anim.fade_in)
            .setExitAnim(R.anim.fade_out)
            .setPopUpTo(R.id.nav_graph, true)
            .build()
        findNavController().navigate(destinationId, null, options)
    }
}
