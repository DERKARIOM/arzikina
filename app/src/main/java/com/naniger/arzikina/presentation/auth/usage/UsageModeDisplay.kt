package com.naniger.arzikina.presentation.auth.usage

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.UsageMode

/** Textes et pictogrammes d'un [UsageMode] (le domaine ne connaît ni ressources ni Android). */
@get:StringRes
val UsageMode.titleRes: Int
    get() = when (this) {
        UsageMode.SHOP -> R.string.usage_mode_shop_title
        UsageMode.PERSONAL -> R.string.usage_mode_personal_title
        UsageMode.BOTH -> R.string.usage_mode_both_title
    }

@get:StringRes
val UsageMode.descriptionRes: Int
    get() = when (this) {
        UsageMode.SHOP -> R.string.usage_mode_shop_description
        UsageMode.PERSONAL -> R.string.usage_mode_personal_description
        UsageMode.BOTH -> R.string.usage_mode_both_description
    }

/** Pictogramme principal : boutique, portefeuille, ou boutique (associée à un portefeuille, voir [secondIconRes]). */
@get:DrawableRes
val UsageMode.firstIconRes: Int
    get() = when (this) {
        UsageMode.SHOP, UsageMode.BOTH -> R.drawable.ic_storefront_24
        UsageMode.PERSONAL -> R.drawable.ic_wallet_24
    }

/** Second pictogramme, uniquement pour « Boutique et finances personnelles » (portefeuille associé à la boutique). */
@get:DrawableRes
val UsageMode.secondIconRes: Int?
    get() = if (this == UsageMode.BOTH) R.drawable.ic_wallet_24 else null
