package com.secondproject.datasortgallery.core.media

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.secondproject.datasortgallery.core.common.IoDispatcher
import com.secondproject.datasortgallery.data.local.dao.MediaDao
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class MediaActionType {
    TRASH,
    RESTORE,
    PERMANENT_DELETE
}

data class MediaConsentRequest(
    val intentSender: IntentSender,
    val targetUniqueIds: List<String>,
    val actionType: MediaActionType
)

sealed class MediaOperationResult {
    data class RequiresConsent(val request: MediaConsentRequest) : MediaOperationResult()
    data class Completed(val targetUniqueIds: List<String>, val actionType: MediaActionType) : MediaOperationResult()
    data class Error(val message: String) : MediaOperationResult()
}

@Singleton
class MediaStoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "MediaStoreManager"
    }

    suspend fun prepareTrashRequest(
        items: List<MediaEntity>,
        trash: Boolean
    ): MediaOperationResult = withContext(ioDispatcher) {
        val actionType = if (trash) MediaActionType.TRASH else MediaActionType.RESTORE
        if (items.isEmpty()) {
            return@withContext MediaOperationResult.Completed(emptyList(), actionType)
        }

        try {
            val (existingItems, missingUniqueIds) = verifyAndFilterExistingMedia(items)
            if (missingUniqueIds.isNotEmpty()) {
                Log.d(TAG, "Cleaning up ${missingUniqueIds.size} already deleted items from database")
                mediaDao.deleteMediaByIdsChunked(missingUniqueIds)
            }

            if (existingItems.isEmpty()) {
                return@withContext MediaOperationResult.Completed(emptyList(), actionType)
            }

            val validUris = existingItems.mapNotNull { parseValidContentUri(it.contentUri) }
            val validUniqueIds = existingItems.map { it.uniqueId }

            if (validUris.isEmpty()) {
                return@withContext MediaOperationResult.Completed(emptyList(), actionType)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, validUris, trash)
                MediaOperationResult.RequiresConsent(
                    MediaConsentRequest(
                        intentSender = pendingIntent.intentSender,
                        targetUniqueIds = validUniqueIds,
                        actionType = actionType
                    )
                )
            } else {
                // Pre-Android 11 fallback: MediaStore trash API is not available
                // Update local database trash status directly
                MediaOperationResult.Completed(validUniqueIds, actionType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing trash request", e)
            MediaOperationResult.Error(e.localizedMessage ?: "Failed to prepare trash request")
        }
    }

    suspend fun prepareDeleteRequest(
        items: List<MediaEntity>
    ): MediaOperationResult = withContext(ioDispatcher) {
        val actionType = MediaActionType.PERMANENT_DELETE
        if (items.isEmpty()) {
            return@withContext MediaOperationResult.Completed(emptyList(), actionType)
        }

        try {
            val (existingItems, missingUniqueIds) = verifyAndFilterExistingMedia(items)
            if (missingUniqueIds.isNotEmpty()) {
                Log.d(TAG, "Cleaning up ${missingUniqueIds.size} already deleted items from database")
                mediaDao.deleteMediaByIdsChunked(missingUniqueIds)
            }

            if (existingItems.isEmpty()) {
                return@withContext MediaOperationResult.Completed(emptyList(), actionType)
            }

            val validUris = existingItems.mapNotNull { parseValidContentUri(it.contentUri) }
            val validUniqueIds = existingItems.map { it.uniqueId }

            if (validUris.isEmpty()) {
                return@withContext MediaOperationResult.Completed(emptyList(), actionType)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, validUris)
                MediaOperationResult.RequiresConsent(
                    MediaConsentRequest(
                        intentSender = pendingIntent.intentSender,
                        targetUniqueIds = validUniqueIds,
                        actionType = actionType
                    )
                )
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                // Android 10 (Q): Catch RecoverableSecurityException if user consent needed
                for (uri in validUris) {
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (secEx: SecurityException) {
                        if (secEx is RecoverableSecurityException) {
                            return@withContext MediaOperationResult.RequiresConsent(
                                MediaConsentRequest(
                                    intentSender = secEx.userAction.actionIntent.intentSender,
                                    targetUniqueIds = validUniqueIds,
                                    actionType = actionType
                                )
                            )
                        } else {
                            throw secEx
                        }
                    }
                }
                MediaOperationResult.Completed(validUniqueIds, actionType)
            } else {
                // Android 9 or lower
                validUris.forEach { uri ->
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete URI: $uri", e)
                    }
                }
                MediaOperationResult.Completed(validUniqueIds, actionType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing delete request", e)
            MediaOperationResult.Error(e.localizedMessage ?: "Failed to prepare delete request")
        }
    }

    private fun verifyAndFilterExistingMedia(items: List<MediaEntity>): Pair<List<MediaEntity>, List<String>> {
        val existing = ArrayList<MediaEntity>(items.size)
        val missingUniqueIds = ArrayList<String>()

        val projection = arrayOf(MediaStore.MediaColumns._ID)
        for (item in items) {
            val uri = parseValidContentUri(item.contentUri)
            if (uri == null) {
                missingUniqueIds.add(item.uniqueId)
                continue
            }

            var exists = false
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        exists = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying uri existence: $uri", e)
                exists = false
            }

            if (exists) {
                existing.add(item)
            } else {
                missingUniqueIds.add(item.uniqueId)
            }
        }
        return Pair(existing, missingUniqueIds)
    }

    private fun parseValidContentUri(uriString: String?): Uri? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) uri else null
        } catch (e: Exception) {
            null
        }
    }
}

