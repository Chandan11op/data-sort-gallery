package com.secondproject.datasortgallery

import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.local.entity.MediaType
import com.secondproject.datasortgallery.feature.duplicates.DuplicateGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateCleanerTest {

    private fun createMedia(
        uniqueId: String,
        size: Long,
        resolution: String = "1920x1080",
        hash: String? = null
    ): MediaEntity {
        return MediaEntity(
            uniqueId = uniqueId,
            id = 1L,
            contentUri = "content://media/external/images/media/1",
            filename = "$uniqueId.jpg",
            mimeType = "image/jpeg",
            size = size,
            dateAdded = 1000L,
            dateModified = 1000L,
            captureDate = 1000L,
            resolution = resolution,
            sha256Hash = hash,
            mediaType = MediaType.IMAGE
        )
    }

    @Test
    fun testDuplicateGrouping_IdentifiesDuplicatesAndPreservesOriginal() {
        val original = createMedia("img_1", size = 2048L, resolution = "1920x1080")
        val dup1 = createMedia("img_2", size = 2048L, resolution = "1920x1080")
        val dup2 = createMedia("img_3", size = 2048L, resolution = "1920x1080")
        val uniqueItem = createMedia("img_4", size = 4096L, resolution = "1920x1080")

        val mediaList = listOf(original, dup1, dup2, uniqueItem)

        val duplicateGroups = mediaList.filter { it.size > 0 }
            .groupBy { media ->
                if (!media.sha256Hash.isNullOrBlank()) {
                    "hash_${media.sha256Hash}"
                } else if (media.resolution.isNotBlank()) {
                    "size_res_${media.size}_${media.resolution}"
                } else {
                    "size_name_${media.size}_${media.filename}"
                }
            }
            .filter { it.value.size > 1 }
            .map { (key, items) -> DuplicateGroup(key, items) }

        assertEquals(1, duplicateGroups.size)
        val group = duplicateGroups.first()
        assertEquals(3, group.items.size)
        assertEquals("img_1", group.items.first().uniqueId) // Original kept

        val duplicatesToDelete = group.items.drop(1).map { it.uniqueId }
        assertEquals(listOf("img_2", "img_3"), duplicatesToDelete)
    }

    @Test
    fun testDuplicateGrouping_IgnoresZeroSizeBytes() {
        val zero1 = createMedia("img_0_1", size = 0L, resolution = "1920x1080")
        val zero2 = createMedia("img_0_2", size = 0L, resolution = "1920x1080")

        val mediaList = listOf(zero1, zero2)

        val duplicateGroups = mediaList.filter { it.size > 0 }
            .groupBy { media ->
                if (!media.sha256Hash.isNullOrBlank()) {
                    "hash_${media.sha256Hash}"
                } else if (media.resolution.isNotBlank()) {
                    "size_res_${media.size}_${media.resolution}"
                } else {
                    "size_name_${media.size}_${media.filename}"
                }
            }
            .filter { it.value.size > 1 }
            .map { (key, items) -> DuplicateGroup(key, items) }

        assertTrue(duplicateGroups.isEmpty())
    }

    @Test
    fun testChunkingLogic_SplitsLargeBatchesSafely() {
        val totalItems = 1250
        val ids = (1..totalItems).map { "item_$it" }

        val chunks = ids.chunked(500)
        assertEquals(3, chunks.size)
        assertEquals(500, chunks[0].size)
        assertEquals(500, chunks[1].size)
        assertEquals(250, chunks[2].size)
        assertEquals(totalItems, chunks.sumOf { it.size })
    }

    @Test
    fun testRecoverableStorageCalculation() {
        val orig = createMedia("orig", size = 10 * 1024 * 1024L) // 10MB
        val d1 = createMedia("d1", size = 10 * 1024 * 1024L) // 10MB
        val d2 = createMedia("d2", size = 10 * 1024 * 1024L) // 10MB

        val group = DuplicateGroup("group1", listOf(orig, d1, d2))
        val groups = listOf(group)

        val totalRecoverableBytes = groups.sumOf { g ->
            g.items.drop(1).sumOf { it.size }
        }

        assertEquals(20 * 1024 * 1024L, totalRecoverableBytes)
    }
}

