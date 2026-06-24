package com.neversoft.spotlight.search

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import com.neversoft.spotlight.model.LaunchAction
import com.neversoft.spotlight.model.ResultType
import com.neversoft.spotlight.model.SearchResult

/** Finds device contacts by display name. Requires READ_CONTACTS. */
class ContactSearchProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun search(query: String, limit: Int, isActive: () -> Boolean): List<SearchResult> {
        if (!hasPermission()) return emptyList()

        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$query%")
        val sort = "${ContactsContract.Contacts.DISPLAY_NAME} ASC"

        val out = ArrayList<SearchResult>()
        try {
            context.contentResolver.query(uri, projection, selection, args, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val lookupCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
                val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                val phoneCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                while (cursor.moveToNext()) {
                    if (!isActive() || out.size >= limit) break
                    val id = cursor.getLong(idCol)
                    val lookup = cursor.getString(lookupCol) ?: continue
                    val name = cursor.getString(nameCol) ?: continue
                    val hasPhone = cursor.getInt(phoneCol) == 1
                    val contactUri = ContactsContract.Contacts.getLookupUri(id, lookup)
                    out += SearchResult(
                        id = "contact:$id",
                        title = name,
                        subtitle = if (hasPhone) "Contact · has phone number" else "Contact",
                        type = ResultType.CONTACT,
                        launch = LaunchAction.ViewContact(contactUri.toString()),
                    )
                }
            }
        } catch (e: Exception) {
            // ignore (permission revoked mid-query, etc.)
        }
        return out
    }
}
