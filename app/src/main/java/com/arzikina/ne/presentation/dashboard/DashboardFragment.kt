package com.arzikina.ne.presentation.dashboard

import androidx.fragment.app.Fragment
import com.arzikina.ne.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Écran d'accueil (solde, revenus/dépenses du mois, dernières transactions).
 *
 * Reconversion Compose -> XML/Views en cours (voir instructions projet) :
 * ce Fragment n'affiche pour l'instant qu'un espace réservé
 * ([R.layout.fragment_dashboard]), le contenu réel sera ajouté dans une
 * prochaine étape dédiée. [DashboardViewModel] existe déjà et reste
 * inchangé — il ne dépend d'aucun framework d'UI.
 */
@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard)
