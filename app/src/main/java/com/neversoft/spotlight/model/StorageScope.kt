package com.neversoft.spotlight.model

import androidx.annotation.StringRes
import com.neversoft.spotlight.R

/**
 * Where on the device to search, ordered from the "furthest" storage (removable
 * volumes such as an SD card or USB/OTG drive) down to internal storage.
 * [ALL] is the default and searches everywhere.
 *
 * Cloud locations are intentionally NOT modelled here: they cannot be walked as a
 * filesystem and would need Storage Access Framework / account integration.
 */
enum class StorageScope(@StringRes val labelRes: Int) {
    ALL(R.string.scope_all),
    REMOVABLE(R.string.scope_removable),
    INTERNAL(R.string.scope_internal);

    companion object {
        /** A path is "internal" when it lives under the primary storage root. */
        fun isInternal(path: String, primaryRoot: String?): Boolean =
            primaryRoot != null && (path == primaryRoot || path.startsWith("$primaryRoot/"))

        /** True if [path] belongs to [scope], given the primary storage root. */
        fun matches(scope: StorageScope, path: String, primaryRoot: String?): Boolean =
            when (scope) {
                ALL -> true
                INTERNAL -> isInternal(path, primaryRoot)
                REMOVABLE -> !isInternal(path, primaryRoot)
            }
    }
}
