package com.arzikina.ne.presentation.utilities.loans

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemLoanStatusBreakdownBinding
import com.arzikina.ne.domain.model.LoanStatus
import kotlin.math.roundToInt

/**
 * Légende de la répartition par statut (voir [CategoryPieView][com.arzikina.ne.presentation.statistics.CategoryPieView],
 * l'anneau affiche les proportions, cette liste donne le décompte chiffré par statut avec la même
 * couleur que sa part d'anneau) — même principe que `CategoryBreakdownAdapter` (écran Statistiques
 * général).
 *
 * [statusColorRes] : PAS [loanStatusDisplay] (qui a besoin d'un [com.arzikina.ne.domain.model.LoanType],
 * sans objet ici puisque cette répartition agrège prêts ET emprunts ensemble) — couleurs fixes
 * dédiées à cet écran, indépendantes du type.
 */
class LoanStatusBreakdownAdapter :
    ListAdapter<LoanStatusBreakdownItem, LoanStatusBreakdownAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLoanStatusBreakdownBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemLoanStatusBreakdownBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LoanStatusBreakdownItem) {
            val context = binding.root.context
            binding.colorDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, statusColorRes(item.status)))
            binding.statusLabel.text = context.getString(statusLabelRes(item.status))
            binding.countValue.text = context.getString(
                R.string.loan_statistics_status_count,
                item.count,
                (item.percentage * 100).roundToInt()
            )
        }

        private fun statusLabelRes(status: LoanStatus): Int = when (status) {
            LoanStatus.ONGOING -> R.string.loans_status_ongoing
            LoanStatus.REPAID -> R.string.loans_status_repaid
            LoanStatus.OVERDUE -> R.string.loans_status_overdue
            LoanStatus.UPCOMING -> R.string.loans_status_upcoming
        }

        private fun statusColorRes(status: LoanStatus): Int = when (status) {
            LoanStatus.ONGOING -> R.color.arzikina_primary
            LoanStatus.REPAID -> R.color.loan_lent_color
            LoanStatus.OVERDUE -> R.color.expense_red
            LoanStatus.UPCOMING -> R.color.arzikina_outline
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LoanStatusBreakdownItem>() {
            override fun areItemsTheSame(oldItem: LoanStatusBreakdownItem, newItem: LoanStatusBreakdownItem): Boolean =
                oldItem.status == newItem.status

            override fun areContentsTheSame(oldItem: LoanStatusBreakdownItem, newItem: LoanStatusBreakdownItem): Boolean =
                oldItem == newItem
        }
    }
}
