package com.arzikina.ne.presentation.settings

import androidx.fragment.app.Fragment
import com.arzikina.ne.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Paramètres (thème, devise principale, langue, sauvegarde/restauration, à propos).
 *
 * Reconversion Compose -> XML/Views en cours (voir instructions projet) :
 * ce Fragment n'affiche pour l'instant qu'un espace réservé
 * ([R.layout.fragment_settings]), le contenu réel sera ajouté dans une
 * prochaine étape dédiée. [SettingsViewModel] et [BackupViewModel] existent
 * déjà et restent inchangés.
 */
@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings)
