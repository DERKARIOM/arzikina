package com.arzikina.ne.presentation.settings

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentSettingsBinding
import com.arzikina.ne.databinding.ItemSettingsRowBinding
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.model.SupportedCurrency
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.presentation.profile.BiometricLockUiState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Paramètres — voir le plan "Refonte de la page Paramètres" pour la liste complète des sections
 * prévues. Cette étape construit l'en-tête profil et la section "Général" (devise, thème) ; les
 * étapes suivantes ajouteront leurs propres `setUpXxxSection()`/`renderXxx()` sans toucher à ceux
 * déjà en place.
 *
 * [BackupFragment] reste un écran séparé (voir sa doc) : un futur `setUpBackupSection()` s'y
 * contentera de naviguer, sans dupliquer sa logique ici.
 */
@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels()
    private var binding: FragmentSettingsBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentSettingsBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.subtitleText.text = getString(R.string.settings_screen_subtitle, getString(R.string.app_name))

        setUpProfileRow(viewBinding)
        setUpGeneralSection(viewBinding)
        setUpSecuritySection(viewBinding)
        setUpAccountsSection(viewBinding)
        setUpTransactionsSection(viewBinding)
        setUpBudgetFinanceSection(viewBinding)
        setUpBackupSection(viewBinding)
        setUpAboutSection(viewBinding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(viewBinding, state) } }
                launch { viewModel.biometricLockState.collect { state -> renderBiometricLock(viewBinding, state) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpProfileRow(binding: FragmentSettingsBinding) {
        binding.profileCard.setOnClickListener {
            findNavController().navigate(R.id.profileFragment, null, NavAnimations.push)
        }
    }

    /**
     * `languageRow` reste sans `setOnClickListener` (voir sa doc dans fragment_settings.xml) :
     * pas d'action tant qu'aucune langue alternative n'existe, pour ne jamais laisser croire à un
     * réglage fantôme.
     */
    private fun setUpGeneralSection(binding: FragmentSettingsBinding) {
        binding.currencyRow.rowIcon.setImageResource(R.drawable.ic_payments_24)
        binding.currencyRow.rowTitle.setText(R.string.settings_currency_label)
        binding.currencyRow.rowSubtitle.setText(R.string.settings_section_currency)
        binding.currencyRow.rowValue.visibility = View.VISIBLE
        binding.currencyRow.root.setOnClickListener { showCurrencyPicker() }

        binding.themeRow.rowIcon.setImageResource(R.drawable.ic_dark_mode_24)
        binding.themeRow.rowTitle.setText(R.string.settings_theme_label)
        binding.themeRow.rowSubtitle.setText(R.string.settings_theme_description)
        binding.themeRow.rowValue.visibility = View.VISIBLE
        binding.themeRow.root.setOnClickListener { showThemePicker() }

        binding.languageRow.rowIcon.setImageResource(R.drawable.ic_language_24)
        binding.languageRow.rowTitle.setText(R.string.settings_section_language)
        binding.languageRow.rowSubtitle.setText(R.string.settings_language_french_only)
        binding.languageRow.rowValue.visibility = View.VISIBLE
        binding.languageRow.rowValue.setText(R.string.settings_language_value)
        binding.languageRow.rowChevron.visibility = View.GONE
        binding.languageRow.root.isClickable = false
        binding.languageRow.root.isFocusable = false
        binding.languageRow.root.background = null
    }

    /**
     * `biometricLockRow` lit/écrit EXACTEMENT la même préférence que `fragment_profile.xml`
     * (voir la doc de [SettingsViewModel]) — le switch reste cliquable sur toute la ligne (icône,
     * titre, sous-titre compris), pas seulement sur le `MaterialSwitch` lui-même, pour rester
     * cohérent avec les autres lignes de cet écran qui sont toutes cliquables sur toute leur
     * surface. `changePasswordRow`/`securityQuestionRow` renvoient vers les écrans déjà existants
     * (voir `ProfileFragment`, même navigation), sans dupliquer leur logique ici.
     */
    private fun setUpSecuritySection(binding: FragmentSettingsBinding) {
        binding.biometricLockRow.rowIcon.setImageResource(R.drawable.ic_fingerprint_24)
        binding.biometricLockRow.rowTitle.setText(R.string.profile_biometric_lock_label)
        binding.biometricLockRow.rowChevron.visibility = View.GONE
        binding.biometricLockRow.rowSwitch.visibility = View.VISIBLE
        binding.biometricLockRow.root.setOnClickListener { binding.biometricLockRow.rowSwitch.toggle() }
        binding.biometricLockRow.rowSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onBiometricLockToggle(isChecked)
        }

        bindNavigationRow(
            row = binding.changePasswordRow,
            iconRes = R.drawable.ic_lock_24,
            titleRes = R.string.profile_change_password_action,
            subtitleRes = R.string.settings_security_change_password_subtitle,
            destinationId = R.id.changePasswordFragment
        )
        bindNavigationRow(
            row = binding.securityQuestionRow,
            iconRes = R.drawable.ic_help_24,
            titleRes = R.string.profile_security_question_action,
            subtitleRes = R.string.settings_security_question_subtitle,
            destinationId = R.id.securityQuestionFragment
        )
    }

    /**
     * Simple raccourci de navigation, aucun état à observer (voir sa doc dans
     * fragment_settings.xml) — icône réutilisée telle quelle depuis `bottom_nav_menu.xml`
     * (`comptes`), pas de nouvelle ressource.
     */
    private fun setUpAccountsSection(binding: FragmentSettingsBinding) {
        bindNavigationRow(
            row = binding.accountsRow,
            iconRes = R.drawable.comptes,
            titleRes = R.string.accounts_title,
            subtitleRes = R.string.settings_accounts_row_subtitle,
            destinationId = R.id.accountsFragment
        )
    }

    /** Icônes réutilisées depuis `UtilityCatalog`/`bottom_nav_menu.xml` (`categorie_24`,
     * `ic_planifications`) pour rester cohérent avec le bloc Utilitaires du Dashboard, sauf
     * `ic_receipt_long_24` (nouvelle, aucune icône existante pour "toutes les transactions"). */
    private fun setUpTransactionsSection(binding: FragmentSettingsBinding) {
        bindNavigationRow(
            row = binding.transactionsRow,
            iconRes = R.drawable.ic_receipt_long_24,
            titleRes = R.string.settings_transactions_all_label,
            subtitleRes = R.string.settings_transactions_all_subtitle,
            destinationId = R.id.transactionsFragment
        )
        bindNavigationRow(
            row = binding.categoriesRow,
            iconRes = R.drawable.categorie_24,
            titleRes = R.string.categories_title,
            subtitleRes = R.string.settings_categories_row_subtitle,
            destinationId = R.id.categoriesFragment
        )
        bindNavigationRow(
            row = binding.recurringRow,
            iconRes = R.drawable.ic_planifications,
            titleRes = R.string.utility_recurring_transactions_title,
            subtitleRes = R.string.settings_recurring_row_subtitle,
            destinationId = R.id.recurringTransactionsFragment
        )
    }

    /** Icônes réutilisées depuis `MoreFragment`/`UtilityCatalog`/`bottom_nav_menu.xml`
     * (`ic_wallet_24`, `investissement_24`, `rapport`). */
    private fun setUpBudgetFinanceSection(binding: FragmentSettingsBinding) {
        bindNavigationRow(
            row = binding.budgetRow,
            iconRes = R.drawable.ic_wallet_24,
            titleRes = R.string.nav_budget,
            subtitleRes = R.string.settings_budget_row_subtitle,
            destinationId = R.id.budgetFragment
        )
        bindNavigationRow(
            row = binding.loansRow,
            iconRes = R.drawable.investissement_24,
            titleRes = R.string.utility_loans_title,
            subtitleRes = R.string.settings_loans_row_subtitle,
            destinationId = R.id.loansFragment
        )
        bindNavigationRow(
            row = binding.statisticsRow,
            iconRes = R.drawable.rapport,
            titleRes = R.string.nav_statistics,
            subtitleRes = R.string.settings_statistics_row_subtitle,
            destinationId = R.id.statisticsFragment
        )
    }

    /** Simple raccourci vers [BackupFragment] déjà existant (voir sa doc de tête) — icône
     * réutilisée telle quelle depuis `UtilityCatalog` (`sauvegarde`), aucune logique
     * d'export/import dupliquée ici. */
    private fun setUpBackupSection(binding: FragmentSettingsBinding) {
        bindNavigationRow(
            row = binding.backupRow,
            iconRes = R.drawable.sauvegarde,
            titleRes = R.string.backup_screen_title,
            subtitleRes = R.string.settings_backup_row_subtitle,
            destinationId = R.id.backupFragment
        )
    }

    /**
     * Factorise le motif répété par TOUTES les lignes de navigation pure de cet écran (icône +
     * titre + sous-titre + clic → destination) — évite la duplication quasi identique qui existait
     * jusqu'ici sur 10 lignes distinctes (changePasswordRow, securityQuestionRow, accountsRow,
     * transactionsRow, categoriesRow, recurringRow, budgetRow, loansRow, statisticsRow,
     * backupRow). Volontairement PAS utilisé par `biometricLockRow` (switch, pas de navigation) ni
     * `languageRow` (ligne désactivée, voir sa doc) : ces deux-là restent des cas particuliers
     * gérés explicitement.
     */
    private fun bindNavigationRow(
        row: ItemSettingsRowBinding,
        @DrawableRes iconRes: Int,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int,
        @IdRes destinationId: Int
    ) {
        row.rowIcon.setImageResource(iconRes)
        row.rowTitle.setText(titleRes)
        row.rowSubtitle.setText(subtitleRes)
        row.root.setOnClickListener {
            findNavController().navigate(destinationId, null, NavAnimations.push)
        }
    }

    /**
     * Section STATIQUE (voir sa doc dans fragment_settings.xml) : aucun état à observer, aucune
     * navigation — remplit une seule fois dans `onViewCreated`, comme `setUpProfileRow`.
     * `versionName` lu via [android.content.pm.PackageManager] (pas de constante codée en dur) :
     * il évolue à chaque publication sans jamais nécessiter de toucher à ce fichier.
     */
    private fun setUpAboutSection(binding: FragmentSettingsBinding) {
        binding.aboutVersion.text = getString(R.string.settings_about_version, appVersionName())
    }

    /** `getPackageInfo(String, Int)` est dépréciée depuis l'API 33 au profit de la surcharge à
     * [android.content.pm.PackageManager.PackageInfoFlags] — les deux chemins sont conservés pour
     * couvrir `minSdk` (26) jusqu'à la dernière API sans avertissement de dépréciation.
     * `runCatching` : cet appel peut théoriquement échouer (voir sa documentation), une valeur par
     * défaut vaut mieux qu'un crash sur un simple texte informatif. */
    private fun appVersionName(): String {
        val context = requireContext()
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName
        }.getOrNull() ?: "1.0"
    }

    /** Le switch reste visible mais désactivé (jamais masqué) si la biométrie est indisponible sur
     * l'appareil — même comportement que [com.arzikina.ne.presentation.profile.ProfileFragment.renderBiometricLock],
     * voir sa doc. */
    private fun renderBiometricLock(binding: FragmentSettingsBinding, state: BiometricLockUiState) {
        binding.biometricLockRow.rowSwitch.isEnabled = state.isAvailable
        if (binding.biometricLockRow.rowSwitch.isChecked != state.isEnabled) {
            binding.biometricLockRow.rowSwitch.isChecked = state.isEnabled
        }
        binding.biometricLockRow.rowSubtitle.text = getString(
            if (state.isAvailable) {
                R.string.profile_biometric_lock_description
            } else {
                R.string.profile_biometric_lock_unavailable_description
            }
        )
    }

    private fun render(binding: FragmentSettingsBinding, state: SettingsUiState) {
        binding.profileName.text = state.fullName
        if (state.profilePhotoUri != null) {
            binding.profileAvatar.load(state.profilePhotoUri)
        }

        binding.currencyRow.rowValue.text = state.currencyCode
        binding.themeRow.rowValue.text = getString(state.themeMode.displayTextRes())
    }

    /** Liste FERMÉE (voir `SupportedCurrency`, même source que le formulaire de compte) — une
     * seule sélection à la fois, ferme le dialogue immédiatement (pas de bouton "Valider" séparé,
     * cohérent avec le comportement d'un réglage instantané). */
    private fun showCurrencyPicker() {
        val currencies = SupportedCurrency.entries
        val labels = currencies.map { "${it.displayName} (${it.symbol})" }.toTypedArray()
        val currentIndex = currencies.indexOfFirst { it.code == viewModel.uiState.value.currencyCode }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_dialog_currency_title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, position ->
                viewModel.onCurrencyChange(currencies[position].code)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showThemePicker() {
        val modes = ThemeMode.entries
        val labels = modes.map { getString(it.displayTextRes()) }.toTypedArray()
        val currentIndex = modes.indexOf(viewModel.uiState.value.themeMode).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_dialog_theme_title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, position ->
                viewModel.onThemeModeChange(modes[position])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
