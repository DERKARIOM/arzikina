package com.arzikina.ne.presentation.budget

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemBudgetModernBinding
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.presentation.categories.CategoryIconMapper
import com.arzikina.ne.util.BudgetPeriodStatus
import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.Money
import com.arzikina.ne.util.daysRemaining
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Liste des budgets avec leur progression sur la période en cours — PostCard "moderne" (voir
 * item_budget_modern.xml, cahier des charges "Personnalisation des PostCards — Fragment Budget
 * uniquement"). Utilisé UNIQUEMENT par [BudgetFragment] : [com.arzikina.ne.presentation.dashboard.DashboardFragment]
 * continue de réutiliser [BudgetAdapter]/`item_budget.xml` tels quels, sans aucun changement.
 *
 * Même signature de constructeur que [BudgetAdapter] (`onClick`/`onDeleteClick`) : [BudgetFragment]
 * n'a donc qu'à changer la CLASSE d'adapter instanciée, ses callbacks restent identiques
 * (`navigateToForm`/`confirmDelete`, inchangés). Aucun recalcul ici : [BudgetUiItem] (statut,
 * montants, progression) reste entièrement fourni par [BudgetViewModel]/[BudgetPeriodStatus]/
 * [daysRemaining], seule la PRÉSENTATION change.
 */
class BudgetModernAdapter(
    private val onClick: (BudgetUiItem) -> Unit,
    private val onDeleteClick: (BudgetUiItem) -> Unit
) : ListAdapter<BudgetUiItem, BudgetModernAdapter.ViewHolder>(DIFF_CALLBACK) {

    /**
     * Position la plus haute déjà animée à l'apparition (voir [animateAppearance]) : une carte ne
     * joue son animation d'entrée qu'une seule fois, la première fois qu'une position est liée —
     * un simple changement de valeur (montant/progression) redéclenche [onBindViewHolder] sur un
     * ViewHolder DÉJÀ affiché à cette position, qui ne doit surtout pas rejouer l'animation (cahier
     * des charges section 15 : "éviter de recréer brutalement toute la Card" quand une valeur
     * change) — seule une position encore jamais vue est concernée.
     */
    private var lastAnimatedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemBudgetModernBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onDeleteClick)
        if (position > lastAnimatedPosition) {
            lastAnimatedPosition = position
            animateAppearance(holder.itemView)
        }
    }

    /** Fondu + léger glissement vers le haut, 260 ms (fourchette 200-300 ms demandée) — même
     * technique `View.animate()` déjà utilisée dans ce projet (voir `AccountsFragment.animateTabSwitch`),
     * aucune nouvelle dépendance d'animation. */
    private fun animateAppearance(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = view.resources.getDimension(R.dimen.spacing_m)
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ENTRANCE_ANIMATION_DURATION_MS)
            .start()
    }

    class ViewHolder(private val binding: ItemBudgetModernBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BudgetUiItem, onClick: (BudgetUiItem) -> Unit, onDeleteClick: (BudgetUiItem) -> Unit) {
            val context = binding.root.context

            bindHeader(context, item)
            bindStatusBadge(context, item)
            bindFinancialColumns(context, item)
            bindProgress(context, item)
            bindPeriodAndDaysRemaining(context, item)

            binding.root.setOnClickListener { onClick(item) }
            binding.menuButton.setOnClickListener { anchor -> showActionsMenu(anchor, item, onClick, onDeleteClick) }
        }

        /** Icône toujours sur une pastille translucide neutre (jamais la couleur de la catégorie,
         * voir item_budget_modern.xml) : une teinte de catégorie arbitraire jurerait sur le dégradé
         * vert fixe de cette carte — contrairement à item_budget.xml (fond neutre), où elle reste
         * pertinente. */
        private fun bindHeader(context: Context, item: BudgetUiItem) {
            val category = item.category
            binding.categoryIcon.setImageResource(
                category?.let { CategoryIconMapper.iconFor(it.icon) } ?: R.drawable.ic_category_other_24
            )
            binding.categoryIcon.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.arzikina_chip_on_budget_card))
            binding.categoryName.text = category?.name ?: context.getString(R.string.transaction_uncategorized)
        }

        /**
         * Priorité "Dépassé" sur le statut de période (cahier des charges section 4) : un budget
         * ONGOING dont le plafond est dépassé doit afficher "Dépassé", pas "En cours" — l'inverse
         * masquerait l'information la plus importante pour l'utilisateur. `status == null` (budget
         * récurrent legacy, voir [BudgetPeriodStatus.of]) traité comme ONGOING : un budget
         * hebdomadaire/mensuel est par nature toujours "en cours" tant qu'il existe.
         */
        private fun bindStatusBadge(context: Context, item: BudgetUiItem) {
            val today = LocalDate.now()
            val status = BudgetPeriodStatus.of(item.budget.startDate, item.budget.endDate, today)
            val isOverspent = item.progress > 1f

            val (textRes, dotColorRes, dotAlpha) = when {
                isOverspent -> Triple(R.string.budget_modern_status_overspent, R.color.expense_red, 1f)
                status == BudgetPeriodStatus.UPCOMING -> Triple(R.string.budget_filter_upcoming, R.color.warning_amber, 1f)
                status == BudgetPeriodStatus.COMPLETED -> Triple(R.string.budget_status_completed, R.color.white, 0.5f)
                else -> Triple(R.string.budget_filter_ongoing, R.color.white, 1f)
            }
            binding.statusText.text = context.getString(textRes)
            binding.statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, dotColorRes))
            binding.statusDot.alpha = dotAlpha
        }

        private fun bindFinancialColumns(context: Context, item: BudgetUiItem) {
            binding.totalValue.text = Money.format(CurrencyAmount(item.budget.currencyCode, item.budget.limitAmount))
            binding.spentValue.text = Money.format(CurrencyAmount(item.budget.currencyCode, item.spentMinor))

            val isOverspent = item.progress > 1f
            val remainingMinor = item.budget.limitAmount - item.spentMinor
            binding.remainingValue.text = Money.format(CurrencyAmount(item.budget.currencyCode, remainingMinor))
            // Négatif en cas de dépassement (voir cahier des charges section 5, exemple "-7 000 CFA") :
            // Money.format garde nativement le signe, aucune logique de préfixe séparée à écrire ici
            // (contrairement à BudgetAdapter.remainingLabel, qui préfixe "Dépassement :"/"Restant :"
            // — inutile ici, la valeur signée + le badge "Dépassé" suffisent).
            binding.remainingLabel.text = context.getString(R.string.budget_modern_remaining_label)
            binding.remainingIcon.alpha = if (isOverspent) 0.85f else 1f
        }

        private fun bindProgress(context: Context, item: BudgetUiItem) {
            val isOverspent = item.progress > 1f
            binding.progressBar.progress = (item.progress.coerceIn(0f, 1f) * 100).roundToInt()
            binding.progressBar.setIndicatorColor(
                ContextCompat.getColor(context, if (isOverspent) R.color.expense_red else R.color.white)
            )
            // Pourcentage textuel NON borné à 100 (contrairement à la barre elle-même, qui ne peut
            // pas visuellement dépasser sa largeur) : un budget dépassé doit pouvoir afficher "114 %",
            // cohérent avec le badge "Dépassé" et le montant négatif de bindFinancialColumns.
            val percent = (item.progress * 100).roundToInt()
            binding.percentUsedLabel.text = context.getString(R.string.budget_modern_percent_used, percent)
            binding.percentOfBudgetLabel.text = context.getString(R.string.budget_modern_percent_of_budget, percent)
        }

        /** Même logique que `BudgetAdapter` (période fixe vs récurrent legacy, voir sa doc) —
         * seule la présentation change (deux éléments séparés : `periodValue` + `daysChip`, au lieu
         * d'une seule ligne "Expire le ..."). */
        private fun bindPeriodAndDaysRemaining(context: Context, item: BudgetUiItem) {
            val today = LocalDate.now()
            val status = BudgetPeriodStatus.of(item.budget.startDate, item.budget.endDate, today)

            if (status != null) {
                val start = DatePeriods.toLocalDate(item.budget.startDate!!)
                val end = DatePeriods.toLocalDate(item.budget.endDate!!)
                binding.periodValue.text = context.getString(
                    R.string.budget_modern_period_range,
                    start.format(DATE_FORMATTER),
                    end.format(DATE_FORMATTER)
                )
                binding.daysValue.text = when (status) {
                    BudgetPeriodStatus.COMPLETED -> context.getString(R.string.budget_modern_completed_chip)
                    BudgetPeriodStatus.UPCOMING -> {
                        val daysUntilStart = ChronoUnit.DAYS.between(today, DatePeriods.toLocalDate(item.budget.startDate!!))
                        context.getString(R.string.budget_modern_starts_in_days, daysUntilStart)
                    }
                    BudgetPeriodStatus.ONGOING -> {
                        val remaining = daysRemaining(item.budget.endDate!!, today)
                        context.getString(R.string.budget_modern_days_remaining, remaining)
                    }
                }
            } else {
                // Budget récurrent legacy (pas de startDate/endDate, voir Budget.period) : jamais de
                // dates fictives (cahier des charges section 7) — libellé "Hebdomadaire"/"Mensuel",
                // même calcul de fin de période courante que BudgetAdapter.
                binding.periodValue.text = context.getString(
                    if (item.budget.period == BudgetPeriod.WEEKLY) R.string.budget_period_weekly else R.string.budget_period_monthly
                )
                val periodEnd = DatePeriods.currentPeriodEnd(item.budget.period, today)
                val daysUntilExpiration = ChronoUnit.DAYS.between(today, periodEnd)
                binding.daysValue.text = context.getString(R.string.budget_modern_days_remaining, daysUntilExpiration)
            }
        }

        /** Menu ⋮ (voir menu/budget_item_actions_menu.xml) : réutilise EXACTEMENT les callbacks déjà
         * fournis par [BudgetFragment] (`navigateToForm`/`confirmDelete`, inchangés) — un tap sur la
         * carte elle-même déclenche aussi `onClick` (édition directe), ce menu n'ajoute qu'un accès
         * explicite à la suppression, absente par défaut de cette carte (contrairement à
         * item_budget.xml et son `deleteButton` toujours visible). */
        private fun showActionsMenu(
            anchor: View,
            item: BudgetUiItem,
            onClick: (BudgetUiItem) -> Unit,
            onDeleteClick: (BudgetUiItem) -> Unit
        ) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.inflate(R.menu.budget_item_actions_menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit_budget -> {
                        onClick(item)
                        true
                    }
                    R.id.action_delete_budget -> {
                        onDeleteClick(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BudgetUiItem>() {
            override fun areItemsTheSame(oldItem: BudgetUiItem, newItem: BudgetUiItem): Boolean =
                oldItem.budget.id == newItem.budget.id

            override fun areContentsTheSame(oldItem: BudgetUiItem, newItem: BudgetUiItem): Boolean =
                oldItem == newItem
        }

        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

        /** Voir [animateAppearance] — reste dans la fourchette 200-300 ms demandée. */
        const val ENTRANCE_ANIMATION_DURATION_MS = 260L
    }
}
