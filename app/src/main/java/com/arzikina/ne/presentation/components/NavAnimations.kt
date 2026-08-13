package com.arzikina.ne.presentation.components

import androidx.navigation.NavOptions
import com.arzikina.ne.R

/**
 * Options de navigation partagées, pour ne jamais reconstruire un `NavOptions.Builder()`
 * identique à chaque appel de `navigate(...)` (voir plan "Animations et transitions entre les
 * pages"). Utilisées par la quasi-totalité des Fragments de l'app — voir chaque appel de
 * `navigate(..., NavAnimations.push)`/`navigate(..., NavAnimations.tabSwitch)`.
 *
 * Deux styles distincts, volontairement différents dans leur direction :
 * - [push] : navigation hiérarchique (liste → détail, liste → formulaire, etc.) — la nouvelle
 *   destination glisse depuis la droite en apparaissant en fondu (`slide_enter.xml`), l'écran
 *   quitté recule légèrement vers la gauche en s'estompant (`slide_exit.xml`) ; au retour (bouton
 *   Précédent/`popBackStack`), l'animation s'inverse symétriquement (`slide_pop_enter.xml`/
 *   `slide_pop_exit.xml`). Un léger déplacement (8% de la largeur) suffit à suggérer un mouvement
 *   spatial cohérent sans jamais devenir spectaculaire (cahier des charges UX : "discrètes,
 *   jamais extravagantes").
 * - [tabSwitch] : changement d'onglet de la Bottom Navigation (Accueil/Comptes/Rapports/Autre) —
 *   un simple fondu enchaîné (`fade_in.xml`/`fade_out.xml`), SANS glissement. Ces destinations
 *   sont des pairs au même niveau hiérarchique, pas un parent et son enfant : un glissement
 *   latéral suggérerait une relation spatiale gauche/droite entre les onglets qui n'existe pas
 *   (Material Design recommande un "fade through" pour ce cas précis, pas un "shared axis").
 *
 * Un ancien style `fade` (fondu pur, sans glissement, seul style existant avant l'introduction de
 * [push]) a existé ici et n'est plus utilisé nulle part dans l'app — supprimé plutôt que laissé en
 * code mort. `fade_in.xml`/`fade_out.xml` restent utilisées par [tabSwitch].
 */
object NavAnimations {

    val push: NavOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.slide_enter)
        .setExitAnim(R.anim.slide_exit)
        .setPopEnterAnim(R.anim.slide_pop_enter)
        .setPopExitAnim(R.anim.slide_pop_exit)
        .build()

    val tabSwitch: NavOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.fade_in)
        .setExitAnim(R.anim.fade_out)
        .setPopEnterAnim(R.anim.fade_in)
        .setPopExitAnim(R.anim.fade_out)
        .build()
}
