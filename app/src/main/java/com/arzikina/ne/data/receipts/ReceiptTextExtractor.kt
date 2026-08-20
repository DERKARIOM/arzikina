package com.arzikina.ne.data.receipts

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extraction du texte brut d'un reçu PDF — préparation à la SUGGESTION de montant (voir cahier des
 * charges "Gestion des reçus" et `ReceiptDetailViewModel`, à venir) : le texte extrait ici n'est
 * jamais affiché tel quel ni enregistré, seulement transmis à un parseur d'heuristiques séparé.
 *
 * Contrairement à [ReceiptPdfRenderer] (rendu de page en [android.graphics.Bitmap], API Android
 * native `PdfRenderer`), AUCUNE API Android native n'extrait le texte d'un PDF — `PdfRenderer` ne
 * fait QUE du rendu visuel. D'où la dépendance à PdfBox-Android (portage d'Apache PdfBox, licence
 * Apache 2.0), seule concession à la règle "pas de bibliothèque tierce inutile" déjà appliquée pour
 * le rendu — ici réellement nécessaire, pas une préférence.
 *
 * Ne connaît JAMAIS [com.arzikina.ne.domain.model.Receipt] ni Room (même principe que
 * [ReceiptFileStorage]/[ReceiptPdfRenderer]) : uniquement responsable de transformer un [File] PDF
 * en texte brut, rien d'autre — en particulier AUCUNE logique de détection de montant ici (voir le
 * parseur séparé, dont la doc explique pourquoi cette responsabilité est volontairement distincte).
 */
@Singleton
class ReceiptTextExtractor @Inject constructor() {

    /**
     * Texte brut de TOUTES les pages de [file], concaténé dans l'ordre — `null` si le fichier n'est
     * pas un PDF valide, s'il est protégé par mot de passe, OU s'il ne contient tout simplement
     * AUCUNE couche de texte (cas d'un reçu scanné/photographié plutôt que généré numériquement :
     * jamais traité comme une erreur, juste une absence de texte à extraire — voir la doc de
     * `ReceiptDetailViewModel` sur le comportement attendu dans ce cas). Jamais d'exception propagée.
     */
    fun extractText(file: File): String? = runCatching {
        PDDocument.load(file).use { document ->
            if (document.isEncrypted) return@runCatching null
            PDFTextStripper().getText(document)
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
