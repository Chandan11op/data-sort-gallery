package com.secondproject.datasortgallery.data.repository

import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.local.entity.MediaType
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getAllMedia(): Flow<List<MediaEntity>>
    fun getMediaByType(mediaType: MediaType): Flow<List<MediaEntity>>
    fun getFavoriteMedia(): Flow<List<MediaEntity>>
    fun getTrashedMedia(): Flow<List<MediaEntity>>
    suspend fun getMediaById(id: Long): MediaEntity?
    suspend fun scanAndSyncMedia()
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)
    suspend fun toggleTrash(id: Long, isTrashed: Boolean)
    suspend fun deleteMedia(id: Long)
    suspend fun deleteMultipleMedia(ids: List<Long>)
    fun searchMedia(query: String): Flow<List<MediaEntity>>
}
