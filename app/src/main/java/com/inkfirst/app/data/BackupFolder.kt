package com.inkfirst.app.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Remembers the folder the reader picked to back up into.
 *
 * The picker's grant does not outlive the process unless it is taken
 * persistably, and even then it can go away later — the card is pulled, the
 * folder is deleted, the user revokes it in Settings. So [uri] does not
 * simply return what was stored: it checks the grant is still held and
 * forgets the folder if it is not. A backup screen that offers to copy 15 MB
 * into a folder the app can no longer open is worse than one that asks again.
 */
class BackupFolder(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The chosen folder, or null if none is chosen or the grant is gone. */
    fun uri(): Uri? {
        val stored = prefs.getString(KEY_URI, null)?.let(Uri::parse) ?: return null
        val held = app.contentResolver.persistedUriPermissions.any {
            it.uri == stored && it.isReadPermission && it.isWritePermission
        }
        if (!held) {
            forget()
            return null
        }
        return stored
    }

    /**
     * Where the picker should open when it is opened.
     *
     * The chosen folder if there is one — changing it usually means moving it
     * somewhere near — and otherwise a suggestion: `Documents/Zhuzha` on
     * the device's own storage. The app cannot CREATE that folder or write to
     * it unaided; a folder outside its own is reachable only through the
     * picker, by a grant a person hands over. What it can do is open the
     * picker where the answer probably is, so choosing is one tap rather than
     * a walk down a tree.
     *
     * A hint only: a provider that cannot resolve it opens wherever it likes,
     * which is exactly what happened before.
     */
    fun suggestion(): Uri = uri() ?: DocumentsContract.buildDocumentUri(
        EXTERNAL_STORAGE_PROVIDER,
        // Documents itself, not Documents/Zhuzha: pointing the picker at a
        // folder that does not exist yet is how you get it to give up and
        // open wherever it feels like, which is the case this is for.
        "primary:Documents",
    )

    /** Takes the long-lived grant the picker offers, and stores the folder. */
    fun remember(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }
        prefs.edit().putString(KEY_URI, uri.toString()).apply()
    }

    fun forget() {
        prefs.edit().remove(KEY_URI).apply()
    }

    /**
     * What to call the folder on screen. The provider's own display name when
     * it gives one ("Downloads", "Zhuzha backup"), otherwise the tail of
     * the document id, which for the built-in provider reads like
     * "primary:Documents/Zhuzha".
     */
    fun label(uri: Uri): String {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val name = documentId?.let { displayName(uri, it) }
        if (!name.isNullOrBlank()) return name
        return documentId?.substringAfterLast(':')?.takeIf { it.isNotBlank() } ?: uri.toString()
    }

    private fun displayName(treeUri: Uri, documentId: String): String? {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return runCatching {
            app.contentResolver.query(docUri, projection, null, null, null)?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    companion object {
        /** Shown on screen when no folder is chosen, so the suggestion is sayable. */
        const val SUGGESTED_PATH = "Documents/Zhuzha"

        /** What the picker actually opens at — the parent, which exists. */
        const val SUGGESTED_PARENT = "Documents"

        private const val EXTERNAL_STORAGE_PROVIDER = "com.android.externalstorage.documents"
        private const val PREFS_NAME = "inkfirst_backup"
        private const val KEY_URI = "folder_uri"
    }
}
