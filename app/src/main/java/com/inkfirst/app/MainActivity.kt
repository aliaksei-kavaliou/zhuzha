package com.inkfirst.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.inkfirst.app.ui.CourseDetailScreen
import com.inkfirst.app.ui.LibraryScreen
import com.inkfirst.app.ui.NewCourseScreen
import com.inkfirst.app.ui.ReaderScreen
import com.inkfirst.app.ui.SettingsScreen

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
                onBack = { nav.popBackStack() },
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
                    nav.navigate(Routes.courseDetail(courseSlug, chapterSlug))
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
