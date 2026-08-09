package com.arzikina.ne.presentation.components

import androidx.navigation.NavOptions
import com.arzikina.ne.R

/**
 * Options de navigation partagées pour une transition discrète (fondu simple, voir
 * `res/anim/fade_in.xml`/`fade_out.xml`) — introduites pour l'ouverture de "Détail du compte" et
 * du formulaire de compte (section UX de la fonctionnalité Carte de crédit), mais réutilisables
 * par n'importe quel écran qui voudrait la même transition plutôt que le comportement par défaut
 * de Navigation Component. Un seul objet centralisé plutôt que reconstruire un `NavOptions.Builder()`
 * identique à chaque appel de `navigate(...)`.
 */
object NavAnimations {
    val fade: NavOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.fade_in)
        .setExitAnim(R.anim.fade_out)
        .setPopEnterAnim(R.anim.fade_in)
        .setPopExitAnim(R.anim.fade_out)
        .build()
}
