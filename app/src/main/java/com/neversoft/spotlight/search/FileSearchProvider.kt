package com.neversoft.spotlight.search

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.neversoft.spotlight.model.LaunchAction
import com.neversoft.spotlight.model.ResultType
import com.neversoft.spotlight.model.SearchResult
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

/** Describes what the file walk should collect. */
data class FileSearchSpec(
    val includeFiles: Boolean = true,
    val includeFolders: Boolean = false,
    /** Only entries whose name starts with '.' (hidden). */
    val hiddenOnly: Boolean = false,
    /** Restrict to these extensions (null = any). */
    val extensions: Set<String>? = null,
    /** Restrict to anything under a Download/ directory. */
    val downloadsOnly: Boolean = false,
)

/**
 * Walks the whole external storage tree to find matching files when the app has
 * all-files access; otherwise falls back to the indexed MediaStore.Files table.
 */
class FileSearchProvider(private val context: Context) {

    /** True when the user has granted "All files access" (Android 11+) or
     *  legacy read permission (Android 10 and below). */
    fun hasFullAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        // Legacy READ_EXTERNAL_STORAGE is requested at runtime on these versions.
        true
    }

    fun search(
        query: String,
        spec: FileSearchSpec,
        limit: Int,
        deadlineMs: Long,
        isActive: () -> Boolean,
    ): List<SearchResult> {
        return if (hasFullAccess()) {
            walk(query, spec, limit, deadlineMs, isActive)
        } else {
            queryMediaStoreFiles(query, spec, limit, isActive)
        }
    }

    // --- Full filesystem traversal -----------------------------------------

    private fun walk(
        query: String,
        spec: FileSearchSpec,
        limit: Int,
        deadlineMs: Long,
        isActive: () -> Boolean,
    ): List<SearchResult> {
        val q = query.lowercase(Locale.getDefault())
        val out = ArrayList<SearchResult>()
        val roots = storageRoots()
        val stack = ArrayDeque<File>()
        roots.forEach { if (it.isDirectory) stack.push(it) }

        var visited = 0
        val maxVisited = 120_000

        while (stack.isNotEmpty()) {
            if (!isActive() || out.size >= limit ||
                visited >= maxVisited || System.currentTimeMillis() > deadlineMs
            ) break

            val dir = stack.pop()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (out.size >= limit) break
                visited++
                val name = child.name
                val isHidden = name.startsWith(".")

                if (child.isDirectory) {
                    // Skip the restricted Android/data & Android/obb sandboxes.
                    if (isRestricted(child)) continue
                    stack.push(child)
                    if (spec.includeFolders && matches(child, q, spec, isDir = true)) {
                        out += folderResult(child)
                    }
                } else if (spec.includeFiles && matches(child, q, spec, isDir = false)) {
                    out += fileResult(child)
                }

                if (spec.hiddenOnly && !isHidden) {
                    // still traversed dirs above; nothing to add for non-hidden
                }
            }
        }
        return out
    }

    private fun matches(file: File, lowerQuery: String, spec: FileSearchSpec, isDir: Boolean): Boolean {
        val name = file.name
        if (!name.lowercase(Locale.getDefault()).contains(lowerQuery)) return false
        if (spec.hiddenOnly && !name.startsWith(".")) return false
        if (spec.downloadsOnly && !file.absolutePath.contains("/Download", ignoreCase = true)) return false
        if (!isDir) {
            val exts = spec.extensions
            if (exts != null && FileTypes.extensionOf(name) !in exts) return false
        }
        return true
    }

    private fun fileResult(file: File): SearchResult {
        val type = FileTypes.typeForName(file.name)
        val mime = FileTypes.mimeFor(file.name)
        return SearchResult(
            id = "file:${file.absolutePath}",
            title = file.name,
            subtitle = file.parent ?: file.absolutePath,
            type = type,
            launch = LaunchAction.OpenFile(file.absolutePath, mime),
            sizeBytes = file.length(),
            dateModified = file.lastModified(),
        )
    }

    private fun folderResult(dir: File): SearchResult = SearchResult(
        id = "folder:${dir.absolutePath}",
        title = dir.name,
        subtitle = dir.parent ?: dir.absolutePath,
        type = ResultType.FOLDER,
        launch = LaunchAction.OpenFile(dir.absolutePath, "resource/folder"),
        dateModified = dir.lastModified(),
    )

    private fun isRestricted(dir: File): Boolean {
        val p = dir.absolutePath
        return p.endsWith("/Android/data") || p.endsWith("/Android/obb")
    }

    private fun storageRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        Environment.getExternalStorageDirectory()?.let { roots.add(it) }
        // Secondary volumes (SD cards / USB) exposed to the app.
        context.getExternalFilesDirs(null).forEach { f ->
            // Climb up from /storage/XXXX/Android/data/<pkg>/files to /storage/XXXX
            var cursor: File? = f
            repeat(4) { cursor = cursor?.parentFile }
            cursor?.let { if (it.isDirectory) roots.add(it) }
        }
        return roots.toList()
    }

    // --- MediaStore.Files fallback (no all-files access) -------------------

    private fun queryMediaStoreFiles(
        query: String,
        spec: FileSearchSpec,
        limit: Int,
        isActive: () -> Boolean,
    ): List<SearchResult> {
        val filesUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$query%")
        val sort = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        val out = ArrayList<SearchResult>()
        try {
            context.contentResolver.query(filesUri, projection, selection, args, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    if (!isActive() || out.size >= limit) break
                    val name = cursor.getString(nameCol) ?: continue
                    if (spec.hiddenOnly && !name.startsWith(".")) continue
                    val exts = spec.extensions
                    if (exts != null && FileTypes.extensionOf(name) !in exts) continue
                    val relPath = cursor.getString(pathCol) ?: ""
                    if (spec.downloadsOnly && !relPath.contains("Download", ignoreCase = true)) continue

                    val id = cursor.getLong(idCol)
                    val mime = cursor.getString(mimeCol) ?: FileTypes.mimeFor(name)
                    val itemUri = ContentUris.withAppendedId(filesUri, id)
                    out += SearchResult(
                        id = "file:$id",
                        title = name,
                        subtitle = relPath.ifEmpty { mime },
                        type = FileTypes.typeForName(name),
                        launch = LaunchAction.OpenUri(itemUri.toString(), mime),
                        sizeBytes = cursor.getLong(sizeCol),
                        dateModified = cursor.getLong(dateCol) * 1000L,
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return out
    }
}
