package com.inkfirst.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.Course
import com.inkfirst.core.ReaderNotes

private val EdgeTapWidth = 44.dp

/**
 * What the book noticed while you read, and the means to cross it out.
 *
 * `reader.md` has been written since September and fed into every chapter
 * compiled since — see design.md §13 — with no surface in the app at all. A
 * book that forms opinions about its reader and shows them to nobody is the
 * one thing §13 set out not to build, so this is the part that was missing
 * rather than a feature on top.
 *
 * Two things follow from that and shape the screen. The reader has to be told
 * plainly what the file DOES, not just what is in it — an observation is
 * harmless, an observation that steers the next chapter is not, and only one
 * of those needs consent. And striking a line out has to be one tap with no
 * confirmation: a dialog asking "are you sure?" makes disagreeing with the
 * book feel like vandalism, when it is the point.
 */
private sealed interface NoteRow {
    data class Intro(val text: String) : NoteRow
    data class ChapterHeader(val label: String, val title: String?) : NoteRow
    data class GroupLabel(val text: String) : NoteRow
    data class Observation(val lineIndex: Int, val text: String) : NoteRow
    data class Empty(val text: String) : NoteRow
}

@Composable
fun ReaderNotesScreen(
    slug: String,
    onBack: () -> Unit,
) {
    val store = rememberCourseStore()
    var course by remember(slug) { mutableStateOf<Course?>(null) }
    var markdown by remember(slug) { mutableStateOf("") }

    LaunchedEffect(slug) {
        course = store.load(slug)
        markdown = store.readReaderNotes(slug)
    }

    val sections = remember(markdown) { ReaderNotes.parse(markdown) }
    val strike: (Int) -> Unit = { lineIndex ->
        val updated = ReaderNotes.removeObservations(markdown, setOf(lineIndex))
        store.writeReaderNotes(slug, updated)
        markdown = updated
    }

    val rows = remember(sections, course) {
        buildList {
            add(
                NoteRow.Intro(
                    "What the book noticed while you read. It is fed into the next " +
                        "chapter you compile, and only to decide which earlier ideas are " +
                        "worth restating in passing — never to rewrite a chapter around " +
                        "you. Cross out anything you disagree with and the book will not " +
                        "see it again.",
                ),
            )
            if (sections.isEmpty()) {
                add(
                    NoteRow.Empty(
                        "Nothing recorded yet. A chapter's reading is written down when " +
                            "the NEXT one is compiled, so this fills in as the book goes.",
                    ),
                )
            }
            sections.forEach { section ->
                val label = section.chapterN?.let { "Chapter $it" } ?: "This course"
                add(NoteRow.ChapterHeader(label, section.title))
                section.groups.forEach { group ->
                    group.label?.let { add(NoteRow.GroupLabel(it)) }
                    group.observations.forEach {
                        add(NoteRow.Observation(it.lineIndex, it.text))
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Paper)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            ) {
                TextButton(onClick = onBack) { Text("≡ Contents", color = Ink) }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "How the reading has gone",
                    style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                )
            }
            PagedNotes(rows = rows, onStrike = strike)
        }
    }
}

