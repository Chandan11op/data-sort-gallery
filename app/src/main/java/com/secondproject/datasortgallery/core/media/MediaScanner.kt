package com.secondproject.datasortgallery.core.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.local.entity.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun scanMedia(): List<MediaEntity> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaEntity>()
        mediaList.addAll(scanImages())
        mediaList.addAll(scanVideos())
        mediaList.addAll(scanAudio())
        mediaList
    }

    private fun scanImages(): List<MediaEntity> {
        val images = mutableListOf<MediaEntity>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "IMG_$id.jpg"
                val mimeType = cursor.getString(mimeColumn) ?: "image/jpeg"
                val size = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(addedColumn) * 1000
                val dateModified = cursor.getLong(modifiedColumn) * 1000
                val dateTaken = cursor.getLong(takenColumn).let { if (it == 0L) dateAdded else it }
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val relativePath = cursor.getString(pathColumn) ?: ""

                val contentUri = ContentUris.withAppendedId(collection, id).toString()
                val resolution = if (width > 0 && height > 0) "${width}x$height" else ""

                images.add(
                    MediaEntity(
                        id = id,
                        contentUri = contentUri,
                        filename = name,
                        mimeType = mimeType,
                        size = size,
                        dateAdded = dateAdded,
                        dateModified = dateModified,
                        captureDate = dateTaken,
                        width = width,
                        height = height,
                        resolution = resolution,
                        relativePath = relativePath,
                        mediaType = MediaType.IMAGE
                    )
                )
            }
        }
        return images
    }

    private fun scanVideos(): List<MediaEntity> {
        val videos = mutableListOf<MediaEntity>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.RELATIVE_PATH
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "VID_$id.mp4"
                val mimeType = cursor.getString(mimeColumn) ?: "video/mp4"
                val size = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(addedColumn) * 1000
                val dateModified = cursor.getLong(modifiedColumn) * 1000
                val dateTaken = cursor.getLong(takenColumn).let { if (it == 0L) dateAdded else it }
                val duration = cursor.getLong(durationColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val relativePath = cursor.getString(pathColumn) ?: ""

                val contentUri = ContentUris.withAppendedId(collection, id).toString()
                val resolution = if (width > 0 && height > 0) "${width}x$height" else ""

                videos.add(
                    MediaEntity(
                        id = id,
                        contentUri = contentUri,
                        filename = name,
                        mimeType = mimeType,
                        size = size,
                        dateAdded = dateAdded,
                        dateModified = dateModified,
                        captureDate = dateTaken,
                        duration = duration,
                        width = width,
                        height = height,
                        resolution = resolution,
                        relativePath = relativePath,
                        mediaType = MediaType.VIDEO
                    )
                )
            }
        }
        return videos
    }

    private fun scanAudio(): List<MediaEntity> {
        val audioList = mutableListOf<MediaEntity>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH
        )

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "AUD_$id.mp3"
                val mimeType = cursor.getString(mimeColumn) ?: "audio/mpeg"
                val size = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(addedColumn) * 1000
                val dateModified = cursor.getLong(modifiedColumn) * 1000
                val duration = cursor.getLong(durationColumn)
                val relativePath = cursor.getString(pathColumn) ?: ""

                val contentUri = ContentUris.withAppendedId(collection, id).toString()

                audioList.add(
                    MediaEntity(
                        id = id + 1_000_000_000L, // offset ID to avoid collision with image/video IDs if any overlap
                        contentUri = contentUri,
                        filename = name,
                        mimeType = mimeType,
                        size = size,
                        dateAdded = dateAdded,
                        dateModified = dateModified,
                        captureDate = dateAdded,
                        duration = duration,
                        relativePath = relativePath,
                        mediaType = MediaType.AUDIO
                    )
                )
            }
        }
        return audioList
    }
}
