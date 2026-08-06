package com.arzikina.ne.data.local.database

import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.TransactionType

/**
 * Catégories proposées par défaut à la première installation.
 *
 * Reprend les exemples du cahier des charges. Utilisée à deux endroits qui
 * doivent rester synchronisés avec la même donnée : le callback `onCreate`
 * de [ArzikinaDatabase] (nouvelle installation) et [MIGRATION_1_2] (mise à
 * jour d'une base existante) — voir leurs commentaires respectifs pour le
 * détail de cette double exécution.
 */
internal object DefaultCategories {

    fun seed(now: Long): List<CategoryEntity> = listOf(
        // Revenus
        category("Salaire", CategoryIcon.SALARY, 0xFF006C4FL, TransactionType.INCOME, now),
        category("Divers", CategoryIcon.OTHER, 0xFF64748BL, TransactionType.INCOME, now),

        // Dépenses
        category("Nourriture", CategoryIcon.FOOD, 0xFFF59E0BL, TransactionType.EXPENSE, now),
        category("Transport", CategoryIcon.TRANSPORT, 0xFF2563EBL, TransactionType.EXPENSE, now),
        category("Santé", CategoryIcon.HEALTH, 0xFFDC2626L, TransactionType.EXPENSE, now),
        category("Shopping", CategoryIcon.SHOPPING, 0xFF7C3AEDL, TransactionType.EXPENSE, now),
        category("Cadeaux", CategoryIcon.GIFTS, 0xFFEC4899L, TransactionType.EXPENSE, now),
        category("Internet", CategoryIcon.INTERNET, 0xFF0EA5E9L, TransactionType.EXPENSE, now),
        category("Eau", CategoryIcon.WATER, 0xFF06B6D4L, TransactionType.EXPENSE, now),
        category("Électricité", CategoryIcon.ELECTRICITY, 0xFFF59E0BL, TransactionType.EXPENSE, now),
        category("Éducation", CategoryIcon.EDUCATION, 0xFF16A34AL, TransactionType.EXPENSE, now),
        category("Maison", CategoryIcon.HOME, 0xFF10B981L, TransactionType.EXPENSE, now),
        category("Divers", CategoryIcon.OTHER, 0xFF64748BL, TransactionType.EXPENSE, now)
    )

    private fun category(
        name: String,
        icon: CategoryIcon,
        colorArgb: Long,
        type: TransactionType,
        createdAt: Long
    ) = CategoryEntity(
        name = name,
        icon = icon,
        colorArgb = colorArgb,
        type = type,
        createdAt = createdAt
    )
}
