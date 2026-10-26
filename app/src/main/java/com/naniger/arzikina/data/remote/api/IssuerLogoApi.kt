package com.naniger.arzikina.data.remote.api

import com.naniger.arzikina.data.remote.RemoteConfig
import com.naniger.arzikina.data.remote.SyncHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transport des images de logo d'émetteur (`server/api/issuer/upload_logo.php` et `get_logo.php`) — le
 * JSON du profil/des reçus ne porte que le NOM du fichier. Client authentifié ([SyncHttpClient]) pour
 * les deux sens : les logos sont privés à chaque utilisateur.
 *
 * Aucune logique métier (quels logos envoyer/télécharger, quand) : voir `IssuedReceiptSyncHandler`.
 */
@Singleton
class IssuerLogoApi @Inject constructor(
    @SyncHttpClient private val client: OkHttpClient
) {

    /**
     * Envoi idempotent (le serveur répond « déjà présent » pour un nom connu). Lève
     * [HttpFailureException] pour toute réponse non-2xx (le code permet à l'appelant de distinguer un
     * refus définitif — 4xx — d'une panne temporaire).
     */
    suspend fun upload(fileName: String, file: File) {
        val mediaType = (if (fileName.endsWith(".png")) "image/png" else "image/jpeg").toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", fileName)
            .addFormDataPart("logo", fileName, file.asRequestBody(mediaType))
            .build()
        val request = Request.Builder().url(RemoteConfig.BASE_URL + "api/issuer/upload_logo.php").post(body).build()
        execute(request) { _, _ -> Unit }
    }

    /** Octets du logo, ou `null` si le serveur ne le connaît pas (404). Autre échec : exception. */
    suspend fun download(fileName: String): ByteArray? {
        val url = (RemoteConfig.BASE_URL + "api/issuer/get_logo.php").toHttpUrl().newBuilder()
            .addQueryParameter("name", fileName)
            .build()
        return try {
            execute(Request.Builder().url(url).get().build()) { _, bytes -> bytes }
        } catch (e: HttpFailureException) {
            if (e.code == 404) null else throw e
        }
    }

    private suspend fun <T> execute(request: Request, transform: (Int, ByteArray) -> T): T =
        client.awaitBytes(request, MAX_RESPONSE_BYTES, transform)

    private companion object {
        const val MAX_RESPONSE_BYTES = 5_000_000L
    }
}
