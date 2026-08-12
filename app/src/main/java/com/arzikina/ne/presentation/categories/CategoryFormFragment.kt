package com.arzikina.ne.presentation.categories

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentCategoryFormBinding
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.presentation.components.ColorPickerAdapter
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.components.IconPickerAdapter
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Formulaire d'ajout/édition d'une catégorie. Reconstruit en XML/Views (voir
 * instructions projet) ; [CategoryFormViewModel] est inchangé. Voir
 * [com.arzikina.ne.presentation.accounts.AccountFormFragment] pour le
 * raisonnement des gardes anti-boucle sur les champs texte.
 */
@AndroidEntryPoint
class CategoryFormFragment : Fragment(R.layout.fragment_category_form) {

    private val viewModel: CategoryFormViewModel by viewModels()
    private var binding: FragmentCategoryFormBinding? = null

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
        binding.iconPicker.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.iconPicker.adapter = iconPickerAdapter
        binding.colorPicker.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.colorPicker.adapter = colorPickerAdapter
    }

    private fun render(state: CategoryFormState) {
        val binding = binding ?: return

        if (binding.nameInput.text?.toString() != state.name) {
            binding.nameInput.setText(state.name)
        }
        binding.nameLayout.error = state.nameError

        val expectedTypeButtonId = if (state.type == TransactionType.INCOME) R.id.typeIncome else R.id.typeExpense
        if (binding.typeGroup.checkedButtonId != expectedTypeButtonId) {
            binding.typeGroup.check(expectedTypeButtonId)
        }

        iconPickerAdapter.setSelected(state.icon)
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
}
