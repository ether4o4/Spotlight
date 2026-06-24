package com.neversoft.spotlight.search

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.neversoft.spotlight.model.LaunchAction
import com.neversoft.spotlight.model.ResultType
import com.neversoft.spotlight.model.SearchResult
import com.neversoft.spotlight.model.StorageScope

/**
 * Searches the system MediaStore for photos, videos and audio by display name.
 * Works without all-files access because the MediaStore is already indexed.
 */
class MediaSearchProvider(private val context: Context) {

    private data class Collection(
        val uri: Uri,
        val type: ResultType,
    )

    fun search(
        query: String,
        types: Set<ResultType>,
        limit: Int,
        isActive: () -> Boolean,
        scope: StorageScope = StorageScope.ALL,
    ): List<SearchResult> {
        val primaryRoot = Environment.getExternalStorageDirectory()?.absolutePath
        val collections = buildList {
            if (ResultType.IMAGE in types) {
                add(Collection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ResultType.IMAGE))
            }
            if (ResultType.VIDEO in types) {
                add(Collection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ResultType.VIDEO))
            }
            if (ResultType.AUDIO in types) {
                add(Collection(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ResultType.AUDIO))
            }
        }

        val out = ArrayList<SearchResult>()
        for (c in collections) {
            if (!isActive() || out.size >= limit) break
            out += queryCollection(c, query, limit - out.size, isActive, scope, primaryRoot)
        }
        return out
    }

    private fun queryCollection(
        collection: Collection,
        query: String,
        limit: Int,
        isActive: () -> Boolean,
        scope: StorageScope,
        primaryRoot: String?,
    ): List<SearchResult> {
        @Suppress("DEPRECATION") // DATA is the one path column present on every API level (min 26).
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA,
        )
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$query%")
        val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        val out = ArrayList<SearchResult>()
        try {
            context.contentResolver.query(collection.uri, projection, selection, args, sort)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    @Suppress("DEPRECATION")
                    val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)

                    while (cursor.moveToNext()) {
                        if (!isActive() || out.size >= limit) break
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val size = cursor.getLong(sizeCol)
                        val date = cursor.getLong(dateCol)
                        val mime = cursor.getString(mimeCol) ?: FileTypes.mimeFor(name)
                        val fullPath = cursor.getString(pathCol)
                        if (scope != StorageScope.ALL &&
                            (fullPath == null || !StorageScope.matches(scope, fullPath, primaryRoot))
                        ) continue
                        val folder = fullPath?.substringBeforeLast('/', "")?.ifEmpty { null }
                        val itemUri = ContentUris.withAppendedId(collection.uri, id)

                        out += SearchResult(
                            id = "media:${collection.type}:$id",
                            title = name,
                            subtitle = folder ?: mime,
                            type = collection.type,
                            launch = LaunchAction.OpenUri(itemUri.toString(), mime),
                            sizeBytes = size,
                            dateModified = date * 1000L,
                            thumbnailUri = if (collection.type != ResultType.AUDIO) itemUri.toString() else null,
                        )
                    }
                }
        } catch (e: Exception) {
            // Permission missing or provider error -> just return what we have.
        }
        return out
    }
}
