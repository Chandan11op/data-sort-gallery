package com.secondproject.datasortgallery.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.secondproject.datasortgallery.data.local.dao.MediaDao
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.local.entity.MediaType

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = try {
        MediaType.valueOf(value)
    } catch (e: Exception) {
        MediaType.IMAGE
    }
}

@Database(entities = [MediaEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        const val DATABASE_NAME = "data_sort_gallery_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `media_new` (
                        `uniqueId` TEXT NOT NULL,
                        `id` INTEGER NOT NULL,
                        `contentUri` TEXT NOT NULL,
                        `filename` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `size` INTEGER NOT NULL,
                        `dateAdded` INTEGER NOT NULL,
                        `dateModified` INTEGER NOT NULL,
                        `captureDate` INTEGER NOT NULL,
                        `duration` INTEGER NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `resolution` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `sha256Hash` TEXT,
                        `perceptualHash` TEXT,
                        `isFavorite` INTEGER NOT NULL,
                        `isTrashed` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `analysisStatus` TEXT NOT NULL,
                        PRIMARY KEY(`uniqueId`)
                    )
                """)

                database.execSQL("""
                    INSERT INTO `media_new` (
                        `uniqueId`, `id`, `contentUri`, `filename`, `mimeType`, `size`, 
                        `dateAdded`, `dateModified`, `captureDate`, `duration`, `width`, 
                        `height`, `resolution`, `relativePath`, `sha256Hash`, `perceptualHash`, 
                        `isFavorite`, `isTrashed`, `mediaType`, `analysisStatus`
                    )
                    SELECT 
                        CASE 
                            WHEN `mediaType` = 'VIDEO' THEN 'VIDEO_' || `id`
                            WHEN `mediaType` = 'AUDIO' THEN 'AUDIO_' || `id`
                            ELSE 'IMAGE_' || `id`
                        END,
                        `id`, `contentUri`, `filename`, `mimeType`, `size`, 
                        `dateAdded`, `dateModified`, `captureDate`, `duration`, `width`, 
                        `height`, `resolution`, `relativePath`, `sha256Hash`, `perceptualHash`, 
                        `isFavorite`, `isTrashed`, `mediaType`, `analysisStatus`
                    FROM `media`
                """)

                database.execSQL("DROP TABLE `media`")
                database.execSQL("ALTER TABLE `media_new` RENAME TO `media`")
            }
        }
    }
}
