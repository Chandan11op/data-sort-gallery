package com.secondproject.datasortgallery.data.repository

import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.local.entity.MediaType
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getAllMedia(): Flow<List<MediaEntity>>
    fun getMediaByType(mediaType: MediaType): Flow<List<MediaEntity>>
    fun getFavoriteMedia(): Flow<List<MediaEntity>>
    fun getTrashedMedia(): Flow<List<MediaEntity>>
    suspend fun getMediaById(uniqueId: String): MediaEntity?
    suspend fun scanAndSyncMedia()
    suspend fun toggleFavorite(uniqueId: String, isFavorite: Boolean)
    suspend fun toggleTrash(uniqueId: String, isTrashed: Boolean)
    suspend fun toggleTrashBatch(uniqueIds: List<String>, isTrashed: Boolean)
    suspend fun deleteMedia(uniqueId: String)
    suspend fun deleteMultipleMedia(uniqueIds: List<String>)
    suspend fun deleteMultipleMediaChunked(uniqueIds: List<String>)
    suspend fun getMediaByIds(uniqueIds: List<String>): List<MediaEntity>
    fun searchMedia(query: String): Flow<List<MediaEntity>>
}
