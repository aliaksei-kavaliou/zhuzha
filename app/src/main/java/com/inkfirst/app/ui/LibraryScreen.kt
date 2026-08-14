package com.inkfirst.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.CourseSummary

@Composable
fun LibraryScreen(
    onNewCourse: () -> Unit,
    onSettings: () -> Unit,
    onOpenCourse: (String) -> Unit,
) {
    val store = rememberCourseStore()
    var summaries by remember { mutableStateOf<List<CourseSummary>>(emptyList()) }

    LaunchedEffect(Unit) { summaries = store.list() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("InkFirst", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSettings) { Text("Settings") }
        }
        Text("AI-generated study books.", style = MaterialTheme.typography.bodySmall)

        Button(onClick = onNewCourse) { Text("New course") }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        if (summaries.isEmpty()) {
            Text(
                text = "No courses yet. Tap New course to start.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(summaries) { summary ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCourse(summary.slug) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(summary.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${summary.chapterCount} chapters · ${summary.created}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
