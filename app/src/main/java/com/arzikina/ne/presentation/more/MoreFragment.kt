package com.arzikina.ne.presentation.more

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentMoreBinding
import com.arzikina.ne.presentation.components.NavAnimations

/**
 * Onglet "Autre" de la Bottom Navigation : regroupe les destinations qui ne
 * sont plus des onglets directs (Budget, Catégories, Paramètres) pour ne
 * garder que 5 onglets au total (Accueil, Compte, Transaction, Rapports,
 * Autre — voir menu/bottom_nav_menu.xml).
 *
 * Pas de ViewModel ici, contrairement aux autres écrans du projet : cet
 * écran n'affiche aucune donnée métier ni état asynchrone, uniquement une
 * liste fixe de raccourcis de navigation — un ViewModel n'apporterait ici
 * aucun bénéfice de testabilité ou de survie aux changements de
 * configuration, seulement du code mort.
 */
class MoreFragment : Fragment(R.layout.fragment_more) {

    private var binding: FragmentMoreBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentMoreBinding.bind(view)
        binding = viewBinding

        val adapter = MoreMenuAdapter(menuItems()) { item ->
            findNavController().navigate(item.destinationId, null, NavAnimations.push)
        }
        viewBinding.moreMenuList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.moreMenuList.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    /**
     * Liste des raccourcis affichés. Ajouter une future entrée (Objectifs
     * d'épargne, Sauvegarde...) se fait en ajoutant une ligne ici.
     */
    private fun menuItems(): List<MoreMenuItem> = listOf(
        MoreMenuItem(
            iconRes = R.drawable.ic_person_24,
            titleRes = R.string.more_menu_profile,
            destinationId = R.id.profileFragment
        ),
        MoreMenuItem(
            iconRes = R.drawable.ic_wallet_24,
            titleRes = R.string.nav_budget,
            destinationId = R.id.budgetFragment
        ),
        MoreMenuItem(
            iconRes = R.drawable.ic_label_24,
            titleRes = R.string.more_menu_categories,
            destinationId = R.id.categoriesFragment
        ),
        MoreMenuItem(
            iconRes = R.drawable.ic_settings_24,
            titleRes = R.string.nav_settings,
            destinationId = R.id.settingsFragment
        )
    )
}
