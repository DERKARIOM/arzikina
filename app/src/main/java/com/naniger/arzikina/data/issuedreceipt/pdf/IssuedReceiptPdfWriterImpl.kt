package com.naniger.arzikina.data.issuedreceipt.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.naniger.arzikina.data.issuedreceipt.IssuerLogoStorage
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptException
import com.naniger.arzikina.domain.issuedreceipt.ReceiptNumber
import com.naniger.arzikina.domain.issuedreceipt.pdf.IssuedReceiptPdfWriter
import com.naniger.arzikina.domain.issuedreceipt.pdf.PdfLogo
import com.naniger.arzikina.domain.issuedreceipt.pdf.ReceiptPdfContentBuilder
import com.naniger.arzikina.domain.issuedreceipt.pdf.ReceiptPdfLayoutEngine
import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.util.Constants
import com.naniger.arzikina.util.pdf.PdfPaints
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Orchestre la génération : contenu mis en forme → mise en page (mesure via `Paint`) → rendu
 * `PdfDocument` → écriture atomique. Aucune connexion Internet requise, aucune ressource distante :
 * le PDF est autonome. Un logo illisible ou absent donne un reçu sans logo, jamais une erreur.
 */
class IssuedReceiptPdfWriterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logoStorage: IssuerLogoStorage,
    private val pdfStorage: IssuedReceiptPdfStorage,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IssuedReceiptPdfWriter {

    override suspend fun write(receipt: IssuedReceipt, logoCandidates: List<String>, language: String): String =
        withContext(ioDispatcher) {
            var logo: Bitmap? = null
            try {
                require(ReceiptNumber.isValid(receipt.number)) { "Le reçu doit être enregistré (numéro attribué) avant son PDF." }
                val userId = sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
                logo = logoCandidates.firstNotNullOfOrNull { name ->
                    logoStorage.resolveFile(name)?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
                }
                val content = ReceiptPdfContentBuilder.build(receipt, language, logo?.let { PdfLogo(it.width, it.height) })
                val paints = PdfPaints()
                val pages = ReceiptPdfLayoutEngine.layout(content, paints)
                val fileName = ReceiptNumber.pdfFileName(receipt.number)
                pdfStorage.writeAtomically(userId, fileName) { out -> PdfCanvasRenderer(paints).render(pages, logo, out) }
                fileName
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IssuedReceiptException.PdfFailed(e)
            } catch (e: OutOfMemoryError) {
                throw IssuedReceiptException.PdfFailed(e)
            } finally {
                logo?.recycle()
            }
        }

    override suspend fun contentUriFor(fileName: String): String? = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return@withContext null
        val file = pdfStorage.resolveFile(userId, fileName) ?: return@withContext null
        FileProvider.getUriForFile(context, context.packageName + Constants.FILE_PROVIDER_AUTHORITY_SUFFIX, file).toString()
    }

    override suspend fun delete(fileName: String): Unit = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return@withContext
        pdfStorage.delete(userId, fileName)
        Unit
    }
}
