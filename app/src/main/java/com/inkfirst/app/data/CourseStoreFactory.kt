package com.inkfirst.app.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.inkfirst.core.CourseStore
import java.io.File

/**
 * Roots courses under getExternalFilesDir(null)/courses so the files are
 * visible to file managers on the device (per docs/design.md — greppable,
 * backupable, readable in 10 years).
 */
fun courseStoreFor(context: Context): CourseStore = CourseStore(coursesRootFor(context))

/**
 * The same directory as a plain [File], for the paths that work on the whole
 * shelf rather than on one course — backup copies the tree wholesale, and
 * `CourseStore` deliberately keeps its root to itself.
 */
fun coursesRootFor(context: Context): File {
    val base = context.getExternalFilesDir(null) ?: context.filesDir
    return File(base, "courses")
}

@Composable
fun rememberCourseStore(): CourseStore {
    val context = LocalContext.current
    return remember { courseStoreFor(context) }
}
