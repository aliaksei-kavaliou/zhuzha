package com.inkfirst.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.inkfirst.app.data.courseStoreFor
import com.inkfirst.app.ui.CourseDetailScreen
import com.inkfirst.app.ui.LibraryScreen
import com.inkfirst.app.ui.NewCourseScreen
import com.inkfirst.app.ui.ReaderScreen
import com.inkfirst.app.ui.SettingsScreen
import com.inkfirst.core.ChapterStatus

private object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val NEW_COURSE = "new-course"
    // A trailing "/-" segment means "no current chapter" (from the Library entry).
    // From the Reader menu we pass the current chapterSlug so the Contents page
    // can highlight the "you are here" row.
    const val COURSE_DETAIL = "course/{slug}/{currentChapter}"
    const val READER = "reader/{courseSlug}/{chapterSlug}/{blockIndex}"
    fun courseDetail(slug: String, currentChapter: String? = null) =
        "course/$slug/${currentChapter?.takeIf { it.isNotBlank() } ?: "-"}"
    fun reader(courseSlug: String, chapterSlug: String, blockIndex: Int = 0) =
        "reader/$courseSlug/$chapterSlug/$blockIndex"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    InkFirstApp()
                }
            }
        }
    }
}

@Composable
private fun InkFirstApp() {
    val nav = rememberNavController()
    val context = LocalContext.current

    // Resolve at cold start: if the last-read pointer names a course + chapter
    // that still exist on disk, push the Reader on top of Library so back
    // still lands on the shelf. Otherwise stay on Library. Computed once via
    // `remember(Unit)` — we don't want auto-resume triggering on config changes.
    val resumeRoute = remember {
        val store = courseStoreFor(context)
        val pointer = store.readLastRead() ?: return@remember null
        val course = store.load(pointer.courseSlug)
        val chapter = course?.chapters?.firstOrNull { it.slug == pointer.chapterSlug }
        if (course == null || chapter == null) {
            // Course/chapter gone (deleted, renamed) — drop the stale pointer.
            store.clearLastRead()
            return@remember null
        }
        // Match Contents' behavior: READING resumes at the saved anchor,
        // DONE/GENERATED open at the top. PENDING opens on the CompilePrompt
        // regardless of blockIndex.
        val blockIndex = if (chapter.status == ChapterStatus.READING) chapter.lastBlockIndex else 0
        Routes.reader(course.slug, chapter.slug, blockIndex)
    }

    if (resumeRoute != null) {
        LaunchedEffect(Unit) {
            nav.navigate(resumeRoute) { launchSingleTop = true }
        }
    }

    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onNewCourse = { nav.navigate(Routes.NEW_COURSE) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenCourse = { slug -> nav.navigate(Routes.courseDetail(slug)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.NEW_COURSE) {
            NewCourseScreen(
                onBack = { nav.popBackStack() },
                onCourseCreated = { slug ->
                    nav.navigate(Routes.courseDetail(slug)) {
                        popUpTo(Routes.LIBRARY) { inclusive = false }
                    }
                },
            )
        }
        composable(Routes.COURSE_DETAIL) { entry ->
            val slug = entry.arguments?.getString("slug").orEmpty()
            val currentChapter = entry.arguments?.getString("currentChapter")
                ?.takeIf { it != "-" && it.isNotBlank() }
            CourseDetailScreen(
                slug = slug,
                currentChapterSlug = currentChapter,
                // "≡ Library" means Library, so name the destination instead
                // of popping. Auto-resume cold-starts into [Library, Reader]
                // with no Contents between them, so Reader → Contents leaves
                // [Library, Reader, Contents] — and a pop from there landed
                // back in the chapter the user was trying to leave. Same
                // failure the Reader header had before a0fe57f.
                onBack = {
                    nav.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.LIBRARY) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onReadChapter = { chapterSlug, blockIndex ->
                    nav.navigate(Routes.reader(slug, chapterSlug, blockIndex))
                },
            )
        }
        composable(Routes.READER) { entry ->
            val courseSlug = entry.arguments?.getString("courseSlug").orEmpty()
            val chapterSlug = entry.arguments?.getString("chapterSlug").orEmpty()
            val blockIndex = entry.arguments?.getString("blockIndex")?.toIntOrNull() ?: 0
            ReaderScreen(
                courseSlug = courseSlug,
                chapterSlug = chapterSlug,
                startBlockIndex = blockIndex,
                onBack = { nav.popBackStack() },
                onOpenContents = {
                    // Reader → Contents shouldn't stack a new Contents entry each
                    // time; pop any existing Contents off first so the back stack
                    // stays flat (Library → Contents → Reader), and the new
                    // Contents entry can pass the current-chapter highlight.
                    nav.navigate(Routes.courseDetail(courseSlug, chapterSlug)) {
                        popUpTo(Routes.COURSE_DETAIL) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenChapter = { newChapterSlug, newBlockIndex ->
                    nav.navigate(Routes.reader(courseSlug, newChapterSlug, newBlockIndex)) {
                        popUpTo(Routes.READER) { inclusive = true }
                    }
                },
            )
        }
    }
}
