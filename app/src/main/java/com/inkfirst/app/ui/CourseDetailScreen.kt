package com.inkfirst.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@Composable
private fun PagedContents(
    blocks: List<TocBlock>,
    onChapterTap: (PersistedChapter) -> Unit,
    onBookmarkTap: (Bookmark) -> Unit,
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val availableHeightPx = with(density) { (maxHeight - 32.dp).toPx().toInt() }

        val pages = remember(blocks, availableHeightPx) {
            paginateToc(blocks, availableHeightPx, density)
        }
        var pageIndex by remember(pages) { mutableStateOf(0) }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(pageIndex, pages.size) {
                        detectTapGestures { pos ->
                            when {
                                pos.x < size.width / 2 -> {
                                    if (pageIndex > 0) pageIndex--
                                }
                                else -> {
                                    if (pageIndex < pages.size - 1) pageIndex++
                                }
                            }
                        }
                    },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    pages.getOrNull(pageIndex)?.forEach { block ->
                        RenderTocBlock(
                            block = block,
                            onChapterTap = onChapterTap,
                            onBookmarkTap = onBookmarkTap,
                        )
                    }
                }
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
                    text = if (pages.size > 1) "tap ← / → to turn" else "",
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
            Text(
                text = "~${chapter.estMinutes} min · ${statusLabel(chapter.status)}",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = MutedColor,
                modifier = Modifier.padding(top = 2.dp),
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap(entry.bookmark) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "▪",
            fontFamily = FontFamily.Serif,
            fontSize = 14.sp,
            color = InkColor,
            modifier = Modifier.padding(end = 8.dp, top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.chapterTitle,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                color = InkColor,
            )
            Text(
                text = "saved ${entry.bookmark.created}",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = MutedColor,
            )
        }
    }
}

private fun statusLabel(status: ChapterStatus): String = when (status) {
    ChapterStatus.PENDING -> "not compiled"
    ChapterStatus.GENERATED -> "unread"
    ChapterStatus.READING -> "reading"
    ChapterStatus.DONE -> "read"
}

// Fixed per-block heights (dp). Rough enough — Contents pages don't need
// the pixel accuracy the chapter paginator does, because chapters/bookmarks
// are all small and homogeneous.
private const val HEIGHT_BOOK_TITLE_DP = 128
private const val HEIGHT_SECTION_HEADER_DP = 40
private const val HEIGHT_CHAPTER_ENTRY_DP = 88
private const val HEIGHT_BOOKMARK_ENTRY_DP = 60

private fun paginateToc(
    blocks: List<TocBlock>,
    availableHeightPx: Int,
    density: Density,
): List<List<TocBlock>> {
    if (blocks.isEmpty()) return listOf(emptyList())
    val budget = (availableHeightPx * 0.95f).toInt()
    val pages = mutableListOf<MutableList<TocBlock>>()
    pages.add(mutableListOf())
    var height = 0
    for (block in blocks) {
        val h = with(density) {
            when (block) {
                is TocBlock.BookTitle -> HEIGHT_BOOK_TITLE_DP.dp.toPx().toInt()
                is TocBlock.SectionHeader -> HEIGHT_SECTION_HEADER_DP.dp.toPx().toInt()
                is TocBlock.ChapterEntry -> {
                    var extra = 0
                    if (block.genRunning) extra += 24
                    if (block.genError != null) extra += 40
                    (HEIGHT_CHAPTER_ENTRY_DP + extra).dp.toPx().toInt()
                }
                is TocBlock.BookmarkEntry -> HEIGHT_BOOKMARK_ENTRY_DP.dp.toPx().toInt()
            }
        }
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
