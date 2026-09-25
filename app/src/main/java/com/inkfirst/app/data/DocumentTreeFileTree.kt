package com.inkfirst.app.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.inkfirst.core.FileTree
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The [FileTree] over a folder the reader picked through Android's document
 * picker — the far end of a backup.
 *
 * `:core` decides WHAT to copy and knows nothing about Android; this is the
 * half that knows about Android and decides nothing. Everything here is
 * ordinary file work said in the document API's vocabulary: a folder is not a
 * path but a *document id*, and the only way from one to another is to ask
 * the provider for a directory's children.
 *
 * Why not `DocumentFile` from androidx, which looks like exactly this class?
 * Because `DocumentFile.findFile(name)` lists the whole directory on every
 * call, so writing 400 files into one folder is 400 full listings — the
 * behaviour is quadratic and a course is mostly one big `figures/` directory.
 * Here every listing is one cursor over one directory, and [ids] remembers
 * what it saw, so a backup walks each directory once.
 *
 * Every failure is raised as [IOException], including the ones the platform
 * raises as `SecurityException` (permission revoked) or
 * `IllegalArgumentException` (a document id that no longer resolves). That is
 * not tidiness: `CourseBackup.run` catches [IOException] per file and carries
 * on, so anything else thrown from here abandons the other 399 files.
 */
class DocumentTreeFileTree(context: Context, private val treeUri: Uri) : FileTree {

    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val rootId: String = DocumentsContract.getTreeDocumentId(treeUri)

    /**
     * Relative path to document id, for files and directories alike; "" is
     * the root itself. Filled by [list] and by any lookup that has to walk.
     */
    private val ids = mutableMapOf("" to rootId)

    override fun list(): List<FileTree.Entry> {
        val out = mutableListOf<FileTree.Entry>()
        val pending = ArrayDeque<Pair<String, String>>()  // relative path, document id
        pending += "" to rootId
        while (pending.isNotEmpty()) {
            val (dirPath, dirId) = pending.removeFirst()
            childrenOf(dirId).forEach { child ->
                val path = if (dirPath.isEmpty()) child.name else "$dirPath/${child.name}"
                ids[path] = child.id
                if (child.isDirectory) pending += path to child.id
                else out += FileTree.Entry(path, child.size, child.modified)
            }
        }
        return out.sortedBy { it.path }
    }

    override fun read(path: String): ByteArray = readStream(path).use { it.readBytes() }

    override fun write(path: String, bytes: ByteArray) = writeStream(path) { it.write(bytes) }

    /**
     * The streaming form is the real one — a book packed as a zip is 15 MB
     * and there is no reason for it to exist twice, once in a buffer and once
     * on the card.
     */
    override fun writeStream(path: String, body: (OutputStream) -> Unit) {
        val cut = path.lastIndexOf('/')
        val name = path.substring(cut + 1)
        val parentPath = if (cut < 0) "" else path.substring(0, cut)

        wrapFailures(path) {
            val parentId = makeDirs(parentPath)
            val existing = ids[path] ?: childrenOf(parentId)
                .firstOrNull { it.name == name && !it.isDirectory }
                ?.id
            val target = when {
                existing != null -> documentUri(existing)
                else -> DocumentsContract.createDocument(
                    resolver,
                    documentUri(parentId),
                    mimeTypeFor(name),
                    name,
                ) ?: throw IOException("could not create $path")
            }
            ids[path] = DocumentsContract.getDocumentId(target)
            openTruncating(target).use(body)
        }
    }

    override fun readStream(path: String): InputStream {
        val id = idOf(path) ?: throw FileNotFoundException(path)
        return wrapFailures(path) {
            resolver.openInputStream(documentUri(id))
                ?: throw IOException("could not open $path")
        }
    }

    /**
     * The document id at a relative path, walking down from the root and
     * remembering what it passes. Null if nothing is there.
     */
    private fun idOf(path: String): String? {
        ids[path]?.let { return it }
        var currentPath = ""
        var currentId = rootId
        path.split("/").filter { it.isNotEmpty() }.forEach { segment ->
            val next = childrenOf(currentId).firstOrNull { it.name == segment } ?: return null
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            currentId = next.id
            ids[currentPath] = currentId
        }
        return currentId
    }

    /** Creates any missing directories along [path] and returns the last one's id. */
    private fun makeDirs(path: String): String {
        var currentPath = ""
        var currentId = rootId
        path.split("/").filter { it.isNotEmpty() }.forEach { segment ->
            val nextPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            val known = ids[nextPath]
            currentId = known ?: run {
                val there = childrenOf(currentId).firstOrNull { it.name == segment && it.isDirectory }
                there?.id ?: DocumentsContract.createDocument(
                    resolver,
                    documentUri(currentId),
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    segment,
                )?.let { DocumentsContract.getDocumentId(it) }
                ?: throw IOException("could not create folder $nextPath")
            }
            ids[nextPath] = currentId
            currentPath = nextPath
        }
        return currentId
    }

    private fun childrenOf(dirId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
        val out = mutableListOf<Child>()
        resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                out += Child(
                    id = cursor.getString(0),
                    name = name,
                    mime = cursor.getString(2).orEmpty(),
                    size = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                    modified = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                )
            }
        }
        return out
    }

    private fun documentUri(id: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    /**
     * "wt" is write-and-truncate. Plain "w" is not guaranteed to shorten an
     * existing file, so restoring a shorter chapter over a longer one could
     * otherwise leave the tail of the old one behind. Not every provider
     * accepts the mode, hence the fallback.
     */
    private fun openTruncating(uri: Uri) =
        (runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri))
            ?: throw IOException("could not write to $uri")

    private fun <T> wrapFailures(path: String, block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw e
    } catch (e: SecurityException) {
        // The folder's permission was revoked, or the card it lives on was
        // pulled. Per-file so the report can name what got through.
        throw IOException("no longer allowed to read this folder ($path)", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("$path is not where it was", e)
    } catch (e: IllegalStateException) {
        throw IOException("$path could not be written", e)
    } catch (e: UnsupportedOperationException) {
        throw IOException("this folder does not allow that ($path)", e)
    }

    private data class Child(
        val id: String,
        val name: String,
        val mime: String,
        val size: Long,
        val modified: Long,
    ) {
        val isDirectory: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}

/**
 * The type to create a file as — and the reason it is derived from the name's
 * extension rather than picked to be something sensible.
 *
 * `createDocument` treats the type as authoritative and the name as a
 * suggestion: if the two disagree, the provider appends the extension its
 * type implies. Ask for "01-intro.md" as `application/octet-stream` on a
 * device whose mime table knows `md`, and the backup holds "01-intro.md.bin".
 * Deriving the type FROM the extension makes them agree by construction, for
 * every extension and on every device, and the name survives — which matters
 * because these filenames are the app's index: `CourseStore` finds a chapter
 * by its slug, so a restored "01-intro.md.bin" is a chapter the app cannot
 * see.
 */
internal fun mimeTypeFor(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    if (extension.isEmpty()) return DEFAULT_MIME
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: DEFAULT_MIME
}

private const val DEFAULT_MIME = "application/octet-stream"
