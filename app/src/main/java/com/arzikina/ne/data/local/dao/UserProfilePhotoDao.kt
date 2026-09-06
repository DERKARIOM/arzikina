package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.UserProfilePhotoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Accès Room à [UserProfilePhotoEntity] — voir sa doc de tête. Réservé au futur
 * `ProfilePhotoRepository` (cahier des charges "Gestion de la photo de profil"), seul appelant
 * prévu de cette table.
 */
@Dao
interface UserProfilePhotoDao {

    /** Utilisé par l'écran Profil pour afficher l'avatar en temps réel (voir `ProfileViewModel`). */
    @Query("SELECT * FROM user_profile_photos WHERE userId = :userId LIMIT 1")
    fun observeByUserId(userId: Long): Flow<UserProfilePhotoEntity?>

    @Query("SELECT * FROM user_profile_photos WHERE userId = :userId LIMIT 1")
    suspend fun findByUserId(userId: Long): UserProfilePhotoEntity?

    /** `@Upsert` (plutôt que `@Insert`) : une seule ligne par utilisateur (voir l'index unique sur
     *  `userId`), réécrite à chaque changement de photo sans jamais dupliquer de ligne — même
     *  principe que [com.arzikina.ne.data.local.dao.UserServerLinkDao.upsert]. */
    @Upsert
    suspend fun upsert(photo: UserProfilePhotoEntity)
}
