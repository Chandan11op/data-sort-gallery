package com.secondproject.datasortgallery.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.local.entity.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media WHERE isTrashed = 0 ORDER BY captureDate DESC, dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE mediaType = :mediaType AND isTrashed = 0 ORDER BY captureDate DESC, dateAdded DESC")
    fun getMediaByType(mediaType: MediaType): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isFavorite = 1 AND isTrashed = 0 ORDER BY captureDate DESC, dateAdded DESC")
    fun getFavoriteMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isTrashed = 1 ORDER BY dateAdded DESC")
    fun getTrashedMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE uniqueId = :uniqueId")
    suspend fun getMediaById(uniqueId: String): MediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItem(media: MediaEntity)

    @Update
    suspend fun updateMedia(media: MediaEntity)

    @Query("UPDATE media SET isFavorite = :isFavorite WHERE uniqueId = :uniqueId")
    suspend fun updateFavorite(uniqueId: String, isFavorite: Boolean)

    @Query("UPDATE media SET isTrashed = :isTrashed WHERE uniqueId = :uniqueId")
    suspend fun updateTrashed(uniqueId: String, isTrashed: Boolean)

    @Query("DELETE FROM media WHERE uniqueId = :uniqueId")
    suspend fun deleteMediaById(uniqueId: String)

    @Query("DELETE FROM media WHERE uniqueId IN (:uniqueIds)")
    suspend fun deleteMediaByIds(uniqueIds: List<String>)

    @Query("SELECT * FROM media WHERE filename LIKE '%' || :query || '%' AND isTrashed = 0")
    fun searchMedia(query: String): Flow<List<MediaEntity>>

    @Query("SELECT COUNT(*) FROM media WHERE isTrashed = 0")
    suspend fun getMediaCount(): Int
}
