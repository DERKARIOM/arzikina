package com.arzikina.ne.presentation.statistics

import androidx.fragment.app.Fragment
import com.arzikina.ne.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Graphiques (évolution mensuelle, répartition des dépenses).
 *
 * Reconversion Compose -> XML/Views en cours (voir instructions projet) :
 * ce Fragment n'affiche pour l'instant qu'un espace réservé
 * ([R.layout.fragment_statistics]). Les graphiques seront reconstruits avec
 * le module Views de Vico (`vico-views`, voir app/build.gradle.kts) plutôt
 * que le module Compose désormais retiré. [StatisticsViewModel] existe déjà
 * et reste inchangé.
 */
@AndroidEntryPoint
class StatisticsFragment : Fragment(R.layout.fragment_statistics)
