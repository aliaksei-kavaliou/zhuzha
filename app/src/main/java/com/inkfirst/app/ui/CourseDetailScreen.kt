package com.inkfirst.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.inkfirst.app.data.ApiKeyStore
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.Bookmark
import com.inkfirst.core.ChapterGenerator
import com.inkfirst.core.ChapterStatus
import com.inkfirst.core.Course
import com.inkfirst.core.CourseStore
import com.inkfirst.core.KtorGeminiClient
import com.inkfirst.core.MemoryUpdater
import com.inkfirst.core.PersistedChapter
import kotlinx.coroutines.launch

// E-ink palette — same as reader for continuity.
private val InkColor = Color(0xFF111111)
private val PaperColor = Color(0xFFF8F5EE)
private val MutedColor = Color(0xFF555555)
private val BorderColor = Color(0xFF888888)

private sealed interface TocBlock {
    data class BookTitle(val title: String, val subtitle: String) : TocBlock
    data class SectionHeader(val text: String) : TocBlock
    data class ChapterEntry(
        val chapter: PersistedChapter,
        val isCurrent: Boolean,
        val genRunning: Boolean,
        val genError: String?,
    ) : TocBlock
    data class BookmarkEntry(val bookmark: Bookmark, val chapterTitle: String) : TocBlock
}

private sealed interface GenState {
    data object Idle : GenState
    data class Running(val chapterN: Int) : GenState
    data class Failed(val chapterN: Int, val message: String) : GenState
}

@Composable
fun CourseDetailScreen(
    slug: String,
    currentChapterSlug: String? = null,
    onBack: () -> Unit,
    onReadChapter: (chapterSlug: String, blockIndex: Int) -> Unit,
) {
    val context = LocalContext.current
    val courseStore = rememberCourseStore()
    val apiKeyStore = remember { ApiKeyStore(context) }
    val scope = rememberCoroutineScope()

    var course by remember { mutableStateOf<Course?>(null) }
    var gen by remember { mutableStateOf<GenState>(GenState.Idle) }

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
                TextButton(onClick = onBack) { Text("← Back", color = InkColor) }
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

            val tocBlocks = remember(c, currentChapterSlug, gen) {
                buildTocBlocks(c, currentChapterSlug, gen)
            }

            PagedContents(
                blocks = tocBlocks,
                onChapterTap = { chapter ->
                    if (chapter.status == ChapterStatus.PENDING) {
                        startGenerate(c, chapter.n, scope, courseStore, apiKeyStore,
                            onDone = { updated ->
                                if (updated != null) {
                                    course = updated
                                    // After successful generation, jump straight into the reader.
                                    onReadChapter(chapter.slug, 0)
                                }
                            },
                            onGenState = { gen = it },
                        )
                    } else {
                        onReadChapter(chapter.slug, 0)
                    }
                },
                onBookmarkTap = { bookmark ->
                    onReadChapter(bookmark.chapterSlug, bookmark.blockIndex)
                },
            )
        }
    }
}

