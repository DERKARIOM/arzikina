package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.UserServerLinkEntity

/**
 * Accès Room à [UserServerLinkEntity] — voir sa doc de tête. Réservé au flux de connexion unifié
 * (`presentation/auth`) : résout, dans un sens comme dans l'autre, la correspondance entre le
 * compte local de CET appareil et le compte serveur de synchronisation.
 */
@Dao
interface UserServerLinkDao {

    @Query("SELECT * FROM user_server_links WHERE localUserId = :localUserId LIMIT 1")
    suspend fun getByLocalUserId(localUserId: Long): UserServerLinkEntity?

    @Query("SELECT * FROM user_server_links WHERE serverUserId = :serverUserId LIMIT 1")
    suspend fun getByServerUserId(serverUserId: String): UserServerLinkEntity?

    /** `@Upsert` (plutôt que `@Insert`) : un rattachement peut être réécrit si nécessaire (ex.
     *  changement de compte serveur sur un appareil déjà lié), sans jamais dupliquer de ligne. */
    @Upsert
    suspend fun upsert(link: UserServerLinkEntity)
}
