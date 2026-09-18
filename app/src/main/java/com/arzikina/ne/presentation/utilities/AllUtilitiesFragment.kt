package com.arzikina.ne.presentation.utilities

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentAllUtilitiesBinding
import com.arzikina.ne.presentation.components.NavAnimations

/**
 * Écran "Tous les utilitaires" (voir "Voir tout" sur le bloc Utilitaires du Dashboard).
 *
 * Affiche [UtilityCatalog.all] en intégralité (contrairement au Dashboard, qui n'en montrera
 * qu'une sélection une fois le catalogue plus grand — voir la doc de [UtilityCatalog]), en
 * grille via `allUtilitiesList` (voir fragment_all_utilities.xml). Pas de ViewModel : cet écran
 * n'affiche aucune donnée métier ni état asynchrone, uniquement une liste fixe de raccourcis de
 * navigation — un ViewModel n'apporterait ici aucun bénéfice de testabilité ou de survie aux
 * changements de configuration, seulement du code mort.
 */
class AllUtilitiesFragment : Fragment(R.layout.fragment_all_utilities) {

    private var binding: FragmentAllUtilitiesBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentAllUtilitiesBinding.bind(view)
        binding = viewBinding
        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // useCardStyle = true : postcard moderne par tuile (voir la doc de tête de
        // UtilityTileAdapter) — demande explicitement limitée à cet écran, le Dashboard garde ses
        // tuiles compactes.
        viewBinding.allUtilitiesList.adapter = UtilityTileAdapter(UtilityCatalog.all(), useCardStyle = true) { item ->
            findNavController().navigate(item.destinationId, null, NavAnimations.push)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
