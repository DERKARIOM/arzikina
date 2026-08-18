package com.arzikina.ne.presentation.utilities.financialplan

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentFinancialPlanItemFormBinding
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.PlanItemPriority
import com.arzikina.ne.domain.model.PlanItemStatus
import com.arzikina.ne.presentation.categories.CategoryIconMapper
import com.arzikina.ne.presentation.components.CategoryPickerDialog
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Ajout/modification d'une dépense prévue, atteint depuis le FAB de [FinancialPlanDetailFragment]
 * (création) ou en cliquant sur une ligne de sa liste (édition) — voir [FinancialPlanItemFormViewModel]
 * pour le détail des champs. Catégorie/description/date facultatives, priorité/état toujours
 * renseignés (valeurs par défaut IMPORTANT/TO_PLAN).
 */
@AndroidEntryPoint
class FinancialPlanItemFormFragment : Fragment(R.layout.fragment_financial_plan_item_form) {

    private val viewModel: FinancialPlanItemFormViewModel by viewModels()
    private var binding: FragmentFinancialPlanItemFormBinding? = null

    private var latestCategories: List<Category> = emptyList()

    /** Voir `LoanPaymentFormFragment.hasNavigatedAwayOnNotFound` pour le même raisonnement. */
    private var hasNavigatedAwayOnNotFound = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentFinancialPlanItemFormBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpInputs(viewBinding)
        setUpPickers(viewBinding)
        setUpToggleGroups(viewBinding)
        viewBinding.saveButton.setOnClickListener { viewModel.save() }
        viewBinding.convertButton.setOnClickListener {
            findNavController().navigate(
                R.id.financialPlanItemConvertFragment,
                bundleOf("itemId" to viewModel.itemId),
                NavAnimations.push
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.formState, viewModel.categories) { state, categories -> state to categories }
                        .collect { (state, categories) -> render(state, categories) }
                }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentFinancialPlanItemFormBinding) {
        binding.toolbar.title = getString(
            if (viewModel.isEditMode) R.string.financial_plan_item_form_title_edit else R.string.financial_plan_item_form_title_add
        )
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setUpInputs(binding: FragmentFinancialPlanItemFormBinding) {
        binding.nameInput.doAfterTextChanged { text -> viewModel.onNameChange(text?.toString().orEmpty()) }
        binding.amountInput.doAfterTextChanged { text -> viewModel.onAmountChange(text?.toString().orEmpty()) }
        binding.descriptionInput.doAfterTextChanged { text -> viewModel.onDescriptionChange(text?.toString().orEmpty()) }
    }

    private fun setUpPickers(binding: FragmentFinancialPlanItemFormBinding) {
        binding.categoryField.categoryFieldLabel.text = getString(R.string.financial_plan_item_form_category_label)
        binding.categoryRow.setOnClickListener {
            CategoryPickerDialog.show(
                context = requireContext(),
                categories = latestCategories,
                onSelect = { category -> viewModel.onCategoryChange(category) }
            )
        }

        binding.dateField.dateFieldLabel.text = getString(R.string.financial_plan_item_form_date_label)
        binding.dateSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.onDateToggle(isChecked) }
        binding.dateRow.setOnClickListener { showDatePicker { viewModel.onDateChange(it) } }
    }

    private fun setUpToggleGroups(binding: FragmentFinancialPlanItemFormBinding) {
        binding.priorityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val priority = when (checkedId) {
                R.id.priorityEssential -> PlanItemPriority.ESSENTIAL
                R.id.priorityOptional -> PlanItemPriority.OPTIONAL
                else -> PlanItemPriority.IMPORTANT
            }
            viewModel.onPriorityChange(priority)
        }
        binding.statusGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val status = when (checkedId) {
                R.id.statusDone -> PlanItemStatus.DONE
                R.id.statusCancelled -> PlanItemStatus.CANCELLED
                else -> PlanItemStatus.TO_PLAN
            }
            viewModel.onStatusChange(status)
        }
    }

    private fun showDatePicker(onSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.financial_plan_item_form_date_label)
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            val localDate = Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            onSelected(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        picker.show(parentFragmentManager, "financial_plan_item_form_date_picker")
    }

    private fun handleEvent(event: FinancialPlanItemFormEvent) {
        when (event) {
            FinancialPlanItemFormEvent.Saved -> findNavController().navigateUp()
        }
    }

    private fun render(state: FinancialPlanItemFormState, categories: List<Category>) {
        val binding = binding ?: return
        latestCategories = categories

        if (state.notFound) {
            if (!hasNavigatedAwayOnNotFound) {
                hasNavigatedAwayOnNotFound = true
                Snackbar.make(binding.root, R.string.financial_plan_item_form_not_found_message, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            return
        }

        binding.saveButton.isEnabled = state.isLoaded && !state.isSaving
        // "Enregistrer comme transaction" (Étape 6) : uniquement en édition, pour une dépense pas
        // encore convertie (voir la doc de FinancialPlanItemFormState.isAlreadyConverted) ET pas
        // annulée (Étape 11 — une dépense CANCELLED n'a plus lieu d'être honorée, voir la doc de
        // FinancialPlanRepository.convertItemToTransaction).
        binding.convertButton.visibility =
            if (viewModel.isEditMode && state.isLoaded && !state.isAlreadyConverted && state.status != PlanItemStatus.CANCELLED) {
                View.VISIBLE
            } else {
                View.GONE
            }
        if (state.isLoaded) {
            // Étape 7 — alerte sobre de dépassement, même logique visuelle que
            // FinancialPlansAdapter/FinancialPlanDetailAdapter (voir leur doc) : rouge dès que le
            // reste (hors dépense en cours d'édition, voir FinancialPlanItemFormViewModel.init) est
            // déjà négatif, texte neutre sinon.
            if (state.planRemainingAmount < 0L) {
                val overspentLabel = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, -state.planRemainingAmount))
                binding.planContextLabel.text =
                    getString(R.string.financial_plan_item_form_context_overbudget, state.planName, overspentLabel)
                binding.planContextLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.expense_red))
            } else {
                val remainingLabel = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, state.planRemainingAmount))
                binding.planContextLabel.text = getString(R.string.financial_plan_item_form_context, state.planName, remainingLabel)
                binding.planContextLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.arzikina_on_surface_variant))
            }

            // Avertissement non bloquant : CE montant, à lui seul, dépasserait le reste disponible
            // ci-dessus (indépendant du cas déjà-en-dépassement traité juste au-dessus).
            val amountMinor = Money.parseToMinorUnits(state.amountInput)
            val exceedsRemaining = amountMinor != null && amountMinor > state.planRemainingAmount
            binding.amountExceedsWarningText.visibility = if (exceedsRemaining) View.VISIBLE else View.GONE
            if (exceedsRemaining) {
                val remainingLabel = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, state.planRemainingAmount))
                binding.amountExceedsWarningText.text =
                    getString(R.string.financial_plan_item_form_amount_exceeds_warning, remainingLabel)
            }
        }

        if (binding.nameInput.text?.toString() != state.nameInput) {
            binding.nameInput.setText(state.nameInput)
        }
        binding.nameLayout.error = state.nameError

        if (binding.amountInput.text?.toString() != state.amountInput) {
            binding.amountInput.setText(state.amountInput)
        }
        binding.amountLayout.error = state.amountError

        bindCategoryField(binding, categories.firstOrNull { it.id == state.categoryId })

        if (binding.descriptionInput.text?.toString() != state.descriptionInput) {
            binding.descriptionInput.setText(state.descriptionInput)
        }

        if (binding.dateSwitch.isChecked != state.hasDate) {
            binding.dateSwitch.isChecked = state.hasDate
        }
        binding.dateCard.visibility = if (state.hasDate) View.VISIBLE else View.GONE
        binding.dateField.dateFieldValue.text = formatDate(state.dateMillis)

        val expectedPriorityButtonId = when (state.priority) {
            PlanItemPriority.ESSENTIAL -> R.id.priorityEssential
            PlanItemPriority.IMPORTANT -> R.id.priorityImportant
            PlanItemPriority.OPTIONAL -> R.id.priorityOptional
        }
        if (binding.priorityGroup.checkedButtonId != expectedPriorityButtonId) {
            binding.priorityGroup.check(expectedPriorityButtonId)
        }

        val expectedStatusButtonId = when (state.status) {
            PlanItemStatus.TO_PLAN -> R.id.statusToPlan
            PlanItemStatus.DONE -> R.id.statusDone
            PlanItemStatus.CANCELLED -> R.id.statusCancelled
        }
        if (binding.statusGroup.checkedButtonId != expectedStatusButtonId) {
            binding.statusGroup.check(expectedStatusButtonId)
        }
    }

    /** Voir `AccountFormFragment.bindAccountField`/`LoanPaymentFormFragment.bindAccountField` pour
     * le même raisonnement (petite duplication assumée entre formulaires indépendants). */
    private fun bindCategoryField(binding: FragmentFinancialPlanItemFormBinding, category: Category?) {
        val fieldBinding = binding.categoryField
        if (category != null) {
            fieldBinding.categoryFieldIcon.setImageResource(CategoryIconMapper.iconFor(category.icon))
            fieldBinding.categoryFieldIcon.backgroundTintList = ColorStateList.valueOf(category.colorArgb.toInt())
            fieldBinding.categoryFieldName.text = category.name
        } else {
            fieldBinding.categoryFieldIcon.setImageResource(R.drawable.ic_category_other_24)
            fieldBinding.categoryFieldIcon.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.arzikina_outline))
            fieldBinding.categoryFieldName.text = getString(R.string.financial_plan_item_form_category_none)
        }
    }

    private fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMATTER)

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
