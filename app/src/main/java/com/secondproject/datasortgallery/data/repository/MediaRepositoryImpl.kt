package com.secondproject.datasortgallery.data.repository

import com.secondproject.datasortgallery.core.common.IoDispatcher
import com.secondproject.datasortgallery.core.media.MediaScanner
import com.secondproject.datasortgallery.data.local.dao.MediaDao
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.local.entity.MediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val mediaScanner: MediaScanner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MediaRepository {

    override fun getAllMedia(): Flow<List<MediaEntity>> = mediaDao.getAllMedia()

    override fun getMediaByType(mediaType: MediaType): Flow<List<MediaEntity>> = mediaDao.getMediaByType(mediaType)

    override fun getFavoriteMedia(): Flow<List<MediaEntity>> = mediaDao.getFavoriteMedia()

    override fun getTrashedMedia(): Flow<List<MediaEntity>> = mediaDao.getTrashedMedia()

    override suspend fun getMediaById(id: Long): MediaEntity? = withContext(ioDispatcher) {
        mediaDao.getMediaById(id)
    }

    override suspend fun scanAndSyncMedia() = withContext(ioDispatcher) {
        val scannedMedia = mediaScanner.scanMedia()
        if (scannedMedia.isNotEmpty()) {
            mediaDao.insertMedia(scannedMedia)
        }
    }

    override suspend fun toggleFavorite(id: Long, isFavorite: Boolean) = withContext(ioDispatcher) {
        mediaDao.updateFavorite(id, isFavorite)
    }

    override suspend fun toggleTrash(id: Long, isTrashed: Boolean) = withContext(ioDispatcher) {
        mediaDao.updateTrashed(id, isTrashed)
    }

    override suspend fun deleteMedia(id: Long) = withContext(ioDispatcher) {
        mediaDao.deleteMediaById(id)
    }

    override suspend fun deleteMultipleMedia(ids: List<Long>) = withContext(ioDispatcher) {
        mediaDao.deleteMediaByIds(ids)
    }

    override fun searchMedia(query: String): Flow<List<MediaEntity>> = mediaDao.searchMedia(query)
}
