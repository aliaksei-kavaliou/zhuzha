package com.inkfirst.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.rememberCourseStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.inkfirst.core.ChapterStatus
import com.inkfirst.core.CourseStore
import com.inkfirst.core.CourseSummary

@Composable
fun LibraryScreen(
    onNewCourse: () -> Unit,
    onSettings: () -> Unit,
    onOpenCourse: (String) -> Unit,
) {
    val store = rememberCourseStore()
    var summaries by remember { mutableStateOf<List<CourseSummary>>(emptyList()) }
    // The book the reader has asked to remove, held until they confirm. Null
    // means no dialog is up.
    var pendingDelete by remember { mutableStateOf<CourseSummary?>(null) }

    // Faces for the shelf, resolved once off the main thread. A miss is a
    // real answer — a book with no illustrated chapter yet has no face — so
    // the map holds nulls rather than being retried on every recomposition.
    var covers by remember { mutableStateOf<Map<String, ImageBitmap?>>(emptyMap()) }

    LaunchedEffect(Unit) { summaries = store.list() }

    LaunchedEffect(summaries) {
        if (summaries.isEmpty()) return@LaunchedEffect
        covers = withContext(Dispatchers.IO) {
            summaries.associate { it.slug to thumbnail(store.bookCoverFile(it.slug)) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("InkFirst", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSettings) { Text("Settings") }
        }
        Text("AI-generated study books.", style = MaterialTheme.typography.bodySmall)

        Button(onClick = onNewCourse) { Text("New course") }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        if (summaries.isEmpty()) {
            Text(
                text = "No courses yet. Tap New course to start.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(summaries) { summary ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookFace(
                            cover = covers[summary.slug],
                            title = summary.title,
                            modifier = Modifier
                                .clickable { onOpenCourse(summary.slug) }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenCourse(summary.slug) }
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            Text(summary.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = shelfLine(summary),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        // Its own clickable, so aiming for the bin never
                        // opens the book instead. Same shape as the note
                        // delete in the Contents.
                        //
                        // A bin rather than the × this used to be: × is the
                        // DISMISS glyph everywhere else in software, so on a
                        // row it reads as "close this", which is the opposite
                        // of what it does. Outlined rather than filled, to sit
                        // with the rest of the line art.
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Remove this book",
                            tint = Ink,
                            modifier = Modifier
                                .clickable { pendingDelete = summary }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .size(22.dp),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { doomed ->
        // A confirm step rather than an undo, because there is nothing to
        // undo from: the files ARE the book. Naming what is about to go —
        // the chapters that cost API calls, the notes nobody can regenerate —
        // is the part that makes the decision an informed one.
        val loss = remember(doomed.slug) { describeLoss(store, doomed.slug) }
        InkDialog(
            title = doomed.title,
            onDismiss = { pendingDelete = null },
            confirmLabel = "Remove",
            onConfirm = {
                store.deleteCourse(doomed.slug)
                summaries = store.list()
                pendingDelete = null
            },
            dismissLabel = "Keep",
        ) {
            Text(
                text = "Remove this book and everything in it" +
                    (if (loss.isEmpty()) "" else " — $loss") +
                    ". This cannot be undone.",
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

/**
 * The line under a book's title: how long it is, and how far in you are.
 *
 * The date it was created is gone from it. It answered a question nobody asks
 * of their own shelf, and the answer people do want — "where was I?" — was
 * not on this screen at all. Both numbers come from the book's own
 * course.json, so every book on the shelf can say where it is up to; the
 * marker used to come from one root-level pointer and could therefore only
 * ever be right about the last book opened.
 */
private fun shelfLine(summary: CourseSummary): String {
    val chapters = "${summary.chapterCount} chapters"
    val current = summary.currentChapterN
    return when {
        current != null -> "$chapters · reading ${current}"
        summary.chaptersDone >= summary.chapterCount && summary.chapterCount > 0 ->
            "$chapters · finished"
        else -> "$chapters · not started"
    }
}

/**
 * A book's face on the shelf: its first drawn chapter picture, or its initial
 * lettered on a plain card while it has none.
 *
 * Portrait, cropped from the 4:3 the pictures are drawn at, because a book is
 * portrait and a shelf of landscape rectangles does not read as books. The
 * crop takes the middle, which is where the house style puts the subject —
 * "ZOOM IN CLOSE, the subject fills the frame" — so the sides it loses are
 * the empty ones.
 *
 * The lettered fallback is not a placeholder to be replaced later. A book
 * with no chapter written yet HAS no picture, and an initial on a bordered
 * card is what an unjacketed book looks like anyway.
 */
@Composable
private fun BookFace(cover: ImageBitmap?, title: String, modifier: Modifier = Modifier) {
    val face = modifier
        .width(FACE_WIDTH)
        .height(FACE_HEIGHT)
        .border(width = 1.dp, color = Hairline)
    if (cover != null) {
        Image(
            bitmap = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = face,
        )
    } else {
        Box(modifier = face, contentAlignment = Alignment.Center) {
            Text(
                text = title.trim().take(1).uppercase(),
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                color = Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A shelf-sized face. Portrait, in the proportions of a paperback. */
private val FACE_WIDTH = 44.dp
private val FACE_HEIGHT = 60.dp

/**
 * Decodes a picture down to shelf size.
 *
 * Downsampled while reading rather than after: the source is a 1K square from
 * the image model, and decoding a dozen of those at full size to draw them at
 * 44dp would cost tens of megabytes for pictures nobody can see the detail of.
 * `inSampleSize` halves per step, so this asks the decoder for the smallest
 * power-of-two reduction that still covers the face.
 */
private fun thumbnail(file: File?): ImageBitmap? {
    if (file == null || !file.isFile) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= FACE_DECODE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
    }.getOrNull()
}

/** Roughly the widest a face is drawn on the densest screen we target. */
private const val FACE_DECODE_PX = 160

/**
 * A plain-English inventory of what deleting a course destroys, for the
 * confirm dialog. Only counts what actually exists — a course with nothing in
 * it yet reads as an empty string rather than a row of zeroes.
 */
private fun describeLoss(store: CourseStore, slug: String): String {
    val course = store.load(slug) ?: return ""
    val written = course.chapters.count { it.status != ChapterStatus.PENDING }
    val questions = course.annotations.count { it.kind == "ask" }
    val notes = course.annotations.size - questions
    val inked = store.chaptersWithInk(slug).size
    val parts = buildList {
        if (written > 0) add(if (written == 1) "1 written chapter" else "$written written chapters")
        if (notes > 0) add(if (notes == 1) "1 note" else "$notes notes")
        if (questions > 0) add(if (questions == 1) "1 question" else "$questions questions")
        if (course.bookmarks.isNotEmpty()) {
            val n = course.bookmarks.size
            add(if (n == 1) "1 bookmark" else "$n bookmarks")
        }
        if (inked > 0) add(if (inked == 1) "ink on 1 chapter" else "ink on $inked chapters")
    }
    return when (parts.size) {
        0 -> ""
        1 -> parts.first()
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
}
