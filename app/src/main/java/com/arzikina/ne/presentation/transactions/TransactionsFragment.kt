package com.arzikina.ne.presentation.transactions

import androidx.fragment.app.Fragment
import com.arzikina.ne.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Liste des transactions avec recherche et filtres.
 *
 * Reconversion Compose -> XML/Views en cours (voir instructions projet) :
 * ce Fragment n'affiche pour l'instant qu'un espace réservé
 * ([R.layout.fragment_transactions]), le contenu réel (RecyclerView, barre de
 * recherche, filtres) sera ajouté dans une prochaine étape dédiée.
 * [TransactionsViewModel] existe déjà et reste inchangé.
 */
@AndroidEntryPoint
class TransactionsFragment : Fragment(R.layout.fragment_transactions)
