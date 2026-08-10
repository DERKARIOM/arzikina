package com.arzikina.ne.data.local.database

import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.LoanCategoryNames
import com.arzikina.ne.domain.model.TransactionType

/**
 * Catégories proposées par défaut à un nouvel utilisateur.
 *
 * Reprend les exemples du cahier des charges. Utilisée à trois endroits :
 * après l'inscription d'un nouvel utilisateur (voir la feuille de route
 * Authentification, écran Inscription), [MIGRATION_1_2] (mise à jour
 * d'une base antérieure à la fonctionnalité Catégories — voir son
 * commentaire pour le détail de [userId] dans ce cas) et [MIGRATION_12_13]
 * (backfill des 4 catégories Prêts/Emprunts pour les utilisateurs déjà
 * existants au moment de l'introduction de cette fonctionnalité).
 */
internal object DefaultCategories {

    // Noms des 4 catégories Prêts/Emprunts : voir [LoanCategoryNames] (domaine), désormais
    // l'unique source de vérité — cette classe se contente de les réutiliser pour les semer.
    private val LOAN_DISBURSEMENT_LENT_NAME = LoanCategoryNames.DISBURSEMENT_LENT
    private val LOAN_REPAYMENT_LENT_NAME = LoanCategoryNames.REPAYMENT_LENT
    private val LOAN_DISBURSEMENT_BORROWED_NAME = LoanCategoryNames.DISBURSEMENT_BORROWED
    private val LOAN_REPAYMENT_BORROWED_NAME = LoanCategoryNames.REPAYMENT_BORROWED

    fun seed(now: Long, userId: Long): List<CategoryEntity> = listOf(
        // Revenus
        category("Salaire", CategoryIcon.SALARY, 0xFF006C4FL, TransactionType.INCOME, now, userId),
        category("Divers", CategoryIcon.OTHER, 0xFF64748BL, TransactionType.INCOME, now, userId),

        // Dépenses
        category("Nourriture", CategoryIcon.FOOD, 0xFFF59E0BL, TransactionType.EXPENSE, now, userId),
        category("Transport", CategoryIcon.TRANSPORT, 0xFF2563EBL, TransactionType.EXPENSE, now, userId),
        category("Santé", CategoryIcon.HEALTH, 0xFFDC2626L, TransactionType.EXPENSE, now, userId),
        category("Shopping", CategoryIcon.SHOPPING, 0xFF7C3AEDL, TransactionType.EXPENSE, now, userId),
        category("Cadeaux", CategoryIcon.GIFTS, 0xFFEC4899L, TransactionType.EXPENSE, now, userId),
        category("Internet", CategoryIcon.INTERNET, 0xFF0EA5E9L, TransactionType.EXPENSE, now, userId),
        category("Eau", CategoryIcon.WATER, 0xFF06B6D4L, TransactionType.EXPENSE, now, userId),
        category("Électricité", CategoryIcon.ELECTRICITY, 0xFFF59E0BL, TransactionType.EXPENSE, now, userId),
        category("Éducation", CategoryIcon.EDUCATION, 0xFF16A34AL, TransactionType.EXPENSE, now, userId),
        category("Maison", CategoryIcon.HOME, 0xFF10B981L, TransactionType.EXPENSE, now, userId),
        category("Divers", CategoryIcon.OTHER, 0xFF64748BL, TransactionType.EXPENSE, now, userId),

        // Prêts/Emprunts (voir domain/model/Loan — ces 4 catégories couvrent les 2 sens de
        // domain/model/LoanType, chacun ayant sa jambe dépense ET sa jambe revenu).
        category(LOAN_DISBURSEMENT_LENT_NAME, CategoryIcon.LOAN, 0xFF16A34AL, TransactionType.EXPENSE, now, userId),
        category(LOAN_REPAYMENT_LENT_NAME, CategoryIcon.LOAN, 0xFF16A34AL, TransactionType.INCOME, now, userId),
        category(LOAN_DISBURSEMENT_BORROWED_NAME, CategoryIcon.LOAN, 0xFFDC2626L, TransactionType.INCOME, now, userId),
        category(LOAN_REPAYMENT_BORROWED_NAME, CategoryIcon.LOAN, 0xFFDC2626L, TransactionType.EXPENSE, now, userId)
    )

    private fun category(
        name: String,
        icon: CategoryIcon,
        colorArgb: Long,
        type: TransactionType,
        createdAt: Long,
        userId: Long
    ) = CategoryEntity(
        userId = userId,
        name = name,
        icon = icon,
        colorArgb = colorArgb,
        type = type,
        createdAt = createdAt
    )
}
