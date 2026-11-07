package com.secondproject.datasortgallery.core.media

import java.util.Locale

data class BulkOperationState(
    val operationType: MediaActionType = MediaActionType.TRASH,
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val failedFiles: Int = 0,
    val currentBatch: Int = 1,
    val totalBatches: Int = 1,
    val recoveredBytes: Long = 0L,
    val totalBytesToRecover: Long = 0L,
    val isRunning: Boolean = true,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val errorMessage: String? = null
) {
    val remainingFiles: Int
        get() = (totalFiles - processedFiles - failedFiles).coerceAtLeast(0)

    val progressPercent: Int
        get() = if (totalFiles > 0) ((processedFiles + failedFiles) * 100 / totalFiles).coerceIn(0, 100) else 0

    val progressFraction: Float
        get() = if (totalFiles > 0) (processedFiles + failedFiles).toFloat() / totalFiles.toFloat() else 0f

    val recoveredFormatted: String
        get() = formatBytes(recoveredBytes)

    val totalBytesFormatted: String
        get() = formatBytes(totalBytesToRecover)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}

