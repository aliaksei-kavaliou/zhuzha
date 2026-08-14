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
fun courseStoreFor(context: Context): CourseStore {
    val base = context.getExternalFilesDir(null) ?: context.filesDir
    return CourseStore(File(base, "courses"))
}

@Composable
fun rememberCourseStore(): CourseStore {
    val context = LocalContext.current
    return remember { courseStoreFor(context) }
}
