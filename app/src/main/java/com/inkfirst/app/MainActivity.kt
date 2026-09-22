package com.inkfirst.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
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
import com.inkfirst.app.ui.BackupScreen
import com.inkfirst.app.ui.CourseDetailScreen
import com.inkfirst.app.ui.InkFirstTheme
import com.inkfirst.app.ui.LibraryScreen
import com.inkfirst.app.ui.NewCourseScreen
import com.inkfirst.app.ui.PageKeys
import com.inkfirst.app.ui.ReaderNotesScreen
import com.inkfirst.app.ui.ReaderScreen
import com.inkfirst.app.ui.SearchScreen
import com.inkfirst.app.ui.SettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val NEW_COURSE = "new-course"
    // A trailing "/-" segment means "no current chapter" (from the Library entry).
    // From the Reader menu we pass the current chapterSlug so the Contents page
    // can highlight the "you are here" row.
    const val COURSE_DETAIL = "course/{slug}/{currentChapter}"
    const val READER = "reader/{courseSlug}/{chapterSlug}/{blockIndex}"
    const val READER_NOTES = "reader-notes/{slug}"
    fun readerNotes(slug: String) = "reader-notes/$slug"
    const val SEARCH = "search/{slug}"
    fun search(slug: String) = "search/$slug"
    fun courseDetail(slug: String, currentChapter: String? = null) =
        "course/$slug/${currentChapter?.takeIf { it.isNotBlank() } ?: "-"}"
    fun reader(courseSlug: String, chapterSlug: String, blockIndex: Int = 0) =
        "reader/$courseSlug/$chapterSlug/$blockIndex"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InkFirstTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    InkFirstApp()
                }
            }
        }
    }

    /**
     * The Boox side buttons, caught at the door.
     *
     * They arrive as VolumeUp / VolumeDown, so not consuming them puts
     * Android's volume slider over the page. A Compose key handler can only
     * consume them while something in its subtree holds focus, and on the pen
     * path focus moves around constantly — the SDK's SurfaceView, any
     * TextButton on the page, the selection container being rebuilt. Here
     * focus is not part of the question. See [PageKeys].
     *
     * Both the DOWN and the UP are consumed: releasing an unconsumed
     * VolumeUp is enough on its own to raise the slider.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val forward = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> true
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP -> false
            else -> return super.dispatchKeyEvent(event)
        }
        if (!PageKeys.claimed()) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) PageKeys.dispatch(forward)
        return true
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
        // A cold start puts you back exactly where you were, whatever the
        // chapter's status says. Resuming only READING chapters meant that
        // reaching the last page — which flips a chapter to DONE — threw the
        // position away, so finishing a chapter in the evening and opening
        // the app next morning landed on its first page. PENDING opens on the
        // CompilePrompt regardless of blockIndex.
        val blockIndex = chapter.lastBlockIndex.coerceAtLeast(0)
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
            SettingsScreen(
                onDone = { nav.popBackStack() },
                onBackup = { nav.navigate(Routes.BACKUP) },
            )
        }
        composable(Routes.BACKUP) {
            BackupScreen(onDone = { nav.popBackStack() })
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
                onOpenReaderNotes = { nav.navigate(Routes.readerNotes(slug)) },
                onSearch = { nav.navigate(Routes.search(slug)) },
                onReadChapter = { chapterSlug, blockIndex ->
                    nav.navigate(Routes.reader(slug, chapterSlug, blockIndex))
                },
            )
        }
        composable(Routes.SEARCH) { entry ->
            val slug = entry.arguments?.getString("slug").orEmpty()
            SearchScreen(
                slug = slug,
                // Same rule as the reader-notes screen: name the destination
                // rather than popping, so arriving here from anywhere leaves
                // the reader where the label said they would be.
                onBack = {
                    nav.navigate(Routes.courseDetail(slug)) {
                        popUpTo(Routes.COURSE_DETAIL) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenHit = { chapterSlug, blockIndex ->
                    nav.navigate(Routes.reader(slug, chapterSlug, blockIndex))
                },
            )
        }
        composable(Routes.READER_NOTES) { entry ->
            val slug = entry.arguments?.getString("slug").orEmpty()
            ReaderNotesScreen(
                slug = slug,
                // Always Contents, never a pop: this screen is reached from
                // the Contents list AND from the reader's own menu, and a pop
                // from the second lands back in the chapter rather than
                // where the label says.
                onBack = {
                    nav.navigate(Routes.courseDetail(slug)) {
                        popUpTo(Routes.COURSE_DETAIL) { inclusive = true }
                        launchSingleTop = true
                    }
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
                onOpenReaderNotes = { nav.navigate(Routes.readerNotes(courseSlug)) },
                onSearch = { nav.navigate(Routes.search(courseSlug)) },
                onOpenChapter = { newChapterSlug, newBlockIndex ->
                    nav.navigate(Routes.reader(courseSlug, newChapterSlug, newBlockIndex)) {
                        popUpTo(Routes.READER) { inclusive = true }
                    }
                },
            )
        }
    }
}
