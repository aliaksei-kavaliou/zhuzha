package com.inkfirst.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

private sealed interface GenState {
    data object Idle : GenState
    data class Running(val chapterN: Int) : GenState
    data class Failed(val chapterN: Int, val message: String) : GenState
}

@Composable
fun CourseDetailScreen(
    slug: String,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
        }

        val c = course
        if (c == null) {
            Text("Course not found: $slug", color = MaterialTheme.colorScheme.error)
            return@Column
        }

        Text(c.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "${c.chapters.size} chapters · ${c.dailyMinutes} min/day · ${c.style}",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.padding(top = 4.dp))
        HorizontalDivider()

        if (c.bookmarks.isNotEmpty()) {
            BookmarksSection(
                course = c,
                onJump = { bm -> onReadChapter(bm.chapterSlug, bm.blockIndex) },
            )
            HorizontalDivider()
        }

        val apiKeyIsSet = !apiKeyStore.getApiKey().isNullOrBlank()

        c.chapters.forEach { chapter ->
            ChapterRow(
                chapter = chapter,
                gen = gen,
                apiKeyIsSet = apiKeyIsSet,
                onGenerate = { onGenerateChapter(c, chapter.n, scope, courseStore, apiKeyStore, onDone = { updated ->
                    course = updated ?: course
                }, onGenState = { gen = it }) },
                onRead = { onReadChapter(chapter.slug, 0) },
            )
        }
    }
}

@Composable
private fun BookmarksSection(course: Course, onJump: (Bookmark) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Bookmarks", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.padding(top = 4.dp))
        course.bookmarks.forEach { bm ->
            val chapterTitle = course.chapters.firstOrNull { it.slug == bm.chapterSlug }?.let {
                "Ch ${it.n}: ${it.title}"
            } ?: bm.chapterSlug
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onJump(bm) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("★  ", style = MaterialTheme.typography.bodyMedium)
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(chapterTitle, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "block ${bm.blockIndex} · saved ${bm.created}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: PersistedChapter,
    gen: GenState,
    apiKeyIsSet: Boolean,
    onGenerate: () -> Unit,
    onRead: () -> Unit,
) {
    val isThisRunning = gen is GenState.Running && gen.chapterN == chapter.n
    val thisFailure = (gen as? GenState.Failed)?.takeIf { it.chapterN == chapter.n }
    val anyRunning = gen is GenState.Running

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = "${chapter.n}. ${chapter.title}  ·  ~${chapter.estMinutes} min",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "status: ${chapter.status.name.lowercase()}",
            style = MaterialTheme.typography.bodySmall,
        )
        chapter.objectives.forEach { objective ->
            Text("• $objective", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.padding(top = 4.dp))

        when {
            isThisRunning -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text("Compiling chapter…", style = MaterialTheme.typography.bodySmall)
            }
            chapter.status == ChapterStatus.PENDING -> Button(
                enabled = !anyRunning && apiKeyIsSet,
                onClick = onGenerate,
            ) { Text(if (apiKeyIsSet) "Generate chapter" else "Set API key in Settings") }
            else -> Button(onClick = onRead) { Text("Read") }
        }

        thisFailure?.let {
            Text(
                text = it.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun onGenerateChapter(
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

                // Memory summary is best-effort — a failure here doesn't roll back the chapter.
                try {
                    val summary = MemoryUpdater(client, model).summarize(course, n, markdown, apiKey)
                    courseStore.appendMemory(course.slug, n, chapter.title, summary)
                } catch (e: Exception) {
                    System.err.println("memory update failed for chapter $n: ${e.message}")
                }

                onDone(updated)
                onGenState(GenState.Idle)
            }
        } catch (e: Exception) {
            onGenState(GenState.Failed(n, e.message ?: "Unknown error"))
        }
    }
}
