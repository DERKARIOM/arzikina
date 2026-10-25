package com.naniger.arzikina.presentation.shop

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import coil3.dispose
import coil3.load
import coil3.request.crossfade
import com.naniger.arzikina.R
import java.io.File

/**
 * Affiche la photo d'un produit (fichier privé de l'appli) avec Coil : décodage à la taille de la vue,
 * cache mémoire, annulation automatique quand la vue est recyclée. Sans photo : pictogramme neutre.
 * Chaque appel remet l'état complet de la vue (cadrage, teinte) : une ligne recyclée n'hérite de rien.
 */
fun ImageView.showProductImage(file: File?, @DrawableRes placeholder: Int = R.drawable.ic_storefront_24) {
    if (file == null) {
        dispose()
        showPlaceholder(placeholder)
        return
    }
    scaleType = ImageView.ScaleType.CENTER_CROP
    imageTintList = null
    load(file) { crossfade(true) }
}

/** Aperçu d'une image choisie mais pas encore enregistrée (URI de la galerie ou de l'appareil photo). */
fun ImageView.showPickedImage(uri: String) {
    scaleType = ImageView.ScaleType.CENTER_CROP
    imageTintList = null
    load(uri) { crossfade(true) }
}

fun ImageView.showPlaceholder(@DrawableRes placeholder: Int = R.drawable.ic_storefront_24) {
    scaleType = ImageView.ScaleType.CENTER_INSIDE
    imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.arzikina_on_balance_card_variant))
    setImageResource(placeholder)
}
