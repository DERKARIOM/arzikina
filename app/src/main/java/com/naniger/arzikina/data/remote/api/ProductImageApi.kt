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
 * Transport des photos de produits (`server/api/shop/upload_product_image.php` et `get_product_image.php`) :
 * le JSON du produit ne porte que le NOM du fichier. Client authentifié ([SyncHttpClient]) : les photos sont
 * privées à chaque utilisateur. Aucune logique métier (quelles photos, quand) : voir `ProductImageSyncHandler`.
 */
@Singleton
class ProductImageApi @Inject constructor(
    @SyncHttpClient private val client: OkHttpClient
) {

    /**
     * Envoi idempotent (le serveur répond « déjà présent » pour un nom connu). Lève [HttpFailureException]
     * pour toute réponse non-2xx.
     */
    suspend fun upload(fileName: String, file: File) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", fileName)
            .addFormDataPart("image", fileName, file.asRequestBody("image/jpeg".toMediaType()))
            .build()
        val request = Request.Builder().url(RemoteConfig.BASE_URL + "api/shop/upload_product_image.php").post(body).build()
        client.awaitBytes(request, MAX_RESPONSE_BYTES) { _, _ -> Unit }
    }

    /** Octets de la photo, ou `null` si le serveur ne la connaît pas (404). Autre échec : exception. */
    suspend fun download(fileName: String): ByteArray? {
        val url = (RemoteConfig.BASE_URL + "api/shop/get_product_image.php").toHttpUrl().newBuilder()
            .addQueryParameter("name", fileName)
            .build()
        return try {
            client.awaitBytes(Request.Builder().url(url).get().build(), MAX_RESPONSE_BYTES) { _, bytes -> bytes }
        } catch (e: HttpFailureException) {
            if (e.code == 404) null else throw e
        }
    }

    private companion object {
        /** Garde-fou mémoire : le serveur plafonne à 2 Mo. */
        const val MAX_RESPONSE_BYTES = 5_000_000L
    }
}
