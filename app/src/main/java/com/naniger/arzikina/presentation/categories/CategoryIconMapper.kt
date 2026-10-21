package com.naniger.arzikina.presentation.categories

import androidx.annotation.DrawableRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.CategoryIcon

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
        CategoryIcon.FEE -> R.drawable.ic_category_fee_24
        CategoryIcon.RESTAURANT -> R.drawable.ic_category_restaurant_24
        CategoryIcon.COFFEE -> R.drawable.ic_category_coffee_24
        CategoryIcon.GROCERIES -> R.drawable.ic_category_groceries_24
        CategoryIcon.FUEL -> R.drawable.ic_category_fuel_24
        CategoryIcon.MOTORBIKE -> R.drawable.ic_category_motorbike_24
        CategoryIcon.BUS -> R.drawable.ic_category_bus_24
        CategoryIcon.TAXI -> R.drawable.ic_category_taxi_24
        CategoryIcon.CAR -> R.drawable.ic_category_car_24
        CategoryIcon.FLIGHT -> R.drawable.ic_category_flight_24
        CategoryIcon.RENT -> R.drawable.ic_category_rent_24
        CategoryIcon.REPAIRS -> R.drawable.ic_category_repairs_24
        CategoryIcon.CLEANING -> R.drawable.ic_category_cleaning_24
        CategoryIcon.LAUNDRY -> R.drawable.ic_category_laundry_24
        CategoryIcon.PHONE -> R.drawable.ic_category_phone_24
        CategoryIcon.TV -> R.drawable.ic_category_tv_24
        CategoryIcon.PHARMACY -> R.drawable.ic_category_pharmacy_24
        CategoryIcon.FITNESS -> R.drawable.ic_category_fitness_24
        CategoryIcon.BEAUTY -> R.drawable.ic_category_beauty_24
        CategoryIcon.CHILDREN -> R.drawable.ic_category_children_24
        CategoryIcon.PETS -> R.drawable.ic_category_pets_24
        CategoryIcon.FAMILY -> R.drawable.ic_category_family_24
        CategoryIcon.WEDDING -> R.drawable.ic_category_wedding_24
        CategoryIcon.CELEBRATION -> R.drawable.ic_category_celebration_24
        CategoryIcon.BIRTHDAY -> R.drawable.ic_category_birthday_24
        CategoryIcon.CHARITY -> R.drawable.ic_category_charity_24
        CategoryIcon.BUSINESS -> R.drawable.ic_category_business_24
        CategoryIcon.SHOP -> R.drawable.ic_category_shop_24
        CategoryIcon.AGRICULTURE -> R.drawable.ic_category_agriculture_24
        CategoryIcon.SAVINGS -> R.drawable.ic_category_savings_24
        CategoryIcon.INVESTMENT -> R.drawable.ic_category_investment_24
        CategoryIcon.BANK -> R.drawable.ic_category_bank_24
        CategoryIcon.BILLS -> R.drawable.ic_category_bills_24
        CategoryIcon.INSURANCE -> R.drawable.ic_category_insurance_24
        CategoryIcon.LEGAL -> R.drawable.ic_category_legal_24
        CategoryIcon.GAMES -> R.drawable.ic_category_games_24
        CategoryIcon.MUSIC -> R.drawable.ic_category_music_24
        CategoryIcon.SPORT -> R.drawable.ic_category_sport_24
        CategoryIcon.BOOKS -> R.drawable.ic_category_books_24
        CategoryIcon.CLOTHING -> R.drawable.ic_category_clothing_24
        CategoryIcon.TRAVEL -> R.drawable.ic_category_travel_24
        CategoryIcon.NATURE -> R.drawable.ic_category_nature_24
        CategoryIcon.MOSQUE -> R.drawable.ic_category_mosque_24
        CategoryIcon.CHURCH -> R.drawable.ic_category_church_24
    }
}
