package com.secondproject.datasortgallery

import com.secondproject.datasortgallery.core.media.BulkOperationState
import com.secondproject.datasortgallery.core.media.MediaActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkOperationTest {

    @Test
    fun testBatchCountCalculation() {
        fun calculateBatches(total: Int, batchSize: Int = 500): Int {
            if (total <= 0) return 0
            return (total + batchSize - 1) / batchSize
        }

        // Test specified boundaries
        assertEquals(1, calculateBatches(10))
        assertEquals(1, calculateBatches(499))
        assertEquals(1, calculateBatches(500))
        assertEquals(2, calculateBatches(501))
        assertEquals(2, calculateBatches(1000))
        assertEquals(4, calculateBatches(2000))
        assertEquals(10, calculateBatches(5000))
        assertEquals(11, calculateBatches(5250))
    }

    @Test
    fun testChunkingIntegrity() {
        val totalItems = (1..5250).map { "item_$it" }
        val batches = totalItems.chunked(500)

        assertEquals(11, batches.size)
        assertEquals(500, batches[0].size)
        assertEquals(500, batches[9].size)
        assertEquals(250, batches[10].size)
        assertEquals(5250, batches.flatten().size)
    }

    @Test
    fun testBulkOperationStateProgress() {
        val state = BulkOperationState(
            operationType = MediaActionType.TRASH,
            totalFiles = 4250,
            processedFiles = 1500,
            failedFiles = 0,
            currentBatch = 4,
            totalBatches = 9,
            recoveredBytes = 1500L * 1024L * 1024L,
            totalBytesToRecover = 4250L * 1024L * 1024L,
            isRunning = true
        )

        assertEquals(2750, state.remainingFiles)
        assertEquals(35, state.progressPercent)
        assertEquals(1500f / 4250f, state.progressFraction, 0.001f)
        assertTrue(state.isRunning)
        assertFalse(state.isCompleted)
        assertFalse(state.isCancelled)
    }

    @Test
    fun testFormatBytesUtility() {
        assertEquals("0 B", BulkOperationState.formatBytes(0L))
        assertEquals("500 B", BulkOperationState.formatBytes(500L))
        assertEquals("1.0 KB", BulkOperationState.formatBytes(1024L))
        assertEquals("1.5 MB", BulkOperationState.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.50 GB", BulkOperationState.formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }
}

