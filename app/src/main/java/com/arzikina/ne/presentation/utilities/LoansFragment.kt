package com.arzikina.ne.presentation.utilities

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentLoansBinding

/**
 * Placeholder "en cours de développement" pour Prêts/Emprunts (voir bloc Utilitaires du
 * Dashboard). Pas de ViewModel : aucune donnée ni état à gérer pour l'instant (voir
 * [com.arzikina.ne.presentation.more.MoreFragment], même raisonnement pour un écran purement
 * statique). À remplacer par le vrai écran (domaine, Room, ViewModel...) dans une étape dédiée
 * future, sans changer l'id de destination `loansFragment` (voir nav_graph.xml) ni son point
 * d'entrée depuis le Dashboard.
 */
class LoansFragment : Fragment(R.layout.fragment_loans) {

    private var binding: FragmentLoansBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentLoansBinding.bind(view)
        binding = viewBinding
        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