@Composable
private fun PagedNotes(rows: List<NoteRow>, onStrike: (Int) -> Unit) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val availableHeightPx = with(density) { (maxHeight - 32.dp).toPx().toInt() }
        val contentWidthPx = with(density) {
            (maxWidth - EdgeTapWidth * 2).toPx().toInt().coerceAtLeast(1)
        }
        val pages = remember(rows, contentWidthPx, availableHeightPx) {
            paginateNotes(rows, contentWidthPx, availableHeightPx, density, measurer)
        }
        var pageIndex by remember(pages.size) { mutableStateOf(0) }
        // Striking a line out reflows the pages under the reader's thumb. Stay
        // on the page they were looking at unless it no longer exists — the
        // same rule the Contents screen follows after deleting a note.
        LaunchedEffect(pages.size) {
            if (pageIndex >= pages.size) pageIndex = (pages.size - 1).coerceAtLeast(0)
        }
        val hasPrev = pageIndex > 0
        val hasNext = pageIndex < pages.size - 1
        val interaction = remember { MutableInteractionSource() }

        // The side buttons, claimed the way every other paged screen claims
        // them — at the Activity's door, not through Compose focus.
        val turn = rememberUpdatedState<(Boolean) -> Unit> { forward ->
            if (forward) { if (hasNext) pageIndex++ } else { if (hasPrev) pageIndex-- }
        }
        DisposableEffect(Unit) {
            val release = PageKeys.register { forward -> turn.value(forward) }
            onDispose(release)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(EdgeTapWidth)
                        .fillMaxHeight()
                        .clickable(enabled = hasPrev, interactionSource = interaction, indication = null) {
                            pageIndex--
                        },
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    pages.getOrNull(pageIndex)?.forEach { row ->
                        RenderNoteRow(row, onStrike)
                    }
                }
                Box(
                    modifier = Modifier
                        .width(EdgeTapWidth)
                        .fillMaxHeight()
                        .clickable(enabled = hasNext, interactionSource = interaction, indication = null) {
                            pageIndex++
                        },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (pages.size <= 1) "" else "Page ${pageIndex + 1} of ${pages.size}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (pages.size > 1) "tap ← / → edges to turn" else "",
                    style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun RenderNoteRow(row: NoteRow, onStrike: (Int) -> Unit) {
    when (row) {
        is NoteRow.Intro -> {
            Spacer(Modifier.height(12.dp))
            Text(
                text = row.text,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Muted,
            )
            Spacer(Modifier.height(16.dp))
        }
        is NoteRow.Empty -> {
            Text(
                text = row.text,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Muted,
            )
        }
        is NoteRow.ChapterHeader -> {
            Spacer(Modifier.height(14.dp))
            Text(
                text = row.label,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Muted,
            )
            row.title?.let {
                Text(
                    text = it,
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    color = Ink,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        is NoteRow.GroupLabel -> {
            Spacer(Modifier.height(6.dp))
            Text(
                text = row.text,
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = Muted,
            )
        }
        is NoteRow.Observation -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = row.text,
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                // One tap, no confirmation. Disagreeing with what the book
                // decided about you should not feel like an irreversible act
                // requiring a dialog — and the observation is only ever a
                // sentence about something you did, which is on the page
                // behind it either way.
                Text(
                    text = "✕",
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    color = Hairline,
                    modifier = Modifier
                        .clickable { onStrike(row.lineIndex) }
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/**
 * Same discipline as the Contents pager: measure anything that wraps, use
 * fixed heights only for rows known to be one line, and shave the budget so
 * an under-count clips nothing.
 */
private fun paginateNotes(
    rows: List<NoteRow>,
    contentWidthPx: Int,
    availableHeightPx: Int,
    density: Density,
    measurer: TextMeasurer,
): List<List<NoteRow>> {
    if (rows.isEmpty()) return listOf(emptyList())
    val budget = (availableHeightPx * 0.85f).toInt()
    val pages = mutableListOf<MutableList<NoteRow>>()
    pages.add(mutableListOf())
    var height = 0
    rows.forEach { row ->
        val h = estimateNoteRowHeight(row, contentWidthPx, density, measurer)
        if (height + h > budget && pages.last().isNotEmpty()) {
            pages.add(mutableListOf())
            height = 0
        }
        pages.last().add(row)
        height += h
    }
    return pages.map { it.toList() }
}

private fun estimateNoteRowHeight(
    row: NoteRow,
    contentWidthPx: Int,
    density: Density,
    measurer: TextMeasurer,
): Int = with(density) {
    fun wrapped(text: String, size: Int, lineHeight: Int, widthPx: Int): Int = measurer.measure(
        text = AnnotatedString(text),
        style = TextStyle(
            fontSize = size.sp,
            lineHeight = lineHeight.sp,
            fontFamily = FontFamily.Serif,
        ),
        constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)),
    ).size.height

    when (row) {
        is NoteRow.Intro ->
            12.dp.toPx().toInt() + wrapped(row.text, 13, 19, contentWidthPx) +
                16.dp.toPx().toInt()
        is NoteRow.Empty -> wrapped(row.text, 14, 20, contentWidthPx)
        is NoteRow.ChapterHeader -> {
            val title = row.title?.let { wrapped(it, 16, 21, contentWidthPx) } ?: 0
            14.dp.toPx().toInt() + (12.sp.toPx() * 1.3f).toInt() + title + 6.dp.toPx().toInt()
        }
        is NoteRow.GroupLabel ->
            6.dp.toPx().toInt() + wrapped(row.text, 14, 19, contentWidthPx)
        is NoteRow.Observation -> {
            // The ✕ sits beside the text, so the text column is narrower.
            val textWidth = contentWidthPx - 32.dp.toPx().toInt()
            wrapped(row.text, 15, 21, textWidth) + 5.dp.toPx().toInt() * 2
        }
    }
}
