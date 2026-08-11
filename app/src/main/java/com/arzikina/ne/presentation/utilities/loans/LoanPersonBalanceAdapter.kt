package com.arzikina.ne.presentation.utilities.loans

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemLoanPersonBalanceBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.util.Money

/**
 * Classement "Par personne" (écran Statistiques Prêts/Emprunts, voir la doc de [LoanPersonBalanceItem]) :
 * une ligne par (personne, devise), déjà triée par [LoanStatisticsViewModel.computePersonBalances]
 * (montant absolu décroissant).
 */
class LoanPersonBalanceAdapter : ListAdapter<LoanPersonBalanceItem, LoanPersonBalanceAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLoanPersonBalanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemLoanPersonBalanceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LoanPersonBalanceItem) {
            val context = binding.root.context

            binding.personAvatar.text = personAvatarInitial(item.personName)
            binding.personAvatar.backgroundTintList = ColorStateList.valueOf(personAvatarColorArgb(item.personName).toInt())
            binding.personName.text = item.personName

            // Positif = la personne doit de l'argent à l'utilisateur (vert, même sens que "Total
            // reçu" sur l'écran principal) ; négatif = l'utilisateur lui doit de l'argent (rouge).
            val isOwedToUser = item.netAmountMinor > 0L
            val amountText = Money.format(CurrencyAmount(item.currencyCode, kotlin.math.abs(item.netAmountMinor)))
            binding.netAmountValue.text = "${if (isOwedToUser) "+" else "-"}$amountText"
            binding.netAmountValue.setTextColor(
                ContextCompat.getColor(context, if (isOwedToUser) R.color.loan_lent_color else R.color.expense_red)
            )
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LoanPersonBalanceItem>() {
            override fun areItemsTheSame(oldItem: LoanPersonBalanceItem, newItem: LoanPersonBalanceItem): Boolean =
                oldItem.personName == newItem.personName && oldItem.currencyCode == newItem.currencyCode

            override fun areContentsTheSame(oldItem: LoanPersonBalanceItem, newItem: LoanPersonBalanceItem): Boolean =
                oldItem == newItem
        }
    }
}
