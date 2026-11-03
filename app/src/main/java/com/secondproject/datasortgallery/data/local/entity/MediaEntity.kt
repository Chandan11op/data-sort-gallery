package com.secondproject.datasortgallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType {
    IMAGE, VIDEO, AUDIO
}

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey
    val id: Long, // MediaStore ID or unique hash-based ID
    val contentUri: String,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val captureDate: Long,
    val duration: Long = 0L, // For video/audio in ms
    val width: Int = 0,
    val height: Int = 0,
    val resolution: String = "",
    val relativePath: String = "",
    val sha256Hash: String? = null,
    val perceptualHash: String? = null,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val mediaType: MediaType = MediaType.IMAGE,
    val analysisStatus: String = "PENDING" // PENDING, ANALYZED, FAILED
)
