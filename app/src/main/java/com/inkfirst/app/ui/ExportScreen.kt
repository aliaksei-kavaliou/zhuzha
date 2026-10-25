package com.inkfirst.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.Course
import com.inkfirst.core.EPUB_MIMETYPE
import com.inkfirst.core.EpubExport
import com.inkfirst.core.EpubOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Handing a book to the rest of the world.
 *
 * The point is not mainly sharing. It is that Markdown on disk keeps this
 * book readable in ten years for its OWNER, and an EPUB keeps it readable
 * for anyone — including on a device that has never heard of this app. It is
 * also the thing that makes "I finished a book" produce an object.
 *
 * One decision is visible on this screen and worth defending: the annotations
 * switch defaults to OFF. Export is irreversible — a file handed to someone
 * carries whatever is inside it, and a reader's questions are the most
 * private thing this app stores. Opting in costs one tap; opting out after
 * the file has been sent costs nothing that can be done.
 */
@Composable
fun ExportScreen(slug: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = rememberCourseStore()
    val scope = rememberCoroutineScope()

    var course by remember { mutableStateOf<Course?>(null) }
    LaunchedEffect(slug) { course = store.load(slug) }

    var includeAnnotations by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val written = remember(course) { course?.chapters?.count { store.readChapter(slug, it.slug) != null } ?: 0 }
    val noteCount = remember(course, slug) {
        val c = course ?: return@remember 0
        c.annotations.count { it.note.isNotBlank() }
    }

    // CreateDocument fixes its mime type at construction. The type and the
    // file name's extension MUST agree: createDocument treats the type as
    // authoritative and the name as a suggestion, so a mismatch comes back
    // with the provider's own extension appended (see DocumentTreeFileTree's
    // mimeTypeFor — the same trap, found during backup).
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EPUB_MIMETYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        saved = false
        status = "Writing…"
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val result = context.contentResolver.openOutputStream(uri)?.use { out ->
                        EpubExport(store).write(
                            slug = slug,
                            out = out,
                            options = EpubOptions(includeAnnotations = includeAnnotations),
                            modified = Instant.now().toString().substringBefore('.') + "Z",
                        )
                    } ?: error("Could not open that file for writing.")
                    buildString {
                        append("Saved. ${result.chapters} chapters")
                        if (result.pictures > 0) append(", ${result.pictures} pictures")
                        if (result.annotated) append(", with your notes")
                        append(".")
                    }
                }.getOrElse { "Could not export: ${it.message}" }
            }
            saved = status?.startsWith("Saved") == true
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Export", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }

        Text(
            text = course?.title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = "An EPUB: the chapters that have been written, their pictures, and the " +
                "exercises with their answers collected at the back of each chapter. " +
                "It opens in any e-reader.",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Include my notes and questions",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 12.dp),
            )
            Switch(checked = includeAnnotations, onCheckedChange = { includeAnnotations = it })
        }
        Text(
            text = if (includeAnnotations) {
                "Your notes and the answers you were given go in at the end of each chapter, " +
                    "and the title page says so. Anyone you send this file to will read them."
            } else {
                "Off: the book only. Turn this on for your own copy — but remember that " +
                    "whoever opens the file gets everything in it."
            },
            style = MaterialTheme.typography.bodySmall,
        )

        // Drawings never travel, whichever way the switch is set, and saying
        // so here is cheaper than being asked why they are missing.
        Text(
            text = "Handwriting is not exported: strokes are anchored to this device's " +
                "layout, and an EPUB reflows to whatever size it is read at.",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            enabled = !busy && written > 0,
            onClick = {
                status = null
                saver.launch(exportFileName(course?.title ?: slug))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Writing…" else "Save as EPUB…")
        }

        if (written == 0) {
            Text(
                text = "Nothing to export yet — no chapter of this book has been written.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (noteCount > 0 && !includeAnnotations) {
            Text(
                text = "You have $noteCount notes in this book. They will not be in the file.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        // WHY THIS SCREEN DOES NOT CLOSE ITSELF on a successful save.
        //
        // The result line is the only evidence the export happened, and
        // popping the moment it appears is the defect this app has already
        // shipped once: NewCourseScreen's own header records "new course
        // generate did nothing", where a finished result rendered somewhere
        // the reader never saw. Nothing on e-ink animates, so a screen that
        // vanishes leaves no transition to read either.
        //
        // The second reason is that exporting TWICE is a real errand — the
        // clean copy to send someone, then the annotated copy to keep — and
        // it costs one toggle and one tap while this screen is still here.
        //
        // So the exit is explicit instead, and only appears once there is
        // something to leave.
        if (saved) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            Text(
                text = "Or change the switch above and save a second copy.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * A file name a person would recognise on a laptop — the book's title, not
 * its slug. Stripped of the characters that are illegal on some filesystem or
 * other rather than only on this one, since the point of the file is to leave
 * this device.
 */
internal fun exportFileName(title: String): String {
    val safe = title.trim()
        .map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)
    return "${safe.ifEmpty { "book" }}.epub"
}
