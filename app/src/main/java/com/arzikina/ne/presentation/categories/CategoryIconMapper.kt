package com.arzikina.ne.presentation.categories

import androidx.annotation.DrawableRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.CategoryIcon

/**
 * Correspondance [CategoryIcon] -> ressource Vector Drawable concrète.
 *
 * Le domaine ne connaît jamais de référence Android ([CategoryIcon] est un
 * simple enum) : ce mapping reste entièrement dans la couche presentation,
 * ici sous forme de `@DrawableRes` (Views) plutôt que d'`ImageVector`
 * (ancienne version Compose, voir instructions projet).
 */
object CategoryIconMapper {

    @DrawableRes
    fun iconFor(icon: CategoryIcon): Int = when (icon) {
        CategoryIcon.FOOD -> R.drawable.ic_category_food_24
        CategoryIcon.TRANSPORT -> R.drawable.ic_category_transport_24
        CategoryIcon.HEALTH -> R.drawable.ic_category_health_24
        CategoryIcon.SALARY -> R.drawable.ic_category_salary_24
        CategoryIcon.SHOPPING -> R.drawable.ic_category_shopping_24
        CategoryIcon.GIFTS -> R.drawable.ic_category_gifts_24
        CategoryIcon.INTERNET -> R.drawable.ic_category_internet_24
        CategoryIcon.WATER -> R.drawable.ic_category_water_24
        CategoryIcon.ELECTRICITY -> R.drawable.ic_category_electricity_24
        CategoryIcon.EDUCATION -> R.drawable.ic_category_education_24
        CategoryIcon.HOME -> R.drawable.ic_category_home_24
        CategoryIcon.OTHER -> R.drawable.ic_category_other_24
        CategoryIcon.LOAN -> R.drawable.ic_loan_24
    }
}
