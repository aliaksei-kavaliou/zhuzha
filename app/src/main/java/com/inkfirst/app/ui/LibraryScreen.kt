package com.inkfirst.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.rememberCourseStore
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

    LaunchedEffect(Unit) { summaries = store.list() }

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
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenCourse(summary.slug) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(summary.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${summary.chapterCount} chapters · ${summary.created}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        // Its own clickable, so aiming for the × never opens
                        // the book instead. Same shape as the note delete in
                        // the Contents.
                        Text(
                            text = "×",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clickable { pendingDelete = summary }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
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
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(doomed.title) },
            text = {
                Text(
                    "Remove this book and everything in it" +
                        (if (loss.isEmpty()) "" else " — $loss") +
                        ". This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteCourse(doomed.slug)
                    summaries = store.list()
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep") }
            },
        )
    }
}

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
