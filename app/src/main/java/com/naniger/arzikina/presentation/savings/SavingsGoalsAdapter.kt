package com.naniger.arzikina.presentation.savings

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.ItemSavingsGoalBinding
import com.naniger.arzikina.databinding.ItemSavingsGoalsSectionBinding
import com.naniger.arzikina.databinding.ItemSavingsGoalsSummaryBinding
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.util.AppDateFormats
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.SavingsGoalDeadline
import com.naniger.arzikina.util.SavingsSuggestion
import com.naniger.arzikina.util.appLocale

/**
 * Liste « Mes objectifs d'épargne » : synthèse, titres de section et cartes d'objectif dans une
 * seule RecyclerView (voir [SavingsGoalsRow]). Aucun calcul ici : tout arrive déjà calculé
 * ([SavingsGoalUiItem]), l'adapter ne fait que choisir textes et couleurs.
 */
class SavingsGoalsAdapter(
    private val onGoalClick: (SavingsGoalUiItem) -> Unit,
    private val onEditClick: (SavingsGoalUiItem) -> Unit,
    private val onDeleteClick: (SavingsGoalUiItem) -> Unit
) : ListAdapter<SavingsGoalsRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SavingsGoalsRow.Summary -> TYPE_SUMMARY
        is SavingsGoalsRow.Section -> TYPE_SECTION
        is SavingsGoalsRow.Goal -> TYPE_GOAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SUMMARY -> SummaryViewHolder(ItemSavingsGoalsSummaryBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionViewHolder(ItemSavingsGoalsSectionBinding.inflate(inflater, parent, false))
            else -> GoalViewHolder(ItemSavingsGoalBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is SavingsGoalsRow.Summary -> (holder as SummaryViewHolder).bind(row.summary)
            is SavingsGoalsRow.Section -> (holder as SectionViewHolder).bind(row)
            is SavingsGoalsRow.Goal -> (holder as GoalViewHolder).bind(row.item, onGoalClick, onEditClick, onDeleteClick)
        }
    }

    class SummaryViewHolder(private val binding: ItemSavingsGoalsSummaryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(summary: SavingsGoalsSummary) {
            val context = binding.root.context
            val resources = context.resources
            binding.totalSavedValue.text = Money.format(CurrencyAmount(summary.currencyCode, summary.totalSaved))
            binding.totalTargetValue.text = context.getString(
                R.string.savings_goals_summary_target,
                Money.format(CurrencyAmount(summary.currencyCode, summary.totalTarget))
            )
            binding.totalProgress.progress = summary.progressPercent
            binding.summaryDetails.text = listOf(
                context.getString(R.string.savings_goal_progress_percent, summary.progressPercent),
                resources.getQuantityString(R.plurals.savings_goals_summary_count, summary.goalCount, summary.goalCount),
                resources.getQuantityString(R.plurals.savings_goals_summary_completed, summary.completedCount, summary.completedCount)
            ).joinToString(separator = "  ·  ")
        }
    }

    class SectionViewHolder(private val binding: ItemSavingsGoalsSectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(section: SavingsGoalsRow.Section) {
            binding.sectionTitle.setText(section.titleRes)
        }
    }

    class GoalViewHolder(private val binding: ItemSavingsGoalBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: SavingsGoalUiItem,
            onGoalClick: (SavingsGoalUiItem) -> Unit,
            onEditClick: (SavingsGoalUiItem) -> Unit,
            onDeleteClick: (SavingsGoalUiItem) -> Unit
        ) {
            val context = binding.root.context
            val goal = item.goal

            binding.goalName.text = goal.name
            bindInitial(context, item)
            binding.savedValue.text = Money.format(CurrencyAmount(goal.currencyCode, goal.currentAmount))
            binding.targetValue.text = context.getString(
                R.string.savings_goal_amount_of,
                Money.format(CurrencyAmount(goal.currencyCode, goal.targetAmount))
            )
            binding.percentValue.text = context.getString(R.string.savings_goal_progress_percent, item.progressPercent)
            binding.goalProgress.progress = item.progressPercent
            binding.goalProgress.setIndicatorColor(ContextCompat.getColor(context, progressColorOf(item)))
            bindDeadline(context, item.deadline)
            bindSuggestion(context, item)

            binding.root.setOnClickListener { onGoalClick(item) }
            binding.menuButton.setOnClickListener { anchor -> showActionsMenu(anchor, item, onEditClick, onDeleteClick) }
        }

        private fun bindInitial(context: Context, item: SavingsGoalUiItem) {
            binding.goalInitial.text = item.goal.name.trim().take(1).uppercase(context.appLocale())
            val (background, text) = if (item.isCompleted) {
                R.color.savings_completed to R.color.white
            } else {
                R.color.savings_avatar_bg to R.color.savings_avatar_text
            }
            binding.goalInitial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, background))
            binding.goalInitial.setTextColor(ContextCompat.getColor(context, text))
        }

        private fun bindDeadline(context: Context, deadline: SavingsGoalDeadline) {
            val style = when (deadline) {
                SavingsGoalDeadline.None -> ChipStyle(
                    context.getString(R.string.savings_goal_no_deadline),
                    R.drawable.ic_calendar_24, R.color.savings_chip_neutral_bg, R.color.savings_chip_neutral_text
                )
                SavingsGoalDeadline.Reached -> ChipStyle(
                    context.getString(R.string.savings_goal_completed),
                    R.drawable.ic_check_24, R.color.savings_chip_done_bg, R.color.savings_chip_done_text
                )
                is SavingsGoalDeadline.Remaining -> ChipStyle(
                    context.resources.getQuantityString(
                        R.plurals.savings_goal_deadline_remaining,
                        deadline.days,
                        deadline.date.format(AppDateFormats.mediumDate(context)),
                        deadline.days
                    ),
                    R.drawable.ic_calendar_24,
                    if (deadline.isSoon) R.color.savings_chip_soon_bg else R.color.savings_chip_neutral_bg,
                    if (deadline.isSoon) R.color.savings_chip_soon_text else R.color.savings_chip_neutral_text
                )
                is SavingsGoalDeadline.Today -> ChipStyle(
                    context.getString(R.string.savings_goal_deadline_today),
                    R.drawable.ic_time_24, R.color.savings_chip_soon_bg, R.color.savings_chip_soon_text
                )
                is SavingsGoalDeadline.Overdue -> ChipStyle(
                    context.getString(
                        R.string.savings_goal_deadline_overdue,
                        deadline.date.format(AppDateFormats.mediumDate(context))
                    ),
                    R.drawable.ic_error_outline_24, R.color.savings_chip_overdue_bg, R.color.savings_chip_overdue_text
                )
            }
            val textColor = ContextCompat.getColor(context, style.textColor)
            binding.deadlineText.text = style.text
            binding.deadlineText.setTextColor(textColor)
            binding.deadlineIcon.setImageResource(style.iconRes)
            binding.deadlineIcon.imageTintList = ColorStateList.valueOf(textColor)
            binding.deadlineChip.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, style.backgroundColor))
        }

        private fun bindSuggestion(context: Context, item: SavingsGoalUiItem) {
            val currencyCode = item.goal.currencyCode
            val text = when (val suggestion = item.suggestion) {
                is SavingsSuggestion.PerMonth -> context.getString(
                    R.string.savings_goal_suggestion_monthly,
                    Money.format(CurrencyAmount(currencyCode, suggestion.amountMinor))
                )
                is SavingsSuggestion.BeforeDeadline -> context.getString(
                    R.string.savings_goal_suggestion_before_deadline,
                    Money.format(CurrencyAmount(currencyCode, suggestion.amountMinor))
                )
                null -> null
            }
            binding.suggestionBox.visibility = if (text != null) View.VISIBLE else View.GONE
            binding.suggestionText.text = text
        }

        /** Orange quand l'échéance presse (puce orange assortie), vert foncé une fois atteint. */
        @ColorRes
        private fun progressColorOf(item: SavingsGoalUiItem): Int = when {
            item.isCompleted -> R.color.savings_completed
            item.deadline is SavingsGoalDeadline.Overdue ||
                item.deadline is SavingsGoalDeadline.Today ||
                (item.deadline as? SavingsGoalDeadline.Remaining)?.isSoon == true -> R.color.warning_amber
            else -> R.color.arzikina_primary
        }

        private fun showActionsMenu(
            anchor: View,
            item: SavingsGoalUiItem,
            onEditClick: (SavingsGoalUiItem) -> Unit,
            onDeleteClick: (SavingsGoalUiItem) -> Unit
        ) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.inflate(R.menu.savings_goal_actions_menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit_savings_goal -> {
                        onEditClick(item)
                        true
                    }
                    R.id.action_delete_savings_goal -> {
                        onDeleteClick(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        private data class ChipStyle(
            val text: String,
            @DrawableRes val iconRes: Int,
            @ColorRes val backgroundColor: Int,
            @ColorRes val textColor: Int
        )
    }

    private companion object {
        const val TYPE_SUMMARY = 0
        const val TYPE_SECTION = 1
        const val TYPE_GOAL = 2

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SavingsGoalsRow>() {
            override fun areItemsTheSame(oldItem: SavingsGoalsRow, newItem: SavingsGoalsRow): Boolean = when {
                oldItem is SavingsGoalsRow.Summary && newItem is SavingsGoalsRow.Summary -> true
                oldItem is SavingsGoalsRow.Section && newItem is SavingsGoalsRow.Section -> oldItem.titleRes == newItem.titleRes
                oldItem is SavingsGoalsRow.Goal && newItem is SavingsGoalsRow.Goal -> oldItem.item.goal.id == newItem.item.goal.id
                else -> false
            }

            override fun areContentsTheSame(oldItem: SavingsGoalsRow, newItem: SavingsGoalsRow): Boolean = oldItem == newItem
        }
    }
}
