package com.arzikina.ne.presentation.utilities.loans

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
import com.arzikina.ne.databinding.FragmentLoanDetailBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.LoanPayment
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.Money
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Détail d'un prêt/emprunt (voir maquette), atteint en cliquant sur une carte de [LoansFragment].
 *
 * Suppression du prêt/emprunt via le menu "⋮" de la Toolbar (réutilise `res/menu/form_delete_menu.xml`,
 * un menu "Supprimer" seul déjà générique dans l'app — voir sa doc). Pas d'action "Modifier" :
 * l'édition n'est pas prévue à ce stade du plan de développement Prêts/Emprunts (voir la doc de
 * `nav_graph.xml`, destination `loanFormFragment`, "toujours en création").
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
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentLoanDetailBinding) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.form_delete_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
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

        val rows = buildList {
            add(LoanDetailListRow.Header(uiState))
            addAll(
                uiState.payments.map { payment ->
                    LoanDetailListRow.PaymentRow(
                        payment = payment,
                        accountName = uiState.accountNamesById[payment.accountId].orEmpty(),
                        loanType = uiState.loan.type,
                        currencyCode = uiState.currencyCode
                    )
                }
            )
        }
        adapter.submitList(rows)
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
        findNavController().navigate(R.id.loanPaymentFormFragment, bundleOf("loanId" to viewModel.loanId), NavAnimations.fade)
    }
}
