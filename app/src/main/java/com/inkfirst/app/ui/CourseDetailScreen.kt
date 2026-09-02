package com.inkfirst.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.Bookmark
import com.inkfirst.core.ChapterStatus
import com.inkfirst.core.Course
import com.inkfirst.core.PersistedChapter

private val InkColor = Color(0xFF000000)
private val PaperColor = Color(0xFFFFFFFF)
private val MutedColor = Color(0xFF555555)

private sealed interface TocBlock {
    data class BookTitle(val title: String, val subtitle: String) : TocBlock
    data class SectionHeader(val text: String) : TocBlock
    data class ChapterEntry(val chapter: PersistedChapter, val isCurrent: Boolean) : TocBlock
    data class BookmarkEntry(val bookmark: Bookmark, val chapterTitle: String) : TocBlock
}

@Composable
fun CourseDetailScreen(
    slug: String,
    currentChapterSlug: String? = null,
    onBack: () -> Unit,
    onReadChapter: (chapterSlug: String, blockIndex: Int) -> Unit,
) {
    val courseStore = rememberCourseStore()
    var course by remember { mutableStateOf<Course?>(null) }
    LaunchedEffect(slug) { course = courseStore.load(slug) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperColor),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            ) {
                // Same idea as the Reader's "≡ Contents" — label the destination
                // instead of the direction. Popping back from Contents lands on
                // the Library shelf.
                TextButton(onClick = onBack) { Text("≡ Library", color = InkColor) }
            }

            val c = course
            if (c == null) {
                Text(
                    "Course not found: $slug",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
                return@Column
            }

            val tocBlocks = remember(c, currentChapterSlug) {
                buildTocBlocks(c, currentChapterSlug)
            }

            PagedContents(
                blocks = tocBlocks,
                onChapterTap = { chapter ->
                    // Only READING chapters resume at the saved anchor. DONE
                    // chapters open at the top — a finished chapter's "resume"
                    // point is meaningless; bookmarks are the right tool for
                    // jumping to a specific spot.
                    val startIndex = if (chapter.status == ChapterStatus.READING) {
                        chapter.lastBlockIndex
                    } else 0
                    onReadChapter(chapter.slug, startIndex)
                },
                onBookmarkTap = { bookmark -> onReadChapter(bookmark.chapterSlug, bookmark.blockIndex) },
            )
        }
    }
}

private fun buildTocBlocks(course: Course, currentChapterSlug: String?): List<TocBlock> {
    val blocks = mutableListOf<TocBlock>()
    blocks += TocBlock.BookTitle(
        title = course.title,
        subtitle = "${course.chapters.size} chapters · ${course.dailyMinutes} min/day · ${course.style}",
    )
    blocks += TocBlock.SectionHeader("Contents")
    course.chapters.forEach { chapter ->
        // Mark as "here" if either (a) we jumped to Contents from the Reader
        // for this exact chapter, or (b) the chapter is in READING status —
        // the latter is what surfaces the "resume where you left off" hint
        // when the user arrives at Contents fresh from Library.
        val isCurrent = (currentChapterSlug != null && chapter.slug == currentChapterSlug) ||
            chapter.status == ChapterStatus.READING
        blocks += TocBlock.ChapterEntry(chapter = chapter, isCurrent = isCurrent)
    }
    if (course.bookmarks.isNotEmpty()) {
        blocks += TocBlock.SectionHeader("Bookmarks")
        course.bookmarks.forEach { bm ->
            val title = course.chapters.firstOrNull { it.slug == bm.chapterSlug }?.let {
                "Ch ${it.n}: ${it.title}"
            } ?: bm.chapterSlug
            blocks += TocBlock.BookmarkEntry(bm, title)
        }
    }
    return blocks
}

private val TocEdgeTapWidth = 44.dp

