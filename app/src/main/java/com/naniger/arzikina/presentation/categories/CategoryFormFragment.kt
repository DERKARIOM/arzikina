package com.naniger.arzikina.presentation.categories

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentCategoryFormBinding
import com.naniger.arzikina.domain.model.CategoryIcon
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.presentation.components.ColorPickerAdapter
import com.naniger.arzikina.presentation.components.ConfirmDialogs
import com.naniger.arzikina.presentation.components.IconPickerAdapter
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Formulaire d'ajout/édition d'une catégorie. Reconstruit en XML/Views (voir
 * instructions projet) ; [CategoryFormViewModel] est inchangé. Voir
 * [com.naniger.arzikina.presentation.accounts.AccountFormFragment] pour le
 * raisonnement des gardes anti-boucle sur les champs texte.
 */
@AndroidEntryPoint
class CategoryFormFragment : Fragment(R.layout.fragment_category_form) {

    private val viewModel: CategoryFormViewModel by viewModels()
    private var binding: FragmentCategoryFormBinding? = null

    /** Colonnes de la grille (connues après la mesure) et état du bouton « Voir plus ». */
    private var iconColumns = MIN_ICON_COLUMNS

    /** `null` tant que l'utilisateur n'a pas touché au bouton : l'état dépend alors de l'icône choisie. */
    private var iconsExpanded: Boolean? = null
    private var shownIcon: CategoryIcon = CategoryIcon.OTHER

    private val iconPickerAdapter = IconPickerAdapter(
        items = CategoryIcon.entries,
        iconRes = CategoryIconMapper::iconFor,
        selected = CategoryIcon.OTHER,
        onSelect = { icon -> viewModel.onIconChange(icon) }
    )