private fun buildTocBlocks(
    course: Course,
    currentChapterSlug: String?,
    gen: GenState,
): List<TocBlock> {
    val blocks = mutableListOf<TocBlock>()
    blocks += TocBlock.BookTitle(
        title = course.title,
        subtitle = "${course.chapters.size} chapters · ${course.dailyMinutes} min/day · ${course.style}",
    )
    blocks += TocBlock.SectionHeader("Contents")
    course.chapters.forEach { chapter ->
        val running = gen is GenState.Running && gen.chapterN == chapter.n
        val error = (gen as? GenState.Failed)?.takeIf { it.chapterN == chapter.n }?.message
        blocks += TocBlock.ChapterEntry(
            chapter = chapter,
            isCurrent = currentChapterSlug != null && chapter.slug == currentChapterSlug,
            genRunning = running,
            genError = error,
        )
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

// Width of the dedicated left/right edge zones that turn TOC pages. Rows on
// the Contents page have their own clickable so tapping on-row would open a
// chapter; reserving these strips as page-turn-only sidesteps the conflict
// without gesture-priority tricks.
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
        // Content width = full width minus the two 44dp edge tap zones.
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
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                // Left edge — prev page.
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
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    pages.getOrNull(pageIndex)?.forEach { block ->
                        RenderTocBlock(
                            block = block,
                            onChapterTap = onChapterTap,
                            onBookmarkTap = onBookmarkTap,
                        )
                    }
                }
                // Right edge — next page.
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
        // "You are here" marker for the chapter currently being read.
        Text(
            text = if (entry.isCurrent) "▸" else " ",
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            color = InkColor,
            modifier = Modifier.padding(end = 8.dp, top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${chapter.n}. ${chapter.title}",
                fontFamily = FontFamily.Serif,
                fontWeight = if (entry.isCurrent) FontWeight.Bold else FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = InkColor,
            )
            // Details (est-minutes, status) intentionally omitted here — TOC stays
            // scannable; the meta line renders as an intro at the start of the
            // chapter itself instead.
            if (entry.genRunning) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 6.dp).height(14.dp))
                    Text(
                        "Compiling…",
                        fontFamily = FontFamily.Serif,
                        fontSize = 12.sp,
                        color = MutedColor,
                    )
                }
            }
            entry.genError?.let {
                Text(
                    text = it,
                    fontFamily = FontFamily.Serif,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BookmarkEntryBlock(entry: TocBlock.BookmarkEntry, onTap: (Bookmark) -> Unit) {
    // Compact display — the full chapter title is already right above in
    // the Contents section, no need to repeat it here.
    val chapterLabel = entry.chapterTitle.substringBefore(":").trim()  // "Ch 1"
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
 * Contents pagination. Titles wrap unpredictably at emulator/phone widths, so
 * we can't fake it with fixed heights — that's how chapter 3 fell off the
 * bottom of page 1. We measure the title text at the actual content width
 * (which is what the row will render at) and add fixed heights only for
 * subtitles/padding/status lines.
 */
private fun paginateToc(
    blocks: List<TocBlock>,
    contentWidthPx: Int,
    availableHeightPx: Int,
    density: Density,
    measurer: TextMeasurer,
): List<List<TocBlock>> {
    if (blocks.isEmpty()) return listOf(emptyList())
    // 10% shave for the same reason the reader paginator uses one: TextMeasurer
    // undercounts by a few pixels per block, enough over ~5 blocks to push a
    // row's subtitle off the visible edge.
    val budget = (availableHeightPx * 0.90f).toInt()
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
            // 16dp top spacer + 6dp gap + subtitle (~1 line at 13sp) + 16dp bottom spacer.
            val subtitleH = (13.sp.toPx() * 1.3f).toInt()
            16.dp.toPx().toInt() + titleResult.size.height + 6.dp.toPx().toInt() +
                subtitleH + 16.dp.toPx().toInt()
        }
        is TocBlock.SectionHeader -> {
            // 12dp top spacer + label height + 6dp bottom spacer.
            12.dp.toPx().toInt() + (12.sp.toPx() * 1.3f).toInt() + 6.dp.toPx().toInt()
        }
        is TocBlock.ChapterEntry -> {
            // Row layout leaves ~24dp for the marker column; that shrinks the title width.
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
            val padding = 8.dp.toPx().toInt() * 2  // Row vertical padding
            val genExtra = if (block.genRunning) 24.dp.toPx().toInt() else 0
            val errorExtra = if (block.genError != null) 40.dp.toPx().toInt() else 0
            titleResult.size.height + padding + genExtra + errorExtra
        }
        is TocBlock.BookmarkEntry -> {
            // One line: "Ch 1 · block 5    2026-08-27". ~14sp + row padding.
            (14.sp.toPx() * 1.3f).toInt() + 4.dp.toPx().toInt() * 2
        }
    }
}

private fun startGenerate(
    course: Course,
    n: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    courseStore: CourseStore,
    apiKeyStore: ApiKeyStore,
    onDone: (Course?) -> Unit,
    onGenState: (GenState) -> Unit,
) {
    val apiKey = apiKeyStore.getApiKey().orEmpty()
    val model = apiKeyStore.getModelName()
    if (apiKey.isBlank()) {
        onGenState(GenState.Failed(n, "No API key set. Go to Settings first."))
        return
    }
    onGenState(GenState.Running(n))
    scope.launch {
        try {
            KtorGeminiClient().use { client ->
                val memory = courseStore.readMemory(course.slug)
                val markdown = ChapterGenerator(client, model).generate(course, n, memory, apiKey)
                val chapter = course.chapters.first { it.n == n }
                courseStore.saveChapter(course.slug, chapter.slug, markdown)
                val updated = courseStore.updateChapterStatus(course.slug, n, ChapterStatus.GENERATED)
                try {
                    val summary = MemoryUpdater(client, model).summarize(course, n, markdown, apiKey)
                    courseStore.appendMemory(course.slug, n, chapter.title, summary)
                } catch (e: Exception) {
                    System.err.println("memory update failed for chapter $n: ${e.message}")
                }
                onGenState(GenState.Idle)
                onDone(updated)
            }
        } catch (e: Exception) {
            onGenState(GenState.Failed(n, e.message ?: "Unknown error"))
            onDone(null)
        }
    }
}
