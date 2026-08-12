package com.arzikina.ne.presentation.categories

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentCategoriesBinding
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.util.AppResult
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Liste des catégories, filtrable par type. Reconstruit en XML/Views (voir
 * instructions projet) ; [CategoriesViewModel] est inchangé.
 */
@AndroidEntryPoint
class CategoriesFragment : Fragment(R.layout.fragment_categories) {

    private val viewModel: CategoriesViewModel by viewModels()
    private var binding: FragmentCategoriesBinding? = null
    private val adapter = CategoriesAdapter(
        onClick = { category -> navigateToForm(category.id) },
        onDeleteClick = { category -> confirmDelete(category) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentCategoriesBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.categoriesList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.categoriesList.adapter = adapter
        viewBinding.addCategoryButton.setOnClickListener { navigateToForm(categoryId = 0L) }

        viewBinding.filterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val filter = when (checkedId) {
                R.id.filterExpense -> CategoryFilter.EXPENSE
                R.id.filterIncome -> CategoryFilter.INCOME
                else -> CategoryFilter.ALL
            }
            viewModel.onFilterChange(filter)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(state) } }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun handleEvent(event: CategoriesEvent) {
        val binding = binding ?: return
        when (event) {
            CategoriesEvent.DeleteBlocked ->
                Snackbar.make(binding.root, R.string.categories_delete_blocked_message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun render(state: AppResult<List<Category>>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return

        val hasCategories = state.data.isNotEmpty()
        binding.categoriesList.visibility = if (hasCategories) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasCategories) View.GONE else View.VISIBLE
        adapter.submitList(state.data)
    }

    private fun confirmDelete(category: Category) {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.categories_delete_title),
            message = getString(R.string.categories_delete_message, category.name),
            onConfirm = { viewModel.deleteCategory(category.id) }
        )
    }

    private fun navigateToForm(categoryId: Long) {
        findNavController().navigate(R.id.categoryFormFragment, bundleOf("categoryId" to categoryId))
    }
}