    private val colorPickerAdapter = ColorPickerAdapter(
        selected = 0xFF10B981L,
        onSelect = { color -> viewModel.onColorChange(color) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentCategoryFormBinding.bind(view)
        binding = viewBinding

        iconsExpanded = savedInstanceState?.takeIf { it.containsKey(STATE_ICONS_EXPANDED) }?.getBoolean(STATE_ICONS_EXPANDED)
        setUpToolbar(viewBinding)
        setUpPickers(viewBinding)

        viewBinding.nameInput.doAfterTextChanged { text ->
            viewModel.onNameChange(text?.toString().orEmpty())
        }
        viewBinding.typeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val type = if (checkedId == R.id.typeIncome) TransactionType.INCOME else TransactionType.EXPENSE
            viewModel.onTypeChange(type)
        }
        viewBinding.saveButton.setOnClickListener { viewModel.save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.formState.collect { state -> render(state) } }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        iconsExpanded?.let { outState.putBoolean(STATE_ICONS_EXPANDED, it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentCategoryFormBinding) {
        binding.toolbar.title = getString(
            if (viewModel.isEditMode) R.string.category_form_title_edit else R.string.category_form_title_add
        )
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.form_delete_menu)
        binding.toolbar.menu.findItem(R.id.action_delete_item).isVisible = viewModel.isEditMode
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_delete_item) {
                confirmDelete()
                true
            } else {
                false
            }
        }
    }

    private fun setUpPickers(binding: FragmentCategoryFormBinding) {
        setUpIconGrid(binding.iconPicker)
        binding.colorPicker.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.colorPicker.adapter = colorPickerAdapter
    }

    /**
     * Les icônes sont nombreuses : grille qui s'adapte à la largeur (autant de colonnes que de cases
     * entières). Pas de défilement imbriqué : la grille s'étale dans le formulaire, qui défile.
     */
    private fun setUpIconGrid(picker: RecyclerView) {
        val cellPx = resources.getDimensionPixelSize(R.dimen.icon_size_large) +
            2 * resources.getDimensionPixelSize(R.dimen.spacing_xs)
        val grid = GridLayoutManager(requireContext(), MIN_ICON_COLUMNS)
        picker.layoutManager = grid
        picker.adapter = iconPickerAdapter
        picker.isNestedScrollingEnabled = false
        // Écouteur PERMANENT (pas doOnLayout, qui ne se déclenche qu'une fois) : le nombre de colonnes
        // suit la largeur réelle (rotation, écran partagé). Une largeur nulle (première passe) est ignorée.
        picker.addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
            val usable = right - left - view.paddingStart - view.paddingEnd
            if (usable <= 0) return@addOnLayoutChangeListener
            val columns = (usable / cellPx).coerceAtLeast(MIN_ICON_COLUMNS)
            if (grid.spanCount != columns) grid.spanCount = columns
            if (iconColumns != columns) {
                iconColumns = columns
                // Pas de notifyDataSetChanged pendant une passe de mise en page : on reporte d'un cran.
                view.post { refreshIcons() }
            }
        }
        binding?.iconToggleButton?.setOnClickListener {
            iconsExpanded = !currentExpanded()
            refreshIcons()
        }
        refreshIcons()
    }

    /**
     * Déplié si l'utilisateur l'a demandé ; sinon replié (2 lignes), SAUF en modification d'une
     * catégorie dont l'icône serait cachée. Une NOUVELLE catégorie s'ouvre toujours repliée, même si
     * son icône par défaut ne tient pas dans les premières cases (elle est alors ajoutée à la liste).
     */
    private fun currentExpanded(): Boolean = iconsExpanded
        ?: (viewModel.isEditMode && CategoryIconVisibility.startsExpanded(CategoryIcon.entries, shownIcon, iconColumns))

    /** Applique « Voir plus / Voir moins » : liste visible et libellé du bouton. */
    private fun refreshIcons() {
        val binding = binding ?: return
        val expanded = currentExpanded()
        iconPickerAdapter.setItems(CategoryIconVisibility.visible(CategoryIcon.entries, shownIcon, expanded, iconColumns))
        binding.iconToggleButton.visibility =
            if (CategoryIconVisibility.needsToggle(CategoryIcon.entries.size, iconColumns)) View.VISIBLE else View.GONE
        binding.iconToggleButton.setText(if (expanded) R.string.category_form_icons_less else R.string.category_form_icons_more)
    }

    private fun render(state: CategoryFormState) {
        val binding = binding ?: return

        if (binding.nameInput.text?.toString() != state.name) {
            binding.nameInput.setText(state.name)
        }
        binding.nameLayout.error = state.nameError?.let { getString(it) }

        val expectedTypeButtonId = if (state.type == TransactionType.INCOME) R.id.typeIncome else R.id.typeExpense
        if (binding.typeGroup.checkedButtonId != expectedTypeButtonId) {
            binding.typeGroup.check(expectedTypeButtonId)
        }

        iconPickerAdapter.setSelected(state.icon)
        // Icône chargée (modification) ou changée : on réévalue ce qui doit être montré, sans recomposer la grille à chaque clic visible.
        if (state.icon != shownIcon) {
            val hidden = state.icon !in CategoryIconVisibility.visible(CategoryIcon.entries, shownIcon, currentExpanded(), iconColumns)
            shownIcon = state.icon
            if (hidden) refreshIcons()
        }
        colorPickerAdapter.setSelected(state.colorArgb)
    }

    private fun handleEvent(event: CategoryFormEvent) {
        when (event) {
            CategoryFormEvent.Saved, CategoryFormEvent.Deleted -> findNavController().navigateUp()
            CategoryFormEvent.DeleteBlocked -> {
                val binding = binding ?: return
                Snackbar.make(binding.root, R.string.categories_delete_blocked_message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.categories_delete_title),
            message = getString(R.string.categories_delete_message, viewModel.formState.value.name),
            onConfirm = { viewModel.delete() }
        )
    }

    private companion object {
        const val STATE_ICONS_EXPANDED = "category_icons_expanded"
        const val MIN_ICON_COLUMNS = 4
    }
}
