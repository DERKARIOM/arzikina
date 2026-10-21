package com.naniger.arzikina.data.remote.api

import com.naniger.arzikina.data.remote.RemoteConfig
import com.naniger.arzikina.data.remote.SyncHttpClient
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            continuation.resumeWithException(HttpFailureException(it.code, it.message))
                            return
                        }
                        // Garde-fou mémoire : le serveur plafonne à 2 Mo, au-delà ce n'est pas un logo.
                        if ((it.body?.contentLength() ?: 0L) > MAX_RESPONSE_BYTES) {
                            continuation.resumeWithException(HttpFailureException(413, "Réponse trop volumineuse"))
                            return
                        }
                        val bytes = it.body?.bytes() ?: ByteArray(0)
                        continuation.resume(transform(it.code, bytes))
                    }
                }
            })
        }

    private companion object {
        const val MAX_RESPONSE_BYTES = 5_000_000L
    }
}
