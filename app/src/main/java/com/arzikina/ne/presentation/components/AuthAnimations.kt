package com.arzikina.ne.presentation.components

import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemPostcardTextInputBinding
import com.google.android.material.card.MaterialCardView

/**
 * Animations discrètes des écrans Connexion/Inscription (cahier des charges "Refonte
 * Login/Register", section "Animations attendues") : factorisées ici plutôt que dupliquées entre
 * [com.arzikina.ne.presentation.auth.LoginFragment] et [com.arzikina.ne.presentation.auth.RegisterFragment]
 * — même principe que [NavAnimations] pour les transitions de navigation.
 *
 * Purement visuel : aucune de ces fonctions ne lit ni ne modifie un état de ViewModel, elles ne
 * font qu'habiller des transitions déjà pilotées par LoginViewModel/RegisterViewModel (formState,
 * events) ou des interactions UI locales (focus clavier, appui bouton).
 *
 * `ValueAnimator` + affectation explicite de la propriété animée (plutôt qu'un `ObjectAnimator`
 * ciblant un nom de propriété par réflexion) dans [elevateOnFocusOf] : évite toute dépendance à la
 * réflexion, donc tout risque lié à l'obfuscation R8 en build release.
 */

private const val ENTRANCE_DURATION_MS = 350L
private const val ENTRANCE_TRANSLATION_DP = 16f
private const val FOCUS_ELEVATION_DURATION_MS = 150L
private const val VISIBILITY_FADE_DURATION_MS = 200L
private const val PRESS_SCALE = 0.97f
private const val PRESS_SCALE_DURATION_MS = 100L

/**
 * Entrée progressive du contenu au chargement de l'écran : léger fondu + glissement vertical.
 * À appeler une seule fois depuis `onViewCreated`, sur le conteneur racine défilant
 * (`contentContainer`) de chaque écran.
 */
fun View.playEntranceAnimation() {
    translationY = resources.displayMetrics.density * ENTRANCE_TRANSLATION_DP
    alpha = 0f
    animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(ENTRANCE_DURATION_MS)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

/**
 * Élève discrètement une Card "postcard" tant que [input] a le focus clavier, revient à son
 * élévation de repos (celle déjà posée en XML, `app:cardElevation`) sinon. Fonction de bas niveau
 * utilisée par [ItemPostcardTextInputBinding.animateElevationOnFocus] et directement par
 * `RegisterFragment` pour `securityQuestionCard` (Card déclarée en dur autour de
 * `item_dropdown_field.xml`, voir sa doc — même traitement visuel malgré un layout différent).
 */
fun MaterialCardView.elevateOnFocusOf(input: View) {
    val restElevation = cardElevation
    val focusedElevation = resources.getDimension(R.dimen.elevation_raised_focused)
    var animator: ValueAnimator? = null
    input.setOnFocusChangeListener { _, hasFocus ->
        animator?.cancel()
        animator = ValueAnimator.ofFloat(cardElevation, if (hasFocus) focusedElevation else restElevation).apply {
            duration = FOCUS_ELEVATION_DURATION_MS
            addUpdateListener { cardElevation = it.animatedValue as Float }
            start()
        }
    }
}

/** Raccourci de [elevateOnFocusOf] pour un champ `item_postcard_text_input.xml` inclus. */
fun ItemPostcardTextInputBinding.animateElevationOnFocus() {
    root.elevateOnFocusOf(postcardInput)
}

/**
 * Retour visuel discret à l'appui sur un bouton principal (Connexion/Inscription) : léger effet
 * d'échelle, en complément du ripple + de l'animation d'élévation déjà fournis par défaut par
 * `MaterialButton` (non désactivés par le thème du projet). Retourne toujours `false` : ne
 * consomme pas l'évènement, laisse le traitement normal du clic (et donc le ripple Material) se
 * poursuivre sans changement.
 */
fun View.playPressScaleFeedback() {
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                view.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE).setDuration(PRESS_SCALE_DURATION_MS).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(PRESS_SCALE_DURATION_MS).start()
        }
        false
    }
}

/**
 * Bascule de visibilité animée (fondu) pour un texte d'erreur/état, à la place d'une simple
 * affectation `visibility = VISIBLE/GONE`. Ne change aucune condition métier : les appelants
 * ([com.arzikina.ne.presentation.auth.LoginFragment.render]) continuent de décider QUAND afficher
 * le texte, cette fonction ne fait qu'animer la transition.
 *
 * Protégée contre les ré-appels redondants : `render()` est ré-invoqué à chaque émission du
 * StateFlow, y compris quand la visibilité demandée est déjà l'état courant — dans ce cas, ne
 * relance pas l'animation (éviterait un fondu répété/un flash à chaque frappe clavier).
 */
fun View.setVisibleAnimated(visible: Boolean) {
    if (visible) {
        if (visibility == View.VISIBLE && alpha == 1f) return
        animate().cancel()
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(VISIBILITY_FADE_DURATION_MS).start()
    } else {
        if (visibility == View.GONE) return
        animate().cancel()
        alpha = 1f
        visibility = View.GONE
    }
}