@Composable
private fun PagedContents(
    blocks: List<TocBlock>,
    onChapterTap: (PersistedChapter) -> Unit,
    onBookmarkTap: (Bookmark) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val availableHeightPx = with(density) { (maxHeight - 32.dp).toPx().toInt() }
        val contentWidthPx = with(density) {
            (maxWidth - TocEdgeTapWidth * 2).toPx().toInt().coerceAtLeast(1)
        }

        val pages = remember(blocks, availableHeightPx, contentWidthPx) {
            paginateToc(blocks, contentWidthPx, availableHeightPx, density, measurer)
        }
        var pageIndex by remember(pages) { mutableStateOf(0) }
        val hasPrev = pageIndex > 0
        val hasNext = pageIndex < pages.size - 1
        val interaction = remember { MutableInteractionSource() }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(TocEdgeTapWidth)
                        .fillMaxHeight()
                        .clickable(
                            enabled = hasPrev,
                            interactionSource = interaction,
                            indication = null,
                        ) { pageIndex-- },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // Swipe support in the middle content area — matches the
                        // reader's horizontal-drag-to-turn. Consumed-check keeps
                        // row taps (chapter/bookmark) working unimpeded.
                        .pointerInput(pageIndex, pages.size) {
                            val threshold = 60.dp.toPx()
                            val startPx = 10.dp.toPx()
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var dx = 0f
                                var isDrag = false
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull() ?: break
                                    if (!ch.pressed) {
                                        if (isDrag && !ch.isConsumed) {
                                            if (dx <= -threshold && hasNext) pageIndex++
                                            else if (dx >= threshold && hasPrev) pageIndex--
                                        }
                                        break
                                    }
                                    dx += ch.positionChange().x
                                    if (!isDrag && kotlin.math.abs(dx) > startPx) isDrag = true
                                }
                            }
                        },
                ) {
                    pages.getOrNull(pageIndex)?.forEach { block ->
                        RenderTocBlock(
                            block = block,
                            onChapterTap = onChapterTap,
                            onBookmarkTap = onBookmarkTap,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(TocEdgeTapWidth)
                        .fillMaxHeight()
                        .clickable(
                            enabled = hasNext,
                            interactionSource = interaction,
                            indication = null,
                        ) { pageIndex++ },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (pages.size <= 1) "" else "Page ${pageIndex + 1} of ${pages.size}",
                    style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (pages.size > 1) "tap ← / → edges to turn" else "",
                    style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun RenderTocBlock(
    block: TocBlock,
    onChapterTap: (PersistedChapter) -> Unit,
    onBookmarkTap: (Bookmark) -> Unit,
) {
    when (block) {
        is TocBlock.BookTitle -> BookTitleBlock(block)
        is TocBlock.SectionHeader -> SectionHeaderBlock(block)
        is TocBlock.ChapterEntry -> ChapterEntryBlock(block, onChapterTap)
        is TocBlock.BookmarkEntry -> BookmarkEntryBlock(block, onBookmarkTap)
    }
}

@Composable
private fun BookTitleBlock(b: TocBlock.BookTitle) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = b.title,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        color = InkColor,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = b.subtitle,
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        fontSize = 13.sp,
        color = MutedColor,
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SectionHeaderBlock(b: TocBlock.SectionHeader) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = b.text.uppercase(),
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = MutedColor,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ChapterEntryBlock(entry: TocBlock.ChapterEntry, onTap: (PersistedChapter) -> Unit) {
    val chapter = entry.chapter
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap(chapter) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (entry.isCurrent) "▸" else " ",
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            color = InkColor,
            modifier = Modifier.padding(end = 8.dp, top = 2.dp),
        )
        Text(
            text = "${chapter.n}. ${chapter.title}",
            fontFamily = FontFamily.Serif,
            fontWeight = if (entry.isCurrent) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = InkColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BookmarkEntryBlock(entry: TocBlock.BookmarkEntry, onTap: (Bookmark) -> Unit) {
    val chapterLabel = entry.chapterTitle.substringBefore(":").trim()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap(entry.bookmark) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "▪",
            fontFamily = FontFamily.Serif,
            fontSize = 14.sp,
            color = InkColor,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = "$chapterLabel · block ${entry.bookmark.blockIndex}",
            fontFamily = FontFamily.Serif,
            fontSize = 14.sp,
            color = InkColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = entry.bookmark.created,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 12.sp,
            color = MutedColor,
        )
    }
}

internal fun statusLabel(status: ChapterStatus): String = when (status) {
    ChapterStatus.PENDING -> "not compiled"
    ChapterStatus.GENERATED -> "unread"
    ChapterStatus.READING -> "reading"
    ChapterStatus.DONE -> "read"
}

/**
 * TOC pagination. Uses TextMeasurer for anything with wrapping text (BookTitle,
 * ChapterEntry title, BookmarkEntry) — fixed heights only for known-single-line
 * elements. 15% shave off the height budget — 10% was still allowing occasional
 * bottom-row clipping at 320px on multi-line titles.
 */
private fun paginateToc(
    blocks: List<TocBlock>,
    contentWidthPx: Int,
    availableHeightPx: Int,
    density: Density,
    measurer: TextMeasurer,
): List<List<TocBlock>> {
    if (blocks.isEmpty()) return listOf(emptyList())
    val budget = (availableHeightPx * 0.85f).toInt()
    val pages = mutableListOf<MutableList<TocBlock>>()
    pages.add(mutableListOf())
    var height = 0
    for (block in blocks) {
        val h = estimateTocBlockHeight(block, contentWidthPx, density, measurer)
        val pageEmpty = pages.last().isEmpty()
        if (height + h > budget && !pageEmpty) {
            pages.add(mutableListOf())
            height = 0
        }
        pages.last().add(block)
        height += h
    }
    return pages.map { it.toList() }
}

private fun estimateTocBlockHeight(
    block: TocBlock,
    contentWidthPx: Int,
    density: Density,
    measurer: TextMeasurer,
): Int = with(density) {
    when (block) {
        is TocBlock.BookTitle -> {
            val titleResult = measurer.measure(
                text = AnnotatedString(block.title),
                style = TextStyle(
                    fontSize = 26.sp, lineHeight = 32.sp,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                ),
                constraints = Constraints(maxWidth = contentWidthPx),
            )
            val subtitleH = (13.sp.toPx() * 1.3f).toInt()
            16.dp.toPx().toInt() + titleResult.size.height + 6.dp.toPx().toInt() +
                subtitleH + 16.dp.toPx().toInt()
        }
        is TocBlock.SectionHeader -> {
            12.dp.toPx().toInt() + (12.sp.toPx() * 1.3f).toInt() + 6.dp.toPx().toInt()
        }
        is TocBlock.ChapterEntry -> {
            val titleWidth = (contentWidthPx - 24.dp.toPx().toInt()).coerceAtLeast(1)
            val titleResult = measurer.measure(
                text = AnnotatedString("${block.chapter.n}. ${block.chapter.title}"),
                style = TextStyle(
                    fontSize = 16.sp, lineHeight = 22.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = if (block.isCurrent) FontWeight.Bold else FontWeight.Normal,
                ),
                constraints = Constraints(maxWidth = titleWidth),
            )
            val padding = 8.dp.toPx().toInt() * 2
            titleResult.size.height + padding
        }
        is TocBlock.BookmarkEntry -> {
            (14.sp.toPx() * 1.3f).toInt() + 4.dp.toPx().toInt() * 2
        }
    }
}
