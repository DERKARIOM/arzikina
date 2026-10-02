package com.naniger.arzikina.presentation.utilities.loans

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentLoanDetailBinding
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.LoanPayment
import com.naniger.arzikina.domain.model.LoanType
import com.naniger.arzikina.domain.model.outstandingAmount
import com.naniger.arzikina.presentation.components.ConfirmDialogs
import com.naniger.arzikina.presentation.components.NavAnimations
import com.naniger.arzikina.presentation.components.TimePickerHelper
import com.naniger.arzikina.presentation.components.displayName
import com.naniger.arzikina.util.AppResult
import com.naniger.arzikina.util.LoanDateTime
import com.naniger.arzikina.util.Money
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Détail d'un prêt/emprunt (voir maquette), atteint en cliquant sur une carte de [LoansFragment].
 *
 * Suppression du prêt/emprunt via le menu "⋮" de la Toolbar (réutilise `res/menu/form_delete_menu.xml`,
 * un menu "Supprimer" seul déjà générique dans l'app — voir sa doc). Pas d'action "Modifier" :
 * l'édition n'est pas prévue à ce stade du plan de développement Prêts/Emprunts (voir la doc de
 * `nav_graph.xml`, destination `loanFormFragment`, "toujours en création").
 *
 * « Transformer en cadeau » (⋮, voir [confirmConvertToGift]) : visible seulement si la dette peut
 * encore l'être ; une fois transformée, une carte dédiée l'indique et les actions de
 * remboursement disparaissent.
 *
 * "Enregistrer un remboursement" ouvre désormais [LoanPaymentFormFragment] (Étape 6, Gestion des
 * remboursements) ; chaque ligne de la section "Versements" peut aussi être supprimée
 * individuellement (voir [confirmDeletePayment]).
 */
@AndroidEntryPoint
class LoanDetailFragment : Fragment(R.layout.fragment_loan_detail) {

    private val viewModel: LoanDetailViewModel by viewModels()
    private var binding: FragmentLoanDetailBinding? = null
    private val adapter = LoanDetailAdapter(onDeletePayment = { payment -> confirmDeletePayment(payment) })

    /** Dernier état connu, pour [confirmDelete] (même raisonnement que
     * `AccountDetailFragment.confirmDelete`, qui lit `viewModel.uiState.value` directement). */
    private var latestUiState: LoanDetailUiState? = null

    /** Évite de déclencher [findNavController.navigateUp] plusieurs fois si [AppResult.Error]
     * est émis à répétition (voir [render]) : le prêt/emprunt affiché a été supprimé depuis un
     * autre écran (ex. `LoansFragment`) pendant que celui-ci restait ouvert. */
    private var hasNavigatedAwayOnError = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentLoanDetailBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        viewBinding.detailList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.detailList.adapter = adapter
        viewBinding.addPaymentButton.setOnClickListener { navigateToPaymentForm() }

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

    private fun setUpToolbar(binding: FragmentLoanDetailBinding) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.loan_detail_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_convert_to_gift -> {
                    confirmConvertToGift()
                    true
                }
                R.id.action_edit_loan_date_time -> {
                    editStartDateTime()
                    true
                }
                R.id.action_delete_item -> {
                    confirmDelete()
                    true
                }
                else -> false
            }
        }
    }

    private fun render(state: AppResult<LoanDetailUiState>) {
        val binding = binding ?: return
        // Le prêt/emprunt affiché a été supprimé depuis un autre écran (voir la doc de
        // [LoanDetailViewModel.uiState], `AppResult.Error("Prêt/emprunt introuvable")`) : sans ce
        // guard, l'écran resterait figé avec le dernier état connu au lieu de prévenir l'utilisateur
        // et de revenir en arrière.
        if (state is AppResult.Error) {
            if (!hasNavigatedAwayOnError) {
                hasNavigatedAwayOnError = true
                Snackbar.make(binding.root, R.string.loan_detail_not_found_message, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            return
        }
        if (state !is AppResult.Success) return
        val uiState = state.data
        latestUiState = uiState
        renderActions(binding, uiState)

        val rows = buildList {
            add(LoanDetailListRow.Header(uiState))
            addAll(
                uiState.payments.map { payment ->
                    LoanDetailListRow.PaymentRow(
                        payment = payment,
                        accountName = uiState.accountsById[payment.accountId]?.displayName(requireContext()).orEmpty(),
                        loanType = uiState.loan.type,
                        currencyCode = uiState.currencyCode,
                        canDelete = uiState.canDeletePayments
                    )
                }
            )
        }
        adapter.submitList(rows)
    }

    /**
     * « Modifier la date et l'heure » : sélecteur de date natif puis sélecteur d'heure natif
     * (mêmes composants que le formulaire de création), pré-positionnés sur la valeur actuelle.
     * Même conversion que `LoanFormFragment.showDatePicker` : le jour choisi (UTC côté
     * MaterialDatePicker) est relu en date LOCALE — aucun décalage de fuseau.
     */
    private fun editStartDateTime() {
        val loan = latestUiState?.loan ?: return
        val currentDate = LoanDateTime.toLocalDate(loan.startDate)
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.loan_form_start_date_label)
            .setSelection(currentDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            val date = Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            // Ancienne donnée sans heure : proposer l'heure actuelle plutôt que 00:00.
            val initialTime = if (LoanDateTime.hasExplicitTime(loan.startDate)) {
                LoanDateTime.toLocalTime(loan.startDate)
            } else {
                LocalTime.now()
            }
            TimePickerHelper.show(
                context = requireContext(),
                fragmentManager = parentFragmentManager,
                initialHour = initialTime.hour,
                initialMinute = initialTime.minute,
                titleText = getString(R.string.loan_form_start_time_label),
                tag = "loan_detail_time_picker"
            ) { hour, minute ->
                val dayMillis = LoanDateTime.withDate(loan.startDate, date)
                viewModel.updateStartDateTime(LoanDateTime.withTime(dayMillis, hour, minute))
            }
        }
        picker.show(parentFragmentManager, "loan_detail_date_picker")
    }

    /**
     * Actions dépendant de l'état de la dette : « Transformer en cadeau » (⋮) uniquement si la
     * transformation est permise ; « Enregistrer un remboursement » masqué dès que la dette est
     * éteinte (remboursée ou offerte) — il n'y a plus rien à rembourser.
     */
    private fun renderActions(binding: FragmentLoanDetailBinding, uiState: LoanDetailUiState) {
        binding.toolbar.menu.findItem(R.id.action_convert_to_gift)?.isVisible = uiState.canConvertToGift
        if (uiState.isSettled) binding.addPaymentButton.hide() else binding.addPaymentButton.show()
    }

    /**
     * Confirmation avant transformation : titre selon le sens (prêt/emprunt), conséquence (plus
     * une dette), montant exact concerné — en précisant, si une partie a déjà été remboursée, que
     * SEUL le reste devient un cadeau — et libellé de la transaction qui sera visible dans
     * l'historique.
     */
    private fun confirmConvertToGift() {
        val uiState = latestUiState ?: return
        if (!uiState.canConvertToGift) return
        val loan = uiState.loan
        val description = LoanGiftDescription.build(requireContext(), loan.type, uiState.personName)
        val giftAmount = Money.format(CurrencyAmount(uiState.currencyCode, loan.outstandingAmount()))
        val amountLine = if (loan.amountRepaid > 0L) {
            val repaid = Money.format(CurrencyAmount(uiState.currencyCode, loan.amountRepaid))
            getString(R.string.loan_gift_confirm_partial, giftAmount, repaid)
        } else {
            getString(R.string.loan_gift_confirm_full, giftAmount)
        }
        val message = listOf(
            getString(R.string.loan_gift_confirm_message),
            amountLine,
            getString(R.string.loan_gift_confirm_label, description)
        ).joinToString(separator = "\n\n")
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(
                if (loan.type == LoanType.LENT) R.string.loan_gift_confirm_title_lent else R.string.loan_gift_confirm_title_borrowed
            ),
            message = message,
            confirmLabel = getString(R.string.loan_gift_action),
            onConfirm = { viewModel.convertToGift(description) }
        )
    }

    private fun handleEvent(event: LoanDetailEvent) {
        val binding = binding ?: return
        val messageRes = when (event) {
            LoanDetailEvent.StartNotBeforeDue -> R.string.error_due_date_before_start
            LoanDetailEvent.StartAfterPayment -> R.string.loan_detail_start_after_payment_error
            LoanDetailEvent.ConvertedToGift -> R.string.loan_gift_done_message
            LoanDetailEvent.GiftNotAllowed -> R.string.loan_gift_not_convertible_error
        }
        Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG).show()
    }

    private fun confirmDelete() {
        val uiState = latestUiState ?: return
        val title = uiState.loan.description.ifBlank { getString(defaultLoanTitleRes(uiState.loan.type)) }
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.loan_detail_delete_title),
            message = getString(R.string.loan_detail_delete_message, title),
            onConfirm = {
                viewModel.deleteLoan()
                findNavController().navigateUp()
            }
        )
    }

    private fun confirmDeletePayment(payment: LoanPayment) {
        val uiState = latestUiState ?: return
        val amountLabel = Money.format(CurrencyAmount(uiState.currencyCode, payment.amount))
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.loan_payment_delete_title),
            message = getString(R.string.loan_payment_delete_message, amountLabel),
            onConfirm = { viewModel.deletePayment(payment.id) }
        )
    }

    private fun navigateToPaymentForm() {
        findNavController().navigate(R.id.loanPaymentFormFragment, LoanPaymentFormFragmentArgs(loanId = viewModel.loanId).toBundle(), NavAnimations.push)
    }
}
