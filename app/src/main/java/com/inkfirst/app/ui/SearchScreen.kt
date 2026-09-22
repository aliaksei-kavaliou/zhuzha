package com.inkfirst.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.CourseSearch

/**
 * Finding a passage again in a book already read.
 *
 * Results scroll rather than paginate, unlike the Contents. The no-scrolling
 * rule is about the READING surface — a page of a book has a fixed extent and
 * pretending otherwise is what makes e-readers feel wrong. A result list has
 * no such nature; it is machinery, like the Library, which scrolls too.
 */
@Composable
fun SearchScreen(
    slug: String,
    onBack: () -> Unit,
    onOpenHit: (chapterSlug: String, blockIndex: Int) -> Unit,
) {
    val store = rememberCourseStore()
    var query by remember { mutableStateOf("") }
    val course = remember(slug) { store.load(slug) }

    // Recomputed as the query changes. Whole-book search reads every
    // generated chapter off disk, which is milliseconds for a book — no
    // index to build, invalidate or keep in step with the files.
    val hits = remember(query, course) {
        val c = course ?: return@remember emptyList()
        CourseSearch.inCourse(c, query) { chapterSlug -> store.readChapter(slug, chapterSlug) }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(horizontal = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        ) {
            TextButton(onClick = onBack) { Text("≡ Contents", color = Ink) }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = {
                Text(
                    text = "Search this book",
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    color = Muted,
                )
            },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                color = Ink,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Paper,
                unfocusedContainerColor = Paper,
                cursorColor = Ink,
            ),
        )

        val summary = when {
            course == null -> "Course not found."
            query.isBlank() -> "Searches the chapters, your notes and the passages you asked about."
            hits.isEmpty() -> "Nothing found."
            hits.size == 1 -> "1 result"
            else -> "${hits.size} results"
        }
        Text(
            text = summary,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 12.sp,
            color = Muted,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(hits) { hit ->
                HitRow(hit) {
                    // A chapter hit is an index into the Markdown as WRITTEN;
                    // a mark already stores its index as DISPLAYED, because
                    // the reader is what created it. Only the first needs
                    // converting — see [READER_LEADING_BLOCKS].
                    val target = if (hit.kind == CourseSearch.Kind.CHAPTER) {
                        readerBlockIndex(hit.blockIndex)
                    } else {
                        hit.blockIndex
                    }
                    onOpenHit(hit.chapterSlug, target)
                }
                HorizontalDivider(color = Color(0xFFDDDDDD))
            }
        }
    }
}

@Composable
private fun HitRow(hit: CourseSearch.Hit, onTap: () -> Unit) {
    // The same margin glyphs the page and the Contents use, so a result is
    // recognisable as "a thing I wrote" or "a thing I asked" at a glance.
    val glyph = when (hit.kind) {
        CourseSearch.Kind.NOTE -> "✎"
        CourseSearch.Kind.QUESTION -> "?"
        CourseSearch.Kind.CHAPTER -> "•"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = glyph,
            fontFamily = FontFamily.Serif,
            fontSize = 14.sp,
            color = Ink,
            modifier = Modifier.padding(end = 8.dp, top = 2.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = hit.snippet,
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Ink,
            )
            Text(
                text = if (hit.chapterN > 0) "Ch ${hit.chapterN}: ${hit.chapterTitle}" else hit.chapterTitle,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Muted,
            )
        }
    }
}
