package com.arzikina.ne.data.receipts

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rendu de pages PDF en [Bitmap] via `android.graphics.pdf.PdfRenderer` — API native Android,
 * aucune bibliothèque tierce (cahier des charges "Gestion des reçus", section 7 : "ne pas ajouter
 * une bibliothèque inutilement").
 *
 * Utilisé par "Détail du reçu" (aperçu de la page 1 uniquement, voir
 * `presentation/utilities/receipts/ReceiptDetailViewModel`) ET par le visualiseur PDF plein écran
 * (toutes les pages, Étape 7) — [open] est le point d'entrée commun aux deux, évite de dupliquer
 * l'ouverture/fermeture du [PdfRenderer] entre les deux écrans.
 *
 * Ne connaît JAMAIS [com.arzikina.ne.domain.model.Receipt] ni Room (voir `ReceiptFileStorage`, même
 * principe) : uniquement responsable de transformer un [File] PDF en [Bitmap], rien d'autre.
 */
@Singleton
class ReceiptPdfRenderer @Inject constructor() {

    /**
     * Ouvre [file] pour rendu — `null` si le fichier n'est pas un PDF valide/lisible (jamais
     * d'exception propagée). L'appelant DOIT fermer la [Session] retournée (voir [Closeable.use])
     * pour libérer le descripteur de fichier sous-jacent.
     */
    fun open(file: File): Session? = runCatching {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        Session(PdfRenderer(pfd), pfd)
    }.getOrNull()

    /** Raccourci pour un rendu ponctuel de la première page uniquement (voir "Détail du reçu") —
     * ouvre, rend, ferme immédiatement : pas de descripteur de fichier laissé ouvert au-delà de cet
     * appel. */
    fun renderFirstPage(file: File, targetWidthPx: Int): Bitmap? =
        open(file)?.use { session -> session.renderPage(0, targetWidthPx) }

    /** Session de rendu ouverte sur un PDF — voir [open]. [renderPage] peut être appelée plusieurs
     * fois (pages différentes) tant que la session n'est pas fermée : c'est ce qui permet au
     * visualiseur plein écran (Étape 7) de rendre chaque page à la demande sans rouvrir le fichier. */
    class Session internal constructor(
        private val renderer: PdfRenderer,
        private val pfd: ParcelFileDescriptor
    ) : Closeable {

        val pageCount: Int get() = renderer.pageCount

        /** `null` si [index] est hors limites ou si le rendu échoue — jamais d'exception. Le bitmap
         * conserve le ratio d'aspect réel de la page, mis à l'échelle sur [targetWidthPx]. */
        fun renderPage(index: Int, targetWidthPx: Int): Bitmap? {
            if (index < 0 || index >= renderer.pageCount) return null
            return runCatching {
                renderer.openPage(index).use { page ->
                    val scale = targetWidthPx.toFloat() / page.width
                    val targetHeightPx = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                    // Un PDF n'a pas de fond transparent implicite : sans ce remplissage, les zones
                    // hors du contenu réellement dessiné par render() resteraient noires (bitmap par
                    // défaut) plutôt que blanches, comme le rendu attendu d'une page de document.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }.getOrNull()
        }

        override fun close() {
            renderer.close()
            pfd.close()
        }
    }
}
