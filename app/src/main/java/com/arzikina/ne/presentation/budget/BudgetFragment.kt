package com.arzikina.ne.presentation.budget

import androidx.fragment.app.Fragment
import com.arzikina.ne.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Liste des budgets avec leur progression sur la période en cours.
 *
 * Reconversion Compose -> XML/Views en cours (voir instructions projet) :
 * ce Fragment n'affiche pour l'instant qu'un espace réservé
 * ([R.layout.fragment_budget]), le contenu réel sera ajouté dans une
 * prochaine étape dédiée. [BudgetViewModel] existe déjà et reste inchangé.
 */
@AndroidEntryPoint
class BudgetFragment : Fragment(R.layout.fragment_budget)
