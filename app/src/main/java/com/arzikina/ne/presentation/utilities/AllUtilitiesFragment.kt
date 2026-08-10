package com.arzikina.ne.presentation.utilities

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentAllUtilitiesBinding

/**
 * Écran "Tous les utilitaires" (voir "Voir tout" sur le bloc Utilitaires du Dashboard).
 *
 * Affiche [UtilityCatalog.all] en intégralité (contrairement au Dashboard, qui n'en montrera
 * qu'une sélection une fois le catalogue plus grand — voir la doc de [UtilityCatalog]), en
 * grille via `allUtilitiesList` (voir fragment_all_utilities.xml). Pas de ViewModel : même
 * raisonnement que [com.arzikina.ne.presentation.more.MoreFragment], une liste fixe ne
 * bénéficie d'aucune testabilité/survie de configuration supplémentaire apportée par un
 * ViewModel.
 */
class AllUtilitiesFragment : Fragment(R.layout.fragment_all_utilities) {

    private var binding: FragmentAllUtilitiesBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentAllUtilitiesBinding.bind(view)
        binding = viewBinding
        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewBinding.allUtilitiesList.adapter = UtilityTileAdapter(UtilityCatalog.all()) { item ->
            findNavController().navigate(item.destinationId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
